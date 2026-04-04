package com.rpeters.jellyfin.ui.viewmodel

import android.content.Context
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.common.TestDispatcherProvider
import com.rpeters.jellyfin.data.repository.JellyfinAuthRepository
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.JellyfinSearchRepository
import com.rpeters.jellyfin.data.repository.JellyfinStreamRepository
import com.rpeters.jellyfin.data.repository.JellyfinUserRepository
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.model.CurrentUserDetails
import com.rpeters.jellyfin.data.repository.LibraryItemsResult
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.ui.player.CastManager
import com.rpeters.jellyfin.ui.screens.LibraryType
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Tests for MainAppViewModel's loadLibraryTypeData method.
 */
@OptIn(ExperimentalCoroutinesApi::class, androidx.media3.common.util.UnstableApi::class)
class MainAppViewModelLibraryLoadTest {

    @MockK(relaxed = true)
    private lateinit var repository: com.rpeters.jellyfin.data.repository.IJellyfinRepository

    @MockK(relaxed = true)
    private lateinit var authRepository: com.rpeters.jellyfin.data.repository.IJellyfinAuthRepository

    @MockK(relaxed = true)
    private lateinit var mediaRepository: JellyfinMediaRepository

    @MockK(relaxed = true)
    private lateinit var userRepository: JellyfinUserRepository

    @MockK(relaxed = true)
    private lateinit var streamRepository: JellyfinStreamRepository

    @MockK(relaxed = true)
    private lateinit var searchRepository: JellyfinSearchRepository

    @MockK(relaxed = true)
    private lateinit var credentialManager: SecureCredentialManager

    @MockK(relaxed = true)
    private lateinit var castManager: CastManager

    @MockK(relaxed = true)
    private lateinit var generativeAiRepository: com.rpeters.jellyfin.data.repository.GenerativeAiRepository

    @MockK(relaxed = true)
    private lateinit var analyticsHelper: com.rpeters.jellyfin.utils.AnalyticsHelper

    @MockK(relaxed = true)
    private lateinit var context: Context

    private val currentServerFlow = MutableStateFlow<JellyfinServer?>(null)
    private val isConnectedFlow = MutableStateFlow(false)

