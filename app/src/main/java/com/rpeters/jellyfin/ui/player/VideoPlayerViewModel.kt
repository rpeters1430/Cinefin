package com.rpeters.jellyfin.ui.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@UnstableApi
enum class AspectRatioMode(val label: String, val resizeMode: Int) {
    FIT("Fit", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Fill", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    FIXED_WIDTH("Fixed Width", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),
    FIXED_HEIGHT("Fixed Height", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT),
}

@UnstableApi
data class TrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val format: androidx.media3.common.Format,
    val isSelected: Boolean,
    val displayName: String,
)

data class VideoQuality(
    val id: String,
    val label: String,
    val bitrate: Int,
    val width: Int,
    val height: Int,
)

@UnstableApi
data class VideoPlayerState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val error: String? = null,
    val itemId: String = "",
    val itemName: String = "",
    val aspectRatio: Float = 16f / 9f,
    val selectedAspectRatio: AspectRatioMode = AspectRatioMode.FILL,
    val availableAspectRatios: List<AspectRatioMode> = AspectRatioMode.values().toList(),
    val isControlsVisible: Boolean = true,
    val showSubtitleDialog: Boolean = false,
    val showCastDialog: Boolean = false,
    val availableCastDevices: List<String> = emptyList(),
    val availableQualities: List<VideoQuality> = emptyList(),
    val selectedQuality: VideoQuality? = null,
    val isCasting: Boolean = false,
    val castDeviceName: String? = null,
    val availableAudioTracks: List<TrackInfo> = emptyList(),
    val selectedAudioTrack: TrackInfo? = null,
    val availableSubtitleTracks: List<TrackInfo> = emptyList(),
    val selectedSubtitleTrack: TrackInfo? = null,
)

