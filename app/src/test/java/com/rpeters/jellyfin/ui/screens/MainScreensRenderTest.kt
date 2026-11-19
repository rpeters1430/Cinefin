package com.rpeters.jellyfin.ui.screens

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.rpeters.jellyfin.data.ServerInfo
import com.rpeters.jellyfin.data.model.CurrentUserDetails
import com.rpeters.jellyfin.data.model.DiscoveredServer
import com.rpeters.jellyfin.data.model.ServerType
import com.rpeters.jellyfin.data.offline.DownloadProgress
import com.rpeters.jellyfin.data.offline.DownloadStatus
import com.rpeters.jellyfin.data.offline.OfflineStorageInfo
import com.rpeters.jellyfin.data.preferences.LibraryActionsPreferences
import com.rpeters.jellyfin.testing.compose.ComposeScreenTest
import com.rpeters.jellyfin.testing.compose.SharedViewModelMocks
import com.rpeters.jellyfin.testing.compose.TestViewModels
import com.rpeters.jellyfin.testing.compose.loadedMainAppState
import com.rpeters.jellyfin.testing.fixtures.TestItems
import com.rpeters.jellyfin.ui.components.ConnectionPhase
import com.rpeters.jellyfin.ui.components.ConnectionState
import com.rpeters.jellyfin.ui.components.PinningAlertReason
import com.rpeters.jellyfin.ui.components.PinningAlertState
import com.rpeters.jellyfin.ui.downloads.DownloadsScreen
import com.rpeters.jellyfin.ui.player.audio.AudioPlaybackState
import com.rpeters.jellyfin.ui.utils.NetworkType
import com.rpeters.jellyfin.ui.utils.OfflineLibraryItem
import com.rpeters.jellyfin.ui.utils.OfflineManager
import com.rpeters.jellyfin.ui.viewmodel.AudioPlaybackViewModel
import com.rpeters.jellyfin.ui.viewmodel.LibraryActionsPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.MainAppState
import com.rpeters.jellyfin.ui.viewmodel.SearchAiState
import com.rpeters.jellyfin.ui.viewmodel.SearchState
import com.rpeters.jellyfin.ui.viewmodel.SearchViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test

class MainScreensRenderTest : ComposeScreenTest() {

    private val libraryActions: LibraryActionsPreferencesViewModel = mockk(relaxed = true) {
        coEvery { preferences } returns MutableStateFlow(LibraryActionsPreferences(enableManagementActions = true))
    }

    @Before
    fun setUp() {
        TestViewModels.provide(libraryActions)
    }

    // region Server connection

    private fun renderConnection(state: ConnectionState, plex: Boolean = false) = render {
        ServerConnectionScreen(
            connectionState = state,
            savedServerUrl = state.savedServerUrl,
            savedUsername = state.savedUsername,
            hasSavedPassword = state.hasSavedPassword,
            isBiometricAuthEnabled = state.isBiometricAuthEnabled,
            isBiometricAuthAvailable = state.isBiometricAuthAvailable,
            isPlexSignInAvailable = plex,
        )
    }

    @Test fun connection_idle() = renderConnection(ConnectionState()).also { assertRendered() }

    @Test fun connection_discoveredAndSaved() = renderConnection(
        ConnectionState(
            savedServerUrl = "https://jellyfin.example.test",
            savedUsername = "tester",
            hasSavedPassword = true,
            isLocalCredentialCheckComplete = true,
            isBiometricAuthEnabled = true,
            isBiometricAuthAvailable = true,
            discoveredServers = listOf(
                DiscoveredServer("Living Room", "http://192.168.1.10:8096", "a", "10.11.0"),
                DiscoveredServer("Emby Box", "http://192.168.1.11:8096", "b", "4.9.3", ServerType.EMBY),
            ),
            canEnterOffline = true,
        ),
        plex = true,
    ).also { assertRendered() }

    @Test fun connection_connecting() = renderConnection(
        ConnectionState(isConnecting = true, connectionPhase = ConnectionPhase.Authenticating, currentUrl = "https://x.test", progress = 0.5f, loadedCount = 2, totalCount = 4),
    ).also { assertRendered() }

