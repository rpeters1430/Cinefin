package com.rpeters.jellyfin.ui.player.audio

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.rpeters.jellyfin.ui.player.PlaybackProgressManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@UnstableApi
@Singleton
class AudioServiceConnection @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val playbackProgressManager: PlaybackProgressManager,
) {

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressUpdateJob: Job? = null
    private var currentTrackingItemId: String? = null

    private val _playbackState = kotlinx.coroutines.flow.MutableStateFlow(AudioPlaybackState())
    private val _queueState = kotlinx.coroutines.flow.MutableStateFlow<List<MediaItem>>(emptyList())

    private val controllerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            mediaController?.let { controller ->
                updateQueue(controller)
                updatePlaybackState(controller)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Track changed - report progress for previous item and start tracking new one
            controllerScope.launch {
                handleTrackChange(mediaItem)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startProgressUpdates()
            } else {
                stopProgressUpdates()
            }
        }
    }

    val playbackState: kotlinx.coroutines.flow.StateFlow<AudioPlaybackState>
        get() = _playbackState

    val queueState: kotlinx.coroutines.flow.StateFlow<List<MediaItem>>
        get() = _queueState

    fun playNow(mediaItem: MediaItem) {
        controllerScope.launch {
            val controller = ensureController()
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
    }

    fun enqueue(mediaItem: MediaItem) {
        controllerScope.launch {
            val controller = ensureController()
            controller.addMediaItem(mediaItem)
            if (controller.playbackState == Player.STATE_IDLE) {
                controller.prepare()
            }
            updateQueue(controller)
        }
    }

    fun togglePlayPause() {
        controllerScope.launch {
            val controller = ensureController()
            if (controller.playWhenReady) {
                controller.pause()
            } else {
                controller.play()
            }
        }
    }

    fun toggleShuffle() {
        controllerScope.launch {
            val controller = ensureController()
            controller.shuffleModeEnabled = !controller.shuffleModeEnabled
            updatePlaybackState(controller)
        }
    }

    fun skipToNext() {
        controllerScope.launch {
            val controller = ensureController()
            controller.seekToNextMediaItem()
        }
    }

    fun skipToPrevious() {
        controllerScope.launch {
            val controller = ensureController()
            controller.seekToPreviousMediaItem()
        }
    }

    fun toggleRepeat() {
        controllerScope.launch {
            val controller = ensureController()
            controller.repeatMode = when (controller.repeatMode) {
                androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
                androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
                androidx.media3.common.Player.REPEAT_MODE_ONE -> androidx.media3.common.Player.REPEAT_MODE_OFF
                else -> androidx.media3.common.Player.REPEAT_MODE_OFF
            }
            updatePlaybackState(controller)
        }
    }

    fun seekTo(positionMs: Long) {
        controllerScope.launch {
            val controller = ensureController()
            controller.seekTo(positionMs)
        }
    }

    fun seekForward(amountMs: Long) {
        controllerScope.launch {
            val controller = ensureController()
            val newPosition = (controller.currentPosition + amountMs).coerceAtMost(controller.duration)
            controller.seekTo(newPosition)
        }
    }

    fun seekBackward(amountMs: Long) {
        controllerScope.launch {
            val controller = ensureController()
            val newPosition = (controller.currentPosition - amountMs).coerceAtLeast(0)
            controller.seekTo(newPosition)
        }
    }

    fun removeFromQueue(index: Int) {
        controllerScope.launch {
            val controller = ensureController()
            if (index >= 0 && index < controller.mediaItemCount) {
                controller.removeMediaItem(index)
                updateQueue(controller)
            }
        }
    }

    fun clearQueue() {
        controllerScope.launch {
            val controller = ensureController()
            controller.clearMediaItems()
            updateQueue(controller)
        }
    }

    fun skipToQueueItem(index: Int) {
        controllerScope.launch {
            val controller = ensureController()
            if (index >= 0 && index < controller.mediaItemCount) {
                controller.seekToDefaultPosition(index)
            }
        }
    }

    fun getCurrentPosition(): Long {
        return mediaController?.currentPosition ?: 0L
    }

    fun getDuration(): Long {
        return mediaController?.duration ?: 0L
    }

    fun getRepeatMode(): Int {
        return mediaController?.repeatMode ?: androidx.media3.common.Player.REPEAT_MODE_OFF
    }

    suspend fun ensureController(): MediaController {
        mediaController?.let { return it }

        return withContext(Dispatchers.Main) {
            val token = SessionToken(appContext, ComponentName(appContext, AudioService::class.java))
            val future = MediaController.Builder(appContext, token).buildAsync()
            controllerFuture = future
            try {
                val controller = future.await(appContext)
                controller.addListener(controllerListener)
                mediaController = controller
                _playbackState.value = _playbackState.value.copy(isConnected = true)
                updatePlaybackState(controller)
                updateQueue(controller)
                controller
            } catch (exception: Exception) {
                controllerFuture = null
                throw exception
            }
        }
    }

    fun refreshState() {
        mediaController?.let { controller ->
            updatePlaybackState(controller)
            updateQueue(controller)
        }
    }

    fun releaseController() {
        // Stop progress tracking
        stopProgressUpdates()
        controllerScope.launch {
            if (currentTrackingItemId != null) {
                playbackProgressManager.stopTracking()
                currentTrackingItemId = null
            }
        }

        mediaController?.let { controller ->
            try {
                controller.removeListener(controllerListener)
                controller.release()
            } catch (e: CancellationException) {
                throw e
            }
        }
        mediaController = null
        controllerFuture?.cancel(true)
        controllerFuture = null
        _playbackState.value = AudioPlaybackState(isConnected = false)
        _queueState.value = emptyList()
    }

    fun shutdown() {
        releaseController()
        controllerScope.cancel()
    }

    // No-op: Do not explicitly start a foreground service. The MediaController binding
    // will start the MediaSessionService as needed, and Media3 will manage foreground state.
    private fun startService() { /* intentionally left blank */ }

    private fun updatePlaybackState(controller: MediaController) {
        val isPlaying = controller.playWhenReady && controller.playbackState == Player.STATE_READY
        _playbackState.value = AudioPlaybackState(
            isConnected = true,
            isPlaying = isPlaying,
            currentMediaItem = controller.currentMediaItem,
            shuffleEnabled = controller.shuffleModeEnabled,
            repeatMode = controller.repeatMode,
            currentPosition = controller.currentPosition,
            duration = controller.duration,
        )
    }

    private fun updateQueue(controller: MediaController) {
        val items = buildList(controller.mediaItemCount) {
            repeat(controller.mediaItemCount) { index ->
                add(controller.getMediaItemAt(index))
            }
        }
        _queueState.value = items
    }

    private suspend fun handleTrackChange(newMediaItem: MediaItem?) {
        // Stop tracking previous item
        if (currentTrackingItemId != null) {
            playbackProgressManager.stopTracking()
        }

        // Start tracking new item
        val itemId = newMediaItem?.mediaId
        if (!itemId.isNullOrBlank()) {
            currentTrackingItemId = itemId
            playbackProgressManager.startTracking(itemId, controllerScope)
            Log.d(TAG, "Started tracking audio progress for item: $itemId")
        } else {
            currentTrackingItemId = null
        }
    }

    private fun startProgressUpdates() {
        if (progressUpdateJob?.isActive == true) return

        progressUpdateJob = controllerScope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    val position = controller.currentPosition
                    val duration = controller.duration
                    if (duration > 0 && currentTrackingItemId != null) {
                        playbackProgressManager.updateProgress(position, duration)
                    }
                }
                delay(PROGRESS_UPDATE_INTERVAL)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null

        // Report final position when pausing
        mediaController?.let { controller ->
            val position = controller.currentPosition
            val duration = controller.duration
            if (duration > 0 && currentTrackingItemId != null) {
                playbackProgressManager.updateProgress(position, duration)
            }
        }
    }

    companion object {
        private const val PROGRESS_UPDATE_INTERVAL = 5000L // 5 seconds
    }
}

data class AudioPlaybackState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val currentMediaItem: MediaItem? = null,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = androidx.media3.common.Player.REPEAT_MODE_OFF,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
)

private suspend fun <T> ListenableFuture<T>.await(context: Context): T =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                try {
                    continuation.resume(get())
                } catch (e: CancellationException) {
                    throw e
                }
            },
            ContextCompat.getMainExecutor(context),
        )

        continuation.invokeOnCancellation { cancel(true) }
    }

private const val TAG = "AudioServiceConnection"
