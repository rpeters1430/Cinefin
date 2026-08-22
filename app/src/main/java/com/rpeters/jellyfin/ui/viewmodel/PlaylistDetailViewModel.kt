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

data class PlaylistDetailState(
    val isLoading: Boolean = false,
    val playlist: BaseItemDto? = null,
    val items: List<BaseItemDto> = emptyList(),
    val isFavorite: Boolean = false,
    val isYouTubePlaylist: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val repository: IJellyfinRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PlaylistDetailState())
    val state: StateFlow<PlaylistDetailState> = _state.asStateFlow()

    fun load(playlistId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)

            // Load playlist details
            val detailsResult = repository.getPlaylistDetails(playlistId)
            val playlist = when (detailsResult) {
                is ApiResult.Success -> detailsResult.data
                is ApiResult.Error -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = detailsResult.message,
                    )
                    return@launch
                }
                else -> null
            }

            // Load playlist items
            val itemsResult = repository.getPlaylistItems(playlistId)
            val items = when (itemsResult) {
                is ApiResult.Success -> itemsResult.data
                is ApiResult.Error -> emptyList()
                else -> emptyList()
            }

            val isYouTube = isYouTubePlaylistMetadata(playlist, items)
            val isFav = playlist?.userData?.isFavorite == true

            _state.value = _state.value.copy(
                isLoading = false,
                playlist = playlist,
                items = items,
                isFavorite = isFav,
                isYouTubePlaylist = isYouTube,
            )
        }
    }

    fun toggleFavorite() {
        val currentPlaylist = _state.value.playlist ?: return
        val currentFav = _state.value.isFavorite
        val newFav = !currentFav

        _state.value = _state.value.copy(isFavorite = newFav)

        viewModelScope.launch {
            val result = repository.toggleFavorite(currentPlaylist.id.toString(), newFav)
            if (result is ApiResult.Error) {
                // Revert on failure
                _state.value = _state.value.copy(isFavorite = currentFav)
            }
        }
    }

    private fun isYouTubePlaylistMetadata(playlist: BaseItemDto?, items: List<BaseItemDto>): Boolean {
        if (playlist == null) return false

        val checkString = buildString {
            append(playlist.name ?: "")
            append(" ")
            append(playlist.overview ?: "")
            append(" ")
            playlist.tags?.forEach { append(it).append(" ") }
            playlist.genres?.forEach { append(it).append(" ") }
            playlist.studios?.forEach { append(it.name ?: "").append(" ") }
        }.lowercase()

        if (checkString.contains("youtube") ||
            checkString.contains("youtarr") ||
            checkString.contains("yt-dlp") ||
            checkString.contains("tubesync")
        ) {
            return true
        }

        // Check if any items have YouTube tags / studios / overview markers
        return items.take(5).any { item ->
            val itemCheck = buildString {
                append(item.name ?: "")
                append(" ")
                append(item.overview ?: "")
                append(" ")
                item.tags?.forEach { append(it).append(" ") }
                item.studios?.forEach { append(it.name ?: "").append(" ") }
            }.lowercase()

            itemCheck.contains("youtube") ||
                itemCheck.contains("youtarr") ||
                itemCheck.contains("yt-dlp") ||
                itemCheck.contains("youtu.be")
        }
    }
}
