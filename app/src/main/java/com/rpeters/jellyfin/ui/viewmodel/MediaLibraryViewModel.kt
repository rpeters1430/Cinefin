package com.rpeters.jellyfin.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import com.rpeters.jellyfin.ui.screens.LibraryType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import javax.inject.Inject

data class MediaLibraryState(
    val libraries: List<BaseItemDto> = emptyList(),
    val recentlyAdded: List<BaseItemDto> = emptyList(),
    val recentlyAddedByTypes: Map<String, List<BaseItemDto>> = emptyMap(),
    val allMovies: List<BaseItemDto> = emptyList(),
    val allTVShows: List<BaseItemDto> = emptyList(),
    val allItems: List<BaseItemDto> = emptyList(),
    val homeVideosByLibrary: Map<String, List<BaseItemDto>> = emptyMap(),
    val isLoadingLibraries: Boolean = false,
    val isLoadingRecentlyAdded: Boolean = false,
    val isLoadingMovies: Boolean = false,
    val isLoadingTVShows: Boolean = false,
    val hasMoreMovies: Boolean = true,
    val hasMoreTVShows: Boolean = true,
    val moviesPage: Int = 0,
    val tvShowsPage: Int = 0,
    val loadedLibraryTypes: Set<String> = emptySet(),
    val errorMessage: String? = null,
)

/**
 * Dedicated ViewModel for media library operations.
 * Extracted from MainAppViewModel to reduce complexity and prevent merge conflicts.
 */
