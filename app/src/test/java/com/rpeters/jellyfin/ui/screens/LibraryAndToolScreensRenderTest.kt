package com.rpeters.jellyfin.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.offline.DownloadStatus
import com.rpeters.jellyfin.testing.compose.ComposeScreenTest
import com.rpeters.jellyfin.testing.compose.SharedViewModelMocks
import com.rpeters.jellyfin.testing.compose.TestViewModels
import com.rpeters.jellyfin.testing.compose.loadedMainAppState
import com.rpeters.jellyfin.testing.fixtures.TestItems
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.screens.home.EnhancedContentCarousel
import com.rpeters.jellyfin.ui.screens.home.HomeCarousel
import com.rpeters.jellyfin.ui.screens.home.HomeContent
import com.rpeters.jellyfin.ui.screens.settings.PrivacyPolicyScreen
import com.rpeters.jellyfin.ui.screens.settings.SettingsSectionScreen
import com.rpeters.jellyfin.ui.utils.PlaybackBreakdownItem
import com.rpeters.jellyfin.ui.utils.PlaybackCapabilityAnalysis
import com.rpeters.jellyfin.ui.utils.PlaybackMethod
import com.rpeters.jellyfin.ui.utils.PlaybackRecommendation
import com.rpeters.jellyfin.ui.utils.RecommendationType
import com.rpeters.jellyfin.ui.viewmodel.AiAssistantState
import com.rpeters.jellyfin.ui.viewmodel.AiAssistantViewModel
import com.rpeters.jellyfin.ui.viewmodel.AiBackendUiState
import com.rpeters.jellyfin.ui.viewmodel.AiDiagnosticsViewModel
import com.rpeters.jellyfin.ui.viewmodel.AiMessage
import com.rpeters.jellyfin.ui.viewmodel.ArtistAlbumsState
import com.rpeters.jellyfin.ui.viewmodel.ArtistAlbumsViewModel
import com.rpeters.jellyfin.ui.viewmodel.MainAppState
import com.rpeters.jellyfin.ui.viewmodel.PlaybackRecommendationViewModel
import com.rpeters.jellyfin.ui.viewmodel.PlaylistsState
import com.rpeters.jellyfin.ui.viewmodel.PlaylistsViewModel
import com.rpeters.jellyfin.ui.viewmodel.TranscodingDiagnosticsViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Test

class LibraryAndToolScreensRenderTest : ComposeScreenTest() {

    private val analysis = PlaybackCapabilityAnalysis(
        canPlay = true,
        preferredMethod = PlaybackMethod.TRANSCODING,
        methodLabel = "Transcode",
        expectedQuality = "1080p",
        details = "HEVC is not supported by this device",
        codecs = "hevc / eac3",
        container = "mkv",
        estimatedBandwidth = 8_000_000,
        transcodeReasons = listOf("VideoCodecNotSupported", "AudioChannelsNotSupported"),
        breakdown = listOf(
            PlaybackBreakdownItem("Video", "Transcode", "HEVC not supported"),
            PlaybackBreakdownItem("Audio", "Direct", "EAC3 supported"),
        ),
    )

    @Test
    fun homeContent_loaded() {
        TestViewModels.provide(SharedViewModelMocks.mainAppViewModel(loadedMainAppState()))
        render {
            HomeContent(
                appState = loadedMainAppState(),
                currentServer = SharedViewModelMocks.testServer,
                onRefresh = {},
                getImageUrl = { null },
                getBackdropUrl = { null },
                getSeriesImageUrl = { null },
            )
        }
        assertRendered()
    }

    @Test
    fun homeContent_empty() {
        TestViewModels.provide(SharedViewModelMocks.mainAppViewModel(MainAppState(isLoading = true)))
        render {
            HomeContent(appState = MainAppState(isLoading = true, errorMessage = "Offline"), currentServer = null, onRefresh = {}, getImageUrl = { null }, getBackdropUrl = { null }, getSeriesImageUrl = { null })
        }
        assertRendered()
    }

    @Test
    fun carousels() {
        render {
            androidx.compose.foundation.layout.Column {
                HomeCarousel(movies = TestItems.movies(5), getBackdropUrl = { null }, onItemClick = {}, title = "Featured")
                EnhancedContentCarousel(
                    items = TestItems.movies(3) + TestItems.episodes(2) + TestItems.seriesList(2),
                    getImageUrl = { null },
                    getBackdropUrl = { null },
                    onItemClick = {},
                    title = "Recently added",
                )
            }
        }
        assertRendered()
    }

