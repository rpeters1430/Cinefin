package com.rpeters.jellyfin.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.repository.JellyfinUserRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

data class UserActionsState(
    val favorites: List<BaseItemDto> = emptyList(),
    val isLoadingFavorites: Boolean = false,
    val isProcessingAction: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

/**
 * Dedicated ViewModel for user actions like favorites, watch status, etc.
 * Extracted from MainAppViewModel to reduce complexity and prevent merge conflicts.
 */
@HiltViewModel
class UserActionsViewModel @Inject constructor(
    private val userRepository: JellyfinUserRepository,
) : ViewModel() {

    private val _userActionsState = MutableStateFlow(UserActionsState())
    val userActionsState: StateFlow<UserActionsState> = _userActionsState.asStateFlow()

    /**
     * Load user favorites
     */
    fun loadFavorites() {
        viewModelScope.launch {
            _userActionsState.value = _userActionsState.value.copy(
                isLoadingFavorites = true,
                errorMessage = null,
            )

            when (val result = userRepository.getFavorites()) {
                is ApiResult.Success -> {
                    _userActionsState.value = _userActionsState.value.copy(
                        favorites = result.data,
                        isLoadingFavorites = false,
                    )

                    if (BuildConfig.DEBUG) {
                        Log.d("UserActionsViewModel", "loadFavorites: Loaded ${result.data.size} favorites")
                    }
                }
                is ApiResult.Error -> {
                    _userActionsState.value = _userActionsState.value.copy(
                        isLoadingFavorites = false,
                        errorMessage = "Failed to load favorites: ${result.message}",
                    )

                    Log.e("UserActionsViewModel", "loadFavorites: Failed to load favorites: ${result.message}")
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }
        }
    }

    /**
     * Toggle favorite status for an item
     */
    fun toggleFavorite(item: BaseItemDto, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            _userActionsState.value = _userActionsState.value.copy(
                isProcessingAction = true,
                errorMessage = null,
            )

            val currentFavoriteState = item.userData?.isFavorite ?: false
            val newFavoriteState = !currentFavoriteState

            when (val result = userRepository.toggleFavorite(item.id.toString(), newFavoriteState)) {
                is ApiResult.Success -> {
                    _userActionsState.value = _userActionsState.value.copy(
                        isProcessingAction = false,
                        successMessage = if (newFavoriteState) "Added to favorites" else "Removed from favorites",
                    )

                    if (BuildConfig.DEBUG) {
                        Log.d("UserActionsViewModel", "Successfully toggled favorite for ${item.name}")
                    }

                    // Update local favorites list
                    val updatedFavorites = if (newFavoriteState) {
                        _userActionsState.value.favorites + item
                    } else {
                        _userActionsState.value.favorites.filterNot { it.id == item.id }
                    }

                    _userActionsState.value = _userActionsState.value.copy(favorites = updatedFavorites)
                    onSuccess?.invoke()
                }
                is ApiResult.Error -> {
                    _userActionsState.value = _userActionsState.value.copy(
                        isProcessingAction = false,
                        errorMessage = "Failed to update favorite: ${result.message}",
                    )

                    Log.e("UserActionsViewModel", "Failed to toggle favorite: ${result.message}")
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }
        }
    }

    /**
     * Mark an item as watched
     */
    fun markAsWatched(item: BaseItemDto, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            _userActionsState.value = _userActionsState.value.copy(
                isProcessingAction = true,
                errorMessage = null,
            )

            when (val result = userRepository.markAsWatched(item.id.toString())) {
                is ApiResult.Success -> {
                    _userActionsState.value = _userActionsState.value.copy(
                        isProcessingAction = false,
                        successMessage = "Marked as watched",
                    )

                    if (BuildConfig.DEBUG) {
                        Log.d("UserActionsViewModel", "Successfully marked ${item.name} as watched")
                    }

                    onSuccess?.invoke()
                }
                is ApiResult.Error -> {
                    _userActionsState.value = _userActionsState.value.copy(
                        isProcessingAction = false,
                        errorMessage = "Failed to mark as watched: ${result.message}",
                    )

                    Log.e("UserActionsViewModel", "Failed to mark as watched: ${result.message}")
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }
        }
    }

    /**
     * Mark an item as unwatched
     */
    fun markAsUnwatched(item: BaseItemDto, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            _userActionsState.value = _userActionsState.value.copy(
                isProcessingAction = true,
                errorMessage = null,
            )

            when (val result = userRepository.markAsUnwatched(item.id.toString())) {
                is ApiResult.Success -> {
                    _userActionsState.value = _userActionsState.value.copy(
                        isProcessingAction = false,
                        successMessage = "Marked as unwatched",
                    )

                    if (BuildConfig.DEBUG) {
                        Log.d("UserActionsViewModel", "Successfully marked ${item.name} as unwatched")
                    }

                    onSuccess?.invoke()
                }
                is ApiResult.Error -> {
                    _userActionsState.value = _userActionsState.value.copy(
                        isProcessingAction = false,
                        errorMessage = "Failed to mark as unwatched: ${result.message}",
                    )

                    Log.e("UserActionsViewModel", "Failed to mark as unwatched: ${result.message}")
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }
        }
    }

    /**
     * Delete an item (admin function)
     */
    fun deleteItem(item: BaseItemDto, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _userActionsState.value = _userActionsState.value.copy(
                isProcessingAction = true,
                errorMessage = null,
            )

            when (val result = userRepository.deleteItemAsAdmin(item.id.toString())) {
                is ApiResult.Success -> {
                    _userActionsState.value = _userActionsState.value.copy(
                        isProcessingAction = false,
                        successMessage = "Item deleted successfully",
                    )

                    if (BuildConfig.DEBUG) {
                        Log.d("UserActionsViewModel", "Successfully deleted ${item.name}")
                    }

                    // Remove from favorites if it was favorited
                    val updatedFavorites = _userActionsState.value.favorites.filterNot { it.id == item.id }
                    _userActionsState.value = _userActionsState.value.copy(favorites = updatedFavorites)

                    onResult(true, null)
                }
                is ApiResult.Error -> {
                    _userActionsState.value = _userActionsState.value.copy(
                        isProcessingAction = false,
                        errorMessage = "Failed to delete item: ${result.message}",
                    )

                    Log.e("UserActionsViewModel", "Failed to delete item: ${result.message}")
                    onResult(false, result.message)
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }
        }
    }

    /**
     * Clear error messages
     */
    fun clearError() {
        _userActionsState.value = _userActionsState.value.copy(errorMessage = null)
    }

    /**
     * Clear success messages
     */
    fun clearSuccess() {
        _userActionsState.value = _userActionsState.value.copy(successMessage = null)
    }

    /**
     * Clear all messages
     */
    fun clearMessages() {
        _userActionsState.value = _userActionsState.value.copy(
            errorMessage = null,
            successMessage = null,
        )
    }
}
