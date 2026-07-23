package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.cache.JellyfinCache
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.session.JellyfinSessionManager
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class JellyfinMediaRepositoryTest {

    @MockK
    private lateinit var authRepository: JellyfinAuthRepository

    @MockK
    private lateinit var sessionManager: JellyfinSessionManager

    @MockK
    private lateinit var cache: JellyfinCache

    @MockK
    private lateinit var healthChecker: com.rpeters.jellyfin.data.repository.common.LibraryHealthChecker

    @MockK
    private lateinit var apiClient: ApiClient

    @MockK
    private lateinit var itemsApi: ItemsApi

    private lateinit var repository: JellyfinMediaRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        repository = spyk(JellyfinMediaRepository(authRepository, sessionManager, cache, healthChecker))

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

        // Mock repository methods
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
        } returns ApiResult.Success(LibraryItemsResult(mockMovies, 1))

        // When
        val result = repository.getLibraryItems(
            parentId = parentId,
            itemTypes = itemTypes,
            startIndex = 0,
            limit = 100,
        )

        // Then
        assertTrue(result is ApiResult.Success<LibraryItemsResult>)
        val successResult = result as ApiResult.Success<LibraryItemsResult>
        assertEquals(1, successResult.data.items.size)
        assertEquals("Test Movie", successResult.data.items[0].name)
        assertEquals(BaseItemKind.MOVIE, successResult.data.items[0].type)
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
        val successResult = result as ApiResult.Success<List<BaseItemDto>>
        assertEquals(2, successResult.data.size)
        assertTrue(successResult.data.any { it.name == "Recent Movie" })
        assertTrue(successResult.data.any { it.name == "Recent Episode" })
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
        val successResult = result as ApiResult.Success<List<BaseItemDto>>
        assertEquals(2, successResult.data.size)
        assertTrue(successResult.data.all { it.type == BaseItemKind.MOVIE })
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
        val successResult = result as ApiResult.Success<BaseItemDto>
        assertEquals("Test Movie", successResult.data.name)
        assertEquals(BaseItemKind.MOVIE, successResult.data.type)
        assertEquals("A great test movie", successResult.data.overview)
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
        val successResult = result as ApiResult.Success<List<BaseItemDto>>
        assertEquals(2, successResult.data.size)
        assertEquals("Season 1", successResult.data[0].name)
        assertEquals("Season 2", successResult.data[1].name)
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
        val successResult = result as ApiResult.Success<List<BaseItemDto>>
        assertEquals(2, successResult.data.size)
        assertEquals("Episode 1", successResult.data[0].name)
        assertEquals("Episode 2", successResult.data[1].name)
    }

    @Test
    fun `repository handles API errors gracefully`() = runTest {
        // Given
        val errorMessage = "Network error occurred"

        coEvery {
            repository.getUserLibraries()
        } returns ApiResult.Error(errorMessage, null, com.rpeters.jellyfin.data.repository.common.ErrorType.NETWORK)

        // When
        val result = repository.getUserLibraries()

        // Then
        assertTrue(result is ApiResult.Error<List<BaseItemDto>>)
        val errorResult = result as ApiResult.Error<List<BaseItemDto>>
        assertEquals(errorMessage, errorResult.message)
        assertEquals(com.rpeters.jellyfin.data.repository.common.ErrorType.NETWORK, errorResult.errorType)
    }

    // ========== getAlbumsForArtist regression tests (GitHub issue #1194) ==========
    //
    // MusicArtist entities in Jellyfin are virtual/aggregated - they are NOT the literal
    // folder parent of MusicAlbum items, so querying with `parentId` always returned zero
    // albums. The fix queries with `artistIds` + `recursive = true` instead. Unlike the
    // tests above (which stub the repository method itself via `spyk`, never exercising the
    // real method body), this test drives the REAL `getAlbumsForArtist()` implementation so
    // the actual `ItemsApi.getItems(...)` call arguments can be verified with `coVerify`.

    @Test
    fun `getAlbumsForArtist queries itemsApi with artistIds and recursive, not parentId`() = runTest {
        // Given
        val artistId = "550e8400-e29b-41d4-a716-446655440000"
        val artistUuid = UUID.fromString(artistId)
        val userId = "11111111-1111-1111-1111-111111111111"
        val userUuid = UUID.fromString(userId)

        val testServer = JellyfinServer(
            id = "server-1",
            name = "Test Server",
            url = "https://demo.jellyfin.org",
            isConnected = true,
            userId = userId,
            username = "test-user",
            accessToken = "test-access-token",
        )

        // Wire authRepository so BaseJellyfinRepository.validateServer() /
        // validateTokenAndRefreshIfNeeded() resolve to our test server without reauthenticating.
        every { authRepository.getCurrentServer() } returns testServer
        every { authRepository.isTokenExpired() } returns false

        // Wire sessionManager.executeWithAuth(...) - called internally by
        // BaseJellyfinRepository.executeWithClient() - to invoke the real operation block with
        // our mocked ApiClient, so the real getAlbumsForArtist() body actually runs.
        coEvery {
            sessionManager.executeWithAuth<List<BaseItemDto>?>(any(), any())
        } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val block = invocation.args[1] as suspend (JellyfinServer, ApiClient) -> List<BaseItemDto>?
            block(testServer, apiClient)
        }

        val mockAlbums = listOf(
            mockk<BaseItemDto> {
                coEvery { id } returns UUID.randomUUID()
                coEvery { name } returns "Test Album"
                coEvery { type } returns BaseItemKind.MUSIC_ALBUM
            },
        )
        val queryResult = mockk<BaseItemDtoQueryResult> {
            coEvery { items } returns mockAlbums
            coEvery { totalRecordCount } returns mockAlbums.size
        }

        // Permissive stub: matches any argument shape so the "when" step always succeeds.
        // Correctness of the actual query is asserted purely via coVerify below.
        coEvery {
            itemsApi.getItems(
                userId = any(),
                artistIds = any(),
                recursive = any(),
                parentId = any(),
                includeItemTypes = any(),
                sortBy = any(),
                sortOrder = any(),
                fields = any(),
            )
        } returns Response(queryResult, 200, emptyMap())

        // Use a real (non-spyk) repository instance so getAlbumsForArtist()'s actual method
        // body executes instead of being stubbed away.
        val realRepository = JellyfinMediaRepository(authRepository, sessionManager, cache, healthChecker)

        // When
        val result = realRepository.getAlbumsForArtist(artistId)

        // Then
        assertTrue(result is ApiResult.Success<List<BaseItemDto>>)
        val successResult = result as ApiResult.Success<List<BaseItemDto>>
        assertEquals(1, successResult.data.size)
        assertEquals("Test Album", successResult.data[0].name)

        // Regression guard: must query by artistIds + recursive = true, and must NOT scope
        // the query by parentId (that was the bug behind issue #1194 - MusicArtist is not the
        // literal folder parent of its MusicAlbum items).
        coVerify(exactly = 1) {
            itemsApi.getItems(
                userId = userUuid,
                artistIds = listOf(artistUuid),
                recursive = true,
                parentId = null,
                includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                sortBy = any(),
                sortOrder = any(),
                fields = any(),
            )
        }
    }
}
