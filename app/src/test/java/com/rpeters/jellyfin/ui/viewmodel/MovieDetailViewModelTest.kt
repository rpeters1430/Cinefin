package com.rpeters.jellyfin.ui.viewmodel

import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.ui.utils.EnhancedPlaybackUtils
import io.mockk.coEvery
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailViewModelTest {
    private val repository: JellyfinRepository = mockk()
    private val playbackUtils: EnhancedPlaybackUtils = mockk()
    private val dispatcher = StandardTestDispatcher()
    private val viewModel by lazy { MovieDetailViewModel(repository, playbackUtils) }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadMovieDetails updates state on success`() = runTest {
        val movie = BaseItemDto(id = UUID.randomUUID(), name = "Test", type = BaseItemKind.MOVIE)
        coEvery { repository.getMovieDetails(movie.id.toString()) } returns ApiResult.Success(movie)

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
}
