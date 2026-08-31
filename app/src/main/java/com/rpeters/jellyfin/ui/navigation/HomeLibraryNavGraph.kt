@file:Suppress("DEPRECATION")

package com.rpeters.jellyfin.ui.navigation

import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.ui.screens.ImmersiveHomeScreen
import com.rpeters.jellyfin.ui.screens.ImmersiveLibraryScreen
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.coroutines.CancellationException

/**
 * Home and library navigation destinations.
 */
fun androidx.navigation.NavGraphBuilder.homeLibraryNavGraph(
    navController: NavHostController,
) {
    composable(Screen.Home.route) {
        val viewModel: MainAppViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
        val lifecycleOwner = LocalLifecycleOwner.current
        val appState by viewModel.appState.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = Lifecycle.State.STARTED,
        )
        val currentServer by viewModel.currentServer.collectAsStateWithLifecycle(
            initialValue = null,
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = Lifecycle.State.STARTED,
        )
        val isConnected by viewModel.isConnected.collectAsStateWithLifecycle(
            initialValue = false,
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = Lifecycle.State.STARTED,
        )

        // ✅ Performance: Stabilize callbacks to prevent unnecessary recompositions
        val onRefresh = remember(viewModel) { { viewModel.loadInitialData() } }
        val onSearch = remember(viewModel, navController) {
            { query: String ->
                viewModel.search(query)
                navController.navigate(Screen.Search.route)
            }
        }
        val onClearSearch = remember(viewModel) { { viewModel.clearSearch() } }
        val onSearchClick = remember(navController) { { navController.navigate(Screen.Search.route) } }
        val onAiAssistantClick = remember(navController) { { navController.navigate(Screen.AiAssistant.route) } }
        val getImageUrl = remember(viewModel, currentServer) { { item: org.jellyfin.sdk.model.api.BaseItemDto -> viewModel.getImageUrl(item) } }
        val getBackdropUrl = remember(viewModel, currentServer) { { item: org.jellyfin.sdk.model.api.BaseItemDto -> viewModel.getBackdropUrl(item) } }
        val getSeriesImageUrl = remember(viewModel, currentServer) { { item: org.jellyfin.sdk.model.api.BaseItemDto -> viewModel.getSeriesImageUrl(item) } }

        val onItemClick = remember(navController) {
            { item: org.jellyfin.sdk.model.api.BaseItemDto ->
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

                    org.jellyfin.sdk.model.api.BaseItemKind.PLAYLIST -> {
                        item.id.let { playlistId ->
                            navController.navigate(Screen.PlaylistDetail.createRoute(playlistId.toString()))
                        }
                    }

                    else -> {
                        item.id.let { genericId ->
                            navController.navigate(Screen.ItemDetail.createRoute(genericId.toString()))
                        }
                    }
                }
            }
        }

        val onLibraryClick = remember(navController) {
            { library: org.jellyfin.sdk.model.api.BaseItemDto ->
                try {
                    libraryRouteFor(library)?.let { route ->
                        navController.navigate(route)
                    } ?: run {
                        Log.w(
                            "NavGraph",
                            "No route found for library: ${library.name} (${library.collectionType})",
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                }
                Unit
            }
        }

        val onSettingsClick = remember(navController) { { navController.navigate(Screen.Settings.route) } }
        val onNowPlayingClick = remember(navController) { { navController.navigate(Screen.NowPlaying.route) } }
        val onAiHealthCheck = remember(viewModel) { { viewModel.runAiHealthCheck(force = true) } }

        // Wait for an active connection before loading data. A restored but expired session can
        // populate currentServer before auto-login completes, which would otherwise trigger a
        // doomed initial load on phones.
        LaunchedEffect(currentServer, isConnected) {
            val server = currentServer
            if (server != null && isConnected) {
                if (BuildConfig.DEBUG) {
                    Log.d("HomeScreen", "Current server available, loading initial data for: ${server.name}")
                }
                viewModel.loadInitialData()
                viewModel.runAiHealthCheck()
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d("HomeScreen", "Waiting for server connection before loading data")
                }
            }
        }

        ImmersiveHomeScreen(
            appState = appState,
            currentServer = currentServer,
            onRefresh = onRefresh,
            onSearch = onSearch,
            onClearSearch = onClearSearch,
            onSearchClick = onSearchClick,
            onAiAssistantClick = onAiAssistantClick,
            getImageUrl = getImageUrl,
            getBackdropUrl = getBackdropUrl,
            getSeriesImageUrl = getSeriesImageUrl,
            onItemClick = onItemClick,
            onLibraryClick = onLibraryClick,
            onSettingsClick = onSettingsClick,
            onNowPlayingClick = onNowPlayingClick,
            onAiHealthCheck = onAiHealthCheck,
            animatedVisibilityScope = this,
        )
    }

    composable(Screen.Library.route) {
        val viewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<MainAppViewModel>()
        val lifecycleOwner = LocalLifecycleOwner.current
        val appState by viewModel.appState.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = Lifecycle.State.STARTED,
        )
        val currentServer by viewModel.currentServer.collectAsStateWithLifecycle(
            initialValue = null,
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = Lifecycle.State.STARTED,
        )
        val isConnected by viewModel.isConnected.collectAsStateWithLifecycle(
            initialValue = false,
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = Lifecycle.State.STARTED,
        )

        val onRefresh = remember(viewModel) { { viewModel.loadInitialData(forceRefresh = true) } }
        val getImageUrl = remember(viewModel, currentServer) { { item: org.jellyfin.sdk.model.api.BaseItemDto -> viewModel.getImageUrl(item) } }
        val onLibraryClick = remember(navController) {
            { library: org.jellyfin.sdk.model.api.BaseItemDto ->
                try {
                    libraryRouteFor(library)?.let { route ->
                        navController.navigate(route)
                    } ?: run {
                        Log.w(
                            "NavGraph",
                            "No route found for library: ${library.name} (${library.collectionType})",
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                }
                Unit
            }
        }
        val onSearchClick = remember(navController) { { navController.navigate(Screen.Search.route) } }
        val onAiAssistantClick = remember(navController) { { navController.navigate(Screen.AiAssistant.route) } }
        val onSettingsClick = remember(navController) { { navController.navigate(Screen.Settings.route) } }
        val onNowPlayingClick = remember(navController) { { navController.navigate(Screen.NowPlaying.route) } }

        // Only auto-trigger the initial load once per server session. Without this guard, an
        // empty (but successful) libraries response re-satisfies this effect's condition on
        // every isLoading true->false transition, causing the screen to loop between the
        // "no libraries" empty state and the loading state forever. Pull-to-refresh (onRefresh)
        // remains available for the user to explicitly retry.
        //
        // The "already attempted" identity is stored as a value compared inside the effect
        // rather than as a rememberSaveable key: currentServer is collected with
        // initialValue = null, so keying rememberSaveable directly on it would transiently
        // reset to (null) on every recomposition before the real value arrives (e.g. on
        // configuration change), discarding the guard and allowing one more spurious retry.
        // Including loginTimestamp in the identity (rather than just server URL/userId) makes
        // a fresh login to the same server/user count as a new session, so auto-load still
        // fires after a logout/re-login within the same process.
        var lastAttemptedSessionKey by rememberSaveable { mutableStateOf<String?>(null) }

        LaunchedEffect(
            currentServer?.normalizedUrl ?: currentServer?.url,
            currentServer?.userId,
            currentServer?.loginTimestamp,
            isConnected,
            appState.libraries.size,
            appState.isLoading,
            appState.errorMessage,
        ) {
            val server = currentServer
            val sessionKey = server?.let { "${it.normalizedUrl ?: it.url}|${it.userId}|${it.loginTimestamp}" }
            if (
                isConnected &&
                server != null &&
                appState.libraries.isEmpty() &&
                !appState.isLoading &&
                appState.errorMessage == null &&
                lastAttemptedSessionKey != sessionKey
            ) {
                if (BuildConfig.DEBUG) {
                    SecureLogger.v("NavGraph", "Library screen - session ready, triggering initial data load")
                }
                lastAttemptedSessionKey = sessionKey
                viewModel.loadInitialData()
            }
        }

        ImmersiveLibraryScreen(
            libraries = appState.libraries,
            isLoading = appState.isLoading,
            errorMessage = appState.errorMessage,
            onRefresh = onRefresh,
            getImageUrl = getImageUrl,
            onLibraryClick = onLibraryClick,
            onSearchClick = onSearchClick,
            onAiAssistantClick = onAiAssistantClick,
            onSettingsClick = onSettingsClick,
            onNowPlayingClick = onNowPlayingClick,
            animatedVisibilityScope = this,
        )
    }

    composable(Screen.AiAssistant.route) {
        com.rpeters.jellyfin.ui.screens.AiAssistantScreen(
            onBackClick = { navController.popBackStack() },
            onItemClick = { item ->
                when (item.type) {
                    org.jellyfin.sdk.model.api.BaseItemKind.MOVIE -> {
                        item.id.let { movieId ->
                            navController.navigate(Screen.MovieDetail.createRoute(movieId.toString()))
                        }
                    }
                    org.jellyfin.sdk.model.api.BaseItemKind.SERIES -> {
                        item.id.let { seriesId ->
                            navController.navigate(Screen.TVSeasons.createRoute(seriesId.toString()))
                        }
                    }
                    org.jellyfin.sdk.model.api.BaseItemKind.PLAYLIST -> {
                        item.id.let { playlistId ->
                            navController.navigate(Screen.PlaylistDetail.createRoute(playlistId.toString()))
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

    composable(
        route = Screen.Requests.route,
        arguments = listOf(
            androidx.navigation.navArgument("query") {
                type = androidx.navigation.NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { backStackEntry ->
        com.rpeters.jellyfin.ui.screens.RequestsScreen(
            initialQuery = backStackEntry.arguments?.getString("query"),
            onNavigateToSettings = { navController.navigate(Screen.SeerrSettings.route) },
        )
    }
}
