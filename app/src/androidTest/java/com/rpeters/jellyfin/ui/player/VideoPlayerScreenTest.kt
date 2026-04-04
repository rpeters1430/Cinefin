package com.rpeters.jellyfin.ui.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.jellyfin.data.playback.QualityRecommendation
import com.rpeters.jellyfin.data.playback.RecommendationSeverity
import com.rpeters.jellyfin.data.preferences.TranscodingQuality
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class VideoPlayerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun castOverlay_isVisibleWhenCasting() {
        composeRule.setContent {
            JellyfinAndroidTheme {
                CastRemoteScreen(
                    playerState = VideoPlayerState(
                        itemName = "Test Video",
                        isCastConnected = true,
                        castDeviceName = "Living Room TV",
                        castDuration = 60_000L,
                        castPosition = 10_000L,
                    ),
                    onPauseCast = {},
                    onResumeCast = {},
                    onStopCast = {},
                    onSeekCast = {},
                    onDisconnectCast = {},
                )
            }
        }

        composeRule.onNodeWithTag(VideoPlayerTestTags.CastOverlay).assertIsDisplayed()
        composeRule.onNodeWithText("Playing on Living Room TV").assertIsDisplayed()
    }

    @Test
    fun qualityRecommendationNotification_acceptInvokesCallback() {
        var accepted = false

        composeRule.setContent {
            JellyfinAndroidTheme {
                QualityRecommendationNotification(
                    recommendation = QualityRecommendation(
                        recommendedQuality = TranscodingQuality.MEDIUM,
                        reason = "Frequent buffering",
                        severity = RecommendationSeverity.MEDIUM,
                    ),
                    onAccept = { accepted = true },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Switch Quality").performClick()

        composeRule.runOnIdle {
            assertTrue(accepted)
        }
    }
}
