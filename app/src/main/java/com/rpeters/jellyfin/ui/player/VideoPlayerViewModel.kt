package com.rpeters.jellyfin.ui.player

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.core.architecture.BaseMviViewModel
import com.rpeters.jellyfin.data.playback.AdaptiveBitrateMonitor
import com.rpeters.jellyfin.data.repository.IJellyfinRepository
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.PlayMethod
import org.orbitmvi.orbit.syntax.*
import javax.inject.Inject

/**
 * Refactored VideoPlayerViewModel that delegates to specialized managers and uses MVI.
 */
@UnstableApi
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: IJellyfinRepository,
    private val stateManager: VideoPlayerStateManager,
    private val playbackManager: VideoPlayerPlaybackManager,
    private val trackManager: VideoPlayerTrackManager,
    private val castManager: VideoPlayerCastManager,
    private val metadataManager: VideoPlayerMetadataManager,
    private val playbackProgressManager: PlaybackProgressManager,
    private val playbackPreferencesRepository: com.rpeters.jellyfin.data.preferences.PlaybackPreferencesRepository,
    private val adaptiveBitrateMonitor: AdaptiveBitrateMonitor,
) : BaseMviViewModel<VideoPlayerState, VideoPlayerSideEffect, VideoPlayerIntent>(
    initialState = VideoPlayerState()
) {

    val playerState: StateFlow<VideoPlayerState> = stateManager.playerState
    val playbackProgress: StateFlow<PlaybackProgress> = playbackProgressManager.playbackProgress

    val exoPlayer get() = playbackManager.exoPlayer

    private var currentItemMetadata: BaseItemDto? = null
    private val playbackPreferences =
        playbackPreferencesRepository.preferences.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.rpeters.jellyfin.data.preferences.PlaybackPreferences.DEFAULT,
        )

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            playbackManager.handlePlaybackStateChanged(playbackState, previousPlaybackState, viewModelScope)
            previousPlaybackState = playbackState

            if (playbackState == Player.STATE_READY) {
                playbackManager.startPositionUpdates(viewModelScope)
            } else if (playbackState == Player.STATE_ENDED) {
                handlePlaybackEnded()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            stateManager.updateState {
                it.copy(
                    isPlaying = isPlaying,
                    // Safety net: a playing player cannot be in a loading/buffering state
                    isLoading = if (isPlaying) false else it.isLoading,
                )
            }
            if (isPlaying) playbackManager.startPositionUpdates(viewModelScope) else playbackManager.stopPositionUpdates()
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            trackManager.updateTracks(tracks)
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            stateManager.updateState {
                it.copy(
                    videoWidth = videoSize.width,
                    videoHeight = videoSize.height,
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            playbackManager.handlePlayerError(error, viewModelScope, currentItemMetadata, stateManager.playerState.value.itemId)
        }
    }

    private var previousPlaybackState: Int = Player.STATE_IDLE

    init {
        // Observe stateManager.playerState and sync with Orbit's container
        viewModelScope.launch {
            stateManager.playerState.collect { newState ->
                intent {
                    reduce { newState }
                }
            }
        }

        castManager.initialize(
            scope = viewModelScope,
            onStartPlayback = { itemId, itemName, pos ->
                onIntent(VideoPlayerIntent.Initialize(itemId, itemName, pos))
            },
            onReleasePlayer = {
                viewModelScope.launch { playbackManager.releasePlayer(reportStop = false) }
            },
            onStartCasting = cast@{ startPosition ->
                val mediaItem = playbackManager.currentMediaItem ?: return@cast
                val metadata = currentItemMetadata ?: return@cast
                castManager.startCasting(
                    mediaItem = mediaItem,
                    item = metadata,
                    sideLoadedSubs = playbackManager.currentSubtitleSpecs,
                    startPositionMs = startPosition,
                    playSessionId = playbackManager.currentPlaySessionId,
                    mediaSourceId = playbackManager.currentMediaSourceId,
                )
            },
        )

        viewModelScope.launch {
            adaptiveBitrateMonitor.qualityRecommendation.collect { recommendation ->
                stateManager.updateState { it.copy(qualityRecommendation = recommendation) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            playbackManager.releasePlayer()
        }
    }

    override fun onIntent(intent: VideoPlayerIntent) {
        when (intent) {
            is VideoPlayerIntent.Initialize -> {
                viewModelScope.launch {
                    initializePlayerInternal(
                        intent.itemId,
                        intent.itemName,
                        intent.startPosition,
                        intent.subtitleIndex,
                        intent.audioIndex,
                        intent.forceOffline
                    )
                }
            }
            VideoPlayerIntent.TogglePlayPause -> togglePlayPause()
            is VideoPlayerIntent.SeekTo -> seekTo(intent.positionMs)
            is VideoPlayerIntent.ChangeQuality -> changeQuality(intent.quality)
            is VideoPlayerIntent.SetPlaybackSpeed -> setPlaybackSpeed(intent.speed)
            is VideoPlayerIntent.ChangeAspectRatio -> changeAspectRatio(intent.aspectRatio)
            VideoPlayerIntent.PlayNextEpisode -> playNextEpisode()
            VideoPlayerIntent.CancelNextEpisodeCountdown -> cancelNextEpisodeCountdown()
            VideoPlayerIntent.DismissNextEpisodePrompt -> dismissNextEpisodePrompt()
            VideoPlayerIntent.ToggleControls -> {
                stateManager.updateState { it.copy(isControlsVisible = !it.isControlsVisible) }
            }
            is VideoPlayerIntent.SetControlsVisible -> {
                stateManager.updateState { it.copy(isControlsVisible = intent.visible) }
            }
            VideoPlayerIntent.HandleCastButtonClick -> handleCastButtonClick()
            VideoPlayerIntent.ShowCastDialog -> showCastDialog()
            VideoPlayerIntent.HideCastDialog -> hideCastDialog()
            is VideoPlayerIntent.SelectCastDevice -> selectCastDevice(intent.deviceName)
            VideoPlayerIntent.PauseCast -> castManager.pauseCasting()
            VideoPlayerIntent.ResumeCast -> castManager.resumeCasting()
            VideoPlayerIntent.StopCast -> castManager.stopCasting()
            VideoPlayerIntent.DisconnectCast -> castManager.disconnectCastSession()
            is VideoPlayerIntent.SeekCast -> castManager.seekTo(intent.positionMs)
            is VideoPlayerIntent.SetCastVolume -> castManager.setVolume(intent.volume)
            VideoPlayerIntent.ShowSubtitleDialog -> {
                stateManager.updateState { it.copy(showSubtitleDialog = true) }
            }
            VideoPlayerIntent.HideSubtitleDialog -> {
                stateManager.updateState { it.copy(showSubtitleDialog = false) }
            }
            VideoPlayerIntent.ShowAudioDialog -> {
                stateManager.updateState { it.copy(showAudioDialog = true) }
            }
            VideoPlayerIntent.HideAudioDialog -> {
                stateManager.updateState { it.copy(showAudioDialog = false) }
            }
            VideoPlayerIntent.ShowQualityDialog -> {
                stateManager.updateState { it.copy(showQualityDialog = true) }
            }
            VideoPlayerIntent.HideQualityDialog -> {
                stateManager.updateState { it.copy(showQualityDialog = false) }
            }
            is VideoPlayerIntent.SelectAudioTrack -> selectAudioTrack(intent.track)
            is VideoPlayerIntent.SelectSubtitleTrack -> selectSubtitleTrack(intent.track)
            VideoPlayerIntent.AcceptQualityRecommendation -> acceptQualityRecommendation()
            VideoPlayerIntent.DismissQualityRecommendation -> dismissQualityRecommendation()
            VideoPlayerIntent.ClearError -> clearError()
            VideoPlayerIntent.ClosePlayer -> {
                intent { postSideEffect(VideoPlayerSideEffect.ClosePlayer) }
            }
            VideoPlayerIntent.ToggleOrientation -> {
                // This is typically handled by the UI/Activity, but we can emit a side effect if needed
                // For now, it's a no-op as the UI handles it directly in this pilot
            }
            VideoPlayerIntent.EnterPip -> {
                // Similarly, Activity handles PIP, but we could emit a side effect
            }
            VideoPlayerIntent.PausePlayback -> pausePlayback()
            VideoPlayerIntent.ReleasePlayer -> releasePlayerImmediate()
        }
    }

    private suspend fun initializePlayerInternal(
        itemId: String,
        itemName: String,
        startPosition: Long = 0,
        subtitleIndex: Int? = null,
        audioIndex: Int? = null,
        forceOffline: Boolean = false,
    ) {
        SecureLogger.d("VideoPlayer", "Initializing playback for $itemName")

        val resumeStartPosition = if (startPosition > 0) {
            startPosition
        } else {
            when (playbackPreferences.value.resumePlaybackMode) {
                com.rpeters.jellyfin.data.preferences.ResumePlaybackMode.NEVER -> 0L
                else -> playbackProgressManager.getResumePosition(itemId)
            }
        }

        stateManager.updateState {
            it.copy(
                itemId = itemId,
                itemName = itemName,
                isLoading = true,
                isPlaying = false,
                currentPosition = 0L,
                duration = 0L,
                bufferedPosition = 0L,
                hasEnded = false,
                error = null,
                videoWidth = 0,
                videoHeight = 0,
                isHdrContent = false,
                isDirectPlaying = false,
                isDirectStreaming = false,
                isTranscoding = false,
                transcodingReason = null,
                playbackMethod = "Unknown",
                availableAudioTracks = emptyList(),
                selectedAudioTrack = null,
                availableSubtitleTracks = emptyList(),
                selectedSubtitleTrack = null,
                showSubtitleDialog = false,
                showAudioDialog = false,
                showQualityDialog = false,
                qualityRecommendation = null,
                nextEpisode = null,
                isNextEpisodePromptDismissed = false,
                showNextEpisodeCountdown = false,
                nextEpisodeCountdown = 0,
            )
        }

        try {
            // Load metadata and skip markers
            val metadata = metadataManager.loadSkipMarkers(itemId)
            currentItemMetadata = metadata
            metadataManager.loadNextEpisodeIfAvailable(metadata)

            // Get playback info for subtitles
            val playbackInfo = try { repository.getPlaybackInfo(itemId) } catch (_: Exception) { null }
            val subtitles = metadataManager.extractSubtitleSpecs(metadata, playbackInfo)
            val fallbackSubtitleTracks = metadataManager.extractSubtitleTracks(playbackInfo, subtitleIndex)
            val mediaSourceId = playbackInfo?.mediaSources?.firstOrNull()?.id

            if (fallbackSubtitleTracks.isNotEmpty()) {
                stateManager.updateState {
                    it.copy(
                        availableSubtitleTracks = fallbackSubtitleTracks,
                        selectedSubtitleTrack = fallbackSubtitleTracks.firstOrNull { track -> track.isSelected },
                    )
                }
            }

            // Initialize ExoPlayer if needed, resetting playback state tracking
            // so stale state from a previous session doesn't affect the new player.
            previousPlaybackState = Player.STATE_IDLE
            if (playbackManager.exoPlayer == null) {
                playbackManager.initializeExoPlayer(playerListener)
            }

            // Start playback logic
            playbackManager.startPlayback(
                itemId = itemId,
                itemName = itemName,
                startPosition = resumeStartPosition,
                metadata = metadata,
                sideLoadedSubs = subtitles,
                forceOffline = forceOffline,
                audioIndex = audioIndex,
                subtitleIndex = subtitleIndex,
                mediaSourceIdHint = mediaSourceId,
                scope = viewModelScope,
            )

            // Start tracking
            val playMethod = when {
                stateManager.playerState.value.isTranscoding -> PlayMethod.TRANSCODE
                stateManager.playerState.value.isDirectStreaming -> PlayMethod.DIRECT_STREAM
                else -> PlayMethod.DIRECT_PLAY
            }
            playbackProgressManager.startTracking(
                itemId = itemId,
                scope = viewModelScope,
                sessionId = playbackManager.currentPlaySessionId ?: java.util.UUID.randomUUID().toString(),
                mediaSourceId = playbackManager.currentMediaSourceId,
                playMethod = playMethod,
            )
        } catch (e: Exception) {
            SecureLogger.e("VideoPlayer", "Initialization failed: ${e.message}", e)
            stateManager.updateState {
                it.copy(
                    error = "Failed to initialize: ${e.message}",
                    isLoading = false,
                )
            }
        }
    }

    internal fun togglePlayPause() {
        exoPlayer?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    internal fun pausePlayback() {
        exoPlayer?.pause()
    }

    internal fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    internal fun changeQuality(videoQuality: VideoQuality?) {
        trackManager.changeQuality(videoQuality, playbackManager.trackSelector, exoPlayer)
    }

    internal fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        stateManager.updateState { it.copy(playbackSpeed = speed) }
    }

    internal fun changeAspectRatio(aspectRatio: AspectRatioMode) {
        stateManager.updateState { it.copy(selectedAspectRatio = aspectRatio) }
    }

    private fun handlePlaybackEnded() {
        playbackManager.stopPositionUpdates()
        val nextEpisode = stateManager.playerState.value.nextEpisode
        if (nextEpisode != null) {
            metadataManager.startNextEpisodeCountdown(viewModelScope) {
                onIntent(VideoPlayerIntent.PlayNextEpisode)
            }
        }
    }

    internal fun playNextEpisode() {
        val nextEpisode = stateManager.playerState.value.nextEpisode ?: return
        metadataManager.cancelNextEpisodeCountdown()
        viewModelScope.launch {
            playbackManager.releasePlayer()
            initializePlayerInternal(
                itemId = nextEpisode.id.toString(),
                itemName = nextEpisode.name ?: "Next Episode",
                startPosition = 0,
            )
        }
    }

    internal fun cancelNextEpisodeCountdown() {
        metadataManager.cancelNextEpisodeCountdown()
    }

    internal fun dismissNextEpisodePrompt() {
        stateManager.updateState { it.copy(isNextEpisodePromptDismissed = true) }
    }

    internal fun handleCastButtonClick() {
        if (stateManager.playerState.value.isCastConnected) {
            castManager.disconnectCastSession()
        } else {
            showCastDialog()
        }
    }

    internal fun showCastDialog() {
        viewModelScope.launch {
            val ready = castManager.awaitInitialization()
            if (!ready) {
                stateManager.updateState { it.copy(error = "Cast not available") }
                return@launch
            }
            castManager.startDiscovery()
            stateManager.updateState { it.copy(showCastDialog = true) }
        }
    }

    internal fun hideCastDialog() {
        castManager.stopDiscovery()
        stateManager.updateState { it.copy(showCastDialog = false) }
    }

    internal fun selectCastDevice(deviceName: String) {
        stateManager.updateState { it.copy(showCastDialog = false) }
        viewModelScope.launch {
            castManager.connectToDevice(deviceName)
            castManager.stopDiscovery()
        }
    }

    internal fun selectAudioTrack(track: TrackInfo) {
        val player = exoPlayer ?: return
        if (stateManager.playerState.value.isTranscoding) {
            val audioIndex = track.format.id?.toIntOrNull()
            if (audioIndex != null) {
                viewModelScope.launch {
                    val currentPos = stateManager.playerState.value.currentPosition
                    playbackManager.releasePlayer()
                    initializePlayerInternal(
                        itemId = stateManager.playerState.value.itemId,
                        itemName = stateManager.playerState.value.itemName,
                        startPosition = currentPos,
                        audioIndex = audioIndex,
                        subtitleIndex = stateManager.playerState.value.selectedSubtitleTrack?.format?.id?.toIntOrNull(),
                    )
                }
                return
            }
        }
        val params = player.trackSelectionParameters
        val group = player.currentTracks.groups.getOrNull(track.groupIndex) ?: return
        val override = androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, listOf(track.trackIndex))
        player.trackSelectionParameters = params.buildUpon()
            .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_AUDIO)
            .addOverride(override)
            .build()
        stateManager.updateState { it.copy(selectedAudioTrack = track) }
    }

    internal fun selectSubtitleTrack(track: TrackInfo?) {
        val player = exoPlayer ?: return
        if (track != null && (stateManager.playerState.value.isTranscoding || track.groupIndex < 0)) {
            val subIndex = track.format.id?.toIntOrNull() ?: track.trackIndex.takeIf { it >= 0 }
            if (subIndex != null) {
                viewModelScope.launch {
                    val currentPos = stateManager.playerState.value.currentPosition
                    playbackManager.releasePlayer()
                    initializePlayerInternal(
                        itemId = stateManager.playerState.value.itemId,
                        itemName = stateManager.playerState.value.itemName,
                        startPosition = currentPos,
                        subtitleIndex = subIndex,
                    )
                }
                stateManager.updateState {
                    it.copy(
                        selectedSubtitleTrack = track,
                        showSubtitleDialog = false,
                    )
                }
                return
            }
        }
        val params = player.trackSelectionParameters
        val builder = params.buildUpon().clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
        if (track == null) {
            builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
        } else {
            val group = player.currentTracks.groups.getOrNull(track.groupIndex) ?: return
            val override = androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, listOf(track.trackIndex))
            builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false).addOverride(override)
        }
        player.trackSelectionParameters = builder.build()
        stateManager.updateState {
            it.copy(
                selectedSubtitleTrack = track,
                showSubtitleDialog = false,
            )
        }
    }

    internal fun acceptQualityRecommendation() {
        val recommendation = stateManager.playerState.value.qualityRecommendation ?: return
        SecureLogger.d("VideoPlayer", "Accepting quality recommendation: ${recommendation.recommendedQuality}")
        val currentPosition = exoPlayer?.currentPosition ?: 0L
        val itemId = stateManager.playerState.value.itemId
        val itemName = stateManager.playerState.value.itemName
        adaptiveBitrateMonitor.clearRecommendation()
        adaptiveBitrateMonitor.resetBufferingTracking()
        viewModelScope.launch {
            playbackPreferencesRepository.setTranscodingQuality(recommendation.recommendedQuality)
            playbackManager.releasePlayer(reportStop = false)
            initializePlayerInternal(
                itemId = itemId,
                itemName = itemName,
                startPosition = currentPosition,
            )
        }
    }

    internal fun dismissQualityRecommendation() {
        adaptiveBitrateMonitor.clearRecommendation()
        adaptiveBitrateMonitor.resetBufferingTracking()
    }

    internal fun clearError() {
        stateManager.updateState { it.copy(error = null) }
    }

    internal fun releasePlayerImmediate() {
        playbackManager.releasePlayerImmediate()
    }

    internal fun showSubtitleDialog() {
        stateManager.updateState { it.copy(showSubtitleDialog = true) }
    }

    internal fun hideSubtitleDialog() {
        stateManager.updateState { it.copy(showSubtitleDialog = false) }
    }

    internal fun showAudioDialog() {
        stateManager.updateState { it.copy(showAudioDialog = true) }
    }

    internal fun hideAudioDialog() {
        stateManager.updateState { it.copy(showAudioDialog = false) }
    }

    internal fun showQualityDialog() {
        stateManager.updateState { it.copy(showQualityDialog = true) }
    }

    internal fun hideQualityDialog() {
        stateManager.updateState { it.copy(showQualityDialog = false) }
    }
}