    private lateinit var viewModel: MainAppViewModel

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testDispatchers: TestDispatcherProvider

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)

        Dispatchers.setMain(testDispatcher)

        testDispatchers = TestDispatcherProvider(testDispatcher)

        every { repository.connectivityChecker.observeNetworkConnectivity() } returns kotlinx.coroutines.flow.flowOf(false)

        every { repository.currentServerFlow } returns currentServerFlow
        every { repository.isConnectedFlow } returns isConnectedFlow

        every { authRepository.isTokenExpired() } returns false
        coEvery { authRepository.reAuthenticate() } returns true

        currentServerFlow.value = null
        isConnectedFlow.value = false

        viewModel = MainAppViewModel(
            context = context,
            repository = repository,
            authRepository = authRepository,
            mediaRepository = mediaRepository,
            userRepository = userRepository,
            streamRepository = streamRepository,
            searchRepository = searchRepository,
            credentialManager = credentialManager,
            castManager = castManager,
            dispatchers = testDispatchers,
            generativeAiRepository = generativeAiRepository,
            analytics = analyticsHelper,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadLibraryTypeData_whenLibrariesAlreadyLoaded_loadsItemsDirectly`() = runTest {
        // Arrange
        val libraryId = UUID.randomUUID()
        val library = BaseItemDto(
            id = libraryId,
            name = "Movies",
            type = BaseItemKind.COLLECTION_FOLDER,
            collectionType = CollectionType.MOVIES,
        )
        val movie1 = BaseItemDto(
            id = UUID.randomUUID(),
            name = "Test Movie 1",
            type = BaseItemKind.MOVIE,
        )
        val movie2 = BaseItemDto(
            id = UUID.randomUUID(),
            name = "Test Movie 2",
            type = BaseItemKind.MOVIE,
        )

        viewModel.setAppStateForTest(
            MainAppState(libraries = listOf(library)),
        )

        coEvery {
            mediaRepository.getLibraryItems(
                parentId = libraryId.toString(),
                itemTypes = "Movie",
                startIndex = any(),
                limit = any(),
                collectionType = "movies",
            )
        } returns ApiResult.Success(LibraryItemsResult(listOf(movie1, movie2), 2))

        // Act
        viewModel.loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = false)

        advanceUntilIdle()

        // Assert
        val state = viewModel.appState.value

        val items = state.itemsByLibrary[libraryId.toString()]
        assertNotNull("Items should be loaded for library $libraryId", items)
        assertEquals(2, items!!.size)
        assertEquals("Test Movie 1", items[0].name)
        assertEquals("Test Movie 2", items[1].name)

        assertFalse(state.isLoading)
        assertFalse(state.isLoadingMovies)

        coVerify(exactly = 0) { mediaRepository.getUserLibraries(any()) }

        coVerify(exactly = 1) {
            mediaRepository.getLibraryItems(
                parentId = libraryId.toString(),
                itemTypes = "Movie",
                startIndex = any(),
                limit = any(),
                collectionType = "movies",
            )
        }
    }

    @Test
    fun `loadLibraryTypeData_withTVShows_loadsSeriesCorrectly`() = runTest {
        // Arrange
        val libraryId = UUID.randomUUID()
        val library = BaseItemDto(
            id = libraryId,
            name = "TV Shows",
            type = BaseItemKind.COLLECTION_FOLDER,
            collectionType = CollectionType.TVSHOWS,
        )
        val series = BaseItemDto(
            id = UUID.randomUUID(),
            name = "Test Series",
            type = BaseItemKind.SERIES,
        )

        viewModel.setAppStateForTest(
            MainAppState(libraries = listOf(library)),
        )

        coEvery {
            mediaRepository.getLibraryItems(
                parentId = libraryId.toString(),
                itemTypes = "Series",
                startIndex = any(),
                limit = any(),
                collectionType = "tvshows",
            )
        } returns ApiResult.Success(LibraryItemsResult(listOf(series), 1))

        // Act
        viewModel.loadLibraryTypeData(LibraryType.TV_SHOWS, forceRefresh = false)
        advanceUntilIdle()

        // Assert
        val state = viewModel.appState.value
        val items = state.itemsByLibrary[libraryId.toString()]
        assertNotNull("Items should be loaded for library $libraryId", items)
        assertEquals(1, items!!.size)
        assertEquals("Test Series", items[0].name)
        assertEquals(BaseItemKind.SERIES, items[0].type)

        assertFalse(state.isLoading)
        assertFalse(state.isLoadingTVShows)

        coVerify(exactly = 1) {
            mediaRepository.getLibraryItems(
                parentId = libraryId.toString(),
                itemTypes = "Series",
                startIndex = any(),
                limit = any(),
                collectionType = "tvshows",
            )
        }
    }

    @Test
    fun `loadLibraryTypeData_onError_updatesErrorMessage`() = runTest {
        // Arrange
        val libraryId = UUID.randomUUID()
        val library = BaseItemDto(
            id = libraryId,
            name = "Movies",
            type = BaseItemKind.COLLECTION_FOLDER,
            collectionType = CollectionType.MOVIES,
        )

        viewModel.setAppStateForTest(
            MainAppState(libraries = listOf(library)),
        )

        val errorMessage = "Network error"
        coEvery {
            mediaRepository.getLibraryItems(
                parentId = libraryId.toString(),
                itemTypes = "Movie",
                startIndex = any(),
                limit = any(),
                collectionType = "movies",
            )
        } returns ApiResult.Error(errorMessage)

        // Act
        viewModel.loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = false)
        advanceUntilIdle()

        // Assert
        val state = viewModel.appState.value
        assertNotNull("Error message should be set", state.errorMessage)
        assertTrue(
            "Error message should contain network error",
            state.errorMessage?.contains("Network error") == true,
        )

        assertFalse(state.isLoading)
        assertFalse(state.isLoadingMovies)

        assertTrue(
            "No items should be loaded on error",
            state.itemsByLibrary[libraryId.toString()]?.isEmpty() != false,
        )
    }

    @Test
    fun `loadLibraryTypeData_withPagination_detectsHasMore`() = runTest {
        // Arrange
        val libraryId = UUID.randomUUID()
        val library = BaseItemDto(
            id = libraryId,
            name = "Movies",
            type = BaseItemKind.COLLECTION_FOLDER,
            collectionType = CollectionType.MOVIES,
        )

        viewModel.setAppStateForTest(
            MainAppState(libraries = listOf(library)),
        )

        val movies = (1..100).map { index ->
            BaseItemDto(
                id = UUID.randomUUID(),
                name = "Movie $index",
                type = BaseItemKind.MOVIE,
            )
        }

        coEvery {
            mediaRepository.getLibraryItems(
                parentId = libraryId.toString(),
                itemTypes = "Movie",
                startIndex = any(),
                limit = any(),
                collectionType = "movies",
            )
        } returns ApiResult.Success(LibraryItemsResult(movies, 200))

        // Act
        viewModel.loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = false)
        advanceUntilIdle()

        // Assert
        val state = viewModel.appState.value
        val paginationState = state.libraryPaginationState[libraryId.toString()]
        assertNotNull("Pagination state should exist", paginationState)
        assertEquals(100, paginationState!!.nextStartIndex)
        assertTrue(
            "hasMore should be true when items returned is less than totalCount",
            paginationState.hasMore,
        )
        assertFalse("isLoadingMore should be false", paginationState.isLoadingMore)
    }

    @Test
    fun `loadLibraryTypeData_withoutMatchingLibrary_handlesGracefully`() = runTest {
        // Arrange - Create a music library when we're trying to load movies
        val musicLibraryId = UUID.randomUUID()
        val musicLibrary = BaseItemDto(
            id = musicLibraryId,
            name = "Music",
            type = BaseItemKind.COLLECTION_FOLDER,
            collectionType = CollectionType.MUSIC,
        )

        viewModel.setAppStateForTest(
            MainAppState(libraries = listOf(musicLibrary)),
        )

        // Act - Try to load movies (which don't exist)
        viewModel.loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = false)
        advanceUntilIdle()

        // Assert - No items should be loaded
        val state = viewModel.appState.value
        assertEquals("No items should be in itemsByLibrary", 0, state.itemsByLibrary.size)

        coVerify(exactly = 0) {
            mediaRepository.getLibraryItems(
                parentId = any(),
                itemTypes = any(),
                startIndex = any(),
                limit = any(),
                collectionType = any(),
            )
        }
    }

    @Test
    fun `loadLibraryTypeData_clearError_removesErrorMessage`() = runTest {
        // Arrange
        viewModel.setAppStateForTest(
            MainAppState(errorMessage = "Previous error"),
        )

        // Act
        viewModel.clearError()
        advanceUntilIdle()

        // Assert
        val state = viewModel.appState.value
        assertEquals(null, state.errorMessage)
    }

    @Test
    fun `loadLibraryTypeData_whenDataAlreadyLoaded_skipsReloadWithoutForceRefresh`() = runTest {
        // Arrange
        val libraryId = UUID.randomUUID()
        val library = BaseItemDto(
            id = libraryId,
            name = "Movies",
            type = BaseItemKind.COLLECTION_FOLDER,
            collectionType = CollectionType.MOVIES,
        )
        val existingMovie = BaseItemDto(
            id = UUID.randomUUID(),
            name = "Already Loaded Movie",
            type = BaseItemKind.MOVIE,
        )

        viewModel.setAppStateForTest(
            MainAppState(
                libraries = listOf(library),
                itemsByLibrary = mapOf(libraryId.toString() to listOf(existingMovie)),
            ),
        )

        // Act - call without forceRefresh
        viewModel.loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = false)
        advanceUntilIdle()

        coVerify(exactly = 0) {
            mediaRepository.getLibraryItems(
                parentId = any(),
                itemTypes = any(),
                startIndex = any(),
                limit = any(),
                collectionType = any(),
            )
        }

        val state = viewModel.appState.value
        val items = state.itemsByLibrary[libraryId.toString()]
        assertNotNull("Items should still be present", items)
        assertEquals(1, items!!.size)
        assertEquals("Already Loaded Movie", items.first().name)
    }

    @Test
    fun `loadLibraryTypeData_whenDataAlreadyLoaded_reloadsWithForceRefresh`() = runTest {
        // Arrange
        val libraryId = UUID.randomUUID()
        val library = BaseItemDto(
            id = libraryId,
            name = "Movies",
            type = BaseItemKind.COLLECTION_FOLDER,
            collectionType = CollectionType.MOVIES,
        )
        val existingMovie = BaseItemDto(
            id = UUID.randomUUID(),
            name = "Old Movie",
            type = BaseItemKind.MOVIE,
        )
        val newMovie = BaseItemDto(
            id = UUID.randomUUID(),
            name = "New Movie",
            type = BaseItemKind.MOVIE,
        )

        viewModel.setAppStateForTest(
            MainAppState(
                libraries = listOf(library),
                itemsByLibrary = mapOf(libraryId.toString() to listOf(existingMovie)),
            ),
        )

        coEvery {
            mediaRepository.getLibraryItems(
                parentId = libraryId.toString(),
                itemTypes = "Movie",
                startIndex = any(),
                limit = any(),
                collectionType = "movies",
            )
        } returns ApiResult.Success(LibraryItemsResult(listOf(newMovie), 1))

        // Act - call WITH forceRefresh
        viewModel.loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = true)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            mediaRepository.getLibraryItems(
                parentId = libraryId.toString(),
                itemTypes = "Movie",
                startIndex = any(),
                limit = any(),
                collectionType = "movies",
            )
        }

        val state = viewModel.appState.value
        val items = state.itemsByLibrary[libraryId.toString()]
        assertEquals(1, items?.size)
        assertEquals("New Movie", items?.firstOrNull()?.name)
    }

    @Test
    fun `loadInitialData_whenAuthenticated_loadsAllCategoriesCorrectly`() = runTest {
        // Arrange
        val library = BaseItemDto(
            id = UUID.randomUUID(),
            name = "Movies",
            type = BaseItemKind.COLLECTION_FOLDER,
            collectionType = CollectionType.MOVIES,
        )
        val recentlyAdded = BaseItemDto(
            id = UUID.randomUUID(),
            name = "Recent Movie",
            type = BaseItemKind.MOVIE,
        )
        val continueWatching = BaseItemDto(
            id = UUID.randomUUID(),
            name = "Continue Movie",
            type = BaseItemKind.MOVIE,
        )
        val userDetails = CurrentUserDetails(
            name = "Test User",
            primaryImageTag = null,
            lastLoginDate = null,
            isAdministrator = false,
        )

        val server = JellyfinServer(
            id = "test-server",
            url = "http://localhost",
            name = "Test Server",
            accessToken = "token",
            userId = "user",
            isConnected = true,
        )
        currentServerFlow.value = server

        coEvery { mediaRepository.getUserLibraries(any()) } returns ApiResult.Success(listOf(library))
        coEvery { mediaRepository.getRecentlyAdded(any(), any()) } returns ApiResult.Success(listOf(recentlyAdded))
        coEvery { mediaRepository.getContinueWatching(any()) } returns ApiResult.Success(listOf(continueWatching))
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Success(userDetails)
        coEvery { mediaRepository.getRecentlyAddedByType(any(), any(), any()) } returns ApiResult.Success(emptyList())

        // Act
        viewModel.loadInitialData()
        advanceUntilIdle()

        // Assert
        val state = viewModel.appState.value
        assertFalse("Should not be loading after completion", state.isLoading)
        assertNull("Should not have error message", state.errorMessage)
        assertEquals(1, state.libraries.size)
        assertEquals("Movies", state.libraries[0].name)
        assertEquals(1, state.recentlyAdded.size)
        assertEquals("Recent Movie", recentlyAdded.name)
        assertEquals(1, state.continueWatching.size)
        assertEquals("Continue Movie", continueWatching.name)
        assertNotNull("User should be loaded", state.currentUser)
        assertEquals("Test User", state.currentUser?.name)
    }

    @Test
    fun `loadInitialData_whenTokenPreflightFailsButSessionAvailable_stillAttemptsLoad`() = runTest {
        // Arrange
        val server = JellyfinServer(
            id = "test-server",
            url = "http://localhost",
            name = "Test Server",
            accessToken = "token",
            userId = "user",
            isConnected = true,
        )
        currentServerFlow.value = server

        // Mock reAuthenticate to fail but ensureValidToken might still pass or we hit the fallback logic
        coEvery { authRepository.reAuthenticate() } returns false
        every { authRepository.isTokenExpired() } returns true
        every { authRepository.isUserAuthenticated() } returns true
        every { authRepository.getCurrentServer() } returns server

        val library = BaseItemDto(
            id = UUID.randomUUID(),
            name = "Movies",
            type = BaseItemKind.COLLECTION_FOLDER,
            collectionType = CollectionType.MOVIES,
        )

        coEvery { mediaRepository.getUserLibraries(any()) } returns ApiResult.Success(listOf(library))
        coEvery { mediaRepository.getRecentlyAdded(any(), any()) } returns ApiResult.Success(emptyList())
        coEvery { mediaRepository.getContinueWatching(any()) } returns ApiResult.Success(emptyList())
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Error("Fail")
        coEvery { mediaRepository.getRecentlyAddedByType(any(), any(), any()) } returns ApiResult.Success(emptyList())

        // Act
        viewModel.loadInitialData()
        advanceUntilIdle()

        // Assert
        val state = viewModel.appState.value
        assertEquals(1, state.libraries.size)
        assertEquals("Movies", state.libraries[0].name)
    }

    @Test
    fun `loadInitialData_onLibraryError_updatesStateWithError`() = runTest {
        // Arrange
        val server = JellyfinServer(
            id = "test-server",
            url = "http://localhost",
            name = "Test Server",
            accessToken = "token",
            userId = "user",
            isConnected = true,
        )
        currentServerFlow.value = server

        coEvery { mediaRepository.getUserLibraries(any()) } returns ApiResult.Error("Network Error")
        coEvery { mediaRepository.getRecentlyAdded(any(), any()) } returns ApiResult.Success(emptyList())
        coEvery { mediaRepository.getContinueWatching(any()) } returns ApiResult.Success(emptyList())
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Success(
            CurrentUserDetails("User", null, null, false),
        )
        coEvery { mediaRepository.getRecentlyAddedByType(any(), any(), any()) } returns ApiResult.Success(emptyList())

        // Act
        viewModel.loadInitialData()
        advanceUntilIdle()

        // Assert
        val state = viewModel.appState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Failed to load libraries: Network Error"))
    }
}
