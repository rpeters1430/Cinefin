package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import javax.inject.Inject

data class TVSeasonState(
    val seriesDetails: BaseItemDto? = null,
    val seasons: List<BaseItemDto> = emptyList(),
    val similarSeries: List<BaseItemDto> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class TVSeasonViewModel @Inject constructor(
    private val repository: JellyfinRepository,
    private val mediaRepository: JellyfinMediaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TVSeasonState())
    val state: StateFlow<TVSeasonState> = _state.asStateFlow()

    fun loadSeriesData(seriesId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)

            var seriesDetails = _state.value.seriesDetails
            var seasons = _state.value.seasons
            var similarSeries = _state.value.similarSeries
            var errorMessage: String? = null

            // Load series details
            when (val seriesResult = repository.getSeriesDetails(seriesId)) {
                is ApiResult.Success -> {
                    seriesDetails = seriesResult.data
                }
                is ApiResult.Error -> {
                    errorMessage = "Failed to load series details: ${seriesResult.message}"
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }

            // Load seasons
            when (val seasonsResult = mediaRepository.getSeasonsForSeries(seriesId)) {
                is ApiResult.Success -> {
                    seasons = seasonsResult.data
                }
                is ApiResult.Error -> {
                    errorMessage = errorMessage ?: "Failed to load seasons: ${seasonsResult.message}"
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }

            // Load similar series
            when (val similarResult = mediaRepository.getSimilarSeries(seriesId)) {
                is ApiResult.Success -> {
                    similarSeries = similarResult.data
                        .filter { it.type == BaseItemKind.SERIES }
                        .filterNot { it.id?.toString() == seriesId }
                }

                is ApiResult.Error -> {
                    if (errorMessage == null) {
                        errorMessage = "Failed to load similar series: ${similarResult.message}"
                    }
                }

                is ApiResult.Loading -> {
                    // Already handled
                }
            }

            _state.value = _state.value.copy(
                seriesDetails = seriesDetails,
                seasons = seasons,
                similarSeries = similarSeries,
                isLoading = false,
                errorMessage = errorMessage,
            )
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun refresh() {
        val seriesId = _state.value.seriesDetails?.id?.toString()
        if (seriesId != null) {
            loadSeriesData(seriesId)
        }
    }
}
