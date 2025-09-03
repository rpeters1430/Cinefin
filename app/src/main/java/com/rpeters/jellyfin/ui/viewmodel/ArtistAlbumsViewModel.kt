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

data class ArtistAlbumsState(
    val albums: List<BaseItemDto> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ArtistAlbumsViewModel @Inject constructor(
    private val mediaRepository: JellyfinMediaRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ArtistAlbumsState())
    val state: StateFlow<ArtistAlbumsState> = _state.asStateFlow()

    fun loadAlbums(artistId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            when (val result = mediaRepository.getAlbumsForArtist(artistId)) {
                is ApiResult.Success -> _state.value = _state.value.copy(albums = result.data, isLoading = false)
                is ApiResult.Error -> _state.value = _state.value.copy(isLoading = false, errorMessage = result.message)
                is ApiResult.Loading -> Unit
            }
        }
    }
}
