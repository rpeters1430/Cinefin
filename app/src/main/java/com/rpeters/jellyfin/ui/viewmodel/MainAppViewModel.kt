package com.rpeters.jellyfin.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.repository.JellyfinAuthRepository
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.JellyfinStreamRepository
import com.rpeters.jellyfin.data.repository.JellyfinUserRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import com.rpeters.jellyfin.ui.screens.LibraryType
import com.rpeters.jellyfin.utils.ConcurrencyThrottler
import com.rpeters.jellyfin.utils.PerformanceMonitor
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.Locale
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
    val homeVideosByLibrary: Map<String, List<BaseItemDto>> = emptyMap(),
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
    val loadedLibraryTypes: Set<String> = emptySet(),
    val errorMessage: String? = null,
)

data class PaginatedItems(
    val items: List<BaseItemDto>,
    val hasMore: Boolean,
    val totalCount: Int? = null,
)

@HiltViewModel
class MainAppViewModel @Inject constructor(
    private val authRepository: JellyfinAuthRepository,
    private val repository: JellyfinRepository,
    private val mediaRepository: JellyfinMediaRepository,
    private val userRepository: JellyfinUserRepository,
    private val streamRepository: JellyfinStreamRepository,
    private val credentialManager: SecureCredentialManager,
    @UnstableApi private val castManager: com.rpeters.jellyfin.ui.player.CastManager,
) : ViewModel() {

    private val _appState = MutableStateFlow(MainAppState())
    val appState: StateFlow<MainAppState> = _appState.asStateFlow()

    val currentServer = repository.currentServer
    val isConnected = repository.isConnected

    // ✅ FIX: Track which library types have been loaded to prevent double loading
    private val loadedLibraryTypes = mutableSetOf<String>()

    // Prevent multiple concurrent data loading operations
    @Volatile
    private var isLoadingData = false

    init {
        // ✅ AUTHENTICATION FIX: Don't load data in init block
        // Data loading should only happen after authentication is established
        // This prevents premature API calls and "User not authenticated" logs
        if (BuildConfig.DEBUG) {
            Log.d("MainAppViewModel", "ViewModel initialized - data loading will be triggered by UI after auth")
        }
    }

    private suspend fun ensureValidToken(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Use the improved token validation that waits for concurrent authentication
                return@withContext ensureValidTokenWithWait()
            } catch (e: Exception) {
                SecureLogger.e("MainAppViewModel", "Error ensuring valid token", e)
                return@withContext false
            }
        }
    }

    /**
     * ✅ FIX: Enhanced token validation that waits for authentication to complete
     * This prevents race conditions during app startup with remembered credentials
     */
    private suspend fun ensureValidTokenWithWait(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // First check if we have a valid token
                if (!authRepository.isTokenExpired()) {
                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "ensureValidTokenWithWait: Token is valid, proceeding")
                    }
                    return@withContext true
                }

                if (BuildConfig.DEBUG) {
                    Log.d("MainAppViewModel", "ensureValidTokenWithWait: Token expired, attempting re-authentication")
                }

                // Wait for authentication to complete
                val authSuccess = authRepository.reAuthenticate()

                if (authSuccess) {
                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "ensureValidTokenWithWait: Re-authentication successful")
                    }
                    // Additional verification that the token is now valid
                    return@withContext !authRepository.isTokenExpired()
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.w("MainAppViewModel", "ensureValidTokenWithWait: Re-authentication failed")
                    }
                    return@withContext false
                }
            } catch (e: Exception) {
                Log.e("MainAppViewModel", "ensureValidTokenWithWait: Exception during authentication", e)
                return@withContext false
            }
        }
    }

    /**
     * ✅ ENHANCED: Load initial data with improved authentication handling
     * Enhanced to prevent concurrent authentication issues and better handle 401 errors
     */
    fun loadInitialData(forceRefresh: Boolean = false) {
        if (isLoadingData) {
            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "loadInitialData: Already loading data, skipping")
            }
            return
        }

        viewModelScope.launch {
            try {
                isLoadingData = true
                if (BuildConfig.DEBUG) {
                    Log.d("MainAppViewModel", "loadInitialData: Starting to load all data (forceRefresh=$forceRefresh)")
                    PerformanceMonitor.logMemoryUsage("Before loading data")
                }

                // ✅ FIX: Check authentication status before loading data
                if (!authRepository.isUserAuthenticated()) {
                    if (BuildConfig.DEBUG) {
                        Log.w("MainAppViewModel", "loadInitialData: User not authenticated, skipping data load")
                    }
                    PerformanceMonitor.measureExecutionTime("loadInitialData") { }
                    return@launch
                }

                // ✅ FIX: Ensure we have a valid token before starting any data loading
                // This prevents multiple concurrent authentication attempts
                if (!ensureValidTokenWithWait()) {
                    if (BuildConfig.DEBUG) {
                        Log.e("MainAppViewModel", "loadInitialData: Token validation failed, cannot load data")
                    }
                    return@launch
                }

                // Clear any existing library health issues for known problematic libraries
                mediaRepository.clearKnownLibraryHealthIssues()

                _appState.value = _appState.value.copy(isLoading = true, errorMessage = null)

                // Load libraries first, then recently added sequentially to avoid auth conflicts
                if (BuildConfig.DEBUG) {
                    Log.d("MainAppViewModel", "loadInitialData: Loading libraries first, then recently added sequentially")
                }

                val librariesDeferred = async {
                    ConcurrencyThrottler.throttle {
                        mediaRepository.getUserLibraries()
                    }
                }

                // Wait for libraries first
                val librariesResult = librariesDeferred.await()

                val recentlyAddedDeferred = async {
                    // Add delay after libraries complete to prevent auth conflicts
                    delay(500)
                    ConcurrencyThrottler.throttle {
                        mediaRepository.getRecentlyAdded()
                    }
                }

                // Process libraries result
                var newLibraries: List<BaseItemDto> = _appState.value.libraries // Preserve existing libraries on failure
                when (librariesResult) {
                    is ApiResult.Success -> {
                        newLibraries = librariesResult.data
                        if (BuildConfig.DEBUG) {
                            Log.d("MainAppViewModel", "loadInitialData: Loaded ${newLibraries.size} libraries")
                        }

                        // ✅ PERFORMANCE FIX: Load library type data asynchronously with staggered timing to prevent auth conflicts
                        launch {
                            delay(100) // Small delay to prevent concurrent 401 errors
                            loadLibraryTypeData(LibraryType.MOVIES, forceRefresh)
                        }
                        launch {
                            delay(200) // Staggered delay
                            loadLibraryTypeData(LibraryType.TV_SHOWS, forceRefresh)
                        }
                        launch {
                            delay(300) // Staggered delay
                            loadLibraryTypeData(LibraryType.MUSIC, forceRefresh)
                        }

                        // Load home videos for custom libraries
                        val previousIds = _appState.value.homeVideosByLibrary.keys.toSet()
                        val updatedHomeVideos = _appState.value.homeVideosByLibrary.toMutableMap()
                        var updatedAllMovies = _appState.value.allMovies.toMutableList()
                        var updatedAllTVShows = _appState.value.allTVShows.toMutableList()
                        var updatedAllItems = _appState.value.allItems.toMutableList()
                        val loadedLibraryTypes = _appState.value.loadedLibraryTypes.toMutableSet()
                        var customRemoved = false

                        newLibraries.forEach { lib ->
                            val type = (lib.collectionType?.toString() ?: lib.type?.name)?.lowercase(Locale.getDefault())

                            when (type) {
                                "movies" -> {
                                    if (LibraryType.MOVIES.name !in loadedLibraryTypes) {
                                        // ✅ PERFORMANCE FIX: Launch asynchronously with delay to prevent auth conflicts
                                        launch {
                                            delay(700) // Further staggered to avoid auth conflicts
                                            loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = true)
                                        }
                                    } else {
                                        updatedAllMovies = emptyList<BaseItemDto>().toMutableList()
                                        loadedLibraryTypes.remove(LibraryType.MOVIES.name)
                                    }
                                }
                                "tvshows" -> {
                                    if (LibraryType.TV_SHOWS.name !in loadedLibraryTypes) {
                                        // ✅ PERFORMANCE FIX: Launch asynchronously with delay to prevent auth conflicts
                                        launch {
                                            delay(800) // Further staggered to avoid auth conflicts
                                            loadLibraryTypeData(LibraryType.TV_SHOWS, forceRefresh = true)
                                        }
                                    } else {
                                        updatedAllTVShows = emptyList<BaseItemDto>().toMutableList()
                                        loadedLibraryTypes.remove(LibraryType.TV_SHOWS.name)
                                    }
                                }
                                "music" -> {
                                    if (LibraryType.MUSIC.name !in loadedLibraryTypes) {
                                        // ✅ PERFORMANCE FIX: Launch asynchronously with delay to prevent auth conflicts
                                        launch {
                                            delay(900) // Further staggered to avoid auth conflicts
                                            loadLibraryTypeData(LibraryType.MUSIC, forceRefresh = true)
                                        }
                                    } else {
                                        updatedAllItems = updatedAllItems.filterNot {
                                            LibraryType.MUSIC.itemKinds.contains(it.type)
                                        }.toMutableList()
                                        loadedLibraryTypes.remove(LibraryType.MUSIC.name)
                                    }
                                }
                                else -> {
                                    customRemoved = true
                                    lib.id?.toString()?.let { updatedHomeVideos.remove(it) }
                                }
                            }
                        }

                        if (customRemoved && newLibraries.none { (it.collectionType?.toString() ?: it.type?.name)?.lowercase(Locale.getDefault()) !in setOf("movies", "tvshows", "music") }) {
                            loadedLibraryTypes.remove(LibraryType.STUFF.name)
                        }

                        _appState.value = _appState.value.copy(
                            allMovies = updatedAllMovies,
                            allTVShows = updatedAllTVShows,
                            allItems = updatedAllItems,
                            homeVideosByLibrary = updatedHomeVideos,
                            libraries = newLibraries,
                        )

                        val addedLibraries = newLibraries.filter { it.id?.toString() !in previousIds }
                        val addedTypes: Set<String> = addedLibraries.mapNotNull {
                            (it.collectionType?.toString() ?: it.type?.name)?.lowercase(Locale.getDefault())
                        }.toSet()
                        // ✅ PERFORMANCE FIX: Launch library type data loading asynchronously with delays
                        if ("movies" in addedTypes) {
                            launch {
                                delay(400) // Staggered to avoid auth conflicts
                                loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = true)
                            }
                        }
                        if ("tvshows" in addedTypes) {
                            launch {
                                delay(500) // Staggered to avoid auth conflicts
                                loadLibraryTypeData(LibraryType.TV_SHOWS, forceRefresh = true)
                            }
                        }
                        if ("music" in addedTypes) {
                            launch {
                                delay(600) // Staggered to avoid auth conflicts
                                loadLibraryTypeData(LibraryType.MUSIC, forceRefresh = true)
                            }
                        }
                        // ✅ PERFORMANCE FIX: Launch home videos loading asynchronously
                        addedLibraries.filter {
                            val type = (it.collectionType?.toString() ?: it.type?.name)?.lowercase(Locale.getDefault())
                            type !in setOf("movies", "tvshows", "music")
                        }.forEach { library ->
                            library.id?.let { libraryId ->
                                launch {
                                    delay(1000) // Delay to avoid auth conflicts with other async calls
                                    loadHomeVideos(libraryId.toString())
                                }
                            }
                        }
                    }
                    is ApiResult.Error -> {
                        // ✅ FIX: Don't show error messages for cancelled operations (navigation/lifecycle)
                        if (librariesResult.errorType == ErrorType.OPERATION_CANCELLED) {
                            if (BuildConfig.DEBUG) {
                                Log.d("MainAppViewModel", "loadInitialData: Library loading was cancelled (navigation)")
                            }
                        } else {
                            Log.e("MainAppViewModel", "loadInitialData: Failed to load libraries: ${librariesResult.message}")
                            _appState.value = _appState.value.copy(
                                errorMessage = "Failed to load libraries: ${librariesResult.message}",
                            )
                        }
                    }
                    is ApiResult.Loading -> {
                        // Already handled
                    }
                }

                // Process recently added result (collect data for batch update)
                var recentlyAddedItems: List<BaseItemDto> = _appState.value.recentlyAdded // Preserve existing recently added on failure
                var errorMessage: String? = null

                when (val recentlyAddedResult = recentlyAddedDeferred.await()) {
                    is ApiResult.Success -> {
                        if (BuildConfig.DEBUG) {
                            Log.d("MainAppViewModel", "loadInitialData: Loaded ${recentlyAddedResult.data.size} recently added items")
                        }
                        recentlyAddedItems = recentlyAddedResult.data
                    }
                    is ApiResult.Error -> {
                        // ✅ FIX: Don't show error messages for cancelled operations
                        if (recentlyAddedResult.errorType == ErrorType.OPERATION_CANCELLED) {
                            if (BuildConfig.DEBUG) {
                                Log.d("MainAppViewModel", "loadInitialData: Recent items loading was cancelled (navigation)")
                            }
                        } else {
                            Log.e("MainAppViewModel", "loadInitialData: Failed to load recent items: ${recentlyAddedResult.message}")
                            errorMessage = "Failed to load recent items: ${recentlyAddedResult.message}"
                        }
                    }
                    is ApiResult.Loading -> {
                        // Already handled
                    }
                }

                // Load recently added items by types - PARALLEL LOADING
                if (BuildConfig.DEBUG) {
                    Log.d("MainAppViewModel", "loadInitialData: Loading recently added items by types (parallel)")
                }

                // ✅ FIX: Check authentication status before making API calls to prevent race conditions
                if (!authRepository.isUserAuthenticated()) {
                    Log.w("MainAppViewModel", "loadLibraryTypeData: User not authenticated")
                    return@launch
                }

                // ✅ FIX: Add delay to prevent concurrent authentication attempts
                kotlinx.coroutines.delay(100) // Small delay to prevent race conditions

                // Launch all API calls concurrently
                val types = listOf(
                    BaseItemKind.MOVIE to "MOVIE",
                    BaseItemKind.SERIES to "SERIES",
                    BaseItemKind.EPISODE to "EPISODE",
                    BaseItemKind.AUDIO to "AUDIO",
                    BaseItemKind.VIDEO to "VIDEO",
                )

                val contentTypeDeferreds = types.map { (itemType, typeKey) ->
                    async {
                        typeKey to ConcurrencyThrottler.throttle {
                            mediaRepository.getRecentlyAddedByType(itemType, limit = 20)
                        }
                    }
                }

                // Await all results concurrently
                val contentTypeResults = contentTypeDeferreds.awaitAll()

                // Process results - start with existing data to preserve on failure
                val recentlyAddedByTypes = _appState.value.recentlyAddedByTypes.toMutableMap()
                contentTypeResults.forEach { (typeKey, result) ->
                    when (result) {
                        is ApiResult.Success -> {
                            recentlyAddedByTypes[typeKey] = result.data
                            val displayName = when (typeKey) {
                                "MOVIE" -> "Movies"
                                "SERIES" -> "TV Shows"
                                "EPISODE" -> "TV Episodes"
                                "AUDIO" -> "Music"
                                "VIDEO" -> "Home Videos"
                                else -> typeKey
                            }
                            if (BuildConfig.DEBUG) {
                                Log.d("MainAppViewModel", "loadInitialData: $displayName: ${result.data.size} items")
                            }
                        }
                        is ApiResult.Error -> {
                            val displayName = when (typeKey) {
                                "MOVIE" -> "Movies"
                                "SERIES" -> "TV Shows"
                                "EPISODE" -> "TV Episodes"
                                "AUDIO" -> "Music"
                                "VIDEO" -> "Home Videos"
                                else -> typeKey
                            }
                            if (result.errorType != ErrorType.OPERATION_CANCELLED) {
                                if (BuildConfig.DEBUG) {
                                    Log.e("MainAppViewModel", "loadInitialData: Failed to load recent $displayName: ${result.message}")
                                }
                            }
                        }
                        is ApiResult.Loading -> {
                            // Ignore loading state for this
                        }
                    }
                }

                // ✅ PERFORMANCE FIX: Batch the final state updates to minimize expensive copy operations
                // This significantly reduces frame skipping during app startup by doing only one expensive copy operation
                _appState.value = _appState.value.copy(
                    libraries = newLibraries,
                    recentlyAdded = recentlyAddedItems,
                    recentlyAddedByTypes = recentlyAddedByTypes,
                    isLoading = false,
                    errorMessage = errorMessage,
                )

                // ✅ DEBUG: Log final state for UI troubleshooting
                if (BuildConfig.DEBUG) {
                    Log.d("MainAppViewModel", "Final state update - Libraries: ${newLibraries.size}, RecentlyAdded: ${recentlyAddedItems.size}")
                    Log.d("MainAppViewModel", "RecentlyAddedByTypes: ${recentlyAddedByTypes.mapValues { it.value.size }}")
                    newLibraries.forEachIndexed { index, library ->
                        Log.d("MainAppViewModel", "Library $index: ${library.name} (${library.collectionType})")
                    }
                }

                // ✅ FIX: Only load essential data initially, load library-specific data on-demand
                // This prevents the double loading issue when navigating to library type screens
                if (BuildConfig.DEBUG) {
                    Log.d("MainAppViewModel", "loadInitialData: Completed loading essential data. Library-specific data will load on-demand.")
                    PerformanceMonitor.logMemoryUsage("After loading data")

                    // Check for memory pressure and suggest GC if needed
                    if (PerformanceMonitor.checkMemoryPressure()) {
                        PerformanceMonitor.forceGarbageCollection("High memory usage after data loading")
                    }
                }
            } finally {
                isLoadingData = false
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

    fun loadLibraryItemsPage(reset: Boolean = false) {
        viewModelScope.launch {
            // ✅ FIX: Use same authentication validation as loadInitialData
            if (!ensureValidTokenWithWait()) {
                if (BuildConfig.DEBUG) {
                    Log.w("MainAppViewModel", "loadLibraryItemsPage: Authentication failed, skipping API call")
                }
                _appState.value = _appState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = "Authentication required. Please log in again.",
                )
                return@launch
            }

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
                    collectionType = null, // General loading, no specific type
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

    fun loadStuff() {
        // Load mixed content including home videos, photos, books, etc.
        loadLibraryTypeData(LibraryType.STUFF)
    }

    fun loadHomeVideos(libraryId: String) {
        viewModelScope.launch {
            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "loadHomeVideos: Starting to load home videos for libraryId=$libraryId")
            }

            // ✅ FIX: Use same authentication validation as loadInitialData
            if (!ensureValidTokenWithWait()) {
                if (BuildConfig.DEBUG) {
                    Log.w("MainAppViewModel", "loadHomeVideos: Authentication failed, skipping API call")
                }
                return@launch
            }

            // Try to locate the library (may not be loaded yet during navigation)
            val currentLibraries = _appState.value.libraries
            val library = currentLibraries.find { it.id?.toString() == libraryId }
            val collectionType = library?.collectionType?.toString()?.lowercase()

            // Determine appropriate item types based on collection type
            // ✅ FIX: Let ApiParameterValidator handle homevideos validation
            val itemTypes = when (collectionType) {
                "homevideos" -> null // Let ApiParameterValidator handle this to avoid HTTP 400 errors
                "photos" -> "Photo"
                "books" -> "Book,AudioBook"
                else -> null // Let server decide for unknown types
            }

            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "loadHomeVideos: Loading library $libraryId with collectionType=$collectionType and itemTypes=$itemTypes")
                Log.d("MainAppViewModel", "loadHomeVideos: Library name=${library?.name}")
            }

            when (
                val result = mediaRepository.getLibraryItems(
                    parentId = libraryId,
                    itemTypes = itemTypes,
                    startIndex = 0,
                    limit = 100,
                    collectionType = collectionType,
                )
            ) {
                is ApiResult.Success -> {
                    val updated = _appState.value.homeVideosByLibrary.toMutableMap()
                    updated[libraryId] = result.data
                    _appState.value = _appState.value.copy(homeVideosByLibrary = updated)

                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "loadHomeVideos: Successfully loaded ${result.data.size} items for library $libraryId")
                        // Log item types for debugging
                        val typeBreakdown = result.data.groupBy { it.type }.mapValues { it.value.size }
                        Log.d("MainAppViewModel", "loadHomeVideos: Item types breakdown: $typeBreakdown")
                    }
                }
                is ApiResult.Error -> {
                    // Don't show error for homevideos libraries as they're handled gracefully
                    if (collectionType != "homevideos") {
                        _appState.value = _appState.value.copy(
                            errorMessage = "Failed to load library items: ${result.message}",
                        )
                    } else {
                        if (BuildConfig.DEBUG) {
                            Log.w("MainAppViewModel", "loadHomeVideos: Home videos library failed gracefully: ${result.message}")
                        }
                        // Still update the state with empty list for home videos
                        val updated = _appState.value.homeVideosByLibrary.toMutableMap()
                        updated[libraryId] = emptyList()
                        _appState.value = _appState.value.copy(homeVideosByLibrary = updated)
                    }
                }
                else -> {}
            }
        }
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
            // Check authentication before attempting to load movies
            if (!repository.isUserAuthenticated()) {
                if (BuildConfig.DEBUG) {
                    Log.w("MainAppViewModel", "loadAllMovies: User not authenticated, skipping data load")
                }
                _appState.value = _appState.value.copy(
                    isLoadingMovies = false,
                    errorMessage = "Authentication required. Please log in again.",
                )
                return@launch
            }

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

            // ✅ FIX: Ensure libraries are loaded before trying to filter them
            if (_appState.value.libraries.isEmpty()) {
                if (BuildConfig.DEBUG) {
                    Log.d("MainAppViewModel", "loadAllMovies: Libraries not loaded yet, loading libraries first")
                }

                // Wait for libraries to load
                val librariesResult = mediaRepository.getUserLibraries()
                when (librariesResult) {
                    is ApiResult.Success -> {
                        _appState.value = _appState.value.copy(libraries = librariesResult.data)
                        if (BuildConfig.DEBUG) {
                            Log.d("MainAppViewModel", "loadAllMovies: Loaded ${librariesResult.data.size} libraries")
                        }
                    }
                    is ApiResult.Error -> {
                        if (BuildConfig.DEBUG) {
                            Log.e("MainAppViewModel", "loadAllMovies: Failed to load libraries: ${librariesResult.message}")
                        }
                        _appState.value = _appState.value.copy(
                            isLoadingMovies = false,
                            hasMoreMovies = false,
                            errorMessage = "Failed to load libraries: ${librariesResult.message}",
                        )
                        return@launch
                    }
                    is ApiResult.Loading -> {
                        // Continue with empty state, should not happen
                    }
                }
            }

            val pageSize = 50
            val page = if (reset) 0 else currentState.moviesPage + 1
            val startIndex = page * pageSize

            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "loadAllMovies: Requesting page $page (startIndex: $startIndex, limit: $pageSize)")
            }

            // Fix HTTP 400: Get the first available movie library for parentId
            val movieLibraries = _appState.value.libraries.filter {
                it.collectionType == org.jellyfin.sdk.model.api.CollectionType.MOVIES
            }

            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "loadAllMovies: Found ${movieLibraries.size} movie libraries")
                movieLibraries.forEachIndexed { index, library ->
                    Log.d("MainAppViewModel", "loadAllMovies: Movie library $index: ${library.name} (ID: ${library.id})")
                }
            }

            if (movieLibraries.isEmpty()) {
                if (BuildConfig.DEBUG) {
                    Log.w("MainAppViewModel", "loadAllMovies: No movie libraries found in ${_appState.value.libraries.size} total libraries")
                    _appState.value.libraries.forEach { lib ->
                        Log.d("MainAppViewModel", "Available library: ${lib.name} (Type: ${lib.collectionType}, ID: ${lib.id})")
                    }
                }
                _appState.value = _appState.value.copy(
                    isLoadingMovies = false,
                    hasMoreMovies = false,
                    errorMessage = "No movie libraries available",
                )
                return@launch
            }

            // Use the first movie library as parentId to avoid HTTP 400
            val movieLibraryId = movieLibraries.first().id.toString()

            when (
                val result = mediaRepository.getLibraryItems(
                    parentId = movieLibraryId, // Add parentId to prevent HTTP 400
                    itemTypes = "Movie",
                    startIndex = startIndex,
                    limit = pageSize,
                    collectionType = "movies",
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
                    // ✅ FIX: Handle job cancellation gracefully - don't show error for navigation cancellations
                    if (result.message.contains("Job was cancelled", ignoreCase = true)) {
                        if (BuildConfig.DEBUG) {
                            Log.d("MainAppViewModel", "loadAllMovies: Request cancelled due to navigation, keeping current state")
                        }
                        // Don't update error state for navigation cancellations
                        _appState.value = _appState.value.copy(isLoadingMovies = false)
                    } else {
                        Log.e("MainAppViewModel", "loadAllMovies: Failed to load page $page: ${result.message}")
                        _appState.value = _appState.value.copy(
                            isLoadingMovies = false,
                            errorMessage = if (reset) "Failed to load movies: ${result.message}" else result.message,
                        )
                    }
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
            // Check authentication before attempting to load TV shows
            if (!repository.isUserAuthenticated()) {
                if (BuildConfig.DEBUG) {
                    Log.w("MainAppViewModel", "loadAllTVShows: User not authenticated, skipping data load")
                }
                _appState.value = _appState.value.copy(
                    isLoadingTVShows = false,
                    errorMessage = "Authentication required. Please log in again.",
                )
                return@launch
            }

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

            // ✅ FIX: Ensure libraries are loaded before trying to filter them
            if (_appState.value.libraries.isEmpty()) {
                if (BuildConfig.DEBUG) {
                    Log.d("MainAppViewModel", "loadAllTVShows: Libraries not loaded yet, loading libraries first")
                }

                // Wait for libraries to load
                val librariesResult = mediaRepository.getUserLibraries()
                when (librariesResult) {
                    is ApiResult.Success -> {
                        _appState.value = _appState.value.copy(libraries = librariesResult.data)
                        if (BuildConfig.DEBUG) {
                            Log.d("MainAppViewModel", "loadAllTVShows: Loaded ${librariesResult.data.size} libraries")
                        }
                    }
                    is ApiResult.Error -> {
                        if (BuildConfig.DEBUG) {
                            Log.e("MainAppViewModel", "loadAllTVShows: Failed to load libraries: ${librariesResult.message}")
                        }
                        _appState.value = _appState.value.copy(
                            isLoadingTVShows = false,
                            hasMoreTVShows = false,
                            errorMessage = "Failed to load libraries: ${librariesResult.message}",
                        )
                        return@launch
                    }
                    is ApiResult.Loading -> {
                        // Continue with empty state, should not happen
                    }
                }
            }

            val pageSize = 50
            val page = if (reset) 0 else currentState.tvShowsPage + 1
            val startIndex = page * pageSize

            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "loadAllTVShows: Requesting page $page (startIndex: $startIndex, limit: $pageSize)")
            }

            // Fix HTTP 400: Get the first available TV show library for parentId
            val tvLibraries = _appState.value.libraries.filter {
                it.collectionType == org.jellyfin.sdk.model.api.CollectionType.TVSHOWS
            }

            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "loadAllTVShows: Found ${tvLibraries.size} TV libraries")
                tvLibraries.forEachIndexed { index, library ->
                    Log.d("MainAppViewModel", "loadAllTVShows: TV library $index: ${library.name} (ID: ${library.id})")
                }
            }

            if (tvLibraries.isEmpty()) {
                if (BuildConfig.DEBUG) {
                    Log.w("MainAppViewModel", "loadAllTVShows: No TV show libraries found in ${_appState.value.libraries.size} total libraries")
                    _appState.value.libraries.forEach { lib ->
                        Log.d("MainAppViewModel", "Available library: ${lib.name} (Type: ${lib.collectionType}, ID: ${lib.id})")
                    }
                }
                _appState.value = _appState.value.copy(
                    isLoadingTVShows = false,
                    hasMoreTVShows = false,
                    errorMessage = "No TV show libraries available",
                )
                return@launch
            }

            // Use the first TV show library as parentId to avoid HTTP 400
            val tvLibraryId = tvLibraries.first().id.toString()

            when (
                val result = mediaRepository.getLibraryItems(
                    parentId = tvLibraryId, // Add parentId to prevent HTTP 400
                    itemTypes = "Series",
                    startIndex = startIndex,
                    limit = pageSize,
                    collectionType = "tvshows",
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
                    // ✅ FIX: Handle job cancellation gracefully - don't show error for navigation cancellations
                    if (result.message.contains("Job was cancelled", ignoreCase = true)) {
                        if (BuildConfig.DEBUG) {
                            Log.d("MainAppViewModel", "loadAllTVShows: Request cancelled due to navigation, keeping current state")
                        }
                        // Don't update error state for navigation cancellations
                        _appState.value = _appState.value.copy(isLoadingTVShows = false)
                    } else {
                        Log.e("MainAppViewModel", "loadAllTVShows: Failed to load page $page: ${result.message}")
                        _appState.value = _appState.value.copy(
                            isLoadingTVShows = false,
                            errorMessage = if (reset) "Failed to load TV shows: ${result.message}" else result.message,
                        )
                    }
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
     */
    fun loadLibraryTypeData(libraryType: LibraryType, forceRefresh: Boolean = false) {
        val typeKey = libraryType.name

        // Skip if already loaded (prevents double loading)
        if (!forceRefresh && loadedLibraryTypes.contains(typeKey)) {
            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "loadLibraryTypeData: Skipping $typeKey - already loaded")
            }
            return // No unnecessary API calls!
        }

        viewModelScope.launch {
            try {
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
                    LibraryType.MUSIC -> {
                        if (BuildConfig.DEBUG) {
                            Log.d("MainAppViewModel", "loadLibraryTypeData: Loading music library items")
                        }

                        // ✅ FIX: Ensure libraries are loaded before trying to filter them
                        if (_appState.value.libraries.isEmpty()) {
                            if (BuildConfig.DEBUG) {
                                Log.d("MainAppViewModel", "loadLibraryTypeData: Libraries not loaded yet, loading libraries first for music")
                            }

                            // Wait for libraries to load
                            val librariesResult = mediaRepository.getUserLibraries()
                            when (librariesResult) {
                                is ApiResult.Success -> {
                                    _appState.value = _appState.value.copy(libraries = librariesResult.data)
                                    if (BuildConfig.DEBUG) {
                                        Log.d("MainAppViewModel", "loadLibraryTypeData: Loaded ${librariesResult.data.size} libraries for music")
                                    }
                                }
                                is ApiResult.Error -> {
                                    if (BuildConfig.DEBUG) {
                                        Log.e("MainAppViewModel", "loadLibraryTypeData: Failed to load libraries for music: ${librariesResult.message}")
                                    }
                                    _appState.value = _appState.value.copy(
                                        errorMessage = "Failed to load libraries: ${librariesResult.message}",
                                    )
                                    return@launch
                                }
                                is ApiResult.Loading -> {
                                    // Continue with empty state, should not happen
                                }
                            }
                        }

                        // Get the first available music library for parentId
                        val musicLibraries = _appState.value.libraries.filter {
                            it.collectionType == org.jellyfin.sdk.model.api.CollectionType.MUSIC
                        }

                        if (musicLibraries.isEmpty()) {
                            if (BuildConfig.DEBUG) {
                                Log.w("MainAppViewModel", "loadLibraryTypeData: No music libraries found")
                            }
                            _appState.value = _appState.value.copy(
                                errorMessage = "No music libraries available",
                            )
                        } else {
                            // Use the first music library as parentId to avoid HTTP 400
                            val musicLibraryId = musicLibraries.first().id.toString()
                            loadMusicLibraryItems(musicLibraryId)
                        }
                        loadedLibraryTypes.add(typeKey)
                    }
                    LibraryType.STUFF -> {
                        if (BuildConfig.DEBUG) {
                            Log.d("MainAppViewModel", "loadLibraryTypeData: Loading general library items for $typeKey")
                        }
                        // For other types, use the general library items with first available non-standard library
                        val otherLibraries = _appState.value.libraries.filter {
                            it.collectionType !in setOf(
                                org.jellyfin.sdk.model.api.CollectionType.MOVIES,
                                org.jellyfin.sdk.model.api.CollectionType.TVSHOWS,
                                org.jellyfin.sdk.model.api.CollectionType.MUSIC,
                            )
                        }

                        if (otherLibraries.isEmpty()) {
                            if (BuildConfig.DEBUG) {
                                Log.w("MainAppViewModel", "loadLibraryTypeData: No other content libraries found")
                            }
                        } else {
                            // Use the first other library as parentId
                            val otherLibraryId = otherLibraries.first().id.toString()
                            loadOtherLibraryItems(otherLibraryId)
                        }
                        loadedLibraryTypes.add(typeKey)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainAppViewModel", "loadLibraryTypeData: Error loading $typeKey", e)
                _appState.value = _appState.value.copy(
                    errorMessage = "Failed to load ${libraryType.displayName}: ${e.message}",
                )
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
        viewModelScope.launch {
            // Initialize cast on background thread if not yet initialized (safe to call multiple times)
            withContext(Dispatchers.IO) {
                castManager.initialize()
            }
            val image = getImageUrl(item)
            val backdrop = getBackdropUrl(item)
            castManager.loadPreview(item, imageUrl = image, backdropUrl = backdrop)
        }
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

    /**
     * Loads music library items with proper parentId to prevent HTTP 400 errors
     */
    private fun loadMusicLibraryItems(musicLibraryId: String) {
        viewModelScope.launch {
            // ✅ FIX: Use same authentication validation as loadInitialData
            if (!ensureValidTokenWithWait()) {
                if (BuildConfig.DEBUG) {
                    Log.w("MainAppViewModel", "loadMusicLibraryItems: Authentication failed, skipping API call")
                }
                return@launch
            }

            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "loadMusicLibraryItems: Loading music items for library $musicLibraryId")
            }

            when (
                val result = mediaRepository.getLibraryItems(
                    parentId = musicLibraryId,
                    itemTypes = "MusicAlbum,MusicArtist,Audio", // Specify music-specific item types
                    startIndex = 0,
                    limit = 50,
                    collectionType = "music",
                )
            ) {
                is ApiResult.Success -> {
                    val musicItems = result.data
                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "loadMusicLibraryItems: Successfully loaded ${musicItems.size} music items")
                    }

                    // Add music items to allItems
                    val currentItems = _appState.value.allItems.toMutableList()
                    // Remove existing music items to avoid duplicates
                    currentItems.removeAll { it.type in LibraryType.MUSIC.itemKinds }
                    currentItems.addAll(musicItems)

                    _appState.value = _appState.value.copy(allItems = currentItems)
                }
                is ApiResult.Error -> {
                    // ✅ FIX: Handle job cancellation gracefully - don't show error for navigation cancellations
                    if (result.message.contains("Job was cancelled", ignoreCase = true) || result.errorType == ErrorType.OPERATION_CANCELLED) {
                        if (BuildConfig.DEBUG) {
                            Log.d("MainAppViewModel", "loadMusicLibraryItems: Request cancelled due to navigation, keeping current state")
                        }
                        // Don't update error state for navigation cancellations
                    } else {
                        Log.e("MainAppViewModel", "loadMusicLibraryItems: Failed to load music items: ${result.message}")
                        _appState.value = _appState.value.copy(
                            errorMessage = "Failed to load music items: ${result.message}",
                        )
                    }
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }
        }
    }

    /**
     * Loads other library items (photos, books, etc.) with proper parentId to prevent HTTP 400 errors
     */
    private fun loadOtherLibraryItems(libraryId: String) {
        viewModelScope.launch {
            // ✅ FIX: Use same authentication validation as loadInitialData
            if (!ensureValidTokenWithWait()) {
                if (BuildConfig.DEBUG) {
                    Log.w("MainAppViewModel", "loadOtherLibraryItems: Authentication failed, skipping API call")
                }
                return@launch
            }

            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "loadOtherLibraryItems: Loading other items for library $libraryId")
            }

            // Determine the collection type from the library to specify appropriate item types
            val library = _appState.value.libraries.find { it.id.toString() == libraryId }
            val itemTypes = when (library?.collectionType) {
                org.jellyfin.sdk.model.api.CollectionType.HOMEVIDEOS -> "Video" // Specify video type for home videos to avoid HTTP 400
                org.jellyfin.sdk.model.api.CollectionType.BOOKS -> "Book,AudioBook"
                org.jellyfin.sdk.model.api.CollectionType.PHOTOS -> "Photo"
                // Provide safe fallback for unknown collection types to prevent HTTP 400 errors
                else -> "Video,Audio,Photo,Book,AudioBook" // General mixed content types
            }

            if (BuildConfig.DEBUG) {
                Log.d("MainAppViewModel", "loadOtherLibraryItems: Using itemTypes=$itemTypes for library type ${library?.collectionType}")
                Log.d("MainAppViewModel", "loadOtherLibraryItems: Library name='${library?.name}', collectionType=${library?.collectionType}")
            }

            val collectionTypeStr = when (library?.collectionType) {
                org.jellyfin.sdk.model.api.CollectionType.HOMEVIDEOS -> "homevideos"
                org.jellyfin.sdk.model.api.CollectionType.BOOKS -> "books"
                org.jellyfin.sdk.model.api.CollectionType.PHOTOS -> "photos"
                else -> null
            }

            when (
                val result = mediaRepository.getLibraryItems(
                    parentId = libraryId,
                    itemTypes = itemTypes,
                    startIndex = 0,
                    limit = 50,
                    collectionType = collectionTypeStr,
                )
            ) {
                is ApiResult.Success -> {
                    val otherItems = result.data
                    if (BuildConfig.DEBUG) {
                        Log.d("MainAppViewModel", "loadOtherLibraryItems: Successfully loaded ${otherItems.size} other items")
                    }

                    // Add other items to allItems with proper type checking
                    val currentItems = _appState.value.allItems.toMutableList()
                    // Remove existing items of this type to avoid duplicates
                    currentItems.removeAll { it.type in LibraryType.STUFF.itemKinds }
                    currentItems.addAll(otherItems)
                }
                is ApiResult.Error -> {
                    Log.e("MainAppViewModel", "loadOtherLibraryItems: Failed to load other items: ${result.message}")
                    if (result.errorType != ErrorType.OPERATION_CANCELLED) {
                        _appState.value = _appState.value.copy(
                            errorMessage = "Failed to load other items: ${result.message}",
                        )
                    }
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel is being destroyed, clean up resources
        clearState()
    }
}
