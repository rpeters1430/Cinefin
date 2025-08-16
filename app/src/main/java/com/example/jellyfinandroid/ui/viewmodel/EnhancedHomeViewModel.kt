package com.example.jellyfinandroid.ui.viewmodel

import com.example.jellyfinandroid.data.repository.JellyfinMediaRepository
import com.example.jellyfinandroid.data.repository.JellyfinRepository
import com.example.jellyfinandroid.ui.viewmodel.common.BaseJellyfinViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

/**
 * Enhanced HomeViewModel demonstrating Phase 2 improvements:
 * - Smart retry logic with circuit breaker protection
 * - Intelligent caching with offline support
 * - Enhanced error handling with user-friendly messages
 * - Seamless loading state management
 *
 * This is a demonstration of how to use the new BaseJellyfinViewModel.
 */
@HiltViewModel
class EnhancedHomeViewModel @Inject constructor(
    private val repository: JellyfinRepository,
    private val mediaRepository: JellyfinMediaRepository,
) : BaseJellyfinViewModel() {

    private val _homeState = MutableStateFlow(HomeState())
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()

    val currentServer = repository.currentServer
    val isConnected = repository.isConnected

    init {
        loadHomeData()
    }

    /**
     * Loads all home screen data with smart retry and caching.
     * This method demonstrates the enhanced error handling capabilities.
     */
    fun loadHomeData() {
        // Load libraries with cache-first strategy
        executeOperation(
            operationName = "loadLibraries",
            showLoading = true,
            operation = { mediaRepository.getUserLibraries() },
            onSuccess = { libraries ->
                _homeState.value = _homeState.value.copy(libraries = libraries)
            },
        )

        // Load recently added content with cache and retry
        executeOperation(
            operationName = "loadRecentlyAdded",
            showLoading = false, // Don't show loading for secondary content
            operation = { mediaRepository.getRecentlyAdded(limit = 20) },
            onSuccess = { items ->
                _homeState.value = _homeState.value.copy(recentlyAdded = items)
            },
        )
    }

    /**
     * Refreshes home data by forcing cache invalidation.
     * This demonstrates the refresh functionality with enhanced UX.
     */
    fun refreshHomeData() {
        executeRefresh(
            operationName = "refreshLibraries",
            operation = { mediaRepository.refreshUserLibraries() },
            onSuccess = { libraries ->
                _homeState.value = _homeState.value.copy(libraries = libraries)
            },
        )

        executeRefresh(
            operationName = "refreshRecentlyAdded",
            operation = { mediaRepository.refreshRecentlyAdded(limit = 20) },
            onSuccess = { items ->
                _homeState.value = _homeState.value.copy(recentlyAdded = items)
            },
        )
    }

    /**
     * Demonstrates manual retry functionality.
     * Users can tap "retry" buttons in the UI to call this.
     */
    fun retryLastFailedOperation() {
        retryLastOperation()
    }

    /**
     * Demonstrates how to load content for a specific library with enhanced error handling.
     */
    fun loadLibraryContent(libraryId: String, libraryName: String) {
        executeOperation(
            operationName = "loadLibrary_$libraryId",
            showLoading = true,
            operation = {
                // This would typically be implemented in mediaRepository
                mediaRepository.getUserLibraries() // Placeholder
            },
            onSuccess = { items ->
                // Handle successful load
                _homeState.value = _homeState.value.copy(
                    selectedLibraryContent = items,
                    selectedLibraryName = libraryName,
                )
            },
            onError = { error ->
                // Custom error handling for this specific operation
                if (error.errorType.name.contains("NOT_FOUND")) {
                    // Show specific message for missing library
                    _homeState.value = _homeState.value.copy(
                        customErrorMessage = "Library '$libraryName' was not found. It may have been removed or you may not have access.",
                    )
                }
            },
        )
    }

    /**
     * Demonstrates bulk operations with parallel execution.
     */
    fun loadAllContentTypes() {
        executeOperation(
            operationName = "loadAllContent",
            showLoading = true,
            operation = {
                // For demo purposes, just combine the two main calls
                val librariesResult = mediaRepository.getUserLibraries()
                val recentResult = mediaRepository.getRecentlyAdded(limit = 20)

                when {
                    librariesResult is com.example.jellyfinandroid.data.repository.common.ApiResult.Success &&
                        recentResult is com.example.jellyfinandroid.data.repository.common.ApiResult.Success -> {
                        val allItems = librariesResult.data + recentResult.data
                        com.example.jellyfinandroid.data.repository.common.ApiResult.Success(allItems)
                    }
                    librariesResult is com.example.jellyfinandroid.data.repository.common.ApiResult.Error -> librariesResult
                    recentResult is com.example.jellyfinandroid.data.repository.common.ApiResult.Error -> recentResult
                    else -> com.example.jellyfinandroid.data.repository.common.ApiResult.Loading("Loading...")
                }
            },
            onSuccess = { allItems ->
                // Update state with all loaded content
                _homeState.value = _homeState.value.copy(
                    allContent = allItems,
                )
            },
        )
    }

    /**
     * Clears any custom error messages.
     */
    fun clearCustomError() {
        _homeState.value = _homeState.value.copy(customErrorMessage = null)
    }
}

/**
 * Home screen state with enhanced error tracking.
 */
data class HomeState(
    val libraries: List<BaseItemDto> = emptyList(),
    val recentlyAdded: List<BaseItemDto> = emptyList(),
    val selectedLibraryContent: List<BaseItemDto> = emptyList(),
    val selectedLibraryName: String = "",
    val allContent: List<BaseItemDto> = emptyList(),
    val customErrorMessage: String? = null,
)
