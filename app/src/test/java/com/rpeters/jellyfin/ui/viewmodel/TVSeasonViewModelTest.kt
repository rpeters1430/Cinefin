package com.rpeters.jellyfin.ui.viewmodel

import app.cash.turbine.test
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class TVSeasonViewModelTest {
    private val repository: JellyfinRepository = mockk()
    private val mediaRepository: JellyfinMediaRepository = mockk()
    private val dispatcher = StandardTestDispatcher()
    private val viewModel by lazy { TVSeasonViewModel(repository, mediaRepository) }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadSeriesData emits loading states and final data`() = runTest {
        val seriesId = randomSeriesId()
        val series = series(id = seriesId, name = "Series")
        val season = season(name = "Season 1")
        val similar = similarSeries(name = "Similar")

        coEvery { repository.getSeriesDetails(seriesId) } returns ApiResult.Success(series)
        coEvery { mediaRepository.getSeasonsForSeries(seriesId) } returns ApiResult.Success(listOf(season))
        coEvery { mediaRepository.getSimilarSeries(seriesId) } returns ApiResult.Success(listOf(similar))

        viewModel.state.test {
            assertEquals(TVSeasonState(), awaitItem())

            viewModel.loadSeriesData(seriesId)
            dispatcher.scheduler.advanceUntilIdle()

            val loadingState = awaitItem()
            assertTrue(loadingState.isLoading)
            assertFalse(loadingState.isSimilarSeriesLoading)

            val similarLoadingState = awaitItem()
            assertTrue(similarLoadingState.isLoading)
            assertTrue(similarLoadingState.isSimilarSeriesLoading)

            val similarFinishedState = awaitItem()
            assertTrue(similarFinishedState.isLoading)
            assertFalse(similarFinishedState.isSimilarSeriesLoading)

            val finalState = awaitItem()
            assertFalse(finalState.isLoading)
            assertFalse(finalState.isSimilarSeriesLoading)
            assertEquals(series.id, finalState.seriesDetails?.id)
            assertEquals(listOf(season), finalState.seasons)
            assertEquals(listOf(similar), finalState.similarSeries)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadSeriesData sets error when repository call fails`() = runTest {
        val seriesId = randomSeriesId()
        coEvery { repository.getSeriesDetails(seriesId) } returns ApiResult.Error("failed to load series")
        coEvery { mediaRepository.getSeasonsForSeries(seriesId) } returns ApiResult.Success(emptyList())
        coEvery { mediaRepository.getSimilarSeries(seriesId) } returns ApiResult.Success(emptyList())

        viewModel.loadSeriesData(seriesId)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
    }

    @Test
    fun `loadSeriesData filters similar series entries`() = runTest {
        val seriesId = randomSeriesId()
        val series = series(id = seriesId, name = "Main Series")
        val validSimilar = similarSeries(name = "Valid")
        val anotherSeries = similarSeries(name = "Another")
        val unrelatedMovie = similarSeries(name = "Movie", type = BaseItemKind.MOVIE)

        coEvery { repository.getSeriesDetails(seriesId) } returns ApiResult.Success(series)
        coEvery { mediaRepository.getSeasonsForSeries(seriesId) } returns ApiResult.Success(emptyList())
        coEvery { mediaRepository.getSimilarSeries(seriesId) } returns ApiResult.Success(
            listOf(validSimilar, anotherSeries.copy(type = BaseItemKind.MOVIE), series, unrelatedMovie),
        )

        viewModel.loadSeriesData(seriesId)
        dispatcher.scheduler.advanceUntilIdle()

        val similarSeries = viewModel.state.value.similarSeries
        assertEquals(listOf(validSimilar), similarSeries)
    }

    private fun randomSeriesId(): String = UUID.randomUUID().toString()

    private fun series(id: String = randomSeriesId(), name: String): BaseItemDto =
        BaseItemDto(id = UUID.fromString(id), name = name, type = BaseItemKind.SERIES)

    private fun season(name: String): BaseItemDto = BaseItemDto(
        id = UUID.randomUUID(),
        name = name,
        type = BaseItemKind.SEASON,
    )

    private fun similarSeries(name: String, type: BaseItemKind = BaseItemKind.SERIES): BaseItemDto =
        BaseItemDto(id = UUID.randomUUID(), name = name, type = type)
}
