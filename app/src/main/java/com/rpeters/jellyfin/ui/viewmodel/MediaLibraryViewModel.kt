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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    private suspend fun ensureLibrariesLoaded(): List<BaseItemDto>? {
        val currentState = _libraryState.value
        if (currentState.libraries.isNotEmpty()) {
            return currentState.libraries
        }

        return when (val result = mediaRepository.getUserLibraries()) {
            is ApiResult.Success -> {
                _libraryState.value = currentState.copy(libraries = result.data)
                result.data
            }
            is ApiResult.Error -> {
                if (result.errorType != ErrorType.OPERATION_CANCELLED) {
                    Log.e("MediaLibraryViewModel", "ensureLibrariesLoaded: Failed to load libraries: ${result.message}")
                    _libraryState.value = currentState.copy(
                        errorMessage = result.message ?: "Failed to load libraries",
                    )
                }
                null
            }
            is ApiResult.Loading -> null
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

            val libraries = ensureLibrariesLoaded()
            if (libraries == null) {
                _libraryState.value = _libraryState.value.copy(isLoadingMovies = false)
                return@launch
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

            val allNewMovies = mutableListOf<BaseItemDto>()
            var lastError: String? = null

            val results = coroutineScope {
                movieLibraries.map { library ->
                    async {
                        library to mediaRepository.getLibraryItems(
                            parentId = library.id.toString(),
                            itemTypes = "Movie",
                            startIndex = startIndex,
                            limit = pageSize,
                            collectionType = "movies",
                        )
                    }
                }.awaitAll()
            }

            var successCount = 0
            var hasMore = true
            results.forEach { (library, result) ->
                when (result) {
                    is ApiResult.Success -> {
                        allNewMovies.addAll(result.data)
                        successCount++
                        if (result.data.size < pageSize) {
                            hasMore = false
                        }
                    }
                    is ApiResult.Error -> {
                        if (!result.message.contains("Job was cancelled", ignoreCase = true)) {
                            Log.e(
                                "MediaLibraryViewModel",
                                "loadMovies: Failed to load page $page for library ${library.id}: ${result.message}",
                            )
                            lastError = result.message
                        }
                    }
                    is ApiResult.Loading -> { /* Handled */ }
                }
            }

            if (successCount > 0) {
                val finalMovies = if (reset) allNewMovies else currentState.allMovies + allNewMovies
                _libraryState.value = _libraryState.value.copy(
                    allMovies = finalMovies,
                    moviesPage = page,
                    hasMoreMovies = hasMore,
                    isLoadingMovies = false,
                    errorMessage = lastError,
                )
            } else {
                _libraryState.value = _libraryState.value.copy(
                    isLoadingMovies = false,
                    errorMessage = lastError,
                )
            }

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

            val libraries = ensureLibrariesLoaded()
            if (libraries == null) {
                _libraryState.value = _libraryState.value.copy(isLoadingTVShows = false)
                return@launch
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

            val allNewTVShows = mutableListOf<BaseItemDto>()
            var lastError: String? = null

            val results = coroutineScope {
                tvLibraries.map { library ->
                    async {
                        library to mediaRepository.getLibraryItems(
                            parentId = library.id.toString(),
                            itemTypes = "Series",
                            startIndex = startIndex,
                            limit = pageSize,
                            collectionType = "tvshows",
                        )
                    }
                }.awaitAll()
            }

            var successCount = 0
            var hasMore = true
            results.forEach { (library, result) ->
                when (result) {
                    is ApiResult.Success -> {
                        allNewTVShows.addAll(result.data)
                        successCount++
                        if (result.data.size < pageSize) {
                            hasMore = false
                        }
                    }
                    is ApiResult.Error -> {
                        if (!result.message.contains("Job was cancelled", ignoreCase = true)) {
                            Log.e(
                                "MediaLibraryViewModel",
                                "loadTVShows: Failed to load page $page for library ${library.id}: ${result.message}",
                            )
                            lastError = result.message
                        }
                    }
                    is ApiResult.Loading -> { /* Handled */ }
                }
            }

            if (successCount > 0) {
                val finalTVShows = if (reset) allNewTVShows else currentState.allTVShows + allNewTVShows
                _libraryState.value = _libraryState.value.copy(
                    allTVShows = finalTVShows,
                    tvShowsPage = page,
                    hasMoreTVShows = hasMore,
                    isLoadingTVShows = false,
                    errorMessage = lastError,
                )
            } else {
                _libraryState.value = _libraryState.value.copy(
                    isLoadingTVShows = false,
                    errorMessage = lastError,
                )
            }

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

        val results = coroutineScope {
            musicLibraries.map { library ->
                async {
                    mediaRepository.getLibraryItems(
                        parentId = library.id.toString(),
                        itemTypes = "MusicAlbum,MusicArtist,Audio",
                        startIndex = 0,
                        limit = 50,
                        collectionType = "music",
                    )
                }
            }.awaitAll()
        }

        val allItems = mutableListOf<BaseItemDto>()
        var lastError: String? = null
        results.forEach { result ->
            when (result) {
                is ApiResult.Success -> allItems.addAll(result.data)
                is ApiResult.Error -> {
                    if (result.errorType != ErrorType.OPERATION_CANCELLED) {
                        lastError = result.message
                    }
                }
                is ApiResult.Loading -> { /* Handled */ }
            }
        }

        if (allItems.isNotEmpty()) {
            val currentItems = _libraryState.value.allItems + allItems
            _libraryState.value = _libraryState.value.copy(allItems = currentItems, errorMessage = lastError)
        } else if (lastError != null) {
            _libraryState.value = _libraryState.value.copy(errorMessage = lastError)
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

        val results = coroutineScope {
            otherLibraries.map { library ->
                async {
                    mediaRepository.getLibraryItems(
                        parentId = library.id.toString(),
                        itemTypes = "Video,Audio,Photo,Book,AudioBook",
                        startIndex = 0,
                        limit = 50,
                        collectionType = null,
                    )
                }
            }.awaitAll()
        }

        val allItems = mutableListOf<BaseItemDto>()
        var lastError: String? = null
        results.forEach { result ->
            when (result) {
                is ApiResult.Success -> allItems.addAll(result.data)
                is ApiResult.Error -> {
                    if (result.errorType != ErrorType.OPERATION_CANCELLED) {
                        lastError = result.message
                    }
                }
                is ApiResult.Loading -> { /* Handled */ }
            }
        }

        if (allItems.isNotEmpty()) {
            val currentItems = _libraryState.value.allItems + allItems
            _libraryState.value = _libraryState.value.copy(allItems = currentItems, errorMessage = lastError)
        } else if (lastError != null) {
            _libraryState.value = _libraryState.value.copy(errorMessage = lastError)
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
