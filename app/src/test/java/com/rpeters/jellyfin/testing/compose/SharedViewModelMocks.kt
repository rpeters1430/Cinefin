package com.rpeters.jellyfin.testing.compose

import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.offline.DownloadProgress
import com.rpeters.jellyfin.data.offline.DownloadStatus
import com.rpeters.jellyfin.data.offline.OfflineDownload
import com.rpeters.jellyfin.data.offline.OfflineStorageInfo
import com.rpeters.jellyfin.data.preferences.DownloadPreferences
import com.rpeters.jellyfin.testing.fixtures.TestItems
import com.rpeters.jellyfin.ui.downloads.DownloadsViewModel
import com.rpeters.jellyfin.ui.viewmodel.MainAppState
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Relaxed mocks of the ViewModels that many screens pull in through `hiltViewModel()`.
 * Relaxed mocks answer plain calls; every Flow a composable collects is stubbed with real state.
 */
object SharedViewModelMocks {

    val testServer = JellyfinServer(
        id = "server-1",
        name = "Test Server",
        url = "https://jellyfin.example.test",
        isConnected = true,
        version = "10.11.0",
        userId = "00000000-0000-0000-0000-000000000042",
        username = "tester",
        accessToken = "token",
    )

    fun sampleDownload(item: BaseItemDto = TestItems.movie(), status: DownloadStatus = DownloadStatus.COMPLETED) = OfflineDownload(
        id = "dl-${item.id}",
        jellyfinItemId = item.id.toString(),
        itemName = item.name ?: "Item",
        itemType = item.type.serialName,
        downloadUrl = "https://jellyfin.example.test/Items/${item.id}/Download",
        localFilePath = "/data/downloads/${item.id}.mkv",
        fileSize = 4_000_000_000L,
        downloadedBytes = if (status == DownloadStatus.COMPLETED) 4_000_000_000L else 1_500_000_000L,
        status = status,
        seriesName = item.seriesName,
        seasonNumber = item.parentIndexNumber,
        episodeNumber = item.indexNumber,
        overview = item.overview,
        productionYear = item.productionYear,
        runtimeTicks = item.runTimeTicks,
    )

    fun downloadsViewModel(
        downloads: List<OfflineDownload> = emptyList(),
        progress: Map<String, DownloadProgress> = emptyMap(),
        storage: OfflineStorageInfo? = null,
    ): DownloadsViewModel = mockk(relaxed = true) {
        every { this@mockk.downloads } returns MutableStateFlow(downloads)
        every { downloadProgress } returns MutableStateFlow(progress)
        every { downloadPreferences } returns MutableStateFlow(DownloadPreferences.DEFAULT)
        every { pendingOfflineSyncCount } returns MutableStateFlow(0)
        every { storageInfo } returns MutableStateFlow(storage)
    }

    fun mainAppViewModel(state: MainAppState = MainAppState()): MainAppViewModel = mockk(relaxed = true) {
        every { appState } returns MutableStateFlow(state)
        every { currentServer } returns MutableStateFlow(testServer)
        every { isConnected } returns MutableStateFlow(true)
        every { isAdultVerified } returns MutableStateFlow(true)
        every { ageSignalsStatus } returns MutableStateFlow(null)
        every { serverInfo } returns MutableStateFlow(null)
        every { aiDownloadState } returns MutableStateFlow(com.rpeters.jellyfin.data.ai.AiDownloadState.READY)
        every { isNanoActive } returns MutableStateFlow(false)
        every { getImageUrl(any()) } returns null
        every { getBackdropUrl(any()) } returns null
        every { getLibraryTypeData(any<com.rpeters.jellyfin.ui.screens.LibraryType>()) } returns state.allItems
        every { getLibraryTypeData(any<BaseItemDto>()) } returns state.allItems
        every { libraryItemKey(any()) } answers { firstArg<BaseItemDto>().id.toString() }
    }

    /** Registers both shared mocks; returns them for further stubbing or verification. */
    fun provideDefaults(
        main: MainAppViewModel = mainAppViewModel(),
        downloads: DownloadsViewModel = downloadsViewModel(),
    ): Pair<MainAppViewModel, DownloadsViewModel> {
        TestViewModels.provide(main)
        TestViewModels.provide(downloads)
        return main to downloads
    }
}

/** A fully loaded home/library state: every row the home and library screens can show has items. */
fun loadedMainAppState(): MainAppState {
    val movies = TestItems.movies(8)
    val shows = TestItems.seriesList(6)
    val episodes = TestItems.episodes(6)
    val tracks = TestItems.tracks(6)
    val videos = (1..4).map { TestItems.homeVideo(seed = 420 + it) }
    val libraries = TestItems.libraries()
    return MainAppState(
        libraries = libraries,
        recentlyAdded = movies + shows,
        recentlyAddedByTypes = mapOf(
            org.jellyfin.sdk.model.api.BaseItemKind.MOVIE.name to movies,
            org.jellyfin.sdk.model.api.BaseItemKind.SERIES.name to shows,
            org.jellyfin.sdk.model.api.BaseItemKind.EPISODE.name to episodes,
            org.jellyfin.sdk.model.api.BaseItemKind.AUDIO.name to tracks,
            org.jellyfin.sdk.model.api.BaseItemKind.VIDEO.name to videos,
        ),
        recentlyAddedByLibrary = libraries.associate { it.id.toString() to movies.take(4) },
        allMovies = movies,
        allTVShows = shows,
        allItems = movies + shows + tracks + videos + listOf(TestItems.album()),
        itemsByLibrary = libraries.associate { it.id.toString() to (movies + shows + tracks + videos) },
        continueWatching = movies.take(3) + episodes.take(2),
        nextUp = episodes.drop(2),
        favorites = movies.take(2) + shows.take(2),
        currentUser = com.rpeters.jellyfin.data.model.CurrentUserDetails("tester", "tag", "2026-11-01", true),
        viewingMood = "Cozy sci-fi evenings",
        moodCollections = mapOf("Cozy" to movies.take(3), "Thrilling" to shows.take(3)),
        aiHealthCheckPassed = true,
        aiHealthStatusMessage = "Healthy",
        hasMoreMovies = true,
        hasMoreTVShows = true,
        hasMoreItems = true,
    )
}
