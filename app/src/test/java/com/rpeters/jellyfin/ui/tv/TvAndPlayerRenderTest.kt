package com.rpeters.jellyfin.ui.tv

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.focus.FocusRequester
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.rpeters.jellyfin.data.model.DiscoveredServer
import com.rpeters.jellyfin.data.model.ServerProfile
import com.rpeters.jellyfin.data.model.ServerType
import com.rpeters.jellyfin.data.playback.QualityRecommendation
import com.rpeters.jellyfin.data.playback.RecommendationSeverity
import com.rpeters.jellyfin.data.preferences.PlaybackPreferences
import com.rpeters.jellyfin.data.preferences.SeerrPreferences
import com.rpeters.jellyfin.data.preferences.ServerProfiles
import com.rpeters.jellyfin.data.preferences.SubtitleAppearancePreferences
import com.rpeters.jellyfin.data.preferences.ThemePreferences
import com.rpeters.jellyfin.data.preferences.TranscodingQuality
import com.rpeters.jellyfin.testing.compose.ComposeScreenTest
import com.rpeters.jellyfin.testing.compose.SharedViewModelMocks
import com.rpeters.jellyfin.testing.compose.TestViewModels
import com.rpeters.jellyfin.testing.compose.loadedMainAppState
import com.rpeters.jellyfin.testing.fixtures.TestItems
import com.rpeters.jellyfin.ui.adaptive.LocalAdaptiveLayoutConfig
import com.rpeters.jellyfin.ui.components.ConnectionState
import com.rpeters.jellyfin.ui.components.tv.TvEmptyState
import com.rpeters.jellyfin.ui.components.tv.TvErrorBanner
import com.rpeters.jellyfin.ui.components.tv.TvFullScreenLoading
import com.rpeters.jellyfin.ui.components.tv.TvSkeletonCard
import com.rpeters.jellyfin.ui.components.tv.TvSkeletonCarousel
import com.rpeters.jellyfin.ui.components.tv.TvWarningBanner
import com.rpeters.jellyfin.ui.player.AspectRatioMode
import com.rpeters.jellyfin.ui.player.AudioTrackSelectionDialog
import com.rpeters.jellyfin.ui.player.CastDeviceSelectionDialog
import com.rpeters.jellyfin.ui.player.CastNowPlayingOverlay
import com.rpeters.jellyfin.ui.player.CastRemoteScreen
import com.rpeters.jellyfin.ui.player.ExpressiveVideoControls
import com.rpeters.jellyfin.ui.player.QualityRecommendationNotification
import com.rpeters.jellyfin.ui.player.QualitySelectionDialog
import com.rpeters.jellyfin.ui.player.ResumePlaybackDialog
import com.rpeters.jellyfin.ui.player.SubtitleTrackSelectionDialog
import com.rpeters.jellyfin.ui.player.TrackInfo
import com.rpeters.jellyfin.ui.player.TvPlayerSettingsDialog
import com.rpeters.jellyfin.ui.player.TvVideoPlayerControls
import com.rpeters.jellyfin.ui.player.VideoPlayerState
import com.rpeters.jellyfin.ui.player.VideoQuality
import com.rpeters.jellyfin.ui.player.audio.AudioPlaybackState
import com.rpeters.jellyfin.ui.player.cast.DiscoveryState
import com.rpeters.jellyfin.ui.player.tv.TvAudioPlayerScreen
import com.rpeters.jellyfin.ui.player.tv.TvNextEpisodeOverlay
import com.rpeters.jellyfin.ui.player.tv.TvPictureInPictureState
import com.rpeters.jellyfin.ui.player.tv.TvQuickSettingsDrawer
import com.rpeters.jellyfin.ui.player.tv.TvSeekBar
import com.rpeters.jellyfin.ui.player.tv.TvVideoPlayerScreen
import com.rpeters.jellyfin.ui.screens.tv.TvHomeScreen
import com.rpeters.jellyfin.ui.screens.tv.TvItemDetailScreen
import com.rpeters.jellyfin.ui.screens.tv.TvLibraryScreen
import com.rpeters.jellyfin.ui.screens.tv.TvQuickConnectScreen
import com.rpeters.jellyfin.ui.screens.tv.TvRequestsScreen
import com.rpeters.jellyfin.ui.screens.tv.TvSearchScreen
import com.rpeters.jellyfin.ui.screens.tv.TvServerConnectionScreen
import com.rpeters.jellyfin.ui.screens.tv.TvSettingsScreen
import com.rpeters.jellyfin.ui.screens.tv.adaptive.TvCarouselHomeContent
import com.rpeters.jellyfin.ui.screens.tv.adaptive.TvHomeMediaSection
import com.rpeters.jellyfin.ui.viewmodel.AudioPlaybackViewModel
import com.rpeters.jellyfin.ui.viewmodel.MainAppState
import com.rpeters.jellyfin.ui.viewmodel.PlaybackPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.ProfilesUiState
import com.rpeters.jellyfin.ui.viewmodel.ProfilesViewModel
import com.rpeters.jellyfin.ui.viewmodel.RequestsUiState
import com.rpeters.jellyfin.ui.viewmodel.RequestsViewModel
import com.rpeters.jellyfin.ui.viewmodel.SearchAiState
import com.rpeters.jellyfin.ui.viewmodel.SearchState
import com.rpeters.jellyfin.ui.viewmodel.SearchViewModel
import com.rpeters.jellyfin.ui.viewmodel.ServerConnectionViewModel
import com.rpeters.jellyfin.ui.viewmodel.SubtitleAppearancePreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.TVSeasonState
import com.rpeters.jellyfin.ui.viewmodel.TVSeasonViewModel
import com.rpeters.jellyfin.ui.viewmodel.ThemePreferencesViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

