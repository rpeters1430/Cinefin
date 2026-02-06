package com.rpeters.jellyfin.ui.theme

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.jellyfin.TestJellyfinApplication
import com.rpeters.jellyfin.data.preferences.ThemePreferences
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestJellyfinApplication::class)
class ThemeComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testThemeRendering() {
        composeTestRule.setContent {
            JellyfinAndroidTheme(themePreferences = ThemePreferences.DEFAULT) {
                Text("Hello Compose")
            }
        }

        // If it reaches here without NoSuchMethodError, it's likely fixed or not present in this config
    }
}
