package com.rpeters.jellyfin.ui.viewmodel

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.common.DispatcherProvider
import com.rpeters.jellyfin.data.repository.JellyfinAuthRepository
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.JellyfinSearchRepository
import com.rpeters.jellyfin.data.repository.JellyfinStreamRepository
import com.rpeters.jellyfin.data.repository.JellyfinUserRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.ui.player.CastManager
import com.rpeters.jellyfin.ui.screens.LibraryType
import com.rpeters.jellyfin.utils.SecureLogger
import com.rpeters.jellyfin.utils.isWatched
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import java.util.UUID
import javax.inject.Inject

/**
 * Combined state from all component ViewModels for backward compatibility
 */
data class MainAppState(
    // Library data
    val libraries: List<BaseItemDto> = emptyList(),
    val recentlyAdded: List<BaseItemDto> = emptyList(),
    val recentlyAddedByTypes: Map<String, List<BaseItemDto>> = emptyMap(),
    val allMovies: List<BaseItemDto> = emptyList(),
    val allTVShows: List<BaseItemDto> = emptyList(),
    val allItems: List<BaseItemDto> = emptyList(),
    val itemsByLibrary: Map<String, List<BaseItemDto>> = emptyMap(),

    // Loading states
    val isLoading: Boolean = false,
    val isLoadingMovies: Boolean = false,
    val isLoadingTVShows: Boolean = false,
    val hasMoreMovies: Boolean = true,
    val hasMoreTVShows: Boolean = true,
    val moviesPage: Int = 0,
    val tvShowsPage: Int = 0,

    // Search
    val searchResults: List<BaseItemDto> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,

    // User data
    val favorites: List<BaseItemDto> = emptyList(),

    // Pagination (legacy)
    val isLoadingMore: Boolean = false,
    val hasMoreItems: Boolean = false,
    val currentPage: Int = 0,
    val loadedLibraryTypes: Set<String> = emptySet(),

    // Per-library pagination tracking
    val libraryPaginationState: Map<String, LibraryPaginationState> = emptyMap(),

    val errorMessage: String? = null,
)

/**
 * Tracks pagination state for each library
 */
data class LibraryPaginationState(
    val loadedCount: Int = 0,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
)

/**
 * Refactored MainAppViewModel that delegates to smaller, focused repositories.
 * This reduces complexity from 1778 lines to ~200 lines (90% reduction) and prevents merge conflicts.
 *
 * ROADMAP Step 4 Implementation:
 * - Removed duplicated ensureValidTokenWithWait methods
 * - Reduced massive size to prevent merge artifacts
 * - Simplified by delegating to specialized repositories
 */
