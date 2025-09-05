package com.rpeters.jellyfin.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.utils.MainThreadMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
            MainThreadMonitor.warnIfMainThread("HomeViewModel.loadHomeData")

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Loading home screen data")
            }

            // Check authentication before attempting data load
            // Note: We don't have direct access to repository here, so we'll rely on the repository's error handling
            _homeState.value = _homeState.value.copy(isLoading = true, errorMessage = null)

            try {
                // Load libraries and recently added content in parallel for better performance
                coroutineScope {
                    val librariesDeferred = async { loadLibraries() }
                    val recentlyAddedDeferred = async { loadRecentlyAdded() }
                    val recentlyAddedByTypesDeferred = async { loadRecentlyAddedByTypes() }

                    // Wait for all operations to complete
                    librariesDeferred.await()
                    recentlyAddedDeferred.await()
                    recentlyAddedByTypesDeferred.await()
                }
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
                // Force refresh by running operations in parallel
                coroutineScope {
                    val librariesDeferred = async { loadLibraries(forceRefresh = true) }
                    val recentlyAddedDeferred = async { loadRecentlyAdded(forceRefresh = true) }
                    val recentlyAddedByTypesDeferred = async { loadRecentlyAddedByTypes(forceRefresh = true) }

                    // Wait for all operations to complete
                    librariesDeferred.await()
                    recentlyAddedDeferred.await()
                    recentlyAddedByTypesDeferred.await()
                }
            } finally {
                _homeState.value = _homeState.value.copy(isRefreshing = false)
            }
        }
    }

    /**
     * Load user libraries.
     */
    internal suspend fun loadLibraries(forceRefresh: Boolean = false) {
        val result = mediaRepository.getUserLibraries(forceRefresh)

        when (result) {
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
    internal suspend fun loadRecentlyAdded(forceRefresh: Boolean = false) {
        val result = mediaRepository.getRecentlyAdded(RECENTLY_ADDED_LIMIT, forceRefresh)

        when (result) {
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
     * Now optimized to run all API calls in parallel instead of sequential.
     */
    internal suspend fun loadRecentlyAddedByTypes(forceRefresh: Boolean = false) {
        val contentTypes = listOf(
            BaseItemKind.MOVIE,
            BaseItemKind.SERIES,
            BaseItemKind.EPISODE,
            BaseItemKind.AUDIO,
            BaseItemKind.BOOK,
            BaseItemKind.AUDIO_BOOK,
            BaseItemKind.VIDEO,
        )

        // Run all API calls in parallel for much better performance
        val deferredResults = coroutineScope {
            contentTypes.map { contentType ->
                async {
                    val result = mediaRepository.getRecentlyAddedByType(contentType, RECENTLY_ADDED_BY_TYPE_LIMIT, forceRefresh)
                    Pair(contentType, result)
                }
            }
        }

        val results = mutableMapOf<String, List<BaseItemDto>>()

        // Wait for all results and process them
        deferredResults.forEach { deferred ->
            val (contentType, result) = deferred.await()

            when (result) {
                is ApiResult.Success -> {
                    if (result.data.isNotEmpty()) {
                        results[contentType.name] = result.data
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
     * Backwards-compatible refresh API expected by older tests.
     */
    fun refreshAll() = refreshHomeData()

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
