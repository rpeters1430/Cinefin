package com.rpeters.jellyfin.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class HomeViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mediaRepository: JellyfinMediaRepository

    private lateinit var viewModel: HomeViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        viewModel = HomeViewModel(mediaRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() = runTest {
        // When
        val state = viewModel.homeState.first()

        // Then
        assertFalse(state.isLoading)
        assertTrue(state.libraries.isEmpty())
        assertTrue(state.recentlyAdded.isEmpty())
        assertTrue(state.recentlyAddedByTypes.isEmpty())
        assertNull(state.errorMessage)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun `loadLibraries updates state correctly on success`() = runTest {
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

        coEvery { mediaRepository.getUserLibraries() } returns ApiResult.Success(mockLibraries)

        // When
        viewModel.loadLibraries()

        // Then
        val state = viewModel.homeState.first()
        assertFalse(state.isLoading)
        assertEquals(2, state.libraries.size)
        assertEquals("Movies", state.libraries[0].name)
        assertEquals("TV Shows", state.libraries[1].name)
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadLibraries handles error correctly`() = runTest {
        // Given
        val errorMessage = "Failed to load libraries"
        coEvery { mediaRepository.getUserLibraries() } returns ApiResult.Error(
            errorMessage,
            errorType = ErrorType.NETWORK,
        )

        // When
        viewModel.loadLibraries()

        // Then
        val state = viewModel.homeState.first()
        assertFalse(state.isLoading)
        assertTrue(state.libraries.isEmpty())
        assertEquals(errorMessage, state.errorMessage)
    }

    @Test
    fun `loadRecentlyAdded updates state correctly on success`() = runTest {
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

        coEvery { mediaRepository.getRecentlyAdded(50) } returns ApiResult.Success(mockRecentItems)

        // When
        viewModel.loadRecentlyAdded()

        // Then
        val state = viewModel.homeState.first()
        assertFalse(state.isLoading)
        assertEquals(2, state.recentlyAdded.size)
        assertEquals("Recent Movie", state.recentlyAdded[0].name)
        assertEquals("Recent Episode", state.recentlyAdded[1].name)
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadRecentlyAddedByTypes loads content for each type`() = runTest {
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

        coEvery {
            mediaRepository.getRecentlyAddedByType(
                BaseItemKind.MOVIE,
                20,
            )
        } returns ApiResult.Success(mockMovies)
        coEvery {
            mediaRepository.getRecentlyAddedByType(
                BaseItemKind.SERIES,
                20,
            )
        } returns ApiResult.Success(mockSeries)
        coEvery {
            mediaRepository.getRecentlyAddedByType(
                BaseItemKind.AUDIO,
                20,
            )
        } returns ApiResult.Success(emptyList())

        // When
        viewModel.loadRecentlyAddedByTypes()

        // Then
        val state = viewModel.homeState.first()
        assertFalse(state.isLoading)

        val moviesList = state.recentlyAddedByTypes[BaseItemKind.MOVIE.name]
        val seriesList = state.recentlyAddedByTypes[BaseItemKind.SERIES.name]
        val musicList = state.recentlyAddedByTypes[BaseItemKind.AUDIO.name]

        assertEquals(1, moviesList?.size)
        assertEquals("Recent Movie", moviesList?.get(0)?.name)

        assertEquals(1, seriesList?.size)
        assertEquals("Recent Series", seriesList?.get(0)?.name)

        assertNull(musicList)
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadRecentlyAddedByTypes uses enum names as keys`() = runTest {
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

        coEvery {
            mediaRepository.getRecentlyAddedByType(
                BaseItemKind.MOVIE,
                20,
            )
        } returns ApiResult.Success(mockMovies)
        coEvery {
            mediaRepository.getRecentlyAddedByType(
                BaseItemKind.SERIES,
                20,
            )
        } returns ApiResult.Success(mockSeries)

        // When
        viewModel.loadRecentlyAddedByTypes()

        // Then
        val state = viewModel.homeState.first()
        assertEquals(
            setOf(BaseItemKind.MOVIE.name, BaseItemKind.SERIES.name),
            state.recentlyAddedByTypes.keys,
        )
    }

    @Test
    fun `refreshAll loads all data successfully`() = runTest {
        // Given
        val mockLibraries = listOf(
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Movies"
                coEvery { type } returns BaseItemKind.COLLECTION_FOLDER
            },
        )

        val mockRecentItems = listOf(
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Recent Movie"
                coEvery { type } returns BaseItemKind.MOVIE
            },
        )

        coEvery { mediaRepository.getUserLibraries() } returns ApiResult.Success(mockLibraries)
        coEvery { mediaRepository.getRecentlyAdded(50) } returns ApiResult.Success(mockRecentItems)

        // When
        viewModel.refreshAll()

        // Then
        val state = viewModel.homeState.first()
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertEquals(1, state.libraries.size)
        assertEquals(1, state.recentlyAdded.size)
        assertNull(state.errorMessage)
    }

    @Test
    fun `clearError removes error message`() = runTest {
        // Given - set an error first
        coEvery { mediaRepository.getUserLibraries() } returns ApiResult.Error(
            "Error",
            errorType = ErrorType.NETWORK,
        )
        viewModel.loadLibraries()

        // Verify error is set
        val stateWithError = viewModel.homeState.first()
        assertEquals("Error", stateWithError.errorMessage)

        // When
        viewModel.clearError()

        // Then
        val stateAfterClear = viewModel.homeState.first()
        assertNull(stateAfterClear.errorMessage)
    }

    @Test
    fun `loading state is managed correctly`() = runTest {
        // Given
        coEvery { mediaRepository.getUserLibraries() } returns ApiResult.Success(emptyList())

        // When
        viewModel.loadLibraries()

        // Then - loading should be false after completion
        val finalState = viewModel.homeState.first()
        assertFalse(finalState.isLoading)
    }

    @Test
    fun `refreshing state is managed correctly`() = runTest {
        // Given
        coEvery { mediaRepository.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery { mediaRepository.getRecentlyAdded(50) } returns ApiResult.Success(emptyList())

        // When
        viewModel.refreshAll()

        // Then - refreshing should be false after completion
        val finalState = viewModel.homeState.first()
        assertFalse(finalState.isRefreshing)
    }

    @Test
    fun `multiple errors are handled gracefully`() = runTest {
        // Given
        val libraryError = "Library error"
        val recentError = "Recent items error"

        coEvery { mediaRepository.getUserLibraries() } returns ApiResult.Error(
            libraryError,
            errorType = ErrorType.NETWORK,
        )
        coEvery { mediaRepository.getRecentlyAdded(50) } returns ApiResult.Error(
            recentError,
            errorType = ErrorType.SERVER_ERROR,
        )

        // When
        viewModel.loadLibraries()
        viewModel.loadRecentlyAdded()

        // Then - should show the most recent error
        val state = viewModel.homeState.first()
        assertEquals(recentError, state.errorMessage)
        assertTrue(state.libraries.isEmpty())
        assertTrue(state.recentlyAdded.isEmpty())
    }
}
