package com.example.jellyfinandroid.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jellyfinandroid.data.repository.ApiResult
import com.example.jellyfinandroid.data.repository.JellyfinRepository
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
    private val repository: JellyfinRepository
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
            _appState.value = _appState.value.copy(isLoading = true, errorMessage = null)
            
            // Load libraries
            when (val result = repository.getUserLibraries()) {
                is ApiResult.Success -> {
                    _appState.value = _appState.value.copy(libraries = result.data)
                }
                is ApiResult.Error -> {
                    _appState.value = _appState.value.copy(
                        errorMessage = "Failed to load libraries: ${result.message}"
                    )
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }
            
            // Load recently added items
            when (val result = repository.getRecentlyAdded()) {
                is ApiResult.Success -> {
                    _appState.value = _appState.value.copy(recentlyAdded = result.data)
                }
                is ApiResult.Error -> {
                    // Don't override library error, just log this
                    if (_appState.value.errorMessage == null) {
                        _appState.value = _appState.value.copy(
                            errorMessage = "Failed to load recent items: ${result.message}"
                        )
                    }
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }

            // Load recently added items by types
            when (val result = repository.getRecentlyAddedByTypes()) {
                is ApiResult.Success -> {
                    _appState.value = _appState.value.copy(recentlyAddedByTypes = result.data)
                }
                is ApiResult.Error -> {
                    // Don't override other errors, just log this
                    if (_appState.value.errorMessage == null) {
                        _appState.value = _appState.value.copy(
                            errorMessage = "Failed to load recent items by type: ${result.message}"
                        )
                    }
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }
            
            // Load initial page of items for library type screens
            loadLibraryItemsPage(reset = true)
            
            _appState.value = _appState.value.copy(isLoading = false)
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
            // This will be handled by the ServerConnectionViewModel when it observes the logout
        }
    }
    
    fun getImageUrl(item: BaseItemDto): String? {
        return repository.getImageUrl(item.id.toString(), "Primary", null)
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
                _appState.value = currentState.copy(
                    allItems = emptyList(),
                    currentPage = 0,
                    hasMoreItems = true,
                    isLoading = true
                )
            } else {
                _appState.value = currentState.copy(isLoadingMore = true)
            }
            
            val pageSize = 50 // Reasonable page size
            val page = if (reset) 0 else currentState.currentPage + 1
            val startIndex = page * pageSize
            
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
}
