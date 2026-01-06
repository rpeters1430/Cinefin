@file:Suppress("DEPRECATION")

package com.rpeters.jellyfin.ui.navigation

import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.ui.screens.HomeScreen
import com.rpeters.jellyfin.ui.screens.LibraryScreen
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.utils.SecureLogger

/**
 * Home and library navigation destinations.
 */
fun androidx.navigation.NavGraphBuilder.homeLibraryNavGraph(
    navController: NavHostController,
) {
    composable(Screen.Home.route) {
        val viewModel: MainAppViewModel = androidx.hilt.navigation.compose.hiltViewModel()
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

        // CRITICAL FIX: Wait for currentServer to be available before loading data
        // During auto-login, the navigation happens before the session is fully established
        // This ensures we only load data when we have a valid connection
        LaunchedEffect(currentServer) {
            val server = currentServer
            if (server != null) {
                if (BuildConfig.DEBUG) {
                    Log.d("HomeScreen", "Current server available, loading initial data for: ${server.name}")
                }
                viewModel.loadInitialData()
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d("HomeScreen", "Waiting for server connection before loading data")
                }
            }
        }

        HomeScreen(
            appState = appState,
            currentServer = currentServer,
            onRefresh = { viewModel.loadInitialData() },
            onSearch = { query ->
                viewModel.search(query)
                navController.navigate(Screen.Search.route)
            },
            onClearSearch = { viewModel.clearSearch() },
            onSearchClick = { navController.navigate(Screen.Search.route) },
            getImageUrl = { item -> viewModel.getImageUrl(item) },
            getBackdropUrl = { item -> viewModel.getBackdropUrl(item) },
            getSeriesImageUrl = { item -> viewModel.getSeriesImageUrl(item) },
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
            onLibraryClick = { library ->
                try {
                    libraryRouteFor(library)?.let { route ->
                        navController.navigate(route)
                    } ?: run {
                        Log.w(
                            "NavGraph",
                            "No route found for library: ${library.name} (${library.collectionType})",
                        )
                    }
                } catch (e: Exception) {
                    SecureLogger.e("NavGraph", "Error navigating to library: ${library.name}", e)
                }
            },
            onSettingsClick = { navController.navigate(Screen.Profile.route) },
            onNowPlayingClick = { navController.navigate(Screen.NowPlaying.route) },
        )
    }

    composable(Screen.Library.route) {
        val viewModel = androidx.hilt.navigation.compose.hiltViewModel<MainAppViewModel>()
        val lifecycleOwner = LocalLifecycleOwner.current
        val appState by viewModel.appState.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = Lifecycle.State.STARTED,
        )

        LaunchedEffect(Unit) {
            if (appState.libraries.isEmpty() && !appState.isLoading) {
                if (BuildConfig.DEBUG) {
                    SecureLogger.v("NavGraph", "Library screen - triggering initial data load")
                }
                viewModel.loadInitialData()
            }
        }

        LibraryScreen(
            libraries = appState.libraries,
            isLoading = appState.isLoading,
            errorMessage = appState.errorMessage,
            onRefresh = { viewModel.loadInitialData(forceRefresh = true) },
            getImageUrl = { item -> viewModel.getImageUrl(item) },
            onLibraryClick = { library ->
                try {
                    libraryRouteFor(library)?.let { route ->
                        navController.navigate(route)
                    } ?: run {
                        Log.w(
                            "NavGraph",
                            "No route found for library: ${library.name} (${library.collectionType})",
                        )
                    }
                } catch (e: Exception) {
                    SecureLogger.e("NavGraph", "Error navigating to library: ${library.name}", e)
                }
            },
            onSearchClick = { navController.navigate(Screen.Search.route) },
            onSettingsClick = { navController.navigate(Screen.Profile.route) },
            onNowPlayingClick = { navController.navigate(Screen.NowPlaying.route) },
        )
    }
}
