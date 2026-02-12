@file:Suppress("DEPRECATION")

package com.rpeters.jellyfin.ui.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.FeatureFlags
import com.rpeters.jellyfin.ui.screens.FavoritesScreen
import com.rpeters.jellyfin.ui.screens.ImmersiveFavoritesScreen
import com.rpeters.jellyfin.ui.screens.ImmersiveSearchScreen
import com.rpeters.jellyfin.ui.screens.ProfileScreen
import com.rpeters.jellyfin.ui.screens.SearchScreen
import com.rpeters.jellyfin.ui.screens.SettingsRecommendationOptions
import com.rpeters.jellyfin.ui.screens.SettingsScreen
import com.rpeters.jellyfin.ui.screens.TranscodingDiagnosticsScreen
import com.rpeters.jellyfin.ui.screens.settings.AppearanceSettingsScreen
import com.rpeters.jellyfin.ui.screens.settings.PinningSettingsScreen
import com.rpeters.jellyfin.ui.screens.settings.PrivacySettingsScreen
import com.rpeters.jellyfin.ui.screens.settings.SettingsSectionScreen
import com.rpeters.jellyfin.ui.screens.settings.SubtitleSettingsScreen
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.ui.viewmodel.RemoteConfigViewModel
import com.rpeters.jellyfin.utils.SecureLogger

/**
 * Profile, search, favorites, and settings routes.
 */
