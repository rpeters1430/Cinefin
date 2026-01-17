package com.rpeters.jellyfin.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.Locale
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
    val availableAspectRatios: List<AspectRatioMode> = AspectRatioMode.entries.toList(),
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val isControlsVisible: Boolean = true,
    val showSubtitleDialog: Boolean = false,
    val showCastDialog: Boolean = false,
    val availableCastDevices: List<String> = emptyList(),
    val availableQualities: List<VideoQuality> = emptyList(),
    val selectedQuality: VideoQuality? = null,
    val isCasting: Boolean = false,
    val isCastConnected: Boolean = false,
    val castDeviceName: String? = null,
    val isCastPlaying: Boolean = false,
    val castPosterUrl: String? = null,
    val castBackdropUrl: String? = null,
    val castOverview: String? = null,
    // Cast playback position and volume
    val castPosition: Long = 0L,
    val castDuration: Long = 0L,
    val castVolume: Float = 1.0f,
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
    private val enhancedPlaybackManager: com.rpeters.jellyfin.data.playback.EnhancedPlaybackManager,
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
        SecureLogger.d("VideoPlayer", "ViewModel cleared - resources released")
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
    private var currentSubtitleSpecs: List<com.rpeters.jellyfin.ui.player.SubtitleSpec> = emptyList()
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
            SecureLogger.d("VideoPlayer", "State: $stateString, isPlaying: ${exoPlayer?.isPlaying}")

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
            SecureLogger.d("VideoPlayer", "Playing changed: $isPlaying")
            _playerState.value = _playerState.value.copy(isPlaying = isPlaying)

            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
        }

        override fun onPlayerError(error: PlaybackException) {
            SecureLogger.e("VideoPlayer", "Error: ${error.message}", error)
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
                    val display = buildTrackDisplayName(
                        format = format,
                        index = i,
                        trackType = trackType,
                    )

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

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            SecureLogger.d(
                "VideoPlayer",
                "Video size changed: ${videoSize.width}x${videoSize.height}",
            )
            _playerState.value = _playerState.value.copy(
                videoWidth = videoSize.width,
                videoHeight = videoSize.height,
            )
        }
    }

    // Lightweight fetch for technical info without initializing playback UI
    suspend fun fetchPlaybackInfo(itemId: String) = withContext(Dispatchers.IO) {
        repository.getPlaybackInfo(itemId)
    }

    suspend fun initializePlayer(itemId: String, itemName: String, startPosition: Long) {
        SecureLogger.d("VideoPlayer", "Initializing player for: $itemName")

        // If player already exists for the same item, just seek to position instead of recreating
        if (exoPlayer != null && currentItemId == itemId) {
            SecureLogger.d("VideoPlayer", "Player already exists for this item, seeking to position")
            if (startPosition > 0) {
                withContext(Dispatchers.Main) {
                    exoPlayer?.seekTo(startPosition)
                    exoPlayer?.play()
                }
            }
            return
        }

        // Release existing player if switching to a different item
        if (exoPlayer != null && currentItemId != itemId) {
            SecureLogger.d("VideoPlayer", "Releasing existing player before initializing new item")
            releasePlayer()
        }

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

                // Attempt to load chapter markers and full metadata for intro/outro skip
                currentItemMetadata = loadSkipMarkers(itemId)

                // Get playbook info once and reuse it for both subtitle extraction and playback URL selection
                val playbackInfo = try {
                    repository.getPlaybackInfo(itemId)
                } catch (e: Exception) {
                    null
                }

                // Extract subtitle specs from metadata and playback info for casting support
                currentSubtitleSpecs = extractSubtitleSpecs(currentItemMetadata, playbackInfo)

                // Update cast state with poster and backdrop if connected
                if (lastCastState?.isConnected == true && !hasSentCastLoad) {
                    _playerState.value = _playerState.value.copy(
                        castPosterUrl = repository.getImageUrl(itemId),
                        castBackdropUrl = currentItemMetadata?.let { repository.getBackdropUrl(it) },
                        castOverview = currentItemMetadata?.overview,
                    )
                }

                // Use EnhancedPlaybackManager for intelligent playback URL selection
                val playbackResult = enhancedPlaybackManager.getOptimalPlaybackUrl(
                    currentItemMetadata ?: throw Exception("Failed to load item metadata"),
                )

                val streamUrl = when (playbackResult) {
                    is com.rpeters.jellyfin.data.playback.PlaybackResult.DirectPlay -> {
                        SecureLogger.d(
                            "VideoPlayer",
                            "Direct Play: ${playbackResult.container} (${playbackResult.videoCodec}/${playbackResult.audioCodec}) @ ${playbackResult.bitrate / 1_000_000}Mbps - ${playbackResult.reason}",
                        )
                        playbackResult.url
                    }
                    is com.rpeters.jellyfin.data.playback.PlaybackResult.Transcoding -> {
                        SecureLogger.d(
                            "VideoPlayer",
                            "Transcoding: ${playbackResult.targetResolution} ${playbackResult.targetVideoCodec}/${playbackResult.targetAudioCodec} @ ${playbackResult.targetBitrate / 1_000_000}Mbps - ${playbackResult.reason}",
                        )
                        playbackResult.url
                    }
                    is com.rpeters.jellyfin.data.playback.PlaybackResult.Error -> {
                        throw Exception("Playback URL error: ${playbackResult.message}")
                    }
                }

                if (streamUrl.isNullOrEmpty()) {
                    throw Exception("No stream URL available from playback manager")
                }

                SecureLogger.d("VideoPlayer", "Final stream URL: $streamUrl")

                withContext(Dispatchers.Main) {
                    // Create ExoPlayer with optimized renderer support
                    // Use ON mode for extensions to allow hardware decoders for HEVC/high-res content
                    // while falling back to FFmpeg for unsupported codecs like Vorbis
                    val renderersFactory = DefaultRenderersFactory(context)
                        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                        .setEnableDecoderFallback(true) // Enable fallback for codec issues

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
                        // Handle video output to ensure proper surface attachment
                        .setVideoScalingMode(androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
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
                        _playerState.value = _playerState.value.copy(
                            castPosterUrl = repository.getImageUrl(itemId),
                            castBackdropUrl = currentItemMetadata?.let { repository.getBackdropUrl(it) },
                            castOverview = currentItemMetadata?.overview,
                        )
                        if (startCastingIfReady()) {
                            hasSentCastLoad = true
                        }
                    }

                    SecureLogger.d("VideoPlayer", "Player prepared successfully")
                }
            } catch (e: Exception) {
                SecureLogger.e("VideoPlayer", "Init failed: ${e.message}", e)
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

    /**
     * Extract subtitle specifications from item metadata for casting support
     * @param item The media item metadata
     * @param playbackInfo Pre-fetched playback info to avoid redundant API calls
     */
    private suspend fun extractSubtitleSpecs(
        item: BaseItemDto?,
        playbackInfo: org.jellyfin.sdk.model.api.PlaybackInfoResponse?,
    ): List<com.rpeters.jellyfin.ui.player.SubtitleSpec> {
        if (item == null || playbackInfo == null) return emptyList()

        return try {
            val subtitleSpecs = mutableListOf<com.rpeters.jellyfin.ui.player.SubtitleSpec>()
            val itemId = item.id.toString()
            val serverUrl = repository.getCurrentServer()?.url ?: return emptyList()
            val accessToken = repository.getCurrentServer()?.accessToken ?: return emptyList()

            val mediaSource = playbackInfo.mediaSources.firstOrNull() ?: return emptyList()

            // Extract subtitle streams
            mediaSource.mediaStreams
                ?.filter { stream -> stream.type == org.jellyfin.sdk.model.api.MediaStreamType.SUBTITLE }
                ?.filter { stream -> !stream.isExternal } // Only include embedded subtitles
                ?.forEach { stream ->
                    val codec = stream.codec?.lowercase() ?: return@forEach
                    val language = stream.language ?: "und"
                    val displayTitle = stream.displayTitle ?: stream.title ?: language.uppercase()

                    // Map codec to MIME type
                    val mimeType = when (codec) {
                        "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
                        "vtt", "webvtt" -> MimeTypes.TEXT_VTT
                        "ass", "ssa" -> MimeTypes.TEXT_SSA
                        "ttml" -> MimeTypes.APPLICATION_TTML
                        else -> null
                    }

                    if (mimeType != null) {
                        // Build subtitle URL with authentication
                        // Note: Using query parameter for auth because Cast receiver may not support custom headers
                        val subtitleUrl = "$serverUrl/Videos/$itemId/${mediaSource.id}/Subtitles/${stream.index}/Stream.$codec?api_key=$accessToken"

                        subtitleSpecs.add(
                            com.rpeters.jellyfin.ui.player.SubtitleSpec(
                                url = subtitleUrl,
                                mimeType = mimeType,
                                language = language,
                                label = displayTitle,
                                isForced = false,
                            ),
                        )

                        SecureLogger.d("VideoPlayer", "Added subtitle spec: $displayTitle ($language) - $codec")
                    }
                }

            subtitleSpecs
        } catch (e: Exception) {
            SecureLogger.e("VideoPlayer", "Failed to extract subtitle specs", e)
            emptyList()
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
            isCastPlaying = castState.isRemotePlaying,
            castPosition = castState.currentPosition,
            castDuration = castState.duration,
            castVolume = castState.volume,
            showCastDialog = if (hideDialog) false else currentState.showCastDialog,
            error = castState.error ?: currentState.error, // Propagate Cast errors to UI
        )

        if (castState.isConnected && previous?.isConnected != true && !hasSentCastLoad) {
            _playerState.value = _playerState.value.copy(
                castPosterUrl = currentItemId?.let { repository.getImageUrl(it) },
                castBackdropUrl = currentItemMetadata?.let { repository.getBackdropUrl(it) },
                castOverview = currentItemMetadata?.overview,
            )
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
            // Start tracking cast position
            startCastPositionUpdates()
        }

        if (!castState.isCasting && !castState.isConnected) {
            wasPlayingBeforeCast = false
            // Stop tracking cast position
            stopCastPositionUpdates()
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

        SecureLogger.d(
            "VideoPlayer",
            "Toggle play/pause. Current state: playing=${player.isPlaying}, playWhenReady=${player.playWhenReady}",
        )

        if (player.isPlaying) {
            player.pause()
            SecureLogger.d("VideoPlayer", "Paused")
        } else {
            player.play()
            SecureLogger.d("VideoPlayer", "Play requested")
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        SecureLogger.d("VideoPlayer", "Seeked to: $positionMs")
    }

    fun startPlayback() {
        exoPlayer?.play()
        SecureLogger.d("VideoPlayer", "Start playback requested")
    }

    fun pausePlayback() {
        exoPlayer?.pause()
        SecureLogger.d("VideoPlayer", "Pause requested")
    }

    fun pauseCastPlayback() {
        castManager.pauseCasting()
    }

    fun resumeCastPlayback() {
        castManager.resumeCasting()
    }

    fun stopCastPlayback() {
        castManager.stopCasting()
        hasSentCastLoad = false
        wasPlayingBeforeCast = false
        stopCastPositionUpdates()
    }

    /**
     * Disconnect from Cast device entirely (stops playback AND ends session).
     * This will dismiss the persistent notification.
     */
    fun disconnectCast() {
        castManager.disconnectCastSession()
        hasSentCastLoad = false
        wasPlayingBeforeCast = false
        stopCastPositionUpdates()
    }

    fun seekCastPlayback(positionMs: Long) {
        castManager.seekTo(positionMs)
    }

    fun setCastVolume(volume: Float) {
        castManager.setVolume(volume)
    }

    private var castPositionJob: kotlinx.coroutines.Job? = null

    private fun startCastPositionUpdates() {
        if (castPositionJob?.isActive == true) return
        castPositionJob = viewModelScope.launch(Dispatchers.Main) {
            while (true) {
                castManager.updatePlaybackState()
                kotlinx.coroutines.delay(1000) // Update every second
            }
        }
    }

    private fun stopCastPositionUpdates() {
        castPositionJob?.cancel()
        castPositionJob = null
    }

    fun releasePlayer() {
        SecureLogger.d("VideoPlayer", "Releasing player")
        stopPositionUpdates()

        // Stop tracking in a coroutine since it's a suspend function
        viewModelScope.launch {
            try {
                playbackProgressManager.stopTracking()
            } catch (e: Exception) {
                SecureLogger.e("VideoPlayer", "Error stopping playback tracking: ${e.message}")
            }
        }

        exoPlayer?.let { p ->
            try {
                p.removeListener(playerListener)
                // Stop playback to flush decoders before releasing
                p.stop()
                // Clear all video outputs to properly detach surfaces and prevent black screen issues
                // This is critical for proper cleanup with HEVC/high-resolution content
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

    /**
     * Handle cast button click:
     * - If already connected, disconnect from the cast device
     * - If not connected, show the device selection dialog
     */
    fun handleCastButtonClick() {
        if (_playerState.value.isCastConnected) {
            // Already connected - disconnect
            disconnectCast()
        } else {
            // Not connected - show dialog
            showCastDialog()
        }
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

    private fun buildTrackDisplayName(
        format: androidx.media3.common.Format,
        index: Int,
        trackType: Int,
    ): String {
        val label = format.label?.trim().orEmpty()
        val languageName = format.language?.toDisplayLanguage()
        val primary = when {
            label.isNotBlank() && label.length > 3 -> label
            languageName != null -> languageName
            label.isNotBlank() -> label.uppercase()
            else -> null
        } ?: "Track ${index + 1}"

        val descriptor = label.takeIf {
            it.isNotBlank() && !it.equals(primary, true) && !it.equals(format.language, true)
        }

        return buildString {
            append(primary)
            if (descriptor != null) append(" ($descriptor)")
            if (trackType == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                if (format.channelCount != androidx.media3.common.Format.NO_VALUE && format.channelCount > 0) {
                    append(" • ${format.channelCount}ch")
                }
                if (format.sampleRate != androidx.media3.common.Format.NO_VALUE && format.sampleRate > 0) {
                    append(" • ${format.sampleRate}Hz")
                }
            }
        }.ifBlank { "Track ${index + 1}" }
    }

    private fun String.toDisplayLanguage(): String? {
        if (isBlank() || equals("und", true)) return null
        val locale = Locale.forLanguageTag(this)
        val display = locale.getDisplayName(Locale.getDefault()).trim()
        return display.takeIf { it.isNotBlank() }
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

    fun clearError() {
        _playerState.value = _playerState.value.copy(error = null)
    }
}
