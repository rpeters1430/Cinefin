package com.rpeters.jellyfin.ui.screens

import androidx.compose.ui.test.onAllNodesWithText
import com.rpeters.jellyfin.testing.compose.ComposeScreenTest
import com.rpeters.jellyfin.testing.compose.SharedViewModelMocks
import com.rpeters.jellyfin.testing.fixtures.TestItems
import com.rpeters.jellyfin.ui.player.PlaybackProgress
import org.jellyfin.sdk.model.api.BaseItemDto
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImmersiveMovieDetailScreenTest : ComposeScreenTest() {

    @Before
    fun setUp() {
        SharedViewModelMocks.provideDefaults()
    }

    private fun renderMovie(
        movie: BaseItemDto = TestItems.movie(),
        isDownloaded: Boolean = false,
        isOffline: Boolean = false,
        progress: PlaybackProgress? = null,
        aiSummary: String? = null,
        whyYoullLoveThis: String? = null,
        loadingAi: Boolean = false,
        warnings: List<String> = emptyList(),
        supportsFavorites: Boolean = true,
        isRefreshing: Boolean = false,
    ) = render {
        ImmersiveMovieDetailScreen(
            movie = movie,
            relatedItems = TestItems.movies(6),
            playbackProgress = progress,
            onBackClick = {},
            onPlayClick = { _, _, _ -> },
            onFavoriteClick = {},
            onShareClick = {},
            onDeleteClick = {},
            onMarkWatchedClick = {},
            onDownloadClick = { _, _ -> },
            isDownloaded = isDownloaded,
            isOffline = isOffline,
            downloadInfo = if (isDownloaded) SharedViewModelMocks.sampleDownload(movie) else null,
            onDeleteOfflineCopy = {},
            onRelatedMovieClick = {},
            onPersonClick = { _, _ -> },
            onRefresh = {},
            isRefreshing = isRefreshing,
            whyYoullLoveThis = whyYoullLoveThis,
            isLoadingWhyYoullLoveThis = loadingAi,
            contentWarnings = warnings,
            isLoadingContentWarnings = loadingAi,
            aiChapterMarkers = if (loadingAi) emptyList() else TestItems.chapters(),
            isLoadingAiChapterMarkers = loadingAi,
            getImageUrl = { "https://img.test/${it.id}" },
            getChapterImageUrl = { _, _ -> null },
            getBackdropUrl = { null },
            getPersonImageUrl = { null },
            serverUrl = "https://jellyfin.example.test",
            onGenerateAiSummary = {},
            aiSummary = aiSummary,
            isLoadingAiSummary = loadingAi,
            supportsFavorites = supportsFavorites,
        )
    }

    @Test
    fun fullMovie_showsTitleAndOverview() {
        renderMovie()
        assertTrue(compose.onAllNodesWithText("The Test Movie", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun withProgressAndAiContent_renders() {
        renderMovie(
            progress = PlaybackProgress(itemId = "x", positionMs = 600_000, durationMs = 7_200_000, percentageWatched = 0.1f),
            aiSummary = "Short summary.",
            whyYoullLoveThis = "Because tests.",
            warnings = listOf("Violence", "Language"),
        )
        assertRendered()
    }

    @Test
    fun loadingAiAndRefreshing_renders() {
        renderMovie(loadingAi = true, isRefreshing = true)
        assertRendered()
    }

    @Test
    fun downloadedAndOffline_renders() {
        renderMovie(isDownloaded = true, isOffline = true, supportsFavorites = false)
        assertRendered()
    }

    @Test
    fun sparseMovie_rendersWithoutOptionalFields() {
        val sparse = BaseItemDto(id = TestItems.id(77), name = "Bare", type = org.jellyfin.sdk.model.api.BaseItemKind.MOVIE)
        renderMovie(movie = sparse)
        assertRendered()
    }
}
