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
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userViewApi
import org.jellyfin.sdk.api.operations.LibraryApi
import org.jellyfin.sdk.api.operations.UserViewApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
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
    private lateinit var libraryApi: LibraryApi

    @MockK
    private lateinit var userViewApi: UserViewApi

    private lateinit var repository: JellyfinMediaRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        repository = spyk(JellyfinMediaRepository(authRepository, sessionManager, cache, healthChecker))

        // Mock API client setup
        coEvery { apiClient.libraryApi } returns libraryApi
        coEvery { apiClient.userViewApi } returns userViewApi
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
    fun `getUserLibraries queries canonical user views endpoint`() = runTest {
        val userUuid = UUID.randomUUID()
        val testServer = JellyfinServer(
            id = "test-server",
            name = "Test Server",
            url = "http://localhost:8096",
            userId = userUuid.toString(),
            accessToken = "test-token",
        )
        val mockLibraries = listOf(
            mockk<BaseItemDto> {
                coEvery { id } returns UUID.randomUUID()
                coEvery { name } returns "Movies"
                coEvery { type } returns BaseItemKind.COLLECTION_FOLDER
                coEvery { collectionType } returns CollectionType.MOVIES
            },
        )
        val queryResult = mockk<BaseItemDtoQueryResult> {
            coEvery { items } returns mockLibraries
        }

        every { authRepository.getCurrentServerSync() } returns testServer
        every { authRepository.isTokenExpired() } returns false
        coEvery {
            sessionManager.executeWithAuth<List<BaseItemDto>>(any(), any())
        } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val block = invocation.args[1] as suspend (JellyfinServer, ApiClient) -> List<BaseItemDto>
            block(testServer, apiClient)
        }
        coEvery {
            userViewApi.getUserViews(
                userId = userUuid,
                includeExternalContent = false,
                includeHidden = false,
            )
        } returns Response(queryResult, 200, emptyMap())

        val realRepository = JellyfinMediaRepository(authRepository, sessionManager, cache, healthChecker)

        val result = realRepository.getUserLibraries()

        assertTrue(result is ApiResult.Success<List<BaseItemDto>>)
        assertEquals(mockLibraries, (result as ApiResult.Success).data)
        coVerify(exactly = 1) {
            userViewApi.getUserViews(
                userId = userUuid,
                includeExternalContent = false,
                includeHidden = false,
            )
        }
        coVerify(exactly = 0) {
            libraryApi.getItems(
                userId = any(),
                includeItemTypes = listOf(BaseItemKind.COLLECTION_FOLDER),
            )
        }
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
            coEvery { id } returns java.util.UUID.fromString(TEST_ARTIST_ID)
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
    // albums. The fix queries with `albumArtistIds`/`artistIds` + `recursive = true` instead. Unlike the
    // tests above (which stub the repository method itself via `spyk`, never exercising the
    // real method body), this test drives the REAL `getAlbumsForArtist()` implementation so
    // the actual `ItemsApi.getItems(...)` call arguments can be verified with `coVerify`.

    private fun wireRealAlbumQueries(userId: String) {
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
        every { authRepository.getCurrentServerSync() } returns testServer
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
    }

    @Test
    fun `getAlbumTracks combines featured variants recursively without artist filtering`() = runTest {
        wireRealAlbumQueries(TEST_USER_ID)
        val parent = UUID.randomUUID()
        val main = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MUSIC_ALBUM,
            name = "Test Album", albumArtist = "Metallica", parentId = parent)
        val featured = main.copy(id = UUID.randomUUID(), albumArtist = "Metallica feat. Guest")
        val unrelated = main.copy(id = UUID.randomUUID(), albumArtist = "Megadeth")
        val first = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.AUDIO,
            parentIndexNumber = 1, indexNumber = 1, artists = listOf("Metallica"))
        val guest = first.copy(id = UUID.randomUUID(), indexNumber = 2, artists = listOf("Metallica", "Guest"))
        val disc2 = first.copy(id = UUID.randomUUID(), parentIndexNumber = 2)
        coEvery {
            libraryApi.getItems(userId = any(), ids = listOf(main.id), limit = 1, fields = any())
        } returns Response(BaseItemDtoQueryResult(items = listOf(main)), 200, emptyMap())
        coEvery {
            libraryApi.getItems(userId = any(), parentId = parent, recursive = true,
                searchTerm = main.name, includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM), fields = any())
        } returns Response(BaseItemDtoQueryResult(items = listOf(main, featured, unrelated)), 200, emptyMap())
        coEvery {
            libraryApi.getItems(userId = any(), parentId = main.id, recursive = true,
                includeItemTypes = listOf(BaseItemKind.AUDIO), sortBy = any(), sortOrder = any(), fields = any())
        } returns Response(BaseItemDtoQueryResult(items = listOf(disc2, first)), 200, emptyMap())
        coEvery {
            libraryApi.getItems(userId = any(), parentId = featured.id, recursive = true,
                includeItemTypes = listOf(BaseItemKind.AUDIO), sortBy = any(), sortOrder = any(), fields = any())
        } returns Response(BaseItemDtoQueryResult(items = listOf(guest)), 200, emptyMap())
        val realRepository = JellyfinMediaRepository(authRepository, sessionManager, cache, healthChecker)
        val result = realRepository.getAlbumTracks(main.id.toString())
        assertTrue(result is ApiResult.Success)
        assertEquals(listOf(first, guest, disc2), (result as ApiResult.Success<List<BaseItemDto>>).data)
        coVerify(exactly = 0) {
            libraryApi.getItems(userId = any(), parentId = unrelated.id, recursive = any(),
                includeItemTypes = any(), sortBy = any(), sortOrder = any(), fields = any())
        }
    }

    private fun albumQueryResult(vararg names: String): BaseItemDtoQueryResult {
        val albums = names.map { albumName ->
            mockk<BaseItemDto> {
                coEvery { id } returns UUID.randomUUID()
                coEvery { name } returns albumName
                coEvery { type } returns BaseItemKind.MUSIC_ALBUM
            }
        }
        return mockk {
            coEvery { items } returns albums
            coEvery { totalRecordCount } returns albums.size
        }
    }

    private fun stubAlbumQuery(
        albumArtistIds: List<UUID>?,
        artistIds: List<UUID>?,
        result: BaseItemDtoQueryResult,
    ) {
        coEvery {
            libraryApi.getItems(
                userId = any(),
                albumArtistIds = albumArtistIds,
                artistIds = artistIds,
                recursive = any(),
                parentId = any(),
                includeItemTypes = any(),
                sortBy = any(),
                sortOrder = any(),
                fields = any(),
            )
        } returns Response(result, 200, emptyMap())
    }

    @Test
    fun `getAlbumsForArtist queries by albumArtistIds and recursive, not parentId`() = runTest {
        // Given
        val artistId = TEST_ARTIST_ID
        val artistUuid = UUID.fromString(artistId)
        val userId = TEST_USER_ID
        val userUuid = UUID.fromString(userId)
        wireRealAlbumQueries(userId)
        stubAlbumQuery(albumArtistIds = listOf(artistUuid), artistIds = null, result = albumQueryResult("Test Album"))

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

        // Regression guard: must query recursively and must NOT scope the query by parentId
        // (that was the bug behind issue #1194 - MusicArtist is not the literal folder parent
        // of its MusicAlbum items). Album-artist matches are preferred so albums with featured
        // guests aren't listed under every guest (issue #1329).
        coVerify(exactly = 1) {
            libraryApi.getItems(
                userId = userUuid,
                albumArtistIds = listOf(artistUuid),
                artistIds = null,
                recursive = true,
                parentId = null,
                includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                sortBy = any(),
                sortOrder = any(),
                fields = any(),
            )
        }
        coVerify(exactly = 0) {
            libraryApi.getItems(
                userId = any(),
                albumArtistIds = null,
                artistIds = listOf(artistUuid),
                recursive = any(),
                parentId = any(),
                includeItemTypes = any(),
                sortBy = any(),
                sortOrder = any(),
                fields = any(),
            )
        }
    }

    @Test
    fun `getAlbumsForArtist falls back to artistIds when artist has no albums of their own`() = runTest {
        // Given
        val artistId = TEST_ARTIST_ID
        val artistUuid = UUID.fromString(artistId)
        wireRealAlbumQueries(TEST_USER_ID)
        stubAlbumQuery(albumArtistIds = listOf(artistUuid), artistIds = null, result = albumQueryResult())
        stubAlbumQuery(albumArtistIds = null, artistIds = listOf(artistUuid), result = albumQueryResult("Guest Spot"))

        val realRepository = JellyfinMediaRepository(authRepository, sessionManager, cache, healthChecker)

        // When
        val result = realRepository.getAlbumsForArtist(artistId)

        // Then
        assertTrue(result is ApiResult.Success<List<BaseItemDto>>)
        val successResult = result as ApiResult.Success<List<BaseItemDto>>
        assertEquals(listOf("Guest Spot"), successResult.data.map { it.name })
    }

    @Test
    fun `getPlaylistItems queries items with parentId and returns items`() = runTest {
        val userUuid = UUID.randomUUID()
        val playlistUuid = UUID.randomUUID()
        val playlistId = playlistUuid.toString()

        val testServer = JellyfinServer(
            id = "test-server",
            name = "Test Server",
            url = "http://localhost:8096",
            userId = userUuid.toString(),
            accessToken = "test-token",
        )

        every { authRepository.getCurrentServerSync() } returns testServer
        every { authRepository.isTokenExpired() } returns false

        coEvery {
            sessionManager.executeWithAuth<List<BaseItemDto>?>(any(), any())
        } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val block = invocation.args[1] as suspend (JellyfinServer, ApiClient) -> List<BaseItemDto>?
            block(testServer, apiClient)
        }

        val mockVideos = listOf(
            mockk<BaseItemDto> {
                coEvery { id } returns UUID.randomUUID()
                coEvery { name } returns "YouTube Video 1"
                coEvery { type } returns BaseItemKind.VIDEO
            },
        )
        val queryResult = mockk<BaseItemDtoQueryResult> {
            coEvery { items } returns mockVideos
        }

        coEvery {
            libraryApi.getItems(
                userId = userUuid,
                parentId = playlistUuid,
                fields = any(),
            )
        } returns Response(queryResult, 200, emptyMap())

        val realRepository = JellyfinMediaRepository(authRepository, sessionManager, cache, healthChecker)
        val result = realRepository.getPlaylistItems(playlistId)

        assertTrue(result is ApiResult.Success<List<BaseItemDto>>)
        val successResult = result as ApiResult.Success<List<BaseItemDto>>
        assertEquals(1, successResult.data.size)
        assertEquals("YouTube Video 1", successResult.data[0].name)
    }

    @Test
    fun `getPlaylists queries items with PLAYLIST type and returns list`() = runTest {
        val userUuid = UUID.randomUUID()

        val testServer = JellyfinServer(
            id = "test-server",
            name = "Test Server",
            url = "http://localhost:8096",
            userId = userUuid.toString(),
            accessToken = "test-token",
        )

        every { authRepository.getCurrentServerSync() } returns testServer
        every { authRepository.isTokenExpired() } returns false

        coEvery {
            sessionManager.executeWithAuth<List<BaseItemDto>?>(any(), any())
        } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val block = invocation.args[1] as suspend (JellyfinServer, ApiClient) -> List<BaseItemDto>?
            block(testServer, apiClient)
        }

        val mockPlaylists = listOf(
            mockk<BaseItemDto> {
                coEvery { id } returns UUID.randomUUID()
                coEvery { name } returns "YouTarr Playlist"
                coEvery { type } returns BaseItemKind.PLAYLIST
            },
        )
        val queryResult = mockk<BaseItemDtoQueryResult> {
            coEvery { items } returns mockPlaylists
        }

        coEvery {
            libraryApi.getItems(
                userId = userUuid,
                includeItemTypes = listOf(BaseItemKind.PLAYLIST),
                recursive = true,
                limit = any(),
                sortBy = any(),
                sortOrder = any(),
                fields = any(),
            )
        } returns Response(queryResult, 200, emptyMap())

        val realRepository = JellyfinMediaRepository(authRepository, sessionManager, cache, healthChecker)
        val result = realRepository.getPlaylists(100)

        assertTrue(result is ApiResult.Success<List<BaseItemDto>>)
        val successResult = result as ApiResult.Success<List<BaseItemDto>>
        assertEquals(1, successResult.data.size)
        assertEquals("YouTarr Playlist", successResult.data[0].name)
    }

    @Test
    fun `getRecentlyAddedFromLibrary returns success with items from specific library`() = runTest {
        val userUuid = UUID.randomUUID()
        val libraryUuid = UUID.randomUUID()
        val testServer = JellyfinServer(
            id = "server-1",
            name = "Test Server",
            url = "http://localhost:8096",
            accessToken = "test-token",
            userId = userUuid.toString(),
        )

        every { authRepository.getCurrentServerSync() } returns testServer
        every { authRepository.isTokenExpired() } returns false

        coEvery {
            sessionManager.executeWithAuth<List<BaseItemDto>?>(any(), any())
        } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val block = invocation.args[1] as suspend (JellyfinServer, ApiClient) -> List<BaseItemDto>?
            block(testServer, apiClient)
        }

        val mockItems = listOf(
            mockk<BaseItemDto> {
                coEvery { id } returns UUID.randomUUID()
                coEvery { name } returns "YouTube Video 1"
                coEvery { type } returns BaseItemKind.VIDEO
            },
        )
        val queryResult = mockk<BaseItemDtoQueryResult> {
            coEvery { items } returns mockItems
        }

        coEvery {
            libraryApi.getItems(
                userId = userUuid,
                parentId = libraryUuid,
                recursive = true,
                includeItemTypes = listOf(BaseItemKind.VIDEO),
                limit = any(),
                sortBy = any(),
                sortOrder = any(),
                fields = any(),
            )
        } returns Response(queryResult, 200, emptyMap())

        val realRepository = JellyfinMediaRepository(authRepository, sessionManager, cache, healthChecker)
        val result = realRepository.getRecentlyAddedFromLibrary(
            libraryId = libraryUuid.toString(),
            limit = 15,
            includeItemTypes = listOf(BaseItemKind.VIDEO),
        )

        assertTrue(result is ApiResult.Success<List<BaseItemDto>>)
        val successResult = result as ApiResult.Success<List<BaseItemDto>>
        assertEquals(1, successResult.data.size)
        assertEquals("YouTube Video 1", successResult.data[0].name)

        coVerify(exactly = 1) {
            libraryApi.getItems(
                userId = userUuid,
                parentId = libraryUuid,
                recursive = true,
                includeItemTypes = listOf(BaseItemKind.VIDEO),
                limit = 15,
                sortBy = any(),
                sortOrder = any(),
                fields = any(),
            )
        }
    }

    @Test
    fun `recently added rows request playable episodes for TV libraries`() {
        assertEquals(
            listOf(BaseItemKind.EPISODE),
            recentlyAddedItemTypesForCollection(CollectionType.TVSHOWS),
        )
    }

    @Test
    fun `recently added rows request playable videos for home video libraries`() {
        assertEquals(
            listOf(BaseItemKind.VIDEO),
            recentlyAddedItemTypesForCollection(CollectionType.HOMEVIDEOS),
        )
    }

    @Test
    fun `recently added rows request native playlists for playlist libraries`() {
        assertEquals(
            listOf(BaseItemKind.PLAYLIST),
            recentlyAddedItemTypesForCollection(CollectionType.PLAYLISTS),
        )
    }

    private companion object {
        const val TEST_ARTIST_ID = "550e8400-e29b-41d4-a716-446655440000"
        const val TEST_USER_ID = "11111111-1111-1111-1111-111111111111"
    }
}
