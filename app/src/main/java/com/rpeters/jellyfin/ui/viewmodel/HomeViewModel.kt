package com.rpeters.jellyfin.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import javax.inject.Inject

/**
 * ViewModel responsible for the home screen functionality.
 * Handles library discovery, recently added content, and home screen state.
 */
data class HomeState(
    val isLoading: Boolean = false,
    val libraries: List<BaseItemDto> = emptyList(),
    val recentlyAdded: List<BaseItemDto> = emptyList(),
    val recentlyAddedByTypes: Map<String, List<BaseItemDto>> = emptyMap(),
    val errorMessage: String? = null,
    val isRefreshing: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: JellyfinMediaRepository,
) : ViewModel() {

    private val _homeState = MutableStateFlow(HomeState())
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()

    companion object {
        private const val TAG = "HomeViewModel"
        private const val RECENTLY_ADDED_LIMIT = 50
        private const val RECENTLY_ADDED_BY_TYPE_LIMIT = 20
    }

    init {
        loadHomeData()
    }

    /**
     * Load all home screen data including libraries and recently added content.
     */
    fun loadHomeData() {
        viewModelScope.launch {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Loading home screen data")
            }

            _homeState.value = _homeState.value.copy(isLoading = true, errorMessage = null)

            try {
                // Load libraries and recently added content in parallel
                loadLibraries()
                loadRecentlyAdded()
                loadRecentlyAddedByTypes()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Error loading home data", e)
                }
                _homeState.value = _homeState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load home screen data",
                )
            } finally {
                _homeState.value = _homeState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Refresh all home screen data.
     */
    fun refreshHomeData() {
        viewModelScope.launch {
            _homeState.value = _homeState.value.copy(isRefreshing = true)

            try {
                // Force refresh by clearing cache first (would need cache invalidation)
                loadLibraries()
                loadRecentlyAdded()
                loadRecentlyAddedByTypes()
            } finally {
                _homeState.value = _homeState.value.copy(isRefreshing = false)
            }
        }
    }

    /**
     * Load user libraries.
     */
    private suspend fun loadLibraries() {
        when (val result = mediaRepository.getUserLibraries()) {
            is ApiResult.Success -> {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Loaded ${result.data.size} libraries")
                }
                _homeState.value = _homeState.value.copy(libraries = result.data)
            }
            is ApiResult.Error -> {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Failed to load libraries: ${result.message}")
                }
                _homeState.value = _homeState.value.copy(
                    errorMessage = "Failed to load libraries: ${result.message}",
                )
            }
            is ApiResult.Loading -> {
                // Already handled by isLoading state
            }
        }
    }

    /**
     * Load recently added content across all types.
     */
    private suspend fun loadRecentlyAdded() {
        when (val result = mediaRepository.getRecentlyAdded(RECENTLY_ADDED_LIMIT)) {
            is ApiResult.Success -> {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Loaded ${result.data.size} recently added items")
                }
                _homeState.value = _homeState.value.copy(recentlyAdded = result.data)
            }
            is ApiResult.Error -> {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Failed to load recently added: ${result.message}")
                }
                // Don't overwrite other errors, just log this one
            }
            is ApiResult.Loading -> {
                // Handled by overall loading state
            }
        }
    }

    /**
     * Load recently added content organized by type.
     */
    private suspend fun loadRecentlyAddedByTypes() {
        val contentTypes = listOf(
            BaseItemKind.MOVIE,
            BaseItemKind.SERIES,
            BaseItemKind.EPISODE,
            BaseItemKind.AUDIO,
            BaseItemKind.BOOK,
            BaseItemKind.AUDIO_BOOK,
            BaseItemKind.VIDEO,
        )

        val results = mutableMapOf<String, List<BaseItemDto>>()

        for (contentType in contentTypes) {
            when (val result = mediaRepository.getRecentlyAddedByType(contentType, RECENTLY_ADDED_BY_TYPE_LIMIT)) {
                is ApiResult.Success -> {
                    if (result.data.isNotEmpty()) {
                        val typeName = when (contentType) {
                            BaseItemKind.MOVIE -> "Movies"
                            BaseItemKind.SERIES -> "TV Shows"
                            BaseItemKind.EPISODE -> "Episodes"
                            BaseItemKind.AUDIO -> "Music"
                            BaseItemKind.BOOK -> "Books"
                            BaseItemKind.AUDIO_BOOK -> "Audiobooks"
                            BaseItemKind.VIDEO -> "Videos"
                            else -> "Other"
                        }
                        results[typeName] = result.data
                    }
                }
                is ApiResult.Error -> {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Failed to load $contentType: ${result.message}")
                    }
                    // Continue with other types even if one fails
                }
                is ApiResult.Loading -> {
                    // Handled by overall loading state
                }
            }
        }

        _homeState.value = _homeState.value.copy(recentlyAddedByTypes = results)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Loaded recently added by types: ${results.keys.joinToString()}")
        }
    }

    /**
     * Clear any error messages.
     */
    fun clearError() {
        _homeState.value = _homeState.value.copy(errorMessage = null)
    }

    /**
     * Clean up resources when ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "HomeViewModel cleared")
        }
    }
}
