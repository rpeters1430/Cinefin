@file:Suppress("DEPRECATION")

package com.rpeters.jellyfin.ui.navigation

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.downloads.DownloadsScreen
import com.rpeters.jellyfin.ui.screens.AiDiagnosticsScreen
import com.rpeters.jellyfin.ui.screens.ImmersiveFavoritesScreen
import com.rpeters.jellyfin.ui.screens.ImmersiveSearchScreen
import com.rpeters.jellyfin.ui.screens.ProfileScreen
import com.rpeters.jellyfin.ui.screens.SettingsRecommendationOptions
import com.rpeters.jellyfin.ui.screens.SettingsScreen
import com.rpeters.jellyfin.ui.screens.TranscodingDiagnosticsScreen
import com.rpeters.jellyfin.ui.screens.settings.AppearanceSettingsScreen
import com.rpeters.jellyfin.ui.screens.settings.EpisodeNotificationSettingsScreen
import com.rpeters.jellyfin.ui.screens.settings.PinningSettingsScreen
import com.rpeters.jellyfin.ui.screens.settings.PrivacySettingsScreen
import com.rpeters.jellyfin.ui.screens.settings.MediaRequestSettingsScreen
import com.rpeters.jellyfin.ui.screens.settings.SeerrSettingsScreen
import com.rpeters.jellyfin.ui.screens.settings.SettingsSectionScreen
import com.rpeters.jellyfin.ui.screens.settings.SubtitleSettingsScreen
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Shared navigation logic for an item selected from search/favorites/diagnostics lists.
 */
private fun navigateToMediaItem(navController: NavHostController, item: BaseItemDto) {
    when (item.type) {
        BaseItemKind.MOVIE -> navController.navigate(Screen.MovieDetail.createRoute(item.id.toString()))
        BaseItemKind.VIDEO -> navController.navigate(Screen.HomeVideoDetail.createRoute(item.id.toString()))
        BaseItemKind.SERIES -> navController.navigate(Screen.TVSeasons.createRoute(item.id.toString()))
        BaseItemKind.EPISODE -> navController.navigate(Screen.TVEpisodeDetail.createRoute(item.id.toString()))
        BaseItemKind.PLAYLIST -> navController.navigate(Screen.PlaylistDetail.createRoute(item.id.toString()))
        BaseItemKind.PERSON -> navController.navigate(
            Screen.PersonDetail.createRoute(item.id.toString(), item.name.orEmpty()),
        )
        else -> navController.navigate(Screen.ItemDetail.createRoute(item.id.toString()))
    }
}

/**
 * Profile, search, favorites, and settings routes.
 */
