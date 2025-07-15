package com.example.jellyfinandroid.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jellyfinandroid.data.repository.ApiResult
import com.example.jellyfinandroid.data.repository.JellyfinRepository
import com.example.jellyfinandroid.data.SecureCredentialManager
import org.jellyfin.sdk.model.api.BaseItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainAppState(
    val isLoading: Boolean = false,
    val libraries: List<BaseItemDto> = emptyList(),
    val recentlyAdded: List<BaseItemDto> = emptyList(),
    val recentlyAddedByTypes: Map<String, List<BaseItemDto>> = emptyMap(),
    val favorites: List<BaseItemDto> = emptyList(),
    val searchResults: List<BaseItemDto> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val allItems: List<BaseItemDto> = emptyList(),
    val isLoadingMore: Boolean = false,
    val hasMoreItems: Boolean = true,
    val currentPage: Int = 0,
    val errorMessage: String? = null
)

data class PaginatedItems(
    val items: List<BaseItemDto>,
    val hasMore: Boolean,
    val totalCount: Int? = null
)

@HiltViewModel
class MainAppViewModel @Inject constructor(
    private val repository: JellyfinRepository,
    private val credentialManager: SecureCredentialManager
) : ViewModel() {
    
    private val _appState = MutableStateFlow(MainAppState())
    val appState: StateFlow<MainAppState> = _appState.asStateFlow()
    
    val currentServer = repository.currentServer
    val isConnected = repository.isConnected
    
    init {
        loadInitialData()
    }
    
    fun loadInitialData() {
        viewModelScope.launch {
            Log.d("MainAppViewModel", "loadInitialData: Starting to load all data")
            _appState.value = _appState.value.copy(isLoading = true, errorMessage = null)
            
            // Load libraries
            Log.d("MainAppViewModel", "loadInitialData: Loading libraries")
            when (val result = repository.getUserLibraries()) {
                is ApiResult.Success -> {
                    Log.d("MainAppViewModel", "loadInitialData: Loaded ${result.data.size} libraries")
                    _appState.value = _appState.value.copy(libraries = result.data)
                }
                is ApiResult.Error -> {
                    // ✅ FIX: Don't show error messages for cancelled operations (navigation/lifecycle)
                    if (result.errorType == com.example.jellyfinandroid.data.repository.ErrorType.OPERATION_CANCELLED) {
                        Log.d("MainAppViewModel", "loadInitialData: Library loading was cancelled (navigation)")
                    } else {
                        Log.e("MainAppViewModel", "loadInitialData: Failed to load libraries: ${result.message}")
                        _appState.value = _appState.value.copy(
                            errorMessage = "Failed to load libraries: ${result.message}"
                        )
                    }
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }
            
            // Load recently added items
            Log.d("MainAppViewModel", "loadInitialData: Loading recently added items")
            when (val result = repository.getRecentlyAdded()) {
                is ApiResult.Success -> {
                    Log.d("MainAppViewModel", "loadInitialData: Loaded ${result.data.size} recently added items")
                    _appState.value = _appState.value.copy(recentlyAdded = result.data)
                }
                is ApiResult.Error -> {
                    // ✅ FIX: Don't show error messages for cancelled operations
                    if (result.errorType == com.example.jellyfinandroid.data.repository.ErrorType.OPERATION_CANCELLED) {
                        Log.d("MainAppViewModel", "loadInitialData: Recent items loading was cancelled (navigation)")
                    } else {
                        Log.e("MainAppViewModel", "loadInitialData: Failed to load recent items: ${result.message}")
                        // Don't override library error, just log this
                        if (_appState.value.errorMessage == null) {
                            _appState.value = _appState.value.copy(
                                errorMessage = "Failed to load recent items: ${result.message}"
                            )
                        }
                    }
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }

            // Load recently added items by types
            Log.d("MainAppViewModel", "loadInitialData: Loading recently added items by types")
            when (val result = repository.getRecentlyAddedByTypes(limit = 20)) {
                is ApiResult.Success -> {
                    val totalItems = result.data.values.sumOf { it.size }
                    Log.d("MainAppViewModel", "loadInitialData: Loaded $totalItems items across ${result.data.size} types: ${result.data.keys.joinToString(", ")}")
                    result.data.forEach { (type, items) ->
                        Log.d("MainAppViewModel", "loadInitialData: $type: ${items.size} items")
                    }
                    _appState.value = _appState.value.copy(recentlyAddedByTypes = result.data)
                }
                is ApiResult.Error -> {
                    // ✅ FIX: Don't show error messages for cancelled operations  
                    if (result.errorType == com.example.jellyfinandroid.data.repository.ErrorType.OPERATION_CANCELLED) {
                        Log.d("MainAppViewModel", "loadInitialData: Recent items by type loading was cancelled (navigation)")
                    } else {
                        Log.e("MainAppViewModel", "loadInitialData: Failed to load recent items by type: ${result.message}")
                        // Don't override other errors, just log this
                        if (_appState.value.errorMessage == null) {
                            _appState.value = _appState.value.copy(
                                errorMessage = "Failed to load recent items by type: ${result.message}"
                            )
                        }
                    }
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }
            
            // Load initial page of items for library type screens
            Log.d("MainAppViewModel", "loadInitialData: Loading library items page")
            loadLibraryItemsPage(reset = true)
            
            _appState.value = _appState.value.copy(isLoading = false)
            Log.d("MainAppViewModel", "loadInitialData: Completed loading all data")
        }
    }
    
    fun loadFavorites() {
        viewModelScope.launch {
            _appState.value = _appState.value.copy(isLoading = true, errorMessage = null)
            
            when (val result = repository.getFavorites()) {
                is ApiResult.Success -> {
                    _appState.value = _appState.value.copy(
                        favorites = result.data,
                        isLoading = false
                    )
                }
                is ApiResult.Error -> {
                    _appState.value = _appState.value.copy(
                        errorMessage = "Failed to load favorites: ${result.message}",
                        isLoading = false
                    )
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }
        }
    }
    
    fun clearError() {
        _appState.value = _appState.value.copy(errorMessage = null)
    }
    
    fun logout() {
        repository.logout()
        // Clear saved credentials on logout
        viewModelScope.launch {
            credentialManager.clearCredentials()
        }
    }
    
    fun getImageUrl(item: BaseItemDto): String? {
        return repository.getImageUrl(item.id.toString(), "Primary", null)
    }
    
    fun getBackdropUrl(item: BaseItemDto): String? {
        return repository.getBackdropUrl(item)
    }
    
    fun getSeriesImageUrl(item: BaseItemDto): String? {
        return repository.getSeriesImageUrl(item)
    }
    
    fun search(query: String) {
        viewModelScope.launch {
            _appState.value = _appState.value.copy(
                searchQuery = query,
                isSearching = true,
                errorMessage = null
            )
            
            when (val result = repository.searchItems(query)) {
                is ApiResult.Success -> {
                    _appState.value = _appState.value.copy(
                        searchResults = result.data,
                        isSearching = false
                    )
                }
                is ApiResult.Error -> {
                    _appState.value = _appState.value.copy(
                        searchResults = emptyList(),
                        isSearching = false,
                        errorMessage = "Search failed: ${result.message}"
                    )
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }
        }
    }
    
    fun clearSearch() {
        _appState.value = _appState.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false
        )
    }
    
    private fun loadLibraryItemsPage(reset: Boolean = false) {
        viewModelScope.launch {
            val currentState = _appState.value
            
            if (reset) {
                Log.d("MainAppViewModel", "loadLibraryItemsPage: Resetting and loading first page")
                _appState.value = currentState.copy(
                    allItems = emptyList(),
                    currentPage = 0,
                    hasMoreItems = true,
                    isLoading = true
                )
            } else {
                Log.d("MainAppViewModel", "loadLibraryItemsPage: Loading next page")
                _appState.value = currentState.copy(isLoadingMore = true)
            }
            
            val pageSize = 50 // Reasonable page size
            val page = if (reset) 0 else currentState.currentPage + 1
            val startIndex = page * pageSize
            
            Log.d("MainAppViewModel", "loadLibraryItemsPage: Requesting page $page (startIndex: $startIndex, limit: $pageSize)")
            
            when (val result = repository.getLibraryItems(
                startIndex = startIndex,
                limit = pageSize
            )) {
                is ApiResult.Success -> {
                    val newItems = result.data
                    val allItems = if (reset) {
                        newItems
                    } else {
                        currentState.allItems + newItems
                    }
                    
                    Log.d("MainAppViewModel", "loadLibraryItemsPage: Successfully loaded ${newItems.size} items for page $page")
                    Log.d("MainAppViewModel", "loadLibraryItemsPage: Total items now: ${allItems.size}")
                    
                    // Log item types breakdown
                    val typeBreakdown = allItems.groupBy { it.type }.mapValues { it.value.size }
                    Log.d("MainAppViewModel", "loadLibraryItemsPage: Item types breakdown: $typeBreakdown")
                    
                    _appState.value = _appState.value.copy(
                        allItems = allItems,
                        currentPage = page,
                        hasMoreItems = newItems.size == pageSize, // If we got less than pageSize, no more items
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = null
                    )
                }
                is ApiResult.Error -> {
                    Log.e("MainAppViewModel", "loadLibraryItemsPage: Failed to load page $page: ${result.message}")
                    _appState.value = _appState.value.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = if (reset) "Failed to load items: ${result.message}" else result.message
                    )
                }
                is ApiResult.Loading -> {
                    // Already handled above
                }
            }
        }
    }
    
    fun loadMoreItems() {
        val currentState = _appState.value
        if (!currentState.isLoadingMore && currentState.hasMoreItems) {
            loadLibraryItemsPage(reset = false)
        }
    }
    
    fun refreshLibraryItems() {
        loadLibraryItemsPage(reset = true)
    }
    
    fun toggleFavorite(item: BaseItemDto) {
        viewModelScope.launch {
            val currentFavoriteState = item.userData?.isFavorite ?: false
            when (val result = repository.toggleFavorite(item.id.toString(), !currentFavoriteState)) {
                is ApiResult.Success -> {
                    Log.d("MainAppViewModel", "Successfully toggled favorite for ${item.name}")
                    // Refresh data to update UI state
                    loadInitialData()
                }
                is ApiResult.Error -> {
                    Log.e("MainAppViewModel", "Failed to toggle favorite: ${result.message}")
                    _appState.value = _appState.value.copy(
                        errorMessage = "Failed to update favorite: ${result.message}"
                    )
                }
                is ApiResult.Loading -> {
                    // Handle loading state if needed
                }
            }
        }
    }

    fun markAsWatched(item: BaseItemDto) {
        viewModelScope.launch {
            when (val result = repository.markAsWatched(item.id.toString())) {
                is ApiResult.Success -> {
                    Log.d("MainAppViewModel", "Successfully marked ${item.name} as watched")
                    // Refresh data to update UI state
                    loadInitialData()
                }
                is ApiResult.Error -> {
                    Log.e("MainAppViewModel", "Failed to mark as watched: ${result.message}")
                    _appState.value = _appState.value.copy(
                        errorMessage = "Failed to mark as watched: ${result.message}"
                    )
                }
                is ApiResult.Loading -> {
                    // Handle loading state if needed
                }
            }
        }
    }

    fun markAsUnwatched(item: BaseItemDto) {
        viewModelScope.launch {
            when (val result = repository.markAsUnwatched(item.id.toString())) {
                is ApiResult.Success -> {
                    Log.d("MainAppViewModel", "Successfully marked ${item.name} as unwatched")
                    // Refresh data to update UI state
                    loadInitialData()
                }
                is ApiResult.Error -> {
                    Log.e("MainAppViewModel", "Failed to mark as unwatched: ${result.message}")
                    _appState.value = _appState.value.copy(
                        errorMessage = "Failed to mark as unwatched: ${result.message}"
                    )
                }
                is ApiResult.Loading -> {
                    // Handle loading state if needed
                }
            }
        }
    }

    fun getStreamUrl(item: BaseItemDto): String? {
        return repository.getStreamUrl(item.id.toString())
    }
    
    /**
     * Gets the download URL for a media item.
     * This URL can be used with DownloadManager for offline storage.
     */
    fun getDownloadUrl(item: BaseItemDto): String? {
        return repository.getDownloadUrl(item.id.toString())
    }
    
    /**
     * Gets a direct stream URL optimized for downloads.
     */
    fun getDirectStreamUrl(item: BaseItemDto, container: String? = null): String? {
        return repository.getDirectStreamUrl(item.id.toString(), container)
    }

    fun getMovieDetails(movieId: String) {
        viewModelScope.launch {
            _appState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.getMovieDetails(movieId)) {
                is ApiResult.Success -> {
                    _appState.update { currentState ->
                        val items = currentState.allItems.toMutableList()
val items = currentState.allItems.associateBy { it.id.toString() }.toMutableMap()
        if (items.containsKey(movieId)) {
            items[movieId] = result.data
        } else {
            items[result.data.id.toString()] = result.data
        }
                        } else {
                            items.add(result.data)
                        }
                        currentState.copy(allItems = items, isLoading = false)
                    }
                }
                is ApiResult.Error -> {
                    _appState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to load movie details: ${result.message}"
                        )
                    }
                }
                is ApiResult.Loading -> {
                    // no-op
                }
            }
        }
    }

    // Navigation compatibility methods - no-op for now
    fun loadTVShowDetails(seriesId: String) {
        // Navigation compatibility method - actual details loading handled by dedicated ViewModels
    }
    
    fun loadMusic() {
        // Navigation compatibility method - music loading handled by existing functionality
    }
}
