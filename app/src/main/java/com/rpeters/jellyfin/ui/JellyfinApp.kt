package com.rpeters.jellyfin.ui

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rpeters.jellyfin.network.ConnectivityChecker
import com.rpeters.jellyfin.ui.components.OfflineIndicatorBanner
import com.rpeters.jellyfin.ui.navigation.BottomNavItem
import com.rpeters.jellyfin.ui.navigation.JellyfinNavGraph
import com.rpeters.jellyfin.ui.navigation.Screen
import com.rpeters.jellyfin.ui.shortcuts.DynamicShortcutManager
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import com.rpeters.jellyfin.ui.viewmodel.ServerConnectionViewModel
import com.rpeters.jellyfin.ui.viewmodel.ThemePreferencesViewModel
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Entry point to access ConnectivityChecker from Compose without ViewModel.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ConnectivityCheckerEntryPoint {
    fun connectivityChecker(): ConnectivityChecker
}

/**
 * Root composable for the phone experience.
 *
 * @param onLogout callback when the user logs out.
 * @param useDynamicColor whether to apply dynamic colors on Android 12+ devices. Enabled by default.
 * @param initialDestination optional destination to navigate to from app shortcuts.
 */
@androidx.media3.common.util.UnstableApi
@Composable
fun JellyfinApp(
    onLogout: () -> Unit = {},
    useDynamicColor: Boolean = true,
    initialDestination: String? = null,
    onShortcutConsumed: () -> Unit = {},
) {
    // Collect theme preferences
    val themeViewModel: ThemePreferencesViewModel = hiltViewModel()
    val themePreferences by themeViewModel.themePreferences.collectAsStateWithLifecycle()

    JellyfinAndroidTheme(themePreferences = themePreferences) {
        val navController = rememberNavController()
        val connectionViewModel: ServerConnectionViewModel = hiltViewModel()
        val connectionState by connectionViewModel.connectionState.collectAsStateWithLifecycle()
        val consumeShortcut by rememberUpdatedState(onShortcutConsumed)
        val context = LocalContext.current
        val applicationContext = remember(context) { context.applicationContext }

        // Get ConnectivityChecker from Hilt to monitor network state
        val connectivityChecker = remember(applicationContext) {
            EntryPointAccessors.fromApplication(
                applicationContext,
                ConnectivityCheckerEntryPoint::class.java,
            ).connectivityChecker()
        }

        // Monitor network connectivity at app level
        val isOnline by connectivityChecker.observeNetworkConnectivity()
            .collectAsStateWithLifecycle(initialValue = connectivityChecker.isOnline())

        // Log network state changes
        LaunchedEffect(isOnline) {
            SecureLogger.i("JellyfinApp", "Network state changed: ${if (isOnline) "ONLINE" else "OFFLINE"}")
        }

        // Determine start destination based on authentication state
        // If user has saved credentials and remember login is enabled, start at Home
        // and let auto-login happen in the background
        // Otherwise, start at ServerConnection (login screen)
        val startDestination = if (
            connectionState.isConnected ||
            (
                connectionState.hasSavedPassword && connectionState.rememberLogin &&
                    connectionState.savedServerUrl.isNotBlank() && connectionState.savedUsername.isNotBlank()
                )
        ) {
            Screen.Home.route
        } else {
            Screen.ServerConnection.route
        }

        var pendingShortcutDestination by rememberSaveable { mutableStateOf<String?>(null) }

        LaunchedEffect(initialDestination) {
            if (!initialDestination.isNullOrBlank()) {
                pendingShortcutDestination = initialDestination
            }
        }

        // Handle shortcut navigation when connected
        LaunchedEffect(connectionState.isConnected, pendingShortcutDestination) {
            val destination = pendingShortcutDestination
            if (destination != null && connectionState.isConnected) {
                navController.navigate(destination) {
                    popUpTo(Screen.Home.route) {
                        saveState = false
                    }
                    launchSingleTop = true
                    restoreState = true
                }
                pendingShortcutDestination = null
                consumeShortcut()
            }
        }

        // Navigation guard: redirect to login if auto-login fails
        // This handles the case where auto-login fails with an error
        LaunchedEffect(connectionState.errorMessage, connectionState.isConnected) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            val hasError = connectionState.errorMessage != null
            val isNotConnected = !connectionState.isConnected
            val isNotOnLoginScreens = currentRoute != Screen.ServerConnection.route &&
                currentRoute != Screen.QuickConnect.route

            // If there's an error during auto-login and we're not on login screen, redirect
            if (hasError && isNotConnected && isNotOnLoginScreens) {
                navController.navigate(Screen.ServerConnection.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }

        // Calculate window size class for adaptive navigation
        val windowSizeClass = calculateWindowSizeClass(activity = context as Activity)
        val navBackStackEntry = navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry.value?.destination

        val isCompactWidth = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
        var isNavExpanded by rememberSaveable(windowSizeClass.widthSizeClass) {
            mutableStateOf(windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded)
        }

        // Determine navigation type based on window width and user expansion toggle
        val navigationType = when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> NavigationSuiteType.NavigationBar // Bottom bar for phones
            else -> if (isNavExpanded) {
                NavigationSuiteType.NavigationDrawer // Expanded drawer for tablets
            } else {
                NavigationSuiteType.NavigationRail // Collapsed rail for tablets
            }
        }
        val showNavLabels = navigationType != NavigationSuiteType.NavigationRail
        val showNavToggle = !isCompactWidth

        // Only show navigation on main screens
        val shouldShowNavigation = shouldShowNavigation(currentDestination)

        if (shouldShowNavigation) {
            // Adaptive navigation scaffold for main screens
            NavigationSuiteScaffold(
                navigationSuiteItems = {
                    if (showNavToggle) {
                        item(
                            selected = false,
                            onClick = { isNavExpanded = !isNavExpanded },
                            icon = {
                                Icon(
                                    imageVector = if (isNavExpanded) Icons.Filled.MenuOpen else Icons.Filled.Menu,
                                    contentDescription = if (isNavExpanded) "Collapse navigation" else "Expand navigation",
                                )
                            },
                            label = if (showNavLabels) {
                                { Text(if (isNavExpanded) "Collapse" else "Expand") }
                            } else {
                                null
                            },
                        )
                    }
                    BottomNavItem.bottomNavItems.forEach { item ->
                        item(
                            selected = currentDestination?.hierarchy?.any {
                                it.route == item.route
                            } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon.icon,
                                    contentDescription = item.title,
                                )
                            },
                            label = if (showNavLabels) {
                                { Text(item.title) }
                            } else {
                                null
                            },
                        )
                    }
                },
                layoutType = navigationType,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Show offline indicator when not connected
                    OfflineIndicatorBanner(isVisible = !isOnline)

                    // Main navigation content
                    JellyfinNavGraph(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.fillMaxSize(),
                        onLogout = {
                            DynamicShortcutManager.updateContinueWatchingShortcuts(
                                applicationContext,
                                emptyList(),
                            )
                            if (pendingShortcutDestination != null) {
                                pendingShortcutDestination = null
                                consumeShortcut()
                            }
                            onLogout()
                        },
                    )
                }
            }
        } else {
            // No navigation for auth and detail screens
            Scaffold(
                modifier = Modifier.fillMaxSize(),
            ) { innerPadding ->
                Column(modifier = Modifier.fillMaxSize()) {
                    // Show offline indicator when not connected
                    OfflineIndicatorBanner(isVisible = !isOnline)

                    // Main navigation content
                    JellyfinNavGraph(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(innerPadding),
                        onLogout = {
                            DynamicShortcutManager.updateContinueWatchingShortcuts(
                                applicationContext,
                                emptyList(),
                            )
                            if (pendingShortcutDestination != null) {
                                pendingShortcutDestination = null
                                consumeShortcut()
                            }
                            onLogout()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Determines whether to show navigation based on the current destination.
 * Only shows navigation on main app screens, not on auth or detail screens.
 */
private fun shouldShowNavigation(destination: NavDestination?): Boolean {
    val route = destination?.route ?: return false
    return when (route) {
        Screen.Home.route,
        Screen.Library.route,
        Screen.Search.route,
        Screen.Favorites.route,
        Screen.Profile.route,
        -> true
        else -> false
    }
}
