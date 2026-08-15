package com.rpeters.jellyfin.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rpeters.jellyfin.network.ConnectivityChecker
import com.rpeters.jellyfin.ui.components.MiniPlayer
import com.rpeters.jellyfin.ui.components.OfflineIndicatorBanner
import com.rpeters.jellyfin.ui.navigation.BottomNavItem
import com.rpeters.jellyfin.ui.navigation.JellyfinNavGraph
import com.rpeters.jellyfin.ui.navigation.Screen
import com.rpeters.jellyfin.ui.navigation.navigateToMainDestination
import com.rpeters.jellyfin.ui.navigation.shouldShowNavigation
import com.rpeters.jellyfin.ui.shortcuts.DynamicShortcutManager
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import com.rpeters.jellyfin.ui.viewmodel.AudioPlaybackViewModel
import com.rpeters.jellyfin.ui.viewmodel.SeerrSettingsViewModel
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

    // Audio playback ViewModel for MiniPlayer visibility tracking
    val audioPlaybackViewModel: AudioPlaybackViewModel = hiltViewModel()
    val audioPlaybackState by audioPlaybackViewModel.playbackState.collectAsStateWithLifecycle()

    // Requests tab visible when any request service is enabled
    val seerrViewModel: SeerrSettingsViewModel = hiltViewModel()
    val mediaRequestViewModel: com.rpeters.jellyfin.ui.viewmodel.MediaRequestSettingsViewModel = hiltViewModel()
    val seerrPreferences by seerrViewModel.seerrPreferences.collectAsStateWithLifecycle()
    val isPluginConfigured by seerrViewModel.isPluginConfigured.collectAsStateWithLifecycle()
    val sonarrPrefs by mediaRequestViewModel.sonarrPreferences.collectAsStateWithLifecycle()
    val radarrPrefs by mediaRequestViewModel.radarrPreferences.collectAsStateWithLifecycle()
    val requestsEnabled = seerrPreferences.isEnabled || isPluginConfigured ||
        (sonarrPrefs.isValid && sonarrPrefs.isEnabled) || (radarrPrefs.isValid && radarrPrefs.isEnabled)
    val activeNavItems = BottomNavItem.bottomNavItems(requestsEnabled)
    val isMiniPlayerVisible = audioPlaybackState.currentMediaItem != null

    // Main app ViewModel for global state and sync tasks
    val mainAppViewModel: com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel = hiltViewModel()
    val appState by mainAppViewModel.appState.collectAsStateWithLifecycle()
    val currentServer by mainAppViewModel.currentServer.collectAsStateWithLifecycle(initialValue = null)
    val isConnected by mainAppViewModel.isConnected.collectAsStateWithLifecycle(initialValue = false)

    JellyfinAndroidTheme(themePreferences = themePreferences) {
        val windowSizeClass = calculateWindowSizeClass(activity = checkNotNull(LocalActivity.current))
        val adaptiveLayoutConfig = com.rpeters.jellyfin.ui.adaptive.rememberAdaptiveLayoutConfig(windowSizeClass)

        CompositionLocalProvider(
            com.rpeters.jellyfin.ui.adaptive.LocalWindowSizeClass provides windowSizeClass,
            com.rpeters.jellyfin.ui.adaptive.LocalAdaptiveLayoutConfig provides adaptiveLayoutConfig
        ) {
            val ageSignalsStatus by mainAppViewModel.ageSignalsStatus.collectAsStateWithLifecycle()
            if (ageSignalsStatus != null && com.rpeters.jellyfin.utils.PlayAgeSignalsCompliance.isBlocked(ageSignalsStatus)) {
                com.rpeters.jellyfin.ui.components.AgeSignalsBlockScreen(status = ageSignalsStatus!!)
            } else {
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

        LaunchedEffect(
            currentServer?.normalizedUrl ?: currentServer?.url,
            currentServer?.userId,
            isConnected,
            appState.libraries.size,
            appState.isLoading,
            appState.errorMessage,
        ) {
            if (
                isConnected &&
                currentServer != null &&
                appState.libraries.isEmpty() &&
                !appState.isLoading &&
                appState.errorMessage == null
            ) {
                SecureLogger.v("JellyfinApp", "Session ready with no libraries loaded yet; triggering initial data load")
                mainAppViewModel.loadInitialData()
            }
        }

        // Only enter the main app when a session is actually connected. Remembered credentials
        // should go through ServerConnectionViewModel auto-login first; otherwise Home can render
        // with a stale/restored shell and no usable authenticated session.
        val startDestination = if (connectionState.isConnected) {
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

        // Navigation guard: redirect to login when session is disconnected/logged out or if auto-login fails
        LaunchedEffect(connectionState.isConnected, connectionState.errorMessage) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            val isNotConnected = !connectionState.isConnected
            val isNotOnLoginScreens = currentRoute != null &&
                currentRoute != Screen.ServerConnection.route &&
                currentRoute != Screen.QuickConnect.route &&
                currentRoute != Screen.OfflineLibrary.route

            if (isNotConnected && isNotOnLoginScreens) {
                navController.navigate(Screen.ServerConnection.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }

        // Calculate window size class for adaptive navigation
        val windowSizeClass = calculateWindowSizeClass(activity = checkNotNull(LocalActivity.current))
        val navBackStackEntry = navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry.value?.destination

        val isCompactWidth = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
        var isNavExpanded by rememberSaveable(windowSizeClass.widthSizeClass) {
            mutableStateOf(windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded)
        }

        // Determine navigation type based on window width. Compact width uses the standard,
        // docked Material 3 NavigationBar (via NavigationSuiteScaffold) instead of a custom
        // floating bar - no blur/translucency, no manual inset math, edge-to-edge handled by
        // the component itself.
        val navigationType = when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> NavigationSuiteType.NavigationBar
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

        // The docked NavigationBar/rail/drawer already reserves its own space; content only
        // needs to reserve room for the MiniPlayer overlay when something is playing. Reserve
        // its actual measured height rather than a fixed guess - the player's height varies
        // (it grows when a progress bar/timestamps are shown), so a hardcoded constant either
        // under-reserves space (bottom content peeks out from behind it) or over-reserves it.
        val density = LocalDensity.current
        var miniPlayerHeightPx by remember { mutableIntStateOf(0) }
        val miniPlayerReservedSpace = if (isMiniPlayerVisible) {
            with(density) { miniPlayerHeightPx.toDp() }
        } else {
            0.dp
        }

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
                    activeNavItems.forEach { item ->
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
                // NavigationSuiteScaffold already lays this content out above/beside the docked
                // NavigationBar, NavigationRail, or NavigationDrawer - no manual inset math needed
                // for the nav chrome itself. The only thing this content area must still reserve
                // space for is the floating MiniPlayer overlay.
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = miniPlayerReservedSpace)
                    ) {
                        // Show offline indicator when not connected
                        OfflineIndicatorBanner(isVisible = !isOnline)

                        // Main navigation content
                        JellyfinNavGraph(
                            navController = navController,
                            startDestination = startDestination,
                            modifier = Modifier.fillMaxSize(),
                            onLogout = {
                                connectionViewModel.logout()
                                mainAppViewModel.logout()
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

                    // Global Mini Player - only shows itself if something is playing.
                    // MiniPlayer forwards its `modifier` to internal content inside its own
                    // AnimatedVisibility rather than to that AnimatedVisibility call itself, so
                    // BoxScope.align() must be applied on this wrapper - a genuine direct child
                    // of the outer Box - rather than on MiniPlayer's modifier parameter directly.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .then(
                                if (isCompactWidth) {
                                    // The docked NavigationBar below already pads its own content
                                    // away from the system nav bar, so this content area (above
                                    // it) never overlaps that inset - no extra padding needed.
                                    Modifier
                                } else {
                                    // NavigationRail/NavigationDrawer sit at the side, so this
                                    // content area fills the full screen height; without this the
                                    // overlay sits under the system navigation bar.
                                    Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .onGloballyPositioned { coordinates ->
                                miniPlayerHeightPx = coordinates.size.height
                            }
                    ) {
                        MiniPlayer(
                            onExpandClick = { navController.navigate(Screen.NowPlaying.route) },
                        )
                    }
                }
            }
        } else {
            // No navigation for auth and detail screens.
            // Detail screens (e.g. NowPlayingScreen) each own a Scaffold + top app bar that
            // already consumes the status bar inset, so this outer Scaffold must not reserve
            // it again - otherwise screens end up with two stacked status-bar-height gaps.
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                            connectionViewModel.logout()
                            mainAppViewModel.logout()
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
        } // CompositionLocalProvider(LocalWindowSizeClass, LocalAdaptiveLayoutConfig)
    }
}