@UnstableApi
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: JellyfinRepository,
    private val castManager: CastManager,
) : ViewModel() {

    init {
        castManager.initialize()
    }

    private val _playerState = MutableStateFlow(VideoPlayerState())
    val playerState: StateFlow<VideoPlayerState> = _playerState.asStateFlow()

    var exoPlayer: ExoPlayer? = null
        private set

    private var currentItemId: String? = null
    private var currentItemName: String? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateString = when (playbackState) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> "UNKNOWN($playbackState)"
            }
            Log.d("VideoPlayer", "State: $stateString, isPlaying: ${exoPlayer?.isPlaying}")

            _playerState.value = _playerState.value.copy(
                isLoading = playbackState == Player.STATE_BUFFERING,
                isPlaying = exoPlayer?.isPlaying == true,
            )
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d("VideoPlayer", "Playing changed: $isPlaying")
            _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("VideoPlayer", "Error: ${error.message}", error)
            _playerState.value = _playerState.value.copy(
                error = "Playback error: ${error.message}",
                isLoading = false,
            )
        }
    }

    fun initializePlayer(itemId: String, itemName: String, startPosition: Long) {
        Log.d("VideoPlayer", "Initializing player for: $itemName")

        currentItemId = itemId
        currentItemName = itemName

        _playerState.value = _playerState.value.copy(
            itemId = itemId,
            itemName = itemName,
            isLoading = true,
            error = null,
        )

        viewModelScope.launch {
            try {
                // Get playback info with our device profile for direct play
                val playbackInfo = repository.getPlaybackInfo(itemId)

                // Find the best media source for direct play
                val mediaSource = playbackInfo.mediaSources?.find { source ->
                    source.supportsDirectPlay == true || source.supportsDirectStream == true
                } ?: playbackInfo.mediaSources?.firstOrNull()

                if (mediaSource == null) {
                    throw Exception("No media source available")
                }

                // Choose the best stream URL
                val streamUrl = when {
                    // Direct play - use original file with static=true
                    mediaSource.supportsDirectPlay == true -> {
                        val container = mediaSource.container
                        Log.d("VideoPlayer", "Using direct play with container: $container")
                        "${repository.getCurrentServer()?.url}/Videos/$itemId/stream.$container?static=true&mediaSourceId=${mediaSource.id}&api_key=${repository.getCurrentServer()?.accessToken}"
                    }
                    // Direct stream - server remuxes without transcoding
                    mediaSource.supportsDirectStream == true -> {
                        Log.d("VideoPlayer", "Using direct stream")
                        repository.getDirectStreamUrl(itemId)
                    }
                    // Fallback to transcoded stream
                    else -> {
                        Log.d("VideoPlayer", "Falling back to transcoded stream")
                        repository.getStreamUrl(itemId)
                    }
                }

                if (streamUrl.isNullOrEmpty()) {
                    throw Exception("No stream URL available")
                }

                Log.d("VideoPlayer", "Stream URL: $streamUrl")
                Log.d("VideoPlayer", "Media source supports direct play: ${mediaSource.supportsDirectPlay}")
                Log.d("VideoPlayer", "Media source supports direct stream: ${mediaSource.supportsDirectStream}")

                withContext(Dispatchers.Main) {
                    // Create ExoPlayer with FFmpeg extension renderer support for Vorbis
                    val renderersFactory = DefaultRenderersFactory(context)
                        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

                    exoPlayer = ExoPlayer.Builder(context)
                        .setRenderersFactory(renderersFactory)
                        .build()

                    // Add listener
                    exoPlayer?.addListener(playerListener)

                    // Create media item
                    val mediaItem = MediaItem.fromUri(streamUrl)

                    // Set media and prepare
                    exoPlayer?.setMediaItem(mediaItem)
                    exoPlayer?.prepare()

                    // Seek to start position if specified
                    if (startPosition > 0) {
                        exoPlayer?.seekTo(startPosition)
                    }

                    Log.d("VideoPlayer", "Player prepared successfully")
                }
            } catch (e: Exception) {
                Log.e("VideoPlayer", "Init failed: ${e.message}", e)
                _playerState.value = _playerState.value.copy(
                    error = "Failed to initialize: ${e.message}",
                    isLoading = false,
                )
            }
        }
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return

        Log.d("VideoPlayer", "Toggle play/pause. Current state: playing=${player.isPlaying}, playWhenReady=${player.playWhenReady}")

        if (player.isPlaying) {
            player.pause()
            Log.d("VideoPlayer", "Paused")
        } else {
            player.play()
            Log.d("VideoPlayer", "Play requested")
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        Log.d("VideoPlayer", "Seeked to: $positionMs")
    }

    fun startPlayback() {
        exoPlayer?.play()
        Log.d("VideoPlayer", "Start playback requested")
    }

    fun pausePlayback() {
        exoPlayer?.pause()
        Log.d("VideoPlayer", "Pause requested")
    }

    fun releasePlayer() {
        Log.d("VideoPlayer", "Releasing player")
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
    }

    // Placeholder methods for UI compatibility
    fun changeQuality(quality: VideoQuality) { /* Not implemented yet */ }
    fun changeAspectRatio(aspectRatio: AspectRatioMode) {
        _playerState.value = _playerState.value.copy(selectedAspectRatio = aspectRatio)
    }
    fun showCastDialog() {
        val devices = castManager.discoverDevices()
        _playerState.value = _playerState.value.copy(
            availableCastDevices = devices,
            showCastDialog = true,
        )
    }
    fun showSubtitleDialog() { /* Not implemented yet */ }
    fun selectAudioTrack(track: TrackInfo) { /* Not implemented yet */ }
    fun selectSubtitleTrack(track: TrackInfo?) { /* Not implemented yet */ }
    fun hideSubtitleDialog() {
        _playerState.value = _playerState.value.copy(showSubtitleDialog = false)
    }
    fun selectCastDevice(deviceName: String) {
        val connected = castManager.connectToDevice(deviceName)
        _playerState.value = _playerState.value.copy(
            isCasting = connected,
            castDeviceName = if (connected) deviceName else null,
            showCastDialog = false,
        )
    }
    fun hideCastDialog() {
        _playerState.value = _playerState.value.copy(showCastDialog = false)
    }
}
