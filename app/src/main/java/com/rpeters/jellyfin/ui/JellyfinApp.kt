package com.rpeters.jellyfin.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rpeters.jellyfin.network.ConnectivityChecker
import com.rpeters.jellyfin.ui.components.ExpressiveFloatingNavBar
import com.rpeters.jellyfin.ui.components.MiniPlayer
import com.rpeters.jellyfin.ui.components.OfflineIndicatorBanner
import com.rpeters.jellyfin.ui.navigation.BottomNavItem
import com.rpeters.jellyfin.ui.navigation.JellyfinNavGraph
import com.rpeters.jellyfin.ui.navigation.Screen
import com.rpeters.jellyfin.ui.navigation.LocalNavBarVisible
import com.rpeters.jellyfin.ui.navigation.navigateToMainDestination
import com.rpeters.jellyfin.ui.navigation.shouldShowNavigation
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

    // Main app ViewModel for global state and sync tasks
    val mainAppViewModel: com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel = hiltViewModel()
    val appState by mainAppViewModel.appState.collectAsStateWithLifecycle()

    // Mutable state for nav bar scroll-hide. Immersive screens write to this via
    // LocalNavBarVisible; JellyfinApp reads it for the ExpressiveFloatingNavBar visibility.
    val navBarVisible = remember { mutableStateOf(true) }

    JellyfinAndroidTheme(themePreferences = themePreferences) {
        CompositionLocalProvider(LocalNavBarVisible provides navBarVisible) {
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
        LaunchedEffect(connectionState.errorMessage, connectionState.isConnected) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            val hasError = connectionState.errorMessage != null
            val isNotConnected = !connectionState.isConnected
            val isNotOnLoginScreens = currentRoute != Screen.ServerConnection.route &&
                currentRoute != Screen.QuickConnect.route

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

        // Reset nav bar visibility whenever the destination changes so hidden state from an
        // immersive screen never bleeds into the next screen.
        LaunchedEffect(currentDestination) {
            navBarVisible.value = true
        }

        val isCompactWidth = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
        var isNavExpanded by rememberSaveable(windowSizeClass.widthSizeClass) {
            mutableStateOf(windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded)
        }

        // Determine navigation type based on window width
        val navigationType = when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> NavigationSuiteType.None // Use custom floating toolbar for phones
            else -> if (isNavExpanded) {
                NavigationSuiteType.NavigationDrawer
            } else {
                NavigationSuiteType.NavigationRail
            }
        }
        val showNavLabels = navigationType != NavigationSuiteType.NavigationRail
        val showNavToggle = !isCompactWidth

        // Only show navigation on main screens
        val shouldShowNavigation = shouldShowNavigation(currentDestination?.route)

        // Pre-compute toggle item colors outside the non-composable navigationSuiteItems lambda.
        val toggleItemColors = NavigationSuiteDefaults.itemColors(
            navigationRailItemColors = androidx.compose.material3.NavigationRailItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            navigationDrawerItemColors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        )

        if (shouldShowNavigation) {
            // Adaptive navigation scaffold for main screens
            NavigationSuiteScaffold(
                navigationSuiteItems = {
                    // Destination items first so the toggle never interrupts keyboard/D-pad flow
                    BottomNavItem.bottomNavItems.forEach { item ->
                        item(
                            selected = currentDestination?.hierarchy?.any {
                                it.route == item.route
                            } == true,
                            onClick = {
                                navController.navigateToMainDestination(item.navigateTo)
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
                    // Toggle placed last and styled with secondary colors so it reads as a
                    // utility action rather than a navigation destination.
                    if (showNavToggle) {
                        item(
                            selected = false,
                            onClick = { isNavExpanded = !isNavExpanded },
                            icon = {
                                Icon(
                                    imageVector = if (isNavExpanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Filled.Menu,
                                    contentDescription = if (isNavExpanded) "Collapse navigation" else "Expand navigation",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            label = if (showNavLabels) {
                                { Text(if (isNavExpanded) "Collapse" else "Expand") }
                            } else {
                                null
                            },
                            colors = toggleItemColors,
                        )
                    }
                },
                layoutType = navigationType,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isCompactWidth) {
                                    // Add padding for floating toolbar, mini player, and system navigation bars
                                    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                                    // Room for MiniPlayer (72dp) + ExpressiveNavBar (60dp) + Spacing (8dp) + Floating Gap (8dp)
                                    Modifier.padding(bottom = 148.dp + navBarPadding)
                                } else {
                                    // Room for MiniPlayer on tablets
                                    Modifier.padding(bottom = 80.dp)
                                }
                            )
                    ) {
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

                    // Floating UI stack at the bottom
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Global Mini Player - only show if something is playing
                        MiniPlayer(
                            onExpandClick = { navController.navigate(Screen.NowPlaying.route) },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        // Expressive Floating Navigation Bar for phones
                        if (isCompactWidth) {
                            ExpressiveFloatingNavBar(
                                items = BottomNavItem.bottomNavItems,
                                currentDestination = currentDestination,
                                onNavigate = { item ->
                                    navController.navigateToMainDestination(item.navigateTo)
                                },
                                isVisible = navBarVisible.value,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        } else {
                            // Extra padding for tablets to keep mini player above system bar
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        } else {
            // No navigation for auth and detail screens
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    // Still show mini player on detail screens if something is playing
                    // Positioned at the very bottom
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                    ) {
                        MiniPlayer(
                            onExpandClick = { navController.navigate(Screen.NowPlaying.route) },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding())
                ) {
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
        }
        } // CompositionLocalProvider(LocalNavBarVisible)
    }
}

