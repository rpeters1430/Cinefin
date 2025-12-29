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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Tests for MainAppViewModel's loadLibraryTypeData method.
 *
 * This test demonstrates proper handling of:
 * 1. Test dispatcher configuration using DispatcherProvider pattern
 * 2. Repository mocking with default parameters using any() matchers
 * 3. StateFlow observation and assertion in tests
 * 4. Coroutine execution control with advanceUntilIdle()
 * 5. Flow property mocking using coEvery
 *
 * Key Learnings:
 * - Use StandardTestDispatcher with TestDispatcherProvider to control ALL dispatchers (Main, IO, etc.)
 * - Inject TestDispatcherProvider into ViewModel to make withContext(Dispatchers.IO) testable
 * - Use every() for properties (not coEvery), use coEvery only for suspend functions
 * - Use any() matchers for default parameters in repository method mocks
 * - Always call advanceUntilIdle() after triggering async ViewModel operations
 */
@OptIn(ExperimentalCoroutinesApi::class, androidx.media3.common.util.UnstableApi::class)
class MainAppViewModelLibraryLoadTest {

    private lateinit var repository: JellyfinRepository

    @MockK
    private lateinit var authRepository: JellyfinAuthRepository

    @MockK
    private lateinit var mediaRepository: JellyfinMediaRepository

    @MockK
    private lateinit var userRepository: JellyfinUserRepository

    @MockK
    private lateinit var streamRepository: JellyfinStreamRepository

    @MockK
    private lateinit var searchRepository: JellyfinSearchRepository

    @MockK
    private lateinit var credentialManager: SecureCredentialManager

    @MockK
    private lateinit var castManager: CastManager

    @MockK
    private lateinit var context: Context

    private lateinit var viewModel: MainAppViewModel

    // Use StandardTestDispatcher for deterministic coroutine execution
    // This controls ALL coroutines including those using withContext(Dispatchers.IO)
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testDispatchers: TestDispatcherProvider

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)

        // CRITICAL: Set test dispatcher for Main
        // MainAppViewModel uses viewModelScope which delegates to Main dispatcher
        Dispatchers.setMain(testDispatcher)

        // Create TestDispatcherProvider that uses the same dispatcher for all contexts
        // This ensures withContext(Dispatchers.IO) uses our test dispatcher
        testDispatchers = TestDispatcherProvider(testDispatcher)

        // Mock the JellyfinRepository as relaxed
        repository = mockk(relaxed = true)

        // Mock Flow properties that repository delegates to
        // Use every() for properties, not coEvery()
        every { repository.currentServer } returns MutableStateFlow(null)
        every { repository.isConnected } returns MutableStateFlow(false)

        // Mock authentication to always succeed
        every { authRepository.isTokenExpired() } returns false
        coEvery { authRepository.reAuthenticate() } returns true

        // Create ViewModel with TestDispatcherProvider injected
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

        // Pre-populate the ViewModel state with libraries using the test helper
        viewModel.setAppStateForTest(
            MainAppState(libraries = listOf(library)),
        )

        // Mock getLibraryItems to return movies
        // IMPORTANT: Use any() for parameters with defaults (startIndex, limit)
        coEvery {
            mediaRepository.getLibraryItems(
                parentId = libraryId.toString(),
                itemTypes = "Movie",
                startIndex = any(),
                limit = any(),
                collectionType = "movies",
            )
        } returns ApiResult.Success(listOf(movie1, movie2))

        // Act
        viewModel.loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = false)

        // CRITICAL: Must advance dispatcher to execute all pending coroutines
        advanceUntilIdle()

        // Assert
        val state = viewModel.appState.value

        // Verify library items were loaded
        val items = state.itemsByLibrary[libraryId.toString()]
        assertNotNull("Items should be loaded for library $libraryId", items)
        assertEquals(2, items!!.size)
        assertEquals("Test Movie 1", items[0].name)
        assertEquals("Test Movie 2", items[1].name)

        // Verify loading state is cleared
        assertFalse(state.isLoading)
        assertFalse(state.isLoadingMovies)

        // Verify getUserLibraries was NOT called (libraries already in state)
        coVerify(exactly = 0) { mediaRepository.getUserLibraries(any()) }

        // Verify getLibraryItems was called exactly once
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

        // Pre-populate state
        viewModel.setAppStateForTest(
            MainAppState(libraries = listOf(library)),
        )

        // Mock getLibraryItems to return series
        coEvery {
            mediaRepository.getLibraryItems(
                parentId = libraryId.toString(),
                itemTypes = "Series",
                startIndex = any(),
                limit = any(),
                collectionType = "tvshows",
            )
        } returns ApiResult.Success(listOf(series))

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

        // Verify loading states
        assertFalse(state.isLoading)
        assertFalse(state.isLoadingTVShows)

        // Verify correct itemTypes was used
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

        // Verify loading stopped
        assertFalse(state.isLoading)
        assertFalse(state.isLoadingMovies)

        // Verify no items were loaded
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

        // Return exactly 100 items to simulate hasMore = true
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
        } returns ApiResult.Success(movies)

        // Act
        viewModel.loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = false)
        advanceUntilIdle()

        // Assert
        val state = viewModel.appState.value
        val paginationState = state.libraryPaginationState[libraryId.toString()]
        assertNotNull("Pagination state should exist", paginationState)
        assertEquals(100, paginationState!!.loadedCount)
        assertTrue(
            "hasMore should be true when exactly 100 items returned",
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

        // getLibraryItems should not be called since no matching library exists
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
}
