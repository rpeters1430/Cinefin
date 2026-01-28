package com.rpeters.jellyfin.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.jellyfin.data.preferences.CredentialSecurityPreferences
import com.rpeters.jellyfin.data.preferences.LibraryActionsPreferences
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import com.rpeters.jellyfin.ui.viewmodel.CredentialSecurityPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.LibraryActionsPreferencesViewModel
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
    private lateinit var credentialPreferencesFlow: MutableStateFlow<CredentialSecurityPreferences>
    private lateinit var isUpdatingFlow: MutableStateFlow<Boolean>
    private lateinit var libraryViewModel: LibraryActionsPreferencesViewModel
    private lateinit var credentialViewModel: CredentialSecurityPreferencesViewModel
    private var managePinsClicked = false

    @Before
    fun setup() {
        libraryPreferencesFlow = MutableStateFlow(LibraryActionsPreferences.DEFAULT)
        credentialPreferencesFlow = MutableStateFlow(CredentialSecurityPreferences.DEFAULT)
        isUpdatingFlow = MutableStateFlow(false)

        libraryViewModel = mockk(relaxed = true)
        credentialViewModel = mockk(relaxed = true)

        every { libraryViewModel.preferences } returns libraryPreferencesFlow
        every { credentialViewModel.preferences } returns credentialPreferencesFlow
        every { credentialViewModel.isUpdating } returns isUpdatingFlow

        coEvery { libraryViewModel.setManagementActionsEnabled(any()) } returns Unit
        coEvery { credentialViewModel.setStrongAuthRequired(any()) } returns Unit

        managePinsClicked = false
    }

    @Test
    fun `pinning management card is shown and actionable`() {
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                SettingsScreen(
                    onBackClick = {},
                    onManagePinsClick = { managePinsClicked = true },
                    libraryActionsPreferencesViewModel = libraryViewModel,
                    credentialSecurityPreferencesViewModel = credentialViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Certificate pins").assertIsDisplayed()
        composeTestRule.onNodeWithText("Manage pins").assertIsDisplayed()
        composeTestRule.onNodeWithText("Manage pins").performClick()

        assertTrue(managePinsClicked)
    }
}
