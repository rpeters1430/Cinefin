package com.rpeters.jellyfin.ui.screens

import com.rpeters.jellyfin.data.preferences.LibraryActionsPreferences
import com.rpeters.jellyfin.testing.compose.ComposeScreenTest
import com.rpeters.jellyfin.testing.compose.SharedViewModelMocks
import com.rpeters.jellyfin.testing.compose.TestViewModels
import com.rpeters.jellyfin.testing.fixtures.TestItems
import com.rpeters.jellyfin.ui.player.PlaybackProgress
import com.rpeters.jellyfin.ui.viewmodel.AlbumDetailState
import com.rpeters.jellyfin.ui.viewmodel.AlbumDetailViewModel
import com.rpeters.jellyfin.ui.viewmodel.LibraryActionsPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.MainAppState
import com.rpeters.jellyfin.ui.viewmodel.PersonDetailUiState
import com.rpeters.jellyfin.ui.viewmodel.PersonDetailViewModel
import com.rpeters.jellyfin.ui.viewmodel.PlaylistDetailState
import com.rpeters.jellyfin.ui.viewmodel.PlaylistDetailViewModel
import com.rpeters.jellyfin.ui.viewmodel.SeasonEpisodesState
import com.rpeters.jellyfin.ui.viewmodel.SeasonEpisodesViewModel
import com.rpeters.jellyfin.ui.viewmodel.TVSeasonState
import com.rpeters.jellyfin.ui.viewmodel.TVSeasonViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test

private val testImageUrl: (org.jellyfin.sdk.model.api.BaseItemDto) -> String? = { "https://img.test/${it.id}" }

/** Renders the detail screens in their main states: loaded, loading, error and sparse data. */
class DetailScreensRenderTest : ComposeScreenTest() {

    @Before
    fun setUp() {
        SharedViewModelMocks.provideDefaults(
            main = SharedViewModelMocks.mainAppViewModel(
                MainAppState(libraries = TestItems.libraries(), continueWatching = TestItems.movies(3)),
            ),
            downloads = SharedViewModelMocks.downloadsViewModel(
                downloads = listOf(SharedViewModelMocks.sampleDownload(TestItems.episode())),
            ),
        )
    }

    // region Episode

    private fun renderEpisode(
        loading: Boolean = false,
        downloaded: Boolean = false,
        offline: Boolean = false,
        withAi: Boolean = true,
    ) = render {
        val episode = TestItems.episode()
        ImmersiveTVEpisodeDetailScreen(
            episode = episode,
            seriesInfo = TestItems.series(),
            seasonEpisodes = TestItems.episodes(6),
            getImageUrl = testImageUrl,
            getChapterImageUrl = { _, _ -> null },
            getBackdropUrl = { null },
            onBackClick = {},
            playbackProgress = PlaybackProgress(itemId = episode.id.toString(), positionMs = 60_000, durationMs = 2_640_000, percentageWatched = 0.2f),
            aiSummary = if (withAi && !loading) "An AI summary." else null,
            isLoadingAiSummary = loading,
            previouslyOn = if (withAi && !loading) "Previously, on The Test Series…" else null,
            isLoadingPreviouslyOn = loading,
            contentWarnings = if (withAi && !loading) listOf("Mild peril") else emptyList(),
            isLoadingContentWarnings = loading,
            aiChapterMarkers = if (withAi) TestItems.chapters() else emptyList(),
            isLoadingAiChapterMarkers = loading,
            isDownloaded = downloaded,
            isOffline = offline,
            chapters = TestItems.chapters(),
        )
    }

    @Test fun episode_loaded() = renderEpisode().also { assertRendered() }

    @Test fun episode_loadingAi() = renderEpisode(loading = true).also { assertRendered() }

    @Test fun episode_downloadedOffline() = renderEpisode(downloaded = true, offline = true, withAi = false).also { assertRendered() }

    // endregion

    // region Show and season (both read TVSeasonViewModel)

    private fun tvSeasonViewModel(state: TVSeasonState): TVSeasonViewModel = mockk(relaxed = true) {
        coEvery { this@mockk.state } returns MutableStateFlow(state)
    }

