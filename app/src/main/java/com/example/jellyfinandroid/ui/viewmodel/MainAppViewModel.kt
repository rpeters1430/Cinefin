package com.example.jellyfinandroid.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.jellyfinandroid.BuildConfig
import com.example.jellyfinandroid.data.SecureCredentialManager
import com.example.jellyfinandroid.data.repository.common.ApiResult
import com.example.jellyfinandroid.data.repository.common.ErrorType
import com.example.jellyfinandroid.data.repository.JellyfinMediaRepository
import com.example.jellyfinandroid.data.repository.JellyfinRepository
import com.example.jellyfinandroid.data.repository.JellyfinStreamRepository
import com.example.jellyfinandroid.data.repository.JellyfinUserRepository
import com.example.jellyfinandroid.ui.screens.LibraryType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
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
    val allMovies: List<BaseItemDto> = emptyList(),
    val isLoadingMovies: Boolean = false,
    val hasMoreMovies: Boolean = true,
    val moviesPage: Int = 0,
    val allTVShows: List<BaseItemDto> = emptyList(),
    val isLoadingTVShows: Boolean = false,
    val hasMoreTVShows: Boolean = true,
    val tvShowsPage: Int = 0,
    val isLoadingMore: Boolean = false,
    val hasMoreItems: Boolean = true,
    val currentPage: Int = 0,
    val errorMessage: String? = null,
)

data class PaginatedItems(
    val items: List<BaseItemDto>,
    val hasMore: Boolean,
    val totalCount: Int? = null,
)