    @Test
    fun stuff_and_books() {
        val vm = SharedViewModelMocks.mainAppViewModel(loadedMainAppState())
        TestViewModels.provide(vm)
        render {
            androidx.compose.foundation.layout.Column {
                StuffScreen(libraryId = TestItems.libraries()[3].id.toString(), collectionType = "homevideos", viewModel = vm)
            }
        }
        assertRendered()
    }

    @Test
    fun books() {
        TestViewModels.provide(SharedViewModelMocks.mainAppViewModel(loadedMainAppState()))
        render { BooksScreen() }
        assertRendered()
    }

    @Test
    fun favorites_states() {
        render {
            ImmersiveFavoritesScreen(
                favorites = TestItems.movies(4) + TestItems.seriesList(2) + TestItems.episodes(2) + TestItems.tracks(2),
                isLoading = false,
                errorMessage = null,
                onRefresh = {},
                getImageUrl = { null },
                onBackClick = {},
            )
        }
        assertRendered()
    }

    @Test
    fun favorites_emptyError() {
        render { ImmersiveFavoritesScreen(favorites = emptyList(), isLoading = true, errorMessage = "Failed", onRefresh = {}, getImageUrl = { null }, onBackClick = {}) }
        assertRendered()
    }

    private fun playlistsVm(state: PlaylistsState): PlaylistsViewModel = mockk(relaxed = true) {
        every { this@mockk.state } returns MutableStateFlow(state)
    }

    @Test
    fun playlists_container() {
        val playlists = (1..5).map { TestItems.movie(seed = 950 + it, name = "Playlist $it").copy(type = BaseItemKind.PLAYLIST, childCount = it * 3) }
        TestViewModels.provide(SharedViewModelMocks.mainAppViewModel())
        render { ImmersivePlaylistsScreenContainer(onPlaylistClick = {}, onBackClick = {}, onSearchClick = {}, viewModel = playlistsVm(PlaylistsState(playlists = playlists))) }
        assertRendered()
    }

    @Test
    fun playlists_searchLoadingError() {
        render {
            ImmersivePlaylistsScreen(
                state = PlaylistsState(isLoading = true, searchQuery = "zzz", errorMessage = "Failed"),
                getImageUrl = { null },
                getBackdropUrl = { null },
                onPlaylistClick = {},
                onBackClick = {},
                onSearchClick = {},
                onRefresh = {},
                onSearchQueryChange = {},
            )
        }
        assertRendered()
    }

    @Test
    fun homeVideos_container() {
        val vm = SharedViewModelMocks.mainAppViewModel(loadedMainAppState())
        TestViewModels.provide(vm)
        render { ImmersiveHomeVideosScreenContainer(libraryId = TestItems.libraries()[3].id.toString(), onVideoClick = {}, onItemClick = {}, onBackClick = {}, viewModel = vm) }
        assertRendered()
    }

    @Test
    fun libraryBrowser_full() {
        render {
            ImmersiveLibraryBrowserScreen(
                items = TestItems.movies(10),
                featuredItems = TestItems.movies(3),
                isLoading = false,
                isLoadingMore = true,
                hasMoreItems = true,
                config = ImmersiveLibraryConfig(Color.Red, Icons.Default.Movie, "No movies", "Add some", libraryName = "Movies"),
                sortOptions = listOf(ImmersiveSortOption(R.string.app_name, "name"), ImmersiveSortOption(R.string.app_name, "date")),
                selectedSortIndex = 1,
                onSortSelected = {},
                onLoadMore = {},
                onItemClick = {},
                onCarouselItemClick = {},
                onRefresh = {},
                onSearchClick = {},
                onBackClick = {},
                getImageUrl = { null },
                buildCarouselItem = { CarouselItem(it.id.toString(), it.name ?: "", "2025", "https://img.test/x", rating = 8f) },
            )
        }
        assertRendered()
    }

