package com.example.jellyfinandroid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.jellyfinandroid.ui.screens.FavoritesScreen
import com.example.jellyfinandroid.ui.screens.HomeScreen
import com.example.jellyfinandroid.ui.screens.LibraryScreen
import com.example.jellyfinandroid.ui.screens.MovieDetailScreen
import com.example.jellyfinandroid.ui.screens.TVEpisodeDetailScreen
import com.example.jellyfinandroid.ui.screens.MoviesScreen
import com.example.jellyfinandroid.ui.screens.MusicScreen
import com.example.jellyfinandroid.ui.screens.ProfileScreen
import com.example.jellyfinandroid.ui.screens.QuickConnectScreen
import com.example.jellyfinandroid.ui.screens.ServerConnectionScreen
import com.example.jellyfinandroid.ui.screens.SearchScreen
import com.example.jellyfinandroid.ui.screens.TVEpisodesScreen
import com.example.jellyfinandroid.ui.screens.TVSeasonScreen
import com.example.jellyfinandroid.ui.screens.TVShowsScreen
import com.example.jellyfinandroid.ui.viewmodel.MainAppViewModel
import com.example.jellyfinandroid.ui.viewmodel.SeasonEpisodesViewModel
import com.example.jellyfinandroid.ui.viewmodel.ServerConnectionViewModel

