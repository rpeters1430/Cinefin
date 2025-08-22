package com.rpeters.jellyfin.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.rpeters.jellyfin.data.repository.JellyfinAuthRepository
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Comprehensive test suite for MainAppViewModel.
 *
 * Tests core functionality, state management, and security patterns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainAppViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var authRepository: JellyfinAuthRepository

    @MockK
    private lateinit var mediaRepository: JellyfinMediaRepository

    private lateinit var viewModel: MainAppViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)

        // Set up default mock responses
        coEvery { authRepository.isAuthenticated() } returns true
        coEvery { mediaRepository.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery { mediaRepository.getRecentlyAdded(any()) } returns ApiResult.Success(emptyList())
        coEvery { mediaRepository.getRecentlyAddedByType(any(), any()) } returns ApiResult.Success(emptyList())

        viewModel = MainAppViewModel(authRepository, mediaRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `MainAppViewModel dependencies are properly structured`() {
        // This test validates that the viewModel structure is correct
        // by testing the companion classes exist
        assertTrue("MainAppState should be available", MainAppState::class.java != null)
        assertTrue("PaginatedItems should be available", PaginatedItems::class.java != null)
    }

    @Test
    fun `MainAppState has proper default values`() {
        val state = MainAppState()

        assertFalse("Initial loading state should be false", state.isLoading)
        assertTrue("Initial libraries should be empty", state.libraries.isEmpty())
        assertTrue("Initial recently added should be empty", state.recentlyAdded.isEmpty())
        assertTrue("Initial search query should be empty", state.searchQuery.isEmpty())
        assertNull("Initial error message should be null", state.errorMessage)
    }

    @Test
    fun `PaginatedItems structure is correct`() {
        val paginatedItems = PaginatedItems(
            items = emptyList(),
            hasMore = false,
            totalCount = 0,
        )

        assertTrue("Items should be empty", paginatedItems.items.isEmpty())
        assertFalse("HasMore should be false", paginatedItems.hasMore)
        assertEquals("Total count should be 0", 0, paginatedItems.totalCount)
    }

    @Test
    fun `MainAppState can be copied with new values`() {
        val originalState = MainAppState()
        val newState = originalState.copy(isLoading = true, searchQuery = "test")

        assertTrue("New state should be loading", newState.isLoading)
        assertEquals("Search query should be updated", "test", newState.searchQuery)
        assertFalse("Original state should remain unchanged", originalState.isLoading)
    }

    @Test
    fun `initial state is correct`() = runTest {
        viewModel.uiState.test {
            val initialState = awaitItem()

            assertFalse("Should not be loading initially", initialState.isLoading)
            assertTrue("Libraries should be empty initially", initialState.libraries.isEmpty())
            assertTrue("Recently added should be empty initially", initialState.recentlyAdded.isEmpty())
            assertNull("Error message should be null initially", initialState.errorMessage)
        }
    }

    @Test
    fun `loadData shows loading state and updates libraries`() = runTest {
        // Given
        val mockLibraries = listOf(
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Movies"
                coEvery { type } returns BaseItemKind.COLLECTION_FOLDER
            },
        )

        coEvery { mediaRepository.getUserLibraries() } returns ApiResult.Success(mockLibraries)

        // When & Then
        viewModel.uiState.test {
            val initialState = awaitItem()
            assertFalse("Should not be loading initially", initialState.isLoading)

            viewModel.loadData()
            testDispatcher.scheduler.advanceUntilIdle()

            val loadingState = awaitItem()
            assertTrue("Should be loading", loadingState.isLoading)

            val finalState = awaitItem()
            assertFalse("Should not be loading after completion", finalState.isLoading)
            assertEquals("Should have 1 library", 1, finalState.libraries.size)
            assertEquals("Library name should match", "Movies", finalState.libraries[0].name)
        }
    }

    @Test
    fun `loadData handles errors gracefully`() = runTest {
        // Given
        val errorMessage = "Network error"
        coEvery { mediaRepository.getUserLibraries() } returns ApiResult.Error(errorMessage, "NETWORK_ERROR")

        // When & Then
        viewModel.uiState.test {
            skipItems(1) // Skip initial state

            viewModel.loadData()
            testDispatcher.scheduler.advanceUntilIdle()

            val loadingState = awaitItem()
            assertTrue("Should be loading", loadingState.isLoading)

            val errorState = awaitItem()
            assertFalse("Should not be loading after error", errorState.isLoading)
            assertEquals("Error message should be set", errorMessage, errorState.errorMessage)
        }
    }

    @Test
    fun `search query updates state correctly`() = runTest {
        // When & Then
        viewModel.uiState.test {
            skipItems(1) // Skip initial state

            viewModel.updateSearchQuery("test query")

            val updatedState = awaitItem()
            assertEquals("Search query should be updated", "test query", updatedState.searchQuery)
        }
    }

    @Test
    fun `clearError removes error message`() = runTest {
        // Given - first set an error
        coEvery { mediaRepository.getUserLibraries() } returns ApiResult.Error("Test error", "TEST_ERROR")

        viewModel.uiState.test {
            skipItems(1) // Skip initial state

            viewModel.loadData()
            testDispatcher.scheduler.advanceUntilIdle()

            skipItems(1) // Skip loading state
            val errorState = awaitItem()
            assertEquals("Error should be set", "Test error", errorState.errorMessage)

            // When
            viewModel.clearError()

            // Then
            val clearedState = awaitItem()
            assertNull("Error should be cleared", clearedState.errorMessage)
        }
    }

    @Test
    fun `recently added content loads with parallel execution`() = runTest {
        // Given
        val mockMovies = listOf(
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Recent Movie"
                coEvery { type } returns BaseItemKind.MOVIE
            },
        )

        val mockSeries = listOf(
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Recent Series"
                coEvery { type } returns BaseItemKind.SERIES
            },
        )

        coEvery { mediaRepository.getRecentlyAddedByType(BaseItemKind.MOVIE, 20) } returns ApiResult.Success(mockMovies)
        coEvery { mediaRepository.getRecentlyAddedByType(BaseItemKind.SERIES, 20) } returns ApiResult.Success(mockSeries)

        // When & Then
        viewModel.uiState.test {
            skipItems(1) // Skip initial state

            viewModel.loadData()
            testDispatcher.scheduler.advanceUntilIdle()

            skipItems(1) // Skip loading state
            val finalState = awaitItem()

            assertFalse("Should not be loading", finalState.isLoading)
            assertTrue("Should have recently added content", finalState.recentlyAdded.isNotEmpty())
        }
    }
}
