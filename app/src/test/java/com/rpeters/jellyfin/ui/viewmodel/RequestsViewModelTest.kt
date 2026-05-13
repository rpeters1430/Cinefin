package com.rpeters.jellyfin.ui.viewmodel

import com.rpeters.jellyfin.data.model.CinefinPluginInfoResponse
import com.rpeters.jellyfin.data.model.CinefinPluginRequestResponse
import com.rpeters.jellyfin.data.model.SeerrMediaItem
import com.rpeters.jellyfin.data.model.SeerrSearchResult
import com.rpeters.jellyfin.data.preferences.SeerrPreferences
import com.rpeters.jellyfin.data.preferences.SeerrPreferencesRepository
import com.rpeters.jellyfin.data.repository.CinefinPluginRepository
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.JellyfinSearchRepository
import com.rpeters.jellyfin.data.repository.SeerrRepository
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RequestsViewModelTest {

    @MockK(relaxed = true)
    private lateinit var seerrRepository: SeerrRepository

    @MockK(relaxed = true)
    private lateinit var jellyfinSearchRepository: JellyfinSearchRepository

    @MockK(relaxed = true)
    private lateinit var jellyfinMediaRepository: JellyfinMediaRepository

    @MockK(relaxed = true)
    private lateinit var preferencesRepository: SeerrPreferencesRepository

    @MockK(relaxed = true)
    private lateinit var cinefinPluginRepository: CinefinPluginRepository

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: RequestsViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(testDispatcher)

        coEvery { preferencesRepository.seerrPreferencesFlow } returns MutableStateFlow(SeerrPreferences.DEFAULT)
        coEvery { seerrRepository.getTrending(any()) } returns ApiResult.Success(
            SeerrSearchResult(page = 1, totalPages = 1, totalResults = 0, results = emptyList()),
        )
        coEvery { cinefinPluginRepository.getPluginInfo() } returns ApiResult.Success(
            CinefinPluginInfoResponse(version = "1.0.0", capabilities = emptyList(), isConfigured = true),
        )

        viewModel = RequestsViewModel(
            seerrRepository = seerrRepository,
            jellyfinSearchRepository = jellyfinSearchRepository,
            jellyfinMediaRepository = jellyfinMediaRepository,
            preferencesRepository = preferencesRepository,
            cinefinPluginRepository = cinefinPluginRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun requestSeason_pluginConfiguredWithTmdbId_forwardsSeasonListAndShowsSuccess() = runTest(testDispatcher) {
        val item = SeerrMediaItem(
            id = 101,
            mediaType = "tv",
            tmdbId = 202,
            tvdbId = 303,
            name = "Test Show",
        )
        coEvery { cinefinPluginRepository.requestMedia("202", "tv", listOf(2)) } returns ApiResult.Success(
            CinefinPluginRequestResponse(success = true, message = "ok"),
        )

        advanceUntilIdle()
        viewModel.requestSeason(item, 2)
        advanceUntilIdle()

        coVerify(exactly = 1) { cinefinPluginRepository.requestMedia("202", "tv", listOf(2)) }
        assertEquals("Request submitted for Test Show via Cinefin Plugin", viewModel.uiState.value.successMessage)
    }

    @Test
    fun requestSeason_pluginReturnsSuccessFalse_showsErrorMessage() = runTest(testDispatcher) {
        val item = SeerrMediaItem(
            id = 101,
            mediaType = "tv",
            tmdbId = 202,
            name = "Test Show",
        )
        coEvery { cinefinPluginRepository.requestMedia("202", "tv", listOf(2)) } returns ApiResult.Success(
            CinefinPluginRequestResponse(success = false, message = "Plugin rejected request"),
        )

        advanceUntilIdle()
        viewModel.requestSeason(item, 2)
        advanceUntilIdle()

        assertEquals("Plugin rejected request", viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.successMessage)
    }
}
