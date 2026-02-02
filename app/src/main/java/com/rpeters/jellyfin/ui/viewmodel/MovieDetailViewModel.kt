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
)

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val repository: JellyfinRepository,
    private val mediaRepository: JellyfinMediaRepository,
    private val enhancedPlaybackUtils: EnhancedPlaybackUtils,
    private val generativeAiRepository: GenerativeAiRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MovieDetailState())
    val state: StateFlow<MovieDetailState> = _state.asStateFlow()

    fun loadMovieDetails(movieId: String) {
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

                    // Load similar movies in background
                    loadSimilarMovies(movieId)
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
                throw e
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clear any loaded movie data to prevent memory leaks
        _state.value = MovieDetailState()
    }
}
