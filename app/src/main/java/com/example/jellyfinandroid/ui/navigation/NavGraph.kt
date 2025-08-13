package com.example.jellyfinandroid.ui.navigation

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.jellyfinandroid.BuildConfig
import com.example.jellyfinandroid.ui.screens.FavoritesScreen
import com.example.jellyfinandroid.ui.screens.HomeScreen
import com.example.jellyfinandroid.ui.screens.LibraryScreen
import com.example.jellyfinandroid.ui.screens.MovieDetailScreen
import com.example.jellyfinandroid.ui.screens.MoviesScreen
import com.example.jellyfinandroid.ui.screens.MusicScreen
import com.example.jellyfinandroid.ui.screens.ProfileScreen
import com.example.jellyfinandroid.ui.screens.QuickConnectScreen
import com.example.jellyfinandroid.ui.screens.SearchScreen
import com.example.jellyfinandroid.ui.screens.ServerConnectionScreen
import com.example.jellyfinandroid.ui.screens.TVEpisodeDetailScreen
import com.example.jellyfinandroid.ui.screens.TVEpisodesScreen
import com.example.jellyfinandroid.ui.screens.TVSeasonScreen
import com.example.jellyfinandroid.ui.screens.TVShowsScreen
import com.example.jellyfinandroid.ui.utils.MediaDownloadManager
import com.example.jellyfinandroid.ui.utils.MediaPlayerUtils
import com.example.jellyfinandroid.ui.utils.ShareUtils
import com.example.jellyfinandroid.ui.viewmodel.MainAppViewModel
import com.example.jellyfinandroid.ui.viewmodel.MovieDetailViewModel
import com.example.jellyfinandroid.ui.viewmodel.SeasonEpisodesViewModel
import com.example.jellyfinandroid.ui.viewmodel.ServerConnectionViewModel

