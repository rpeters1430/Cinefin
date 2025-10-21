package com.rpeters.jellyfin.ui.player.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@UnstableApi
@Singleton
class AudioServiceConnection @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _playbackState = kotlinx.coroutines.flow.MutableStateFlow(AudioPlaybackState())
    private val _queueState = kotlinx.coroutines.flow.MutableStateFlow<List<MediaItem>>(emptyList())

    private val controllerListener = object : MediaController.Listener {
        override fun onConnected(controller: MediaController) {
            _playbackState.value = _playbackState.value.copy(isConnected = true)
            updatePlaybackState(controller)
            updateQueue(controller)
        }

        override fun onDisconnected(controller: MediaController) {
            _playbackState.value = AudioPlaybackState(isConnected = false)
            _queueState.value = emptyList()
            releaseController()
        }

        override fun onEvents(controller: MediaController, events: Player.Events) {
            if (events.contains(Player.EVENT_MEDIA_ITEMS_CHANGED) ||
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
            ) {
                updateQueue(controller)
            }
            if (
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)
            ) {
                updatePlaybackState(controller)
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

    suspend fun ensureController(): MediaController {
        mediaController?.let { return it }

        return withContext(Dispatchers.Main) {
            startService()
            val token = SessionToken(appContext, ComponentName(appContext, AudioService::class.java))
            val future = MediaController.Builder(appContext, token).buildAsync()
            controllerFuture = future
            val controller = future.await(appContext)
            controller.addListener(controllerListener)
            mediaController = controller
            controller
        }
    }

    fun refreshState() {
        mediaController?.let { controller ->
            updatePlaybackState(controller)
            updateQueue(controller)
        }
    }

    fun releaseController() {
        mediaController?.let { controller ->
            controller.removeListener(controllerListener)
            controller.release()
        }
        mediaController = null
        controllerFuture?.cancel(true)
        controllerFuture = null
    }

    fun shutdown() {
        releaseController()
        controllerScope.cancel()
    }

    private fun startService() {
        val intent = Intent(appContext, AudioService::class.java)
        ContextCompat.startForegroundService(appContext, intent)
    }

    private fun updatePlaybackState(controller: MediaController) {
        val isPlaying = controller.playWhenReady && controller.playbackState == Player.STATE_READY
        _playbackState.value = AudioPlaybackState(
            isConnected = true,
            isPlaying = isPlaying,
            currentMediaItem = controller.currentMediaItem,
            shuffleEnabled = controller.shuffleModeEnabled,
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
}

data class AudioPlaybackState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val currentMediaItem: MediaItem? = null,
    val shuffleEnabled: Boolean = false,
)

private suspend fun <T> ListenableFuture<T>.await(context: Context): T =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                try {
                    continuation.resume(get())
                } catch (exception: Exception) {
                    continuation.resumeWithException(exception)
                }
            },
            ContextCompat.getMainExecutor(context),
        )

        continuation.invokeOnCancellation { cancel(true) }
    }
