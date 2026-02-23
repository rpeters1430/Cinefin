package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.offline.OfflineDownload
import com.rpeters.jellyfin.data.offline.OfflineDownloadManager
import com.rpeters.jellyfin.data.repository.GenerativeAiRepository
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.network.ConnectivityChecker
import com.rpeters.jellyfin.ui.utils.EnhancedPlaybackUtils
import com.rpeters.jellyfin.ui.utils.PlaybackCapabilityAnalysis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

data class TVEpisodeDetailState(
    val episode: BaseItemDto? = null,
    val seriesInfo: BaseItemDto? = null,
    val previousEpisode: BaseItemDto? = null,
    val nextEpisode: BaseItemDto? = null,
    val seasonEpisodes: List<BaseItemDto> = emptyList(),
    val playbackAnalysis: PlaybackCapabilityAnalysis? = null,
    val playbackProgress: com.rpeters.jellyfin.ui.player.PlaybackProgress? = null,
    val isLoading: Boolean = false,
    val aiSummary: String? = null,
    val isLoadingAiSummary: Boolean = false,
    val isDownloaded: Boolean = false,
    val downloadInfo: OfflineDownload? = null,
    val isOffline: Boolean = false,
)

@HiltViewModel
class TVEpisodeDetailViewModel @Inject constructor(
    private val mediaRepository: JellyfinMediaRepository,
    private val enhancedPlaybackUtils: EnhancedPlaybackUtils,
    private val generativeAiRepository: GenerativeAiRepository,
    private val offlineDownloadManager: OfflineDownloadManager,
    private val connectivityChecker: ConnectivityChecker,
    private val playbackProgressManager: com.rpeters.jellyfin.ui.player.PlaybackProgressManager,
    private val analytics: com.rpeters.jellyfin.utils.AnalyticsHelper,
) : ViewModel() {

    private val _state = MutableStateFlow(TVEpisodeDetailState())
    val state: StateFlow<TVEpisodeDetailState> = _state.asStateFlow()
    private var observeDownloadedJob: Job? = null
    private var observeDownloadInfoJob: Job? = null

    init {
        observePlaybackProgress()
        observeConnectivity()
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityChecker.observeNetworkConnectivity().collect { isOnline ->
                _state.value = _state.value.copy(isOffline = !isOnline)
            }
        }
    }

    private fun observePlaybackProgress() {
        viewModelScope.launch {
            playbackProgressManager.playbackProgress.collect { progress ->
                val currentEpisode = _state.value.episode
                if (currentEpisode != null && progress.itemId == currentEpisode.id.toString()) {
                    // Only update if progress is actually for this item and has valid data
                    if (progress.positionMs > 0 || progress.isWatched) {
                        _state.value = _state.value.copy(playbackProgress = progress)
                    }
                }
            }
        }
    }

    fun loadEpisodeDetails(episode: BaseItemDto, seriesInfo: BaseItemDto? = null) {
        analytics.logUiEvent("TVEpisodeDetail", "view_episode")
        viewModelScope.launch {
            val episodeId = episode.id.toString()
            _state.value = _state.value.copy(
                episode = episode,
                seriesInfo = seriesInfo,
                isLoading = true,
            )

            // Also fetch initial progress from server
            try {
                playbackProgressManager.getResumePosition(episodeId)
            } catch (e: Exception) {
                // Ignore
            }

            // Load playback analysis
            loadEpisodeAnalysis(episode)

            // Load series info if not provided
            if (seriesInfo == null) {
                episode.seriesId?.let { seriesId ->
                    loadSeriesInfo(seriesId.toString())
                }
            }

            // Load adjacent episodes
            episode.seasonId?.let { seasonId ->
                loadAdjacentEpisodes(seasonId.toString(), episode.indexNumber)
            }

            observeDownloadState(episodeId)
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    private fun loadEpisodeAnalysis(episode: BaseItemDto) {
        viewModelScope.launch {
            val analysis = try {
                enhancedPlaybackUtils.analyzePlaybackCapabilities(episode)
            } catch (e: CancellationException) {
                throw e
            }
            _state.value = _state.value.copy(playbackAnalysis = analysis)
        }
    }

    private suspend fun loadSeriesInfo(seriesId: String) {
        when (val result = mediaRepository.getSeriesDetails(seriesId)) {
            is ApiResult.Success -> {
                _state.value = _state.value.copy(seriesInfo = result.data)
            }
            is ApiResult.Error -> {
                android.util.Log.w("TVEpisodeDetailVM", "Failed to load series info: ${result.message}")
            }
            is ApiResult.Loading -> {
                // no-op
            }
        }
    }

    private suspend fun loadAdjacentEpisodes(seasonId: String, currentEpisodeIndex: Int?) {
        if (currentEpisodeIndex == null) return

        when (val result = mediaRepository.getEpisodesForSeason(seasonId)) {
            is ApiResult.Success -> {
                val episodes = result.data.sortedBy { it.indexNumber }
                val currentIndex = episodes.indexOfFirst { it.indexNumber == currentEpisodeIndex }

                if (currentIndex >= 0) {
                    val previous = episodes.getOrNull(currentIndex - 1)
                    val next = episodes.getOrNull(currentIndex + 1)

                    _state.value = _state.value.copy(
                        previousEpisode = previous,
                        nextEpisode = next,
                        seasonEpisodes = episodes,
                    )
                } else {
                    // Still store all episodes even if we can't find previous/next
                    _state.value = _state.value.copy(
                        seasonEpisodes = episodes,
                    )
                }
            }
            is ApiResult.Error -> {
                android.util.Log.w("TVEpisodeDetailVM", "Failed to load adjacent episodes: ${result.message}")
            }
            is ApiResult.Loading -> {
                // no-op
            }
        }
    }


    private fun observeDownloadState(episodeId: String) {
        observeDownloadedJob?.cancel()
        observeDownloadInfoJob?.cancel()

        observeDownloadedJob = viewModelScope.launch {
            offlineDownloadManager.observeIsDownloaded(episodeId).collect { downloaded ->
                _state.value = _state.value.copy(isDownloaded = downloaded)
            }
        }

        observeDownloadInfoJob = viewModelScope.launch {
            offlineDownloadManager.observeDownloadInfo(episodeId).collect { info ->
                _state.value = _state.value.copy(downloadInfo = info)
            }
        }
    }

    fun deleteOfflineCopy() {
        val itemId = _state.value.episode?.id?.toString() ?: return
        offlineDownloadManager.deleteOfflineCopy(itemId)
    }

    /**
     * Generate AI summary of the episode overview
     */
    fun generateAiSummary() {
        val episode = _state.value.episode ?: return
        val overview = episode.overview ?: return
        val title = episode.name ?: "Unknown"

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingAiSummary = true)

            try {
                val summary = generativeAiRepository.generateSummary(title, overview)
                _state.value = _state.value.copy(
                    aiSummary = summary,
                    isLoadingAiSummary = false,
                )
            } catch (e: CancellationException) {
                // Coroutine was cancelled, reset loading state
                _state.value = _state.value.copy(isLoadingAiSummary = false)
                throw e
            } catch (e: Exception) {
                // Any other error (should be rare since generateSummary catches exceptions)
                _state.value = _state.value.copy(
                    aiSummary = "Error generating summary: ${e.message}",
                    isLoadingAiSummary = false,
                )
            }
        }
    }
}