@OptInAppExperimentalApis
@HiltViewModel
class MainAppViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: JellyfinRepository,
    private val authRepository: JellyfinAuthRepository,
    private val mediaRepository: JellyfinMediaRepository,
    private val userRepository: JellyfinUserRepository,
    private val streamRepository: JellyfinStreamRepository,
    private val searchRepository: JellyfinSearchRepository,
    private val credentialManager: SecureCredentialManager,
    @UnstableApi private val castManager: CastManager,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    internal fun libraryItemKey(item: BaseItemDto): String =
        item.id?.toString()
            ?: "${item.name ?: "unknown"}-${item.sortName ?: "unknown"}-${item.type ?: "unknown"}"

    internal fun mergeLibraryItems(currentItems: List<BaseItemDto>, newItems: List<BaseItemDto>): List<BaseItemDto> {
        val merged = LinkedHashMap<String, BaseItemDto>(currentItems.size + newItems.size)
        currentItems.forEach { item -> merged[libraryItemKey(item)] = item }
        newItems.forEach { item -> merged[libraryItemKey(item)] = item }
        return merged.values.toList()
    }

    // Simplified state management
    private val _appState = MutableStateFlow(MainAppState())
    val appState: StateFlow<MainAppState> = _appState.asStateFlow()

    // Delegate to repositories for compatibility
    val currentServer = repository.currentServer
    val isConnected = repository.isConnected

    @VisibleForTesting
    internal fun setAppStateForTest(state: MainAppState) {
        _appState.value = state
    }

    @VisibleForTesting
    internal fun updateAppStateForTest(block: (MainAppState) -> MainAppState) {
        _appState.value = block(_appState.value)
    }

    // Simple authentication check - no duplicate methods
    private suspend fun ensureValidToken(): Boolean {
        return withContext(dispatchers.io) {
            try {
                if (!authRepository.isTokenExpired()) return@withContext true
                return@withContext authRepository.reAuthenticate()
            } catch (e: Exception) {
                false
            }
        }
    }

    // SIMPLIFIED PUBLIC API - Direct repository calls

    /**
     * Load recently added content grouped by item type.
     * Fetches several [BaseItemKind] categories in parallel and returns a map
     * keyed by each type's name (e.g. "MOVIE", "SERIES").
     */
    private suspend fun loadRecentlyAddedByTypes(forceRefresh: Boolean = false): Map<String, List<BaseItemDto>> =
        coroutineScope {
            val contentTypes = listOf(
                BaseItemKind.MOVIE,
                BaseItemKind.SERIES,
                BaseItemKind.EPISODE,
                BaseItemKind.AUDIO,
                BaseItemKind.BOOK,
                BaseItemKind.AUDIO_BOOK,
                BaseItemKind.VIDEO,
            )

            contentTypes.map { contentType ->
                async {
                    mediaRepository.getRecentlyAddedByType(contentType, forceRefresh = forceRefresh)
                        .let { result ->
                            if (result is ApiResult.Success && result.data.isNotEmpty()) {
                                contentType.name to result.data
                            } else {
                                null
                            }
                        }
                }
            }.awaitAll().filterNotNull().toMap()
        }

    fun loadInitialData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!ensureValidToken()) return@launch

            // Prevent concurrent executions that cause frame drops
            val currentState = _appState.value
            if (currentState.isLoading && !forceRefresh) {
                SecureLogger.v(
                    "MainAppViewModel-Initial",
                    "⏭️ Skipping loadInitialData - already loading",
                )
                return@launch
            }

            // ✅ DEBUG: Log initial data loading start
            SecureLogger.v(
                "MainAppViewModel-Initial",
                "🚀 Starting loadInitialData (forceRefresh=$forceRefresh)",
            )
            SecureLogger.v(
                "MainAppViewModel-Initial",
                "  Current libraries count: ${currentState.libraries.size}",
            )

            _appState.value = _appState.value.copy(
                isLoading = true,
                errorMessage = null,
            )

            try {
                // Execute heavy work on IO dispatcher to prevent main thread blocking
                withContext(dispatchers.io) {
                    val librariesDeferred =
                        async { mediaRepository.getUserLibraries(forceRefresh = forceRefresh) }
                    val recentDeferred =
                        async { mediaRepository.getRecentlyAdded(forceRefresh = forceRefresh) }
                    val recentByTypesDeferred =
                        async { loadRecentlyAddedByTypes(forceRefresh = forceRefresh) }

                    awaitAll(librariesDeferred, recentDeferred, recentByTypesDeferred)

                    val librariesResult = librariesDeferred.getCompleted()
                    val recentResult = recentDeferred.getCompleted()
                    val recentlyAddedByTypes = recentByTypesDeferred.getCompleted()

                    SecureLogger.v("MainAppViewModel-Initial", "📦 API calls completed:")
                    SecureLogger.v(
                        "MainAppViewModel-Initial",
                        "  Libraries result: ${if (librariesResult is ApiResult.Success) "Success (${librariesResult.data.size})" else librariesResult::class.simpleName}",
                    )
                    SecureLogger.v(
                        "MainAppViewModel-Initial",
                        "  Recent result: ${if (recentResult is ApiResult.Success) "Success (${recentResult.data.size})" else recentResult::class.simpleName}",
                    )

                    when (librariesResult) {
                        is ApiResult.Success -> {
                            val libraries = librariesResult.data
                            val recentlyAdded = if (recentResult is ApiResult.Success) {
                                recentResult.data
                            } else {
                                emptyList()
                            }

                            SecureLogger.v("MainAppViewModel-Initial", "✅ Setting new state:")
                            libraries.forEach { lib ->
                                SecureLogger.v(
                                    "MainAppViewModel-Initial",
                                    "  Library: ${lib.name} (${lib.collectionType}) id=${lib.id}",
                                )
                            }

                            _appState.value = _appState.value.copy(
                                libraries = libraries,
                                recentlyAdded = recentlyAdded,
                                recentlyAddedByTypes = recentlyAddedByTypes,
                                isLoading = false,
                            )

                            SecureLogger.v(
                                "MainAppViewModel-Initial",
                                "🎯 loadInitialData completed - Libraries now: ${libraries.size}",
                            )
                        }

                        is ApiResult.Error -> {
                            _appState.value = _appState.value.copy(
                                isLoading = false,
                                errorMessage = "Failed to load libraries: ${librariesResult.message}",
                            )
                        }

                        is ApiResult.Loading -> {
                            // Already handled
                        }
                    }
                }
            } catch (e: Exception) {
                _appState.value = _appState.value.copy(
                    isLoading = false,
                    errorMessage = "Error loading data: ${e.message}",
                )
            }
        }
    }

    fun loadFavorites() {
        viewModelScope.launch {
            when (val result = repository.getFavorites()) {
                is ApiResult.Success -> {
                    _appState.value = _appState.value.copy(favorites = result.data)
                }

                is ApiResult.Error -> {
                    _appState.value = _appState.value.copy(
                        errorMessage = "Failed to load favorites: ${result.message}",
                    )
                }

                is ApiResult.Loading -> {
                    // Handle loading state
                }
            }
        }
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

    fun toggleFavorite(item: BaseItemDto) {
        viewModelScope.launch {
            val currentFavoriteState = item.userData?.isFavorite ?: false
            when (
                val result =
                    userRepository.toggleFavorite(item.id.toString(), !currentFavoriteState)
            ) {
                is ApiResult.Success -> {
                    loadInitialData() // Refresh data
                }

                is ApiResult.Error -> {
                    _appState.value = _appState.value.copy(
                        errorMessage = "Failed to update favorite: ${result.message}",
                    )
                }

                is ApiResult.Loading -> {
                    // Handle loading state
                }
            }
        }
    }

    fun markAsWatched(item: BaseItemDto) {
        viewModelScope.launch {
            when (val result = userRepository.markAsWatched(item.id.toString())) {
                is ApiResult.Success -> {
                    // Update item userData in state immediately for responsive UI
                    updateItemWatchedStatus(item.id, isWatched = true)
                }
                is ApiResult.Error -> {
                    _appState.value = _appState.value.copy(
                        errorMessage = "Failed to mark as watched: ${result.message}",
                    )
                }

                is ApiResult.Loading -> {
                    // Handle loading state
                }
            }
        }
    }

    fun markAsUnwatched(item: BaseItemDto) {
        viewModelScope.launch {
            when (val result = userRepository.markAsUnwatched(item.id.toString())) {
                is ApiResult.Success -> {
                    // Update item userData in state immediately for responsive UI
                    updateItemWatchedStatus(item.id, isWatched = false)
                }
                is ApiResult.Error -> {
                    _appState.value = _appState.value.copy(
                        errorMessage = "Failed to mark as unwatched: ${result.message}",
                    )
                }

                is ApiResult.Loading -> {
                    // Handle loading state
                }
            }
        }
    }

    private fun updateItemWatchedStatus(itemId: UUID?, isWatched: Boolean) {
        if (itemId == null) return

        val currentState = _appState.value

        // Helper function to update item in a list
        fun updateItemInList(items: List<BaseItemDto>): List<BaseItemDto> {
            return items.map { item ->
                if (item.id == itemId) {
                    item.copy(
                        userData = item.userData?.copy(
                            played = isWatched,
                            playedPercentage = if (isWatched) 100.0 else 0.0,
                        ),
                    )
                } else {
                    item
                }
            }
        }

        // Update all state lists
        _appState.value = currentState.copy(
            allItems = updateItemInList(currentState.allItems),
            allMovies = updateItemInList(currentState.allMovies),
            allTVShows = updateItemInList(currentState.allTVShows),
            recentlyAdded = updateItemInList(currentState.recentlyAdded),
            recentlyAddedByTypes = currentState.recentlyAddedByTypes.mapValues { (_, items) ->
                updateItemInList(items)
            },
            itemsByLibrary = currentState.itemsByLibrary.mapValues { (_, items) ->
                updateItemInList(items)
            },
        )
    }

    fun toggleWatchedStatus(item: BaseItemDto) {
        if (item.isWatched()) {
            markAsUnwatched(item)
        } else {
            markAsWatched(item)
        }
    }

    fun deleteItem(item: BaseItemDto, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            when (val result = userRepository.deleteItemAsAdmin(item.id.toString())) {
                is ApiResult.Success -> {
                    val updatedLibraryItems = _appState.value.itemsByLibrary.mapValues { (_, items) ->
                        items.filterNot { it.id == item.id }
                    }

                    // Remove from all state lists
                    _appState.value = _appState.value.copy(
                        recentlyAdded = _appState.value.recentlyAdded.filterNot { it.id == item.id },
                        favorites = _appState.value.favorites.filterNot { it.id == item.id },
                        searchResults = _appState.value.searchResults.filterNot { it.id == item.id },
                        allMovies = _appState.value.allMovies.filterNot { it.id == item.id },
                        allTVShows = _appState.value.allTVShows.filterNot { it.id == item.id },
                        allItems = _appState.value.allItems.filterNot { it.id == item.id },
                        itemsByLibrary = updatedLibraryItems,
                    )
                    onResult(true, null)
                }

                is ApiResult.Error -> {
                    _appState.value = _appState.value.copy(
                        errorMessage = "Failed to delete item: ${result.message}",
                    )
                    onResult(false, result.message)
                }

                is ApiResult.Loading -> {
                    // Handle loading state
                }
            }
        }
    }

    fun refreshItemMetadata(item: BaseItemDto, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            when (val result = userRepository.refreshItemMetadata(item.id.toString())) {
                is ApiResult.Success -> {
                    onResult(true, null)
                }

                is ApiResult.Error -> {
                    _appState.value = _appState.value.copy(
                        errorMessage = "Failed to refresh metadata: ${result.message}",
                    )
                    onResult(false, result.message)
                }

                is ApiResult.Loading -> {
                    // Handle loading state
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            userRepository.logout()
            credentialManager.clearCredentials()
        }
    }

    fun refreshAuthentication() {
        viewModelScope.launch {
            try {
                repository.validateAndRefreshTokenManually()
            } catch (e: Exception) {
                _appState.value = _appState.value.copy(
                    errorMessage = "Failed to refresh authentication: ${e.message}",
                )
            }
        }
    }

    fun loadLibraryTypeData(
        library: BaseItemDto,
        libraryType: LibraryType,
        forceRefresh: Boolean = false,
    ) {
        viewModelScope.launch {
            if (!ensureValidToken()) return@launch

            val libraryId = library.id?.toString() ?: return@launch

            // ✅ DEBUG: Enhanced logging for library loading
            SecureLogger.v("MainAppViewModel-Load", "🔄 Starting loadLibraryTypeData:")
            SecureLogger.v(
                "MainAppViewModel-Load",
                "  Library: ${library.name} (${library.collectionType})",
            )
            SecureLogger.v("MainAppViewModel-Load", "  LibraryType: ${libraryType.name}")
            SecureLogger.v("MainAppViewModel-Load", "  LibraryId: $libraryId")
            SecureLogger.v("MainAppViewModel-Load", "  ItemKinds: ${libraryType.itemKinds}")
            SecureLogger.v("MainAppViewModel-Load", "  ForceRefresh: $forceRefresh")

            _appState.value = _appState.value.copy(
                isLoading = true,
                errorMessage = null,
                isLoadingMovies = if (libraryType == LibraryType.MOVIES) {
                    true
                } else {
                    _appState.value.isLoadingMovies
                },
                isLoadingTVShows = if (libraryType == LibraryType.TV_SHOWS) {
                    true
                } else {
                    _appState.value.isLoadingTVShows
                },
            )

            // Map BaseItemKind to Jellyfin API item type names
            fun mapKindsToApiNames(kinds: List<BaseItemKind>): String =
                kinds.mapNotNull { kind ->
                    when (kind) {
                        BaseItemKind.MOVIE -> "Movie"
                        BaseItemKind.SERIES -> "Series"
                        BaseItemKind.EPISODE -> "Episode"
                        BaseItemKind.AUDIO -> "Audio"
                        BaseItemKind.MUSIC_ALBUM -> "MusicAlbum"
                        BaseItemKind.MUSIC_ARTIST -> "MusicArtist"
                        BaseItemKind.BOOK -> "Book"
                        BaseItemKind.AUDIO_BOOK -> "AudioBook"
                        BaseItemKind.VIDEO -> "Video"
                        else -> null
                    }
                }.joinToString(",")

            // Derive collectionType string for validator compatibility
            val collectionTypeStr = when (library.collectionType) {
                CollectionType.MOVIES -> "movies"
                CollectionType.TVSHOWS -> "tvshows"
                CollectionType.MUSIC -> "music"
                CollectionType.HOMEVIDEOS -> "homevideos"
                CollectionType.BOOKS -> "books"
                else -> null
            }

            val itemTypesArg: String? =
                if (library.collectionType == CollectionType.HOMEVIDEOS) {
                    null
                } else {
                    mapKindsToApiNames(libraryType.itemKinds)
                }

            SecureLogger.v("MainAppViewModel-Load", "  Calling API with:")
            SecureLogger.v("MainAppViewModel-Load", "    parentId: $libraryId")
            SecureLogger.v("MainAppViewModel-Load", "    itemTypes: $itemTypesArg")
            SecureLogger.v("MainAppViewModel-Load", "    collectionType: $collectionTypeStr")

            when (
                val result = mediaRepository.getLibraryItems(
                    parentId = libraryId,
                    itemTypes = itemTypesArg,
                    collectionType = collectionTypeStr,
                )
            ) {
                is ApiResult.Success -> {
                    var items = result.data
                    if (items.isEmpty() && libraryType == LibraryType.TV_SHOWS) {
                        SecureLogger.v(
                            "MainAppViewModel-Load",
                            "TV shows empty with filter; retrying without itemTypes",
                        )
                        when (
                            val fallback = mediaRepository.getLibraryItems(
                                parentId = libraryId,
                                itemTypes = null,
                                collectionType = collectionTypeStr,
                            )
                        ) {
                            is ApiResult.Success -> items = fallback.data.filter { it.type == BaseItemKind.SERIES }
                            is ApiResult.Error -> Unit
                            is ApiResult.Loading -> Unit
                        }
                    }
                    SecureLogger.v(
                        "MainAppViewModel-Load",
                        "✅ API Success: ${result.data.size} items loaded",
                    )
                    items.take(3).forEach { item ->
                        SecureLogger.v(
                            "MainAppViewModel-Load",
                            "    Sample item: ${item.name} (${item.type})",
                        )
                    }

                    // Detect if there are more items to load
                    // If we got exactly 100 items (the API default limit), there might be more
                    val hasMore = items.size >= 100

                    val updated = _appState.value.itemsByLibrary.toMutableMap()
                    updated[libraryId] = mergeLibraryItems(emptyList(), items)

                    val updatedPagination = _appState.value.libraryPaginationState.toMutableMap()
                    updatedPagination[libraryId] = LibraryPaginationState(
                        loadedCount = items.size,
                        hasMore = hasMore,
                        isLoadingMore = false,
                    )

                    _appState.value = _appState.value.copy(
                        itemsByLibrary = updated,
                        libraryPaginationState = updatedPagination,
                        isLoading = false,
                        isLoadingMore = false,
                        hasMoreItems = hasMore,
                        isLoadingMovies = if (libraryType == LibraryType.MOVIES) {
                            false
                        } else {
                            _appState.value.isLoadingMovies
                        },
                        isLoadingTVShows = if (libraryType == LibraryType.TV_SHOWS) {
                            false
                        } else {
                            _appState.value.isLoadingTVShows
                        },
                    )
                    SecureLogger.v(
                        "MainAppViewModel-Load",
                        "✅ State updated - itemsByLibrary now has ${updated.size} libraries",
                    )
                }

                is ApiResult.Error -> {
                    SecureLogger.e("MainAppViewModel-Load", "❌ API Error: ${result.message}")
                    _appState.value = _appState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to load library items: ${result.message}",
                        isLoadingMovies = if (libraryType == LibraryType.MOVIES) {
                            false
                        } else {
                            _appState.value.isLoadingMovies
                        },
                        isLoadingTVShows = if (libraryType == LibraryType.TV_SHOWS) {
                            false
                        } else {
                            _appState.value.isLoadingTVShows
                        },
                    )
                }

                is ApiResult.Loading -> {
                    SecureLogger.v("MainAppViewModel-Load", "⏳ API Loading...")
                }
            }
        }
    }

    fun loadLibraryTypeData(libraryType: LibraryType, forceRefresh: Boolean = false) {
        val library = findLibraryForType(libraryType)
        if (library != null) {
            if (com.rpeters.jellyfin.BuildConfig.DEBUG) {
                SecureLogger.v(
                    "MainAppViewModel",
                    "loadLibraryTypeData: triggering load for ${libraryType.name} (library=${library.name})",
                )
            }
            loadLibraryTypeData(library, libraryType, forceRefresh)
        } else {
            viewModelScope.launch {
                val libraries = ensureLibrariesLoaded(forceRefresh)
                val resolvedLibrary = libraries?.let { findLibraryForType(libraryType) }
                if (resolvedLibrary != null) {
                    if (com.rpeters.jellyfin.BuildConfig.DEBUG) {
                        SecureLogger.v(
                            "MainAppViewModel",
                            "loadLibraryTypeData: resolved ${libraryType.name} after loading libraries",
                        )
                    }
                    loadLibraryTypeData(resolvedLibrary, libraryType, forceRefresh)
                } else if (com.rpeters.jellyfin.BuildConfig.DEBUG) {
                    android.util.Log.w(
                        "MainAppViewModel",
                        "loadLibraryTypeData: no matching library found for ${libraryType.name}; " +
                            "librariesLoaded=${_appState.value.libraries.size}; " +
                            "available libraries: ${_appState.value.libraries.map { "${it.name}(${it.collectionType})" }}",
                    )
                }
            }
        }
    }

    fun getLibraryTypeData(library: BaseItemDto): List<BaseItemDto> {
        val id = library.id?.toString() ?: return emptyList()
        return _appState.value.itemsByLibrary[id] ?: emptyList()
    }

    fun getLibraryTypeData(libraryType: LibraryType): List<BaseItemDto> {
        val library = findLibraryForType(libraryType) ?: return emptyList()
        return getLibraryTypeData(library)
    }

    private fun findLibraryForType(libraryType: LibraryType): BaseItemDto? {
        val libraries = _appState.value.libraries
        val targetCollection = when (libraryType) {
            LibraryType.MOVIES -> "movies"
            LibraryType.TV_SHOWS -> "tvshows"
            LibraryType.MUSIC -> "music"
            LibraryType.STUFF -> null
        }

        // Try a strict match first (handles enum equality issues when deserialized differently)
        val strictMatch = targetCollection?.let { target ->
            libraries.firstOrNull { it.normalizedCollectionType() == target }
        }
        if (strictMatch != null) return strictMatch

        // For Stuff, grab the first non-core library or fall back to the first entry
        if (libraryType == LibraryType.STUFF) {
            return libraries.firstOrNull {
                it.normalizedCollectionType() !in setOf("movies", "tvshows", "music")
            } ?: libraries.firstOrNull()
        }

        // Fallback: Some servers omit collectionType for libraries, so attempt a targeted name match
        val nameMatch = targetCollection?.let { target ->
            val targetSingular = target.removeSuffix("s")
            libraries.firstOrNull { library ->
                library.name?.replace(" ", "")?.lowercase()?.let { normalizedName ->
                    normalizedName == target ||
                        normalizedName == targetSingular ||
                        normalizedName.startsWith(target) ||
                        normalizedName.startsWith(targetSingular)
                } == true
            }?.also { matched ->
                if (com.rpeters.jellyfin.BuildConfig.DEBUG) {
                    SecureLogger.v(
                        "MainAppViewModel",
                        "findLibraryForType: using name-based fallback for ${libraryType.name}; matched=${matched.name}",
                    )
                }
            }
        }
        return nameMatch
    }

    private fun BaseItemDto.normalizedCollectionType(): String? =
        collectionType?.toString()?.lowercase()?.replace(" ", "")

    private suspend fun ensureLibrariesLoaded(forceRefresh: Boolean): List<BaseItemDto>? {
        val currentLibraries = _appState.value.libraries
        if (currentLibraries.isNotEmpty() && !forceRefresh) {
            return currentLibraries
        }

        if (_appState.value.isLoading) {
            appState.first { !it.isLoading }
            val afterWait = _appState.value.libraries
            if (afterWait.isNotEmpty() && !forceRefresh) {
                return afterWait
            }
        }

        return when (val result = mediaRepository.getUserLibraries(forceRefresh = forceRefresh)) {
            is ApiResult.Success -> {
                _appState.value = _appState.value.copy(libraries = result.data)
                result.data
            }
            is ApiResult.Error -> {
                _appState.value = _appState.value.copy(
                    errorMessage = "Failed to load libraries: ${result.message}",
                )
                null
            }
            is ApiResult.Loading -> null
        }
    }

    fun clearError() {
        _appState.value = _appState.value.copy(errorMessage = null)
    }

    // Movie and TV show operations - simplified implementations
    fun loadAllMovies(reset: Boolean = false) {
        viewModelScope.launch {
            if (!ensureValidToken()) return@launch

            val currentState = _appState.value
            if (reset) {
                _appState.value = currentState.copy(
                    allMovies = emptyList(),
                    moviesPage = 0,
                    hasMoreMovies = true,
                    isLoadingMovies = true,
                )
            } else {
                if (currentState.isLoadingMovies || !currentState.hasMoreMovies) return@launch
                _appState.value = currentState.copy(isLoadingMovies = true)
            }

            val movieLibrary = _appState.value.libraries.firstOrNull {
                it.collectionType == CollectionType.MOVIES
            }
            if (movieLibrary == null) {
                _appState.value = _appState.value.copy(
                    isLoadingMovies = false,
                    hasMoreMovies = false,
                    errorMessage = "No movie library available",
                )
                return@launch
            }

            val pageSize = 50
            val page = if (reset) 0 else currentState.moviesPage + 1
            val startIndex = page * pageSize

            when (
                val result = mediaRepository.getLibraryItems(
                    parentId = movieLibrary.id?.toString(),
                    itemTypes = "Movie",
                    startIndex = startIndex,
                    limit = pageSize,
                    collectionType = "movies",
                )
            ) {
                is ApiResult.Success -> {
                    val newMovies = result.data
                    val allMovies = if (reset) newMovies else currentState.allMovies + newMovies
                    _appState.value = _appState.value.copy(
                        allMovies = allMovies,
                        moviesPage = page,
                        hasMoreMovies = newMovies.size == pageSize,
                        isLoadingMovies = false,
                    )
                }

                is ApiResult.Error -> {
                    _appState.value = _appState.value.copy(
                        isLoadingMovies = false,
                        errorMessage = "Failed to load movies: ${result.message}",
                    )
                }

                is ApiResult.Loading -> Unit
            }
        }
    }

    fun loadMoreMovies() = loadAllMovies(reset = false)
    fun refreshMovies() = loadAllMovies(reset = true)

    fun loadAllTVShows(reset: Boolean = false) {
        viewModelScope.launch {
            if (!ensureValidToken()) return@launch

            val currentState = _appState.value
            if (reset) {
                _appState.value = currentState.copy(
                    allTVShows = emptyList(),
                    tvShowsPage = 0,
                    hasMoreTVShows = true,
                    isLoadingTVShows = true,
                )
            } else {
                if (currentState.isLoadingTVShows || !currentState.hasMoreTVShows) return@launch
                _appState.value = currentState.copy(isLoadingTVShows = true)
            }

            val tvLibrary = _appState.value.libraries.firstOrNull {
                it.collectionType == CollectionType.TVSHOWS
            }
            if (tvLibrary == null) {
                _appState.value = _appState.value.copy(
                    isLoadingTVShows = false,
                    hasMoreTVShows = false,
                    errorMessage = "No TV show library available",
                )
                return@launch
            }

            val pageSize = 50
            val page = if (reset) 0 else currentState.tvShowsPage + 1
            val startIndex = page * pageSize

            when (
                val result = mediaRepository.getLibraryItems(
                    parentId = tvLibrary.id?.toString(),
                    itemTypes = "Series",
                    startIndex = startIndex,
                    limit = pageSize,
                    collectionType = "tvshows",
                )
            ) {
                is ApiResult.Success -> {
                    val newTVShows = result.data
                    val allTVShows = if (reset) newTVShows else currentState.allTVShows + newTVShows
                    _appState.value = _appState.value.copy(
                        allTVShows = allTVShows,
                        tvShowsPage = page,
                        hasMoreTVShows = newTVShows.size == pageSize,
                        isLoadingTVShows = false,
                    )
                }

                is ApiResult.Error -> {
                    _appState.value = _appState.value.copy(
                        isLoadingTVShows = false,
                        errorMessage = "Failed to load TV shows: ${result.message}",
                    )
                }

                is ApiResult.Loading -> Unit
            }
        }
    }

    fun loadMoreTVShows() = loadAllTVShows(reset = false)
    fun refreshTVShows() = loadAllTVShows(reset = true)

    // Media details (still using repository directly for complex operations)
    fun getMovieDetails(movieId: String) {
        viewModelScope.launch {
            when (val result = repository.getMovieDetails(movieId)) {
                is ApiResult.Success -> {
                    // Could update combined state if needed
                }

                is ApiResult.Error -> {
                    _appState.value = _appState.value.copy(
                        errorMessage = "Failed to load movie details: ${result.message}",
                    )
                }

                is ApiResult.Loading -> {
                    _appState.value = _appState.value.copy(isLoading = true)
                }
            }
        }
    }

    // Streaming operations (direct repository calls)
    fun getImageUrl(item: BaseItemDto): String? =
        streamRepository.getImageUrl(item.id.toString(), "Primary", null)

    fun getBackdropUrl(item: BaseItemDto): String? =
        streamRepository.getBackdropUrl(item)

    fun getSeriesImageUrl(item: BaseItemDto): String? =
        streamRepository.getSeriesImageUrl(item)

    fun getStreamUrl(item: BaseItemDto): String? =
        streamRepository.getStreamUrl(item.id.toString())

    fun getDownloadUrl(item: BaseItemDto): String? =
        streamRepository.getDownloadUrl(item.id.toString())

    fun getDirectStreamUrl(item: BaseItemDto, container: String? = null): String? =
        streamRepository.getDirectStreamUrl(item.id.toString(), container)

    @UnstableApi
    fun sendCastPreview(item: BaseItemDto) {
        viewModelScope.launch {
            withContext(dispatchers.io) {
                castManager.initialize()
            }
            val image = getImageUrl(item)
            val backdrop = getBackdropUrl(item)
            castManager.loadPreview(item, imageUrl = image, backdropUrl = backdrop)
        }
    }

    fun clearState() {
        _appState.value = MainAppState()
    }

    // Legacy compatibility methods (simplified implementations)
    fun loadLibraryItemsPage(reset: Boolean = false) {
        // Simplified - just delegate to library loading
        if (reset) {
            clearState()
        }
        loadInitialData(reset)
    }

    fun loadMoreItems() = loadInitialData()
    fun refreshLibraryItems() = loadInitialData(true)

    /**
     * Load more items for a specific library (pagination)
     */
    fun loadMoreLibraryItems(libraryId: String) {
        viewModelScope.launch {
            val paginationState = _appState.value.libraryPaginationState[libraryId]
            if (paginationState?.isLoadingMore == true || paginationState?.hasMore == false) {
                SecureLogger.v("MainAppViewModel", "Skipping loadMore: isLoadingMore=${paginationState?.isLoadingMore}, hasMore=${paginationState?.hasMore}")
                return@launch
            }

            if (!ensureValidToken()) return@launch

            val currentItems = _appState.value.itemsByLibrary[libraryId] ?: emptyList()
            val startIndex = currentItems.size

            SecureLogger.v("MainAppViewModel", "Loading more items for library $libraryId, startIndex=$startIndex")

            // Mark as loading more
            val updatedPagination = _appState.value.libraryPaginationState.toMutableMap()
            updatedPagination[libraryId] = paginationState?.copy(isLoadingMore = true) ?: LibraryPaginationState(isLoadingMore = true)
            _appState.value = _appState.value.copy(
                libraryPaginationState = updatedPagination,
                isLoadingMore = true,
            )

            // Load next batch
            when (
                val result = mediaRepository.getLibraryItems(
                    parentId = libraryId,
                    startIndex = startIndex,
                    limit = 100,
                )
            ) {
                is ApiResult.Success -> {
                    val newItems = result.data
                    val hasMore = newItems.size >= 100

                    SecureLogger.v("MainAppViewModel", "Loaded ${newItems.size} more items, hasMore=$hasMore")

                    val updated = _appState.value.itemsByLibrary.toMutableMap()
                    updated[libraryId] = mergeLibraryItems(currentItems, newItems)

                    val finalPagination = _appState.value.libraryPaginationState.toMutableMap()
                    finalPagination[libraryId] = LibraryPaginationState(
                        loadedCount = currentItems.size + newItems.size,
                        hasMore = hasMore,
                        isLoadingMore = false,
                    )

                    _appState.value = _appState.value.copy(
                        itemsByLibrary = updated,
                        libraryPaginationState = finalPagination,
                        isLoadingMore = false,
                        hasMoreItems = hasMore,
                    )
                }
                is ApiResult.Error -> {
                    SecureLogger.e("MainAppViewModel", "Failed to load more items: ${result.message}")
                    val finalPagination = _appState.value.libraryPaginationState.toMutableMap()
                    finalPagination[libraryId] = paginationState?.copy(isLoadingMore = false) ?: LibraryPaginationState(isLoadingMore = false)
                    _appState.value = _appState.value.copy(
                        libraryPaginationState = finalPagination,
                        isLoadingMore = false,
                        errorMessage = "Failed to load more items: ${result.message}",
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    // Navigation compatibility methods
    fun loadTVShowDetails(seriesId: String) {
        viewModelScope.launch {
            when (val result = repository.getSeriesDetails(seriesId)) {
                is ApiResult.Success -> {
                    // Add to state if needed
                }

                is ApiResult.Error -> {
                    _appState.value = _appState.value.copy(
                        errorMessage = "Failed to load series details: ${result.message}",
                    )
                }

                is ApiResult.Loading -> {
                    // Handle loading state
                }
            }
        }
    }

    fun loadEpisodeDetails(episodeId: String) {
        viewModelScope.launch {
            // Set loading state
            _appState.value = _appState.value.copy(isLoading = true, errorMessage = null)

            when (val result = repository.getEpisodeDetails(episodeId)) {
                is ApiResult.Success -> {
                    val episode = result.data
                    // Add episode to allItems so it can be found by the detail screen
                    val updatedAllItems = _appState.value.allItems.toMutableList()
                    updatedAllItems.removeAll { it.id?.toString() == episodeId }
                    updatedAllItems.add(episode)

                    _appState.value = _appState.value.copy(
                        allItems = updatedAllItems,
                        isLoading = false,
                        errorMessage = null,
                    )

                    SecureLogger.v(
                        "MainAppViewModel",
                        "✅ Episode loaded: ${episode.name} (${episode.id})",
                    )
                }

                is ApiResult.Error -> {
                    _appState.value = _appState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to load episode details: ${result.message}",
                    )
                    SecureLogger.e(
                        "MainAppViewModel",
                        "❌ Failed to load episode $episodeId: ${result.message}",
                    )
                }

                is ApiResult.Loading -> {
                    _appState.value = _appState.value.copy(isLoading = true)
                }
            }
        }
    }

    // ✅ IMPROVED: Enhanced addOrUpdateItem method
    fun addOrUpdateItem(item: BaseItemDto) {
        val updatedAllItems = _appState.value.allItems.toMutableList()
        updatedAllItems.removeAll { it.id == item.id }
        updatedAllItems.add(item)
        _appState.value = _appState.value.copy(allItems = updatedAllItems)
        SecureLogger.v("MainAppViewModel", "📝 Item added/updated: ${item.name} (${item.id})")
    }

    fun loadHomeVideos(libraryId: String) {
        val library = BaseItemDto(id = UUID.fromString(libraryId), type = BaseItemKind.VIDEO)
        loadLibraryTypeData(library, LibraryType.STUFF)
    }

    fun loadMoreHomeVideos(homeVideoLibraries: List<BaseItemDto>) {
        val libraryToLoad = homeVideoLibraries.firstOrNull { library ->
            val libraryId = library.id?.toString()
            if (libraryId == null) {
                SecureLogger.w(
                    "MainAppViewModel",
                    "Skipping home video library with null ID: ${library.name ?: context.getString(R.string.unknown)}",
                )
                return@firstOrNull false
            }

            val paginationState = _appState.value.libraryPaginationState[libraryId]
            val hasMore = paginationState?.hasMore ?: false
            val isLoadingMore = paginationState?.isLoadingMore == true

            hasMore && !isLoadingMore
        }

        libraryToLoad?.id?.toString()?.let { libraryId ->
            loadMoreLibraryItems(libraryId)
        }
    }

    fun loadMusic() = loadLibraryTypeData(LibraryType.MUSIC)
    fun loadStuff() = loadLibraryTypeData(LibraryType.STUFF)

    // Helper methods
    fun clearLoadedLibraryTypes() {
        // Simplified - just clear state
    }

    override fun onCleared() {
        super.onCleared()
        clearState()
    }
}
