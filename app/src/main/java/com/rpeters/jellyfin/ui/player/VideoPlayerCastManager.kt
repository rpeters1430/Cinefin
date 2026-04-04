package com.rpeters.jellyfin.ui.player

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.ui.player.cast.CastSessionEndReason
import com.rpeters.jellyfin.ui.player.cast.CastState
import com.rpeters.jellyfin.utils.AnalyticsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

/**
 * Handles Google Cast and DLNA integration for the video player.
 */
@UnstableApi
class VideoPlayerCastManager @Inject constructor(
    private val castManager: CastManager,
    private val stateManager: VideoPlayerStateManager,
    private val analytics: AnalyticsHelper
) {
    private data class PendingLocalResume(
        val itemId: String,
        val itemName: String,
        val resumePosition: Long,
        val message: String?,
    )

    private var castPositionJob: Job? = null
    private var reconnectGraceJob: Job? = null
    private var pendingLocalResume: PendingLocalResume? = null
    private var hasSentCastLoad = false
    private var awaitingRemotePlaybackStart = false
    private var localPlaybackReleasedForCast = false
    private var releasePlayerCallback: (() -> Unit)? = null

    companion object {
        private const val CONNECTION_LOSS_RESUME_DELAY_MS = 3_500L

        @JvmStatic
        internal fun shouldResumeLocallyAfterCastEnds(
            wasCastConnected: Boolean,
            localPlaybackReleasedForCast: Boolean,
            isConnected: Boolean,
            sessionEndReason: CastSessionEndReason,
        ): Boolean {
            if (isConnected || sessionEndReason == CastSessionEndReason.USER_DISCONNECTED) return false
            return wasCastConnected || localPlaybackReleasedForCast
        }

        @JvmStatic
        internal fun shouldDelayLocalResume(sessionEndReason: CastSessionEndReason): Boolean {
            return sessionEndReason == CastSessionEndReason.CONNECTION_LOST
        }

        @JvmStatic
        internal fun playerMessageForCastEnd(
            sessionEndReason: CastSessionEndReason,
            didResumeLocally: Boolean,
        ): String? {
            return when (sessionEndReason) {
                CastSessionEndReason.CONNECTION_LOST -> {
                    if (didResumeLocally) {
                        "Cast connection lost. Playback resumed on this device."
                    } else {
                        "Cast connection lost."
                    }
                }
                CastSessionEndReason.LOAD_FAILED -> {
                    if (didResumeLocally) {
                        "Couldn't start casting. Playback continued on this device."
                    } else {
                        "Couldn't start casting."
                    }
                }
                CastSessionEndReason.USER_DISCONNECTED,
                CastSessionEndReason.NONE,
                -> null
            }
        }
    }

    fun initialize(
        scope: CoroutineScope,
        onStartPlayback: (itemId: String, itemName: String, position: Long) -> Unit,
        onReleasePlayer: () -> Unit,
        onStartCasting: (position: Long) -> Unit,
    ) {
        releasePlayerCallback = onReleasePlayer
        castManager.initialize()
        scope.launch {
            castManager.castState.collect { castState ->
                handleCastState(castState, onStartPlayback, onReleasePlayer, onStartCasting, scope)
            }
        }
    }

    private fun handleCastState(
        castState: CastState,
        onStartPlayback: (itemId: String, itemName: String, position: Long) -> Unit,
        onReleasePlayer: () -> Unit,
        onStartCasting: (position: Long) -> Unit,
        scope: CoroutineScope
    ) {
        val currentState = stateManager.playerState.value
        val wasCastConnected = currentState.isCastConnected
        val isConnecting = castState.isConnected && !wasCastConnected && !localPlaybackReleasedForCast
        val shouldExposeRemoteUi = castState.isConnected && !awaitingRemotePlaybackStart

        if (castState.isConnected) {
            reconnectGraceJob?.cancel()
            reconnectGraceJob = null
            pendingLocalResume = null
        }

        stateManager.updateState { it.copy(
            isCastAvailable = castState.isAvailable,
            isCasting = castState.isCasting && shouldExposeRemoteUi,
            isCastConnected = shouldExposeRemoteUi,
            castDeviceName = castState.deviceName,
            isCastPlaying = castState.isRemotePlaying,
            castPosition = castState.currentPosition,
            castDuration = castState.duration,
            castVolume = castState.volume,
            availableCastDevices = castState.availableDevices,
            castDiscoveryState = castState.discoveryState,
            showCastDialog = if (castState.isConnected) false else it.showCastDialog,
            error = castState.error ?: it.error
        ) }

        if (isConnecting && !hasSentCastLoad) {
            val startPosition = currentState.currentPosition
            awaitingRemotePlaybackStart = true
            onStartCasting(startPosition)
        }

        if ((wasCastConnected || localPlaybackReleasedForCast) && !castState.isConnected) {
            hasSentCastLoad = false
            awaitingRemotePlaybackStart = false
            val shouldResumeLocally = shouldResumeLocallyAfterCastEnds(
                wasCastConnected = wasCastConnected,
                localPlaybackReleasedForCast = localPlaybackReleasedForCast,
                isConnected = castState.isConnected,
                sessionEndReason = castState.sessionEndReason,
            )
            localPlaybackReleasedForCast = false
            val resumePosition = castState.currentPosition
            val itemId = currentState.itemId
            val itemName = currentState.itemName
            val playerMessage = playerMessageForCastEnd(
                sessionEndReason = castState.sessionEndReason,
                didResumeLocally = shouldResumeLocally && itemId.isNotEmpty(),
            )
            val localResume = PendingLocalResume(
                itemId = itemId,
                itemName = itemName,
                resumePosition = resumePosition,
                message = playerMessage,
            )
            if (shouldResumeLocally && itemId.isNotEmpty()) {
                if (shouldDelayLocalResume(castState.sessionEndReason)) {
                    scheduleDelayedLocalResume(scope, localResume, onStartPlayback)
                } else {
                    performLocalResume(localResume, onStartPlayback)
                }
            } else if (playerMessage != null) {
                stateManager.updateState { it.copy(error = playerMessage) }
            }
        }

        if (awaitingRemotePlaybackStart && castState.error != null) {
            awaitingRemotePlaybackStart = false
            localPlaybackReleasedForCast = false
            hasSentCastLoad = false
            castManager.disconnectCastSession(userInitiated = false)
        }

        if (castState.isCasting || awaitingRemotePlaybackStart) {
            startCastPositionUpdates(scope)
        } else {
            stopCastPositionUpdates()
        }
    }

    private fun startCastPositionUpdates(scope: CoroutineScope) {
        if (castPositionJob?.isActive == true) return
        castPositionJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                castManager.updatePlaybackState()
                delay(1000)
            }
        }
    }

    private fun stopCastPositionUpdates() {
        castPositionJob?.cancel()
        castPositionJob = null
    }

    private fun scheduleDelayedLocalResume(
        scope: CoroutineScope,
        pendingResume: PendingLocalResume,
        onStartPlayback: (itemId: String, itemName: String, position: Long) -> Unit,
    ) {
        reconnectGraceJob?.cancel()
        pendingLocalResume = pendingResume
        stateManager.updateState {
            it.copy(error = "Cast connection interrupted. Reconnecting to receiver...")
        }
        reconnectGraceJob = scope.launch(Dispatchers.Main) {
            delay(CONNECTION_LOSS_RESUME_DELAY_MS)
            val resume = pendingLocalResume ?: return@launch
            pendingLocalResume = null
            reconnectGraceJob = null
            performLocalResume(resume, onStartPlayback)
        }
    }

    private fun performLocalResume(
        pendingResume: PendingLocalResume,
        onStartPlayback: (itemId: String, itemName: String, position: Long) -> Unit,
    ) {
        if (pendingResume.itemId.isNotEmpty()) {
            onStartPlayback(pendingResume.itemId, pendingResume.itemName, pendingResume.resumePosition)
        }
        pendingResume.message?.let { message ->
            stateManager.updateState { it.copy(error = message) }
        }
    }

    fun startCasting(
        mediaItem: MediaItem,
        item: BaseItemDto,
        sideLoadedSubs: List<SubtitleSpec>,
        startPositionMs: Long,
        playSessionId: String?,
        mediaSourceId: String?
    ) {
        analytics.logCastEvent(stateManager.playerState.value.castDeviceName ?: "Unknown Device")
        castManager.startCasting(
            mediaItem = mediaItem,
            item = item,
            sideLoadedSubs = sideLoadedSubs,
            startPositionMs = startPositionMs,
            playSessionId = playSessionId,
            mediaSourceId = mediaSourceId,
            onStarted = {
                if (!localPlaybackReleasedForCast) {
                    localPlaybackReleasedForCast = true
                    awaitingRemotePlaybackStart = false
                    releasePlayerCallback?.invoke()
                }
            },
            onError = {
                awaitingRemotePlaybackStart = false
                localPlaybackReleasedForCast = false
                hasSentCastLoad = false
            },
        )
        hasSentCastLoad = true
    }

    fun stopDiscovery() = castManager.stopDiscovery()
    fun startDiscovery() = castManager.startDiscovery()
    suspend fun awaitInitialization() = castManager.awaitInitialization()
    fun connectToDevice(deviceName: String) = castManager.connectToDevice(deviceName)
    fun disconnectCastSession() = castManager.disconnectCastSession()
    fun pauseCasting() = castManager.pauseCasting()
    fun resumeCasting() = castManager.resumeCasting()
    fun stopCasting() {
        castManager.stopCasting()
        hasSentCastLoad = false
        awaitingRemotePlaybackStart = false
        localPlaybackReleasedForCast = false
        reconnectGraceJob?.cancel()
        reconnectGraceJob = null
        pendingLocalResume = null
        stopCastPositionUpdates()
    }
    fun seekTo(positionMs: Long) = castManager.seekTo(positionMs)
    fun setVolume(volume: Float) = castManager.setVolume(volume)
}
