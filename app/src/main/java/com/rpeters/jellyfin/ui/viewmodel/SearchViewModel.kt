package com.rpeters.jellyfin.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.data.repository.JellyfinSearchRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import javax.inject.Inject

/**
 * ViewModel responsible for search functionality.
 * Handles search queries, results, and filtering by content type.
 */
data class SearchState(
    val searchQuery: String = "",
    val searchResults: List<BaseItemDto> = emptyList(),
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    val selectedContentTypes: Set<BaseItemKind> = setOf(
        BaseItemKind.MOVIE,
        BaseItemKind.SERIES,
        BaseItemKind.AUDIO,
        BaseItemKind.BOOK,
    ),
    val hasSearched: Boolean = false,
)

@OptInAppExperimentalApis
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: JellyfinSearchRepository,
) : ViewModel() {

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private var searchJob: Job? = null

    companion object {
        private const val TAG = "SearchViewModel"
        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val SEARCH_LIMIT = 50
    }

    init {
        // Set up automatic search when query changes
        setupAutoSearch()
    }

    /**
     * Set up automatic search with debouncing to avoid excessive API calls.
     */
    @OptIn(FlowPreview::class)
    private fun setupAutoSearch() {
        _searchState
            .distinctUntilChanged { old, new -> old.searchQuery == new.searchQuery }
            .debounce(SEARCH_DEBOUNCE_MS)
            .onEach { state ->
                if (state.searchQuery.isNotBlank()) {
                    performSearch(state.searchQuery)
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Update the search query.
     */
    fun updateSearchQuery(query: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Search query updated: $query")
        }

        _searchState.value = _searchState.value.copy(
            searchQuery = query,
            errorMessage = null,
        )

        // Clear results if query is empty
        if (query.isBlank()) {
            _searchState.value = _searchState.value.copy(
                searchResults = emptyList(),
                isSearching = false,
                hasSearched = false,
            )
        }
    }

    /**
     * Perform search with the current query and selected content types.
     */
    fun performSearch(query: String = _searchState.value.searchQuery) {
        // Cancel any ongoing search
        searchJob?.cancel()

        if (query.isBlank()) {
            clearSearch()
            return
        }

        searchJob = viewModelScope.launch {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Performing search for: $query")
            }

            _searchState.value = _searchState.value.copy(
                isSearching = true,
                errorMessage = null,
                hasSearched = true,
            )

            try {
                val contentTypes = _searchState.value.selectedContentTypes.toList()

                when (val result = searchRepository.searchItems(query, contentTypes, SEARCH_LIMIT)) {
                    is ApiResult.Success -> {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Search completed: ${result.data.size} results")
                        }

                        _searchState.value = _searchState.value.copy(
                            searchResults = result.data,
                            isSearching = false,
                        )
                    }
                    is ApiResult.Error -> {
                        if (BuildConfig.DEBUG) {
                            Log.w(TAG, "Search failed: ${result.message}")
                        }

                        _searchState.value = _searchState.value.copy(
                            searchResults = emptyList(),
                            isSearching = false,
                            errorMessage = result.message,
                        )
                    }
                    is ApiResult.Loading -> {
                        // Already handled by isSearching state
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Search error", e)
                }

                _searchState.value = _searchState.value.copy(
                    searchResults = emptyList(),
                    isSearching = false,
                    errorMessage = "Search failed: ${e.message}",
                )
            }
        }
    }

    /**
     * Clear search results and query.
     */
    fun clearSearch() {
        searchJob?.cancel()

        _searchState.value = _searchState.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false,
            errorMessage = null,
            hasSearched = false,
        )

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Search cleared")
        }
    }

    /**
     * Toggle a content type filter.
     */
    fun toggleContentType(contentType: BaseItemKind) {
        val currentTypes = _searchState.value.selectedContentTypes.toMutableSet()

        if (currentTypes.contains(contentType)) {
            currentTypes.remove(contentType)
        } else {
            currentTypes.add(contentType)
        }

        _searchState.value = _searchState.value.copy(selectedContentTypes = currentTypes)

        // Re-search if we have a query
        if (_searchState.value.searchQuery.isNotBlank()) {
            performSearch()
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Content type filter updated: ${currentTypes.map { it.name }}")
        }
    }

    /**
     * Search specifically for movies.
     */
    fun searchMovies(query: String) {
        viewModelScope.launch {
            _searchState.value = _searchState.value.copy(isSearching = true)

            when (val result = searchRepository.searchMovies(query)) {
                is ApiResult.Success -> {
                    _searchState.value = _searchState.value.copy(
                        searchResults = result.data,
                        isSearching = false,
                        hasSearched = true,
                    )
                }
                is ApiResult.Error -> {
                    _searchState.value = _searchState.value.copy(
                        isSearching = false,
                        errorMessage = result.message,
                    )
                }
                is ApiResult.Loading -> {
                    // Handled by isSearching state
                }
            }
        }
    }

    /**
     * Search specifically for TV shows.
     */
    fun searchTVShows(query: String) {
        viewModelScope.launch {
            _searchState.value = _searchState.value.copy(isSearching = true)

            when (val result = searchRepository.searchTVShows(query)) {
                is ApiResult.Success -> {
                    _searchState.value = _searchState.value.copy(
                        searchResults = result.data,
                        isSearching = false,
                        hasSearched = true,
                    )
                }
                is ApiResult.Error -> {
                    _searchState.value = _searchState.value.copy(
                        isSearching = false,
                        errorMessage = result.message,
                    )
                }
                is ApiResult.Loading -> {
                    // Handled by isSearching state
                }
            }
        }
    }

    /**
     * Clear any error messages.
     */
    fun clearError() {
        _searchState.value = _searchState.value.copy(errorMessage = null)
    }

    /**
     * Clean up resources when ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "SearchViewModel cleared")
        }
    }
}
