package com.rpeters.jellyfin.ui.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
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
enum class AspectRatioMode(val label: String, val description: String, val resizeMode: Int) {
    AUTO(
        "Auto",
        "Maintains aspect ratio with letterboxing",
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
    ),
    FILL(
        "Fill",
        "Stretches to fill screen (may distort)",
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL,
    ),
    CROP(
        "Crop",
        "Zooms to fill screen (may crop edges)",
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
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
    val selectedAspectRatio: AspectRatioMode = AspectRatioMode.AUTO,
    val availableAspectRatios: List<AspectRatioMode> = AspectRatioMode.entries.toList(),
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val isHdrContent: Boolean = false,
    val isControlsVisible: Boolean = true,
    val showSubtitleDialog: Boolean = false,
    val showCastDialog: Boolean = false,
    val availableCastDevices: List<String> = emptyList(),
    val availableQualities: List<VideoQuality> = emptyList(),
    val selectedQuality: VideoQuality? = null,
    val isCastAvailable: Boolean = false,
    val isCasting: Boolean = false,
    val isCastConnected: Boolean = false,
    val castDeviceName: String? = null,
    val isCastPlaying: Boolean = false,
    val castPosterUrl: String? = null,
    // Transcoding information
    val isDirectPlaying: Boolean = false,
    val isDirectStreaming: Boolean = false,
    val isTranscoding: Boolean = false,
    val transcodingReason: String? = null,
    val playbackMethod: String = "Unknown",
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
    // Auto-play next episode
    val nextEpisode: BaseItemDto? = null,
    val showNextEpisodeCountdown: Boolean = false,
    val nextEpisodeCountdown: Int = 0, // seconds remaining
    val hasEnded: Boolean = false,
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
        // Release player resources - use runBlocking since viewModelScope is already cancelled
        kotlinx.coroutines.runBlocking {
            releasePlayer()
        }
        SecureLogger.d("VideoPlayer", "ViewModel cleared - resources released")
    }

    private val _playerState = MutableStateFlow(VideoPlayerState())
    val playerState: StateFlow<VideoPlayerState> = _playerState.asStateFlow()
    val playbackProgress: StateFlow<PlaybackProgress> = playbackProgressManager.playbackProgress

    var exoPlayer: ExoPlayer? = null
        private set

    private var trackSelector: DefaultTrackSelector? = null

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
    private var countdownJob: kotlinx.coroutines.Job? = null

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

            // Only update duration if ExoPlayer has a valid duration (> 0)
            // HLS/transcoded streams may initially return C.TIME_UNSET (negative value)
            val rawDuration = exoPlayer?.duration
            val currentDuration = _playerState.value.duration
            val validDuration = rawDuration?.takeIf { it > 0 } ?: currentDuration

            SecureLogger.d("VideoPlayer", "Duration update: rawDuration=$rawDuration, currentDuration=$currentDuration, validDuration=$validDuration")

            _playerState.value = _playerState.value.copy(
                isLoading = playbackState == Player.STATE_BUFFERING,
                isPlaying = exoPlayer?.isPlaying == true,
                duration = validDuration,
                bufferedPosition = exoPlayer?.bufferedPosition
                    ?: _playerState.value.bufferedPosition,
                currentPosition = exoPlayer?.currentPosition ?: _playerState.value.currentPosition,
                hasEnded = playbackState == Player.STATE_ENDED,
            )

