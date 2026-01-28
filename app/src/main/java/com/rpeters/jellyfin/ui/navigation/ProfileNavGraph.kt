@file:Suppress("DEPRECATION")

package com.rpeters.jellyfin.ui.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.rpeters.jellyfin.ui.screens.FavoritesScreen
import com.rpeters.jellyfin.ui.screens.ProfileScreen
import com.rpeters.jellyfin.ui.screens.SearchScreen
import com.rpeters.jellyfin.ui.screens.SettingsScreen
import com.rpeters.jellyfin.ui.screens.settings.PinningSettingsScreen
import com.rpeters.jellyfin.ui.screens.settings.SubtitleSettingsScreen
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel

/**
 * Profile, search, favorites, and settings routes.
 */
fun androidx.navigation.NavGraphBuilder.profileNavGraph(
    navController: NavHostController,
    onLogout: () -> Unit,
) {
    composable(Screen.Search.route) {
        val viewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<MainAppViewModel>()
        val lifecycleOwner = LocalLifecycleOwner.current
        val appState by viewModel.appState.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = Lifecycle.State.STARTED,
        )

        SearchScreen(
            appState = appState,
            onSearch = { query -> viewModel.search(query) },
            onClearSearch = { viewModel.clearSearch() },
            getImageUrl = { item -> viewModel.getImageUrl(item) },
            onBackClick = { navController.popBackStack() },
            onNowPlayingClick = { navController.navigate(Screen.NowPlaying.route) },
        )
    }

    composable(Screen.Favorites.route) {
        val viewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<MainAppViewModel>()
        val lifecycleOwner = LocalLifecycleOwner.current
        val appState by viewModel.appState.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = Lifecycle.State.STARTED,
        )

        LaunchedEffect(Unit) {
            viewModel.loadFavorites()
        }

        FavoritesScreen(
            favorites = appState.favorites,
            isLoading = appState.isLoading,
            errorMessage = appState.errorMessage,
            onRefresh = { viewModel.loadFavorites() },
            getImageUrl = { item -> viewModel.getImageUrl(item) },
            onBackClick = { navController.popBackStack() },
            onNowPlayingClick = { navController.navigate(Screen.NowPlaying.route) },
        )
    }

    composable(Screen.Profile.route) {
        val viewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<MainAppViewModel>()
        val lifecycleOwner = LocalLifecycleOwner.current
        val currentServer by viewModel.currentServer.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            initialValue = null,
        )
        val appState by viewModel.appState.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = Lifecycle.State.STARTED,
        )

        LaunchedEffect(Unit) {
            viewModel.loadCurrentUser()
        }

        ProfileScreen(
            currentServer = currentServer,
            currentUser = appState.currentUser,
            userAvatarUrl = viewModel.getUserAvatarUrl(
                currentServer?.userId,
                appState.currentUser?.primaryImageTag,
            ),
            onLogout = {
                viewModel.logout()
                onLogout()
                navController.navigate(Screen.ServerConnection.route) {
                    popUpTo(0) { inclusive = true }
                }
            },
            onSettingsClick = { navController.navigate(Screen.Settings.route) },
            onBackClick = { navController.popBackStack() },
            onNowPlayingClick = { navController.navigate(Screen.NowPlaying.route) },
        )
    }

    composable(Screen.Settings.route) {
        SettingsScreen(
            onBackClick = { navController.popBackStack() },
            onManagePinsClick = { navController.navigate(Screen.PinSettings.route) },
            onSubtitleSettingsClick = { navController.navigate(Screen.SubtitleSettings.route) },
            onPrivacyPolicyClick = { navController.navigate(Screen.PrivacyPolicy.route) },
        )
    }

    composable(Screen.PinSettings.route) {
        PinningSettingsScreen(
            onBackClick = { navController.popBackStack() },
        )
    }

    composable(Screen.SubtitleSettings.route) {
        SubtitleSettingsScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable(Screen.PrivacyPolicy.route) {
        com.rpeters.jellyfin.ui.screens.settings.PrivacyPolicyScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