    private fun loadedShowState(): TVSeasonState {
        val seasons = (1..3).map { TestItems.season(seed = 200 + it, index = it) }
        return TVSeasonState(
            seriesDetails = TestItems.series(),
            seasons = seasons,
            episodesBySeasonId = mapOf(seasons.first().id.toString() to TestItems.episodes(5)),
            similarSeries = TestItems.seriesList(4),
            nextEpisode = TestItems.episode(index = 2),
            loadingSeasonIds = setOf(seasons[1].id.toString()),
            seasonEpisodeErrors = mapOf(seasons[2].id.toString() to "Could not load"),
            aiSummary = "Series summary.",
            whyYoullLoveThis = "You like tests.",
            isEpisodeNotificationsFollowed = true,
        )
    }

    private fun renderShow(state: TVSeasonState) {
        TestViewModels.provide(tvSeasonViewModel(state))
        render {
            ImmersiveTVShowDetailScreen(
                seriesId = TestItems.series().id.toString(),
                onBackClick = {},
                getImageUrl = testImageUrl,
                getBackdropUrl = { null },
                onSeriesClick = {},
                onEpisodeClick = {},
                onPlayEpisode = {},
            )
        }
    }

    @Test fun show_loaded() = renderShow(loadedShowState()).also { assertRendered() }

    @Test fun show_loading() = renderShow(TVSeasonState(isLoading = true)).also { assertRendered() }

    @Test fun show_error() = renderShow(TVSeasonState(errorMessage = "Server unreachable")).also { assertRendered() }

    @Test fun show_loadingAiAndSimilar() = renderShow(
        loadedShowState().copy(isLoadingAiSummary = true, isLoadingWhyYoullLoveThis = true, isSimilarSeriesLoading = true, aiSummary = null, isUpdatingEpisodeNotifications = true),
    ).also { assertRendered() }

    private fun renderSeason(state: TVSeasonState) {
        TestViewModels.provide(tvSeasonViewModel(state))
        render {
            ImmersiveTVSeasonScreen(
                seriesId = TestItems.series().id.toString(),
                onBackClick = {},
                getImageUrl = testImageUrl,
                getBackdropUrl = { null },
                onSeriesClick = {},
                onEpisodeClick = {},
                onPlayEpisode = {},
            )
        }
    }

    @Test fun season_loaded() = renderSeason(loadedShowState()).also { assertRendered() }

    @Test fun season_loading() = renderSeason(TVSeasonState(isLoading = true)).also { assertRendered() }

    @Test fun season_error() = renderSeason(TVSeasonState(errorMessage = "Nope")).also { assertRendered() }

    // endregion

    // region Home video

    private fun renderHomeVideo(refreshing: Boolean = false, progress: Boolean = true) = render {
        val item = TestItems.homeVideo()
        ImmersiveHomeVideoDetailScreen(
            item = item,
            getImageUrl = testImageUrl,
            getBackdropUrl = { null },
            onBackClick = {},
            playbackProgress = if (progress) PlaybackProgress(itemId = item.id.toString(), positionMs = 1_000, durationMs = 100_000, percentageWatched = 0.01f) else null,
            isRefreshing = refreshing,
        )
    }

    @Test fun homeVideo_loaded() = renderHomeVideo().also { assertRendered() }

    @Test fun homeVideo_refreshingNoProgress() = renderHomeVideo(refreshing = true, progress = false).also { assertRendered() }

    // endregion

    // region Album and playlist

    private fun renderAlbum(state: AlbumDetailState) {
        val vm: AlbumDetailViewModel = mockk(relaxed = true) { coEvery { this@mockk.state } returns MutableStateFlow(state) }
        TestViewModels.provide(vm)
        render { ImmersiveAlbumDetailScreen(albumId = TestItems.album().id.toString(), onBackClick = {}) }
    }

    @Test fun album_loaded() = renderAlbum(AlbumDetailState(album = TestItems.album(), tracks = TestItems.tracks(6))).also { assertRendered() }

    @Test fun album_loading() = renderAlbum(AlbumDetailState(isLoading = true)).also { assertRendered() }

