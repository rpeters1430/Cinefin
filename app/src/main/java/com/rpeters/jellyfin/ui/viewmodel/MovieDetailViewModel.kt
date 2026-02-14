package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.repository.GenerativeAiRepository
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.ui.utils.EnhancedPlaybackUtils
import com.rpeters.jellyfin.ui.utils.PlaybackCapabilityAnalysis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import javax.inject.Inject

data class MovieDetailState(
    val movie: BaseItemDto? = null,
    val similarMovies: List<BaseItemDto> = emptyList(),
    val playbackAnalysis: PlaybackCapabilityAnalysis? = null,
    val isLoading: Boolean = false,
    val isSimilarMoviesLoading: Boolean = false,
    val errorMessage: String? = null,
    val aiSummary: String? = null,
    val isLoadingAiSummary: Boolean = false,
    val whyYoullLoveThis: String? = null,
    val isLoadingWhyYoullLoveThis: Boolean = false,
)

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val repository: JellyfinRepository,
    private val mediaRepository: JellyfinMediaRepository,
    private val enhancedPlaybackUtils: EnhancedPlaybackUtils,
    private val generativeAiRepository: GenerativeAiRepository,
    private val analytics: com.rpeters.jellyfin.utils.AnalyticsHelper,
) : ViewModel() {

    private val _state = MutableStateFlow(MovieDetailState())
    val state: StateFlow<MovieDetailState> = _state.asStateFlow()

    fun loadMovieDetails(movieId: String) {
        analytics.logUiEvent("MovieDetail", "view_movie")
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null, playbackAnalysis = null)
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
                        isLoading = false,
                    )

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
                        viewingHistory = viewingHistory
                    )
                    _state.value = _state.value.copy(
                        whyYoullLoveThis = pitch.takeIf { it.isNotBlank() },
                        isLoadingWhyYoullLoveThis = false
                    )
                } else {
                    // No viewing history, skip
                    _state.value = _state.value.copy(
                        whyYoullLoveThis = null,
                        isLoadingWhyYoullLoveThis = false
                    )
                }
            } catch (e: Exception) {
                // Personalized pitch is non-critical
                _state.value = _state.value.copy(
                    whyYoullLoveThis = null,
                    isLoadingWhyYoullLoveThis = false
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