    @Test fun connection_error() = renderConnection(
        ConnectionState(errorMessage = "Wrong password", connectionPhase = ConnectionPhase.Error, isNetworkAvailable = false),
    ).also { assertRendered() }

    @Test fun connection_quickConnect() = renderConnection(
        ConnectionState(isQuickConnectActive = true, quickConnectCode = "123456", isQuickConnectPolling = true, quickConnectStatus = "Waiting"),
    ).also { assertRendered() }

    @Test fun connection_pinningAlert() = renderConnection(
        ConnectionState(pinningAlert = PinningAlertState("jellyfin.example.test", PinningAlertReason.MISMATCH, attemptedPins = listOf("sha256/AAAA"), firstSeenEpochMillis = 1L, expiresAtEpochMillis = 2L)),
    ).also { assertRendered() }

    @Test fun connection_pinningExpired() = renderConnection(
        ConnectionState(pinningAlert = PinningAlertState("jellyfin.example.test", PinningAlertReason.EXPIRED)),
    ).also { assertRendered() }

    // endregion

    // region Downloads

    private fun renderDownloads(empty: Boolean) {
        val items = listOf(
            SharedViewModelMocks.sampleDownload(TestItems.movie(), DownloadStatus.COMPLETED),
            SharedViewModelMocks.sampleDownload(TestItems.episode(), DownloadStatus.DOWNLOADING),
            SharedViewModelMocks.sampleDownload(TestItems.episode(seed = 311, index = 2), DownloadStatus.PAUSED),
            SharedViewModelMocks.sampleDownload(TestItems.movie(seed = 3), DownloadStatus.FAILED),
            SharedViewModelMocks.sampleDownload(TestItems.movie(seed = 4), DownloadStatus.PENDING),
        )
        val vm = SharedViewModelMocks.downloadsViewModel(
            downloads = if (empty) emptyList() else items,
            progress = if (empty) emptyMap() else mapOf(
                items[1].id to DownloadProgress(items[1].id, 1_500_000_000, 4_000_000_000, 37.5f, 12_000_000, 120_000),
                items[2].id to DownloadProgress(items[2].id, 1, 10, 10f, 0, null, isTranscoding = true, transcodingProgress = 40f),
            ),
            storage = OfflineStorageInfo(128_000_000_000, 64_000_000_000, 64_000_000_000, 5, 20_000_000_000),
        )
        render { DownloadsScreen(onNavigateBack = {}, downloadsViewModel = vm) }
    }

    @Test fun downloads_mixed() = renderDownloads(empty = false).also { assertRendered() }

    @Test fun downloads_empty() = renderDownloads(empty = true).also { assertRendered() }

    // endregion

    // region Home, libraries, search

    @Test
    fun home_loaded() {
        val state = loadedMainAppState()
        val vm = SharedViewModelMocks.mainAppViewModel(state)
        TestViewModels.provide(vm)
        render {
            ImmersiveHomeScreen(
                appState = state,
                currentServer = SharedViewModelMocks.testServer,
                onRefresh = {},
                onSearch = {},
                onClearSearch = {},
                getImageUrl = { null },
                getBackdropUrl = { null },
                getSeriesImageUrl = { null },
                showBackButton = true,
                viewModel = vm,
                libraryActionsPreferencesViewModel = libraryActions,
            )
        }
        assertRendered()
    }

    @Test
    fun home_loadingAndError() {
        val state = MainAppState(isLoading = true, errorMessage = "Offline", isLoadingViewingMood = true)
        val vm = SharedViewModelMocks.mainAppViewModel(state)
        render {
            ImmersiveHomeScreen(
                appState = state,
                currentServer = null,
                onRefresh = {},
                onSearch = {},
                onClearSearch = {},
                getImageUrl = { null },
                getBackdropUrl = { null },
                getSeriesImageUrl = { null },
                viewModel = vm,
                libraryActionsPreferencesViewModel = libraryActions,
            )
        }
        assertRendered()
    }

