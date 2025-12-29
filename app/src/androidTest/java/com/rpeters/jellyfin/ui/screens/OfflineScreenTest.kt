package com.rpeters.jellyfin.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import com.rpeters.jellyfin.ui.utils.NetworkType
import com.rpeters.jellyfin.ui.utils.OfflineManager
import com.rpeters.jellyfin.ui.utils.OfflineStorageInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OfflineScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun offlineScreen_showsEmptyState() {
        val offlineManagerFake = OfflineManagerFake()

        composeTestRule.setContent {
            JellyfinAndroidTheme {
                OfflineScreen(
                    offlineManager = offlineManagerFake.offlineManager,
                )
            }
        }

        composeTestRule.onNodeWithText("No downloaded content").assertIsDisplayed()
        composeTestRule.onNodeWithText("Download content when online to watch offline").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear All").assertDoesNotExist()
    }

    @Test
    fun offlineScreen_showsDownloadsAndTriggersActions() {
        val item = createOfflineItem("Downloaded Movie")
        val offlineManagerFake = OfflineManagerFake(initialContent = listOf(item))
        val onPlay: (BaseItemDto) -> Unit = mockk(relaxed = true)
        val onDelete: (BaseItemDto) -> Unit = mockk(relaxed = true)

        composeTestRule.setContent {
            JellyfinAndroidTheme {
                OfflineScreen(
                    offlineManager = offlineManagerFake.offlineManager,
                    onPlayOfflineContent = onPlay,
                    onDeleteOfflineContent = onDelete,
                )
            }
        }

        composeTestRule.onNodeWithText("Downloaded Movie").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Play").performClick()
        composeTestRule.onNodeWithContentDescription("Delete").performClick()

        verify(exactly = 1) { onPlay(item) }
        verify(exactly = 1) { onDelete(item) }
    }

    @Test
    fun offlineScreen_clearAllDialogTriggersDeleteAll() {
        val offlineManagerFake = OfflineManagerFake(initialContent = listOf(createOfflineItem("Episode", BaseItemKind.EPISODE)))

        composeTestRule.setContent {
            JellyfinAndroidTheme {
                OfflineScreen(
                    offlineManager = offlineManagerFake.offlineManager,
                )
            }
        }

        composeTestRule.onNode(hasText("Clear All") and hasClickAction()).performClick()
        composeTestRule.onNodeWithText("Clear All Downloads").assertIsDisplayed()

        composeTestRule.onAllNodesWithText("Clear All").onLast().performClick()

        composeTestRule.onNodeWithText("Clear All Downloads").assertDoesNotExist()
        verify(exactly = 1) { offlineManagerFake.offlineManager.clearOfflineContent() }
    }

    private fun createOfflineItem(
        name: String,
        kind: BaseItemKind = BaseItemKind.MOVIE,
    ): BaseItemDto {
        return mockk(relaxed = true) {
            every { id } returns UUID.randomUUID().toString()
            every { type } returns kind
            every { this@mockk.name } returns name
            every { seriesName } returns if (kind == BaseItemKind.EPISODE) "Series Name" else null
        }
    }

    private class OfflineManagerFake(
        initialContent: List<BaseItemDto> = emptyList(),
    ) {
        private val isOnlineFlow = MutableStateFlow(true)
        private val networkTypeFlow = MutableStateFlow(NetworkType.WIFI)
        private val offlineContentFlow = MutableStateFlow(initialContent)

        val offlineManager: OfflineManager = mockk(relaxed = true) {
            every { isOnline } returns isOnlineFlow
            every { networkType } returns networkTypeFlow
            every { offlineContent } returns offlineContentFlow
            every { clearOfflineContent() } answers {
                offlineContentFlow.value = emptyList()
                true
            }
            every { getOfflineStorageUsage() } answers {
                val count = offlineContentFlow.value.size
                OfflineStorageInfo(
                    totalSizeBytes = (count * 1024L),
                    itemCount = count,
                    formattedSize = "$count.0 KB",
                )
            }
        }
    }
}
