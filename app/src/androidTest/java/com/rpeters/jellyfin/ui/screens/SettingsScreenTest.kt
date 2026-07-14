package com.rpeters.jellyfin.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.model.CurrentUserDetails
import com.rpeters.jellyfin.data.preferences.AiPreferences
import com.rpeters.jellyfin.data.preferences.LibraryActionsPreferences
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import com.rpeters.jellyfin.ui.viewmodel.AiPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.LibraryActionsPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.RemoteConfigViewModel
import com.rpeters.jellyfin.ui.viewmodel.SettingsServerManagementState
import com.rpeters.jellyfin.ui.viewmodel.SettingsServerManagementViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule(
        effectContext = StandardTestDispatcher(),
    )

    private lateinit var libraryPreferencesFlow: MutableStateFlow<LibraryActionsPreferences>
    private lateinit var aiPreferencesFlow: MutableStateFlow<AiPreferences>
    private lateinit var serverManagementStateFlow: MutableStateFlow<SettingsServerManagementState>
    private lateinit var libraryViewModel: LibraryActionsPreferencesViewModel
    private lateinit var aiPreferencesViewModel: AiPreferencesViewModel
    private lateinit var remoteConfigViewModel: RemoteConfigViewModel
    private lateinit var serverManagementViewModel: SettingsServerManagementViewModel
    private var managePinsClicked = false
    private var switchServerClicked = false

    @Before
    fun setup() {
        libraryPreferencesFlow = MutableStateFlow(LibraryActionsPreferences.DEFAULT)
        aiPreferencesFlow = MutableStateFlow(AiPreferences.DEFAULT)
        serverManagementStateFlow = MutableStateFlow(SettingsServerManagementState())
        libraryViewModel = mockk(relaxed = true)
        aiPreferencesViewModel = mockk(relaxed = true)
        remoteConfigViewModel = mockk(relaxed = true)
        serverManagementViewModel = mockk(relaxed = true)

        every { libraryViewModel.preferences } returns libraryPreferencesFlow
        every { aiPreferencesViewModel.preferences } returns aiPreferencesFlow
        every { remoteConfigViewModel.getBoolean(any()) } returns true
        every { serverManagementViewModel.state } returns serverManagementStateFlow

        coEvery { libraryViewModel.setManagementActionsEnabled(any()) } returns Unit

        managePinsClicked = false
        switchServerClicked = false
    }

    @Test
    fun pinningManagementCardIsShownAndActionable() {
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                SettingsScreen(
                    onBackClick = {},
                    onManagePinsClick = { managePinsClicked = true },
                    libraryActionsPreferencesViewModel = libraryViewModel,
                    remoteConfigViewModel = remoteConfigViewModel,
                    aiPreferencesViewModel = aiPreferencesViewModel,
                    serverManagementViewModel = serverManagementViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Certificate pins").assertIsDisplayed()
        composeTestRule.onNodeWithText("Manage pins").assertIsDisplayed()
        composeTestRule.onNodeWithText("Manage pins").performClick()

        assertTrue(managePinsClicked)
    }

    @Test
    fun switchServerButtonIsShownAndActionable() {
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                SettingsScreen(
                    onBackClick = {},
                    currentServer = JellyfinServer(
                        id = "server-id",
                        name = "My Server",
                        url = "https://example.com",
                        isConnected = true,
                    ),
                    currentUser = CurrentUserDetails(
                        name = "Test User",
                        primaryImageTag = null,
                        lastLoginDate = null,
                        isAdministrator = false,
                    ),
                    onLogout = { switchServerClicked = true },
                    libraryActionsPreferencesViewModel = libraryViewModel,
                    remoteConfigViewModel = remoteConfigViewModel,
                    aiPreferencesViewModel = aiPreferencesViewModel,
                    serverManagementViewModel = serverManagementViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Switch Server").assertIsDisplayed()
        composeTestRule.onNodeWithText("Switch Server").performClick()

        assertTrue(switchServerClicked)
    }
}
