package com.example.jellyfinandroid.ui.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.jellyfinandroid.BuildConfig
import com.example.jellyfinandroid.data.repository.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

@UnstableApi
data class VideoPlayerState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val availableQualities: List<VideoQuality> = emptyList(),
    val selectedQuality: VideoQuality? = null,
    val isControlsVisible: Boolean = true,
    val isCasting: Boolean = false,
    val castDeviceName: String? = null,
    val error: String? = null,
    val itemId: String = "",
    val itemName: String = "",
    val aspectRatio: Float = 16f / 9f,
    val selectedAspectRatio: AspectRatioMode = AspectRatioMode.FILL,
    val availableAspectRatios: List<AspectRatioMode> = AspectRatioMode.values().toList(),
    val showSubtitleDialog: Boolean = false,
    val availableSubtitleTracks: List<TrackInfo> = emptyList(),
    val selectedSubtitleTrack: TrackInfo? = null,
    val showCastDialog: Boolean = false,
    val availableCastDevices: List<String> = emptyList(),
)

data class VideoQuality(
    val id: String,
    val label: String,
    val bitrate: Int,
    val width: Int,
    val height: Int,
)

@UnstableApi
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: JellyfinRepository,
    private val castManager: CastManager,
    private val offlinePlaybackManager: com.example.jellyfinandroid.data.offline.OfflinePlaybackManager,
) : ViewModel() {

    private val _playerState = MutableStateFlow(VideoPlayerState())
    val playerState: StateFlow<VideoPlayerState> = _playerState.asStateFlow()

    private var trackSelector: DefaultTrackSelector? = null
    private var currentMediaItem: MediaItem? = null
    private var currentJellyfinItem: org.jellyfin.sdk.model.api.BaseItemDto? = null

    var exoPlayer: ExoPlayer? = null
        private set

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _playerState.value = _playerState.value.copy(
                isLoading = playbackState == Player.STATE_BUFFERING,
                isPlaying = exoPlayer?.isPlaying == true,
            )
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("VideoPlayerViewModel", "Playback error: ${error.message}", error)
            _playerState.value = _playerState.value.copy(
                error = "Playback error: ${error.message}",
                isLoading = false,
            )
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            val aspectRatio = if (videoSize.height > 0) {
                videoSize.width.toFloat() / videoSize.height.toFloat()
            } else {
                16f / 9f
            }
            _playerState.value = _playerState.value.copy(aspectRatio = aspectRatio)
        }
    }

    fun initializePlayer(itemId: String, itemName: String, streamUrl: String, startPosition: Long) {
        viewModelScope.launch {
            try {
                _playerState.value = _playerState.value.copy(
                    itemId = itemId,
                    itemName = itemName,
                    isLoading = true,
                    error = null,
                )

                // Initialize track selector for quality selection
                trackSelector = DefaultTrackSelector(context)

                // Create ExoPlayer
                // Check if offline content is available
                val offlineMediaItem = offlinePlaybackManager.getOfflineMediaItem(itemId)
                val isOfflinePlayback = offlineMediaItem != null && streamUrl.startsWith("file://")

                // Create media item and store references
                currentMediaItem = if (isOfflinePlayback && offlineMediaItem != null) {
                    offlineMediaItem
                } else {
                    MediaItem.fromUri(streamUrl)
                }

                // Ensure trackSelector and currentMediaItem are not null before creating ExoPlayer
                val selector = trackSelector ?: throw IllegalStateException("Track selector not initialized")
                val mediaItem = currentMediaItem ?: throw IllegalStateException("Media item not created")

                exoPlayer = ExoPlayer.Builder(context)
                    .setTrackSelector(selector)
                    .build()
                    .apply {
                        addListener(playerListener)
                        setMediaItem(mediaItem)
                        seekTo(startPosition)
                        prepare()
                    }

                // Load Jellyfin item for Cast metadata
                loadJellyfinItem(itemId)

                // Initialize Cast support
                castManager.initialize()

                // Observe cast state updates (single collector lifecycle bound to player init)
                launch {
                    castManager.castState.collectLatest { castState ->
                        _playerState.value = _playerState.value.copy(
                            isCasting = castState.isCasting && castState.isConnected,
                            castDeviceName = castState.deviceName,
                        )
                    }
                }

                // Load available qualities
                loadAvailableQualities(itemId)

                // Start position updates
                startPositionUpdates()

                if (BuildConfig.DEBUG) {
                    Log.d("VideoPlayerViewModel", "Player initialized for item: $itemName")
                }
            } catch (e: Exception) {
                Log.e("VideoPlayerViewModel", "Failed to initialize player", e)
                _playerState.value = _playerState.value.copy(
                    error = "Failed to initialize player: ${e.message}",
                    isLoading = false,
                )
            }
        }
    }

    fun startPlayback() {
        exoPlayer?.play()
    }

    fun pausePlayback() {
        exoPlayer?.pause()
        val position = exoPlayer?.currentPosition ?: 0L
        val itemId = _playerState.value.itemId
        if (itemId.isNotEmpty()) {
            viewModelScope.launch {
                com.example.jellyfinandroid.data.PlaybackPositionStore.savePlaybackPosition(context, itemId, position)
            }
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun changeQuality(quality: VideoQuality) {
        viewModelScope.launch {
            try {
                val currentPosition = exoPlayer?.currentPosition ?: 0L
                val itemId = _playerState.value.itemId

                // Get new stream URL with quality parameters
                val newStreamUrl = getStreamUrlWithQuality(itemId, quality)

                newStreamUrl?.let { url ->
                    exoPlayer?.apply {
                        setMediaItem(MediaItem.fromUri(url))
                        seekTo(currentPosition)
                        prepare()
                    }

                    _playerState.value = _playerState.value.copy(selectedQuality = quality)
                    if (BuildConfig.DEBUG) {
                        Log.d("VideoPlayerViewModel", "Quality changed to: ${quality.label}")
                    }
                }
            } catch (e: Exception) {
                Log.e("VideoPlayerViewModel", "Failed to change quality", e)
                _playerState.value = _playerState.value.copy(
                    error = "Failed to change quality: ${e.message}",
                )
            }
        }
    }

    fun showCastDialog() {
        try {
            val castContext = com.google.android.gms.cast.framework.CastContext.getSharedInstance(context)
            val sessionManager = castContext.sessionManager

            if (sessionManager.currentCastSession?.isConnected == true) {
                // If already connected, disconnect
                sessionManager.endCurrentSession(true)
                if (BuildConfig.DEBUG) {
                    Log.d("VideoPlayerViewModel", "Disconnected from Cast device")
                }
            } else {
                // Show cast device selection dialog
                _playerState.value = _playerState.value.copy(showCastDialog = true)
                if (BuildConfig.DEBUG) {
                    Log.d("VideoPlayerViewModel", "Cast dialog requested")
                }
            }
        } catch (e: Exception) {
            Log.e("VideoPlayerViewModel", "Failed to show cast dialog", e)
        }
    }

    fun hideCastDialog() {
        _playerState.value = _playerState.value.copy(showCastDialog = false)
    }

    fun selectCastDevice(deviceName: String) {
        try {
            // This is a simplified implementation - in reality you'd need to
            // interface with the Cast framework to connect to specific devices
            startCasting()
            _playerState.value = _playerState.value.copy(showCastDialog = false)
            if (BuildConfig.DEBUG) {
                Log.d("VideoPlayerViewModel", "Connecting to Cast device: $deviceName")
            }
        } catch (e: Exception) {
            Log.e("VideoPlayerViewModel", "Failed to connect to Cast device", e)
        }
    }

    fun showSubtitleDialog() {
        trackSelector?.let { selector ->
            try {
                // Get current track groups and selections
                val trackGroups = exoPlayer?.currentTracks
                val subtitleTracks = mutableListOf<TrackInfo>()

                trackGroups?.groups?.forEachIndexed { groupIndex, trackGroup ->
                    if (trackGroup.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                        for (i in 0 until trackGroup.length) {
                            val format = trackGroup.getTrackFormat(i)
                            val isSelected = trackGroup.isTrackSelected(i)
                            subtitleTracks.add(
                                TrackInfo(
                                    groupIndex = groupIndex,
                                    trackIndex = i,
                                    format = format,
                                    isSelected = isSelected,
                                    displayName = format.label ?: format.language ?: "Track ${i + 1}",
                                ),
                            )
                        }
                    }
                }

                // Update state with available subtitle tracks
                _playerState.value = _playerState.value.copy(
                    availableSubtitleTracks = subtitleTracks,
                    showSubtitleDialog = true,
                )

                if (BuildConfig.DEBUG) {
                    Log.d("VideoPlayerViewModel", "Found ${subtitleTracks.size} subtitle tracks")
                }
            } catch (e: Exception) {
                Log.e("VideoPlayerViewModel", "Failed to get subtitle tracks", e)
            }
        }
    }

    fun selectSubtitleTrack(trackInfo: TrackInfo?) {
        trackSelector?.let { selector ->
            try {
                val parametersBuilder = selector.parameters.buildUpon()

                if (trackInfo != null) {
                    // Enable the selected track
                    parametersBuilder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                } else {
                    // Disable all subtitle tracks
                    parametersBuilder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                }

                selector.setParameters(parametersBuilder.build())

                // Update UI state
                _playerState.value = _playerState.value.copy(
                    showSubtitleDialog = false,
                    selectedSubtitleTrack = trackInfo,
                )

                if (BuildConfig.DEBUG) {
                    Log.d("VideoPlayerViewModel", "Selected subtitle track: ${trackInfo?.displayName ?: "None"}")
                }
            } catch (e: Exception) {
                Log.e("VideoPlayerViewModel", "Failed to select subtitle track", e)
            }
        }
    }

    fun hideSubtitleDialog() {
        _playerState.value = _playerState.value.copy(showSubtitleDialog = false)
    }

    fun changeAspectRatio(aspectRatioMode: AspectRatioMode) {
        _playerState.value = _playerState.value.copy(selectedAspectRatio = aspectRatioMode)
        if (BuildConfig.DEBUG) {
            Log.d("VideoPlayerViewModel", "Changed aspect ratio to: ${aspectRatioMode.label}")
        }
    }

    fun startCasting() {
        currentMediaItem?.let { mediaItem ->
            currentJellyfinItem?.let { jellyfinItem ->
                castManager.startCasting(mediaItem, jellyfinItem)
                if (BuildConfig.DEBUG) {
                    Log.d("VideoPlayerViewModel", "Started casting: ${jellyfinItem.name}")
                }
            }
        }
    }

    fun stopCasting() {
        castManager.stopCasting()
        if (BuildConfig.DEBUG) {
            Log.d("VideoPlayerViewModel", "Stopped casting")
        }
    }

    fun releasePlayer() {
        val position = exoPlayer?.currentPosition ?: 0L
        val itemId = _playerState.value.itemId
        saveCurrentPlaybackPosition(position, itemId)

        stopPositionUpdates()
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
        castManager.release()
    }

    private fun saveCurrentPlaybackPosition(position: Long, itemId: String) {
        if (itemId.isNotEmpty()) {
            viewModelScope.launch {
                com.example.jellyfinandroid.data.PlaybackPositionStore.savePlaybackPosition(context, itemId, position)
            }
        }
    }

    private suspend fun loadAvailableQualities(itemId: String) {
        try {
            // Generate standard quality options
            val qualities = listOf(
                VideoQuality("auto", "Auto", 0, 0, 0),
                VideoQuality("1080p", "1080p", 8000000, 1920, 1080),
                VideoQuality("720p", "720p", 4000000, 1280, 720),
                VideoQuality("480p", "480p", 2000000, 854, 480),
                VideoQuality("360p", "360p", 1000000, 640, 360),
            )

            _playerState.value = _playerState.value.copy(
                availableQualities = qualities,
                selectedQuality = qualities.first(), // Default to Auto
            )
        } catch (e: Exception) {
            Log.e("VideoPlayerViewModel", "Failed to load qualities", e)
        }
    }

    private fun getStreamUrlWithQuality(itemId: String, quality: VideoQuality): String? {
        return when (quality.id) {
            "auto" -> repository.getStreamUrl(itemId)
            else -> {
                val server = repository.getCurrentServer() ?: return null
                "${server.url}/Videos/$itemId/stream?" +
                    "MaxStreamingBitrate=${quality.bitrate}&" +
                    "VideoCodec=h264&" +
                    "AudioCodec=aac&" +
                    "MaxWidth=${quality.width}&" +
                    "MaxHeight=${quality.height}&" +
                    "api_key=${server.accessToken}"
            }
        }
    }

    private suspend fun loadJellyfinItem(itemId: String) {
        try {
            // This would need to be implemented in the repository
            // For now, create a basic item for Cast metadata
            currentJellyfinItem = org.jellyfin.sdk.model.api.BaseItemDto(
                id = java.util.UUID.fromString(itemId),
                name = _playerState.value.itemName,
                overview = "Playing from Jellyfin Android Client",
                type = org.jellyfin.sdk.model.api.BaseItemKind.VIDEO,
            )
        } catch (e: Exception) {
            Log.w("VideoPlayerViewModel", "Could not load Jellyfin item metadata", e)
        }
    }

    private var positionUpdateJob: Job? = null

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    _playerState.value = _playerState.value.copy(
                        currentPosition = player.currentPosition,
                        duration = player.duration.takeIf { it > 0 } ?: 0L,
                        bufferedPosition = player.bufferedPosition,
                    )
                }
                delay(1000L) // Update every second
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPositionUpdates()
        releasePlayer()
    }
}
