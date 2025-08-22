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
import javax.inject.Inject

data class TVSeasonState(
    val seriesDetails: BaseItemDto? = null,
    val seasons: List<BaseItemDto> = emptyList(),
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

            // Load series details
            when (val seriesResult = repository.getSeriesDetails(seriesId)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(seriesDetails = seriesResult.data)
                }
                is ApiResult.Error -> {
                    _state.value = _state.value.copy(
                        errorMessage = "Failed to load series details: ${seriesResult.message}",
                    )
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }

            // Load seasons
            when (val seasonsResult = mediaRepository.getSeasonsForSeries(seriesId)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(
                        seasons = seasonsResult.data,
                        isLoading = false,
                    )
                }
                is ApiResult.Error -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = if (_state.value.errorMessage == null) {
                            "Failed to load seasons: ${seasonsResult.message}"
                        } else {
                            _state.value.errorMessage
                        },
                    )
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }
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
