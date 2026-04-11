package com.rpeters.jellyfin.ui.viewmodel

import com.rpeters.jellyfin.data.repository.IJellyfinRepository
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.network.ConnectivityChecker
import com.rpeters.jellyfin.ui.utils.EnhancedPlaybackUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailViewModelTest {
    private val repository: IJellyfinRepository = mockk()
    private val mediaRepository: JellyfinMediaRepository = mockk()
    private val playbackUtils: EnhancedPlaybackUtils = mockk()
    private val generativeAiRepository: com.rpeters.jellyfin.data.repository.GenerativeAiRepository = mockk(relaxed = true)
    private val offlineDownloadManager: com.rpeters.jellyfin.data.offline.OfflineDownloadManager = mockk(relaxed = true)
    private val connectivityChecker: ConnectivityChecker = mockk()
    private val playbackProgressManager: com.rpeters.jellyfin.ui.player.PlaybackProgressManager = mockk(relaxed = true)
    private val analyticsHelper: com.rpeters.jellyfin.utils.AnalyticsHelper = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()
    private val viewModel by lazy {
        MovieDetailViewModel(
            repository,
            mediaRepository,
            playbackUtils,
            generativeAiRepository,
            offlineDownloadManager,
            connectivityChecker,
            playbackProgressManager,
            analyticsHelper,
        )
    }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { connectivityChecker.observeNetworkConnectivity() } returns flowOf(true)
        every { playbackProgressManager.playbackProgress } returns MutableStateFlow(
            com.rpeters.jellyfin.ui.player.PlaybackProgress(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadMovieDetails updates state on success`() = runTest {
        val movie = BaseItemDto(id = UUID.randomUUID(), name = "Test", type = BaseItemKind.MOVIE)
        coEvery { repository.getMovieDetails(movie.id.toString()) } returns ApiResult.Success(movie)
        coEvery { playbackUtils.analyzePlaybackCapabilities(movie) } returns mockk()
        coEvery { mediaRepository.getSimilarMovies(movie.id.toString(), limit = 10) } returns ApiResult.Success(emptyList())

        viewModel.loadMovieDetails(movie.id.toString())
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(movie.id, state.movie?.id)
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadMovieDetails sets error on failure`() = runTest {
        coEvery { repository.getMovieDetails("123") } returns ApiResult.Error("failed")

        viewModel.loadMovieDetails("123")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
        assertNull(state.movie)
    }

    @Test
    fun `loadMovieDetails clears why youll love this while reloading a different movie`() = runTest {
        val firstMovie = BaseItemDto(id = UUID.randomUUID(), name = "First", type = BaseItemKind.MOVIE)
        val secondMovie = BaseItemDto(id = UUID.randomUUID(), name = "Second", type = BaseItemKind.MOVIE)
        val history = listOf(BaseItemDto(id = UUID.randomUUID(), name = "History", type = BaseItemKind.MOVIE))

        coEvery { repository.getMovieDetails(firstMovie.id.toString()) } returns ApiResult.Success(firstMovie)
        coEvery { repository.getMovieDetails(secondMovie.id.toString()) } returns ApiResult.Success(secondMovie)
        coEvery { playbackUtils.analyzePlaybackCapabilities(firstMovie) } returns mockk()
        coEvery { playbackUtils.analyzePlaybackCapabilities(secondMovie) } returns mockk()
        coEvery { mediaRepository.getSimilarMovies(any(), limit = 10) } returns ApiResult.Success(emptyList())
        coEvery { mediaRepository.getContinueWatching(limit = 20) } returns ApiResult.Success(history)
        coEvery { generativeAiRepository.generateWhyYoullLoveThis(firstMovie, history) } returns "First pitch"
        coEvery { generativeAiRepository.generateWhyYoullLoveThis(secondMovie, history) } returns "Second pitch"

        viewModel.loadMovieDetails(firstMovie.id.toString())
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("First pitch", viewModel.state.value.whyYoullLoveThis)

        viewModel.loadMovieDetails(secondMovie.id.toString())
        runCurrent()

        val reloadingState = viewModel.state.value
        assertTrue(reloadingState.isLoading)
        assertNull(reloadingState.whyYoullLoveThis)
        assertFalse(reloadingState.isLoadingWhyYoullLoveThis)

        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Second pitch", viewModel.state.value.whyYoullLoveThis)
    }

    @Test
    fun `generateWhyYoullLoveThis stores null when ai returns blank`() = runTest {
        val movie = BaseItemDto(id = UUID.randomUUID(), name = "Test", type = BaseItemKind.MOVIE)
        val history = listOf(BaseItemDto(id = UUID.randomUUID(), name = "History", type = BaseItemKind.MOVIE))

        coEvery { repository.getMovieDetails(movie.id.toString()) } returns ApiResult.Success(movie)
        coEvery { playbackUtils.analyzePlaybackCapabilities(movie) } returns mockk()
        coEvery { mediaRepository.getSimilarMovies(movie.id.toString(), limit = 10) } returns ApiResult.Success(emptyList())
        coEvery { mediaRepository.getContinueWatching(limit = 20) } returns ApiResult.Success(history)
        coEvery { generativeAiRepository.generateWhyYoullLoveThis(movie, history) } returns "   "

        viewModel.loadMovieDetails(movie.id.toString())
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.whyYoullLoveThis)
        assertFalse(viewModel.state.value.isLoadingWhyYoullLoveThis)
    }
}
