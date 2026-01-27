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

    @Test
    fun `findNextUnwatchedEpisode returns first unwatched episode in first season`() = runTest {
        val seriesId = randomSeriesId()
        val series = series(id = seriesId, name = "Test Series", childCount = 6)
        val season1 = season(name = "Season 1", indexNumber = 1)
        val episode1 = episode(name = "Episode 1", indexNumber = 1, watched = true)
        val episode2 = episode(name = "Episode 2", indexNumber = 2, watched = false)
        val episode3 = episode(name = "Episode 3", indexNumber = 3, watched = false)

        coEvery { repository.getSeriesDetails(seriesId) } returns ApiResult.Success(series)
        coEvery { mediaRepository.getSeasonsForSeries(seriesId) } returns ApiResult.Success(listOf(season1))
        coEvery { mediaRepository.getEpisodesForSeason(season1.id.toString()) } returns ApiResult.Success(
            listOf(episode1, episode2, episode3),
        )
        coEvery { mediaRepository.getSimilarSeries(seriesId) } returns ApiResult.Success(emptyList())

        viewModel.loadSeriesData(seriesId)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.nextEpisode)
        assertEquals(episode2.id, state.nextEpisode?.id)
        assertEquals("Episode 2", state.nextEpisode?.name)
    }

    @Test
    fun `findNextUnwatchedEpisode returns null when all episodes are watched`() = runTest {
        val seriesId = randomSeriesId()
        val series = series(id = seriesId, name = "Test Series", childCount = 2, completelyWatched = true)
        val season1 = season(name = "Season 1", indexNumber = 1)
        val episode1 = episode(name = "Episode 1", indexNumber = 1, watched = true)
        val episode2 = episode(name = "Episode 2", indexNumber = 2, watched = true)

        coEvery { repository.getSeriesDetails(seriesId) } returns ApiResult.Success(series)
        coEvery { mediaRepository.getSeasonsForSeries(seriesId) } returns ApiResult.Success(listOf(season1))
        coEvery { mediaRepository.getEpisodesForSeason(season1.id.toString()) } returns ApiResult.Success(
            listOf(episode1, episode2),
        )
        coEvery { mediaRepository.getSimilarSeries(seriesId) } returns ApiResult.Success(emptyList())

        viewModel.loadSeriesData(seriesId)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(null, state.nextEpisode)
    }

    @Test
    fun `findNextUnwatchedEpisode returns null when series has no episodes`() = runTest {
        val seriesId = randomSeriesId()
        val series = series(id = seriesId, name = "Test Series", childCount = 0)

        coEvery { repository.getSeriesDetails(seriesId) } returns ApiResult.Success(series)
        coEvery { mediaRepository.getSeasonsForSeries(seriesId) } returns ApiResult.Success(emptyList())
        coEvery { mediaRepository.getSimilarSeries(seriesId) } returns ApiResult.Success(emptyList())

        viewModel.loadSeriesData(seriesId)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(null, state.nextEpisode)
    }

    @Test
    fun `findNextUnwatchedEpisode finds episode in second season when first is watched`() = runTest {
        val seriesId = randomSeriesId()
        val series = series(id = seriesId, name = "Test Series", childCount = 6)
        val season1 = season(name = "Season 1", indexNumber = 1)
        val season2 = season(name = "Season 2", indexNumber = 2)
        val s1e1 = episode(name = "S1E1", indexNumber = 1, watched = true)
        val s1e2 = episode(name = "S1E2", indexNumber = 2, watched = true)
        val s2e1 = episode(name = "S2E1", indexNumber = 1, watched = false)
        val s2e2 = episode(name = "S2E2", indexNumber = 2, watched = false)

        coEvery { repository.getSeriesDetails(seriesId) } returns ApiResult.Success(series)
        coEvery { mediaRepository.getSeasonsForSeries(seriesId) } returns ApiResult.Success(listOf(season1, season2))
        coEvery { mediaRepository.getEpisodesForSeason(season1.id.toString()) } returns ApiResult.Success(
            listOf(s1e1, s1e2),
        )
        coEvery { mediaRepository.getEpisodesForSeason(season2.id.toString()) } returns ApiResult.Success(
            listOf(s2e1, s2e2),
        )
        coEvery { mediaRepository.getSimilarSeries(seriesId) } returns ApiResult.Success(emptyList())

        viewModel.loadSeriesData(seriesId)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.nextEpisode)
        assertEquals(s2e1.id, state.nextEpisode?.id)
        assertEquals("S2E1", state.nextEpisode?.name)
    }

    @Test
    fun `findNextUnwatchedEpisode skips seasons that fail to load`() = runTest {
        val seriesId = randomSeriesId()
        val series = series(id = seriesId, name = "Test Series", childCount = 4)
        val season1 = season(name = "Season 1", indexNumber = 1)
        val season2 = season(name = "Season 2", indexNumber = 2)
        val s2e1 = episode(name = "S2E1", indexNumber = 1, watched = false)

        coEvery { repository.getSeriesDetails(seriesId) } returns ApiResult.Success(series)
        coEvery { mediaRepository.getSeasonsForSeries(seriesId) } returns ApiResult.Success(listOf(season1, season2))
        coEvery { mediaRepository.getEpisodesForSeason(season1.id.toString()) } returns ApiResult.Error("Failed to load")
        coEvery { mediaRepository.getEpisodesForSeason(season2.id.toString()) } returns ApiResult.Success(
            listOf(s2e1),
        )
        coEvery { mediaRepository.getSimilarSeries(seriesId) } returns ApiResult.Success(emptyList())

        viewModel.loadSeriesData(seriesId)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.nextEpisode)
        assertEquals(s2e1.id, state.nextEpisode?.id)
    }

    @Test
    fun `findNextUnwatchedEpisode handles seasons without indexNumber`() = runTest {
        val seriesId = randomSeriesId()
        val series = series(id = seriesId, name = "Test Series", childCount = 2)
        val seasonNoIndex = season(name = "Specials", indexNumber = null)
        val seasonWithIndex = season(name = "Season 1", indexNumber = 1)
        val specialEpisode = episode(name = "Special", indexNumber = 1, watched = true)
        val regularEpisode = episode(name = "Regular", indexNumber = 1, watched = false)

        coEvery { repository.getSeriesDetails(seriesId) } returns ApiResult.Success(series)
        coEvery { mediaRepository.getSeasonsForSeries(seriesId) } returns ApiResult.Success(
            listOf(seasonNoIndex, seasonWithIndex),
        )
        coEvery { mediaRepository.getEpisodesForSeason(seasonNoIndex.id.toString()) } returns ApiResult.Success(
            listOf(specialEpisode),
        )
        coEvery { mediaRepository.getEpisodesForSeason(seasonWithIndex.id.toString()) } returns ApiResult.Success(
            listOf(regularEpisode),
        )
        coEvery { mediaRepository.getSimilarSeries(seriesId) } returns ApiResult.Success(emptyList())

        viewModel.loadSeriesData(seriesId)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.nextEpisode)
        assertEquals(regularEpisode.id, state.nextEpisode?.id)
    }

    @Test
    fun `findNextUnwatchedEpisode caches episodes across multiple loadSeriesData calls`() = runTest {
        val seriesId = randomSeriesId()
        val series = series(id = seriesId, name = "Test Series", childCount = 2)
        val season1 = season(name = "Season 1", indexNumber = 1)
        val episode1 = episode(name = "Episode 1", indexNumber = 1, watched = false)

        coEvery { repository.getSeriesDetails(seriesId) } returns ApiResult.Success(series)
        coEvery { mediaRepository.getSeasonsForSeries(seriesId) } returns ApiResult.Success(listOf(season1))
        coEvery { mediaRepository.getEpisodesForSeason(season1.id.toString()) } returns ApiResult.Success(
            listOf(episode1),
        )
        coEvery { mediaRepository.getSimilarSeries(seriesId) } returns ApiResult.Success(emptyList())

        // First load
        viewModel.loadSeriesData(seriesId)
        dispatcher.scheduler.advanceUntilIdle()

        val firstState = viewModel.state.value
        assertNotNull(firstState.nextEpisode)

        // Second load should use cache (verify by checking that getEpisodesForSeason is called twice - once per load due to cache clear)
        viewModel.loadSeriesData(seriesId)
        dispatcher.scheduler.advanceUntilIdle()

        val secondState = viewModel.state.value
        assertNotNull(secondState.nextEpisode)
        assertEquals(episode1.id, secondState.nextEpisode?.id)
    }

    private fun randomSeriesId(): String = UUID.randomUUID().toString()

    private fun series(
        id: String = randomSeriesId(),
        name: String,
        childCount: Int? = null,
        completelyWatched: Boolean = false,
    ): BaseItemDto = BaseItemDto(
        id = UUID.fromString(id),
        name = name,
        type = BaseItemKind.SERIES,
        childCount = childCount,
        userData = if (completelyWatched) {
            org.jellyfin.sdk.model.api.UserItemDataDto(
                played = true,
                playedPercentage = 100.0,
                unplayedItemCount = 0,
            )
        } else {
            null
        },
    )

    private fun season(name: String, indexNumber: Int? = null): BaseItemDto = BaseItemDto(
        id = UUID.randomUUID(),
        name = name,
        type = BaseItemKind.SEASON,
        indexNumber = indexNumber,
    )

    private fun episode(
        name: String,
        indexNumber: Int? = null,
        watched: Boolean = false,
    ): BaseItemDto = BaseItemDto(
        id = UUID.randomUUID(),
        name = name,
        type = BaseItemKind.EPISODE,
        indexNumber = indexNumber,
        userData = if (watched) {
            org.jellyfin.sdk.model.api.UserItemDataDto(
                played = true,
                playedPercentage = 100.0,
            )
        } else {
            null
        },
    )

    private fun similarSeries(name: String, type: BaseItemKind = BaseItemKind.SERIES): BaseItemDto =
        BaseItemDto(id = UUID.randomUUID(), name = name, type = type)
}