    @Test
    fun libraryBrowser_emptyError() {
        render {
            ImmersiveLibraryBrowserScreen(
                items = emptyList(),
                featuredItems = emptyList(),
                isLoading = false,
                isLoadingMore = false,
                hasMoreItems = false,
                config = ImmersiveLibraryConfig(Color.Blue, Icons.Default.Movie, "Nothing here", "Try later"),
                sortOptions = emptyList(),
                selectedSortIndex = 0,
                onSortSelected = {},
                onLoadMore = {},
                onItemClick = {},
                onCarouselItemClick = {},
                onRefresh = {},
                onSearchClick = {},
                onBackClick = {},
                getImageUrl = { null },
                buildCarouselItem = { null },
                errorMessage = "Network error",
            )
        }
        assertRendered()
    }

    @Test
    fun libraryBrowser_loading() {
        render {
            ImmersiveLibraryBrowserScreen(
                items = emptyList(), featuredItems = emptyList(), isLoading = true, isLoadingMore = false, hasMoreItems = false,
                config = ImmersiveLibraryConfig(Color.Blue, Icons.Default.Movie, "", ""), sortOptions = emptyList(), selectedSortIndex = 0,
                onSortSelected = {}, onLoadMore = {}, onItemClick = {}, onCarouselItemClick = {}, onRefresh = {}, onSearchClick = {},
                onBackClick = {}, getImageUrl = { null }, buildCarouselItem = { null },
            )
        }
        assertRendered()
    }

    private fun renderItemDetail(withItem: Boolean, error: String? = null) {
        val vm: ItemDetailViewModel = mockk(relaxed = true) {
            every { item } returns mutableStateOf(if (withItem) TestItems.movie() else null)
            every { this@mockk.error } returns mutableStateOf(error)
            every { playbackAnalysis } returns mutableStateOf(if (withItem) analysis else null)
            every { playbackProgress } returns mutableStateOf(null)
        }
        val recs: PlaybackRecommendationViewModel = mockk(relaxed = true) {
            every { recommendations } returns MutableStateFlow(
                RecommendationType.values().map { PlaybackRecommendation(it, "Message ${it.name}", "Details") },
            )
            every { isLoading } returns MutableStateFlow(!withItem)
            every { currentItem } returns MutableStateFlow(if (withItem) TestItems.movie() else null)
        }
        render { ItemDetailScreen(itemId = "x", onBackClick = {}, viewModel = vm, recommendationViewModel = recs) }
    }

    @Test fun itemDetail_loaded() = renderItemDetail(true).also { assertRendered() }

    @Test fun itemDetail_loading() = renderItemDetail(false).also { assertRendered() }

    @Test fun itemDetail_error() = renderItemDetail(false, "Not found").also { assertRendered() }

    private fun renderAssistant(state: AiAssistantState) {
        TestViewModels.provide(SharedViewModelMocks.mainAppViewModel())
        val vm: AiAssistantViewModel = mockk(relaxed = true) { every { uiState } returns MutableStateFlow(state) }
        render { AiAssistantScreen(onBackClick = {}, onItemClick = {}, viewModel = vm) }
    }

    @Test fun assistant_conversation() = renderAssistant(
        AiAssistantState(
            messages = listOf(
                AiMessage(1, "Recommend me something", isUser = true),
                AiMessage(2, "Try these **great** picks:\n- One\n- Two", isUser = false, recommendedItems = TestItems.movies(3)),
            ),
            isOnDeviceAI = true,
            nanoStatus = "Ready",
        ),
    ).also { assertRendered() }

    @Test fun assistant_downloadingNano() = renderAssistant(
        AiAssistantState(isLoading = true, isDownloadingNano = true, downloadProgress = "40 MB / 1 GB", nanoStatus = "Downloading"),
    ).also { assertRendered() }

    @Test fun assistant_error() = renderAssistant(AiAssistantState(canRetryDownload = true, errorCode = 606, nanoStatus = "Failed")).also { assertRendered() }

    private fun renderDiagnostics(state: AiBackendUiState) {
        val vm: AiDiagnosticsViewModel = mockk(relaxed = true) { every { aiBackendState } returns MutableStateFlow(state) }
        render { AiDiagnosticsScreen(onBackClick = {}, viewModel = vm) }
    }

    @Test fun aiDiagnostics_nano() = renderDiagnostics(AiBackendUiState(isUsingNano = true, nanoStatus = "Nano active", detailedStatus = "All good")).also { assertRendered() }

