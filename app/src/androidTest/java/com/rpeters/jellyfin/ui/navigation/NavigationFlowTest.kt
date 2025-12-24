package com.rpeters.jellyfin.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NavigationFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun navigateFromSeasonsToEpisodes() {
        lateinit var navController: TestNavHostController

        composeRule.setContent {
            navController = rememberTestNavController()
            TestNavGraph(navController)
        }

        composeRule.onNodeWithText("Load Episodes").performClick()
        assertEquals("episodes", navController.currentDestination?.route)
        composeRule.onNodeWithText("Episodes Screen").assertExists()
    }
}

@Composable
fun rememberTestNavController(): TestNavHostController {
    val context = LocalContext.current
    val navController = TestNavHostController(context)
    navController.navigatorProvider.addNavigator(ComposeNavigator())
    return navController
}

@Composable
fun TestNavGraph(navController: TestNavHostController) {
    NavHost(navController, startDestination = "seasons") {
        composable("seasons") {
            Button(onClick = { navController.navigate("episodes") }) {
                Text("Load Episodes")
            }
        }
        composable("episodes") {
            Text("Episodes Screen")
        }
    }
}
