package com.rpeters.jellyfin.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.rpeters.jellyfin.ui.navigation.JellyfinNavGraph
import com.rpeters.jellyfin.ui.navigation.Screen
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import com.rpeters.jellyfin.ui.viewmodel.ServerConnectionViewModel

/**
 * Root composable for the phone experience.
 *
 * @param onLogout callback when the user logs out.
 * @param useDynamicColor whether to apply dynamic colors on Android 12+ devices. Enabled by default.
 */
@androidx.media3.common.util.UnstableApi
@Composable
fun JellyfinApp(
    onLogout: () -> Unit = {},
    useDynamicColor: Boolean = true,
) {
    JellyfinAndroidTheme(dynamicColor = useDynamicColor) {
        val navController = rememberNavController()
        val connectionViewModel: ServerConnectionViewModel = hiltViewModel()
        // Use a simple approach without collectAsState for now
        val startDestination = Screen.ServerConnection.route

        Scaffold(
            bottomBar = {
                // TODO: Re-enable bottom nav when connectionState is working
                // BottomNavBar(navController = navController)
            },
            modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
            JellyfinNavGraph(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(innerPadding),
                onLogout = onLogout,
            )
        }
    }
}
