package com.rpeters.jellyfin.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.screens.settings.SettingsSectionScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsSectionNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun clickingRecommendationOptionNavigatesToDetail() {
        lateinit var navController: TestNavHostController

        composeRule.setContent {
            navController = rememberSettingsTestNavController()
            SettingsSectionTestNavGraph(navController)
        }

        val optionLabel = composeRule.activity.getString(R.string.settings_playback_quality)
        composeRule.onNodeWithText(optionLabel).performClick()

        assertEquals("detail", navController.currentDestination?.route)
        composeRule.onNodeWithText("Detail Screen").assertExists()
    }
}

@Composable
private fun rememberSettingsTestNavController(): TestNavHostController {
    val context = LocalContext.current
    return TestNavHostController(context).apply {
        navigatorProvider.addNavigator(ComposeNavigator())
    }
}

@Composable
private fun SettingsSectionTestNavGraph(navController: TestNavHostController) {
    NavHost(navController, startDestination = "section") {
        composable("section") {
            SettingsSectionScreen(
                titleRes = R.string.settings_playback_title,
                descriptionRes = R.string.settings_playback_description,
                optionRes = listOf(R.string.settings_playback_quality),
                onNavigateBack = {},
                onOptionClick = { navController.navigate("detail") },
            )
        }
        composable("detail") {
            Text("Detail Screen")
        }
    }
}
