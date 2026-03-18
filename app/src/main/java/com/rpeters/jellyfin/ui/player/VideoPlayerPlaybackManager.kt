package com.rpeters.jellyfin.ui.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.rpeters.jellyfin.data.offline.OfflinePlaybackManager
import com.rpeters.jellyfin.data.playback.AdaptiveBitrateMonitor
import com.rpeters.jellyfin.data.playback.EnhancedPlaybackManager
import com.rpeters.jellyfin.data.playback.PlaybackResult
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.utils.AnalyticsHelper
import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.PlayMethod
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Handles ExoPlayer lifecycle, initialization, and fallback logic.
 */
@UnstableApi
class VideoPlayerPlaybackManager @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val stateManager: VideoPlayerStateManager,
    private val trackManager: VideoPlayerTrackManager,
    private val repository: JellyfinRepository,
    private val enhancedPlaybackManager: EnhancedPlaybackManager,
    private val offlinePlaybackManager: OfflinePlaybackManager,
    private val playbackProgressManager: PlaybackProgressManager,
    private val adaptiveBitrateMonitor: AdaptiveBitrateMonitor,
    private val analytics: AnalyticsHelper,
    private val okHttpClient: okhttp3.OkHttpClient
) {
    var exoPlayer: ExoPlayer? = null
        private set
    var trackSelector: DefaultTrackSelector? = null
        private set
    val currentMediaItem: MediaItem?
        get() = currentPreparedMediaItem
    val currentSubtitleSpecs: List<SubtitleSpec>
        get() = currentPreparedSubtitleSpecs
    val currentPlaySessionId: String?
        get() = currentPlaybackSessionId
    val currentMediaSourceId: String?
        get() = currentPreparedMediaSourceId

    private var positionJob: Job? = null
    private var savedPositionBeforeFlush: Long? = null
    private var wasBufferingBeforeReady: Boolean = false
    private var positionRestoreAttempts: Int = 0
    private var lastRestoreAttemptTime: Long = 0L
    private val maxRestoreAttempts = 3
    private val restoreAttemptCooldownMs = 2000L

    private var hasAttemptedTranscodingFallback = false
    private var hasAttemptedOfflineFallback = false
    private var playerListener: Player.Listener? = null
    private var currentPreparedMediaItem: MediaItem? = null
    private var currentPreparedSubtitleSpecs: List<SubtitleSpec> = emptyList()
    private var currentPreparedMediaSourceId: String? = null
    private var currentPlaybackSessionId: String? = null

    fun initializeExoPlayer(listener: Player.Listener) {
        playerListener = listener
        val renderersFactory = DefaultRenderersFactory(applicationContext)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
            .forceEnableMediaCodecAsynchronousQueueing()
            .setAllowedVideoJoiningTimeMs(5000)

        val httpFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(applicationContext, httpFactory)
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)

        trackSelector = DefaultTrackSelector(applicationContext).apply {
            val params = buildUponParameters()
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowVideoNonSeamlessAdaptiveness(true)
                .setMaxVideoSizeSd()
                .clearVideoSizeConstraints()
                .setAllowVideoMixedDecoderSupportAdaptiveness(true)
            
            val displayMetrics = applicationContext.resources.displayMetrics
            params.setMaxVideoSize(displayMetrics.widthPixels, displayMetrics.heightPixels)
            setParameters(params.build())
        }

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 2_500, 5_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        exoPlayer = ExoPlayer.Builder(applicationContext)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setMediaSourceFactory(mediaSourceFactory)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector!!)
            .setLoadControl(loadControl)
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .build()

        exoPlayer?.addListener(listener)
    }

    suspend fun startPlayback(
        itemId: String,
        itemName: String,
        startPosition: Long,
        metadata: BaseItemDto?,
        sideLoadedSubs: List<SubtitleSpec>,
        forceOffline: Boolean = false,
        audioIndex: Int? = null,
        subtitleIndex: Int? = null,
        mediaSourceIdHint: String? = null,
        scope: CoroutineScope
    ) {
        hasAttemptedTranscodingFallback = false
        hasAttemptedOfflineFallback = false
        savedPositionBeforeFlush = null
        wasBufferingBeforeReady = false
        positionRestoreAttempts = 0
        lastRestoreAttemptTime = 0L
        currentPreparedSubtitleSpecs = sideLoadedSubs
        currentPreparedMediaSourceId = mediaSourceIdHint
        currentPlaybackSessionId = null
        currentPreparedMediaItem = null

        val isDownloaded = offlinePlaybackManager.isOfflinePlaybackAvailable(itemId)
        val isOnline = isDeviceOnline()
        val shouldUseOfflineSource = isDownloaded && (forceOffline || !isOnline)

        if (shouldUseOfflineSource) {
            initializeOfflinePlayback(itemId, itemName, startPosition, metadata, sideLoadedSubs, scope)
        } else {
            initializeNetworkPlayback(itemId, itemName, startPosition, metadata, sideLoadedSubs, audioIndex, subtitleIndex, scope)
        }
    }

    private suspend fun initializeOfflinePlayback(
        itemId: String,
        itemName: String,
        startPosition: Long,
        metadata: BaseItemDto?,
        sideLoadedSubs: List<SubtitleSpec>,
        scope: CoroutineScope
    ) {
        SecureLogger.i("VideoPlayerPlayback", "Initializing offline playback for $itemId")
        val offlineDownload = offlinePlaybackManager.getOfflineDownload(itemId)
        val offlineMediaItem = offlinePlaybackManager.getOfflineMediaItem(itemId)
            ?: throw IllegalStateException("Offline copy is unavailable")

        val initialDuration = offlineDownload?.runtimeTicks?.let { it / 10_000 } ?: 0L
        
        stateManager.updateState { it.copy(
            isDirectPlaying = false,
            isDirectStreaming = false,
            isTranscoding = false,
            transcodingReason = null,
            playbackMethod = "Offline",
            duration = if (initialDuration > 0) initialDuration else it.duration
        ) }
        currentPlaybackSessionId = UUID.randomUUID().toString()

        withContext(Dispatchers.Main) {
            val player = exoPlayer ?: return@withContext
            val offlineUri = offlineMediaItem.localConfiguration?.uri?.toString()
            val mediaItem = if (!offlineUri.isNullOrBlank()) {
                MediaItemFactory.build(
                    videoUrl = offlineUri,
                    title = metadata?.name ?: itemName,
                    sideLoadedSubs = sideLoadedSubs,
                    mimeTypeHint = null
                )
            } else {
                offlineMediaItem
            }

            currentPreparedMediaItem = mediaItem
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            if (startPosition > 0) player.seekTo(startPosition)
        }
    }

    private suspend fun initializeNetworkPlayback(
        itemId: String,
        itemName: String,
        startPosition: Long,
        metadata: BaseItemDto?,
        sideLoadedSubs: List<SubtitleSpec>,
        audioIndex: Int? = null,
        subtitleIndex: Int? = null,
        scope: CoroutineScope
    ) {
        val itemMetadata = metadata ?: throw Exception("Metadata required for network playback")
        
        val playbackResult = enhancedPlaybackManager.getOptimalPlaybackUrl(
            item = itemMetadata,
            audioStreamIndex = audioIndex,
            subtitleStreamIndex = subtitleIndex
        )

        when (playbackResult) {
            is PlaybackResult.DirectPlay -> {
                handleDirectPlayResult(playbackResult, itemMetadata, sideLoadedSubs, startPosition)
            }
            is PlaybackResult.Transcoding -> {
                handleTranscodingResult(playbackResult, itemMetadata, sideLoadedSubs, startPosition)
            }
            is PlaybackResult.Error -> throw Exception(playbackResult.message)
        }
    }

    private suspend fun handleDirectPlayResult(
        result: PlaybackResult.DirectPlay,
        metadata: BaseItemDto,
        sideLoadedSubs: List<SubtitleSpec>,
        startPosition: Long
    ) {
        stateManager.updateState { it.copy(
            isDirectPlaying = true,
            isDirectStreaming = false,
            isTranscoding = false,
            transcodingReason = null,
            playbackMethod = "Direct Play"
        ) }
        currentPlaybackSessionId = result.playSessionId ?: UUID.randomUUID().toString()

        val mimeType = when (result.container.lowercase(Locale.ROOT)) {
            "mp4", "m4v" -> MimeTypes.VIDEO_MP4
            "mkv" -> MimeTypes.VIDEO_MATROSKA
            "webm" -> MimeTypes.VIDEO_WEBM
            else -> null
        }

        preparePlayer(result.url, metadata.name, sideLoadedSubs, mimeType, startPosition)
    }

    private suspend fun handleTranscodingResult(
        result: PlaybackResult.Transcoding,
        metadata: BaseItemDto,
        sideLoadedSubs: List<SubtitleSpec>,
        startPosition: Long
    ) {
        val methodLabel = if (result.isDirectStream) "Direct Stream" else "Transcoding"
        stateManager.updateState { it.copy(
            isDirectPlaying = false,
            isDirectStreaming = result.isDirectStream,
            isTranscoding = !result.isDirectStream,
            transcodingReason = if (result.isDirectStream) null else result.reason,
            playbackMethod = methodLabel
        ) }
        currentPlaybackSessionId = result.playSessionId ?: UUID.randomUUID().toString()

        val lowerUrl = result.url.lowercase(Locale.ROOT)
        val mimeType = when {
            lowerUrl.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            lowerUrl.contains(".mpd") -> MimeTypes.APPLICATION_MPD
            else -> null
        }

        preparePlayer(result.url, metadata.name, sideLoadedSubs, mimeType, startPosition)
    }

    private suspend fun preparePlayer(
        url: String,
        title: String?,
        subtitles: List<SubtitleSpec>,
        mimeType: String?,
        startPosition: Long
    ) {
        withContext(Dispatchers.Main) {
            val player = exoPlayer ?: return@withContext
            val mediaItem = MediaItemFactory.build(
                videoUrl = url,
                title = title,
                sideLoadedSubs = subtitles,
                mimeTypeHint = mimeType
            )
            currentPreparedMediaItem = mediaItem
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            if (startPosition > 0) player.seekTo(startPosition)
        }
    }

    suspend fun releasePlayer(reportStop: Boolean = true) {
        withContext(Dispatchers.Main) {
            stopPositionUpdates()
            exoPlayer?.let { p ->
                try {
                    p.stop()
                    p.clearVideoSurface()
                    p.release()
                } catch (_: Exception) {}
            }
            exoPlayer = null
            trackSelector = null
            adaptiveBitrateMonitor.stopMonitoring()
            playbackProgressManager.stopTracking(reportStop)
        }
    }

    fun startPositionUpdates(scope: CoroutineScope) {
        if (positionJob?.isActive == true) return
        val player = exoPlayer ?: return
        positionJob = scope.launch(Dispatchers.Main) {
            while (true) {
                val duration = if (player.duration > 0) player.duration else stateManager.playerState.value.duration
                stateManager.updateState { it.copy(
                    currentPosition = player.currentPosition,
                    bufferedPosition = player.bufferedPosition,
                    duration = duration
                ) }
                playbackProgressManager.updateProgress(player.currentPosition, duration)
                delay(500)
            }
        }
    }

    fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    fun handlePlaybackStateChanged(playbackState: Int, previousPlaybackState: Int, scope: CoroutineScope) {
        val player = exoPlayer ?: return
        
        if (playbackState == Player.STATE_BUFFERING && previousPlaybackState == Player.STATE_READY) {
            savedPositionBeforeFlush = player.currentPosition
            wasBufferingBeforeReady = true
        }

        if (playbackState == Player.STATE_READY && wasBufferingBeforeReady) {
            restorePositionIfNeeded(player)
            wasBufferingBeforeReady = false
            savedPositionBeforeFlush = null
        }

        val rawDuration = player.duration
        val validDuration = if (rawDuration > 0) rawDuration else stateManager.playerState.value.duration

        stateManager.updateState { it.copy(
            isLoading = playbackState == Player.STATE_BUFFERING,
            isPlaying = player.isPlaying,
            duration = validDuration,
            bufferedPosition = player.bufferedPosition,
            currentPosition = player.currentPosition,
            hasEnded = playbackState == Player.STATE_ENDED
        ) }

        if (playbackState == Player.STATE_READY) {
            val currentQuality = stateManager.playerState.value.transcodingReason?.let {
                com.rpeters.jellyfin.data.preferences.TranscodingQuality.AUTO
            } ?: com.rpeters.jellyfin.data.preferences.TranscodingQuality.MAXIMUM
            adaptiveBitrateMonitor.startMonitoring(player, scope, currentQuality, stateManager.playerState.value.isTranscoding)
        }
    }

    private fun restorePositionIfNeeded(player: ExoPlayer) {
        val savedPos = savedPositionBeforeFlush ?: return
        val currentPos = player.currentPosition
        val currentTime = System.currentTimeMillis()

        if (savedPos > 5000 && currentPos < 5000) {
            val canAttempt = positionRestoreAttempts < maxRestoreAttempts && 
                (currentTime - lastRestoreAttemptTime) > restoreAttemptCooldownMs
            
            if (canAttempt) {
                positionRestoreAttempts++
                lastRestoreAttemptTime = currentTime
                player.seekTo(savedPos)
            }
        }
    }

    fun handlePlayerError(error: PlaybackException, scope: CoroutineScope, metadata: BaseItemDto?, itemId: String?) {
        SecureLogger.e("VideoPlayerPlayback", "Player Error: ${error.message}", error)
        
        val currentPos = exoPlayer?.currentPosition ?: 0L
        if (currentPos > 0) savedPositionBeforeFlush = currentPos

        // Fallback logic
        if (stateManager.playerState.value.isDirectPlaying && !hasAttemptedTranscodingFallback && metadata != null) {
            hasAttemptedTranscodingFallback = true
            scope.launch { retryWithTranscoding(metadata, currentPos, scope) }
            return
        }

        if (!hasAttemptedOfflineFallback && itemId != null && offlinePlaybackManager.isOfflinePlaybackAvailable(itemId)) {
            hasAttemptedOfflineFallback = true
            scope.launch { retryWithOfflineFallback(itemId, metadata?.name ?: "Unknown", currentPos, scope) }
            return
        }

        stateManager.updateState { it.copy(
            error = "Playback error: ${error.message}",
            isLoading = false
        ) }
    }

    private suspend fun retryWithTranscoding(metadata: BaseItemDto, position: Long, scope: CoroutineScope) {
        SecureLogger.i("VideoPlayerPlayback", "Retrying with transcoding fallback")
        releasePlayer(reportStop = false)
        playerListener?.let { initializeExoPlayer(it) }

        when (
            val result = enhancedPlaybackManager.getTranscodingPlaybackUrl(
                item = metadata,
                audioStreamIndex = stateManager.playerState.value.selectedAudioTrack?.format?.id?.toIntOrNull(),
                subtitleStreamIndex = stateManager.playerState.value.selectedSubtitleTrack?.format?.id?.toIntOrNull(),
            )
        ) {
            is PlaybackResult.DirectPlay -> {
                handleDirectPlayResult(result, metadata, currentPreparedSubtitleSpecs, position)
                playbackProgressManager.startTracking(
                    itemId = metadata.id.toString(),
                    scope = scope,
                    sessionId = currentPlaybackSessionId ?: UUID.randomUUID().toString(),
                    mediaSourceId = currentPreparedMediaSourceId,
                    playMethod = PlayMethod.DIRECT_PLAY,
                )
            }
            is PlaybackResult.Transcoding -> {
                handleTranscodingResult(result, metadata, emptyList(), position)
                playbackProgressManager.startTracking(
                    itemId = metadata.id.toString(),
                    scope = scope,
                    sessionId = currentPlaybackSessionId ?: UUID.randomUUID().toString(),
                    mediaSourceId = currentPreparedMediaSourceId,
                    playMethod = if (result.isDirectStream) PlayMethod.DIRECT_STREAM else PlayMethod.TRANSCODE,
                )
            }
            is PlaybackResult.Error -> {
                stateManager.updateState {
                    it.copy(
                        error = "Transcoding fallback failed: ${result.message}",
                        isLoading = false,
                    )
                }
            }
        }
    }

    private suspend fun retryWithOfflineFallback(itemId: String, itemName: String, position: Long, scope: CoroutineScope) {
        SecureLogger.i("VideoPlayerPlayback", "Retrying with offline fallback")
        releasePlayer(reportStop = false)
        initializeExoPlayer(object : Player.Listener {})
        startPlayback(itemId, itemName, position, null, emptyList(), forceOffline = true, scope = scope)
    }

    fun isDeviceOnline(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
