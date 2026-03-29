package com.rpeters.jellyfin.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvJellyfinApp(
    modifier: Modifier = Modifier,
) {
    val themeViewModel: ThemePreferencesViewModel = hiltViewModel()
    val themePreferences by themeViewModel.themePreferences.collectAsStateWithLifecycle()

    JellyfinAndroidTheme(themePreferences = themePreferences) {
        CinefinTvTheme(accentColor = themePreferences.accentColor) {
            TvSurface(modifier = modifier.fillMaxSize()) {
                val navController = rememberNavController()
                val tvFocusManager = remember { TvFocusManager() }
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                val showDrawer = currentDestination?.route?.let { route ->
                    val normalizedRoute = normalizeTvRoute(route)
                    TvNavigationItem.items.any { it.route == normalizedRoute }
                } ?: false

                CompositionLocalProvider(LocalTvFocusManager provides tvFocusManager) {
                    TvMainScreen(
                        navController = navController,
                        showDrawer = showDrawer,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMainScreen(
    navController: NavHostController,
    showDrawer: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val layout = CinefinTvTheme.layout
    val focusManager = LocalFocusManager.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val selectedItem = TvNavigationItem.items.firstOrNull { item ->
        currentDestination?.hierarchy?.any {
            normalizeTvRoute(it.route) == item.route
        } == true
    }

    // Explicit Row layout guarantees the content area fills exactly the remaining width
    // (weight(1f)) and never overflows off-screen — regardless of sidebar width.
    Row(modifier = modifier.fillMaxSize()) {
        // Persistent navigation sidebar — only rendered on primary screens
        if (showDrawer) {
            TvNavigationSidebar(
                navController = navController,
                selectedItem = selectedItem,
                modifier = Modifier
                    .width(layout.drawerWidth)
                    .fillMaxHeight(),
            )
        }

        // Main content — weight(1f) fills exactly the remaining screen width
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            TvNavGraph(
                navController = navController,
                modifier = Modifier
                    .fillMaxSize()
                    .tvKeyboardHandler(
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvNavigationSidebar(
    navController: NavHostController,
    selectedItem: TvNavigationItem?,
    modifier: Modifier = Modifier,
) {
    val layout = CinefinTvTheme.layout

    Column(
        modifier = modifier.padding(layout.drawerPadding),
        verticalArrangement = Arrangement.spacedBy(layout.drawerItemSpacing),
        horizontalAlignment = Alignment.Start,
    ) {
        TvText(
            text = "CINEFIN",
            style = TvMaterialTheme.typography.headlineSmall,
            color = TvMaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = layout.sectionSpacing),
        )

        TvNavigationItem.items.forEach { item ->
            TvSidebarNavItem(
                item = item,
                selected = selectedItem == item,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}

/**
 * A TV-focused sidebar navigation item that shows an icon and label.
 * Uses [TvSurface] with [onClick] for proper D-pad focus and click handling.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSidebarNavItem(
    item: TvNavigationItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primaryColor = TvMaterialTheme.colorScheme.primary
    val onSurface = TvMaterialTheme.colorScheme.onSurface

    TvSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) primaryColor.copy(alpha = 0.12f) else Color.Transparent,
            focusedContainerColor = onSurface.copy(alpha = 0.12f),
            pressedContainerColor = primaryColor.copy(alpha = 0.2f),
        ),
        shape = ClickableSurfaceDefaults.shape(shape = TvMaterialTheme.shapes.small),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = if (selected) primaryColor else onSurface.copy(alpha = 0.8f),
            )
            TvText(
                text = item.title,
                style = TvMaterialTheme.typography.titleMedium,
                color = if (selected) primaryColor else onSurface.copy(alpha = 0.8f),
            )
        }
    }
}