    private fun renderLibraryType(type: LibraryType, state: MainAppState = loadedMainAppState()) {
        val vm = SharedViewModelMocks.mainAppViewModel(state)
        render {
            LibraryTypeScreen(
                libraryType = type,
                viewModel = vm,
                libraryActionsPreferencesViewModel = libraryActions,
                downloadsViewModel = SharedViewModelMocks.downloadsViewModel(),
            )
        }
    }

    @Test fun libraryType_movies() = renderLibraryType(LibraryType.MOVIES).also { assertRendered() }

    @Test fun libraryType_shows() = renderLibraryType(LibraryType.TV_SHOWS).also { assertRendered() }

    @Test fun libraryType_music() = renderLibraryType(LibraryType.MUSIC).also { assertRendered() }

    @Test fun libraryType_stuff() = renderLibraryType(LibraryType.STUFF).also { assertRendered() }

    @Test fun libraryType_loadingEmpty() = renderLibraryType(LibraryType.MOVIES, MainAppState(isLoadingMovies = true, isLoading = true)).also { assertRendered() }

    @Test fun libraryType_error() = renderLibraryType(LibraryType.TV_SHOWS, MainAppState(errorMessage = "Failed")).also { assertRendered() }

    @Test
    fun libraries_states() {
        render {
            ImmersiveLibraryScreen(libraries = TestItems.libraries(), isLoading = false, errorMessage = null, onRefresh = {}, getImageUrl = { null }, showBackButton = true)
        }
        assertRendered()
    }

    @Test
    fun libraries_loadingError() {
        render {
            ImmersiveLibraryScreen(libraries = emptyList(), isLoading = true, errorMessage = "Nope", onRefresh = {}, getImageUrl = { null })
        }
        assertRendered()
    }

    private fun renderSearch(appState: MainAppState, search: SearchState, ai: SearchAiState = SearchAiState()) {
        val vm: SearchViewModel = mockk(relaxed = true) {
            coEvery { searchState } returns MutableStateFlow(search)
            coEvery { aiState } returns MutableStateFlow(ai)
        }
        render {
            ImmersiveSearchScreen(appState = appState, onSearch = {}, onClearSearch = {}, getImageUrl = { null }, viewModel = vm)
        }
    }

    @Test fun search_results() = renderSearch(
        loadedMainAppState().copy(searchQuery = "test", searchResults = TestItems.movies(4) + TestItems.seriesList(2) + TestItems.tracks(2)),
        SearchState(searchQuery = "test", searchResults = TestItems.movies(4) + TestItems.seriesList(2) + TestItems.tracks(2) + listOf(TestItems.person()), hasSearched = true),
        SearchAiState(lastEnhancedQuery = "test movies"),
    ).also { assertRendered() }

    @Test fun search_empty() = renderSearch(MainAppState(), SearchState()).also { assertRendered() }

    @Test fun search_noResultsAndError() = renderSearch(
        MainAppState(searchQuery = "zzz"),
        SearchState(searchQuery = "zzz", hasSearched = true, errorMessage = "Timed out"),
        SearchAiState(isProcessing = true),
    ).also { assertRendered() }

    @Test fun search_searching() = renderSearch(MainAppState(isSearching = true), SearchState(searchQuery = "abc", isSearching = true)).also { assertRendered() }

    @Test
    fun music_loaded() {
        val vm = SharedViewModelMocks.mainAppViewModel(loadedMainAppState())
        render { MusicScreen(viewModel = vm) }
        assertRendered()
    }

    @Test
    fun music_empty() {
        val vm = SharedViewModelMocks.mainAppViewModel(MainAppState(isLoading = true))
        render { MusicScreen(viewModel = vm) }
        assertRendered()
    }

    @Test
    fun moviesAndShowsContainers() {
        val vm = SharedViewModelMocks.mainAppViewModel(loadedMainAppState())
        TestViewModels.provide(vm)
        render { ImmersiveMoviesScreenContainer(onMovieClick = {}, onSearchClick = {}, onBackClick = {}, viewModel = vm) }
        assertRendered()
    }

