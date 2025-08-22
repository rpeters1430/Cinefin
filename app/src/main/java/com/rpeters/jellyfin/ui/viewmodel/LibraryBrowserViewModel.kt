package com.rpeters.jellyfin.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.paging.LibraryItemPagingSource
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import javax.inject.Inject

/**
 * ViewModel for browsing large libraries with pagination.
 * Uses Paging 3 to efficiently load content on-demand.
 */
data class LibraryBrowserState(
    val currentLibraryId: String? = null,
    val currentLibraryName: String = "",
    val selectedContentTypes: Set<BaseItemKind> = emptySet(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class LibraryBrowserViewModel @Inject constructor(
    private val mediaRepository: JellyfinMediaRepository,
) : ViewModel() {

    private val _browserState = MutableStateFlow(LibraryBrowserState())
    val browserState: StateFlow<LibraryBrowserState> = _browserState.asStateFlow()

    // Note: We don't need to store the pager reference anymore

    companion object {
        private const val TAG = "LibraryBrowserViewModel"
        private const val PAGE_SIZE = 20
        private const val PREFETCH_DISTANCE = 5
        private const val MAX_SIZE = 200 // Maximum items to keep in memory
    }

    /**
     * Get paginated flow of library items for the current library and filters.
     */
    fun getLibraryItemsPagingFlow(): Flow<PagingData<BaseItemDto>>? {
        val state = _browserState.value

        if (state.currentLibraryId == null) {
            return null
        }

        // Create new pager with current filters
        val pagingFlow = Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                maxSize = MAX_SIZE,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                LibraryItemPagingSource(
                    mediaRepository = mediaRepository,
                    parentId = state.currentLibraryId,
                    itemTypes = state.selectedContentTypes.takeIf { it.isNotEmpty() }?.toList(),
                    pageSize = PAGE_SIZE,
                )
            },
        ).flow.cachedIn(viewModelScope)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Created paging flow for library: ${state.currentLibraryName}")
        }

        return pagingFlow
    }

    /**
     * Set the current library to browse.
     */
    fun setCurrentLibrary(libraryId: String, libraryName: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Setting current library: $libraryName ($libraryId)")
        }

        _browserState.value = _browserState.value.copy(
            currentLibraryId = libraryId,
            currentLibraryName = libraryName,
            errorMessage = null,
        )
    }

    /**
     * Set content type filters.
     */
    fun setContentTypeFilters(contentTypes: Set<BaseItemKind>) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Setting content type filters: ${contentTypes.map { it.name }}")
        }

        _browserState.value = _browserState.value.copy(
            selectedContentTypes = contentTypes,
            errorMessage = null,
        )
    }

    /**
     * Add a content type to the current filters.
     */
    fun addContentTypeFilter(contentType: BaseItemKind) {
        val currentTypes = _browserState.value.selectedContentTypes.toMutableSet()
        currentTypes.add(contentType)
        setContentTypeFilters(currentTypes)
    }

    /**
     * Remove a content type from the current filters.
     */
    fun removeContentTypeFilter(contentType: BaseItemKind) {
        val currentTypes = _browserState.value.selectedContentTypes.toMutableSet()
        currentTypes.remove(contentType)
        setContentTypeFilters(currentTypes)
    }

    /**
     * Toggle a content type filter.
     */
    fun toggleContentTypeFilter(contentType: BaseItemKind) {
        val currentTypes = _browserState.value.selectedContentTypes
        if (currentTypes.contains(contentType)) {
            removeContentTypeFilter(contentType)
        } else {
            addContentTypeFilter(contentType)
        }
    }

    /**
     * Clear all content type filters.
     */
    fun clearContentTypeFilters() {
        setContentTypeFilters(emptySet())
    }

    /**
     * Set loading state.
     */
    fun setLoading(isLoading: Boolean) {
        _browserState.value = _browserState.value.copy(isLoading = isLoading)
    }

    /**
     * Set error message.
     */
    fun setError(message: String?) {
        _browserState.value = _browserState.value.copy(errorMessage = message)
    }

    /**
     * Clear any error messages.
     */
    fun clearError() {
        _browserState.value = _browserState.value.copy(errorMessage = null)
    }

    /**
     * Get available content type filters for the current library.
     */
    fun getAvailableContentTypes(): List<BaseItemKind> {
        return listOf(
            BaseItemKind.MOVIE,
            BaseItemKind.SERIES,
            BaseItemKind.EPISODE,
            BaseItemKind.AUDIO,
            BaseItemKind.MUSIC_ALBUM,
            BaseItemKind.MUSIC_ARTIST,
            BaseItemKind.BOOK,
            BaseItemKind.AUDIO_BOOK,
            BaseItemKind.VIDEO,
        )
    }

    /**
     * Check if a content type is currently selected.
     */
    fun isContentTypeSelected(contentType: BaseItemKind): Boolean {
        return _browserState.value.selectedContentTypes.contains(contentType)
    }

    /**
     * Get display name for content type.
     */
    fun getContentTypeDisplayName(contentType: BaseItemKind): String {
        return when (contentType) {
            BaseItemKind.MOVIE -> "Movies"
            BaseItemKind.SERIES -> "TV Shows"
            BaseItemKind.EPISODE -> "Episodes"
            BaseItemKind.AUDIO -> "Music"
            BaseItemKind.MUSIC_ALBUM -> "Albums"
            BaseItemKind.MUSIC_ARTIST -> "Artists"
            BaseItemKind.BOOK -> "Books"
            BaseItemKind.AUDIO_BOOK -> "Audiobooks"
            BaseItemKind.VIDEO -> "Videos"
            else -> contentType.name.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Reset the browser state.
     */
    fun reset() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Resetting library browser state")
        }

        _browserState.value = LibraryBrowserState()
    }

    /**
     * Clean up resources when ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "LibraryBrowserViewModel cleared")
        }
    }
}
