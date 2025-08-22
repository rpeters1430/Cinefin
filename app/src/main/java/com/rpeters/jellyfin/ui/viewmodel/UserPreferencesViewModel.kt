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

/**
 * ViewModel responsible for user-specific operations like favorites,
 * watch status, and user preferences.
 */
data class UserPreferencesState(
    val favorites: List<BaseItemDto> = emptyList(),
    val isLoadingFavorites: Boolean = false,
    val isUpdatingFavorite: Boolean = false,
    val isUpdatingWatchStatus: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class UserPreferencesViewModel @Inject constructor(
    private val userRepository: JellyfinUserRepository,
) : ViewModel() {

    private val _userState = MutableStateFlow(UserPreferencesState())
    val userState: StateFlow<UserPreferencesState> = _userState.asStateFlow()

    companion object {
        private const val TAG = "UserPreferencesViewModel"
    }

    init {
        loadFavorites()
    }

    /**
     * Load user's favorite items.
     */
    fun loadFavorites() {
        viewModelScope.launch {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Loading user favorites")
            }

            _userState.value = _userState.value.copy(
                isLoadingFavorites = true,
                errorMessage = null,
            )

            when (val result = userRepository.getFavorites()) {
                is ApiResult.Success -> {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Loaded ${result.data.size} favorites")
                    }

                    _userState.value = _userState.value.copy(
                        favorites = result.data,
                        isLoadingFavorites = false,
                    )
                }
                is ApiResult.Error -> {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Failed to load favorites: ${result.message}")
                    }

                    _userState.value = _userState.value.copy(
                        isLoadingFavorites = false,
                        errorMessage = "Failed to load favorites: ${result.message}",
                    )
                }
                is ApiResult.Loading -> {
                    // Already handled by isLoadingFavorites
                }
            }
        }
    }

    /**
     * Toggle favorite status for an item.
     */
    fun toggleFavorite(item: BaseItemDto, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Toggling favorite for item: ${item.name}")
            }

            _userState.value = _userState.value.copy(
                isUpdatingFavorite = true,
                errorMessage = null,
            )

            val isFavorite = item.userData?.isFavorite ?: false

            when (val result = userRepository.toggleFavorite(item.id.toString(), isFavorite)) {
                is ApiResult.Success -> {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Successfully toggled favorite for ${item.name}")
                    }

                    // Update the item in favorites list
                    val updatedFavorites = if (result.data) {
                        // Item was added to favorites
                        _userState.value.favorites + item.copy(
                            userData = item.userData?.copy(isFavorite = true),
                        )
                    } else {
                        // Item was removed from favorites
                        _userState.value.favorites.filter { it.id != item.id }
                    }

                    _userState.value = _userState.value.copy(
                        favorites = updatedFavorites,
                        isUpdatingFavorite = false,
                        successMessage = if (result.data) "Added to favorites" else "Removed from favorites",
                    )

                    onResult(true, null)
                }
                is ApiResult.Error -> {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Failed to toggle favorite: ${result.message}")
                    }

                    _userState.value = _userState.value.copy(
                        isUpdatingFavorite = false,
                        errorMessage = "Failed to update favorite: ${result.message}",
                    )

                    onResult(false, result.message)
                }
                is ApiResult.Loading -> {
                    // Already handled by isUpdatingFavorite
                }
            }
        }
    }

    /**
     * Mark an item as watched.
     */
    fun markAsWatched(item: BaseItemDto, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Marking item as watched: ${item.name}")
            }

            _userState.value = _userState.value.copy(
                isUpdatingWatchStatus = true,
                errorMessage = null,
            )

            when (val result = userRepository.markAsWatched(item.id.toString())) {
                is ApiResult.Success -> {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Successfully marked ${item.name} as watched")
                    }

                    _userState.value = _userState.value.copy(
                        isUpdatingWatchStatus = false,
                        successMessage = "Marked as watched",
                    )

                    onResult(true, null)
                }
                is ApiResult.Error -> {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Failed to mark as watched: ${result.message}")
                    }

                    _userState.value = _userState.value.copy(
                        isUpdatingWatchStatus = false,
                        errorMessage = "Failed to mark as watched: ${result.message}",
                    )

                    onResult(false, result.message)
                }
                is ApiResult.Loading -> {
                    // Already handled by isUpdatingWatchStatus
                }
            }
        }
    }

    /**
     * Mark an item as unwatched.
     */
    fun markAsUnwatched(item: BaseItemDto, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Marking item as unwatched: ${item.name}")
            }

            _userState.value = _userState.value.copy(
                isUpdatingWatchStatus = true,
                errorMessage = null,
            )

            when (val result = userRepository.markAsUnwatched(item.id.toString())) {
                is ApiResult.Success -> {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Successfully marked ${item.name} as unwatched")
                    }

                    _userState.value = _userState.value.copy(
                        isUpdatingWatchStatus = false,
                        successMessage = "Marked as unwatched",
                    )

                    onResult(true, null)
                }
                is ApiResult.Error -> {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Failed to mark as unwatched: ${result.message}")
                    }

                    _userState.value = _userState.value.copy(
                        isUpdatingWatchStatus = false,
                        errorMessage = "Failed to mark as unwatched: ${result.message}",
                    )

                    onResult(false, result.message)
                }
                is ApiResult.Loading -> {
                    // Already handled by isUpdatingWatchStatus
                }
            }
        }
    }

    /**
     * Delete an item (admin only).
     */
    fun deleteItem(item: BaseItemDto, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Attempting to delete item: ${item.name}")
            }

            when (val result = userRepository.deleteItemAsAdmin(item.id.toString())) {
                is ApiResult.Success -> {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Successfully deleted ${item.name}")
                    }

                    // Remove from favorites if it was there
                    val updatedFavorites = _userState.value.favorites.filter { it.id != item.id }

                    _userState.value = _userState.value.copy(
                        favorites = updatedFavorites,
                        successMessage = "Item deleted successfully",
                    )

                    onResult(true, null)
                }
                is ApiResult.Error -> {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Failed to delete item: ${result.message}")
                    }

                    _userState.value = _userState.value.copy(
                        errorMessage = "Failed to delete item: ${result.message}",
                    )

                    onResult(false, result.message)
                }
                is ApiResult.Loading -> {
                    // Loading state could be added if needed
                }
            }
        }
    }

    /**
     * Logout the current user.
     */
    fun logout() {
        viewModelScope.launch {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Logging out user")
            }

            userRepository.logout()

            // Clear user-specific data
            _userState.value = UserPreferencesState()
        }
    }

    /**
     * Clear any messages (success or error).
     */
    fun clearMessages() {
        _userState.value = _userState.value.copy(
            errorMessage = null,
            successMessage = null,
        )
    }

    /**
     * Clear any error messages.
     */
    fun clearError() {
        _userState.value = _userState.value.copy(errorMessage = null)
    }

    /**
     * Clean up resources when ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "UserPreferencesViewModel cleared")
        }
    }
}
