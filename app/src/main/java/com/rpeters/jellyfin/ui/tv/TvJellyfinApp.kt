package com.rpeters.jellyfin.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.rememberDrawerState
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import com.rpeters.jellyfin.ui.theme.cinefinTvColorScheme
import com.rpeters.jellyfin.ui.viewmodel.ThemePreferencesViewModel
import androidx.tv.material3.MaterialTheme as TvMaterialTheme

@Composable
fun TvJellyfinApp(
    modifier: Modifier = Modifier,
) {
    // Collect theme preferences
    val themeViewModel: ThemePreferencesViewModel = hiltViewModel()
    val themePreferences by themeViewModel.themePreferences.collectAsStateWithLifecycle()

    // Root composable for the TV experience.
    // Hosts the navigation graph for all TV screens.
    // Apply both Material You theme and TV Material Theme
    JellyfinAndroidTheme(themePreferences = themePreferences) {
        TvMaterialTheme(colorScheme = cinefinTvColorScheme()) {
            Surface(modifier = modifier.fillMaxSize()) {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                // Determine if we should show the navigation drawer based on current route
                val showDrawer = currentDestination?.route?.let { route ->
                    TvNavigationItem.items.any { it.route == route }
                } ?: false

                if (showDrawer) {
                    TvMainScreen(navController = navController)
                } else {
                    TvNavGraph(navController = navController)
                }
            }
        }
    }
}

@Composable
fun TvMainScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // App Logo or Title in Drawer
                Text(
                    text = "CINEFIN",
                    style = TvMaterialTheme.typography.headlineSmall,
                    color = TvMaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 24.dp),
                )

                TvNavigationItem.items.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                    
                    NavigationDrawerItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                // Pop up to the start destination of the graph to
                                // avoid building up a large stack of destinations
                                // on the back stack as users select items
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination when
                                // reselecting the same item
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        },
                        leadingContent = {
                            androidx.tv.material3.Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                            )
                        },
                    ) {
                        Text(text = item.title)
                    }
                }
            }
        },
        modifier = modifier,
    ) {
        TvNavGraph(navController = navController)
    }
}

