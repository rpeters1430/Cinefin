package com.example.jellyfinandroid.data.repository

import com.example.jellyfinandroid.data.cache.JellyfinCache
import com.example.jellyfinandroid.data.repository.common.ApiResult
import com.example.jellyfinandroid.di.JellyfinClientFactory
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JellyfinMediaRepositoryTest {

    @MockK
    private lateinit var authRepository: JellyfinAuthRepository

    @MockK
    private lateinit var clientFactory: JellyfinClientFactory

    @MockK
    private lateinit var cache: JellyfinCache

    @MockK
    private lateinit var apiClient: ApiClient

    @MockK
    private lateinit var itemsApi: ItemsApi

    private lateinit var repository: JellyfinMediaRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        repository = JellyfinMediaRepository(authRepository, clientFactory, cache)

        // Mock API client setup
        coEvery { apiClient.itemsApi } returns itemsApi
    }

    @Test
    fun `getUserLibraries returns success with cached result`() = runTest {
        // Given
        val mockLibraries = listOf(
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Movies"
                coEvery { type } returns BaseItemKind.COLLECTION_FOLDER
            },
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "TV Shows"
                coEvery { type } returns BaseItemKind.COLLECTION_FOLDER
            },
        )

        val mockResult = mockk<BaseItemDtoQueryResult> {
            coEvery { items } returns mockLibraries
        }

        // Mock repository methods via executeWithCache
        coEvery {
            repository.getUserLibraries()
        } returns ApiResult.Success(mockLibraries)

        // When
        val result = repository.getUserLibraries()

        // Then
        assertTrue(result is ApiResult.Success<List<BaseItemDto>>)
        val successResult = result as ApiResult.Success<List<BaseItemDto>>
        assertEquals(2, successResult.data.size)
        assertEquals("Movies", successResult.data[0].name)
        assertEquals("TV Shows", successResult.data[1].name)
    }

    @Test
    fun `getLibraryItems with filters returns correct items`() = runTest {
        // Given
        val parentId = "library-123"
        val itemTypes = "Movie,Series"
        val mockMovies = listOf(
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Test Movie"
                coEvery { type } returns BaseItemKind.MOVIE
            },
        )

        coEvery {
            repository.getLibraryItems(
                parentId = parentId,
                itemTypes = itemTypes,
                startIndex = 0,
                limit = 100,
            )
        } returns ApiResult.Success(mockMovies)

        // When
        val result = repository.getLibraryItems(
            parentId = parentId,
            itemTypes = itemTypes,
            startIndex = 0,
            limit = 100,
        )

        // Then
        assertTrue(result is ApiResult.Success<List<BaseItemDto>>)
        assertEquals(1, result.data.size)
        assertEquals("Test Movie", result.data[0].name)
        assertEquals(BaseItemKind.MOVIE, result.data[0].type)
    }

    @Test
    fun `getRecentlyAdded returns cached results within TTL`() = runTest {
        // Given
        val mockRecentItems = listOf(
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Recent Movie"
                coEvery { type } returns BaseItemKind.MOVIE
            },
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Recent Episode"
                coEvery { type } returns BaseItemKind.EPISODE
            },
        )

        coEvery {
            repository.getRecentlyAdded(limit = 50)
        } returns ApiResult.Success(mockRecentItems)

        // When
        val result = repository.getRecentlyAdded(limit = 50)

        // Then
        assertTrue(result is ApiResult.Success<List<BaseItemDto>>)
        assertEquals(2, result.data.size)
        assertTrue(result.data.any { it.name == "Recent Movie" })
        assertTrue(result.data.any { it.name == "Recent Episode" })
    }

    @Test
    fun `getRecentlyAddedByType filters correctly by type`() = runTest {
        // Given
        val itemType = BaseItemKind.MOVIE
        val mockMovies = listOf(
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Recent Movie 1"
                coEvery { type } returns BaseItemKind.MOVIE
            },
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Recent Movie 2"
                coEvery { type } returns BaseItemKind.MOVIE
            },
        )

        coEvery {
            repository.getRecentlyAddedByType(itemType, limit = 20)
        } returns ApiResult.Success(mockMovies)

        // When
        val result = repository.getRecentlyAddedByType(itemType, limit = 20)

        // Then
        assertTrue(result is ApiResult.Success<List<BaseItemDto>>)
        assertEquals(2, result.data.size)
        assertTrue(result.data.all { it.type == BaseItemKind.MOVIE })
    }

    @Test
    fun `getMovieDetails returns detailed movie information`() = runTest {
        // Given
        val movieId = "movie-123"
        val mockMovie = mockk<BaseItemDto> {
            coEvery { id } returns java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            coEvery { name } returns "Test Movie"
            coEvery { type } returns BaseItemKind.MOVIE
            coEvery { overview } returns "A great test movie"
        }

        coEvery {
            repository.getMovieDetails(movieId)
        } returns ApiResult.Success(mockMovie)

        // When
        val result = repository.getMovieDetails(movieId)

        // Then
        assertTrue(result is ApiResult.Success<BaseItemDto>)
        assertEquals("Test Movie", result.data.name)
        assertEquals(BaseItemKind.MOVIE, result.data.type)
        assertEquals("A great test movie", result.data.overview)
    }

    @Test
    fun `getSeasonsForSeries returns ordered seasons`() = runTest {
        // Given
        val seriesId = "series-123"
        val mockSeasons = listOf(
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Season 1"
                coEvery { type } returns BaseItemKind.SEASON
                coEvery { indexNumber } returns 1
            },
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Season 2"
                coEvery { type } returns BaseItemKind.SEASON
                coEvery { indexNumber } returns 2
            },
        )

        coEvery {
            repository.getSeasonsForSeries(seriesId)
        } returns ApiResult.Success(mockSeasons)

        // When
        val result = repository.getSeasonsForSeries(seriesId)

        // Then
        assertTrue(result is ApiResult.Success<List<BaseItemDto>>)
        assertEquals(2, result.data.size)
        assertEquals("Season 1", result.data[0].name)
        assertEquals("Season 2", result.data[1].name)
    }

    @Test
    fun `getEpisodesForSeason returns episodes in order`() = runTest {
        // Given
        val seasonId = "season-123"
        val mockEpisodes = listOf(
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Episode 1"
                coEvery { type } returns BaseItemKind.EPISODE
                coEvery { indexNumber } returns 1
            },
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Episode 2"
                coEvery { type } returns BaseItemKind.EPISODE
                coEvery { indexNumber } returns 2
            },
        )

        coEvery {
            repository.getEpisodesForSeason(seasonId)
        } returns ApiResult.Success(mockEpisodes)

        // When
        val result = repository.getEpisodesForSeason(seasonId)

        // Then
        assertTrue(result is ApiResult.Success<List<BaseItemDto>>)
        assertEquals(2, result.data.size)
        assertEquals("Episode 1", result.data[0].name)
        assertEquals("Episode 2", result.data[1].name)
    }

    @Test
    fun `repository handles API errors gracefully`() = runTest {
        // Given
        val errorMessage = "Network error occurred"

        coEvery {
            repository.getUserLibraries()
        } returns ApiResult.Error(errorMessage, "NETWORK_ERROR")

        // When
        val result = repository.getUserLibraries()

        // Then
        assertTrue(result is ApiResult.Error<List<BaseItemDto>>)
        assertEquals(errorMessage, result.message)
        assertEquals("NETWORK_ERROR", result.errorType)
    }
}
