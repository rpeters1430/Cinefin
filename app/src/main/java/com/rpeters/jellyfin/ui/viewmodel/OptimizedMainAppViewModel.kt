package com.rpeters.jellyfin.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.repository.JellyfinAuthRepository
import com.rpeters.jellyfin.data.repository.JellyfinStreamRepository
import com.rpeters.jellyfin.data.repository.JellyfinUserRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import com.rpeters.jellyfin.data.repository.common.LibraryLoadingManager
import com.rpeters.jellyfin.data.repository.common.LibraryLoadingState
import com.rpeters.jellyfin.data.repository.common.LibraryTypeLoadRequest
import com.rpeters.jellyfin.ui.screens.LibraryType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import javax.inject.Inject

/**
 * Optimized ViewModel that addresses the systemic issues in library loading:
 *
 * 1. Eliminates duplicate loading operations through centralized coordination
 * 2. Provides proper error handling with recovery mechanisms
 * 3. Uses intelligent caching and pagination
 * 4. Implements proper lifecycle management
 */
@HiltViewModel
class OptimizedMainAppViewModel @Inject constructor(
    private val authRepository: JellyfinAuthRepository,
    private val userRepository: JellyfinUserRepository,
    private val streamRepository: JellyfinStreamRepository,
    private val credentialManager: SecureCredentialManager,
    private val libraryLoadingManager: LibraryLoadingManager,
) : ViewModel() {

    companion object {
        private const val TAG = "OptimizedMainAppViewModel"
        private const val DEFAULT_PAGE_SIZE = 50
        private const val MAX_ITEMS_PER_TYPE = 200
    }

    // Core application state
    private val _appState = MutableStateFlow(OptimizedAppState())
    val appState: StateFlow<OptimizedAppState> = _appState.asStateFlow()

    // Combined state for UI convenience
    val uiState = combine(
        appState,
        libraryLoadingManager.libraryLoadingState,
    ) { app, loading ->
        OptimizedUiState(
            appState = app,
            loadingStates = loading,
            isInitialLoading = loading.values.any { it is LibraryLoadingState.Loading },
            hasErrors = loading.values.any { it is LibraryLoadingState.Error },
        )
    }

    // Authentication state
    val currentServer = authRepository.currentServer
    val isConnected = authRepository.isConnected

    init {
        loadInitialData()
    }

    /**
     * Loads initial data with intelligent coordination and error handling.
     */
    fun loadInitialData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            try {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Starting initial data load (forceRefresh=$forceRefresh)")
                }

                // Check authentication first
                if (!authRepository.isUserAuthenticated()) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "User not authenticated, skipping data load")
                    }
                    _appState.value = _appState.value.copy(
                        errorMessage = "Authentication required. Please log in again.",
                    )
                    return@launch
                }

                // Clear any previous errors
                _appState.value = _appState.value.copy(errorMessage = null)

                // Load libraries first - this is the foundation for everything else
                when (val librariesResult = libraryLoadingManager.loadLibraries(forceRefresh)) {
                    is ApiResult.Success -> {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Loaded ${librariesResult.data.size} libraries")
                        }

                        _appState.value = _appState.value.copy(
                            libraries = librariesResult.data,
                        )

                        // Load library-specific data based on available libraries
                        loadLibrarySpecificData(librariesResult.data, forceRefresh)
                    }
                    is ApiResult.Error -> {
                        if (librariesResult.errorType != ErrorType.OPERATION_CANCELLED) {
                            Log.e(TAG, "Failed to load libraries: ${librariesResult.message}")
                            _appState.value = _appState.value.copy(
                                errorMessage = "Failed to load libraries: ${librariesResult.message}",
                            )
                        }
                    }
                    else -> { /* Loading state handled by manager */ }
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /**
     * Loads library-specific data in parallel based on available libraries.
     */
    private suspend fun loadLibrarySpecificData(libraries: List<BaseItemDto>, forceRefresh: Boolean) {
        if (libraries.isEmpty()) return

        // Group libraries by collection type
        val librariesByType = libraries.groupBy { it.collectionType }

        // Build batch requests for different library types
        val loadRequests = mutableListOf<LibraryTypeLoadRequest>()

        // Movies
        librariesByType[CollectionType.MOVIES]?.firstOrNull()?.let { movieLib ->
            loadRequests.add(
                LibraryTypeLoadRequest(
                    key = "movies",
                    libraryId = movieLib.id.toString(),
                    collectionType = CollectionType.MOVIES,
                    itemTypes = listOf(BaseItemKind.MOVIE),
                    limit = DEFAULT_PAGE_SIZE,
                    forceRefresh = forceRefresh,
                ),
            )
        }

        // TV Shows
        librariesByType[CollectionType.TVSHOWS]?.firstOrNull()?.let { tvLib ->
            loadRequests.add(
                LibraryTypeLoadRequest(
                    key = "tvshows",
                    libraryId = tvLib.id.toString(),
                    collectionType = CollectionType.TVSHOWS,
                    itemTypes = listOf(BaseItemKind.SERIES),
                    limit = DEFAULT_PAGE_SIZE,
                    forceRefresh = forceRefresh,
                ),
            )
        }

        // Music
        librariesByType[CollectionType.MUSIC]?.firstOrNull()?.let { musicLib ->
            loadRequests.add(
                LibraryTypeLoadRequest(
                    key = "music",
                    libraryId = musicLib.id.toString(),
                    collectionType = CollectionType.MUSIC,
                    itemTypes = listOf(BaseItemKind.MUSIC_ALBUM, BaseItemKind.MUSIC_ARTIST),
                    limit = DEFAULT_PAGE_SIZE,
                    forceRefresh = forceRefresh,
                ),
            )
        }

        // Load other library types
        val otherLibraries = librariesByType.filterKeys {
            it !in setOf(CollectionType.MOVIES, CollectionType.TVSHOWS, CollectionType.MUSIC)
        }

        otherLibraries.forEach { (collectionType, libs) ->
            libs.firstOrNull()?.let { lib ->
                val itemTypes = when (collectionType) {
                    CollectionType.HOMEVIDEOS -> listOf(BaseItemKind.VIDEO)
                    CollectionType.BOOKS -> listOf(BaseItemKind.BOOK, BaseItemKind.AUDIO_BOOK)
                    else -> null // Let server decide
                }

                loadRequests.add(
                    LibraryTypeLoadRequest(
                        key = "other_${collectionType?.name?.lowercase() ?: "unknown"}",
                        libraryId = lib.id.toString(),
                        collectionType = collectionType,
                        itemTypes = itemTypes,
                        limit = DEFAULT_PAGE_SIZE,
                        forceRefresh = forceRefresh,
                    ),
                )
            }
        }

        if (loadRequests.isNotEmpty()) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Loading ${loadRequests.size} library types in batch")
            }

            // Load in parallel
            val results = libraryLoadingManager.loadLibraryTypesBatch(loadRequests)

            // Process results
            processLibraryTypeResults(results)
        }
    }

    /**
     * Processes batch loading results and updates app state.
     */
    private fun processLibraryTypeResults(results: Map<String, ApiResult<List<BaseItemDto>>>) {
        val currentState = _appState.value
        var updatedMovies = currentState.movies
        var updatedTVShows = currentState.tvShows
        var updatedMusic = currentState.music
        var updatedOtherItems = currentState.otherItems.toMutableMap()

        results.forEach { (key, result) ->
            when (result) {
                is ApiResult.Success -> {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Successfully loaded ${result.data.size} items for $key")
                    }

                    when (key) {
                        "movies" -> updatedMovies = result.data
                        "tvshows" -> updatedTVShows = result.data
                        "music" -> updatedMusic = result.data
                        else -> if (key.startsWith("other_")) {
                            updatedOtherItems[key] = result.data
                        }
                    }
                }
                is ApiResult.Error -> {
                    if (result.errorType != ErrorType.OPERATION_CANCELLED) {
                        Log.e(TAG, "Failed to load $key: ${result.message}")
                    }
                }
                else -> { /* Loading handled by manager */ }
            }
        }

        // Update state with all loaded data
        _appState.value = currentState.copy(
            movies = updatedMovies,
            tvShows = updatedTVShows,
            music = updatedMusic,
            otherItems = updatedOtherItems,
        )

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Updated state - Movies: ${updatedMovies.size}, TV: ${updatedTVShows.size}, Music: ${updatedMusic.size}, Other: ${updatedOtherItems.size}")
        }
    }

    /**
     * Loads more items for a specific library type with pagination.
     */
    fun loadMoreItems(libraryType: LibraryType) {
        viewModelScope.launch {
            try {
                val libraries = _appState.value.libraries
                val targetLibrary = when (libraryType) {
                    LibraryType.MOVIES -> libraries.find { it.collectionType == CollectionType.MOVIES }
                    LibraryType.TV_SHOWS -> libraries.find { it.collectionType == CollectionType.TVSHOWS }
                    LibraryType.MUSIC -> libraries.find { it.collectionType == CollectionType.MUSIC }
                    LibraryType.STUFF -> libraries.find {
                        it.collectionType !in setOf(CollectionType.MOVIES, CollectionType.TVSHOWS, CollectionType.MUSIC)
                    }
                } ?: return@launch

                val currentItems = when (libraryType) {
                    LibraryType.MOVIES -> _appState.value.movies
                    LibraryType.TV_SHOWS -> _appState.value.tvShows
                    LibraryType.MUSIC -> _appState.value.music
                    LibraryType.STUFF -> _appState.value.otherItems.values.flatten()
                }

                // Check if we should load more (prevent loading too many items)
                if (currentItems.size >= MAX_ITEMS_PER_TYPE) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Max items reached for ${libraryType.displayName}")
                    }
                    return@launch
                }

                val itemTypes = libraryType.itemKinds
                val collectionType = when (libraryType) {
                    LibraryType.MOVIES -> CollectionType.MOVIES
                    LibraryType.TV_SHOWS -> CollectionType.TVSHOWS
                    LibraryType.MUSIC -> CollectionType.MUSIC
                    LibraryType.STUFF -> null // Mixed content
                }

                val result = libraryLoadingManager.loadLibraryItems(
                    libraryId = targetLibrary.id.toString(),
                    collectionType = collectionType,
                    itemTypes = itemTypes,
                    startIndex = currentItems.size,
                    limit = DEFAULT_PAGE_SIZE,
                )

                when (result) {
                    is ApiResult.Success -> {
                        val newItems = result.data
                        if (newItems.isNotEmpty()) {
                            val updatedItems = currentItems + newItems

                            when (libraryType) {
                                LibraryType.MOVIES -> {
                                    _appState.value = _appState.value.copy(movies = updatedItems)
                                }
                                LibraryType.TV_SHOWS -> {
                                    _appState.value = _appState.value.copy(tvShows = updatedItems)
                                }
                                LibraryType.MUSIC -> {
                                    _appState.value = _appState.value.copy(music = updatedItems)
                                }
                                LibraryType.STUFF -> {
                                    val otherKey = "other_${collectionType?.name?.lowercase() ?: "mixed"}"
                                    val updatedOther = _appState.value.otherItems.toMutableMap()
                                    updatedOther[otherKey] = updatedItems
                                    _appState.value = _appState.value.copy(otherItems = updatedOther)
                                }
                            }

                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "Loaded ${newItems.size} more ${libraryType.displayName} items")
                            }
                        }
                    }
                    is ApiResult.Error -> {
                        Log.e(TAG, "Failed to load more ${libraryType.displayName}: ${result.message}")
                        _appState.value = _appState.value.copy(
                            errorMessage = "Failed to load more items: ${result.message}",
                        )
                    }
                    else -> { /* Loading handled by manager */ }
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /**
     * Gets items for a specific library type.
     */
    fun getLibraryItems(libraryType: LibraryType): List<BaseItemDto> {
        return when (libraryType) {
            LibraryType.MOVIES -> _appState.value.movies
            LibraryType.TV_SHOWS -> _appState.value.tvShows
            LibraryType.MUSIC -> _appState.value.music
            LibraryType.STUFF -> _appState.value.otherItems.values.flatten()
        }
    }

    /**
     * Gets image URL using the stream repository.
     */
    fun getImageUrl(item: BaseItemDto): String? {
        return streamRepository.getImageUrl(item.id.toString(), "Primary", null)
    }

    /**
     * Gets backdrop URL using the stream repository.
     */
    fun getBackdropUrl(item: BaseItemDto): String? {
        return streamRepository.getBackdropUrl(item)
    }

    /**
     * Clears any error messages.
     */
    fun clearError() {
        _appState.value = _appState.value.copy(errorMessage = null)
    }

    /**
     * Refreshes all data.
     */
    fun refreshAll() {
        loadInitialData(forceRefresh = true)
    }

    /**
     * Logs out the user and clears all data.
     */
    fun logout() {
        viewModelScope.launch {
            try {
                userRepository.logout()
                credentialManager.clearCredentials()
                libraryLoadingManager.cancelAllOperations()
                _appState.value = OptimizedAppState()
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            libraryLoadingManager.cancelAllOperations()
        }
    }
}

/**
 * Optimized app state with cleaner organization.
 */
data class OptimizedAppState(
    val libraries: List<BaseItemDto> = emptyList(),
    val movies: List<BaseItemDto> = emptyList(),
    val tvShows: List<BaseItemDto> = emptyList(),
    val music: List<BaseItemDto> = emptyList(),
    val otherItems: Map<String, List<BaseItemDto>> = emptyMap(),
    val errorMessage: String? = null,
)

/**
 * Combined UI state for easy consumption by Compose.
 */
data class OptimizedUiState(
    val appState: OptimizedAppState,
    val loadingStates: Map<String, LibraryLoadingState>,
    val isInitialLoading: Boolean,
    val hasErrors: Boolean,
)
