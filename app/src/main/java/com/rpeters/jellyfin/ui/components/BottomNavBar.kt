package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.rpeters.jellyfin.ui.navigation.BottomNavItem

@Composable
fun BottomNavBar(
    navController: NavController,
) {
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry.value?.destination

    // Only show bottom nav for main screens
    if (shouldShowBottomNav(currentDestination)) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .shadow(
                    elevation = 3.dp,
                    shape = RoundedCornerShape(28.dp),
                )
                .clip(RoundedCornerShape(28.dp)),
        ) {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                BottomNavItem.bottomNavItems.forEach { item ->
                    AddItem(
                        screen = item,
                        currentDestination = currentDestination,
                        navController = navController,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.AddItem(
    screen: BottomNavItem,
    currentDestination: NavDestination?,
    navController: NavController,
) {
    NavigationBarItem(
        icon = {
            Icon(
                imageVector = screen.icon.icon,
                contentDescription = screen.title,
            )
        },
        label = { Text(screen.title) },
        selected = currentDestination?.hierarchy?.any {
            it.route == screen.route
        } == true,
        onClick = {
            navController.navigate(screen.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        },
    )
}

private fun shouldShowBottomNav(destination: NavDestination?): Boolean {
    val route = destination?.route ?: return false
    return when (route) {
        "home",
        "library",
        "search",
        "favorites",
        "profile",
        -> true
        else -> false
    }
}
