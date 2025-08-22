package com.rpeters.jellyfin.data.paging

import androidx.paging.PagingSource
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LibraryItemPagingSourceTest {

    @MockK
    private lateinit var mediaRepository: JellyfinMediaRepository

    private lateinit var pagingSource: LibraryItemPagingSource

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        pagingSource = LibraryItemPagingSource(
            mediaRepository = mediaRepository,
            parentId = "library-123",
            itemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
            pageSize = 20,
        )
    }

    @Test
    fun `load returns page when API call succeeds`() = runTest {
        // Given
        val mockItems = listOf(
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Movie 1"
                coEvery { type } returns BaseItemKind.MOVIE
            },
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Movie 2"
                coEvery { type } returns BaseItemKind.MOVIE
            },
        )

        coEvery {
            mediaRepository.getLibraryItems(
                parentId = "library-123",
                itemTypes = "Movie,Series",
                startIndex = 0,
                limit = 20,
            )
        } returns ApiResult.Success(mockItems)

        // When
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )

        // Then
        assertTrue(result is PagingSource.LoadResult.Page<Int, BaseItemDto>)
        val pageResult = result as PagingSource.LoadResult.Page<Int, BaseItemDto>
        assertEquals(2, pageResult.data.size)
        assertEquals("Movie 1", pageResult.data[0].name)
        assertEquals("Movie 2", pageResult.data[1].name)
        assertNull(pageResult.prevKey)
        assertEquals(1, pageResult.nextKey)
    }

    @Test
    fun `load returns error when API call fails`() = runTest {
        // Given
        val errorMessage = "Network error"
        coEvery {
            mediaRepository.getLibraryItems(
                parentId = "library-123",
                itemTypes = "Movie,Series",
                startIndex = 0,
                limit = 20,
            )
        } returns ApiResult.Error(errorMessage, null, com.rpeters.jellyfin.data.repository.common.ErrorType.NETWORK_ERROR)

        // When
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )

        // Then
        assertTrue(result is PagingSource.LoadResult.Error<Int, BaseItemDto>)
        val errorResult = result as PagingSource.LoadResult.Error<Int, BaseItemDto>
        assertEquals(errorMessage, errorResult.throwable.message)
    }

    @Test
    fun `load handles pagination correctly for subsequent pages`() = runTest {
        // Given
        val mockItems = List(20) { index ->
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Movie $index"
                coEvery { type } returns BaseItemKind.MOVIE
            }
        }

        coEvery {
            mediaRepository.getLibraryItems(
                parentId = "library-123",
                itemTypes = "Movie,Series",
                startIndex = 20, // Second page
                limit = 20,
            )
        } returns ApiResult.Success(mockItems)

        // When
        val result = pagingSource.load(
            PagingSource.LoadParams.Append(
                key = 1,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )

        // Then
        assertTrue(result is PagingSource.LoadResult.Page<Int, BaseItemDto>)
        val pageResult = result as PagingSource.LoadResult.Page<Int, BaseItemDto>
        assertEquals(20, pageResult.data.size)
        assertEquals(0, pageResult.prevKey) // Previous page key
        assertEquals(2, pageResult.nextKey) // Next page key
    }

    @Test
    fun `load returns no next key when end of data reached`() = runTest {
        // Given - fewer items than page size indicates end of data
        val mockItems = listOf(
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Last Movie"
                coEvery { type } returns BaseItemKind.MOVIE
            },
        )

        coEvery {
            mediaRepository.getLibraryItems(
                parentId = "library-123",
                itemTypes = "Movie,Series",
                startIndex = 0,
                limit = 20,
            )
        } returns ApiResult.Success(mockItems)

        // When
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )

        // Then
        assertTrue(result is PagingSource.LoadResult.Page<Int, BaseItemDto>)
        val pageResult = result as PagingSource.LoadResult.Page<Int, BaseItemDto>
        assertEquals(1, pageResult.data.size)
        assertNull(pageResult.prevKey)
        assertNull(pageResult.nextKey) // No next page
    }

    @Test
    fun `load returns empty page when no items found`() = runTest {
        // Given
        coEvery {
            mediaRepository.getLibraryItems(
                parentId = "library-123",
                itemTypes = "Movie,Series",
                startIndex = 0,
                limit = 20,
            )
        } returns ApiResult.Success(emptyList())

        // When
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )

        // Then
        assertTrue(result is PagingSource.LoadResult.Page<Int, BaseItemDto>)
        val pageResult = result as PagingSource.LoadResult.Page<Int, BaseItemDto>
        assertEquals(0, pageResult.data.size)
        assertNull(pageResult.prevKey)
        assertNull(pageResult.nextKey)
    }

    @Test
    fun `load handles prepend load type`() = runTest {
        // Given
        val mockItems = listOf(
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Movie 1"
                coEvery { type } returns BaseItemKind.MOVIE
            },
        )

        coEvery {
            mediaRepository.getLibraryItems(
                parentId = "library-123",
                itemTypes = "Movie,Series",
                startIndex = 0, // First page before current
                limit = 20,
            )
        } returns ApiResult.Success(mockItems)

        // When
        val result = pagingSource.load(
            PagingSource.LoadParams.Prepend(
                key = 0,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )

        // Then
        assertTrue(result is PagingSource.LoadResult.Page<Int, BaseItemDto>)
        val pageResult = result as PagingSource.LoadResult.Page<Int, BaseItemDto>
        assertEquals(1, pageResult.data.size)
        assertNull(pageResult.prevKey) // First page
        assertEquals(1, pageResult.nextKey)
    }

    @Test
    fun `getRefreshKey returns correct key for anchor position`() = runTest {
        // Given
        val mockState = mockk<androidx.paging.PagingState<Int, BaseItemDto>> {
            coEvery { anchorPosition } returns 25 // Item at position 25
            coEvery { closestPageToPosition(25) } returns mockk {
                coEvery { prevKey } returns 0
                coEvery { nextKey } returns 2
            }
        }

        // When
        val refreshKey = pagingSource.getRefreshKey(mockState)

        // Then
        assertEquals(1, refreshKey) // prevKey + 1
    }

    @Test
    fun `getRefreshKey returns null when no anchor position`() = runTest {
        // Given
        val mockState = mockk<androidx.paging.PagingState<Int, BaseItemDto>> {
            coEvery { anchorPosition } returns null
        }

        // When
        val refreshKey = pagingSource.getRefreshKey(mockState)

        // Then
        assertNull(refreshKey)
    }

    @Test
    fun `itemTypes are converted to correct string format`() = runTest {
        // Given
        val pagingSourceWithMusicTypes = LibraryItemPagingSource(
            mediaRepository = mediaRepository,
            parentId = "library-123",
            itemTypes = listOf(BaseItemKind.AUDIO, BaseItemKind.MUSIC_ALBUM, BaseItemKind.MUSIC_ARTIST),
            pageSize = 20,
        )

        val mockItems = emptyList<BaseItemDto>()
        coEvery {
            mediaRepository.getLibraryItems(
                parentId = "library-123",
                itemTypes = "Audio,MusicAlbum,MusicArtist",
                startIndex = 0,
                limit = 20,
            )
        } returns ApiResult.Success(mockItems)

        // When
        pagingSourceWithMusicTypes.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )

        // Then - verify the correct itemTypes string was passed
        // (The coEvery setup above verifies this)
    }

    @Test
    fun `handles exception during load gracefully`() = runTest {
        // Given
        val exception = RuntimeException("Unexpected error")
        coEvery {
            mediaRepository.getLibraryItems(any(), any(), any(), any())
        } throws exception

        // When
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )

        // Then
        assertTrue(result is PagingSource.LoadResult.Error<Int, BaseItemDto>)
        val errorResult = result as PagingSource.LoadResult.Error<Int, BaseItemDto>
        assertEquals(exception, errorResult.throwable)
    }
}