@Composable
fun JellyfinNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.ServerConnection.route,
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // Authentication flow
        composable(Screen.ServerConnection.route) {
            val viewModel: ServerConnectionViewModel = hiltViewModel()
            val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
            
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
                onRememberLoginChange = { viewModel.setRememberLogin(it) },
                onAutoLogin = { viewModel.autoLogin() }
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
                onServerUrlChange = { url -> viewModel.updateQuickConnectServerUrl(url) }
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
                    initialValue = null
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
                onSettingsClick = { navController.navigate(Screen.Profile.route) }
            )
        }
        
        composable(Screen.Library.route) {
            val viewModel = hiltViewModel<MainAppViewModel>()
            val lifecycleOwner = LocalLifecycleOwner.current
            val appState by viewModel.appState.collectAsStateWithLifecycle(
                lifecycle = lifecycleOwner.lifecycle,
                minActiveState = Lifecycle.State.STARTED
            )
            
            LaunchedEffect(Unit) {
                viewModel.loadInitialData()
            }
            
            LibraryScreen(
                libraries = appState.libraries,
                isLoading = appState.isLoading,
                errorMessage = appState.errorMessage,
                onRefresh = { viewModel.loadInitialData() },
                getImageUrl = { item -> viewModel.getImageUrl(item) },
                onSettingsClick = { navController.navigate(Screen.Profile.route) }
            )
        }
        
        composable(Screen.Movies.route) {
            val viewModel = hiltViewModel<MainAppViewModel>()
            val lifecycleOwner = LocalLifecycleOwner.current
            
            LaunchedEffect(Unit) {
                // Load initial movies data if needed
            }
            
            MoviesScreen(
                onBackClick = { navController.popBackStack() },
                onMovieClick = { movie -> 
                    movie.id?.let { movieId ->
                        navController.navigate(Screen.MovieDetail.createRoute(movieId.toString()))
                    }
                },
                viewModel = viewModel
            )
        }
        
        composable(Screen.TVShows.route) {
            val viewModel = hiltViewModel<MainAppViewModel>()
            val lifecycleOwner = LocalLifecycleOwner.current
            
            LaunchedEffect(Unit) {
                // Load initial TV shows data if needed
            }
            
            TVShowsScreen(
                onTVShowClick = { seriesId ->
                    navController.navigate(Screen.TVSeasons.createRoute(seriesId))
                },
                onBackClick = { navController.popBackStack() },
                viewModel = viewModel
            )
        }
        
        composable(
            route = Screen.TVSeasons.route,
            arguments = listOf(navArgument(Screen.SERIES_ID_ARG) { type = NavType.StringType })
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getString(Screen.SERIES_ID_ARG) ?: return@composable
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
                }
            )
        }
        
        composable(
            route = Screen.TVEpisodes.route,
            arguments = listOf(navArgument(Screen.SEASON_ID_ARG) { type = NavType.StringType })
        ) { backStackEntry ->
            val seasonId = backStackEntry.arguments?.getString(Screen.SEASON_ID_ARG) ?: return@composable
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
                        navController.navigate(Screen.TVEpisodeDetail.createRoute(episodeId.toString()))
                    }
                },
                viewModel = viewModel
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
                viewModel = viewModel
            )
        }
        
        composable(Screen.Search.route) {
            val viewModel = hiltViewModel<MainAppViewModel>()
            val lifecycleOwner = LocalLifecycleOwner.current
            val appState by viewModel.appState.collectAsStateWithLifecycle(
                lifecycle = lifecycleOwner.lifecycle,
                minActiveState = Lifecycle.State.STARTED
            )
            
            SearchScreen(
                appState = appState,
                onSearch = { query -> viewModel.search(query) },
                onClearSearch = { viewModel.clearSearch() },
                getImageUrl = { item -> viewModel.getImageUrl(item) },
                onBackClick = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Favorites.route) {
            val viewModel = hiltViewModel<MainAppViewModel>()
            val lifecycleOwner = LocalLifecycleOwner.current
            val appState by viewModel.appState.collectAsStateWithLifecycle(
                lifecycle = lifecycleOwner.lifecycle,
                minActiveState = Lifecycle.State.STARTED
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
                onBackClick = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Profile.route) {
            val viewModel = hiltViewModel<MainAppViewModel>()
            val lifecycleOwner = LocalLifecycleOwner.current
            val currentServer by viewModel.currentServer.collectAsStateWithLifecycle(
                lifecycle = lifecycleOwner.lifecycle,
                initialValue = null
            )
            
            ProfileScreen(
                currentServer = currentServer,
                onLogout = {
                    viewModel.logout()
                    onLogout()
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                    navController.navigate(Screen.ServerConnection.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.MovieDetail.route,
            arguments = listOf(navArgument(Screen.MOVIE_ID_ARG) { type = NavType.StringType })
        ) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getString(Screen.MOVIE_ID_ARG) ?: return@composable
            val viewModel = hiltViewModel<MainAppViewModel>()
            val lifecycleOwner = LocalLifecycleOwner.current
            val appState by viewModel.appState.collectAsStateWithLifecycle(
                lifecycle = lifecycleOwner.lifecycle,
                minActiveState = Lifecycle.State.STARTED
            )
            
            // Find the movie from the loaded items
            val movie = appState.allItems.find { it.id.toString() == movieId }
            
            if (movie != null) {
                // Get related items (movies from same genre or similar)
                val relatedItems = appState.allItems.filter { item ->
                    item.id.toString() != movieId && 
                    item.type == org.jellyfin.sdk.model.api.BaseItemKind.MOVIE &&
                    movie.genres?.any { genre -> item.genres?.contains(genre) == true } == true
                }.take(10)
                
                MovieDetailScreen(
                    movie = movie,
                    getImageUrl = { item -> viewModel.getImageUrl(item) },
                    getBackdropUrl = { item -> viewModel.getBackdropUrl(item) },
                    onBackClick = { navController.popBackStack() },
                    onPlayClick = { /* TODO: Implement play functionality */ },
                    onFavoriteClick = { /* TODO: Implement favorite toggle */ },
                    relatedItems = relatedItems
                )
            } else {
                // Movie not found, show error or navigate back
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }
        
        composable(
            route = Screen.TVEpisodeDetail.route,
            arguments = listOf(navArgument(Screen.EPISODE_ID_ARG) { type = NavType.StringType })
        ) { backStackEntry ->
            val episodeId = backStackEntry.arguments?.getString(Screen.EPISODE_ID_ARG) ?: return@composable
            val viewModel = hiltViewModel<MainAppViewModel>()
            val lifecycleOwner = LocalLifecycleOwner.current
            val appState by viewModel.appState.collectAsStateWithLifecycle(
                lifecycle = lifecycleOwner.lifecycle,
                minActiveState = Lifecycle.State.STARTED
            )
            
            // Find the episode from the loaded items
            val episode = appState.allItems.find { it.id.toString() == episodeId }
            
            if (episode != null) {
                // Find the series information if available
                val seriesInfo = episode.seriesId?.let { seriesId ->
                    appState.allItems.find { it.id.toString() == seriesId.toString() }
                }
                
                TVEpisodeDetailScreen(
                    episode = episode,
                    seriesInfo = seriesInfo,
                    getImageUrl = { item -> viewModel.getImageUrl(item) },
                    getBackdropUrl = { item -> viewModel.getBackdropUrl(item) },
                    onBackClick = { navController.popBackStack() },
                    onPlayClick = { /* TODO: Implement play functionality */ },
                    onDownloadClick = { /* TODO: Implement download functionality */ },
                    onDeleteClick = { /* TODO: Implement delete functionality */ },
                    onMarkWatchedClick = { /* TODO: Implement mark watched functionality */ },
                    onMarkUnwatchedClick = { /* TODO: Implement mark unwatched functionality */ },
                    onFavoriteClick = { /* TODO: Implement favorite toggle functionality */ }
                )
            } else {
                // Episode not found, show error or navigate back
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }
    }
}
