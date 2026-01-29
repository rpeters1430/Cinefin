package com.rpeters.jellyfin.ui.player

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.media3.common.Format
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.jellyfin.data.preferences.SubtitleAppearancePreferences
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoPlayerScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pipButton_isClickable_whenSupported() {
        var clicked = false

        setContent(
            state = baseState(),
            supportsPip = true,
            onPictureInPictureClick = { clicked = true },
        )

        composeRule.onNodeWithTag(VideoPlayerTestTags.PipButton)
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(clicked)
        }
    }

    @Test
    fun pipButton_isNotClickable_whenUnsupported() {
        setContent(
            state = baseState(),
            supportsPip = false,
        )

        composeRule.onNodeWithTag(VideoPlayerTestTags.PipButton)
            .assertHasNoClickAction()
    }

    @Test
    fun subtitlesButton_onlyShowsWhenTracksAvailable() {
        setContent(
            state = baseState(availableSubtitleTracks = emptyList()),
            supportsPip = true,
        )

        composeRule.onNodeWithTag(VideoPlayerTestTags.SubtitlesButton).assertDoesNotExist()

        val subtitleTrack = buildTrack(
            groupIndex = 0,
            trackIndex = 0,
            displayName = "English CC",
        )

        setContent(
            state = baseState(availableSubtitleTracks = listOf(subtitleTrack)),
            supportsPip = true,
        )

        composeRule.onNodeWithTag(VideoPlayerTestTags.SubtitlesButton).assertIsDisplayed()
    }

    @Test
    fun audioTracksButton_onlyShowsWhenMultipleTracksAvailable() {
        val singleTrack = buildTrack(
            groupIndex = 0,
            trackIndex = 0,
            displayName = "English",
        )

        setContent(
            state = baseState(availableAudioTracks = listOf(singleTrack)),
            supportsPip = true,
        )

        composeRule.onNodeWithTag(VideoPlayerTestTags.AudioTracksButton).assertDoesNotExist()

        val secondTrack = buildTrack(
            groupIndex = 1,
            trackIndex = 0,
            displayName = "Spanish",
        )

        setContent(
            state = baseState(availableAudioTracks = listOf(singleTrack, secondTrack)),
            supportsPip = true,
        )

        composeRule.onNodeWithTag(VideoPlayerTestTags.AudioTracksButton).assertIsDisplayed()
    }

    @Test
    fun castOverlay_isVisibleWhenCasting() {
        setContent(
            state = baseState(
                isCastConnected = true,
                castDeviceName = "Living Room TV",
            ),
            supportsPip = true,
        )

        composeRule.onNodeWithTag(VideoPlayerTestTags.CastOverlay).assertIsDisplayed()
    }

    private fun setContent(
        state: VideoPlayerState,
        supportsPip: Boolean,
        onPictureInPictureClick: () -> Unit = {},
    ) {
        composeRule.setContent {
            JellyfinAndroidTheme {
                VideoPlayerScreen(
                    playerState = state,
                    subtitleAppearance = SubtitleAppearancePreferences.DEFAULT,
                    onPlayPause = {},
                    onSeek = {},
                    onQualityChange = {},
                    onPlaybackSpeedChange = {},
                    onAspectRatioChange = {},
                    onCastClick = {},
                    onCastPause = {},
                    onCastResume = {},
                    onCastStop = {},
                    onCastDisconnect = {},
                    onCastSeek = {},
                    onCastVolumeChange = {},
                    onSubtitlesClick = {},
                    onPictureInPictureClick = onPictureInPictureClick,
                    onOrientationToggle = {},
                    onAudioTrackSelect = {},
                    onSubtitleTrackSelect = {},
                    onSubtitleDialogDismiss = {},
                    onCastDeviceSelect = {},
                    onCastDialogDismiss = {},
                    onErrorDismiss = {},
                    onClose = {},
                    onPlayNextEpisode = {},
                    onCancelNextEpisode = {},
                    onPlayerViewBoundsChanged = {},
                    exoPlayer = null,
                    supportsPip = supportsPip,
                )
            }
        }
    }

    private fun baseState(
        availableAudioTracks: List<TrackInfo> = emptyList(),
        availableSubtitleTracks: List<TrackInfo> = emptyList(),
        isCastConnected: Boolean = false,
        castDeviceName: String? = null,
    ): VideoPlayerState {
        return VideoPlayerState(
            itemName = "Test Video",
            duration = 60_000L,
            availableAudioTracks = availableAudioTracks,
            availableSubtitleTracks = availableSubtitleTracks,
            isCastConnected = isCastConnected,
            castDeviceName = castDeviceName,
        )
    }

    private fun buildTrack(
        groupIndex: Int,
        trackIndex: Int,
        displayName: String,
    ): TrackInfo {
        val format = Format.Builder()
            .setLanguage("en")
            .build()

        return TrackInfo(
            groupIndex = groupIndex,
            trackIndex = trackIndex,
            format = format,
            isSelected = false,
            displayName = displayName,
        )
    }
}