class TvAndPlayerRenderTest : ComposeScreenTest() {

    // region Player state fixtures

    private fun track(group: Int, index: Int, label: String, audio: Boolean, selected: Boolean) = TrackInfo(
        groupIndex = group,
        trackIndex = index,
        format = Format.Builder()
            .setSampleMimeType(if (audio) MimeTypes.AUDIO_E_AC3 else MimeTypes.TEXT_VTT)
            .setLanguage(if (index == 0) "en" else "es")
            .setLabel(label)
            .setChannelCount(if (audio) 6 else Format.NO_VALUE)
            .build(),
        isSelected = selected,
        displayName = label,
    )

    private val audioTracks = listOf(track(0, 0, "English 5.1", true, true), track(0, 1, "Spanish Stereo", true, false))
    private val subtitleTracks = listOf(track(1, 0, "English", false, true), track(1, 1, "Spanish", false, false))
    private val qualities = listOf(
        VideoQuality("orig", "Original", 40_000_000, 3840, 2160),
        VideoQuality("1080", "1080p", 8_000_000, 1920, 1080),
        VideoQuality("720", "720p", 4_000_000, 1280, 720),
    )

    private fun playerState(
        playing: Boolean = true,
        loading: Boolean = false,
        casting: Boolean = false,
        error: String? = null,
        nextEpisode: Boolean = false,
    ) = VideoPlayerState(
        isPlaying = playing,
        isLoading = loading,
        currentPosition = 600_000,
        duration = 2_640_000,
        bufferedPosition = 900_000,
        error = error,
        itemId = "ep1",
        itemName = "Episode 1: The Pilot",
        videoWidth = 3840,
        videoHeight = 2160,
        isHdrContent = true,
        availableQualities = qualities,
        selectedQuality = qualities[1],
        isCastAvailable = true,
        isCasting = casting,
        isCastConnected = casting,
        castDeviceName = if (casting) "Living Room TV" else null,
        isCastPlaying = casting && playing,
        castPosterUrl = null,
        isTranscoding = true,
        transcodingReason = "VideoCodecNotSupported",
        playbackMethod = "Transcode",
        castOverview = "Overview",
        castPosition = 300_000,
        castDuration = 2_640_000,
        castVolume = 0.6f,
        availableAudioTracks = audioTracks,
        selectedAudioTrack = audioTracks[0],
        availableSubtitleTracks = subtitleTracks,
        selectedSubtitleTrack = subtitleTracks[0],
        playbackSpeed = 1.25f,
        subtitleDelayMs = 300,
        introStartMs = 500_000,
        introEndMs = 700_000,
        outroStartMs = 2_500_000,
        outroEndMs = 2_640_000,
        nextEpisode = if (nextEpisode) TestItems.episode(index = 2) else null,
        showNextEpisodeCountdown = nextEpisode,
        nextEpisodeCountdown = 8,
        qualityRecommendation = QualityRecommendation(TranscodingQuality.MEDIUM, "Buffering detected", RecommendationSeverity.HIGH),
    )

    // endregion

    // region Mobile player pieces

