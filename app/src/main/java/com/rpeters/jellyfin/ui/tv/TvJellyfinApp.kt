package com.rpeters.jellyfin.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.rememberDrawerState
import com.rpeters.jellyfin.ui.theme.CinefinTvTheme
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import com.rpeters.jellyfin.ui.viewmodel.ThemePreferencesViewModel
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.Text as TvText

private fun normalizeTvRoute(route: String?): String? =
    when (route) {
        "tv_homevideos" -> "tv_stuff"
        else -> route
    }

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
        CinefinTvTheme(accentColor = themePreferences.accentColor) {
            TvSurface(modifier = modifier.fillMaxSize()) {
                val navController = rememberNavController()
                val focusManager = LocalFocusManager.current
                val tvFocusManager = remember { TvFocusManager() }
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                // Determine if we should show the navigation drawer based on current route
                val showDrawer = currentDestination?.route?.let { route ->
                    val normalizedRoute = normalizeTvRoute(route)
                    TvNavigationItem.items.any { it.route == normalizedRoute }
                } ?: false

                CompositionLocalProvider(LocalTvFocusManager provides tvFocusManager) {
                    if (showDrawer) {
                        TvMainScreen(navController = navController)
                    } else {
                        TvNavGraph(
                            navController = navController,
                            modifier = Modifier.tvKeyboardHandler(
                                navController = navController,
                                focusManager = focusManager,
                                onHome = {
                                    navController.navigate("tv_home") {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onSearch = {
                                    navController.navigate("tv_search") {
                                        launchSingleTop = true
                                    }
                                },
                                onQuickAccess = { key ->
                                    val route = when (key) {
                                        1 -> "tv_home"
                                        2 -> "tv_movies"
                                        3 -> "tv_shows"
                                        4 -> "tv_music"
                                        5 -> "tv_settings"
                                        else -> null
                                    } ?: return@tvKeyboardHandler

                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                            ),
                        )
                    }
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
    val layout = CinefinTvTheme.layout
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val selectedItem = TvNavigationItem.items.firstOrNull { item ->
        currentDestination?.hierarchy?.any {
            normalizeTvRoute(it.route) == item.route
        } == true
    }

    NavigationDrawer(
        drawerState = drawerState,
        drawerContent = { _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .fillMaxHeight()
                    .padding(layout.drawerPadding),
                verticalArrangement = Arrangement.spacedBy(layout.drawerItemSpacing),
                horizontalAlignment = Alignment.Start,
            ) {
                // App Logo or Title in Drawer
                TvText(
                    text = "CINEFIN",
                    style = TvMaterialTheme.typography.headlineSmall,
                    color = TvMaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = layout.sectionSpacing),
                )

                TvNavigationItem.items.forEach { item ->
                    val selected = selectedItem == item
                    
                    NavigationDrawerItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                            )
                        },
                    ) {
                        TvText(text = item.title)
                    }
                }
            }
        },
        modifier = modifier,
    ) {
        TvNavGraph(navController = navController)
    }
}
