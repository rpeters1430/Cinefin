package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.offline.OfflineDownload
import com.rpeters.jellyfin.data.offline.OfflineDownloadManager
import com.rpeters.jellyfin.data.offline.DownloadStatus
import com.rpeters.jellyfin.data.repository.GenerativeAiRepository
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.network.ConnectivityChecker
import com.rpeters.jellyfin.ui.utils.EnhancedPlaybackUtils
import com.rpeters.jellyfin.ui.utils.PlaybackCapabilityAnalysis
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

data class MovieDetailState(
    val movie: BaseItemDto? = null,
    val similarMovies: List<BaseItemDto> = emptyList(),
    val playbackAnalysis: PlaybackCapabilityAnalysis? = null,
    val playbackProgress: com.rpeters.jellyfin.ui.player.PlaybackProgress? = null,
    val isLoading: Boolean = false,
    val isSimilarMoviesLoading: Boolean = false,
    val errorMessage: String? = null,
    val aiSummary: String? = null,
    val isLoadingAiSummary: Boolean = false,
    val whyYoullLoveThis: String? = null,
    val isLoadingWhyYoullLoveThis: Boolean = false,
    val isDownloaded: Boolean = false,
    val downloadInfo: OfflineDownload? = null,
    val isOffline: Boolean = false,
)

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val repository: JellyfinRepository,
    private val mediaRepository: JellyfinMediaRepository,
    private val enhancedPlaybackUtils: EnhancedPlaybackUtils,
    private val generativeAiRepository: GenerativeAiRepository,
    private val offlineDownloadManager: OfflineDownloadManager,
    private val connectivityChecker: ConnectivityChecker,
    private val playbackProgressManager: com.rpeters.jellyfin.ui.player.PlaybackProgressManager,
    private val analytics: com.rpeters.jellyfin.utils.AnalyticsHelper,
) : ViewModel() {

    private val _state = MutableStateFlow(MovieDetailState())
    val state: StateFlow<MovieDetailState> = _state.asStateFlow()
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
                val currentMovie = _state.value.movie
                if (currentMovie != null && progress.itemId == currentMovie.id.toString()) {
                    // Only update if progress is actually for this item and has valid data
                    if (progress.positionMs > 0 || progress.isWatched) {
                        _state.value = _state.value.copy(playbackProgress = progress)
                    }

                    // If progress was updated externally (e.g. finished playing),
                    // we might want to refresh the movie metadata to get updated 'played' status
                    if (progress.isWatched && currentMovie.userData?.played != true) {
                        refresh()
                    }
                }
            }
        }
    }

    fun loadMovieDetails(movieId: String) {
        analytics.logUiEvent("MovieDetail", "view_movie")
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null, playbackAnalysis = null)

            // Also fetch initial progress from server
            val initialProgress = try {
                val resumePos = playbackProgressManager.getResumePosition(movieId)
                playbackProgressManager.playbackProgress.value
            } catch (e: Exception) {
                null
            }

            when (val result = repository.getMovieDetails(movieId)) {
                is ApiResult.Success -> {
                    val analysis = try {
                        enhancedPlaybackUtils.analyzePlaybackCapabilities(result.data)
                    } catch (e: CancellationException) {
                        throw e
                    }
                    _state.value = _state.value.copy(
                        movie = result.data,
                        playbackAnalysis = analysis,
                        playbackProgress = initialProgress?.takeIf { it.itemId == movieId },
                        isLoading = false,
                    )

                    observeDownloadState(movieId)

                    // Load similar movies in background (uses Jellyfin's built-in recommendations)
                    loadSimilarMovies(movieId)

                    // Generate personalized "Why You'll Love This" pitch in background
                    // This is the only AI feature on detail screens to keep it simple
                    generateWhyYoullLoveThis(result.data)
                }
                is ApiResult.Error -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = result.message,
                    )
                }
                is ApiResult.Loading -> {
                    // no-op
                }
            }
        }
    }

    private fun loadSimilarMovies(movieId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSimilarMoviesLoading = true)
            when (val result = mediaRepository.getSimilarMovies(movieId, limit = 10)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(
                        similarMovies = result.data,
                        isSimilarMoviesLoading = false,
                    )
                }
                is ApiResult.Error -> {
                    // Similar movies is non-critical, just set loading to false
                    _state.value = _state.value.copy(isSimilarMoviesLoading = false)
                }
                is ApiResult.Loading -> {
                    // no-op
                }
            }
        }
    }

    private fun generateWhyYoullLoveThis(movie: BaseItemDto) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingWhyYoullLoveThis = true)
            try {
                // Get viewing history - we'll use recently played items as a proxy
                // In a real implementation, we'd fetch from repository
                val viewingHistory = try {
                    when (val result = mediaRepository.getContinueWatching(limit = 20)) {
                        is ApiResult.Success -> result.data
                        else -> emptyList()
                    }
                } catch (e: Exception) {
                    emptyList()
                }

                if (viewingHistory.isNotEmpty()) {
                    val pitch = generativeAiRepository.generateWhyYoullLoveThis(
                        item = movie,
                        viewingHistory = viewingHistory,
                    )
                    _state.value = _state.value.copy(
                        whyYoullLoveThis = pitch.takeIf { it.isNotBlank() },
                        isLoadingWhyYoullLoveThis = false,
                    )
                } else {
                    // No viewing history, skip
                    _state.value = _state.value.copy(
                        whyYoullLoveThis = null,
                        isLoadingWhyYoullLoveThis = false,
                    )
                }
            } catch (e: Exception) {
                // Personalized pitch is non-critical
                _state.value = _state.value.copy(
                    whyYoullLoveThis = null,
                    isLoadingWhyYoullLoveThis = false,
                )
            }
        }
    }

    fun refresh() {
        _state.value.movie?.id?.toString()?.let { loadMovieDetails(it) }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    private fun observeDownloadState(movieId: String) {
        observeDownloadInfoJob?.cancel()

        observeDownloadInfoJob = viewModelScope.launch {
            offlineDownloadManager.observeCurrentDownload(movieId).collect { info ->
                val completedInfo = info?.takeIf { it.status == DownloadStatus.COMPLETED }
                SecureLogger.i(
                    "MovieDetailDownload",
                    "Observed download state: itemId=$movieId, currentStatus=${info?.status}, isDownloaded=${completedInfo != null}, downloadId=${info?.id}, cid=${info?.id?.take(8)}",
                )
                _state.value = _state.value.copy(
                    downloadInfo = completedInfo,
                    isDownloaded = completedInfo != null
                )
            }
        }
    }

    fun deleteOfflineCopy() {
        val itemId = _state.value.movie?.id?.toString() ?: return
        offlineDownloadManager.deleteOfflineCopy(itemId)
    }

    /**
     * Generate AI summary of the movie overview
     */
    fun generateAiSummary() {
        val movie = _state.value.movie ?: return
        val overview = movie.overview ?: return
        val title = movie.name ?: "Unknown"

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

    override fun onCleared() {
        super.onCleared()
        // Clear any loaded movie data to prevent memory leaks
        _state.value = MovieDetailState()
    }
}