    private fun renderControls(state: VideoPlayerState, loadingUi: Boolean = false, visible: Boolean = true) = render {
        ExpressiveVideoControls(
            playerState = state,
            showPrimaryLoadingUi = loadingUi,
            onPlayPause = {},
            onSeek = {},
            onSeekBy = {},
            onQualityClick = {},
            onAudioClick = {},
            onToggleMute = {},
            onCastClick = {},
            onSubtitlesClick = {},
            onAspectRatioChange = {},
            onPlaybackSpeedChange = {},
            onBackClick = {},
            onPictureInPictureClick = {},
            supportsPip = true,
            isVisible = visible,
        )
    }

    @Test fun controls_playingInIntro() = renderControls(playerState()).also { assertRendered() }

    @Test fun controls_pausedLoading() = renderControls(playerState(playing = false, loading = true), loadingUi = true).also { assertRendered() }

    @Test fun controls_inOutroWithNext() = renderControls(playerState(nextEpisode = true).copy(currentPosition = 2_550_000, isMuted = true)).also { assertRendered() }

    @Test fun controls_hidden() = renderControls(playerState(), visible = false).also { assertRendered() }

    @Test
    fun dialogs_tracksAndQuality() {
        render {
            Column {
                AudioTrackSelectionDialog(availableTracks = audioTracks, onTrackSelect = {}, onDismiss = {})
            }
        }
        assertRendered()
    }

    @Test
    fun dialogs_subtitles() {
        render { SubtitleTrackSelectionDialog(availableTracks = subtitleTracks, selectedTrack = subtitleTracks[1], onTrackSelect = {}, onDismiss = {}, subtitleDelayMs = -200, onSubtitleDelayChange = {}) }
        assertRendered()
    }

    @Test
    fun dialogs_quality() {
        render { QualitySelectionDialog(availableQualities = qualities, selectedQuality = qualities[0], onQualitySelect = {}, onDismiss = {}) }
        assertRendered()
    }

    @Test
    fun dialogs_resumeAndCast() {
        render { ResumePlaybackDialog(resumePositionMs = 3_725_000, onResume = {}, onStartOver = {}) }
        assertRendered()
    }

    @Test
    fun dialogs_castDevices() {
        render { CastDeviceSelectionDialog(availableDevices = listOf("Living Room", "Bedroom"), discoveryState = DiscoveryState.DEVICES_FOUND, onDeviceSelect = {}, onDismiss = {}) }
        assertRendered()
    }

    @Test
    fun dialogs_castDiscovering() {
        render { CastDeviceSelectionDialog(availableDevices = emptyList(), discoveryState = DiscoveryState.DISCOVERING, onDeviceSelect = {}, onDismiss = {}) }
        assertRendered()
    }

    @Test
    fun dialogs_castTimeout() {
        render { CastDeviceSelectionDialog(availableDevices = emptyList(), discoveryState = DiscoveryState.TIMEOUT, onDeviceSelect = {}, onDismiss = {}) }
        assertRendered()
    }

    @Test
    fun qualityRecommendation() {
        render {
            QualityRecommendationNotification(
                recommendation = QualityRecommendation(TranscodingQuality.LOW, "Slow network", RecommendationSeverity.MEDIUM),
                onAccept = {},
                onDismiss = {},
            )
        }
        assertRendered()
    }

    @Test
    fun castRemote_connected() {
        render { CastRemoteScreen(playerState = playerState(casting = true), onPauseCast = {}, onResumeCast = {}, onStopCast = {}, onSeekCast = {}, onDisconnectCast = {}) }
        assertRendered()
    }

    @Test
    fun castRemote_paused() {
        render { CastRemoteScreen(playerState = playerState(playing = false, casting = true), onPauseCast = {}, onResumeCast = {}, onStopCast = {}, onSeekCast = {}, onDisconnectCast = {}) }
        assertRendered()
    }

    @Test
    fun castOverlay() {
        render { CastNowPlayingOverlay(playerState = playerState(casting = true), onPauseCast = {}, onResumeCast = {}, onStopCast = {}, onSeekCast = {}, onVolumeChange = {}) }
        assertRendered()
    }

    // endregion

    // region TV player

    private fun renderTvPlayer(state: VideoPlayerState) = render {
        TvVideoPlayerScreen(
            state = state,
            exoPlayer = null,
            subtitleAppearance = SubtitleAppearancePreferences.DEFAULT,
            pipState = TvPictureInPictureState(isSupported = true) {},
            onBack = {},
            onPlayPause = {},
            onSeekForward = {},
            onSeekBackward = {},
            onSeekTo = {},
            onSetPlaybackSpeed = {},
            onToggleMute = {},
            onChangeAspectRatio = {},
            onShowAudio = {},
            onShowSubtitles = {},
            onPlayNextEpisode = {},
            onDismissNextEpisodePrompt = {},
            onCancelNextEpisodeCountdown = {},
            onErrorDismiss = {},
        )
    }

