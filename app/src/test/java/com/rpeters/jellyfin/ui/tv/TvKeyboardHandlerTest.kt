package com.rpeters.jellyfin.ui.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.jellyfin.TestCinefinApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestCinefinApplication::class)
class TvKeyboardHandlerTest {
    private companion object {
        const val TEST_TAG = "tv_keyboard_target"
    }

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tvKeyboardHandler_dispatchesConfiguredGlobalKeys() {
        val counters = KeyCounters()

        composeRule.setContent {
            val focusRequester = remember { FocusRequester() }

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .testTag(TEST_TAG)
                    .focusRequester(focusRequester)
                    .focusable()
                    .tvKeyboardHandler(
                        onBack = { counters.back++ },
                        onMenu = { counters.menu++ },
                        onSearch = { counters.search++ },
                        onPlayPause = { counters.playPause++ },
                        onSeekForward = { counters.seekForward++ },
                        onSeekBackward = { counters.seekBackward++ },
                        onInfo = { counters.info++ },
                        onGuide = { counters.guide++ },
                        onChannelUp = { counters.channelUp++ },
                        onChannelDown = { counters.channelDown++ },
                        onQuickAccess = { counters.quickAccess = it },
                    ),
            )

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TEST_TAG).performKeyInput {
            pressKey(Key.Back)
            pressKey(Key.Menu)
            pressKey(Key.Search)
            pressKey(Key.MediaPlayPause)
            pressKey(Key.MediaFastForward)
            pressKey(Key.MediaRewind)
            pressKey(Key.Info)
            pressKey(Key.Guide)
            pressKey(Key.ChannelUp)
            pressKey(Key.ChannelDown)
            pressKey(Key.Four)
        }

        composeRule.runOnIdle {
            assertEquals(1, counters.back)
            assertEquals(1, counters.menu)
            assertEquals(1, counters.search)
            assertEquals(1, counters.playPause)
            assertEquals(1, counters.seekForward)
            assertEquals(1, counters.seekBackward)
            assertEquals(1, counters.info)
            assertEquals(1, counters.guide)
            assertEquals(1, counters.channelUp)
            assertEquals(1, counters.channelDown)
            assertEquals(4, counters.quickAccess)
        }
    }

    @Test
    fun tvKeyboardHandler_ignoresUnboundRemoteKeys() {
        assertFalse(
            TvKeyboardHandler.handleGlobalTvKeys(
                keyEvent = composeKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
            ),
        )
        assertFalse(
            TvKeyboardHandler.handleGlobalTvKeys(
                keyEvent = composeKeyEvent(android.view.KeyEvent.KEYCODE_INFO),
            ),
        )
        assertFalse(
            TvKeyboardHandler.handleGlobalTvKeys(
                keyEvent = composeKeyEvent(android.view.KeyEvent.KEYCODE_CHANNEL_UP),
            ),
        )
    }

    private class KeyCounters {
        var back: Int = 0
        var menu: Int = 0
        var search: Int = 0
        var playPause: Int = 0
        var seekForward: Int = 0
        var seekBackward: Int = 0
        var info: Int = 0
        var guide: Int = 0
        var channelUp: Int = 0
        var channelDown: Int = 0
        var quickAccess: Int = -1
    }

    private fun composeKeyEvent(keyCode: Int): androidx.compose.ui.input.key.KeyEvent {
        return androidx.compose.ui.input.key.KeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode),
        )
    }
}