    @Test
    fun showsContainer() {
        val vm = SharedViewModelMocks.mainAppViewModel(loadedMainAppState())
        TestViewModels.provide(vm)
        render { ImmersiveTVShowsScreenContainer(onTVShowClick = {}, onSearchClick = {}, onBackClick = {}, viewModel = vm) }
        assertRendered()
    }

    // endregion

    // region Profile, offline, audio

    @Test
    fun profile_full() {
        render {
            ProfileScreen(
                currentServer = SharedViewModelMocks.testServer.copy(isAdministrator = true),
                serverInfo = ServerInfo("id", "Test Server", "10.11.0", "Linux", "http://192.168.1.10:8096", "Jellyfin Server", true),
                currentUser = CurrentUserDetails("tester", "tag", "2026-11-01T10:00:00Z", true),
                userAvatarUrl = null,
                onLogout = {},
            )
        }
        assertRendered()
    }

    @Test
    fun profile_empty() {
        render { ProfileScreen(currentServer = null, serverInfo = null, currentUser = null, userAvatarUrl = null, onLogout = {}, showBackButton = false) }
        assertRendered()
    }

    private fun offlineManager(online: Boolean, items: List<OfflineLibraryItem>): OfflineManager = mockk(relaxed = true) {
        coEvery { isOnline } returns MutableStateFlow(online)
        coEvery { networkType } returns MutableStateFlow(if (online) NetworkType.WIFI else NetworkType.NONE)
        coEvery { offlineContent } returns MutableStateFlow(items)
        every { getOfflineStorageUsage() } returns com.rpeters.jellyfin.ui.utils.OfflineStorageInfo(10_000_000_000, items.size, "9.3 GB")
        every { isCurrentlyOnline() } returns online
    }

    @Test
    fun offline_withContent() {
        val items = (TestItems.movies(3) + TestItems.episodes(2)).map {
            OfflineLibraryItem(it, "/data/${it.id}.mkv", null, "1080p", 2_000_000_000, 1_763_000_000_000)
        }
        render { OfflineScreen(offlineManager = offlineManager(false, items), onDeleteOfflineContent = {}) }
        assertRendered()
    }

    @Test
    fun offline_emptyOnline() {
        render { OfflineScreen(offlineManager = offlineManager(true, emptyList())) }
        assertRendered()
    }

    private fun audioVm(playing: Boolean, queue: Int): AudioPlaybackViewModel {
        val items = (1..queue).map {
            MediaItem.Builder().setMediaId("t$it").setMediaMetadata(
                MediaMetadata.Builder().setTitle("Track $it").setArtist("The Testers").setAlbumTitle("Test Album").build(),
            ).build()
        }
        return mockk(relaxed = true) {
            coEvery { playbackState } returns MutableStateFlow(
                AudioPlaybackState(
                    isConnected = true,
                    isPlaying = playing,
                    currentMediaItem = items.firstOrNull(),
                    currentMediaItemIndex = 0,
                    shuffleEnabled = playing,
                    repeatMode = if (playing) androidx.media3.common.Player.REPEAT_MODE_ALL else androidx.media3.common.Player.REPEAT_MODE_OFF,
                    currentPosition = 60_000,
                    duration = 240_000,
                ),
            )
            coEvery { this@mockk.queue } returns MutableStateFlow(items)
            coEvery { currentPosition } returns MutableStateFlow(60_000L)
            coEvery { duration } returns MutableStateFlow(240_000L)
        }
    }

    @Test fun nowPlaying_playing() = render { NowPlayingScreen(viewModel = audioVm(true, 5)) }.also { assertRendered() }

    @Test fun nowPlaying_idle() = render { NowPlayingScreen(viewModel = audioVm(false, 0)) }.also { assertRendered() }

    @Test fun queue_items() = render { AudioQueueScreen(viewModel = audioVm(true, 6)) }.also { assertRendered() }

    @Test fun queue_empty() = render { AudioQueueScreen(viewModel = audioVm(false, 0)) }.also { assertRendered() }

    // endregion
}
