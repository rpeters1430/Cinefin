package com.rpeters.jellyfin.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavDestination
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.jellyfin.TestCinefinApplication
import com.rpeters.jellyfin.data.preferences.ThemePreferences
import com.rpeters.jellyfin.ui.navigation.BottomNavItem
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestCinefinApplication::class)
class ExpressiveFloatingNavBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedLabelIsHiddenWhenSixNavItemsAreShown() {
        val items = BottomNavItem.bottomNavItems(seerrEnabled = true)
        val selected = BottomNavItem.Home

        composeRule.setContent {
            JellyfinAndroidTheme(themePreferences = ThemePreferences.DEFAULT) {
                ExpressiveFloatingNavBar(
                    items = items,
                    currentDestination = destinationFor(selected),
                    onNavigate = {},
                    isVisible = true,
                )
            }
        }

        composeRule.onAllNodesWithText(selected.title).assertCountEquals(0)
    }

    @Test
    fun selectedLabelIsVisibleWhenFiveNavItemsAreShown() {
        val items = BottomNavItem.bottomNavItems(seerrEnabled = false)
        val selected = BottomNavItem.Home

        composeRule.setContent {
            JellyfinAndroidTheme(themePreferences = ThemePreferences.DEFAULT) {
                ExpressiveFloatingNavBar(
                    items = items,
                    currentDestination = destinationFor(selected),
                    onNavigate = {},
                    isVisible = true,
                )
            }
        }

        composeRule.onNodeWithText(selected.title).assertIsDisplayed()
    }

    private fun destinationFor(item: BottomNavItem): NavDestination =
        NavDestination("test").apply {
            route = item.route
        }
}