    @Test fun aiDiagnostics_downloading() = renderDiagnostics(AiBackendUiState(isDownloading = true, downloadBytesProgress = "10/100 MB")).also { assertRendered() }

    @Test fun aiDiagnostics_error() = renderDiagnostics(AiBackendUiState(canRetryDownload = true, errorCode = 601)).also { assertRendered() }

    private fun renderTranscoding(state: TranscodingDiagnosticsViewModel.UiState) {
        val vm: TranscodingDiagnosticsViewModel = mockk(relaxed = true) { every { uiState } returns MutableStateFlow(state) }
        render { TranscodingDiagnosticsScreen(onNavigateBack = {}, viewModel = vm) }
    }

    @Test
    fun transcoding_success() = renderTranscoding(
        TranscodingDiagnosticsViewModel.UiState.Success(
            listOf(true, false, true).mapIndexed { i, needs ->
                TranscodingDiagnosticsViewModel.VideoAnalysis(
                    id = "v$i", name = "Video $i", videoCodec = if (needs) "hevc" else "h264", audioCodec = "aac", container = "mkv",
                    resolution = "1920x1080", needsTranscoding = needs, methodLabel = if (needs) "Transcode" else "Direct Play",
                    transcodingReasons = if (needs) listOf("Codec") else emptyList(), breakdown = analysis.breakdown,
                    item = TestItems.movie(seed = 60 + i), itemType = if (i == 1) "Episode" else "Movie",
                )
            },
        ),
    ).also { assertRendered() }

    @Test fun transcoding_loading() = renderTranscoding(TranscodingDiagnosticsViewModel.UiState.Loading).also { assertRendered() }

    @Test fun transcoding_error() = renderTranscoding(TranscodingDiagnosticsViewModel.UiState.Error("Boom")).also { assertRendered() }

    @Test fun transcoding_empty() = renderTranscoding(TranscodingDiagnosticsViewModel.UiState.Success(emptyList())).also { assertRendered() }

    private fun renderArtist(state: ArtistAlbumsState) {
        val vm: ArtistAlbumsViewModel = mockk(relaxed = true) { every { this@mockk.state } returns MutableStateFlow(state) }
        render { ArtistDetailScreen(artistId = "a", artistName = "The Testers", onBackClick = {}, onAlbumClick = {}, mainViewModel = SharedViewModelMocks.mainAppViewModel(), viewModel = vm) }
    }

    @Test fun artist_albums() = renderArtist(ArtistAlbumsState(albums = (1..4).map { TestItems.album(seed = 500 + it) })).also { assertRendered() }

    @Test fun artist_loading() = renderArtist(ArtistAlbumsState(isLoading = true)).also { assertRendered() }

    @Test fun artist_error() = renderArtist(ArtistAlbumsState(errorMessage = "Gone")).also { assertRendered() }

    @Test
    fun offlineLibrary_withDownloads() {
        val vm = SharedViewModelMocks.downloadsViewModel(
            downloads = listOf(
                SharedViewModelMocks.sampleDownload(TestItems.movie()),
                SharedViewModelMocks.sampleDownload(TestItems.episode()),
                SharedViewModelMocks.sampleDownload(TestItems.episode(seed = 333, index = 3)),
                SharedViewModelMocks.sampleDownload(TestItems.movie(seed = 5), DownloadStatus.DOWNLOADING),
            ),
        )
        render { OfflineLibraryScreen(isReconnecting = false, isOnline = false, onReconnect = {}, onBackToLogin = {}, downloadsViewModel = vm) }
        assertRendered()
    }

    @Test
    fun offlineLibrary_reconnecting() {
        render { OfflineLibraryScreen(isReconnecting = true, isOnline = true, onReconnect = {}, onBackToLogin = {}, downloadsViewModel = SharedViewModelMocks.downloadsViewModel()) }
        assertRendered()
    }

    @Test
    fun privacyPolicyAndSection() {
        render { PrivacyPolicyScreen(onNavigateBack = {}) }
        assertRendered()
    }

    @Test
    fun settingsSection() {
        render {
            SettingsSectionScreen(
                titleRes = R.string.app_name,
                descriptionRes = R.string.app_name,
                optionRes = listOf(R.string.app_name, R.string.app_name),
                onNavigateBack = {},
            )
        }
        assertRendered()
    }
}
