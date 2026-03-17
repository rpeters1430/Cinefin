package com.rpeters.jellyfin.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.data.playback.AdaptiveBitrateMonitor
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.PlayMethod
import javax.inject.Inject

/**
 * Refactored VideoPlayerViewModel that delegates to specialized managers.
 */
@UnstableApi
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: JellyfinRepository,
    private val stateManager: VideoPlayerStateManager,
    private val playbackManager: VideoPlayerPlaybackManager,
    private val trackManager: VideoPlayerTrackManager,
    private val castManager: VideoPlayerCastManager,
    private val metadataManager: VideoPlayerMetadataManager,
    private val playbackProgressManager: PlaybackProgressManager,
    private val playbackPreferencesRepository: com.rpeters.jellyfin.data.preferences.PlaybackPreferencesRepository,
    private val adaptiveBitrateMonitor: AdaptiveBitrateMonitor,
) : ViewModel() {

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
            stateManager.updateState { it.copy(
                isPlaying = isPlaying,
                // Safety net: a playing player cannot be in a loading/buffering state
                isLoading = if (isPlaying) false else it.isLoading,
            ) }
            if (isPlaying) playbackManager.startPositionUpdates(viewModelScope) else playbackManager.stopPositionUpdates()
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            trackManager.updateTracks(tracks)
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            stateManager.updateState { it.copy(
                videoWidth = videoSize.width,
                videoHeight = videoSize.height
            ) }
        }

        override fun onPlayerError(error: PlaybackException) {
            playbackManager.handlePlayerError(error, viewModelScope, currentItemMetadata, stateManager.playerState.value.itemId)
        }
    }

    private var previousPlaybackState: Int = Player.STATE_IDLE

    init {
        castManager.initialize(
            scope = viewModelScope,
            onStartPlayback = { itemId, itemName, pos -> 
                viewModelScope.launch { initializePlayer(itemId, itemName, pos) }
            },
            onReleasePlayer = { 
                viewModelScope.launch { playbackManager.releasePlayer(reportStop = false) }
            },
            onStartCasting = cast@ { startPosition ->
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

    suspend fun initializePlayer(
        itemId: String,
        itemName: String,
        startPosition: Long = 0,
        subtitleIndex: Int? = null,
        audioIndex: Int? = null,
        forceOffline: Boolean = false
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
        
        stateManager.updateState { it.copy(
            itemId = itemId,
            itemName = itemName,
            isLoading = true,
            error = null,
            qualityRecommendation = null,
            nextEpisode = null,
            showNextEpisodeCountdown = false,
            nextEpisodeCountdown = 0,
        ) }

        try {
            // Load metadata and skip markers
            val metadata = metadataManager.loadSkipMarkers(itemId)
            currentItemMetadata = metadata
            metadataManager.loadNextEpisodeIfAvailable(metadata)

            // Get playback info for subtitles
            val playbackInfo = try { repository.getPlaybackInfo(itemId) } catch (_: Exception) { null }
            val subtitles = metadataManager.extractSubtitleSpecs(metadata, playbackInfo)
            val mediaSourceId = playbackInfo?.mediaSources?.firstOrNull()?.id

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
                scope = viewModelScope
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
            stateManager.updateState { it.copy(
                error = "Failed to initialize: ${e.message}",
                isLoading = false
            ) }
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun startPlayback() {
        exoPlayer?.play()
    }

    fun pausePlayback() {
        exoPlayer?.pause()
    }

    fun changeQuality(videoQuality: VideoQuality?) {
        trackManager.changeQuality(videoQuality, playbackManager.trackSelector, exoPlayer)
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        stateManager.updateState { it.copy(playbackSpeed = speed) }
    }

    fun changeAspectRatio(aspectRatio: AspectRatioMode) {
        stateManager.updateState { it.copy(selectedAspectRatio = aspectRatio) }
    }

    private fun handlePlaybackEnded() {
        playbackManager.stopPositionUpdates()
        val nextEpisode = stateManager.playerState.value.nextEpisode
        if (nextEpisode != null) {
            metadataManager.startNextEpisodeCountdown(viewModelScope) {
                playNextEpisode()
            }
        }
    }

    fun playNextEpisode() {
        val nextEpisode = stateManager.playerState.value.nextEpisode ?: return
        metadataManager.cancelNextEpisodeCountdown()
        viewModelScope.launch {
            playbackManager.releasePlayer()
            initializePlayer(
                itemId = nextEpisode.id.toString(),
                itemName = nextEpisode.name ?: "Next Episode",
                startPosition = 0
            )
        }
    }

    fun cancelNextEpisodeCountdown() {
        metadataManager.cancelNextEpisodeCountdown()
    }

    fun handleCastButtonClick() {
        if (stateManager.playerState.value.isCastConnected) {
            castManager.disconnectCastSession()
        } else {
            showCastDialog()
        }
    }

    fun showCastDialog() {
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

    fun hideCastDialog() {
        castManager.stopDiscovery()
        stateManager.updateState { it.copy(showCastDialog = false) }
    }

    fun selectCastDevice(deviceName: String) {
        stateManager.updateState { it.copy(showCastDialog = false) }
        viewModelScope.launch {
            castManager.connectToDevice(deviceName)
            castManager.stopDiscovery()
        }
    }

    fun pauseCastPlayback() = castManager.pauseCasting()
    fun resumeCastPlayback() = castManager.resumeCasting()
    fun stopCastPlayback() = castManager.stopCasting()
    fun disconnectCast() = castManager.disconnectCastSession()
    fun seekCastPlayback(positionMs: Long) = castManager.seekTo(positionMs)
    fun setCastVolume(volume: Float) = castManager.setVolume(volume)

    fun showSubtitleDialog() {
        stateManager.updateState { it.copy(showSubtitleDialog = true) }
    }

    fun hideSubtitleDialog() {
        stateManager.updateState { it.copy(showSubtitleDialog = false) }
    }

    fun selectAudioTrack(track: TrackInfo) {
        val player = exoPlayer ?: return
        if (stateManager.playerState.value.isTranscoding) {
            val audioIndex = track.format.id?.toIntOrNull()
            if (audioIndex != null) {
                viewModelScope.launch {
                    val currentPos = player.currentPosition
                    playbackManager.releasePlayer()
                    initializePlayer(
                        itemId = stateManager.playerState.value.itemId,
                        itemName = stateManager.playerState.value.itemName,
                        startPosition = currentPos,
                        audioIndex = audioIndex,
                        subtitleIndex = stateManager.playerState.value.selectedSubtitleTrack?.format?.id?.toIntOrNull()
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
    }

    fun selectSubtitleTrack(track: TrackInfo?) {
        val player = exoPlayer ?: return
        if (stateManager.playerState.value.isTranscoding && track != null) {
            val subIndex = track.format.id?.toIntOrNull()
            if (subIndex != null) {
                viewModelScope.launch {
                    val currentPos = player.currentPosition
                    playbackManager.releasePlayer()
                    initializePlayer(
                        itemId = stateManager.playerState.value.itemId,
                        itemName = stateManager.playerState.value.itemName,
                        startPosition = currentPos,
                        subtitleIndex = subIndex
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
    }

    fun acceptQualityRecommendation() {
        val recommendation = stateManager.playerState.value.qualityRecommendation ?: return
        SecureLogger.d("VideoPlayer", "Accepting quality recommendation: ${recommendation.recommendedQuality}")
        val currentPosition = exoPlayer?.currentPosition ?: 0L
        val itemId = stateManager.playerState.value.itemId
        val itemName = stateManager.playerState.value.itemName
        adaptiveBitrateMonitor.clearRecommendation()
        adaptiveBitrateMonitor.resetBufferingTracking()
        viewModelScope.launch {
            playbackManager.releasePlayer(reportStop = false)
            initializePlayer(
                itemId = itemId,
                itemName = itemName,
                startPosition = currentPosition,
            )
        }
    }

    fun dismissQualityRecommendation() {
        adaptiveBitrateMonitor.clearRecommendation()
        adaptiveBitrateMonitor.resetBufferingTracking()
    }

    fun releasePlayerImmediate(reportStop: Boolean = true) {
        viewModelScope.launch {
            playbackManager.releasePlayer(reportStop)
        }
    }

    suspend fun releasePlayer(reportStop: Boolean = true) {
        playbackManager.releasePlayer(reportStop)
    }

    fun clearError() {
        stateManager.updateState { it.copy(error = null) }
    }
}
