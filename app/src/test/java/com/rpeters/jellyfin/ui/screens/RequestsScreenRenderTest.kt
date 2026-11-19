package com.rpeters.jellyfin.ui.screens

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import com.rpeters.jellyfin.data.model.RadarrQualityProfile
import com.rpeters.jellyfin.data.model.SeerrMediaInfo
import com.rpeters.jellyfin.data.model.SeerrMediaInfoSeason
import com.rpeters.jellyfin.data.model.SeerrMediaItem
import com.rpeters.jellyfin.data.model.SeerrRequest
import com.rpeters.jellyfin.data.model.SeerrRequestItem
import com.rpeters.jellyfin.data.model.SeerrRequestMedia
import com.rpeters.jellyfin.data.model.SeerrRequestedSeason
import com.rpeters.jellyfin.data.model.SonarrQualityProfile
import com.rpeters.jellyfin.data.preferences.SeerrPreferences
import com.rpeters.jellyfin.testing.compose.ComposeScreenTest
import com.rpeters.jellyfin.ui.viewmodel.PendingMovieRequest
import com.rpeters.jellyfin.ui.viewmodel.PendingTvRequest
import com.rpeters.jellyfin.ui.viewmodel.RequestHistoryItem
import com.rpeters.jellyfin.ui.viewmodel.RequestsUiState
import com.rpeters.jellyfin.ui.viewmodel.RequestsViewModel
import com.rpeters.jellyfin.ui.viewmodel.TvAvailability
import com.rpeters.jellyfin.ui.viewmodel.TvEpisodeAvailability
import com.rpeters.jellyfin.ui.viewmodel.TvSeasonAvailability
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

class RequestsScreenRenderTest : ComposeScreenTest() {

    private fun media(id: Int, type: String, status: Int?) = SeerrMediaItem(
        id = id,
        mediaType = type,
        tmdbId = id,
        tvdbId = if (type == "tv") id + 1000 else null,
        title = if (type == "movie") "Movie $id" else null,
        name = if (type == "tv") "Show $id" else null,
        overview = "Overview for $id",
        posterPath = "/p$id.jpg",
        backdropPath = "/b$id.jpg",
        releaseDate = if (type == "movie") "2026-0${(id % 9) + 1}-01" else null,
        firstAirDate = if (type == "tv") "2025-01-01" else null,
        mediaInfo = status?.let {
            SeerrMediaInfo(
                status = it,
                requests = listOf(SeerrRequest(id = id * 10, status = 1, createdAt = "2026-11-01")),
                seasons = listOf(SeerrMediaInfoSeason(seasonNumber = 1, status = 5), SeerrMediaInfoSeason(seasonNumber = 2, status = 2)),
            )
        },
    )

    private val movies = (1..5).map { media(it, "movie", listOf(null, 1, 2, 3, 5)[it - 1]) }
    private val shows = (11..15).map { media(it, "tv", listOf(4, null, 2, 5, 3)[it - 11]) }

    private val availability = TvAvailability(
        localSeriesTitle = "Show 11",
        tvdbId = 1011,
        seasons = (1..3).map { s ->
            TvSeasonAvailability(
                seasonNumber = s,
                title = "Season $s",
                availableCount = if (s == 1) 10 else 3,
                totalCount = 10,
                isRequestableInSeerr = s != 1,
                isPendingRequest = s == 3,
                episodes = (1..4).map { e -> TvEpisodeAvailability(s, e, "Episode $e", isAvailable = e % 2 == 0) },
            )
        },
    )

    private fun history() = listOf(1, 2, 3).map { status ->
        RequestHistoryItem(
            request = SeerrRequestItem(
                id = 100 + status,
                status = status,
                type = if (status == 2) "tv" else "movie",
                is4k = status == 3,
                createdAt = "2026-11-0${status}T10:00:00Z",
                media = SeerrRequestMedia(id = status, tmdbId = status, mediaType = if (status == 2) "tv" else "movie", status = status + 2),
                seasons = if (status == 2) listOf(SeerrRequestedSeason(1, 2)) else emptyList(),
            ),
            title = "Requested $status",
            posterPath = "/r$status.jpg",
            isLoadingDetails = status == 3,
        )
    }

