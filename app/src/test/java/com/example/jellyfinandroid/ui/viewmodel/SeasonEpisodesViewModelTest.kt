package com.example.jellyfinandroid.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.jellyfinandroid.data.repository.JellyfinRepository
import com.example.jellyfinandroid.data.repository.common.ApiResult
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeasonEpisodesViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var repository: JellyfinRepository

    private lateinit var viewModel: SeasonEpisodesViewModel
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(dispatcher)
        viewModel = SeasonEpisodesViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() = runTest {
        val state = viewModel.state.first()
        assertTrue(state.episodes.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadEpisodes updates state on success`() = runTest {
        val seasonId = "season1"
        val episodes = listOf(mockk<BaseItemDto>())
        coEvery { repository.getEpisodesForSeason(seasonId) } returns ApiResult.Success(episodes)

        viewModel.loadEpisodes(seasonId)

        val state = viewModel.state.first()
        assertEquals(episodes, state.episodes)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadEpisodes handles error`() = runTest {
        val seasonId = "season1"
        coEvery { repository.getEpisodesForSeason(seasonId) } returns ApiResult.Error("error")

        viewModel.loadEpisodes(seasonId)

        val state = viewModel.state.first()
        assertTrue(state.episodes.isEmpty())
        assertFalse(state.isLoading)
        assertEquals("Failed to load episodes: error", state.errorMessage)
    }

    @Test
    fun `refresh reloads current season`() = runTest {
        val seasonId = "season1"
        coEvery { repository.getEpisodesForSeason(seasonId) } returns ApiResult.Success(emptyList())

        viewModel.loadEpisodes(seasonId)
        viewModel.refresh()

        coVerify(exactly = 2) { repository.getEpisodesForSeason(seasonId) }
    }
}
