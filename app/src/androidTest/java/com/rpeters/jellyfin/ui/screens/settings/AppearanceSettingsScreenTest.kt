package com.rpeters.jellyfin.ui.screens.settings

import android.os.Build
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.jellyfin.data.preferences.AppFont
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
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Comprehensive UI tests for AppearanceSettingsScreen.
 */
@RunWith(AndroidJUnit4::class)
class AppearanceSettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule(
        effectContext = StandardTestDispatcher(),
    )

    private lateinit var mockViewModel: ThemePreferencesViewModel
    private lateinit var preferencesFlow: MutableStateFlow<ThemePreferences>
    private var navigateBackCalled = false

    @Before
    fun setup() {
        preferencesFlow = MutableStateFlow(ThemePreferences.DEFAULT)

        mockViewModel = mockk(relaxed = true)
        every { mockViewModel.themePreferences } returns preferencesFlow

        coEvery { mockViewModel.setThemeMode(any()) } returns Unit
        coEvery { mockViewModel.setUseDynamicColors(any()) } returns Unit
        coEvery { mockViewModel.setAccentColor(any()) } returns Unit
        coEvery { mockViewModel.setContrastLevel(any()) } returns Unit
        every { mockViewModel.setAppFont(any()) } returns Unit
        coEvery { mockViewModel.setUseThemedIcon(any()) } returns Unit
        coEvery { mockViewModel.setEnableEdgeToEdge(any()) } returns Unit
        coEvery { mockViewModel.setRespectReduceMotion(any()) } returns Unit

        navigateBackCalled = false
    }

    @Test
    fun screenDisplaysAllMajorSections() {
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeTestRule.onNodeWithText("Theme Mode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Color System").assertIsDisplayed()
        composeTestRule.onNodeWithText("Typography").assertIsDisplayed()
        composeTestRule.onNodeWithText("Contrast").assertIsDisplayed()
        composeTestRule.onNodeWithText("Accessibility").assertIsDisplayed()
    }

    @Test
    fun screenDisplaysAllThemeModeOptions() {
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("System").assertIsDisplayed()
        composeTestRule.onNodeWithText("Light").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dark").assertIsDisplayed()
        composeTestRule.onNodeWithText("AMOLED Black").assertIsDisplayed()
    }

    @Test
    fun screenDisplaysAllContrastLevelOptions() {
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Standard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Medium").assertIsDisplayed()
        composeTestRule.onNodeWithText("High").assertIsDisplayed()
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun screenDisplaysDynamicColorsOptionOnAndroid12Plus() {
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Dynamic Colors").assertIsDisplayed()
        composeTestRule.onNodeWithText("Colors extracted from your wallpaper").assertIsDisplayed()
    }

    @Test
    fun backButtonCallsOnNavigateBack() {
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Navigate back").performClick()

        assertTrue(navigateBackCalled)
    }

    @Test
    fun selectingSystemThemeModeCallsViewModel() {
        preferencesFlow.value = ThemePreferences.DEFAULT.copy(themeMode = ThemeMode.DARK)

        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("System").performClick()

        verify { mockViewModel.setThemeMode(ThemeMode.SYSTEM) }
    }

    @Test
    fun selectingLightThemeModeCallsViewModel() {
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Light").performClick()

        verify { mockViewModel.setThemeMode(ThemeMode.LIGHT) }
    }

    @Test
    fun selectingDarkThemeModeCallsViewModel() {
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Dark").performClick()

        verify { mockViewModel.setThemeMode(ThemeMode.DARK) }
    }

    @Test
    fun selectingAmoledBlackThemeModeCallsViewModel() {
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("AMOLED Black").performClick()

        verify { mockViewModel.setThemeMode(ThemeMode.AMOLED_BLACK) }
    }

    @Test
    fun selectingStandardContrastCallsViewModel() {
        preferencesFlow.value = ThemePreferences.DEFAULT.copy(contrastLevel = ContrastLevel.HIGH)

        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Standard").performClick()

        verify { mockViewModel.setContrastLevel(ContrastLevel.STANDARD) }
    }

    @Test
    fun screenDisplaysAllAppFontOptions() {
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Cinefin app font preview").assertIsDisplayed()
        composeTestRule.onNodeWithText("Default").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sans-Serif").assertIsDisplayed()
        composeTestRule.onNodeWithText("Serif").assertIsDisplayed()
        composeTestRule.onNodeWithText("Monospace").assertIsDisplayed()
    }

    @Test
    fun selectingSerifAppFontCallsViewModel() {
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Serif").performClick()

        verify { mockViewModel.setAppFont(AppFont.SERIF) }
    }

    @Test
    fun selectingMediumContrastCallsViewModel() {
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Medium").performClick()

        verify { mockViewModel.setContrastLevel(ContrastLevel.MEDIUM) }
    }

    @Test
    fun selectingHighContrastCallsViewModel() {
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("High").performClick()

        verify { mockViewModel.setContrastLevel(ContrastLevel.HIGH) }
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun togglingDynamicColorsSwitchCallsViewModelOnAndroid12Plus() {
        preferencesFlow.value = ThemePreferences.DEFAULT.copy(useDynamicColors = true)

        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Dynamic Colors").performClick()

        coVerify(exactly = 1) { mockViewModel.setUseDynamicColors(false) }
    }

    @Test
    fun accentColorSectionIsHiddenWhenDynamicColorsEnabledOnAndroid12Plus() {
        assumeTrue("Requires Android 12 or higher", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

        preferencesFlow.value = ThemePreferences.DEFAULT.copy(useDynamicColors = true)

        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onAllNodesWithText("Accent Color").assertCountEquals(0)
    }

    @Test
    fun accentColorSectionIsShownWhenDynamicColorsDisabled() {
        preferencesFlow.value = ThemePreferences.DEFAULT.copy(useDynamicColors = false)

        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Accent Color").assertIsDisplayed()
    }

    @Test
    fun reduceMotionSwitchTogglesCorrectly() {
        preferencesFlow.value = ThemePreferences.DEFAULT.copy(respectReduceMotion = true)

        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Respect Reduce Motion").performClick()

        coVerify(exactly = 1) { mockViewModel.setRespectReduceMotion(false) }
    }

    @Test
    fun accessibilitySectionIsDisplayed() {
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Accessibility").assertIsDisplayed()
        composeTestRule.onNodeWithText("Respect Reduce Motion").assertIsDisplayed()
        composeTestRule.onNodeWithText("Disable heavy animations if system set to reduce motion").assertIsDisplayed()
    }

    @Test
    fun selectedThemeModeIsReflectedInUI() {
        preferencesFlow.value = ThemePreferences.DEFAULT.copy(themeMode = ThemeMode.DARK)

        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Dark").assertIsDisplayed()
    }

    @Test
    fun selectedContrastLevelIsReflectedInUI() {
        preferencesFlow.value = ThemePreferences.DEFAULT.copy(contrastLevel = ContrastLevel.HIGH)

        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("High").assertIsDisplayed()
    }

    @Test
    fun multipleSettingsCanBeChangedInSequence() {
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                AppearanceSettingsScreen(
                    onNavigateBack = { navigateBackCalled = true },
                    viewModel = mockViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Dark").performClick()

        composeTestRule.onNodeWithText("High").performClick()

        verify { mockViewModel.setThemeMode(ThemeMode.DARK) }
        verify { mockViewModel.setContrastLevel(ContrastLevel.HIGH) }
    }
}
