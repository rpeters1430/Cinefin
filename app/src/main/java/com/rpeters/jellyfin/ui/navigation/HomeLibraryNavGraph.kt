@file:Suppress("DEPRECATION")

package com.rpeters.jellyfin.ui.navigation

import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
            {
                    query: String ->
                viewModel.search(query)
                navController.navigate(Screen.Search.route)
            }
        }
        val onClearSearch = remember(viewModel) { { viewModel.clearSearch() } }
        val onSearchClick = remember(navController) { { navController.navigate(Screen.Search.route) } }
        val onAiAssistantClick = remember(navController) { { navController.navigate(Screen.AiAssistant.route) } }
        val getImageUrl = remember(viewModel) { { item: org.jellyfin.sdk.model.api.BaseItemDto -> viewModel.getImageUrl(item) } }
        val getBackdropUrl = remember(viewModel) { { item: org.jellyfin.sdk.model.api.BaseItemDto -> viewModel.getBackdropUrl(item) } }
        val getSeriesImageUrl = remember(viewModel) { { item: org.jellyfin.sdk.model.api.BaseItemDto -> viewModel.getSeriesImageUrl(item) } }

        val onItemClick = remember(navController) {
            {
                    item: org.jellyfin.sdk.model.api.BaseItemDto ->
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
            }
        }

        val onLibraryClick = remember(navController) {
            {
                    library: org.jellyfin.sdk.model.api.BaseItemDto ->
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

        // CRITICAL FIX: Load data once a session is connected, even if currentServer is temporarily null.
        // On some devices, repository state restoration can report isConnected before currentServer emits.
        // Without this fallback, Home can render chrome with empty content until a manual refresh.
        LaunchedEffect(currentServer, isConnected) {
            val server = currentServer
            val shouldLoad = isConnected && appState.libraries.isEmpty() && !appState.isLoading

            if (server != null && (shouldLoad || appState.errorMessage != null)) {
                if (BuildConfig.DEBUG) {
                    Log.d("HomeScreen", "Session ready, loading initial data for: ${server.name}")
                }
                viewModel.loadInitialData()
                viewModel.runAiHealthCheck()
            } else if (server == null && shouldLoad) {
                if (BuildConfig.DEBUG) {
                    Log.d("HomeScreen", "Connected but server metadata pending, triggering fallback load")
                }
                viewModel.loadInitialData()
            } else if (BuildConfig.DEBUG && !isConnected) {
                Log.d("HomeScreen", "Waiting for server connection before loading data")
            }
        }

        // Use ImmersiveHomeScreen by default
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

        // ✅ Performance: Stabilize callbacks
        val onRefresh = remember(viewModel) { { viewModel.loadInitialData(forceRefresh = true) } }
        val getImageUrl = remember(viewModel) { { item: org.jellyfin.sdk.model.api.BaseItemDto -> viewModel.getImageUrl(item) } }
        val onLibraryClick = remember(navController) {
            {
                    library: org.jellyfin.sdk.model.api.BaseItemDto ->
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

        LaunchedEffect(Unit) {
            if (appState.libraries.isEmpty() && !appState.isLoading) {
                if (BuildConfig.DEBUG) {
                    SecureLogger.v("NavGraph", "Library screen - triggering initial data load")
                }
                viewModel.loadInitialData()
            }
        }

        // Use ImmersiveLibraryScreen by default
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
