package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.repository.IJellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

data class PlaylistsState(
    val isLoading: Boolean = false,
    val playlists: List<BaseItemDto> = emptyList(),
    val searchQuery: String = "",
    val errorMessage: String? = null,
) {
    val filteredPlaylists: List<BaseItemDto>
        get() = if (searchQuery.isBlank()) {
            playlists
        } else {
            playlists.filter { playlist ->
                (playlist.name?.contains(searchQuery, ignoreCase = true) == true) ||
                    (playlist.overview?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
}

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repository: IJellyfinRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PlaylistsState())
    val state: StateFlow<PlaylistsState> = _state.asStateFlow()

    init {
        loadPlaylists()
    }

    fun loadPlaylists(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)

            val result = repository.getPlaylists(limit = 200)
            when (result) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        playlists = result.data,
                        errorMessage = null,
                    )
                }
                is ApiResult.Error -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = result.message,
                    )
                }
                else -> {
                    _state.value = _state.value.copy(isLoading = false)
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }
}
