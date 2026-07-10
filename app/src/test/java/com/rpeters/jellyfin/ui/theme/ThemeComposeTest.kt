package com.rpeters.jellyfin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.jellyfin.TestCinefinApplication
import com.rpeters.jellyfin.data.preferences.ThemePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestCinefinApplication::class)
class ThemeComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testThemeRendering() {
        composeTestRule.setContent {
            JellyfinAndroidTheme(themePreferences = ThemePreferences.DEFAULT) {
                ExpressiveThemeProbe()
            }
        }
    }

    @Test
    fun testThemeMotionSchemeDefault() {
        var defaultMotionScheme: androidx.compose.material3.MotionScheme? = null
        composeTestRule.setContent {
            JellyfinAndroidTheme(themePreferences = ThemePreferences(respectReduceMotion = false)) {
                defaultMotionScheme = MaterialTheme.motionScheme
            }
        }
        assertNotNull(defaultMotionScheme)
        assertEquals(MotionTokens.expressiveMotionScheme, defaultMotionScheme)
    }

    @Test
    fun testThemeMotionSchemeReduced() {
        var reducedMotionScheme: androidx.compose.material3.MotionScheme? = null
        composeTestRule.setContent {
            JellyfinAndroidTheme(themePreferences = ThemePreferences(respectReduceMotion = true)) {
                reducedMotionScheme = MaterialTheme.motionScheme
            }
        }
        assertNotNull(reducedMotionScheme)
        assertEquals(MotionTokens.reducedMotionScheme, reducedMotionScheme)
    }

    @Composable
    private fun ExpressiveThemeProbe() {
        val sectionColor = JellyfinExpressiveTheme.colors.sectionContainer
        Text(text = "Hello Compose", color = sectionColor)
    }
}
