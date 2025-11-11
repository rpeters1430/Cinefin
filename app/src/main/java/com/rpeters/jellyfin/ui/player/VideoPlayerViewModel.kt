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
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

@UnstableApi
enum class AspectRatioMode(val label: String, val resizeMode: Int) {
    FIT("Fit", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Fill", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    FIXED_WIDTH("Fixed Width", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),
    FIXED_HEIGHT(
        "Fixed Height",
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT,
    ),
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
    val selectedAspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val availableAspectRatios: List<AspectRatioMode> = AspectRatioMode.values().toList(),
    val isControlsVisible: Boolean = true,
    val showSubtitleDialog: Boolean = false,
    val showCastDialog: Boolean = false,
    val availableCastDevices: List<String> = emptyList(),
    val availableQualities: List<VideoQuality> = emptyList(),
    val selectedQuality: VideoQuality? = null,
    val isCasting: Boolean = false,
    val isCastConnected: Boolean = false,
    val castDeviceName: String? = null,
    val availableAudioTracks: List<TrackInfo> = emptyList(),
    val selectedAudioTrack: TrackInfo? = null,
    val availableSubtitleTracks: List<TrackInfo> = emptyList(),
    val selectedSubtitleTrack: TrackInfo? = null,
    val playbackSpeed: Float = 1.0f,
    // Skip segment markers (ms)
    val introStartMs: Long? = null,
    val introEndMs: Long? = null,
    val outroStartMs: Long? = null,
    val outroEndMs: Long? = null,
)

@UnstableApi
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: JellyfinRepository,
    private val castManager: CastManager,
    private val playbackProgressManager: PlaybackProgressManager,
) : ViewModel() {

    init {
        castManager.initialize()
        viewModelScope.launch {
            castManager.castState.collect { castState ->
                handleCastState(castState)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Release player resources
        releasePlayer()
        // Release CastManager listeners to prevent memory leaks
        castManager.release()
        Log.d("VideoPlayer", "ViewModel cleared - resources released")
    }

    private val _playerState = MutableStateFlow(VideoPlayerState())
    val playerState: StateFlow<VideoPlayerState> = _playerState.asStateFlow()
    val playbackProgress: StateFlow<PlaybackProgress> = playbackProgressManager.playbackProgress

    var exoPlayer: ExoPlayer? = null
        private set

    private var currentItemId: String? = null
    private var currentItemName: String? = null
    private var defaultsApplied: Boolean = false
    private var positionJob: kotlinx.coroutines.Job? = null
    private var currentMediaItem: MediaItem? = null
    private var currentItemMetadata: BaseItemDto? = null
    private var currentSubtitleSpecs: List<SubtitleSpec> = emptyList()
    private var wasPlayingBeforeCast: Boolean = false
    private var hasSentCastLoad: Boolean = false
    private var lastCastState: CastState? = null
    private var playbackSessionId: String? = null

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
                duration = exoPlayer?.duration ?: _playerState.value.duration,
                bufferedPosition = exoPlayer?.bufferedPosition
                    ?: _playerState.value.bufferedPosition,
                currentPosition = exoPlayer?.currentPosition ?: _playerState.value.currentPosition,
            )

            if (playbackState == Player.STATE_READY) {
                startPositionUpdates()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d("VideoPlayer", "Playing changed: $isPlaying")
            _playerState.value = _playerState.value.copy(isPlaying = isPlaying)

            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("VideoPlayer", "Error: ${error.message}", error)
            _playerState.value = _playerState.value.copy(
                error = "Playback error: ${error.message}",
                isLoading = false,
            )
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            // Map current tracks to audio and subtitle lists with selection state
            val audio = mutableListOf<TrackInfo>()
            val text = mutableListOf<TrackInfo>()

            tracks.groups.forEachIndexed { groupIndex, group ->
                val trackType = group.type
                val mediaGroup = group.mediaTrackGroup
                for (i in 0 until mediaGroup.length) {
                    val format = mediaGroup.getFormat(i)
                    val isSelected = group.isTrackSelected(i)
                    val display = buildString {
                        format.label?.let { append(it) }
                        if (isEmpty() && !format.language.isNullOrBlank()) append(format.language)
                        if (format.channelCount != androidx.media3.common.Format.NO_VALUE && format.channelCount > 0) {
                            if (isNotEmpty()) append(" • ")
                            append("${format.channelCount}ch")
                        }
                        if (format.sampleRate != androidx.media3.common.Format.NO_VALUE && format.sampleRate > 0) {
                            if (isNotEmpty()) append(" • ")
                            append("${format.sampleRate}Hz")
                        }
                    }.ifBlank { "Track ${i + 1}" }

                    val info = TrackInfo(
                        groupIndex = groupIndex,
                        trackIndex = i,
                        format = format,
                        isSelected = isSelected,
                        displayName = display,
                    )

                    when (trackType) {
                        androidx.media3.common.C.TRACK_TYPE_AUDIO -> audio += info
                        androidx.media3.common.C.TRACK_TYPE_TEXT -> text += info
                    }
                }
            }

            _playerState.value = _playerState.value.copy(
                availableAudioTracks = audio,
                selectedAudioTrack = audio.firstOrNull { it.isSelected },
                availableSubtitleTracks = text,
                selectedSubtitleTrack = text.firstOrNull { it.isSelected },
            )

            // Apply one-time defaults: English audio if available; subtitles off
            if (!defaultsApplied) {
                defaultsApplied = true
                val player = exoPlayer ?: return
                val currentParams = player.trackSelectionParameters
                val builder = currentParams.buildUpon()

                // Prefer English audio
                val preferredAudio = audio.firstOrNull {
                    val lang = it.format.language ?: ""
                    lang.startsWith("en", ignoreCase = true)
                }
                if (preferredAudio != null) {
                    val group = tracks.groups.getOrNull(preferredAudio.groupIndex) ?: return
                    val override = androidx.media3.common.TrackSelectionOverride(
                        group.mediaTrackGroup,
                        listOf(preferredAudio.trackIndex),
                    )
                    builder.clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_AUDIO)
                    builder.addOverride(override)
                }

                // Disable text tracks by default
                builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                player.trackSelectionParameters = builder.build()
            }
        }
    }

    // Lightweight fetch for technical info without initializing playback UI
    suspend fun fetchPlaybackInfo(itemId: String) = withContext(Dispatchers.IO) {
        repository.getPlaybackInfo(itemId)
    }

    suspend fun initializePlayer(itemId: String, itemName: String, startPosition: Long) {
        Log.d("VideoPlayer", "Initializing player for: $itemName")

        playbackProgressManager.stopTracking()
        currentItemId = itemId
        currentItemName = itemName
        currentMediaItem = null
        currentItemMetadata = null
        currentSubtitleSpecs = emptyList()
        hasSentCastLoad = false
        wasPlayingBeforeCast = false

        val sessionId = java.util.UUID.randomUUID().toString()
        playbackSessionId = sessionId
        playbackProgressManager.startTracking(itemId, viewModelScope, sessionId)

        _playerState.value = _playerState.value.copy(
            itemId = itemId,
            itemName = itemName,
            isLoading = true,
            error = null,
            introStartMs = null,
            introEndMs = null,
            outroStartMs = null,
            outroEndMs = null,
        )

        viewModelScope.launch {
            try {
                val resumePosition = playbackProgressManager.getResumePosition(itemId)
                val initialStartPosition = if (startPosition > 0) startPosition else resumePosition

                // Get playback info with our device profile for direct play
                val playbackInfo = repository.getPlaybackInfo(itemId)

                // Attempt to load chapter markers for intro/outro skip
                currentItemMetadata = loadSkipMarkers(itemId)
                if (lastCastState?.isConnected == true && !hasSentCastLoad) {
                    if (startCastingIfReady()) {
                        hasSentCastLoad = true
                    }
                }

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
                        "${repository.getCurrentServer()?.url}/Videos/$itemId/stream.$container?static=true&mediaSourceId=${mediaSource.id}"
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
                Log.d(
                    "VideoPlayer",
                    "Media source supports direct play: ${mediaSource.supportsDirectPlay}",
                )
                Log.d(
                    "VideoPlayer",
                    "Media source supports direct stream: ${mediaSource.supportsDirectStream}",
                )

                withContext(Dispatchers.Main) {
                    // Create ExoPlayer with FFmpeg extension renderer support for Vorbis
                    val renderersFactory = DefaultRenderersFactory(context)
                        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

                    // Ensure HTTP requests carry the token header
                    val token = repository.getCurrentServer()?.accessToken
                    val httpFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                        .apply {
                            if (!token.isNullOrBlank()) {
                                setDefaultRequestProperties(mapOf("X-Emby-Token" to token))
                            }
                        }
                    val dataSourceFactory =
                        androidx.media3.datasource.DefaultDataSource.Factory(context, httpFactory)
                    val mediaSourceFactory =
                        androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)

                    exoPlayer = ExoPlayer.Builder(context)
                        .setSeekBackIncrementMs(10_000)
                        .setSeekForwardIncrementMs(10_000)
                        .setMediaSourceFactory(mediaSourceFactory)
                        .setRenderersFactory(renderersFactory)
                        .build()

                    // Add listener
                    exoPlayer?.addListener(playerListener)

                    // Create media item
                    val mediaItem = MediaItem.fromUri(streamUrl)
                    currentMediaItem = mediaItem

                    // Set media and prepare
                    exoPlayer?.setMediaItem(mediaItem)
                    exoPlayer?.prepare()
                    exoPlayer?.play()

                    // Update state so UI hides controls after timeout
                    _playerState.value = _playerState.value.copy(isPlaying = true)

                    // Seek to start position if specified
                    if (initialStartPosition > 0) {
                        exoPlayer?.seekTo(initialStartPosition)
                    }

                    if (lastCastState?.isConnected == true && !hasSentCastLoad) {
                        if (startCastingIfReady()) {
                            hasSentCastLoad = true
                        }
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

    private suspend fun loadSkipMarkers(itemId: String): BaseItemDto? {
        return try {
            // Try episode first, then movie as a fallback
            val item = when (val ep = repository.getEpisodeDetails(itemId)) {
                is com.rpeters.jellyfin.data.repository.common.ApiResult.Success -> ep.data
                else -> when (val mv = repository.getMovieDetails(itemId)) {
                    is com.rpeters.jellyfin.data.repository.common.ApiResult.Success -> mv.data
                    else -> null
                }
            }

            if (item == null) {
                _playerState.value = _playerState.value.copy(
                    introStartMs = null,
                    introEndMs = null,
                    outroStartMs = null,
                    outroEndMs = null,
                )
                return null
            }

            val chapters = item.chapters ?: emptyList()
            if (chapters.isEmpty()) {
                _playerState.value = _playerState.value.copy(
                    introStartMs = null,
                    introEndMs = null,
                    outroStartMs = null,
                    outroEndMs = null,
                )
                return item
            }

            fun ticksToMs(ticks: Long?): Long? = ticks?.let { it / 10_000 }

            var introStart: Long? = null
            var introEnd: Long? = null
            var outroStart: Long? = null
            var outroEnd: Long? = null

            chapters.forEachIndexed { index, ch ->
                val name = ch.name?.lowercase() ?: ""
                val startMs = ticksToMs(ch.startPositionTicks)
                val nextStartMs =
                    chapters.getOrNull(index + 1)?.startPositionTicks?.let { it / 10_000 }
                val endMs = nextStartMs

                if (introStart == null && ("intro" in name || "opening" in name)) {
                    introStart = startMs
                    introEnd = endMs
                }
                if (outroStart == null && ("credits" in name || "outro" in name || "ending" in name)) {
                    outroStart = startMs
                    outroEnd = endMs
                }
            }

            _playerState.value = _playerState.value.copy(
                introStartMs = introStart,
                introEndMs = introEnd,
                outroStartMs = outroStart,
                outroEndMs = outroEnd,
            )

            item
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun handleCastState(castState: CastState) {
        val previous = lastCastState
        lastCastState = castState

        val currentState = _playerState.value
        val hideDialog = castState.isConnected
        _playerState.value = currentState.copy(
            isCasting = castState.isCasting,
            isCastConnected = castState.isConnected,
            castDeviceName = castState.deviceName,
            showCastDialog = if (hideDialog) false else currentState.showCastDialog,
        )

        if (castState.isConnected && previous?.isConnected != true && !hasSentCastLoad) {
            if (startCastingIfReady()) {
                hasSentCastLoad = true
            }
        }

        if (previous?.isConnected == true && !castState.isConnected) {
            hasSentCastLoad = false
            if (wasPlayingBeforeCast) {
                withContext(Dispatchers.Main) {
                    exoPlayer?.play()
                }
            }
            wasPlayingBeforeCast = false
        }

        if (castState.isCasting && previous?.isCasting != true) {
            withContext(Dispatchers.Main) {
                exoPlayer?.let { player ->
                    if (!wasPlayingBeforeCast && player.isPlaying) {
                        wasPlayingBeforeCast = true
                    }
                    player.pause()
                }
            }
        }

        if (!castState.isCasting && !castState.isConnected) {
            wasPlayingBeforeCast = false
        }
    }

    private suspend fun startCastingIfReady(): Boolean {
        val mediaItem = currentMediaItem ?: return false
        val metadata = currentItemMetadata ?: return false
        val subtitles = currentSubtitleSpecs

        withContext(Dispatchers.Main) {
            exoPlayer?.let { player ->
                if (!wasPlayingBeforeCast && player.isPlaying) {
                    wasPlayingBeforeCast = true
                }
                player.pause()
            }
        }

        castManager.startCasting(mediaItem, metadata, subtitles)
        return true
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return

        Log.d(
            "VideoPlayer",
            "Toggle play/pause. Current state: playing=${player.isPlaying}, playWhenReady=${player.playWhenReady}",
        )

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
        stopPositionUpdates()
        playbackProgressManager.stopTracking()
        exoPlayer?.let { p ->
            try {
                p.removeListener(playerListener)
                // Stop playback to flush decoders before releasing
                p.stop()
                // Clear any video surface to avoid surface detachment warnings
                p.clearVideoSurface()
            } catch (_: Exception) {
            }
            try {
                p.release()
            } catch (_: Exception) {
            }
        }
        exoPlayer = null
        playbackSessionId = null
    }

    private fun startPositionUpdates() {
        if (positionJob?.isActive == true) return
        val player = exoPlayer ?: return
        positionJob = viewModelScope.launch(Dispatchers.Main) {
            while (true) {
                val duration = if (player.duration > 0) player.duration else _playerState.value.duration
                _playerState.value = _playerState.value.copy(
                    currentPosition = player.currentPosition,
                    bufferedPosition = player.bufferedPosition,
                    duration = duration,
                )
                playbackProgressManager.updateProgress(player.currentPosition, duration)
                kotlinx.coroutines.delay(500)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    // Placeholder methods for UI compatibility
    fun changeQuality(quality: VideoQuality) {
        /* Not implemented yet */
    }

    fun changeAspectRatio(aspectRatio: AspectRatioMode) {
        _playerState.value = _playerState.value.copy(selectedAspectRatio = aspectRatio)
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        _playerState.value = _playerState.value.copy(playbackSpeed = speed)
    }

    fun showCastDialog() {
        val devices = castManager.discoverDevices()
        _playerState.value = _playerState.value.copy(
            availableCastDevices = devices,
            showCastDialog = true,
        )
    }

    fun showSubtitleDialog() {
        /* UI handled in composable for now */
    }

    fun selectAudioTrack(track: TrackInfo) {
        val player = exoPlayer ?: return
        val params = player.trackSelectionParameters
        val group = player.currentTracks.groups.getOrNull(track.groupIndex) ?: return
        val override = androidx.media3.common.TrackSelectionOverride(
            group.mediaTrackGroup,
            listOf(track.trackIndex),
        )
        val newParams = params
            .buildUpon()
            .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_AUDIO)
            .addOverride(override)
            .build()
        player.trackSelectionParameters = newParams
        // State will refresh via onTracksChanged
    }

    fun selectSubtitleTrack(track: TrackInfo?) {
        val player = exoPlayer ?: return
        val params = player.trackSelectionParameters
        val builder = params.buildUpon()
            .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)

        if (track == null) {
            // Turn off text tracks
            builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
        } else {
            val group = player.currentTracks.groups.getOrNull(track.groupIndex) ?: return
            val override = androidx.media3.common.TrackSelectionOverride(
                group.mediaTrackGroup,
                listOf(track.trackIndex),
            )
            builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
            builder.addOverride(override)
        }

        player.trackSelectionParameters = builder.build()
        // State will refresh via onTracksChanged
    }

    fun hideSubtitleDialog() {
        _playerState.value = _playerState.value.copy(showSubtitleDialog = false)
    }

    fun selectCastDevice(deviceName: String) {
        val connected = castManager.connectToDevice(deviceName)
        if (connected) {
            _playerState.value = _playerState.value.copy(showCastDialog = false)
        }
    }

    fun hideCastDialog() {
        _playerState.value = _playerState.value.copy(showCastDialog = false)
    }
}
