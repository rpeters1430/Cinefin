package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.jellyfin.ui.screens.home.HomeContentLists
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import com.rpeters.jellyfin.ui.viewmodel.MainAppState
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ImmersiveHomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule(
        effectContext = StandardTestDispatcher(),
    )

    // =========================================================
    // Test helpers
    // =========================================================

    private fun makeMovie(name: String = "Test Movie"): BaseItemDto =
        mockk<BaseItemDto>(relaxed = true) {
            io.mockk.every { id } returns UUID.randomUUID()
            io.mockk.every { this@mockk.name } returns name
            io.mockk.every { type } returns BaseItemKind.MOVIE
        }

    private fun makeEpisode(
        name: String = "Test Episode",
        seriesName: String = "Test Series",
    ): BaseItemDto =
        mockk<BaseItemDto>(relaxed = true) {
            io.mockk.every { id } returns UUID.randomUUID()
            io.mockk.every { this@mockk.name } returns name
            io.mockk.every { type } returns BaseItemKind.EPISODE
            io.mockk.every { this@mockk.seriesName } returns seriesName
        }

    private fun makeLibrary(name: String = "Movies"): BaseItemDto =
        mockk<BaseItemDto>(relaxed = true) {
            io.mockk.every { id } returns UUID.randomUUID()
            io.mockk.every { this@mockk.name } returns name
            io.mockk.every { type } returns BaseItemKind.COLLECTION_FOLDER
        }

    private fun setHomeContent(
        appState: MainAppState = MainAppState(),
        contentLists: HomeContentLists = HomeContentLists(),
        viewingMood: String? = null,
        onItemClick: (BaseItemDto) -> Unit = {},
        onItemLongPress: (BaseItemDto) -> Unit = {},
        onLibraryClick: (BaseItemDto) -> Unit = {},
    ) {
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                MobileExpressiveHomeContent(
                    appState = appState,
                    contentLists = contentLists,
                    getImageUrl = { null },
                    getBackdropUrl = { null },
                    getSeriesImageUrl = { null },
                    onItemClick = onItemClick,
                    onItemLongPress = onItemLongPress,
                    onLibraryClick = onLibraryClick,
                    viewingMood = viewingMood,
                    listState = androidx.compose.foundation.lazy.rememberLazyListState(),
                    contentPadding = PaddingValues(),
                    bottomSpacing = androidx.compose.ui.unit.Dp.Hairline,
                )
            }
        }
    }

    // =========================================================
    // Hero carousel
    // =========================================================

    @Test
    fun heroCarousel_withMovies_isDisplayed() {
        val movie = makeMovie("Inception")
        setHomeContent(contentLists = HomeContentLists(recentMovies = listOf(movie)))

        composeTestRule.onNodeWithText("Inception").assertIsDisplayed()
    }

    @Test
    fun heroCarousel_withNoMovies_isNotRendered() {
        setHomeContent(contentLists = HomeContentLists(recentMovies = emptyList()))

        composeTestRule.onNodeWithText("Inception").assertDoesNotExist()
    }

    // =========================================================
    // Continue watching
    // =========================================================

    @Test
    fun continueWatching_withItems_sectionVisible() {
        val item = makeMovie("Partially Watched Movie")
        setHomeContent(
            appState = MainAppState(continueWatching = listOf(item)),
            contentLists = HomeContentLists(continueWatching = listOf(item)),
        )

        composeTestRule.onNodeWithText("Partially Watched Movie").assertIsDisplayed()
    }

    @Test
    fun continueWatching_empty_sectionAbsent() {
        setHomeContent(
            appState = MainAppState(continueWatching = emptyList()),
            contentLists = HomeContentLists(continueWatching = emptyList()),
        )

        composeTestRule.onNodeWithText("Partially Watched Movie").assertDoesNotExist()
    }

    // =========================================================
    // Next up
    // =========================================================

    @Test
    fun nextUp_withEpisodes_sectionVisible() {
        val episode = makeEpisode("Pilot")
        setHomeContent(
            contentLists = HomeContentLists(recentEpisodes = listOf(episode)),
        )

        composeTestRule.onNodeWithText("Pilot").assertIsDisplayed()
    }

    @Test
    fun nextUp_empty_sectionAbsent() {
        setHomeContent(
            contentLists = HomeContentLists(recentEpisodes = emptyList()),
        )

        composeTestRule.onNodeWithText("Pilot").assertDoesNotExist()
    }

    // =========================================================
    // Libraries
    // =========================================================

    @Test
    fun libraries_withItems_carouselVisible() {
        val library = makeLibrary("My Movies")
        setHomeContent(appState = MainAppState(libraries = listOf(library)))

        composeTestRule.onNodeWithText("My Movies").assertIsDisplayed()
    }

    @Test
    fun libraries_empty_carouselAbsent() {
        setHomeContent(appState = MainAppState(libraries = emptyList()))

        composeTestRule.onNodeWithText("My Movies").assertDoesNotExist()
    }

    // =========================================================
    // Viewing mood widget
    // =========================================================

    @Test
    fun viewingMoodWidget_whenMoodSet_isVisible() {
        setHomeContent(viewingMood = "Action mood")

        composeTestRule.onNodeWithText("Action mood").assertIsDisplayed()
    }

    @Test
    fun viewingMoodWidget_whenMoodNull_isHidden() {
        setHomeContent(viewingMood = null)

        composeTestRule.onNodeWithText("Action mood").assertDoesNotExist()
    }

    @Test
    fun viewingMoodWidget_whenMoodEmpty_isHidden() {
        setHomeContent(viewingMood = "")

        composeTestRule.onNodeWithText("").assertDoesNotExist()
    }

    // =========================================================
    // Interactions
    // =========================================================

    @Test
    fun itemClick_firesWithCorrectItem() {
        val movieId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val movie = mockk<BaseItemDto>(relaxed = true) {
            io.mockk.every { id } returns movieId
            io.mockk.every { this@mockk.name } returns "Click Me"
            io.mockk.every { type } returns BaseItemKind.MOVIE
        }
        val onItemClick = mockk<(BaseItemDto) -> Unit>(relaxed = true)

        setHomeContent(
            contentLists = HomeContentLists(recentMovies = listOf(movie)),
            onItemClick = onItemClick,
        )

        composeTestRule.onNodeWithText("Click Me").performClick()

        verify { onItemClick(movie) }
    }

    // =========================================================
    // Empty state
    // =========================================================

    @Test
    fun emptyState_noMoviesNoLibraries_doesNotCrash() {
        setHomeContent(
            appState = MainAppState(),
            contentLists = HomeContentLists(),
            viewingMood = null,
        )
        // No assertion needed — just verifying it does not crash
    }
}
