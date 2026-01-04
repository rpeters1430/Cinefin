package com.rpeters.jellyfin.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
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
import androidx.navigation.compose.rememberNavController
import com.rpeters.jellyfin.ui.components.BottomNavBar
import com.rpeters.jellyfin.ui.navigation.JellyfinNavGraph
import com.rpeters.jellyfin.ui.navigation.Screen
import com.rpeters.jellyfin.ui.shortcuts.DynamicShortcutManager
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import com.rpeters.jellyfin.ui.viewmodel.ServerConnectionViewModel
import com.rpeters.jellyfin.ui.viewmodel.ThemePreferencesViewModel

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

        Scaffold(
            bottomBar = {
                BottomNavBar(navController = navController)
            },
            modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
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