@HiltViewModel
class MediaLibraryViewModel @Inject constructor(
    private val mediaRepository: JellyfinMediaRepository,
) : ViewModel() {

    private val _libraryState = MutableStateFlow(MediaLibraryState())
    val libraryState: StateFlow<MediaLibraryState> = _libraryState.asStateFlow()

    // Track which library types have been loaded to prevent double loading
    private val loadedLibraryTypes = mutableSetOf<String>()

    /**
     * Load user libraries
     */
    fun loadLibraries(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _libraryState.value = _libraryState.value.copy(isLoadingLibraries = true, errorMessage = null)

            when (val result = mediaRepository.getUserLibraries()) {
                is ApiResult.Success -> {
                    _libraryState.value = _libraryState.value.copy(
                        libraries = result.data,
                        isLoadingLibraries = false,
                    )

                    if (BuildConfig.DEBUG) {
                        Log.d("MediaLibraryViewModel", "loadLibraries: Loaded ${result.data.size} libraries")
                    }
                }
                is ApiResult.Error -> {
                    if (result.errorType != ErrorType.OPERATION_CANCELLED) {
                        Log.e("MediaLibraryViewModel", "loadLibraries: Failed to load libraries: ${result.message}")
                        _libraryState.value = _libraryState.value.copy(
                            isLoadingLibraries = false,
                            errorMessage = "Failed to load libraries: ${result.message}",
                        )
                    } else {
                        _libraryState.value = _libraryState.value.copy(isLoadingLibraries = false)
                    }
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }
        }
    }

    /**
     * Load recently added items
     */
    fun loadRecentlyAdded() {
        viewModelScope.launch {
            _libraryState.value = _libraryState.value.copy(isLoadingRecentlyAdded = true)

            when (val result = mediaRepository.getRecentlyAdded()) {
                is ApiResult.Success -> {
                    _libraryState.value = _libraryState.value.copy(
                        recentlyAdded = result.data,
                        isLoadingRecentlyAdded = false,
                    )

                    if (BuildConfig.DEBUG) {
                        Log.d("MediaLibraryViewModel", "loadRecentlyAdded: Loaded ${result.data.size} items")
                    }
                }
                is ApiResult.Error -> {
                    if (result.errorType != ErrorType.OPERATION_CANCELLED) {
                        Log.e("MediaLibraryViewModel", "loadRecentlyAdded: Failed: ${result.message}")
                        _libraryState.value = _libraryState.value.copy(
                            isLoadingRecentlyAdded = false,
                            errorMessage = "Failed to load recent items: ${result.message}",
                        )
                    } else {
                        _libraryState.value = _libraryState.value.copy(isLoadingRecentlyAdded = false)
                    }
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }
        }
    }

    /**
     * Load movies with pagination
     */
    fun loadMovies(reset: Boolean = false) {
        viewModelScope.launch {
            val currentState = _libraryState.value

            if (reset) {
                _libraryState.value = currentState.copy(
                    allMovies = emptyList(),
                    moviesPage = 0,
                    hasMoreMovies = true,
                    isLoadingMovies = true,
                )
            } else {
                if (currentState.isLoadingMovies || !currentState.hasMoreMovies) {
                    return@launch
                }
                _libraryState.value = currentState.copy(isLoadingMovies = true)
            }

            // Ensure libraries are loaded before proceeding
            val libraries = if (currentState.libraries.isEmpty()) {
                // ✅ FIX: Await the result of loadLibraries to fix race condition
                val result = mediaRepository.getUserLibraries()
                if (result is ApiResult.Success) {
                    _libraryState.value = _libraryState.value.copy(libraries = result.data)
                    result.data
                } else {
                    _libraryState.value = _libraryState.value.copy(
                        isLoadingMovies = false,
                        errorMessage = (result as? ApiResult.Error)?.message ?: "Failed to load libraries",
                    )
                    return@launch
                }
            } else {
                currentState.libraries
            }

            val pageSize = 50
            val page = if (reset) 0 else currentState.moviesPage + 1
            val startIndex = page * pageSize

            // Get movie libraries
            val movieLibraries = libraries.filter { it.collectionType == CollectionType.MOVIES }

            if (movieLibraries.isEmpty()) {
                _libraryState.value = _libraryState.value.copy(
                    isLoadingMovies = false,
                    hasMoreMovies = false,
                    errorMessage = "No movie libraries available",
                )
                return@launch
            }

            // ✅ FIX: Fetch from all movie libraries, not just the first one
            val allNewMovies = mutableListOf<BaseItemDto>()
            var hasMore = false
            var lastError: String? = null

            movieLibraries.forEach { library ->
                when (
                    val result = mediaRepository.getLibraryItems(
                        parentId = library.id.toString(),
                        itemTypes = "Movie",
                        startIndex = startIndex,
                        limit = pageSize,
                        collectionType = "movies",
                    )
                ) {
                    is ApiResult.Success -> {
                        allNewMovies.addAll(result.data)
                        if (result.data.size == pageSize) {
                            hasMore = true // If any library has a full page, we can load more
                        }
                    }
                    is ApiResult.Error -> {
                        if (!result.message.contains("Job was cancelled", ignoreCase = true)) {
                            Log.e("MediaLibraryViewModel", "loadMovies: Failed to load page $page for library ${library.id}: ${result.message}")
                            lastError = result.message
                        }
                    }
                    is ApiResult.Loading -> { /* Handled */ }
                }
            }

            val finalMovies = if (reset) allNewMovies else currentState.allMovies + allNewMovies
            _libraryState.value = _libraryState.value.copy(
                allMovies = finalMovies,
                moviesPage = page,
                hasMoreMovies = hasMore,
                isLoadingMovies = false,
                errorMessage = lastError,
            )

            if (BuildConfig.DEBUG) {
                Log.d("MediaLibraryViewModel", "loadMovies: Loaded ${allNewMovies.size} movies for page $page from ${movieLibraries.size} libraries")
            }
        }
    }

    /**
     * Load TV shows with pagination
     */
    fun loadTVShows(reset: Boolean = false) {
        viewModelScope.launch {
            val currentState = _libraryState.value

            if (reset) {
                _libraryState.value = currentState.copy(
                    allTVShows = emptyList(),
                    tvShowsPage = 0,
                    hasMoreTVShows = true,
                    isLoadingTVShows = true,
                )
            } else {
                if (currentState.isLoadingTVShows || !currentState.hasMoreTVShows) {
                    return@launch
                }
                _libraryState.value = currentState.copy(isLoadingTVShows = true)
            }

            // Ensure libraries are loaded before proceeding
            val libraries = if (currentState.libraries.isEmpty()) {
                // ✅ FIX: Await the result of loadLibraries to fix race condition
                val result = mediaRepository.getUserLibraries()
                if (result is ApiResult.Success) {
                    _libraryState.value = _libraryState.value.copy(libraries = result.data)
                    result.data
                } else {
                    _libraryState.value = _libraryState.value.copy(
                        isLoadingTVShows = false,
                        errorMessage = (result as? ApiResult.Error)?.message ?: "Failed to load libraries",
                    )
                    return@launch
                }
            } else {
                currentState.libraries
            }

            val pageSize = 50
            val page = if (reset) 0 else currentState.tvShowsPage + 1
            val startIndex = page * pageSize

            // Get TV show libraries
            val tvLibraries = libraries.filter { it.collectionType == CollectionType.TVSHOWS }

            if (tvLibraries.isEmpty()) {
                _libraryState.value = _libraryState.value.copy(
                    isLoadingTVShows = false,
                    hasMoreTVShows = false,
                    errorMessage = "No TV show libraries available",
                )
                return@launch
            }

            // ✅ FIX: Fetch from all TV show libraries, not just the first one
            val allNewTVShows = mutableListOf<BaseItemDto>()
            var hasMore = false
            var lastError: String? = null

            tvLibraries.forEach { library ->
                when (
                    val result = mediaRepository.getLibraryItems(
                        parentId = library.id.toString(),
                        itemTypes = "Series",
                        startIndex = startIndex,
                        limit = pageSize,
                        collectionType = "tvshows",
                    )
                ) {
                    is ApiResult.Success -> {
                        allNewTVShows.addAll(result.data)
                        if (result.data.size == pageSize) {
                            hasMore = true // If any library has a full page, we can load more
                        }
                    }
                    is ApiResult.Error -> {
                        if (!result.message.contains("Job was cancelled", ignoreCase = true)) {
                            Log.e("MediaLibraryViewModel", "loadTVShows: Failed to load page $page for library ${library.id}: ${result.message}")
                            lastError = result.message
                        }
                    }
                    is ApiResult.Loading -> { /* Handled */ }
                }
            }

            val finalTVShows = if (reset) allNewTVShows else currentState.allTVShows + allNewTVShows
            _libraryState.value = _libraryState.value.copy(
                allTVShows = finalTVShows,
                tvShowsPage = page,
                hasMoreTVShows = hasMore,
                isLoadingTVShows = false,
                errorMessage = lastError,
            )

            if (BuildConfig.DEBUG) {
                Log.d("MediaLibraryViewModel", "loadTVShows: Loaded ${allNewTVShows.size} TV shows for page $page from ${tvLibraries.size} libraries")
            }
        }
    }

    /**
     * Load library type specific data
     */
    fun loadLibraryTypeData(libraryType: LibraryType, forceRefresh: Boolean = false) {
        val typeKey = libraryType.name

        // Skip if already loaded (prevents double loading)
        if (!forceRefresh && loadedLibraryTypes.contains(typeKey)) {
            if (BuildConfig.DEBUG) {
                Log.d("MediaLibraryViewModel", "loadLibraryTypeData: Skipping $typeKey - already loaded")
            }
            return
        }

        viewModelScope.launch {
            // ✅ FIX: Ensure libraries are loaded before proceeding to load content
            val libraries = if (_libraryState.value.libraries.isEmpty() || forceRefresh) {
                val result = mediaRepository.getUserLibraries(forceRefresh = forceRefresh)
                if (result is ApiResult.Success) {
                    _libraryState.value = _libraryState.value.copy(libraries = result.data)
                    result.data
                } else {
                    _libraryState.value = _libraryState.value.copy(
                        errorMessage = (result as? ApiResult.Error)?.message ?: "Failed to load libraries",
                    )
                    return@launch
                }
            } else {
                _libraryState.value.libraries
            }

            when (libraryType) {
                LibraryType.MOVIES -> loadMovies(reset = true)
                LibraryType.TV_SHOWS -> loadTVShows(reset = true)
                LibraryType.MUSIC -> loadMusicContent(libraries)
                LibraryType.STUFF -> loadOtherContent(libraries)
            }
            loadedLibraryTypes.add(typeKey)
        }
    }

    /**
     * Get library type specific data
     */
    fun getLibraryTypeData(libraryType: LibraryType): List<BaseItemDto> {
        return when (libraryType) {
            LibraryType.MOVIES -> _libraryState.value.allMovies
            LibraryType.TV_SHOWS -> _libraryState.value.allTVShows
            LibraryType.MUSIC, LibraryType.STUFF -> {
                _libraryState.value.allItems.filter { libraryType.itemKinds.contains(it.type) }
            }
        }
    }

    private suspend fun loadMusicContent(libraries: List<BaseItemDto>) {
        val musicLibraries = libraries.filter { it.collectionType == CollectionType.MUSIC }

        if (musicLibraries.isEmpty()) {
            Log.w("MediaLibraryViewModel", "No music libraries found")
            return
        }

        // ✅ FIX: Process all music libraries, not just the first one
        musicLibraries.forEach { library ->
            loadLibraryItems(library.id.toString(), "MusicAlbum,MusicArtist,Audio", "music")
        }
    }

    private suspend fun loadOtherContent(libraries: List<BaseItemDto>) {
        val otherLibraries = libraries.filter {
            it.collectionType !in setOf(CollectionType.MOVIES, CollectionType.TVSHOWS, CollectionType.MUSIC)
        }

        if (otherLibraries.isEmpty()) {
            Log.w("MediaLibraryViewModel", "No other content libraries found")
            return
        }

        // ✅ FIX: Process all "other" libraries
        otherLibraries.forEach { library ->
            loadLibraryItems(library.id.toString(), "Video,Audio,Photo,Book,AudioBook", null)
        }
    }

    private suspend fun loadLibraryItems(
        libraryId: String,
        itemTypes: String,
        collectionType: String?,
    ) {
        when (
            val result = mediaRepository.getLibraryItems(
                parentId = libraryId,
                itemTypes = itemTypes,
                startIndex = 0,
                limit = 50,
                collectionType = collectionType,
            )
        ) {
            is ApiResult.Success -> {
                val currentItems = _libraryState.value.allItems.toMutableList()
                currentItems.addAll(result.data)
                _libraryState.value = _libraryState.value.copy(allItems = currentItems)

                if (BuildConfig.DEBUG) {
                    Log.d("MediaLibraryViewModel", "loadLibraryItems: Loaded ${result.data.size} items from library $libraryId")
                }
            }
            is ApiResult.Error -> {
                if (result.errorType != ErrorType.OPERATION_CANCELLED) {
                    Log.e("MediaLibraryViewModel", "loadLibraryItems: Failed to load items: ${result.message}")
                    _libraryState.value = _libraryState.value.copy(
                        errorMessage = "Failed to load library items: ${result.message}",
                    )
                }
            }
            is ApiResult.Loading -> {
                // Already handled
            }
        }
    }

    /**
     * Clear error messages
     */
    fun clearError() {
        _libraryState.value = _libraryState.value.copy(errorMessage = null)
    }

    /**
     * Clear loaded library types
     */
    fun clearLoadedLibraryTypes() {
        loadedLibraryTypes.clear()
        _libraryState.value = _libraryState.value.copy(loadedLibraryTypes = emptySet())
    }

    /**
     * Clear all state
     */
    fun clearState() {
        _libraryState.value = MediaLibraryState()
        loadedLibraryTypes.clear()
    }
}