    @Test fun tvPlayer_playing() = renderTvPlayer(playerState()).also { assertRendered() }

    @Test fun tvPlayer_nextEpisode() = renderTvPlayer(playerState(nextEpisode = true).copy(hasEnded = true)).also { assertRendered() }

    @Test fun tvPlayer_error() = renderTvPlayer(playerState(error = "Playback failed", loading = true)).also { assertRendered() }

    @Test
    fun tvPlayer_pieces() {
        render {
            Column {
                TvSeekBar(currentPosition = 60_000, duration = 600_000, bufferedPosition = 120_000, onSeekTo = {}, chapters = listOf(0, 120_000, 300_000))
                TvNextEpisodeOverlay(nextItemName = "Episode 2", onPlayNext = {}, onCancel = {})
                TvQuickSettingsDrawer(
                    state = playerState(),
                    pipState = TvPictureInPictureState(isSupported = false) {},
                    onSetPlaybackSpeed = {},
                    onChangeAspectRatio = {},
                    onShowAudio = {},
                    onShowSubtitles = {},
                    onClose = {},
                    closeButtonFocusRequester = FocusRequester(),
                )
            }
        }
        assertRendered()
    }

    @Test
    fun tvControls_visible() {
        render {
            TvVideoPlayerControls(
                playerState = playerState(),
                onPlayPause = {},
                onSeek = {},
                onSeekBack = {},
                onSeekForward = {},
                onShowSettings = {},
                onBack = {},
                controlsVisible = true,
                onHideControls = {},
            )
        }
        assertRendered()
    }

    @Test
    fun tvControls_pausedHidden() {
        render {
            TvVideoPlayerControls(
                playerState = playerState(playing = false, loading = true),
                onPlayPause = {}, onSeek = {}, onSeekBack = {}, onSeekForward = {}, onShowSettings = {}, onBack = {},
                controlsVisible = false, onHideControls = {},
            )
        }
        assertRendered()
    }

    @Test
    fun tvSettingsDialog() {
        render { TvPlayerSettingsDialog(playerState = playerState(), onDismiss = {}, onAudioTrackSelect = {}, onSubtitleTrackSelect = {}, onPlaybackSpeedChange = {}) }
        assertRendered()
    }

    private fun audioVm(playing: Boolean): AudioPlaybackViewModel {
        val item = MediaItem.Builder().setMediaId("t1").setMediaMetadata(MediaMetadata.Builder().setTitle("Track 1").setArtist("The Testers").build()).build()
        return mockk(relaxed = true) {
            every { playbackState } returns MutableStateFlow(AudioPlaybackState(isConnected = true, isPlaying = playing, currentMediaItem = if (playing) item else null, duration = 200_000, currentPosition = 50_000))
            every { queue } returns MutableStateFlow(if (playing) listOf(item, item) else emptyList())
            every { currentPosition } returns MutableStateFlow(50_000L)
            every { duration } returns MutableStateFlow(200_000L)
        }
    }

    @Test fun tvAudio_playing() = render { TvAudioPlayerScreen(onBack = {}, viewModel = audioVm(true)) }.also { assertRendered() }

    @Test fun tvAudio_idle() = render { TvAudioPlayerScreen(onBack = {}, viewModel = audioVm(false)) }.also { assertRendered() }

    // endregion

    // region TV screens

    @Test
    fun tvLoadingStates() {
        render {
            Column {
                TvSkeletonCard()
                TvSkeletonCarousel(itemCount = 3)
                TvErrorBanner(title = "Error", message = "Something broke", onRetry = {}, onDismiss = {})
                TvWarningBanner(title = "Warning", message = "Heads up", onAction = {})
                TvEmptyState(title = "Empty", message = "Nothing", onAction = {}, onSecondaryAction = {}, secondaryActionText = "Back")
                TvFullScreenLoading()
            }
        }
        assertRendered()
    }

