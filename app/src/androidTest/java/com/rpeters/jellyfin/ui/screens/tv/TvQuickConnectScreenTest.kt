package com.rpeters.jellyfin.ui.screens.tv

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.jellyfin.ui.screens.tv.TvQuickConnectTestTags.CANCEL_BUTTON
import com.rpeters.jellyfin.ui.screens.tv.TvQuickConnectTestTags.CODE_CARD
import com.rpeters.jellyfin.ui.screens.tv.TvQuickConnectTestTags.CODE_TEXT
import com.rpeters.jellyfin.ui.screens.tv.TvQuickConnectTestTags.GET_CODE_BUTTON
import com.rpeters.jellyfin.ui.screens.tv.TvQuickConnectTestTags.SERVER_INPUT
import com.rpeters.jellyfin.ui.screens.tv.TvQuickConnectTestTags.STATUS_CARD
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvQuickConnectScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun serverInputFocusedWhenUrlEmpty() {
        composeTestRule.setContent {
            TvQuickConnectScreen(
                serverUrl = "",
                quickConnectCode = "",
                isConnecting = false,
                isPolling = false,
                status = "",
                errorMessage = null,
                onServerUrlChange = {},
                onInitiateQuickConnect = {},
                onCancel = {},
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(SERVER_INPUT).assertIsFocused()
    }

    @Test
    fun fieldsDisabledDuringPollingAndReenabledAfter() {
        composeTestRule.setContent {
            TvQuickConnectScreen(
                serverUrl = "https://example.com",
                quickConnectCode = "CODE",
                isConnecting = false,
                isPolling = true,
                status = "Waiting",
                errorMessage = null,
                onServerUrlChange = {},
                onInitiateQuickConnect = {},
                onCancel = {},
            )
        }

        composeTestRule.onNodeWithTag(SERVER_INPUT).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(GET_CODE_BUTTON).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(CANCEL_BUTTON).assertIsEnabled()

        composeTestRule.setContent {
            TvQuickConnectScreen(
                serverUrl = "https://example.com",
                quickConnectCode = "CODE",
                isConnecting = false,
                isPolling = false,
                status = "Ready",
                errorMessage = null,
                onServerUrlChange = {},
                onInitiateQuickConnect = {},
                onCancel = {},
            )
        }

        composeTestRule.onNodeWithTag(SERVER_INPUT).assertIsEnabled()
        composeTestRule.onNodeWithTag(GET_CODE_BUTTON).assertIsEnabled()
        composeTestRule.onNodeWithTag(CANCEL_BUTTON).assertIsEnabled()
    }

    @Test
    fun quickConnectCodeCardDisplaysCode() {
        composeTestRule.setContent {
            TvQuickConnectScreen(
                serverUrl = "https://example.com",
                quickConnectCode = "ABCDEF",
                isConnecting = false,
                isPolling = false,
                status = "",
                errorMessage = null,
                onServerUrlChange = {},
                onInitiateQuickConnect = {},
                onCancel = {},
            )
        }

        composeTestRule.onNodeWithTag(CODE_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithTag(CODE_TEXT).assertTextEquals("ABCDEF")
    }

    @Test
    fun statusCardVisibleDuringPolling() {
        composeTestRule.setContent {
            TvQuickConnectScreen(
                serverUrl = "https://example.com",
                quickConnectCode = "CODE",
                isConnecting = false,
                isPolling = true,
                status = "Waiting",
                errorMessage = null,
                onServerUrlChange = {},
                onInitiateQuickConnect = {},
                onCancel = {},
            )
        }

        composeTestRule.onNodeWithTag(STATUS_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Waiting").assertExists()
        composeTestRule.onNodeWithText("Waiting").assertIsDisplayed()
    }

    @Test
    fun statusCardHiddenWhenEmpty() {
        composeTestRule.setContent {
            TvQuickConnectScreen(
                serverUrl = "https://example.com",
                quickConnectCode = "CODE",
                isConnecting = false,
                isPolling = false,
                status = "",
                errorMessage = null,
                onServerUrlChange = {},
                onInitiateQuickConnect = {},
                onCancel = {},
            )
        }

        composeTestRule.onNodeWithTag(STATUS_CARD).assertDoesNotExist()
    }
}
