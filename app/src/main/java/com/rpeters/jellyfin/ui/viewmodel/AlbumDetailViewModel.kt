package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

data class AlbumDetailState(
    val isLoading: Boolean = false,
    val album: BaseItemDto? = null,
    val tracks: List<BaseItemDto> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val mediaRepository: JellyfinMediaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AlbumDetailState())
    val state: StateFlow<AlbumDetailState> = _state.asStateFlow()

    fun load(albumId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)

            // Load album details
            val albumResult = mediaRepository.getAlbumDetails(albumId)
            val album = when (albumResult) {
                is ApiResult.Success -> albumResult.data
                is ApiResult.Error -> {
                    _state.value = _state.value.copy(isLoading = false, errorMessage = albumResult.message)
                    return@launch
                }
                else -> null
            }

            // Load tracks
            val tracksResult = mediaRepository.getAlbumTracks(albumId)
            val tracks = when (tracksResult) {
                is ApiResult.Success -> tracksResult.data
                is ApiResult.Error -> emptyList()
                else -> emptyList()
            }

            _state.value = _state.value.copy(
                isLoading = false,
                album = album,
                tracks = tracks,
            )
        }
    }
}
