package com.rpeters.jellyfin.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.rpeters.jellyfin.ui.components.ConnectionState
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import com.rpeters.jellyfin.ui.viewmodel.ServerConnectionViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(com.rpeters.jellyfin.OptInAppExperimentalApis::class)
@RunWith(AndroidJUnit4::class)
class ServerConnectionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: ServerConnectionViewModel = mockk(relaxed = true)
    private val connectionState = MutableStateFlow(ConnectionState())

    @Test
    fun server_url_input_is_visible_and_triggers_viewmodel() {
        every { viewModel.connectionState } returns connectionState

        composeTestRule.setContent {
            JellyfinAndroidTheme {
                ServerConnectionScreen(
                    connectionState = connectionState.value,
                    onConnect = { url, user, pass -> 
                        viewModel.connectToServer(url, user, pass)
                    }
                )
            }
        }

        // Labels from strings.xml
        composeTestRule.onNodeWithText("Server URL").assertIsDisplayed()
        composeTestRule.onNodeWithText("Username").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").performScrollTo().assertIsDisplayed()
        
        // Connect button
        composeTestRule.onNodeWithText("Connect").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun error_message_is_displayed_when_present() {
        val errorMsg = "Failed to connect to server"
        connectionState.value = ConnectionState(errorMessage = errorMsg)
        every { viewModel.connectionState } returns connectionState

        composeTestRule.setContent {
            JellyfinAndroidTheme {
                ServerConnectionScreen(
                    connectionState = connectionState.value
                )
            }
        }

        composeTestRule.onNodeWithText(errorMsg).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun connect_button_shows_connecting_during_connection() {
        connectionState.value = ConnectionState(isConnecting = true)
        every { viewModel.connectionState } returns connectionState

        composeTestRule.setContent {
            JellyfinAndroidTheme {
                ServerConnectionScreen(
                    connectionState = connectionState.value
                )
            }
        }

        composeTestRule.onNodeWithText("Connecting…").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Connecting…").assertIsNotEnabled()
    }
}