@HiltViewModel
class MainAppViewModel @Inject constructor(
    private val repository: JellyfinRepository,
    private val mediaRepository: JellyfinMediaRepository,
    private val userRepository: JellyfinUserRepository,
    private val streamRepository: JellyfinStreamRepository,
    private val credentialManager: SecureCredentialManager,
    @UnstableApi private val castManager: com.example.jellyfinandroid.ui.player.CastManager,
) : ViewModel() {

    private val _appState = MutableStateFlow(MainAppState())
    val appState: StateFlow<MainAppState> = _appState.asStateFlow()

    val currentServer = repository.currentServer
    val isConnected = repository.isConnected

    // ✅ FIX: Track which library types have been loaded to prevent double loading
    private val loadedLibraryTypes = mutableSetOf<String>()

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        viewModelScope.launch {
            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "loadInitialData: Starting to load all data")
            }

            // ✅ FIX: Clear any previously loaded library type flags for fresh start
            clearLoadedLibraryTypes()

            _appState.value = _appState.value.copy(isLoading = true, errorMessage = null)

            // Load libraries
            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "loadInitialData: Loading libraries")
            }
            when (val result = mediaRepository.getUserLibraries()) {
                is ApiResult.Success -> {
                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "loadInitialData: Loaded ${result.data.size} libraries")
                    }
                    _appState.value = _appState.value.copy(libraries = result.data)
                }
                is ApiResult.Error -> {
                    // ✅ FIX: Don't show error messages for cancelled operations (navigation/lifecycle)
                    if (result.errorType == ErrorType.OPERATION_CANCELLED) {
                        if (BuildConfig.DEBUG) {
                            Log.d("MainAppViewModel", "loadInitialData: Library loading was cancelled (navigation)")
                        }
                    } else {
                        Log.e("MainAppViewModel", "loadInitialData: Failed to load libraries: ${result.message}")
                        _appState.value = _appState.value.copy(
                            errorMessage = "Failed to load libraries: ${result.message}",
                        )
                    }
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }

            // Load recently added items
            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "loadInitialData: Loading recently added items")
            }
            when (val result = mediaRepository.getRecentlyAdded()) {
                is ApiResult.Success -> {
                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "loadInitialData: Loaded ${result.data.size} recently added items")
                    }
                    _appState.value = _appState.value.copy(recentlyAdded = result.data)
                }
                is ApiResult.Error -> {
                    // ✅ FIX: Don't show error messages for cancelled operations
                    if (result.errorType == ErrorType.OPERATION_CANCELLED) {
                        if (BuildConfig.DEBUG) {
                            Log.d("MainAppViewModel", "loadInitialData: Recent items loading was cancelled (navigation)")
                        }
                    } else {
                        Log.e("MainAppViewModel", "loadInitialData: Failed to load recent items: ${result.message}")
                        // Don't override library error, just log this
                        if (_appState.value.errorMessage == null) {
                            _appState.value = _appState.value.copy(
                                errorMessage = "Failed to load recent items: ${result.message}",
                            )
                        }
                    }
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }

            // Load recently added items by types
            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "loadInitialData: Loading recently added items by types")
            }
            when (val result = mediaRepository.getRecentlyAddedByTypes(limit = 20)) {
                is ApiResult.Success -> {
                    val totalItems = result.data.values.sumOf { it.size }
                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "loadInitialData: Loaded $totalItems items across ${result.data.size} types: ${result.data.keys.joinToString(", ")}")
                    }
                    result.data.forEach { (type, items) ->
                        if (BuildConfig.DEBUG) {
                            Log.d("MainAppViewModel", "loadInitialData: $type: ${items.size} items")
                        }
                    }
                    _appState.value = _appState.value.copy(recentlyAddedByTypes = result.data)
                }
                is ApiResult.Error -> {
                    // ✅ FIX: Don't show error messages for cancelled operations
                    if (result.errorType == ErrorType.OPERATION_CANCELLED) {
                        if (BuildConfig.DEBUG) {
                            Log.d("MainAppViewModel", "loadInitialData: Recent items by type loading was cancelled (navigation)")
                        }
                    } else {
                        Log.e("MainAppViewModel", "loadInitialData: Failed to load recent items by type: ${result.message}")
                        // Don't override other errors, just log this
                        if (_appState.value.errorMessage == null) {
                            _appState.value = _appState.value.copy(
                                errorMessage = "Failed to load recent items by type: ${result.message}",
                            )
                        }
                    }
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }

            // ✅ FIX: Only load essential data initially, load library-specific data on-demand
            // This prevents the double loading issue when navigating to library type screens

            _appState.value = _appState.value.copy(isLoading = false)
            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "loadInitialData: Completed loading essential data. Library-specific data will load on-demand.")
            }
        }
    }

    fun loadFavorites() {
        viewModelScope.launch {
            _appState.value = _appState.value.copy(isLoading = true, errorMessage = null)

            when (val result = repository.getFavorites()) {
                is ApiResult.Success -> {
                    _appState.value = _appState.value.copy(
                        favorites = result.data,
                        isLoading = false,
                    )
                }
                is ApiResult.Error -> {
                    _appState.value = _appState.value.copy(
                        errorMessage = "Failed to load favorites: ${result.message}",
                        isLoading = false,
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
        viewModelScope.launch {
            userRepository.logout()
            // Clear saved credentials on logout
            credentialManager.clearCredentials()
        }
    }

    /**
     * Manually refresh authentication token if expired
     */
    fun refreshAuthentication() {
        viewModelScope.launch {
            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "Manual authentication refresh requested")
            }
            try {
                repository.validateAndRefreshTokenManually()
                if (BuildConfig.DEBUG) {
                    Log.d("MainAppViewModel", "Authentication refresh completed")
                }
            } catch (e: Exception) {
                Log.e("MainAppViewModel", "Authentication refresh failed", e)
                _appState.value = _appState.value.copy(
                    errorMessage = "Failed to refresh authentication: ${e.message}",
                )
            }
        }
    }

    fun getImageUrl(item: BaseItemDto): String? {
        return streamRepository.getImageUrl(item.id.toString(), "Primary", null)
    }

    fun getBackdropUrl(item: BaseItemDto): String? {
        return streamRepository.getBackdropUrl(item)
    }

    fun getSeriesImageUrl(item: BaseItemDto): String? {
        return streamRepository.getSeriesImageUrl(item)
    }

    fun search(query: String) {
        viewModelScope.launch {
            _appState.value = _appState.value.copy(
                searchQuery = query,
                isSearching = true,
                errorMessage = null,
            )

            when (val result = repository.searchItems(query)) {
                is ApiResult.Success -> {
                    _appState.value = _appState.value.copy(
                        searchResults = result.data,
                        isSearching = false,
                    )
                }
                is ApiResult.Error -> {
                    _appState.value = _appState.value.copy(
                        searchResults = emptyList(),
                        isSearching = false,
                        errorMessage = "Search failed: ${result.message}",
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
            isSearching = false,
        )
    }

    private fun loadLibraryItemsPage(reset: Boolean = false) {
        viewModelScope.launch {
            val currentState = _appState.value

            if (reset) {
                if (BuildConfig.DEBUG) {
                    Log.d("MainAppViewModel", "loadLibraryItemsPage: Resetting and loading first page")
                }
                _appState.value = currentState.copy(
                    allItems = emptyList(),
                    currentPage = 0,
                    hasMoreItems = true,
                    isLoading = true,
                )
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d("MainAppViewModel", "loadLibraryItemsPage: Loading next page")
                }
                _appState.value = currentState.copy(isLoadingMore = true)
            }

            val pageSize = 50 // Reasonable page size
            val page = if (reset) 0 else currentState.currentPage + 1
            val startIndex = page * pageSize

            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "loadLibraryItemsPage: Requesting page $page (startIndex: $startIndex, limit: $pageSize)")
            }

            when (
                val result = mediaRepository.getLibraryItems(
                    startIndex = startIndex,
                    limit = pageSize,
                )
            ) {
                is ApiResult.Success -> {
                    val newItems = result.data
                    val allItems = if (reset) {
                        newItems
                    } else {
                        currentState.allItems + newItems
                    }

                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "loadLibraryItemsPage: Successfully loaded ${newItems.size} items for page $page")
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "loadLibraryItemsPage: Total items now: ${allItems.size}")
                    }

                    // Log item types breakdown
                    val typeBreakdown = allItems.groupBy { it.type }.mapValues { it.value.size }
                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "loadLibraryItemsPage: Item types breakdown: $typeBreakdown")
                    }

                    _appState.value = _appState.value.copy(
                        allItems = allItems,
                        currentPage = page,
                        hasMoreItems = newItems.size == pageSize, // If we got less than pageSize, no more items
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = null,
                    )
                }
                is ApiResult.Error -> {
                    Log.e("MainAppViewModel", "loadLibraryItemsPage: Failed to load page $page: ${result.message}")
                    _appState.value = _appState.value.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = if (reset) "Failed to load items: ${result.message}" else result.message,
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
            when (val result = userRepository.toggleFavorite(item.id.toString(), !currentFavoriteState)) {
                is ApiResult.Success -> {
                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "Successfully toggled favorite for ${item.name}")
                    }
                    // Refresh data to update UI state
                    loadInitialData()
                }
                is ApiResult.Error -> {
                    Log.e("MainAppViewModel", "Failed to toggle favorite: ${result.message}")
                    _appState.value = _appState.value.copy(
                        errorMessage = "Failed to update favorite: ${result.message}",
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
            when (val result = userRepository.markAsWatched(item.id.toString())) {
                is ApiResult.Success -> {
                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "Successfully marked ${item.name} as watched")
                    }
                    // Refresh data to update UI state
                    loadInitialData()
                }
                is ApiResult.Error -> {
                    Log.e("MainAppViewModel", "Failed to mark as watched: ${result.message}")
                    _appState.value = _appState.value.copy(
                        errorMessage = "Failed to mark as watched: ${result.message}",
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
            when (val result = userRepository.markAsUnwatched(item.id.toString())) {
                is ApiResult.Success -> {
                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "Successfully marked ${item.name} as unwatched")
                    }
                    // Refresh data to update UI state
                    loadInitialData()
                }
                is ApiResult.Error -> {
                    Log.e("MainAppViewModel", "Failed to mark as unwatched: ${result.message}")
                    _appState.value = _appState.value.copy(
                        errorMessage = "Failed to mark as unwatched: ${result.message}",
                    )
                }
                is ApiResult.Loading -> {
                    // Handle loading state if needed
                }
            }
        }
    }

    fun deleteItem(item: BaseItemDto, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            when (val result = userRepository.deleteItemAsAdmin(item.id.toString())) {
                is ApiResult.Success -> {
                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "Successfully deleted ${item.name}")
                    }
                    _appState.value = _appState.value.copy(
                        recentlyAdded = _appState.value.recentlyAdded.filterNot { it.id == item.id },
                        recentlyAddedByTypes = _appState.value.recentlyAddedByTypes.mapValues { entry ->
                            entry.value.filterNot { it.id == item.id }
                        }.filterValues { it.isNotEmpty() },
                        favorites = _appState.value.favorites.filterNot { it.id == item.id },
                        searchResults = _appState.value.searchResults.filterNot { it.id == item.id },
                        allItems = _appState.value.allItems.filterNot { it.id == item.id },
                        allTVShows = _appState.value.allTVShows.filterNot { it.id == item.id },
                        allMovies = _appState.value.allMovies.filterNot { it.id == item.id },
                    )
                    onResult(true, null)
                }
                is ApiResult.Error -> {
                    Log.e("MainAppViewModel", "Failed to delete item: ${result.message}")
                    _appState.value = _appState.value.copy(
                        errorMessage = "Failed to delete item: ${result.message}",
                    )
                    onResult(false, result.message)
                }
                is ApiResult.Loading -> {
                    // no-op
                }
            }
        }
    }

    fun getStreamUrl(item: BaseItemDto): String? {
        return streamRepository.getStreamUrl(item.id.toString())
    }

    /**
     * Gets the download URL for a media item.
     * This URL can be used with DownloadManager for offline storage.
     */
    fun getDownloadUrl(item: BaseItemDto): String? {
        return streamRepository.getDownloadUrl(item.id.toString())
    }

    /**
     * Gets a direct stream URL optimized for downloads.
     */
    fun getDirectStreamUrl(item: BaseItemDto, container: String? = null): String? {
        return streamRepository.getDirectStreamUrl(item.id.toString(), container)
    }

    fun getMovieDetails(movieId: String) {
        viewModelScope.launch {
            _appState.value = _appState.value.copy(isLoading = true, errorMessage = null)
            when (val result = repository.getMovieDetails(movieId)) {
                is ApiResult.Success -> {
                    val currentItems = _appState.value.allItems.toMutableList()
                    val existingIndex = currentItems.indexOfFirst { it.id?.toString() == movieId }

                    if (existingIndex >= 0) {
                        currentItems[existingIndex] = result.data
                    } else {
                        currentItems.add(result.data)
                    }

                    _appState.value = _appState.value.copy(allItems = currentItems, isLoading = false)
                }
                is ApiResult.Error -> {
                    _appState.value = _appState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to load movie details: ${result.message}",
                    )
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }
        }
    }

    // Navigation compatibility methods
    fun loadTVShowDetails(seriesId: String) {
        viewModelScope.launch {
            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "loadTVShowDetails: Loading series details for $seriesId")
            }

            // Load the series details and add to allItems if not already there
            when (val result = repository.getSeriesDetails(seriesId)) {
                is ApiResult.Success -> {
                    val currentItems = _appState.value.allItems.toMutableList()
                    val existingIndex = currentItems.indexOfFirst { it.id?.toString() == seriesId }

                    if (existingIndex >= 0) {
                        currentItems[existingIndex] = result.data
                    } else {
                        currentItems.add(result.data)
                    }

                    _appState.value = _appState.value.copy(allItems = currentItems)
                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "loadTVShowDetails: Successfully loaded series ${result.data.name}")
                    }
                }
                is ApiResult.Error -> {
                    Log.e("MainAppViewModel", "loadTVShowDetails: Failed to load series details: ${result.message}")
                    if (result.errorType != ErrorType.OPERATION_CANCELLED) {
                        _appState.value = _appState.value.copy(
                            errorMessage = "Failed to load series details: ${result.message}",
                        )
                    }
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }
        }
    }

    fun loadMusic() {
        // Navigation compatibility method - music loading handled by existing functionality
    }

    /**
     * Loads episode details for episode detail screen navigation
     */
    fun loadEpisodeDetails(episodeId: String) {
        viewModelScope.launch {
            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "loadEpisodeDetails: Loading episode details for $episodeId")
            }

            // Check if episode already exists in allItems
            val existingEpisode = _appState.value.allItems.find { it.id.toString() == episodeId }
            if (existingEpisode != null) {
                if (BuildConfig.DEBUG) {
                    Log.d("MainAppViewModel", "loadEpisodeDetails: Episode already loaded: ${existingEpisode.name}")
                }
                return@launch
            }

            // Set loading state
            _appState.value = _appState.value.copy(isLoading = true)

            try {
                // Fetch episode details from repository
                when (val result = repository.getEpisodeDetails(episodeId)) {
                    is ApiResult.Success -> {
                        val episode = result.data
                        if (BuildConfig.DEBUG) {
                            Log.d("MainAppViewModel", "loadEpisodeDetails: Successfully loaded episode: ${episode.name}")
                        }

                        // Add episode to app state
                        addOrUpdateItem(episode)

                        // If episode has series info, try to load series details too for better context
                        episode.seriesId?.let { seriesId ->
                            val existingSeries = _appState.value.allItems.find { it.id.toString() == seriesId.toString() }
                            if (existingSeries == null) {
                                if (BuildConfig.DEBUG) {
                                    Log.d("MainAppViewModel", "loadEpisodeDetails: Loading series details for context")
                                }
                                when (val seriesResult = repository.getSeriesDetails(seriesId.toString())) {
                                    is ApiResult.Success -> {
                                        addOrUpdateItem(seriesResult.data)
                                        if (BuildConfig.DEBUG) {
                                            Log.d("MainAppViewModel", "loadEpisodeDetails: Added series context: ${seriesResult.data.name}")
                                        }
                                    }
                                    is ApiResult.Error -> {
                                        Log.w("MainAppViewModel", "loadEpisodeDetails: Failed to load series context: ${seriesResult.message}")
                                    }
                                    else -> { /* Loading state not relevant here */ }
                                }
                            }
                        }

                        _appState.value = _appState.value.copy(isLoading = false)
                    }
                    is ApiResult.Error -> {
                        Log.e("MainAppViewModel", "loadEpisodeDetails: Failed to load episode: ${result.message}")
                        _appState.value = _appState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to load episode: ${result.message}",
                        )
                    }
                    is ApiResult.Loading -> {
                        if (BuildConfig.DEBUG) {
                            Log.d("MainAppViewModel", "loadEpisodeDetails: Episode loading in progress")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainAppViewModel", "loadEpisodeDetails: Exception loading episode", e)
                _appState.value = _appState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load episode: ${e.message}",
                )
            }
        }
    }

    /**
     * Loads all movies with pagination support
     */
    fun loadAllMovies(reset: Boolean = false) {
        viewModelScope.launch {
            val currentState = _appState.value

            if (reset) {
                if (BuildConfig.DEBUG) {
                    Log.d("MainAppViewModel", "loadAllMovies: Resetting and loading first page")
                }
                _appState.value = currentState.copy(
                    allMovies = emptyList(),
                    moviesPage = 0,
                    hasMoreMovies = true,
                    isLoadingMovies = true,
                )
            } else {
                if (currentState.isLoadingMovies || !currentState.hasMoreMovies) {
                    return@launch
                }
                if (BuildConfig.DEBUG) {
                    Log.d("MainAppViewModel", "loadAllMovies: Loading next page")
                }
                _appState.value = currentState.copy(isLoadingMovies = true)
            }

            val pageSize = 50
            val page = if (reset) 0 else currentState.moviesPage + 1
            val startIndex = page * pageSize

            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "loadAllMovies: Requesting page $page (startIndex: $startIndex, limit: $pageSize)")
            }

            when (
                val result = mediaRepository.getLibraryItems(
                    itemTypes = "Movie",
                    startIndex = startIndex,
                    limit = pageSize,
                )
            ) {
                is ApiResult.Success -> {
                    val newMovies = result.data
                    val allMovies = if (reset) {
                        newMovies
                    } else {
                        currentState.allMovies + newMovies
                    }

                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "loadAllMovies: Successfully loaded ${newMovies.size} movies for page $page")
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "loadAllMovies: Total movies now: ${allMovies.size}")
                    }

                    _appState.value = _appState.value.copy(
                        allMovies = allMovies,
                        moviesPage = page,
                        hasMoreMovies = newMovies.size == pageSize,
                        isLoadingMovies = false,
                        errorMessage = null,
                    )
                }
                is ApiResult.Error -> {
                    Log.e("MainAppViewModel", "loadAllMovies: Failed to load page $page: ${result.message}")
                    _appState.value = _appState.value.copy(
                        isLoadingMovies = false,
                        errorMessage = if (reset) "Failed to load movies: ${result.message}" else result.message,
                    )
                }
                is ApiResult.Loading -> {
                    // Already handled above
                }
            }
        }
    }

    /**
     * Loads more movies for pagination
     */
    fun loadMoreMovies() {
        loadAllMovies(reset = false)
    }

    /**
     * Refreshes movies by reloading from the beginning
     */
    fun refreshMovies() {
        loadAllMovies(reset = true)
    }

    /**
     * Loads all TV shows with pagination support
     */
    fun loadAllTVShows(reset: Boolean = false) {
        viewModelScope.launch {
            val currentState = _appState.value

            if (reset) {
                if (BuildConfig.DEBUG) {
                    Log.d("MainAppViewModel", "loadAllTVShows: Resetting and loading first page")
                }
                _appState.value = currentState.copy(
                    allTVShows = emptyList(),
                    tvShowsPage = 0,
                    hasMoreTVShows = true,
                    isLoadingTVShows = true,
                )
            } else {
                if (currentState.isLoadingTVShows || !currentState.hasMoreTVShows) {
                    return@launch
                }
                if (BuildConfig.DEBUG) {
                    Log.d("MainAppViewModel", "loadAllTVShows: Loading next page")
                }
                _appState.value = currentState.copy(isLoadingTVShows = true)
            }

            val pageSize = 50
            val page = if (reset) 0 else currentState.tvShowsPage + 1
            val startIndex = page * pageSize

            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "loadAllTVShows: Requesting page $page (startIndex: $startIndex, limit: $pageSize)")
            }

            when (
                val result = mediaRepository.getLibraryItems(
                    itemTypes = "Series",
                    startIndex = startIndex,
                    limit = pageSize,
                )
            ) {
                is ApiResult.Success -> {
                    val newTVShows = result.data
                    val allTVShows = if (reset) {
                        newTVShows
                    } else {
                        currentState.allTVShows + newTVShows
                    }

                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "loadAllTVShows: Successfully loaded ${newTVShows.size} TV shows for page $page")
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "loadAllTVShows: Total TV shows now: ${allTVShows.size}")
                    }

                    _appState.value = _appState.value.copy(
                        allTVShows = allTVShows,
                        tvShowsPage = page,
                        hasMoreTVShows = newTVShows.size == pageSize,
                        isLoadingTVShows = false,
                        errorMessage = null,
                    )
                }
                is ApiResult.Error -> {
                    Log.e("MainAppViewModel", "loadAllTVShows: Failed to load page $page: ${result.message}")
                    _appState.value = _appState.value.copy(
                        isLoadingTVShows = false,
                        errorMessage = if (reset) "Failed to load TV shows: ${result.message}" else result.message,
                    )
                }
                is ApiResult.Loading -> {
                    // Already handled above
                }
            }
        }
    }

    /**
     * Loads more TV shows for pagination
     */
    fun loadMoreTVShows() {
        loadAllTVShows(reset = false)
    }

    /**
     * Refreshes TV shows by reloading from the beginning
     */
    fun refreshTVShows() {
        loadAllTVShows(reset = true)
    }

    /**
     * ✅ FIX: Clear loaded library types when user state changes
     */
    fun clearLoadedLibraryTypes() {
        loadedLibraryTypes.clear()
        if (BuildConfig.DEBUG) {
            Log.d("MainAppViewModel", "clearLoadedLibraryTypes: Cleared all loaded library type flags")
        }
    }

    /**
     * Adds or updates an item in the main app state
     */
    fun addOrUpdateItem(item: BaseItemDto) {
        val currentItems = _appState.value.allItems.toMutableList()
        val existingIndex = currentItems.indexOfFirst { it.id == item.id }

        if (existingIndex >= 0) {
            currentItems[existingIndex] = item
            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "addOrUpdateItem: Updated existing item ${item.name}")
            }
        } else {
            currentItems.add(item)
            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "addOrUpdateItem: Added new item ${item.name}")
            }
        }

        _appState.value = _appState.value.copy(allItems = currentItems)
    }

    /**
     * ✅ FIX: Load library type data on-demand to prevent double loading
     * This method checks if data for a specific library type is already loaded
     * and only loads it if necessary, preventing the double refresh issue.
     */
    fun loadLibraryTypeData(libraryType: LibraryType, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val typeKey = libraryType.name

            // Skip loading if already loaded and not forcing refresh
            if (!forceRefresh && loadedLibraryTypes.contains(typeKey)) {
                if (BuildConfig.DEBUG) {
                    Log.d("MainAppViewModel", "loadLibraryTypeData: $typeKey already loaded, skipping")
                }
                return@launch
            }

            when (libraryType) {
                LibraryType.MOVIES -> {
                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "loadLibraryTypeData: Loading movies data")
                    }
                    loadAllMovies(reset = true)
                    loadedLibraryTypes.add(typeKey)
                }
                LibraryType.TV_SHOWS -> {
                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "loadLibraryTypeData: Loading TV shows data")
                    }
                    loadAllTVShows(reset = true)
                    loadedLibraryTypes.add(typeKey)
                }
                LibraryType.MUSIC, LibraryType.STUFF -> {
                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "loadLibraryTypeData: Loading general library items for $typeKey")
                    }
                    // For music and other types, use the general library items
                    if (!loadedLibraryTypes.contains("GENERAL_ITEMS") || forceRefresh) {
                        loadLibraryItemsPage(reset = true)
                        loadedLibraryTypes.add("GENERAL_ITEMS")
                    }
                    loadedLibraryTypes.add(typeKey)
                }
            }
        }
    }

    /**
     * ✅ FIX: Get library type specific data to prevent stale data display
     */
    fun getLibraryTypeData(libraryType: LibraryType): List<BaseItemDto> {
        return when (libraryType) {
            LibraryType.MOVIES -> _appState.value.allMovies
            LibraryType.TV_SHOWS -> _appState.value.allTVShows
            LibraryType.MUSIC, LibraryType.STUFF -> {
                // Filter from allItems for music and other types
                _appState.value.allItems.filter { libraryType.itemKinds.contains(it.type) }
            }
        }
    }

    /**
     * Sends a preview (artwork + metadata) to the Cast device if connected.
     */
    @UnstableApi
    fun sendCastPreview(item: BaseItemDto) {
        // Initialize cast if not yet initialized (safe to call multiple times)
        castManager.initialize()
        val image = getImageUrl(item)
        val backdrop = getBackdropUrl(item)
        castManager.loadPreview(item, imageUrl = image, backdropUrl = backdrop)
    }

    /**
     * Clear accumulated state to prevent memory leaks.
     * Should be called when navigating away from screens that load large datasets.
     */
    fun clearState() {
        _appState.value = MainAppState()
        loadedLibraryTypes.clear()
    }

    /**
     * Clear specific library type data to manage memory usage.
     */
    fun clearLibraryTypeData(libraryType: LibraryType) {
        when (libraryType) {
            LibraryType.MOVIES -> {
                _appState.value = _appState.value.copy(allMovies = emptyList())
            }
            LibraryType.TV_SHOWS -> {
                _appState.value = _appState.value.copy(allTVShows = emptyList())
            }
            LibraryType.MUSIC, LibraryType.STUFF -> {
                // For music and other types, we need to filter out from allItems
                val typesToRemove = libraryType.itemKinds
                _appState.value = _appState.value.copy(
                    allItems = _appState.value.allItems.filterNot { item ->
                        typesToRemove.contains(item.type)
                    },
                )
            }
        }
        loadedLibraryTypes.remove(libraryType.toString())
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel is being destroyed, clean up resources
        clearState()
    }
}
