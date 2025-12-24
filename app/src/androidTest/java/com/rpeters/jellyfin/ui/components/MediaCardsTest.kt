package com.rpeters.jellyfin.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Comprehensive UI tests for MediaCard components.
 * Tests rendering, interactions, and accessibility features.
 *
 * Updated for Compose December 2025: Using StandardTestDispatcher for future compatibility.
 */
@RunWith(AndroidJUnit4::class)
class MediaCardsTest {

    @get:Rule
    val composeTestRule = createComposeRule(
        effectContext = StandardTestDispatcher(),
    )

    private fun createTestMovie(): BaseItemDto {
        return mockk<BaseItemDto>(relaxed = true) {
            every { id } returns UUID.randomUUID()
            every { name } returns "Test Movie"
            every { type } returns BaseItemKind.MOVIE
            every { productionYear } returns 2024
            every { communityRating } returns 8.5f
            every { userData?.isFavorite } returns false
        }
    }

    private fun createTestSeries(): BaseItemDto {
        return mockk<BaseItemDto>(relaxed = true) {
            every { id } returns UUID.randomUUID()
            every { name } returns "Test Series"
            every { type } returns BaseItemKind.SERIES
            every { productionYear } returns 2023
            every { communityRating } returns 9.2f
            every { userData?.isFavorite } returns true
        }
    }

    @Test
    fun mediaCard_displaysMovieInformation() {
        // Given
        val testMovie = createTestMovie()
        val mockGetImageUrl: (BaseItemDto) -> String? = { "https://example.com/image.jpg" }

        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                MediaCard(
                    item = testMovie,
                    getImageUrl = mockGetImageUrl,
                    onClick = { },
                )
            }
        }

        // Then
        composeTestRule.onNodeWithText("Test Movie").assertIsDisplayed()
        composeTestRule.onNodeWithText("2024").assertIsDisplayed()
        composeTestRule.onNodeWithText("Movie").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Test Movie poster image").assertIsDisplayed()
    }

    @Test
    fun mediaCard_displaysFavoriteIndicator() {
        // Given
        val testSeries = createTestSeries()
        val mockGetImageUrl: (BaseItemDto) -> String? = { null }

        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                MediaCard(
                    item = testSeries,
                    getImageUrl = mockGetImageUrl,
                    onClick = { },
                )
            }
        }

        // Then
        composeTestRule.onNodeWithText("Test Series").assertIsDisplayed()
        composeTestRule.onNodeWithText("Series").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Favorite").assertIsDisplayed()
    }

    @Test
    fun mediaCard_handlesClickEvents() {
        // Given
        val testMovie = createTestMovie()
        val mockGetImageUrl: (BaseItemDto) -> String? = { null }
        val mockOnClick: (BaseItemDto) -> Unit = mockk(relaxed = true)

        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                MediaCard(
                    item = testMovie,
                    getImageUrl = mockGetImageUrl,
                    onClick = mockOnClick,
                )
            }
        }

        // Then
        composeTestRule.onNodeWithText("Test Movie").performClick()
        verify { mockOnClick(testMovie) }
    }

    @Test
    fun mediaCard_displaysRatingIndicator() {
        // Given
        val testMovie = createTestMovie()
        val mockGetImageUrl: (BaseItemDto) -> String? = { null }

        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                MediaCard(
                    item = testMovie,
                    getImageUrl = mockGetImageUrl,
                    onClick = { },
                )
            }
        }

        // Then
        composeTestRule.onNodeWithText("8").assertIsDisplayed() // Rating shown as integer
    }

    @Test
    fun mediaCard_showsPlaceholderWhenNoImage() {
        // Given
        val testMovie = createTestMovie()
        val mockGetImageUrl: (BaseItemDto) -> String? = { null }

        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                MediaCard(
                    item = testMovie,
                    getImageUrl = mockGetImageUrl,
                    onClick = { },
                )
            }
        }

        // Then
        composeTestRule.onNodeWithContentDescription("No image available").assertIsDisplayed()
    }

    @Test
    fun recentlyAddedCard_displaysCorrectInformation() {
        // Given
        val testMovie = createTestMovie()
        val mockGetImageUrl: (BaseItemDto) -> String? = { "https://example.com/image.jpg" }
        val mockGetSeriesImageUrl: (BaseItemDto) -> String? = { "https://example.com/series.jpg" }

        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                RecentlyAddedCard(
                    item = testMovie,
                    getImageUrl = mockGetImageUrl,
                    getSeriesImageUrl = mockGetSeriesImageUrl,
                    onClick = { },
                )
            }
        }

        // Then
        composeTestRule.onNodeWithText("Test Movie").assertIsDisplayed()
        composeTestRule.onNodeWithText("2024").assertIsDisplayed()
        composeTestRule.onNodeWithText("Movie").assertIsDisplayed()
        composeTestRule.onNodeWithText("8").assertIsDisplayed() // Rating
    }

    @Test
    fun recentlyAddedCard_handlesEpisodeType() {
        // Given
        val testEpisode = mockk<BaseItemDto>(relaxed = true) {
            every { id } returns UUID.randomUUID()
            every { name } returns "Test Episode"
            every { type } returns BaseItemKind.EPISODE
            every { seriesId } returns UUID.randomUUID()
            every { productionYear } returns 2024
        }
        val mockGetImageUrl: (BaseItemDto) -> String? = { null }
        val mockGetSeriesImageUrl: (BaseItemDto) -> String? = { "https://example.com/series.jpg" }

        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                RecentlyAddedCard(
                    item = testEpisode,
                    getImageUrl = mockGetImageUrl,
                    getSeriesImageUrl = mockGetSeriesImageUrl,
                    onClick = { },
                )
            }
        }

        // Then
        composeTestRule.onNodeWithText("Test Episode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Episode").assertIsDisplayed()
    }

    @Test
    fun recentlyAddedCard_handlesClickEvents() {
        // Given
        val testMovie = createTestMovie()
        val mockGetImageUrl: (BaseItemDto) -> String? = { null }
        val mockGetSeriesImageUrl: (BaseItemDto) -> String? = { null }
        val mockOnClick: (BaseItemDto) -> Unit = mockk(relaxed = true)

        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                RecentlyAddedCard(
                    item = testMovie,
                    getImageUrl = mockGetImageUrl,
                    getSeriesImageUrl = mockGetSeriesImageUrl,
                    onClick = mockOnClick,
                )
            }
        }

        // Then
        composeTestRule.onNodeWithText("Test Movie").performClick()
        verify { mockOnClick(testMovie) }
    }

    @Test
    fun mediaCards_supportAccessibility() {
        // Given
        val testMovie = createTestMovie()
        val mockGetImageUrl: (BaseItemDto) -> String? = { null }

        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                MediaCard(
                    item = testMovie,
                    getImageUrl = mockGetImageUrl,
                    onClick = { },
                )
            }
        }

        // Then - Verify accessibility content descriptions exist
        composeTestRule.onNodeWithContentDescription("Test Movie poster image").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("No image available").assertIsDisplayed()
    }
}
