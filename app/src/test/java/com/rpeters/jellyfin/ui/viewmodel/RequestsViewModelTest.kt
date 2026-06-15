package com.rpeters.jellyfin.ui.viewmodel

import com.rpeters.jellyfin.data.model.CinefinPluginInfoResponse
import com.rpeters.jellyfin.data.model.SeerrExternalIds
import com.rpeters.jellyfin.data.model.SeerrEpisode
import com.rpeters.jellyfin.data.model.SeerrMediaItem
import com.rpeters.jellyfin.data.model.SeerrSeason
import com.rpeters.jellyfin.data.model.SeerrSearchResult
import com.rpeters.jellyfin.data.model.SeerrTvDetails
import com.rpeters.jellyfin.data.preferences.SeerrPreferences
import com.rpeters.jellyfin.data.preferences.SeerrPreferencesRepository
import com.rpeters.jellyfin.data.repository.CinefinPluginRepository
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.JellyfinSearchRepository
import com.rpeters.jellyfin.data.repository.SeerrRepository
import com.rpeters.jellyfin.data.repository.SonarrRepository
import com.rpeters.jellyfin.data.repository.RadarrRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class RequestsViewModelTest {

    @MockK(relaxed = true)
    private lateinit var seerrRepository: SeerrRepository

    @MockK(relaxed = true)
    private lateinit var sonarrRepository: SonarrRepository

    @MockK(relaxed = true)
    private lateinit var radarrRepository: RadarrRepository

    @MockK(relaxed = true)
    private lateinit var jellyfinSearchRepository: JellyfinSearchRepository

    @MockK(relaxed = true)
    private lateinit var jellyfinMediaRepository: JellyfinMediaRepository

    @MockK(relaxed = true)
    private lateinit var preferencesRepository: SeerrPreferencesRepository

    @MockK(relaxed = true)
    private lateinit var arrPreferencesRepository: com.rpeters.jellyfin.data.preferences.ArrPreferencesRepository

    @MockK(relaxed = true)
    private lateinit var cinefinPluginRepository: CinefinPluginRepository

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: RequestsViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(testDispatcher)

        coEvery { preferencesRepository.seerrPreferencesFlow } returns MutableStateFlow(SeerrPreferences.DEFAULT)
        coEvery { arrPreferencesRepository.sonarrPreferencesFlow } returns MutableStateFlow(com.rpeters.jellyfin.data.preferences.SonarrPreferences.DEFAULT)
        coEvery { arrPreferencesRepository.radarrPreferencesFlow } returns MutableStateFlow(com.rpeters.jellyfin.data.preferences.RadarrPreferences.DEFAULT)
        coEvery { seerrRepository.getTrending(any()) } returns ApiResult.Success(
            SeerrSearchResult(page = 1, totalPages = 1, totalResults = 0, results = emptyList()),
        )
        coEvery { cinefinPluginRepository.getPluginInfo() } returns ApiResult.Success(
            CinefinPluginInfoResponse(version = "1.0.0", capabilities = emptyList(), isConfigured = true),
        )
        coEvery { cinefinPluginRepository.getCredentials() } returns ApiResult.Error("Plugin not configured")

        viewModel = RequestsViewModel(
            seerrRepository = seerrRepository,
            sonarrRepository = sonarrRepository,
            radarrRepository = radarrRepository,
            jellyfinSearchRepository = jellyfinSearchRepository,
            jellyfinMediaRepository = jellyfinMediaRepository,
            preferencesRepository = preferencesRepository,
            arrPreferencesRepository = arrPreferencesRepository,
            cinefinPluginRepository = cinefinPluginRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun requestSeason_seerrNotConfigured_requestsSonarrDirectlyByTvdbId() = runTest(testDispatcher) {
        val item = SeerrMediaItem(
            id = 101,
            mediaType = "tv",
            tmdbId = 202,
            tvdbId = 303,
            name = "Test Show",
        )
        coEvery { sonarrRepository.addSeries(303, listOf(2)) } returns ApiResult.Success(Unit)

        advanceUntilIdle()
        viewModel.requestSeason(item, 2)
        advanceUntilIdle()

        coVerify(exactly = 1) { sonarrRepository.addSeries(303, listOf(2)) }
        assertEquals("Request submitted for Test Show", viewModel.uiState.value.successMessage)
        coVerify(exactly = 0) { cinefinPluginRepository.requestMedia(any(), any(), any()) }
    }

    @Test
    fun requestSeason_noTvdbIdAndSeerrNotConfigured_showsErrorMessage() = runTest(testDispatcher) {
        val item = SeerrMediaItem(
            id = 101,
            mediaType = "tv",
            tmdbId = 202,
            name = "Test Show",
        )

        advanceUntilIdle()
        viewModel.requestSeason(item, 2)
        advanceUntilIdle()

        assertEquals(
            "No request service configured. Enable Seerr or Sonarr in Settings → Media Requests.",
            viewModel.uiState.value.errorMessage,
        )
        assertNull(viewModel.uiState.value.successMessage)
    }

    @Test
    fun confirmTvRequest_tvdbMissing_fetchesFromSeerrDetailsAndRequestsSonarr() = runTest(testDispatcher) {
        val item = SeerrMediaItem(
            id = 101,
            mediaType = "tv",
            tmdbId = 202,
            tvdbId = null,
            name = "Test Show",
        )
        coEvery { seerrRepository.getTvDetails(202) } returns ApiResult.Success(
            SeerrTvDetails(externalIds = SeerrExternalIds(tvdbId = 303)),
        )
        coEvery { sonarrRepository.addSeries(303, listOf(2), 7) } returns ApiResult.Success(Unit)

        advanceUntilIdle()
        viewModel.confirmTvRequest(item, listOf(2), 7)
        advanceUntilIdle()

        coVerify(exactly = 1) { seerrRepository.getTvDetails(202) }
        coVerify(exactly = 1) { sonarrRepository.addSeries(303, listOf(2), 7) }
        assertEquals("Request submitted for Test Show", viewModel.uiState.value.successMessage)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun checkTvAvailability_ignoresFutureUnairedEpisodesWhenCalculatingMissing() = runTest(testDispatcher) {
        val item = SeerrMediaItem(
            id = 101,
            mediaType = "tv",
            tmdbId = 202,
            name = "Test Show",
        )
        val seriesId = UUID.randomUUID()
        val seasonId = UUID.randomUUID()
        val series = BaseItemDto(
            id = seriesId,
            name = "Test Show",
            type = BaseItemKind.SERIES,
        )
        val localSeason = BaseItemDto(
            id = seasonId,
            name = "Season 1",
            type = BaseItemKind.SEASON,
            indexNumber = 1,
        )
        val localEpisodes = (1..5).map { episodeNumber ->
            BaseItemDto(
                id = UUID.randomUUID(),
                name = "Episode $episodeNumber",
                type = BaseItemKind.EPISODE,
                indexNumber = episodeNumber,
            )
        }
        val seerrSeason = SeerrSeason(
            seasonNumber = 1,
            episodeCount = 6,
            episodes = (1..5).map { episodeNumber ->
                SeerrEpisode(
                    episodeNumber = episodeNumber,
                    name = "Episode $episodeNumber",
                    airDate = "2020-01-0$episodeNumber",
                )
            } + SeerrEpisode(
                episodeNumber = 6,
                name = "Episode 6",
                airDate = "2099-01-01",
            ),
        )

        coEvery { jellyfinSearchRepository.searchTVShows("Test Show", limit = 10) } returns ApiResult.Success(listOf(series))
        coEvery { jellyfinMediaRepository.getSeasonsForSeries(seriesId.toString()) } returns ApiResult.Success(listOf(localSeason))
        coEvery { jellyfinMediaRepository.getEpisodesForSeason(seasonId.toString()) } returns ApiResult.Success(localEpisodes)
        coEvery { seerrRepository.getTvDetails(202) } returns ApiResult.Success(
            SeerrTvDetails(seasons = listOf(SeerrSeason(seasonNumber = 1))),
        )
        coEvery { seerrRepository.getTvSeasonDetails(202, 1) } returns ApiResult.Success(seerrSeason)

        advanceUntilIdle()
        viewModel.checkTvAvailability(item)
        advanceUntilIdle()

        val season = viewModel.uiState.value.tvAvailabilityByMediaId
            .getValue(item.id)
            .seasons
            .single()

        assertEquals(5, season.totalCount)
        assertEquals(5, season.availableCount)
        assertEquals(0, season.missingCount)
        assertFalse(season.hasMissingEpisodes)
        assertEquals(listOf(1, 2, 3, 4, 5), season.episodes.map { it.episodeNumber })
    }
}
