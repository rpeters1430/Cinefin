package com.example.jellyfinandroid.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jellyfinandroid.data.repository.common.ApiResult
import com.example.jellyfinandroid.data.repository.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

data class SeasonEpisodesState(
    val episodes: List<BaseItemDto> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class SeasonEpisodesViewModel @Inject constructor(
    private val repository: JellyfinRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SeasonEpisodesState())
    val state: StateFlow<SeasonEpisodesState> = _state.asStateFlow()

    private var currentSeasonId: String? = null

    fun loadEpisodes(seasonId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            currentSeasonId = seasonId

            when (val result = repository.getEpisodesForSeason(seasonId)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(
                        episodes = result.data,
                        isLoading = false,
                    )
                }
                is ApiResult.Error -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to load episodes: ${result.message}",
                    )
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }
        }
    }

    fun refresh() {
        currentSeasonId?.let { loadEpisodes(it) }
    }
}