    @Test fun album_error() = renderAlbum(AlbumDetailState(errorMessage = "Gone")).also { assertRendered() }

    private fun renderPlaylist(state: PlaylistDetailState) {
        val vm: PlaylistDetailViewModel = mockk(relaxed = true) { coEvery { this@mockk.state } returns MutableStateFlow(state) }
        TestViewModels.provide(vm)
        render { ImmersivePlaylistDetailScreen(playlistId = "playlist-1", onBackClick = {}, onVideoDetailClick = {}) }
    }

    @Test fun playlist_mixedItems() = renderPlaylist(
        PlaylistDetailState(
            playlist = TestItems.movie(seed = 900, name = "Weekend Mix").copy(type = org.jellyfin.sdk.model.api.BaseItemKind.PLAYLIST),
            items = TestItems.movies(3) + TestItems.episodes(2) + TestItems.tracks(2),
            isFavorite = true,
        ),
    ).also { assertRendered() }

    @Test fun playlist_youTube() = renderPlaylist(
        PlaylistDetailState(playlist = TestItems.movie(seed = 901, name = "Clips"), items = TestItems.movies(2), isYouTubePlaylist = true),
    ).also { assertRendered() }

    @Test fun playlist_loading() = renderPlaylist(PlaylistDetailState(isLoading = true)).also { assertRendered() }

    @Test fun playlist_error() = renderPlaylist(PlaylistDetailState(errorMessage = "Failed")).also { assertRendered() }

    // endregion

    // region Person

    private fun renderPerson(state: PersonDetailUiState) {
        val vm: PersonDetailViewModel = mockk(relaxed = true) { coEvery { uiState } returns MutableStateFlow(state) }
        TestViewModels.provide(vm)
        render { PersonDetailScreen(onBackClick = {}, onItemClick = {}, getImageUrl = { null }) }
    }

    @Test fun person_success() = renderPerson(
        PersonDetailUiState.Success(
            personId = TestItems.person().id.toString(),
            personName = "Ada Lovelace",
            movies = TestItems.movies(4),
            tvShows = TestItems.seriesList(3),
            allItems = TestItems.movies(4) + TestItems.seriesList(3),
            aiBio = "A short biography.",
        ),
    ).also { assertRendered() }

    @Test fun person_bioLoading() = renderPerson(
        PersonDetailUiState.Success("p", "Ada", emptyList(), TestItems.seriesList(1), TestItems.seriesList(1), isBioLoading = true),
    ).also { assertRendered() }

    @Test fun person_loading() = renderPerson(PersonDetailUiState.Loading).also { assertRendered() }

    @Test fun person_error() = renderPerson(PersonDetailUiState.Error("Not found")).also { assertRendered() }

    // endregion

    // region Season episodes list

    private fun renderEpisodes(state: SeasonEpisodesState, management: Boolean = true) {
        val vm: SeasonEpisodesViewModel = mockk(relaxed = true) { coEvery { this@mockk.state } returns MutableStateFlow(state) }
        val prefs: LibraryActionsPreferencesViewModel = mockk(relaxed = true) {
            coEvery { preferences } returns MutableStateFlow(LibraryActionsPreferences(enableManagementActions = management))
        }
        TestViewModels.provide(vm)
        TestViewModels.provide(prefs)
        render { TVEpisodesScreen(seasonId = TestItems.season().id.toString(), onBackClick = {}, getImageUrl = { null }) }
    }

    @Test fun episodes_loaded() = renderEpisodes(SeasonEpisodesState(episodes = TestItems.episodes(8), isSonarrConfigured = true, successMessage = "Requested")).also { assertRendered() }

    @Test fun episodes_loading() = renderEpisodes(SeasonEpisodesState(isLoading = true, isLoadingMissing = true), management = false).also { assertRendered() }

    @Test fun episodes_error() = renderEpisodes(SeasonEpisodesState(errorMessage = "Oops")).also { assertRendered() }

    @Test fun episodes_empty() = renderEpisodes(SeasonEpisodesState()).also { assertRendered() }

    // endregion
}