@OptIn(UnstableApi::class)
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
        SearchRoute(navController, backStackEntry.arguments?.getString("query"))
    }

    composable(Screen.Favorites.route) {
        FavoritesRoute(navController)
    }

    composable(Screen.Profile.route) {
        ProfileRoute(navController, onLogout)
    }

    composable(Screen.Settings.route) {
        SettingsRoute(navController, onLogout)
    }

    composable(Screen.SeerrSettings.route) {
        // Legacy route — redirect to new unified screen
        MediaRequestSettingsScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable(Screen.MediaRequestSettings.route) {
        MediaRequestSettingsScreen(onNavigateBack = { navController.popBackStack() })
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
        DownloadsSettingsRoute(navController)
    }

    composable(Screen.NotificationsSettings.route) {
        EpisodeNotificationSettingsScreen(
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
        TranscodingDiagnosticsRoute(navController)
    }

    composable(Screen.AiDiagnostics.route) {
        AiDiagnosticsScreen(
            onBackClick = { navController.popBackStack() },
        )
    }

    composable(Screen.PrivacyPolicy.route) {
        com.rpeters.jellyfin.ui.screens.settings.PrivacyPolicyScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }
}

@Composable
private fun SearchRoute(navController: NavHostController, query: String?) {
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

    // Use ImmersiveSearchScreen by default
    ImmersiveSearchScreen(
        appState = appState,
        onSearch = { searchQuery -> viewModel.search(searchQuery) },
        onClearSearch = { viewModel.clearSearch() },
        getImageUrl = { item -> viewModel.getImageUrl(item) },
        onBackClick = { navController.popBackStack() },
        onNowPlayingClick = { navController.navigate(Screen.NowPlaying.route) },
        onSearchRequests = { requestQuery ->
            navController.navigate(Screen.Requests.createRoute(requestQuery))
        },
        onItemClick = { item -> navigateToMediaItem(navController, item) },
    )
}

@Composable
private fun FavoritesRoute(navController: NavHostController) {
    val viewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<MainAppViewModel>()
    val lifecycleOwner = LocalLifecycleOwner.current
    val appState by viewModel.appState.collectAsStateWithLifecycle(
        lifecycle = lifecycleOwner.lifecycle,
        minActiveState = Lifecycle.State.STARTED,
    )

    LaunchedEffect(Unit) {
        viewModel.loadFavorites()
    }

    // Use ImmersiveFavoritesScreen by default
    ImmersiveFavoritesScreen(
        favorites = appState.favorites,
        isLoading = appState.isLoading,
        errorMessage = appState.errorMessage,
        onRefresh = { viewModel.loadFavorites() },
        getImageUrl = { item -> viewModel.getImageUrl(item) },
        onBackClick = { navController.popBackStack() },
        onNowPlayingClick = { navController.navigate(Screen.NowPlaying.route) },
        onItemClick = { item -> navigateToMediaItem(navController, item) },
    )
}

@Composable
private fun ProfileRoute(navController: NavHostController, onLogout: () -> Unit) {
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

@Composable
private fun SettingsRoute(navController: NavHostController, onLogout: () -> Unit) {
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

    SettingsScreen(
        onBackClick = { navController.popBackStack() },
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
        onNowPlayingClick = { navController.navigate(Screen.NowPlaying.route) },
        onManagePinsClick = { navController.navigate(Screen.PinSettings.route) },
        onSubtitleSettingsClick = { navController.navigate(Screen.SubtitleSettings.route) },
        onPrivacyPolicyClick = { navController.navigate(Screen.PrivacyPolicy.route) },
        onAppearanceSettingsClick = { navController.navigate(Screen.AppearanceSettings.route) },
        onPlaybackSettingsClick = { navController.navigate(Screen.PlaybackSettings.route) },
        onDownloadsSettingsClick = { navController.navigate(Screen.DownloadsSettings.route) },
        onNotificationsSettingsClick = { navController.navigate(Screen.NotificationsSettings.route) },
        onPrivacySettingsClick = { navController.navigate(Screen.PrivacySettings.route) },
        onAccessibilitySettingsClick = { navController.navigate(Screen.AccessibilitySettings.route) },
        onSeerrSettingsClick = { navController.navigate(Screen.MediaRequestSettings.route) },
        onTranscodingDiagnosticsClick = { navController.navigate(Screen.TranscodingDiagnostics.route) },
        onAiDiagnosticsClick = { navController.navigate(Screen.AiDiagnostics.route) },
    )
}

@Composable
private fun DownloadsSettingsRoute(navController: NavHostController) {
    DownloadsScreen(
        onNavigateBack = { navController.popBackStack() },
        onOpenItemDetail = { download ->
            when (download.itemType.uppercase()) {
                BaseItemKind.MOVIE.name -> {
                    navController.navigate(Screen.MovieDetail.createRoute(download.jellyfinItemId))
                }
                BaseItemKind.EPISODE.name -> {
                    navController.navigate(Screen.TVEpisodeDetail.createRoute(download.jellyfinItemId))
                }
                else -> {
                    navController.navigate(Screen.ItemDetail.createRoute(download.jellyfinItemId))
                }
            }
        },
    )
}

@Composable
private fun TranscodingDiagnosticsRoute(navController: NavHostController) {
    TranscodingDiagnosticsScreen(
        onNavigateBack = { navController.popBackStack() },
        onItemClick = { item -> navigateToMediaItem(navController, item) },
    )
}