    @Test
    fun tvServerConnection_full() {
        render {
            TvServerConnectionScreen(
                onConnect = { _, _, _ -> },
                onQuickConnect = {},
                isConnecting = false,
                errorMessage = "Wrong password",
                savedServerUrl = "https://jellyfin.example.test",
                savedUsername = "tester",
                discoveredServers = listOf(DiscoveredServer("Living Room", "http://192.168.1.10:8096", "a", "10.11.0")),
                savedProfiles = listOf(
                    ServerProfile(id = "p1", serverUrl = "https://jellyfin.example.test", serverName = "Home", username = "tester"),
                    ServerProfile(id = "p2", serverUrl = "https://emby.example.test", serverName = "Emby", username = "me", serverType = ServerType.EMBY),
                ),
                detectedServerType = ServerType.EMBY,
            )
        }
        assertRendered()
    }

    @Test
    fun tvServerConnection_connecting() {
        render { TvServerConnectionScreen(onConnect = { _, _, _ -> }, onQuickConnect = {}, isConnecting = true, errorMessage = null, savedServerUrl = null, savedUsername = null) }
        assertRendered()
    }

    @Test
    fun tvQuickConnect_states() {
        render { TvQuickConnectScreen(serverUrl = "https://x.test", quickConnectCode = "123456", isConnecting = false, isPolling = true, status = "Waiting", errorMessage = null, onServerUrlChange = {}, onInitiateQuickConnect = {}, onCancel = {}) }
        assertRendered()
    }

    @Test
    fun tvQuickConnect_error() {
        render { TvQuickConnectScreen(serverUrl = "", quickConnectCode = "", isConnecting = true, isPolling = false, status = "", errorMessage = "Denied", onServerUrlChange = {}, onInitiateQuickConnect = {}, onCancel = {}) }
        assertRendered()
    }

    @Test
    fun tvHome_loaded() {
        val vm = SharedViewModelMocks.mainAppViewModel(loadedMainAppState())
        TestViewModels.provide(vm)
        render { TvHomeScreen(onItemSelect = {}, onLibrarySelect = {}, viewModel = vm) }
        assertRendered()
    }

    @Test
    fun tvHome_loading() {
        val vm = SharedViewModelMocks.mainAppViewModel(MainAppState(isLoading = true))
        TestViewModels.provide(vm)
        render { TvHomeScreen(onItemSelect = {}, onLibrarySelect = {}, viewModel = vm) }
        assertRendered()
    }

    @Test
    fun tvLibrary_movies() {
        val state = loadedMainAppState()
        val vm = SharedViewModelMocks.mainAppViewModel(state)
        TestViewModels.provide(vm)
        render { TvLibraryScreen(libraryId = TestItems.libraries()[0].id.toString(), onItemSelect = {}, onBack = {}, onSearch = {}, viewModel = vm) }
        assertRendered()
    }

    @Test
    fun tvLibrary_all() {
        val vm = SharedViewModelMocks.mainAppViewModel(loadedMainAppState())
        TestViewModels.provide(vm)
        render { TvLibraryScreen(libraryId = null, onItemSelect = {}, viewModel = vm) }
        assertRendered()
    }

    private fun renderTvDetail(item: org.jellyfin.sdk.model.api.BaseItemDto, season: TVSeasonState = TVSeasonState()) {
        val state = loadedMainAppState().copy(selectedItem = item, allItems = loadedMainAppState().allItems + item)
        val vm = SharedViewModelMocks.mainAppViewModel(state)
        val seasonVm: TVSeasonViewModel = mockk(relaxed = true) { every { this@mockk.state } returns MutableStateFlow(season) }
        TestViewModels.provide(vm)
        TestViewModels.provide(seasonVm)
        render { TvItemDetailScreen(itemId = item.id.toString(), viewModel = vm, seasonViewModel = seasonVm, onItemSelect = {}, onPlay = { _, _, _ -> }, onBack = {}, onSearch = {}, onSearchRequests = {}) }
    }

    @Test fun tvDetail_movie() = renderTvDetail(TestItems.movie()).also { assertRendered() }

    @Test fun tvDetail_episode() = renderTvDetail(TestItems.episode()).also { assertRendered() }

    @Test
    fun tvDetail_series() {
        val seasons = (1..2).map { TestItems.season(seed = 200 + it, index = it) }
        renderTvDetail(
            TestItems.series(),
            TVSeasonState(
                seriesDetails = TestItems.series(),
                seasons = seasons,
                episodesBySeasonId = mapOf(seasons[0].id.toString() to TestItems.episodes(4)),
                nextEpisode = TestItems.episode(index = 2),
                similarSeries = TestItems.seriesList(3),
            ),
        )
        assertRendered()
    }