fun androidx.navigation.NavGraphBuilder.profileNavGraph(
    navController: NavHostController,
    onLogout: () -> Unit,
) {
    composable(
        route = Screen.Search.route,
        arguments = listOf(
            androidx.navigation.navArgument("query") {
                type = androidx.navigation.NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { backStackEntry ->
        val query = backStackEntry.arguments?.getString("query")
        val viewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<MainAppViewModel>()
        
        // If a query was passed via navigation, trigger a search immediately
        LaunchedEffect(query) {
            if (!query.isNullOrBlank()) {
                viewModel.search(query)
            }
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        val appState by viewModel.appState.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = Lifecycle.State.STARTED,
        )

        // Feature Flag: Check if immersive UI should be used
        val remoteConfigViewModel: RemoteConfigViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
        val useImmersiveUI = remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.ENABLE_IMMERSIVE_UI) &&
            remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.IMMERSIVE_SEARCH_SCREEN)

        if (BuildConfig.DEBUG) {
            SecureLogger.d("ProfileNavGraph", "SearchScreen: enable_immersive_ui=${remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.ENABLE_IMMERSIVE_UI)}, immersive_search_screen=${remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.IMMERSIVE_SEARCH_SCREEN)}, using immersive: $useImmersiveUI")
        }

        if (useImmersiveUI) {
            ImmersiveSearchScreen(
                appState = appState,
                onSearch = { query -> viewModel.search(query) },
                onClearSearch = { viewModel.clearSearch() },
                getImageUrl = { item -> viewModel.getImageUrl(item) },
                onBackClick = { navController.popBackStack() },
                onNowPlayingClick = { navController.navigate(Screen.NowPlaying.route) },
                onItemClick = { item ->
                    when (item.type) {
                        org.jellyfin.sdk.model.api.BaseItemKind.MOVIE -> {
                            item.id.let { movieId ->
                                navController.navigate(Screen.MovieDetail.createRoute(movieId.toString()))
                            }
                        }

                        org.jellyfin.sdk.model.api.BaseItemKind.VIDEO -> {
                            item.id.let { videoId ->
                                navController.navigate(Screen.HomeVideoDetail.createRoute(videoId.toString()))
                            }
                        }

                        org.jellyfin.sdk.model.api.BaseItemKind.SERIES -> {
                            item.id.let { seriesId ->
                                navController.navigate(Screen.TVSeasons.createRoute(seriesId.toString()))
                            }
                        }

                        org.jellyfin.sdk.model.api.BaseItemKind.EPISODE -> {
                            item.id.let { episodeId ->
                                navController.navigate(Screen.TVEpisodeDetail.createRoute(episodeId.toString()))
                            }
                        }

                        else -> {
                            item.id.let { genericId ->
                                navController.navigate(Screen.ItemDetail.createRoute(genericId.toString()))
                            }
                        }
                    }
                },
            )
        } else {
            SearchScreen(
                appState = appState,
                onSearch = { query -> viewModel.search(query) },
                onClearSearch = { viewModel.clearSearch() },
                getImageUrl = { item -> viewModel.getImageUrl(item) },
                onBackClick = { navController.popBackStack() },
                onNowPlayingClick = { navController.navigate(Screen.NowPlaying.route) },
                onItemClick = { item ->
                    when (item.type) {
                        org.jellyfin.sdk.model.api.BaseItemKind.MOVIE -> {
                            item.id.let { movieId ->
                                navController.navigate(Screen.MovieDetail.createRoute(movieId.toString()))
                            }
                        }

                        org.jellyfin.sdk.model.api.BaseItemKind.VIDEO -> {
                            item.id.let { videoId ->
                                navController.navigate(Screen.HomeVideoDetail.createRoute(videoId.toString()))
                            }
                        }

                        org.jellyfin.sdk.model.api.BaseItemKind.SERIES -> {
                            item.id.let { seriesId ->
                                navController.navigate(Screen.TVSeasons.createRoute(seriesId.toString()))
                            }
                        }

                        org.jellyfin.sdk.model.api.BaseItemKind.EPISODE -> {
                            item.id.let { episodeId ->
                                navController.navigate(Screen.TVEpisodeDetail.createRoute(episodeId.toString()))
                            }
                        }

                        else -> {
                            item.id.let { genericId ->
                                navController.navigate(Screen.ItemDetail.createRoute(genericId.toString()))
                            }
                        }
                    }
                },
            )
        }
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

        // Feature Flag: Check if immersive UI should be used
        val remoteConfigViewModel: RemoteConfigViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
        val useImmersiveUI = remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.ENABLE_IMMERSIVE_UI) &&
            remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.IMMERSIVE_FAVORITES_SCREEN)

        if (BuildConfig.DEBUG) {
            SecureLogger.d("ProfileNavGraph", "FavoritesScreen: enable_immersive_ui=${remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.ENABLE_IMMERSIVE_UI)}, immersive_favorites_screen=${remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.IMMERSIVE_FAVORITES_SCREEN)}, using immersive: $useImmersiveUI")
        }

        if (useImmersiveUI) {
            ImmersiveFavoritesScreen(
                favorites = appState.favorites,
                isLoading = appState.isLoading,
                errorMessage = appState.errorMessage,
                onRefresh = { viewModel.loadFavorites() },
                getImageUrl = { item -> viewModel.getImageUrl(item) },
                onBackClick = { navController.popBackStack() },
                onNowPlayingClick = { navController.navigate(Screen.NowPlaying.route) },
                onItemClick = { item ->
                    when (item.type) {
                        org.jellyfin.sdk.model.api.BaseItemKind.MOVIE -> {
                            item.id.let { movieId ->
                                navController.navigate(Screen.MovieDetail.createRoute(movieId.toString()))
                            }
                        }

                        org.jellyfin.sdk.model.api.BaseItemKind.VIDEO -> {
                            item.id.let { videoId ->
                                navController.navigate(Screen.HomeVideoDetail.createRoute(videoId.toString()))
                            }
                        }

                        org.jellyfin.sdk.model.api.BaseItemKind.SERIES -> {
                            item.id.let { seriesId ->
                                navController.navigate(Screen.TVSeasons.createRoute(seriesId.toString()))
                            }
                        }

                        org.jellyfin.sdk.model.api.BaseItemKind.EPISODE -> {
                            item.id.let { episodeId ->
                                navController.navigate(Screen.TVEpisodeDetail.createRoute(episodeId.toString()))
                            }
                        }

                        else -> {
                            item.id.let { genericId ->
                                navController.navigate(Screen.ItemDetail.createRoute(genericId.toString()))
                            }
                        }
                    }
                },
            )
        } else {
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
        val serverInfoResult by viewModel.serverInfo.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = Lifecycle.State.STARTED,
        )

        LaunchedEffect(Unit) {
            viewModel.loadCurrentUser()
            viewModel.loadServerInfo()
        }

        ProfileScreen(
            currentServer = currentServer,
            serverInfo = (serverInfoResult as? com.rpeters.jellyfin.data.repository.common.ApiResult.Success)?.data,
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
            onAppearanceSettingsClick = { navController.navigate(Screen.AppearanceSettings.route) },
            onPlaybackSettingsClick = { navController.navigate(Screen.PlaybackSettings.route) },
            onDownloadsSettingsClick = { navController.navigate(Screen.DownloadsSettings.route) },
            onNotificationsSettingsClick = { navController.navigate(Screen.NotificationsSettings.route) },
            onPrivacySettingsClick = { navController.navigate(Screen.PrivacySettings.route) },
            onAccessibilitySettingsClick = { navController.navigate(Screen.AccessibilitySettings.route) },
            onTranscodingDiagnosticsClick = { navController.navigate(Screen.TranscodingDiagnostics.route) },
        )
    }

    composable(Screen.AppearanceSettings.route) {
        AppearanceSettingsScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable(Screen.PlaybackSettings.route) {
        com.rpeters.jellyfin.ui.screens.settings.PlaybackSettingsScreen(
            onBackClick = { navController.popBackStack() },
        )
    }

    composable(Screen.DownloadsSettings.route) {
        SettingsSectionScreen(
            titleRes = R.string.settings_downloads_title,
            descriptionRes = R.string.settings_downloads_description,
            optionRes = SettingsRecommendationOptions.downloads,
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable(Screen.NotificationsSettings.route) {
        SettingsSectionScreen(
            titleRes = R.string.settings_notifications_title,
            descriptionRes = R.string.settings_notifications_description,
            optionRes = SettingsRecommendationOptions.notifications,
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable(Screen.PrivacySettings.route) {
        PrivacySettingsScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable(Screen.AccessibilitySettings.route) {
        SettingsSectionScreen(
            titleRes = R.string.settings_accessibility_title,
            descriptionRes = R.string.settings_accessibility_description,
            optionRes = SettingsRecommendationOptions.accessibility,
            onNavigateBack = { navController.popBackStack() },
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

    composable(Screen.TranscodingDiagnostics.route) {
        TranscodingDiagnosticsScreen(
            onNavigateBack = { navController.popBackStack() },
            onItemClick = { item ->
                when (item.type) {
                    org.jellyfin.sdk.model.api.BaseItemKind.MOVIE -> {
                        item.id.let { movieId ->
                            navController.navigate(Screen.MovieDetail.createRoute(movieId.toString()))
                        }
                    }

                    org.jellyfin.sdk.model.api.BaseItemKind.VIDEO -> {
                        item.id.let { videoId ->
                            navController.navigate(Screen.HomeVideoDetail.createRoute(videoId.toString()))
                        }
                    }

                    org.jellyfin.sdk.model.api.BaseItemKind.SERIES -> {
                        item.id.let { seriesId ->
                            navController.navigate(Screen.TVSeasons.createRoute(seriesId.toString()))
                        }
                    }

                    org.jellyfin.sdk.model.api.BaseItemKind.EPISODE -> {
                        item.id.let { episodeId ->
                            navController.navigate(Screen.TVEpisodeDetail.createRoute(episodeId.toString()))
                        }
                    }

                    else -> {
                        item.id.let { genericId ->
                            navController.navigate(Screen.ItemDetail.createRoute(genericId.toString()))
                        }
                    }
                }
            },
        )
    }

    composable(Screen.PrivacyPolicy.route) {
        com.rpeters.jellyfin.ui.screens.settings.PrivacyPolicyScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
