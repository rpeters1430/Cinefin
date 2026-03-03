package com.rpeters.jellyfin.data.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * PagingSource for library items that provides paginated loading of media content.
 * This helps prevent memory issues when loading large libraries.
 */
class LibraryItemPagingSource(
    private val mediaRepository: JellyfinMediaRepository,
    private val parentId: String? = null,
    private val itemTypes: List<BaseItemKind>? = null,
    private val pageSize: Int = 20,
) : PagingSource<Int, BaseItemDto>() {

    companion object {
        private const val TAG = "LibraryItemPagingSource"
        private const val STARTING_START_INDEX = 0
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, BaseItemDto> {
        return try {
            val startIndex = params.key ?: STARTING_START_INDEX

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Loading startIndex=$startIndex, loadSize=${params.loadSize}")
            }

            val itemTypesString = itemTypes?.joinToString(",") { type ->
                when (type) {
                    BaseItemKind.MOVIE -> "Movie"
                    BaseItemKind.SERIES -> "Series"
                    BaseItemKind.EPISODE -> "Episode"
                    BaseItemKind.AUDIO -> "Audio"
                    BaseItemKind.MUSIC_ALBUM -> "MusicAlbum"
                    BaseItemKind.MUSIC_ARTIST -> "MusicArtist"
                    BaseItemKind.BOOK -> "Book"
                    BaseItemKind.AUDIO_BOOK -> "AudioBook"
                    BaseItemKind.VIDEO -> "Video"
                    else -> type.name
                }
            }

            when (
                val result = mediaRepository.getLibraryItems(
                    parentId = parentId,
                    itemTypes = itemTypesString,
                    startIndex = startIndex,
                    limit = params.loadSize,
                )
            ) {
                is ApiResult.Success -> {
                    val items = result.data

                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Loaded ${items.size} items for startIndex=$startIndex")
                    }

                    val previousKey = if (startIndex == STARTING_START_INDEX) {
                        null
                    } else {
                        maxOf(STARTING_START_INDEX, startIndex - params.loadSize)
                    }

                    val nextKey = if (items.isEmpty() || items.size < params.loadSize) {
                        null
                    } else {
                        startIndex + items.size
                    }

                    LoadResult.Page(
                        data = items,
                        prevKey = previousKey,
                        nextKey = nextKey,
                    )
                }
                is ApiResult.Error -> {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Failed to load startIndex=$startIndex: ${result.message}")
                    }
                    LoadResult.Error(Exception(result.message))
                }
                is ApiResult.Loading -> {
                    // This shouldn't happen in our paging context
                    LoadResult.Error(Exception("Unexpected loading state"))
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        }
    }

    override fun getRefreshKey(state: PagingState<Int, BaseItemDto>): Int? {
        // Try to find the page key of the closest page to anchorPosition, from
        // either the prevKey or the nextKey, but you need to handle nullability
        // here:
        //  * prevKey == null -> anchorPage is the first page.
        //  * nextKey == null -> anchorPage is the last page.
        //  * both prevKey and nextKey null -> anchorPage is the initial page, so
        //    just return null.
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(anchorPage.data.size)
                ?: anchorPage?.nextKey?.minus(pageSize)
        }
    }
}