    @Test
    fun tvSearch_results() {
        val vm: SearchViewModel = mockk(relaxed = true) {
            every { searchState } returns MutableStateFlow(SearchState(searchQuery = "test", searchResults = TestItems.movies(4) + TestItems.seriesList(2), hasSearched = true))
            every { aiState } returns MutableStateFlow(SearchAiState(lastEnhancedQuery = "test"))
        }
        TestViewModels.provide(SharedViewModelMocks.mainAppViewModel(loadedMainAppState()))
        render { TvSearchScreen(onItemSelect = {}, onBack = {}, onSearchRequests = {}, viewModel = vm, mainViewModel = SharedViewModelMocks.mainAppViewModel(loadedMainAppState())) }
        assertRendered()
    }

    @Test
    fun tvSearch_empty() {
        val vm: SearchViewModel = mockk(relaxed = true) {
            every { searchState } returns MutableStateFlow(SearchState(isSearching = true, errorMessage = "Timed out"))
            every { aiState } returns MutableStateFlow(SearchAiState(isProcessing = true))
        }
        render { TvSearchScreen(onItemSelect = {}, viewModel = vm, mainViewModel = SharedViewModelMocks.mainAppViewModel()) }
        assertRendered()
    }

    @Test
    fun tvSettings() {
        val connection: ServerConnectionViewModel = mockk(relaxed = true) {
            every { connectionState } returns MutableStateFlow(ConnectionState(savedServerUrl = "https://jellyfin.example.test", savedUsername = "tester", detectedServerType = ServerType.JELLYFIN))
        }
        val profiles: ProfilesViewModel = mockk(relaxed = true) {
            every { this@mockk.profiles } returns MutableStateFlow(
                ServerProfiles(listOf(ServerProfile(id = "p1", serverUrl = "https://jellyfin.example.test", serverName = "Home", username = "tester")), activeProfileId = "p1"),
            )
            every { uiState } returns MutableStateFlow(ProfilesUiState())
        }
        val playback: PlaybackPreferencesViewModel = mockk(relaxed = true) { every { preferences } returns MutableStateFlow(PlaybackPreferences.DEFAULT) }
        val subtitles: SubtitleAppearancePreferencesViewModel = mockk(relaxed = true) { every { preferences } returns MutableStateFlow(SubtitleAppearancePreferences.DEFAULT) }
        val theme: ThemePreferencesViewModel = mockk(relaxed = true) { every { themePreferences } returns MutableStateFlow(ThemePreferences.DEFAULT) }
        render {
            TvSettingsScreen(
                onSignOut = {},
                connectionViewModel = connection,
                profilesViewModel = profiles,
                playbackPreferencesViewModel = playback,
                subtitlePreferencesViewModel = subtitles,
                themePreferencesViewModel = theme,
            )
        }
        assertRendered()
    }

    @Test
    fun tvRequests() {
        val items = (1..4).map { com.rpeters.jellyfin.data.model.SeerrMediaItem(id = it, mediaType = if (it % 2 == 0) "tv" else "movie", title = "Title $it", name = "Name $it", releaseDate = "2026-01-01") }
        val vm: RequestsViewModel = mockk(relaxed = true) {
            every { uiState } returns MutableStateFlow(RequestsUiState(isConfigured = true, trending = items, popularMovies = items, popularTv = items, upcomingMovies = items, results = items, query = "q", successMessage = "OK", errorMessage = "Err"))
            every { seerrPreferences } returns MutableStateFlow(SeerrPreferences(baseUrl = "https://s.test", apiKey = "k", isEnabled = true))
        }
        render { TvRequestsScreen(onBack = {}, viewModel = vm) }
        assertRendered()
    }

    private val tvSections = listOf(
        TvHomeMediaSection("continue", "Continue Watching", TestItems.movies(4), isLoading = false),
        TvHomeMediaSection("shows", "Shows", TestItems.seriesList(3), isLoading = false),
        TvHomeMediaSection("loading", "Loading", emptyList(), isLoading = true),
    )

    @Test
    fun tvAdaptiveHome_carousel() {
        render {
            TvCarouselHomeContent(
                layoutConfig = LocalAdaptiveLayoutConfig.current,
                sections = tvSections,
                focusManager = rememberTvFocusManager(),
                header = {},
                onItemSelect = {},
                libraries = TestItems.libraries(),
                onLibrarySelect = {},
                isLoadingLibraries = false,
                initialFocusRequester = null,
                firstSectionId = "continue",
            )
        }
        assertRendered()
    }

    // endregion
}
