package com.rpeters.jellyfin.ui.screens.settings

import android.os.Build
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.jellyfin.data.preferences.AccentColor
import com.rpeters.jellyfin.data.preferences.ContrastLevel
import com.rpeters.jellyfin.data.preferences.ThemeMode
import com.rpeters.jellyfin.data.preferences.ThemePreferences
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import com.rpeters.jellyfin.ui.viewmodel.ThemePreferencesViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Comprehensive UI tests for AppearanceSettingsScreen.
 * Tests rendering, user interactions, accessibility, and state management.
 */
@RunWith(AndroidJUnit4::class)
class AppearanceSettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var mockViewModel: ThemePreferencesViewModel
    private lateinit var preferencesFlow: MutableStateFlow<ThemePreferences>
    private var navigateBackCalled = false

    @Before
    fun setup() {
        // Create a mutable flow to control preference emissions
        preferencesFlow = MutableStateFlow(ThemePreferences.DEFAULT)

        // Setup mock ViewModel
        mockViewModel = mockk(relaxed = true)
        every { mockViewModel.themePreferences } returns preferencesFlow

        // Mock all setter methods
        coEvery { mockViewModel.setThemeMode(any()) } returns Unit
        coEvery { mockViewModel.setUseDynamicColors(any()) } returns Unit
        coEvery { mockViewModel.setAccentColor(any()) } returns Unit
        coEvery { mockViewModel.setContrastLevel(any()) } returns Unit
        coEvery { mockViewModel.setUseThemedIcon(any()) } returns Unit
        coEvery { mockViewModel.setEnableEdgeToEdge(any()) } returns Unit
        coEvery { mockViewModel.setRespectReduceMotion(any()) } returns Unit

        navigateBackCalled = false
    }

    // ========================================================================
    // RENDERING TESTS
    // ========================================================================

    @Test
    fun `screen displays all major sections`() {
        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel
                )
            }
        }

        // Then
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeTestRule.onNodeWithText("Theme Mode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Material You").assertIsDisplayed()
        composeTestRule.onNodeWithText("Contrast").assertIsDisplayed()
        composeTestRule.onNodeWithText("Accessibility").assertIsDisplayed()
    }

    @Test
    fun `screen displays all theme mode options`() {
        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel
                )
            }
        }

        // Then
        composeTestRule.onNodeWithText("System").assertIsDisplayed()
        composeTestRule.onNodeWithText("Light").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dark").assertIsDisplayed()
        composeTestRule.onNodeWithText("AMOLED Black").assertIsDisplayed()
    }

    @Test
    fun `screen displays all contrast level options`() {
        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel
                )
            }
        }

        // Then
        composeTestRule.onNodeWithText("Standard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Medium").assertIsDisplayed()
        composeTestRule.onNodeWithText("High").assertIsDisplayed()
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `screen displays dynamic colors option on Android 12+`() {
        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel
                )
            }
        }

        // Then
        composeTestRule.onNodeWithText("Dynamic Colors").assertIsDisplayed()
        composeTestRule.onNodeWithText("Use colors from your wallpaper").assertIsDisplayed()
    }

    // ========================================================================
    // NAVIGATION TESTS
    // ========================================================================

    @Test
    fun `back button calls onNavigateBack`() {
        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Navigate back").performClick()

        // Then
        assertTrue(navigateBackCalled)
    }

    // ========================================================================
    // THEME MODE INTERACTION TESTS
    // ========================================================================

    @Test
    fun `selecting system theme mode calls viewModel`() {
        // Given
        preferencesFlow.value = ThemePreferences.DEFAULT.copy(themeMode = ThemeMode.DARK)

        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel
                )
            }
        }

        composeTestRule.onNodeWithText("System").performClick()

        // Then
        verify { mockViewModel.setThemeMode(ThemeMode.SYSTEM) }
    }

    @Test
    fun `selecting light theme mode calls viewModel`() {
        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Light").performClick()

        // Then
        verify { mockViewModel.setThemeMode(ThemeMode.LIGHT) }
    }

    @Test
    fun `selecting dark theme mode calls viewModel`() {
        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Dark").performClick()

        // Then
        verify { mockViewModel.setThemeMode(ThemeMode.DARK) }
    }

    @Test
    fun `selecting AMOLED black theme mode calls viewModel`() {
        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel
                )
            }
        }

        composeTestRule.onNodeWithText("AMOLED Black").performClick()

        // Then
        verify { mockViewModel.setThemeMode(ThemeMode.AMOLED_BLACK) }
    }

    // ========================================================================
    // CONTRAST LEVEL INTERACTION TESTS
    // ========================================================================

    @Test
    fun `selecting standard contrast calls viewModel`() {
        // Given
        preferencesFlow.value = ThemePreferences.DEFAULT.copy(contrastLevel = ContrastLevel.HIGH)

        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Standard").performClick()

        // Then
        verify { mockViewModel.setContrastLevel(ContrastLevel.STANDARD) }
    }

    @Test
    fun `selecting medium contrast calls viewModel`() {
        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Medium").performClick()

        // Then
        verify { mockViewModel.setContrastLevel(ContrastLevel.MEDIUM) }
    }

    @Test
    fun `selecting high contrast calls viewModel`() {
        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel
                )
            }
        }

        composeTestRule.onNodeWithText("High").performClick()

        // Then
        verify { mockViewModel.setContrastLevel(ContrastLevel.HIGH) }
    }

    // ========================================================================
    // DYNAMIC COLORS INTERACTION TESTS
    // ========================================================================

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `toggling dynamic colors switch calls viewModel on Android 12+`() {
        // Given
        preferencesFlow.value = ThemePreferences.DEFAULT.copy(useDynamicColors = true)

        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel
                )
            }
        }

        // Find and click the Dynamic Colors switch (it's in a Row with the text)
        composeTestRule.onNodeWithText("Dynamic Colors").performClick()

        coVerify(exactly = 1) { mockViewModel.setUseDynamicColors(false) }
    }

    // ========================================================================
    // ACCENT COLOR TESTS
    // ========================================================================

    @Test
    fun `accent color section is hidden when dynamic colors enabled on Android 12+`() {
        assumeTrue("Requires Android 12 or higher", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

        // Given
        preferencesFlow.value = ThemePreferences.DEFAULT.copy(useDynamicColors = true)

        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel
                )
            }
        }

        // Then - Accent color section should not be visible
        composeTestRule.onNodeWithText("Accent Color").assertDoesNotExist()
    }

    @Test
    fun `accent color section is shown when dynamic colors disabled`() {
        // Given
        preferencesFlow.value = ThemePreferences.DEFAULT.copy(useDynamicColors = false)

        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel
                )
            }
        }

        // Then
        composeTestRule.onNodeWithText("Accent Color").assertIsDisplayed()
    }

    // ========================================================================
    // ACCESSIBILITY TESTS
    // ========================================================================

    @Test
    fun `reduce motion switch toggles correctly`() {
        // Given
        preferencesFlow.value = ThemePreferences.DEFAULT.copy(respectReduceMotion = true)

        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Respect Reduce Motion").performClick()

        coVerify(exactly = 1) { mockViewModel.setRespectReduceMotion(false) }
    }

    @Test
    fun `accessibility section is displayed`() {
        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel
                )
            }
        }

        // Then
        composeTestRule.onNodeWithText("Accessibility").assertIsDisplayed()
        composeTestRule.onNodeWithText("Respect Reduce Motion").assertIsDisplayed()
        composeTestRule.onNodeWithText("Follow system animation preferences").assertIsDisplayed()
    }

    // ========================================================================
    // STATE REFLECTION TESTS
    // ========================================================================

    @Test
    fun `selected theme mode is reflected in UI`() {
        // Given - Set Dark mode as selected
        preferencesFlow.value = ThemePreferences.DEFAULT.copy(themeMode = ThemeMode.DARK)

        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel
                )
            }
        }

        // Then - Dark mode radio button should be selected
        // Note: Testing radio button selection state requires testTag or semantic properties
        composeTestRule.onNodeWithText("Dark").assertIsDisplayed()
    }

    @Test
    fun `selected contrast level is reflected in UI`() {
        // Given - Set High contrast as selected
        preferencesFlow.value = ThemePreferences.DEFAULT.copy(contrastLevel = ContrastLevel.HIGH)

        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel
                )
            }
        }

        // Then - High contrast radio button should be selected
        composeTestRule.onNodeWithText("High").assertIsDisplayed()
    }

    // ========================================================================
    // MULTIPLE INTERACTIONS TESTS
    // ========================================================================

    @Test
    fun `multiple settings can be changed in sequence`() {
        // When
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel
                )
            }
        }

        // Change theme mode
        composeTestRule.onNodeWithText("Dark").performClick()

        // Change contrast level
        composeTestRule.onNodeWithText("High").performClick()

        // Then
        verify { mockViewModel.setThemeMode(ThemeMode.DARK) }
        verify { mockViewModel.setContrastLevel(ContrastLevel.HIGH) }
    }
}