            if (playbackState == Player.STATE_READY) {
                startPositionUpdates()
            } else if (playbackState == Player.STATE_ENDED) {
                handlePlaybackEnded()
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
            var isHdr = false

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
                        androidx.media3.common.C.TRACK_TYPE_VIDEO -> {
                            if (isSelected && isHdrFormat(format)) {
                                isHdr = true
                            }
                        }
                    }
                }
            }

            _playerState.value = _playerState.value.copy(
                availableAudioTracks = audio,
                selectedAudioTrack = audio.firstOrNull { it.isSelected },
                availableSubtitleTracks = text,
                selectedSubtitleTrack = text.firstOrNull { it.isSelected },
                isHdrContent = isHdr,
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

            // Update available qualities for the quality selection menu
            updateAvailableQualities(tracks)
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

        // Note: We'll set the sessionId from PlaybackResult later to use server's playSessionId
        playbackSessionId = null

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

                // Set initial duration from metadata (converts ticks to milliseconds)
                // This ensures the seek bar is visible even before ExoPlayer reports duration
                val initialDuration = currentItemMetadata?.runTimeTicks?.let { it / 10_000 } ?: 0L
                if (initialDuration > 0) {
                    _playerState.value = _playerState.value.copy(duration = initialDuration)
                    SecureLogger.d("VideoPlayer", "Set initial duration from metadata: ${initialDuration}ms (${initialDuration / 60000}min)")
                }

                // Load next episode if this is an episode
                loadNextEpisodeIfAvailable(currentItemMetadata)

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
                    val posterUrl = currentItemMetadata?.let { repository.getSeriesImageUrl(it) }
                        ?: repository.getImageUrl(itemId)
                    _playerState.value = _playerState.value.copy(
                        castPosterUrl = posterUrl,
                        castBackdropUrl = currentItemMetadata?.let { repository.getBackdropUrl(it) },
                        castOverview = currentItemMetadata?.overview,
                    )
                }

                // Use EnhancedPlaybackManager for intelligent playback URL selection
                val playbackResult = enhancedPlaybackManager.getOptimalPlaybackUrl(
                    currentItemMetadata ?: throw Exception("Failed to load item metadata"),
                )

                val streamUrl: String
                val mimeType: String?
                val sessionId: String

                when (playbackResult) {
                    is com.rpeters.jellyfin.data.playback.PlaybackResult.DirectPlay -> {
                        val directPlayMsg = "Direct Play: ${playbackResult.container} (${playbackResult.videoCodec}/${playbackResult.audioCodec}) @ ${playbackResult.bitrate / 1_000_000}Mbps - ${playbackResult.reason}"
                        SecureLogger.d("VideoPlayer", directPlayMsg)
                        Log.d("VideoPlayer", directPlayMsg) // Direct log bypass SecureLogger
                        streamUrl = playbackResult.url
                        sessionId = playbackResult.playSessionId ?: java.util.UUID.randomUUID().toString()
                        // Infer MIME type from container for direct play
                        mimeType = when (playbackResult.container.lowercase(Locale.ROOT)) {
                            "mp4", "m4v" -> MimeTypes.VIDEO_MP4
                            "mkv" -> MimeTypes.VIDEO_MATROSKA
                            "webm" -> MimeTypes.VIDEO_WEBM
                            "avi" -> "video/avi"
                            else -> null // Let ExoPlayer auto-detect
                        }

                        // Update state with direct play info
                        _playerState.value = _playerState.value.copy(
                            isDirectPlaying = true,
                            isDirectStreaming = false,
                            isTranscoding = false,
                            transcodingReason = null,
                            playbackMethod = "Direct Play",
                        )
                    }
                    is com.rpeters.jellyfin.data.playback.PlaybackResult.Transcoding -> {
                        val transcodingMsg = "Transcoding: ${playbackResult.targetResolution} ${playbackResult.targetVideoCodec}/${playbackResult.targetAudioCodec} @ ${playbackResult.targetBitrate / 1_000_000}Mbps - ${playbackResult.reason}"
                        SecureLogger.d("VideoPlayer", transcodingMsg)
                        Log.d("VideoPlayer", transcodingMsg) // Direct log bypass SecureLogger
                        streamUrl = playbackResult.url
                        sessionId = playbackResult.playSessionId ?: java.util.UUID.randomUUID().toString()

                        // Detect MIME type from URL - Jellyfin often uses HLS for transcoding
                        val lowerUrl = streamUrl.lowercase(Locale.ROOT)
                        mimeType = when {
                            lowerUrl.contains(".m3u8") || lowerUrl.contains("master.m3u8") -> {
                                SecureLogger.d("VideoPlayer", "Detected HLS transcoding (m3u8)")
                                MimeTypes.APPLICATION_M3U8
                            }
                            lowerUrl.contains(".mpd") -> {
                                SecureLogger.d("VideoPlayer", "Detected DASH transcoding (mpd)")
                                MimeTypes.APPLICATION_MPD
                            }
                            else -> {
                                // Let ExoPlayer auto-detect for progressive formats
                                SecureLogger.d("VideoPlayer", "Using auto-detection for transcoded stream")
                                null
                            }
                        }

                        // Update state with transcoding info
                        _playerState.value = _playerState.value.copy(
                            isDirectPlaying = false,
                            isDirectStreaming = false,
                            isTranscoding = true,
                            transcodingReason = playbackResult.reason,
                            playbackMethod = "Transcoding",
                        )
                    }
                    is com.rpeters.jellyfin.data.playback.PlaybackResult.Error -> {
                        throw Exception("Playback URL error: ${playbackResult.message}")
                    }
                }

                // Set the session ID and start tracking with it
                playbackSessionId = sessionId
                playbackProgressManager.startTracking(itemId, viewModelScope, sessionId)

                if (streamUrl.isNullOrEmpty()) {
                    throw Exception("No stream URL available from playback manager")
                }

                SecureLogger.d("VideoPlayer", "Final stream URL: $streamUrl")
                Log.d("VideoPlayer", "Final stream URL: $streamUrl") // Direct log
                SecureLogger.d("VideoPlayer", "MIME type: $mimeType")
                Log.d("VideoPlayer", "MIME type: $mimeType") // Direct log

                // Log URL parameters for debugging transcoding issues
                if (streamUrl.contains("?")) {
                    val params = streamUrl.substringAfter("?")
                    val paramList = params.split("&")
                    SecureLogger.d("VideoPlayer", "URL Parameters:")
                    Log.d("VideoPlayer", "URL Parameters:") // Direct log
                    paramList.forEach { param ->
                        if (param.contains("=")) {
                            val (key, value) = param.split("=", limit = 2)
                            when {
                                key.contains("Width", ignoreCase = true) ||
                                    key.contains("Height", ignoreCase = true) ||
                                    key.contains("Bitrate", ignoreCase = true) ||
                                    key.contains("Codec", ignoreCase = true) ||
                                    key.contains("Container", ignoreCase = true) -> {
                                    val paramLog = "  $key = $value"
                                    SecureLogger.d("VideoPlayer", paramLog)
                                    Log.d("VideoPlayer", paramLog) // Direct log
                                }
                            }
                        }
                    }
                }

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

                    // Create track selector with adaptive bitrate support
                    trackSelector = DefaultTrackSelector(context).apply {
                        // Enable adaptive track selection for better quality switching
                        setParameters(
                            buildUponParameters()
                                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                                .setAllowVideoNonSeamlessAdaptiveness(true)
                                .build(),
                        )
                    }

                    exoPlayer = ExoPlayer.Builder(context)
                        .setSeekBackIncrementMs(10_000)
                        .setSeekForwardIncrementMs(10_000)
                        .setMediaSourceFactory(mediaSourceFactory)
                        .setRenderersFactory(renderersFactory)
                        .setTrackSelector(trackSelector!!)
                        // Handle video output to ensure proper surface attachment
                        .setVideoScalingMode(androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                        .build()

                    // Add listener
                    exoPlayer?.addListener(playerListener)

                    // Create media item with proper MIME type for transcoded content
                    val mediaItem = if (mimeType != null) {
                        MediaItem.Builder()
                            .setUri(streamUrl)
                            .setMimeType(mimeType)
                            .build()
                    } else {
                        MediaItem.fromUri(streamUrl)
                    }
                    currentMediaItem = mediaItem

                    SecureLogger.d("VideoPlayer", "Created MediaItem with MIME type: $mimeType")

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
                        val posterUrl = currentItemMetadata?.let { repository.getSeriesImageUrl(it) }
                            ?: repository.getImageUrl(itemId)
                        _playerState.value = _playerState.value.copy(
                            castPosterUrl = posterUrl,
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

                    // Map codec to MIME type - Convert all text formats to VTT for Cast compatibility
                    // Jellyfin server handles on-the-fly conversion to VTT for these formats
                    val (mimeType, extension) = when (codec) {
                        "srt", "subrip", "vtt", "webvtt", "ass", "ssa", "ttml" -> 
                            MimeTypes.TEXT_VTT to "vtt"
                        else -> null to null
                    }

                    if (mimeType != null && extension != null) {
                        // Build subtitle URL with authentication
                        // Note: Using query parameter for auth because Cast receiver may not support custom headers
                        val subtitleUrl = "$serverUrl/Videos/$itemId/${mediaSource.id}/Subtitles/${stream.index}/Stream.$extension?api_key=$accessToken"

                        subtitleSpecs.add(
                            com.rpeters.jellyfin.ui.player.SubtitleSpec(
                                url = subtitleUrl,
                                mimeType = mimeType,
                                language = language,
                                label = displayTitle,
                                isForced = false,
                            ),
                        )

                        SecureLogger.d("VideoPlayer", "Added subtitle spec: $displayTitle ($language) - $codec -> $extension")
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
            isCastAvailable = castState.isAvailable,
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
            // Capture state before release on Main thread
            val (position, isPlaying) = withContext(Dispatchers.Main) {
                (exoPlayer?.currentPosition ?: _playerState.value.currentPosition) to (exoPlayer?.isPlaying == true)
            }
            if (isPlaying) {
                wasPlayingBeforeCast = true
            }

            // HARD STOP: Release player completely when casting starts
            // This prevents "ownership" conflicts where Media3 keeps the session alive
            // We pass reportStop=false so we don't tell the server we stopped (handoff)
            releasePlayer(reportStop = false)

            val posterUrl = currentItemMetadata?.let { repository.getSeriesImageUrl(it) }
                ?: currentItemId?.let { repository.getImageUrl(it) }
            _playerState.value = _playerState.value.copy(
                castPosterUrl = posterUrl,
                castBackdropUrl = currentItemMetadata?.let { repository.getBackdropUrl(it) },
                castOverview = currentItemMetadata?.overview,
            )
            if (startCastingIfReady(position)) {
                hasSentCastLoad = true
            }
        }

        if (previous?.isConnected == true && !castState.isConnected) {
            hasSentCastLoad = false
            val resumePosition = castState.currentPosition
            val itemId = currentItemId
            val itemName = currentItemName
            
            if (itemId != null && itemName != null) {
                viewModelScope.launch {
                    initializePlayer(itemId, itemName, resumePosition)
                }
            }
            
            wasPlayingBeforeCast = false
        }

        if (castState.isCasting && previous?.isCasting != true) {
            // Ensure player is released if we entered casting state
            releasePlayer(reportStop = false)
            wasPlayingBeforeCast = false
            // Start tracking cast position
            startCastPositionUpdates()
        }

        if (!castState.isCasting && !castState.isConnected) {
            wasPlayingBeforeCast = false
            // Stop tracking cast position
            stopCastPositionUpdates()
        }
    }

    private suspend fun startCastingIfReady(startPosition: Long? = null): Boolean {
        val mediaItem = currentMediaItem ?: return false
        val metadata = currentItemMetadata ?: return false
        val subtitles = currentSubtitleSpecs

        // Get current playback position
        val position = startPosition ?: withContext(Dispatchers.Main) {
            exoPlayer?.let { player ->
                val pos = player.currentPosition
                if (!wasPlayingBeforeCast && player.isPlaying) {
                    wasPlayingBeforeCast = true
                }
                player.pause()
                pos
            } ?: 0L
        }

        // Start casting from current position
        // Default to no subtitles for now to simplify troubleshooting
        castManager.startCasting(mediaItem, metadata, emptyList(), position)
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

    suspend fun releasePlayer(reportStop: Boolean = true) {
        if (exoPlayer == null) return
        
        SecureLogger.d("VideoPlayer", "Releasing player (reportStop=$reportStop)")
        stopPositionUpdates()

        // Stop tracking synchronously to ensure stop playback is reported before player is released
        try {
            playbackProgressManager.stopTracking(reportStop = reportStop)
        } catch (e: Exception) {
            SecureLogger.e("VideoPlayer", "Error stopping playback tracking: ${e.message}")
        }

        withContext(Dispatchers.Main) {
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
            trackSelector = null
            playbackSessionId = null
        }
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

    /**
     * Changes video quality based on user selection.
     * @param quality The selected quality, or null for automatic selection
     */
    fun changeQuality(quality: VideoQuality?) {
        val selector = trackSelector ?: return
        val player = exoPlayer ?: return

        if (quality == null) {
            // Auto quality: Clear overrides and let ExoPlayer use adaptive bitrate streaming
            selector.setParameters(
                selector.buildUponParameters()
                    .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_VIDEO)
                    .build(),
            )
            _playerState.value = _playerState.value.copy(selectedQuality = null)
            SecureLogger.d("VideoPlayer", "Set quality to Auto (adaptive bitrate streaming)")
        } else {
            // Manual quality: Find and select the specific track
            val currentTracks = player.currentTracks
            var trackSelected = false

            // Find the video track that matches the requested quality
            for (trackGroup in currentTracks.groups) {
                if (trackGroup.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                    for (i in 0 until trackGroup.length) {
                        val format = trackGroup.getTrackFormat(i)
                        val formatWidth = format.width
                        val formatHeight = format.height

                        // Match based on resolution
                        if (formatWidth == quality.width && formatHeight == quality.height) {
                            // Create track selection override for this specific track
                            val trackSelectionOverride = androidx.media3.common.TrackSelectionOverride(
                                trackGroup.mediaTrackGroup,
                                listOf(i),
                            )

                            selector.setParameters(
                                selector.buildUponParameters()
                                    .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_VIDEO)
                                    .addOverride(trackSelectionOverride)
                                    .build(),
                            )

                            _playerState.value = _playerState.value.copy(selectedQuality = quality)
                            trackSelected = true
                            SecureLogger.d(
                                "VideoPlayer",
                                "Set quality to ${quality.label} (${quality.width}x${quality.height})",
                            )
                            break
                        }
                    }
                }
                if (trackSelected) break
            }

            if (!trackSelected) {
                SecureLogger.w("VideoPlayer", "Could not find track for quality ${quality.label}")
            }
        }
    }

    /**
     * Extracts available video qualities from the current player tracks.
     * Called when tracks change to update the quality menu options.
     */
    private fun updateAvailableQualities(tracks: Tracks) {
        val qualities = mutableListOf<VideoQuality>()

        for (trackGroup in tracks.groups) {
            if (trackGroup.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                for (i in 0 until trackGroup.length) {
                    val format = trackGroup.getTrackFormat(i)
                    val width = format.width
                    val height = format.height
                    val bitrate = format.bitrate

                    if (width > 0 && height > 0) {
                        // Create quality label based on resolution
                        val label = when {
                            height >= 2160 -> "4K (${width}x$height)"
                            height >= 1440 -> "1440p"
                            height >= 1080 -> "1080p"
                            height >= 720 -> "720p"
                            height >= 480 -> "480p"
                            height >= 360 -> "360p"
                            else -> "${height}p"
                        }

                        val quality = VideoQuality(
                            id = "${width}x$height",
                            label = label,
                            bitrate = if (bitrate > 0) bitrate else 0,
                            width = width,
                            height = height,
                        )

                        // Avoid duplicates (same resolution)
                        if (qualities.none { it.width == width && it.height == height }) {
                            qualities.add(quality)
                        }
                    }
                }
            }
        }

        // Sort by resolution (highest first)
        qualities.sortByDescending { it.height }

        _playerState.value = _playerState.value.copy(availableQualities = qualities)
        SecureLogger.d("VideoPlayer", "Updated available qualities: ${qualities.map { it.label }}")
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
        viewModelScope.launch {
            val ready = castManager.awaitInitialization()
            val castState = castManager.castState.value
            if (!ready || !castState.isAvailable) {
                _playerState.value = _playerState.value.copy(
                    error = "Cast is not available on this device",
                )
                return@launch
            }

            val devices = castManager.discoverDevices()
            if (devices.isEmpty()) {
                _playerState.value = _playerState.value.copy(
                    error = "No Cast devices found. Make sure your device is on the same network.",
                )
                return@launch
            }

            _playerState.value = _playerState.value.copy(
                availableCastDevices = devices,
                showCastDialog = true,
            )
        }
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

    private fun isHdrFormat(format: androidx.media3.common.Format): Boolean {
        val colorInfo = format.colorInfo ?: return false
        return when (colorInfo.colorTransfer) {
            androidx.media3.common.C.COLOR_TRANSFER_ST2084,
            androidx.media3.common.C.COLOR_TRANSFER_HLG,
            -> true
            else -> false
        }
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
        // Always close the dialog - error feedback comes via castState.error → playerState.error
        _playerState.value = _playerState.value.copy(showCastDialog = false)
        viewModelScope.launch {
            val ready = castManager.awaitInitialization()
            if (!ready) {
                _playerState.value = _playerState.value.copy(
                    error = "Cast is not available on this device",
                )
                return@launch
            }
            castManager.connectToDevice(deviceName)
            // Note: Connection result will come through castState updates
            // Success: onSessionStarted callback → handleCastState() → starts casting
            // Failure: error set in CastManager → propagated to playerState.error → shown in Snackbar
        }
    }

    fun hideCastDialog() {
        _playerState.value = _playerState.value.copy(showCastDialog = false)
    }

    fun clearError() {
        _playerState.value = _playerState.value.copy(error = null)
    }

    /**
     * Load the next episode if the current item is an episode
     */
    private suspend fun loadNextEpisodeIfAvailable(metadata: BaseItemDto?) {
        if (metadata == null) return

        // Check if this is an episode
        val seasonId = metadata.seasonId?.toString() ?: return
        val currentEpisodeIndex = metadata.indexNumber ?: return

        SecureLogger.d("VideoPlayer", "Current item is episode $currentEpisodeIndex in season $seasonId")

        try {
            val result = repository.getEpisodesForSeason(seasonId)
            if (result is com.rpeters.jellyfin.data.repository.common.ApiResult.Success) {
                val episodes = result.data.sortedBy { it.indexNumber }
                val currentIndex = episodes.indexOfFirst { it.indexNumber == currentEpisodeIndex }

                if (currentIndex >= 0) {
                    val nextEpisode = episodes.getOrNull(currentIndex + 1)
                    if (nextEpisode != null) {
                        _playerState.value = _playerState.value.copy(nextEpisode = nextEpisode)
                        SecureLogger.d("VideoPlayer", "Next episode loaded: ${nextEpisode.name} (${nextEpisode.id})")
                    } else {
                        SecureLogger.d("VideoPlayer", "No next episode available (last in season)")
                    }
                }
            }
        } catch (e: Exception) {
            SecureLogger.e("VideoPlayer", "Failed to load next episode", e)
        }
    }

    /**
     * Handle playback ended - start auto-play countdown for episodes, or signal activity to finish for movies
     */
    private fun handlePlaybackEnded() {
        SecureLogger.d("VideoPlayer", "Playback ended")
        stopPositionUpdates()

        val nextEpisode = _playerState.value.nextEpisode
        if (nextEpisode != null) {
            // Episode with next episode available - start countdown
            startNextEpisodeCountdown()
        } else {
            // Movie or last episode in season - activity will handle finish
            SecureLogger.d("VideoPlayer", "No next episode, video ended")
        }
    }

    /**
     * Start the countdown timer for auto-playing the next episode
     */
    private fun startNextEpisodeCountdown() {
        val countdownSeconds = 10 // 10 seconds countdown

        _playerState.value = _playerState.value.copy(
            showNextEpisodeCountdown = true,
            nextEpisodeCountdown = countdownSeconds,
        )

        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (i in countdownSeconds downTo 1) {
                _playerState.value = _playerState.value.copy(nextEpisodeCountdown = i)
                kotlinx.coroutines.delay(1000)
            }

            // Countdown finished - play next episode
            playNextEpisode()
        }
    }

    /**
     * Cancel the next episode countdown
     */
    fun cancelNextEpisodeCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _playerState.value = _playerState.value.copy(
            showNextEpisodeCountdown = false,
            nextEpisodeCountdown = 0,
        )
        SecureLogger.d("VideoPlayer", "Next episode countdown cancelled")
    }

    /**
     * Play the next episode immediately
     */
    fun playNextEpisode() {
        val nextEpisode = _playerState.value.nextEpisode ?: return

        cancelNextEpisodeCountdown()

        SecureLogger.d("VideoPlayer", "Playing next episode: ${nextEpisode.name} (${nextEpisode.id})")

        // Release current player and start new playback
        viewModelScope.launch {
            releasePlayer()
            initializePlayer(
                itemId = nextEpisode.id.toString(),
                itemName = nextEpisode.name ?: "Episode ${nextEpisode.indexNumber}",
                startPosition = 0L,
            )
        }
    }
}