@androidx.media3.common.util.UnstableApi
@Composable
fun JellyfinNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.ServerConnection.route,
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        // Authentication flow
        composable(Screen.ServerConnection.route) {
            val viewModel: ServerConnectionViewModel = hiltViewModel()
            val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
            val context = LocalContext.current

            ServerConnectionScreen(
                onConnect = { serverUrl, username, password ->
                    viewModel.connectToServer(serverUrl, username, password)
                },
                onQuickConnect = {
                    navController.navigate(Screen.QuickConnect.route)
                },
                isConnecting = connectionState.isConnecting,
                errorMessage = connectionState.errorMessage,
                savedServerUrl = connectionState.savedServerUrl,
                savedUsername = connectionState.savedUsername,
                rememberLogin = connectionState.rememberLogin,
                hasSavedPassword = connectionState.hasSavedPassword,
                isBiometricAuthAvailable = connectionState.isBiometricAuthAvailable,
                onRememberLoginChange = { viewModel.setRememberLogin(it) },
                onAutoLogin = { viewModel.autoLogin() },
                onBiometricLogin = { 
                    // For biometric auth, we need to convert context to FragmentActivity
                    // This is a simplified approach - in a real app you might want to handle this differently
                    if (context is androidx.fragment.app.FragmentActivity) {
                        viewModel.autoLoginWithBiometric(context)
                    } else {
                        // Fallback to regular auto-login if we can't get the activity
                        viewModel.autoLogin()
                    }
                },
            )
        }

        composable(Screen.QuickConnect.route) {
            val viewModel: ServerConnectionViewModel = hiltViewModel()
            val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

            QuickConnectScreen(
                onConnect = { viewModel.initiateQuickConnect() },
                onCancel = {
                    viewModel.cancelQuickConnect()
                    navController.popBackStack()
                },
                isConnecting = connectionState.isConnecting,
                errorMessage = connectionState.errorMessage,
                serverUrl = connectionState.quickConnectServerUrl,
                code = connectionState.quickConnectCode,
                isPolling = connectionState.isQuickConnectPolling,
                status = connectionState.quickConnectStatus,
                onServerUrlChange = { url -> viewModel.updateQuickConnectServerUrl(url) },
            )
        }

        // Main app flow
        composable(Screen.Home.route) {
            val viewModel: MainAppViewModel = hiltViewModel()
            val lifecycleOwner = LocalLifecycleOwner.current
            val appState by viewModel.appState.collectAsStateWithLifecycle()

            HomeScreen(
                appState = appState,
                currentServer = viewModel.currentServer.collectAsStateWithLifecycle(
                    lifecycle = lifecycleOwner.lifecycle,
                    initialValue = null,
                ).value,
                onRefresh = { viewModel.loadInitialData() },
                onSearch = { query ->
                    viewModel.search(query)
                    navController.navigate(Screen.Search.route)
                },
                onClearSearch = { viewModel.clearSearch() },
                getImageUrl = { item -> viewModel.getImageUrl(item) },
                getBackdropUrl = { item -> viewModel.getBackdropUrl(item) },
                getSeriesImageUrl = { item -> viewModel.getSeriesImageUrl(item) },
                onItemClick = { item ->
                    when (item.type) {
                        org.jellyfin.sdk.model.api.BaseItemKind.MOVIE -> {
                            item.id?.let { movieId ->
                                navController.navigate(Screen.MovieDetail.createRoute(movieId.toString()))
                            }
                        }
                        org.jellyfin.sdk.model.api.BaseItemKind.SERIES -> {
                            item.id?.let { seriesId ->
                                navController.navigate(Screen.TVSeasons.createRoute(seriesId.toString()))
                            }
                        }
                        org.jellyfin.sdk.model.api.BaseItemKind.EPISODE -> {
                            item.id?.let { episodeId ->
                                navController.navigate(Screen.TVEpisodeDetail.createRoute(episodeId.toString()))
                            }
                        }
                        else -> {
                            // For backward compatibility, try to handle movies
                            if (item.type == org.jellyfin.sdk.model.api.BaseItemKind.MOVIE) {
                                item.id?.let { movieId ->
                                    navController.navigate(Screen.MovieDetail.createRoute(movieId.toString()))
                                }
                            }
                        }
                    }
                },
                onSettingsClick = { navController.navigate(Screen.Profile.route) },
            )
        }

        composable(Screen.Library.route) {
            val viewModel = hiltViewModel<MainAppViewModel>()
            val lifecycleOwner = LocalLifecycleOwner.current
            val appState by viewModel.appState.collectAsStateWithLifecycle(
                lifecycle = lifecycleOwner.lifecycle,
                minActiveState = Lifecycle.State.STARTED,
            )

            LibraryScreen(
                libraries = appState.libraries,
                isLoading = appState.isLoading,
                errorMessage = appState.errorMessage,
                onRefresh = { viewModel.loadInitialData() },
                getImageUrl = { item -> viewModel.getImageUrl(item) },
                onLibraryClick = { library ->
                    // Navigate to appropriate screen based on library collection type
                    when (library.collectionType?.toString()?.lowercase()) {
                        "movies" -> navController.navigate(Screen.Movies.route)
                        "tvshows" -> navController.navigate(Screen.TVShows.route)
                        "music" -> navController.navigate(Screen.Music.route)
                        else -> {
                            // For mixed or unknown types, default to Movies screen
                            navController.navigate(Screen.Movies.route)
                        }
                    }
                },
                onSettingsClick = { navController.navigate(Screen.Profile.route) },
            )
        }

        composable(Screen.Movies.route) {
            val viewModel = hiltViewModel<MainAppViewModel>()
            val lifecycleOwner = LocalLifecycleOwner.current

            MoviesScreen(
                onBackClick = { navController.popBackStack() },
                onMovieClick = { movie ->
                    movie.id?.let { movieId ->
                        navController.navigate(Screen.MovieDetail.createRoute(movieId.toString()))
                    }
                },
                viewModel = viewModel,
            )
        }

        composable(Screen.TVShows.route) {
            val viewModel = hiltViewModel<MainAppViewModel>()
            val lifecycleOwner = LocalLifecycleOwner.current

            TVShowsScreen(
                onTVShowClick = { seriesId ->
                    navController.navigate(Screen.TVSeasons.createRoute(seriesId))
                },
                onBackClick = { navController.popBackStack() },
                viewModel = viewModel,
            )
        }

        composable(
            route = Screen.TVSeasons.route,
            arguments = listOf(navArgument(Screen.SERIES_ID_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getString(Screen.SERIES_ID_ARG)
            if (seriesId.isNullOrBlank()) {
                Log.e("NavGraph", "TVSeasons navigation cancelled: seriesId is null or blank")
                return@composable
            }
            val viewModel = hiltViewModel<MainAppViewModel>()
            val lifecycleOwner = LocalLifecycleOwner.current

            LaunchedEffect(seriesId) {
                // Load series data when screen is first shown
                viewModel.loadTVShowDetails(seriesId)
            }

            TVSeasonScreen(
                seriesId = seriesId,
                onBackClick = { navController.popBackStack() },
                getImageUrl = { item -> viewModel.getImageUrl(item) },
                onSeasonClick = { seasonId ->
                    navController.navigate(Screen.TVEpisodes.createRoute(seasonId))
                },
            )
        }

        composable(
            route = Screen.TVEpisodes.route,
            arguments = listOf(navArgument(Screen.SEASON_ID_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val seasonId = backStackEntry.arguments?.getString(Screen.SEASON_ID_ARG)
            if (seasonId.isNullOrBlank()) {
                Log.e("NavGraph", "TVEpisodes navigation cancelled: seasonId is null or blank")
                return@composable
            }
            val viewModel = hiltViewModel<SeasonEpisodesViewModel>()
            val mainViewModel = hiltViewModel<MainAppViewModel>()
            val lifecycleOwner = LocalLifecycleOwner.current

            LaunchedEffect(seasonId) {
                // Load episodes when screen is first shown
                viewModel.loadEpisodes(seasonId)
            }

            TVEpisodesScreen(
                seasonId = seasonId,
                onBackClick = { navController.popBackStack() },
                getImageUrl = { item -> mainViewModel.getImageUrl(item) },
                onEpisodeClick = { episode ->
                    episode.id?.let { episodeId ->
                        // Add episode to main app state for detail screen access
                        mainViewModel.addOrUpdateItem(episode)

                        // Navigate to episode detail screen
                        navController.navigate(Screen.TVEpisodeDetail.createRoute(episodeId.toString()))
                    }
                },
                viewModel = viewModel,
            )
        }

        composable(Screen.Music.route) {
            val viewModel = hiltViewModel<MainAppViewModel>()
            val lifecycleOwner = LocalLifecycleOwner.current

            LaunchedEffect(Unit) {
                // Load music data when screen is first shown
                viewModel.loadMusic()
            }

            MusicScreen(
                onBackClick = { navController.popBackStack() },
                viewModel = viewModel,
            )
        }

        composable(Screen.Search.route) {
            val viewModel = hiltViewModel<MainAppViewModel>()
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
            )
        }

        composable(Screen.Favorites.route) {
            val viewModel = hiltViewModel<MainAppViewModel>()
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
            )
        }

        composable(Screen.Profile.route) {
            val viewModel = hiltViewModel<MainAppViewModel>()
            val lifecycleOwner = LocalLifecycleOwner.current
            val currentServer by viewModel.currentServer.collectAsStateWithLifecycle(
                lifecycle = lifecycleOwner.lifecycle,
                initialValue = null,
            )

            ProfileScreen(
                currentServer = currentServer,
                onLogout = {
                    viewModel.logout()
                    onLogout()
                    navController.navigate(Screen.ServerConnection.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBackClick = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.MovieDetail.route,
            arguments = listOf(navArgument(Screen.MOVIE_ID_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getString(Screen.MOVIE_ID_ARG)
            if (movieId.isNullOrBlank()) {
                Log.e("NavGraph", "MovieDetail navigation cancelled: movieId is null or blank")
                return@composable
            }
            val mainViewModel = hiltViewModel<MainAppViewModel>()
            val detailViewModel = hiltViewModel<MovieDetailViewModel>()

            val lifecycleOwner = LocalLifecycleOwner.current
            val appState by mainViewModel.appState.collectAsStateWithLifecycle(
                lifecycle = lifecycleOwner.lifecycle,
                minActiveState = Lifecycle.State.STARTED,
            )

            // Find the movie from the loaded items or detail view model
            val movie = appState.allItems.find { it.id?.toString() == movieId }
            val detailState by detailViewModel.state.collectAsStateWithLifecycle(lifecycle = lifecycleOwner.lifecycle)

            LaunchedEffect(movieId, movie) {
                if (movie == null && detailState.movie == null && !detailState.isLoading) {
                    detailViewModel.loadMovieDetails(movieId)
                }
            }

            val resolvedMovie = movie ?: detailState.movie

            if (resolvedMovie != null) {
                // Get related items (movies from same genre or similar)
                val relatedItems = appState.allItems.filter { item ->
                    item.id?.toString() != movieId &&
                        item.type == org.jellyfin.sdk.model.api.BaseItemKind.MOVIE &&
                        resolvedMovie.genres?.any { genre -> item.genres?.contains(genre) == true } == true
                }.take(10)

                // Cast preview: show artwork on Cast device when opening detail
                LaunchedEffect(resolvedMovie.id) {
                    mainViewModel.sendCastPreview(resolvedMovie)
                }

                MovieDetailScreen(
                    movie = resolvedMovie,
                    getImageUrl = { item -> mainViewModel.getImageUrl(item) },
                    getBackdropUrl = { item -> mainViewModel.getBackdropUrl(item) },
                    onBackClick = { navController.popBackStack() },
                    onPlayClick = { movieItem ->
                        val streamUrl = mainViewModel.getStreamUrl(movieItem)
                        if (streamUrl != null) {
                            MediaPlayerUtils.playMedia(context = navController.context, streamUrl = streamUrl, item = movieItem)
                            if (BuildConfig.DEBUG) {
                                Log.d("NavGraph", "Playing movie: ${movieItem.name}")
                            }
                        } else {
                            Log.e("NavGraph", "No stream URL available for movie: ${movieItem.name}")
                        }
                    },
                    onFavoriteClick = { movieItem ->
                        mainViewModel.toggleFavorite(movieItem)
                    },
                    onShareClick = { movieItem ->
                        ShareUtils.shareMedia(context = navController.context, item = movieItem)
                    },
                    relatedItems = relatedItems,
                )
            } else if (appState.isLoading || detailState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(detailState.errorMessage ?: appState.errorMessage ?: "Movie not found")
                }
            }
        }

        composable(
            route = Screen.TVEpisodeDetail.route,
            arguments = listOf(navArgument(Screen.EPISODE_ID_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val episodeId = backStackEntry.arguments?.getString(Screen.EPISODE_ID_ARG)
            if (episodeId.isNullOrBlank()) {
                Log.e("NavGraph", "TVEpisodeDetail navigation cancelled: episodeId is null or blank")
                return@composable
            }
            val viewModel = hiltViewModel<MainAppViewModel>()
            val lifecycleOwner = LocalLifecycleOwner.current
            val appState by viewModel.appState.collectAsStateWithLifecycle(
                lifecycle = lifecycleOwner.lifecycle,
                minActiveState = Lifecycle.State.STARTED,
            )

            // Find the episode from the loaded items
            // Try multiple approaches to find the episode
            val episode = appState.allItems.find { item ->
                val itemIdString = item.id?.toString()
                itemIdString == episodeId || itemIdString?.equals(episodeId, ignoreCase = true) == true
            } ?: appState.allItems.find { item ->
                // Fallback: check if this is an episode with matching UUID
                item.type == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE &&
                    item.id?.toString()?.equals(episodeId, ignoreCase = true) == true
            }

            when {
                episode != null -> {
                    // Episode found, show detail screen
                    if (BuildConfig.DEBUG) {
                        Log.d("NavGraph", "TVEpisodeDetail: Found episode ${episode.name} (${episode.id}) in app state")
                    }

                    // Find the series information if available
                    val seriesInfo = episode.seriesId?.let { seriesId ->
                        appState.allItems.find { it.id?.toString() == seriesId.toString() }
                    }

                    TVEpisodeDetailScreen(
                        episode = episode,
                        seriesInfo = seriesInfo,
                        getImageUrl = { item -> viewModel.getImageUrl(item) },
                        getBackdropUrl = { item -> viewModel.getBackdropUrl(item) },
                        onBackClick = { navController.popBackStack() },
                        onPlayClick = { episodeItem ->
                            try {
                                val streamUrl = viewModel.getStreamUrl(episodeItem)
                                if (streamUrl != null) {
                                    MediaPlayerUtils.playMedia(context = navController.context, streamUrl = streamUrl, item = episodeItem)
                                    if (BuildConfig.DEBUG) {
                                        Log.d("NavGraph", "Playing episode: ${episodeItem.name}")
                                    }
                                } else {
                                    Log.e("NavGraph", "No stream URL available for episode: ${episodeItem.name}")
                                }
                            } catch (e: Exception) {
                                Log.e("NavGraph", "Failed to play episode: ${e.message}", e)
                            }
                        },
                        onDownloadClick = { episodeItem ->
                            try {
                                val context = navController.context
                                val downloadUrl = viewModel.getDownloadUrl(episodeItem)

                                if (downloadUrl != null) {
                                    val downloadId = MediaDownloadManager.downloadMedia(
                                        context = context,
                                        item = episodeItem,
                                        streamUrl = downloadUrl,
                                    )

                                    if (downloadId != null) {
                                        if (BuildConfig.DEBUG) {
                                            Log.d("NavGraph", "Started download for episode: ${episodeItem.name} (ID: $downloadId)")
                                        }
                                    } else {
                                        Log.e("NavGraph", "Failed to start download for episode: ${episodeItem.name}")
                                    }
                                } else {
                                    Log.e("NavGraph", "No download URL available for episode: ${episodeItem.name}")
                                }
                            } catch (e: Exception) {
                                Log.e("NavGraph", "Failed to download episode: ${e.message}", e)
                            }
                        },
                        onDeleteClick = { episodeItem ->
                            viewModel.deleteItem(episodeItem) { success, error ->
                                val context = navController.context
                                val message = if (success) {
                                    "Item deleted successfully"
                                } else {
                                    error ?: "Failed to delete item"
                                }
                                android.widget.Toast.makeText(
                                    context,
                                    message,
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        onMarkWatchedClick = { episodeItem ->
                            viewModel.markAsWatched(episodeItem)
                        },
                        onMarkUnwatchedClick = { episodeItem ->
                            viewModel.markAsUnwatched(episodeItem)
                        },
                        onFavoriteClick = { episodeItem ->
                            viewModel.toggleFavorite(episodeItem)
                        },
                    )

                    // Cast preview for episode
                    LaunchedEffect(episode.id) {
                        viewModel.sendCastPreview(episode)
                    }
                }
                appState.isLoading -> {
                    // Still loading, show loading indicator
                    if (BuildConfig.DEBUG) {
                        Log.d("NavGraph", "TVEpisodeDetail: App state is loading, showing loading indicator")
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                !appState.errorMessage.isNullOrBlank() -> {
                    // Error occurred during loading
                    Log.e("NavGraph", "TVEpisodeDetail: Error loading episode: ${appState.errorMessage}")
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "Failed to load episode",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = appState.errorMessage ?: "Unknown error",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    viewModel.clearError()
                                    navController.popBackStack()
                                },
                            ) {
                                Text("Go Back")
                            }
                        }
                    }
                }
                else -> {
                    // Episode not found and not loading - try to load it
                    Log.w("NavGraph", "TVEpisodeDetail: Episode $episodeId not found in app state with ${appState.allItems.size} items, attempting to load")

                    // Debug: Log available episode IDs for troubleshooting
                    val episodeIds = appState.allItems
                        .filter { it.type == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE }
                        .map { "${it.name} (${it.id})" }
                        .take(5) // Limit to first 5 for log readability
                    if (BuildConfig.DEBUG) {
                        Log.d("NavGraph", "TVEpisodeDetail: Available episodes: $episodeIds")
                    }

                    // Load the episode details if we haven't already
                    LaunchedEffect(episodeId) {
                        viewModel.loadEpisodeDetails(episodeId)
                    }

                    // Show loading while we fetch the episode
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