    private fun loadedState() = RequestsUiState(
        trending = movies + shows,
        upcomingMovies = movies,
        upcomingTv = shows,
        popularMovies = movies.reversed(),
        popularTv = shows.reversed(),
        isConfigured = true,
        isPluginConfigured = true,
        pluginCapabilities = listOf("sonarr", "radarr", "seerr"),
        isSonarrConfigured = true,
        isRadarrConfigured = true,
        tvAvailabilityByMediaId = mapOf(11 to availability),
        checkedTvItemIds = setOf(11),
        loadingAvailabilityIds = setOf(13),
        recentSearches = listOf("dune", "severance"),
        myRequests = history(),
        myRequestsLoaded = true,
        successMessage = "Request sent",
        currentPage = 1,
        totalPages = 3,
    )

    private fun renderRequests(state: RequestsUiState, configured: Boolean = true) {
        val vm: RequestsViewModel = mockk(relaxed = true) {
            every { uiState } returns MutableStateFlow(state)
            every { seerrPreferences } returns MutableStateFlow(
                if (configured) SeerrPreferences(baseUrl = "https://seerr.test", apiKey = "k", isEnabled = true) else SeerrPreferences.DEFAULT,
            )
        }
        render { RequestsScreen(onNavigateToSettings = {}, viewModel = vm) }
    }

    @Test
    fun discover_loaded() {
        renderRequests(loadedState())
        assertRendered()
    }

    @Test
    fun discover_openDetailForTvWithAvailability() {
        renderRequests(loadedState())
        compose.onAllNodesWithText("Show 11").onFirst().performClick()
        compose.waitForIdle()
        assertRendered()
    }

    @Test
    fun discover_openDetailForMovie() {
        renderRequests(loadedState())
        compose.onAllNodesWithText("Movie 1").onFirst().performClick()
        compose.waitForIdle()
        assertRendered()
    }

    @Test
    fun status_tab() {
        renderRequests(loadedState())
        compose.onAllNodesWithText("Status").onFirst().performClick()
        compose.waitForIdle()
        assertRendered()
    }

    @Test
    fun status_tabLoadingAndCanceling() {
        renderRequests(loadedState().copy(isLoadingMyRequests = true, cancelingRequestId = 101, myRequestsLoaded = false))
        compose.onAllNodesWithText("Status").onFirst().performClick()
        compose.waitForIdle()
        assertRendered()
    }

    @Test
    fun searchResults() {
        renderRequests(loadedState().copy(query = "dune", results = movies + shows, isLoadingMore = true, requestingMediaId = 2))
        assertRendered()
    }

    @Test
    fun loadingAndError() {
        renderRequests(RequestsUiState(isConfigured = true, isLoading = true, errorMessage = "Seerr unreachable"))
        assertRendered()
    }

    @Test
    fun notConfigured() {
        renderRequests(RequestsUiState(isConfigured = false), configured = false)
        assertRendered()
    }

    @Test
    fun pendingMovieRequestDialog() {
        renderRequests(
            loadedState().copy(
                pendingMovieRequest = PendingMovieRequest(movies.first(), listOf(RadarrQualityProfile(1, "HD-1080p"), RadarrQualityProfile(2, "Ultra-HD")), isUsingRadarrDirect = true),
            ),
        )
        assertRendered()
    }

    @Test
    fun pendingTvRequestDialog() {
        renderRequests(
            loadedState().copy(
                pendingTvRequest = PendingTvRequest(shows.first(), listOf(1, 2, 3), listOf(SonarrQualityProfile(1, "HD"), SonarrQualityProfile(2, "Any")), isUsingSonarrDirect = true),
            ),
        )
        assertRendered()
    }

    @Test
    fun pendingRequestsViaSeerr() {
        renderRequests(
            loadedState().copy(
                pendingMovieRequest = PendingMovieRequest(movies[1], emptyList(), isUsingRadarrDirect = false),
            ),
        )
        assertRendered()
    }

    @Test
    fun pendingTvViaSeerr() {
        renderRequests(
            loadedState().copy(
                pendingTvRequest = PendingTvRequest(shows[1], listOf(1), emptyList(), isUsingSonarrDirect = false),
            ),
        )
        assertRendered()
    }
}
