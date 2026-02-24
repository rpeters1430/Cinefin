package com.rpeters.jellyfin.ui.player.cast

import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.utils.SecureLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles remote playback controls (play, pause, seek, volume)
 * and processes status updates from the receiver.
 */
@UnstableApi
@Singleton
class CastPlaybackController @Inject constructor(
    private val stateStore: CastStateStore,
) {
    private var pendingSeekPosition: Long = -1L
    private var triggerUpdate: (() -> Unit)? = null

    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            triggerUpdate?.invoke()
        }
    }

    /**
     * Registers for status updates from the current session.
     */
    fun registerCallback(client: RemoteMediaClient?, onUpdate: () -> Unit) {
        this.triggerUpdate = onUpdate
        client?.unregisterCallback(remoteMediaClientCallback)
        client?.registerCallback(remoteMediaClientCallback)
    }

    /**
     * Unregisters status updates.
     */
    fun unregisterCallback(client: RemoteMediaClient?) {
        client?.unregisterCallback(remoteMediaClientCallback)
    }

    fun setPendingSeek(positionMs: Long) {
        pendingSeekPosition = positionMs
    }

    fun pause(castContext: CastContext?) {
        castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.pause()
    }

    fun resume(castContext: CastContext?) {
        castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.play()
    }

    fun stop(castContext: CastContext?) {
        castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.stop()
    }

    fun seekTo(castContext: CastContext?, positionMs: Long) {
        val client = castContext?.sessionManager?.currentCastSession?.remoteMediaClient ?: return
        val seekOptions = MediaSeekOptions.Builder()
            .setPosition(positionMs)
            .build()
        client.seek(seekOptions)
        stateStore.update { it.copy(currentPosition = positionMs) }
    }

    fun setVolume(castContext: CastContext?, volume: Float) {
        val session = castContext?.sessionManager?.currentCastSession ?: return
        session.volume = volume.toDouble().coerceIn(0.0, 1.0)
        stateStore.update { it.copy(volume = volume.coerceIn(0f, 1f)) }
    }

    fun updatePlaybackStateFromClient(castContext: CastContext?) {
        val client = castContext?.sessionManager?.currentCastSession?.remoteMediaClient ?: return
        val status = client.mediaStatus

        // Handle Pending Seek
        if (pendingSeekPosition > 0L && status != null) {
            when (status.playerState) {
                MediaStatus.PLAYER_STATE_PLAYING,
                MediaStatus.PLAYER_STATE_PAUSED,
                MediaStatus.PLAYER_STATE_BUFFERING -> {
                    val seekOptions = MediaSeekOptions.Builder()
                        .setPosition(pendingSeekPosition)
                        .setResumeState(MediaSeekOptions.RESUME_STATE_PLAY)
                        .build()
                    client.seek(seekOptions)
                    pendingSeekPosition = -1L
                }
            }
        }

        // Update State Store
        stateStore.update { state ->
            state.copy(
                currentPosition = client.approximateStreamPosition,
                duration = client.mediaInfo?.streamDuration ?: state.duration,
                isRemotePlaying = status?.playerState == MediaStatus.PLAYER_STATE_PLAYING,
                volume = (castContext.sessionManager.currentCastSession?.volume ?: 1.0).toFloat()
            )
        }
    }
}
