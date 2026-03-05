package com.rpeters.jellyfin.ui.player

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.core.FeatureFlags
import com.rpeters.jellyfin.data.repository.RemoteConfigRepository
import com.rpeters.jellyfin.ui.player.cast.*
import com.rpeters.jellyfin.ui.player.dlna.DlnaDevice
import com.rpeters.jellyfin.ui.player.dlna.DlnaPlaybackController
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.json.JSONObject
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrator for the Cast system.
 * Delegating work to specialized controllers while maintaining the public API for the ViewModel.
 */
@UnstableApi
@Singleton
class CastManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateStore: CastStateStore,
    private val discoveryController: CastDiscoveryController,
    private val sessionController: CastSessionController,
    private val playbackController: CastPlaybackController,
    private val mediaLoadBuilder: CastMediaLoadBuilder,
    private val dlnaPlaybackController: DlnaPlaybackController,
    private val remoteConfigRepository: RemoteConfigRepository,
) {
    val castState: StateFlow<CastState> = stateStore.castState

    private var castContext: CastContext? = null
    private val initializationLock = Any()
    private var initializationDeferred: CompletableDeferred<Boolean>? = null
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val castExecutor by lazy { Executors.newSingleThreadExecutor() }
    private var activeDlnaDevice: DlnaDevice? = null

    private val sessionListener by lazy { 
        sessionController.createSessionListener(managerScope) { updatePlaybackState() } 
    }

    fun initialize() {
        managerScope.launch { awaitInitialization() }
    }

    suspend fun awaitInitialization(): Boolean {
        if (castState.value.isInitialized) return castState.value.isAvailable

        val deferred = synchronized(initializationLock) {
            if (castState.value.isInitialized) null
            else {
                initializationDeferred?.takeIf { !it.isCompleted } ?: CompletableDeferred<Boolean>().also {
                    initializationDeferred = it
                    startInitialization(it)
                }
            }
        }
        return deferred?.await() ?: castState.value.isAvailable
    }

    private fun startInitialization(deferred: CompletableDeferred<Boolean>) {
        val contextTask = CastContext.getSharedInstance(context.applicationContext, castExecutor)
        contextTask.addOnSuccessListener { ctx ->
            castContext = ctx
            ctx.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
            
            val existingSession = ctx.sessionManager.currentCastSession
            val isConnected = existingSession?.isConnected == true
            
            if (isConnected) {
                playbackController.registerCallback(existingSession.remoteMediaClient) { updatePlaybackState() }
            }

            stateStore.update { it.copy(
                isInitialized = true,
                isAvailable = true,
                isConnected = isConnected,
                deviceName = existingSession?.castDevice?.friendlyName,
                isCasting = isConnected,
            )}
            deferred.complete(true)
        }.addOnFailureListener { e ->
            stateStore.update { it.copy(isInitialized = true, isAvailable = false) }
            deferred.complete(false)
        }
    }

    fun startDiscovery() = discoveryController.startDiscovery(managerScope, castContext)
    fun stopDiscovery() = discoveryController.stopDiscovery()

    fun connectToDevice(deviceName: String): Boolean {
        discoveryController.findDlnaDevice(deviceName)?.let { dlna ->
            activeDlnaDevice = dlna
            discoveryController.stopDiscovery()
            stateStore.update {
                it.copy(
                    isConnected = true,
                    deviceName = dlna.friendlyName,
                    isCasting = true,
                    isRemotePlaying = false,
                    error = null,
                )
            }
            return true
        }

        val router = MediaRouter.getInstance(context)
        val route = router.routes.firstOrNull { it.name == deviceName }
        return route?.let {
            activeDlnaDevice = null
            router.selectRoute(it)
            discoveryController.stopDiscovery()
            true
        } ?: run {
            stateStore.setError("Could not connect to selected device")
            false
        }
    }

    fun startCasting(
        mediaItem: MediaItem,
        item: BaseItemDto,
        sideLoadedSubs: List<SubtitleSpec> = emptyList(),
        startPositionMs: Long = 0L,
        playSessionId: String? = null,
        mediaSourceId: String? = null,
    ) {
        managerScope.launch {
            activeDlnaDevice?.let { dlna ->
                val playbackData = mediaLoadBuilder.resolvePlaybackData(
                    itemId = item.id.toString(),
                    castContext = castContext,
                    forceTranscode = true,
                ) ?: return@launch
                val ok = dlnaPlaybackController.loadAndPlay(
                    device = dlna,
                    streamUrl = playbackData.url,
                    title = item.name ?: "Video",
                )
                if (ok) {
                    stateStore.update { it.copy(isRemotePlaying = true, error = null) }
                } else {
                    stateStore.setError("Failed to send media via DLNA")
                }
                return@launch
            }

            val session = castContext?.sessionManager?.currentCastSession ?: return@launch
            loadCastMedia(
                session = session,
                item = item,
                sideLoadedSubs = sideLoadedSubs,
                startPositionMs = startPositionMs,
                playSessionId = playSessionId,
                mediaSourceId = mediaSourceId,
                forceTranscode = false,
                allowDirectRetry = true,
            )
        }
    }

    private suspend fun loadCastMedia(
        session: CastSession,
        item: BaseItemDto,
        sideLoadedSubs: List<SubtitleSpec>,
        startPositionMs: Long,
        playSessionId: String?,
        mediaSourceId: String?,
        forceTranscode: Boolean,
        allowDirectRetry: Boolean,
    ) {
        val playbackData = mediaLoadBuilder.resolvePlaybackData(
            itemId = item.id.toString(),
            castContext = castContext,
            forceTranscode = forceTranscode,
        ) ?: return

        val metadata = mediaLoadBuilder.buildMetadata(item)
        val tracks = mediaLoadBuilder.buildTracks(sideLoadedSubs)

        val mediaInfo = MediaInfo.Builder(playbackData.url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(playbackData.mimeType)
            .setMetadata(metadata)
            .setMediaTracks(tracks)
            .build()

        playbackController.setPendingSeek(startPositionMs)

        val customData = JSONObject().apply {
            (playSessionId ?: playbackData.playSessionId)?.let { put("playSessionId", it) }
            (mediaSourceId ?: playbackData.mediaSourceId)?.let { put("mediaSourceId", it) }
        }

        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .setCustomData(customData)
            .build()

        session.remoteMediaClient?.load(request)?.setResultCallback { result ->
            if (result.status.isSuccess) return@setResultCallback

            val shouldRetryAsTranscode = allowDirectRetry && !forceTranscode && playbackData.urlType == "direct"
            if (shouldRetryAsTranscode) {
                SecureLogger.w(
                    "CastManager",
                    "Direct cast load failed (${result.status.statusCode}); retrying with forced transcode",
                )
                managerScope.launch {
                    loadCastMedia(
                        session = session,
                        item = item,
                        sideLoadedSubs = sideLoadedSubs,
                        startPositionMs = startPositionMs,
                        playSessionId = playSessionId,
                        mediaSourceId = mediaSourceId,
                        forceTranscode = true,
                        allowDirectRetry = false,
                    )
                }
                return@setResultCallback
            }

            stateStore.setError("Failed to cast media (error ${result.status.statusCode})")
        }
    }

    /**
     * Loads a non-playing preview (artwork + metadata) for the given item onto the Cast device.
     */
    fun loadPreview(item: BaseItemDto, imageUrl: String?, backdropUrl: String?) {
        val session = castContext?.sessionManager?.currentCastSession ?: return
        
        managerScope.launch {
            val metadata = mediaLoadBuilder.buildMetadata(item)
            // building metadata already handles images, but if they are passed explicitly we could override
            
            val mediaInfo = MediaInfo.Builder(imageUrl ?: "")
                .setStreamType(MediaInfo.STREAM_TYPE_NONE)
                .setContentType("image/jpeg")
                .setMetadata(metadata)
                .build()

            val request = MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(false)
                .build()

            session.remoteMediaClient?.load(request)
        }
    }

    // Transport Controls delegated to PlaybackController
    fun pauseCasting() {
        val dlna = activeDlnaDevice
        if (dlna != null) {
            dlnaPlaybackController.pause(dlna)
            stateStore.update { it.copy(isRemotePlaying = false) }
            return
        }
        playbackController.pause(castContext)
    }

    fun resumeCasting() {
        val dlna = activeDlnaDevice
        if (dlna != null) {
            dlnaPlaybackController.play(dlna)
            stateStore.update { it.copy(isRemotePlaying = true) }
            return
        }
        playbackController.resume(castContext)
    }

    fun stopCasting() {
        val dlna = activeDlnaDevice
        if (dlna != null) {
            dlnaPlaybackController.stop(dlna)
            stateStore.update { it.copy(isRemotePlaying = false) }
            return
        }
        playbackController.stop(castContext)
    }

    fun seekTo(positionMs: Long) {
        val dlna = activeDlnaDevice
        if (dlna != null) {
            dlnaPlaybackController.seek(dlna, positionMs)
            stateStore.update { it.copy(currentPosition = positionMs) }
            return
        }
        playbackController.seekTo(castContext, positionMs)
    }

    fun setVolume(volume: Float) = playbackController.setVolume(castContext, volume)

    fun disconnectCastSession() {
        activeDlnaDevice?.let { dlnaPlaybackController.stop(it) }
        activeDlnaDevice = null
        castContext?.sessionManager?.endCurrentSession(true)
        stateStore.update { it.copy(isConnected = false, isCasting = false, deviceName = null) }
        stateStore.clearPersistedSession(managerScope)
    }

    fun updatePlaybackState() {
        if (activeDlnaDevice != null) return
        playbackController.updatePlaybackStateFromClient(castContext)
    }

    fun release() {
        managerScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        castContext?.sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
        discoveryController.stopDiscovery()
        castContext = null
    }
}
