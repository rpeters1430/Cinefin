package com.rpeters.jellyfin.ui.navigation

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.ui.screens.AlbumDetailScreen
import com.rpeters.jellyfin.ui.screens.ArtistDetailScreen
import com.rpeters.jellyfin.ui.screens.BooksScreen
import com.rpeters.jellyfin.ui.screens.FavoritesScreen
import com.rpeters.jellyfin.ui.screens.HomeScreen
import com.rpeters.jellyfin.ui.screens.HomeVideosScreen
import com.rpeters.jellyfin.ui.screens.ItemDetailScreen
import com.rpeters.jellyfin.ui.screens.LibraryScreen
import com.rpeters.jellyfin.ui.screens.LibraryType
import com.rpeters.jellyfin.ui.screens.MovieDetailScreen
import com.rpeters.jellyfin.ui.screens.MoviesScreen
import com.rpeters.jellyfin.ui.screens.MusicScreen
import com.rpeters.jellyfin.ui.screens.ProfileScreen
import com.rpeters.jellyfin.ui.screens.QuickConnectScreen
import com.rpeters.jellyfin.ui.screens.SearchScreen
import com.rpeters.jellyfin.ui.screens.ServerConnectionScreen
import com.rpeters.jellyfin.ui.screens.StuffScreen
import com.rpeters.jellyfin.ui.screens.TVEpisodeDetailScreen
import com.rpeters.jellyfin.ui.screens.TVEpisodesScreen
import com.rpeters.jellyfin.ui.screens.TVSeasonScreen
import com.rpeters.jellyfin.ui.screens.TVShowsScreen
import com.rpeters.jellyfin.ui.utils.MediaDownloadManager
import com.rpeters.jellyfin.ui.utils.MediaPlayerUtils
import com.rpeters.jellyfin.ui.utils.ShareUtils
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.ui.viewmodel.MovieDetailViewModel
import com.rpeters.jellyfin.ui.viewmodel.SeasonEpisodesViewModel
import com.rpeters.jellyfin.ui.viewmodel.ServerConnectionViewModel
import kotlinx.coroutines.flow.map
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@androidx.media3.common.util.UnstableApi
@Composable
fun JellyfinNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.ServerConnection.route,
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {},
) {
    val mainViewModel: MainAppViewModel = hiltViewModel()
    val libraries by mainViewModel.appState
        .map { it.libraries }
        .collectAsState(initial = emptyList())

    // Helper function to determine the route for a library
    fun libraryRouteFor(library: BaseItemDto): String? {
        return try {
            when (library.collectionType) {
                CollectionType.MOVIES -> Screen.Movies.route
                CollectionType.TVSHOWS -> Screen.TVShows.route
                CollectionType.MUSIC -> Screen.Music.route
                CollectionType.BOOKS -> Screen.Books.route
                CollectionType.HOMEVIDEOS -> Screen.HomeVideos.route
                else -> library.id?.toString()?.let { id ->
                    val type = library.collectionType?.toString()?.lowercase(Locale.getDefault()) ?: "mixed"
                    Screen.Stuff.createRoute(id, type)
                }
            }
        } catch (e: Exception) {
            Log.e("NavGraph", "Error determining library route for ${library.name}", e)
            null
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        // Authentication flow
        composable(Screen.ServerConnection.route) {
            val viewModel: ServerConnectionViewModel = hiltViewModel()
            val lifecycleOwner = LocalLifecycleOwner.current
            val connectionState by viewModel.connectionState.collectAsStateWithLifecycle(
                lifecycle = lifecycleOwner.lifecycle,
                minActiveState = Lifecycle.State.STARTED,
            )

            // Navigate to Home when successfully connected
            LaunchedEffect(connectionState.isConnected) {
                if (connectionState.isConnected) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.ServerConnection.route) { inclusive = true }
                    }
                }
            }

            ServerConnectionScreen(
                onConnect = { serverUrl, username, password ->
                    viewModel.connectToServer(serverUrl, username, password)
                },
                onQuickConnect = {
                    navController.navigate(Screen.QuickConnect.route)
                },
                connectionState = connectionState,
                savedServerUrl = connectionState.savedServerUrl,
                savedUsername = connectionState.savedUsername,
                rememberLogin = connectionState.rememberLogin,
                hasSavedPassword = connectionState.hasSavedPassword,
                isBiometricAuthAvailable = connectionState.isBiometricAuthAvailable,
                onRememberLoginChange = { viewModel.setRememberLogin(it) },
                onAutoLogin = { viewModel.autoLogin() },
                onBiometricLogin = {
                    // TODO: Implement biometric login
                },
            )
        }

        composable(Screen.QuickConnect.route) {
            val viewModel: ServerConnectionViewModel = hiltViewModel()
            val lifecycleOwner = LocalLifecycleOwner.current
            val connectionState by viewModel.connectionState.collectAsStateWithLifecycle(
                lifecycle = lifecycleOwner.lifecycle,
                minActiveState = Lifecycle.State.STARTED,
            )

            // Navigate to Home when successfully connected
            LaunchedEffect(connectionState.isConnected) {
                if (connectionState.isConnected) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.ServerConnection.route) { inclusive = true }
                    }
                }
            }

            QuickConnectScreen(
                connectionState = connectionState,
                onConnect = { viewModel.initiateQuickConnect() },
                onCancel = {
                    viewModel.cancelQuickConnect()
                    navController.popBackStack()
                },
                onServerUrlChange = { url -> viewModel.updateQuickConnectServerUrl(url) },
            )
        }

        // Main app flow
        composable(Screen.Home.route) {
            val viewModel: MainAppViewModel = hiltViewModel()
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

            // ✅ AUTHENTICATION FIX: Only load data after reaching Home screen (post-authentication)
            LaunchedEffect(Unit) {
                // This runs only once when the Home screen is composed
                // At this point, authentication should be established
                viewModel.loadInitialData()
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
                            if (item.type == org.jellyfin.sdk.model.api.BaseItemKind.MOVIE) {
                                item.id?.let { movieId ->
                                    navController.navigate(Screen.MovieDetail.createRoute(movieId.toString()))
                                }
                            }
                        }
                    }
                },
                onLibraryClick = { library ->
                    try {
                        libraryRouteFor(library)?.let { route ->
                            navController.navigate(route)
                        } ?: run {
                            Log.w("NavGraph", "No route found for library: ${library.name} (${library.collectionType})")
                        }
                    } catch (e: Exception) {
                        Log.e("NavGraph", "Error navigating to library: ${library.name}", e)
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

            // ✅ FIX: Trigger initial data loading when navigating to Library screen
            LaunchedEffect(Unit) {
                if (appState.libraries.isEmpty() && !appState.isLoading) {
                    if (com.rpeters.jellyfin.BuildConfig.DEBUG) {
                        Log.d("NavGraph", "Library screen - triggering initial data load")
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
                            Log.w("NavGraph", "No route found for library: ${library.name} (${library.collectionType})")
                        }
                    } catch (e: Exception) {
                        Log.e("NavGraph", "Error navigating to library: ${library.name}", e)
                    }
                },
                onSettingsClick = { navController.navigate(Screen.Profile.route) },
            )
        }

        // Movies Screen - Always available
        composable(Screen.Movies.route) {
            val viewModel = mainViewModel
            val appState by viewModel.appState.collectAsStateWithLifecycle(
                lifecycle = LocalLifecycleOwner.current.lifecycle,
                minActiveState = Lifecycle.State.STARTED,
            )

            var selectedFilter by remember { mutableStateOf(com.rpeters.jellyfin.data.models.MovieFilter.ALL) }
            var selectedSort by remember { mutableStateOf(com.rpeters.jellyfin.data.models.MovieSortOrder.NAME) }
            var viewMode by remember { mutableStateOf(com.rpeters.jellyfin.data.models.MovieViewMode.GRID) }

            // ✅ FIXED: Consolidated LaunchedEffect to prevent double loading
            LaunchedEffect(Unit) {
                Log.d("NavGraph-Movies", "🎬 Movies screen entered - Initial state check")
                Log.d("NavGraph-Movies", "  Libraries count: ${appState.libraries.size}")
                Log.d("NavGraph-Movies", "  Is loading: ${appState.isLoading}")
                Log.d("NavGraph-Movies", "  Current movies data: ${viewModel.getLibraryTypeData(LibraryType.MOVIES).size} items")

                // Ensure libraries are loaded first
                if (appState.libraries.isEmpty() && !appState.isLoading) {
                    Log.d("NavGraph-Movies", "  📥 Loading initial data...")
                    viewModel.loadInitialData()
                } else if (appState.libraries.isNotEmpty() && viewModel.getLibraryTypeData(LibraryType.MOVIES).isEmpty()) {
                    Log.d("NavGraph-Movies", "🔄 Libraries loaded (${appState.libraries.size}) - Loading MOVIES data...")
                    val availableLibraries = appState.libraries.map { "${it.name}(${it.collectionType})" }
                    Log.d("NavGraph-Movies", "  Available libraries: $availableLibraries")
                    viewModel.loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = false)
                }
            }

            MoviesScreen(
                movies = viewModel.getLibraryTypeData(LibraryType.MOVIES),
                isLoading = appState.isLoadingMovies,
                isLoadingMore = false,
                hasMoreItems = appState.hasMoreMovies,
                selectedFilter = selectedFilter,
                onFilterChange = { selectedFilter = it },
                selectedSort = selectedSort,
                onSortChange = { selectedSort = it },
                viewMode = viewMode,
                onViewModeChange = { viewMode = it },
                onMovieClick = { movie ->
                    movie.id?.let { movieId ->
                        navController.navigate(Screen.MovieDetail.createRoute(movieId.toString()))
                    }
                },
                onRefresh = { viewModel.loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = true) },
                onLoadMore = { viewModel.loadMoreMovies() },
                getImageUrl = { movie -> viewModel.getImageUrl(movie) },
            )
        }

        // TV Shows Screen - Use shared MainAppViewModel instance
        composable(Screen.TVShows.route) {
            val viewModel = mainViewModel
            val appState by viewModel.appState.collectAsStateWithLifecycle(
                lifecycle = LocalLifecycleOwner.current.lifecycle,
                minActiveState = Lifecycle.State.STARTED,
            )

            // ✅ FIXED: Consolidated LaunchedEffect to prevent double loading
            LaunchedEffect(Unit) {
                Log.d("NavGraph-TVShows", "📺 TV Shows screen entered - Initial state check")
                Log.d("NavGraph-TVShows", "  Libraries count: ${appState.libraries.size}")
                Log.d("NavGraph-TVShows", "  Is loading: ${appState.isLoading}")
                Log.d("NavGraph-TVShows", "  Current TV shows data: ${viewModel.getLibraryTypeData(LibraryType.TV_SHOWS).size} items")

                // Ensure libraries are loaded first
                if (appState.libraries.isEmpty() && !appState.isLoading) {
                    Log.d("NavGraph-TVShows", "  📥 Loading initial data...")
                    viewModel.loadInitialData()
                } else if (appState.libraries.isNotEmpty() && viewModel.getLibraryTypeData(LibraryType.TV_SHOWS).isEmpty()) {
                    Log.d("NavGraph-TVShows", "🔄 Libraries loaded (${appState.libraries.size}) - Loading TV_SHOWS data...")
                    val availableLibraries = appState.libraries.map { "${it.name}(${it.collectionType})" }
                    Log.d("NavGraph-TVShows", "  Available libraries: $availableLibraries")
                    viewModel.loadLibraryTypeData(LibraryType.TV_SHOWS, forceRefresh = false)
                }
            }

            TVShowsScreen(
                onTVShowClick = { seriesId ->
                    try {
                        Log.d("NavGraph-TVShows", "🎯 Navigating to TV Seasons: $seriesId")
                        navController.navigate(Screen.TVSeasons.createRoute(seriesId))
                    } catch (e: Exception) {
                        Log.e("NavGraph-TVShows", "Failed to navigate to TV Seasons: $seriesId", e)
                    }
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
                viewModel.loadEpisodes(seasonId)
            }

            TVEpisodesScreen(
                seasonId = seasonId,
                onBackClick = { navController.popBackStack() },
                getImageUrl = { item -> mainViewModel.getImageUrl(item) },
                onEpisodeClick = { episode ->
                    episode.id?.let { episodeId ->
                        mainViewModel.addOrUpdateItem(episode)
                        navController.navigate(Screen.TVEpisodeDetail.createRoute(episodeId.toString()))
                    }
                },
                viewModel = viewModel,
            )
        }

        // Music Screen - Always available
        composable(Screen.Music.route) {
            val viewModel = mainViewModel
            val appState by viewModel.appState.collectAsStateWithLifecycle(
                lifecycle = LocalLifecycleOwner.current.lifecycle,
                minActiveState = Lifecycle.State.STARTED,
            )

            // ✅ FIXED: Single LaunchedEffect to prevent double loading
            LaunchedEffect(Unit) {
                Log.d("NavGraph-Music", "🎵 Music screen entered - Initial state check")
                Log.d("NavGraph-Music", "  Libraries count: ${appState.libraries.size}")
                Log.d("NavGraph-Music", "  Is loading: ${appState.isLoading}")
                Log.d("NavGraph-Music", "  Current music data: ${viewModel.getLibraryTypeData(LibraryType.MUSIC).size} items")

                // Ensure libraries are loaded first
                if (appState.libraries.isEmpty() && !appState.isLoading) {
                    Log.d("NavGraph-Music", "  📥 Loading initial data...")
                    viewModel.loadInitialData()
                } else if (appState.libraries.isNotEmpty() && viewModel.getLibraryTypeData(LibraryType.MUSIC).isEmpty()) {
                    Log.d("NavGraph-Music", "🔄 Libraries loaded (${appState.libraries.size}) - Loading MUSIC data...")
                    val availableLibraries = appState.libraries.map { "${it.name}(${it.collectionType})" }
                    Log.d("NavGraph-Music", "  Available libraries: $availableLibraries")
                    viewModel.loadLibraryTypeData(LibraryType.MUSIC, forceRefresh = false)
                }
            }

            MusicScreen(
                onBackClick = { navController.popBackStack() },
                viewModel = viewModel,
                onItemClick = { item ->
                    item.id?.let { id ->
                        when (item.type) {
                            org.jellyfin.sdk.model.api.BaseItemKind.MUSIC_ALBUM -> {
                                navController.navigate(Screen.AlbumDetail.createRoute(id.toString()))
                            }
                            org.jellyfin.sdk.model.api.BaseItemKind.MUSIC_ARTIST -> {
                                // Navigate to artist albums list
                                navController.navigate(Screen.ArtistDetail.createRoute(id.toString()))
                            }
                            else -> {
                                navController.navigate(Screen.ItemDetail.createRoute(id.toString()))
                            }
                        }
                    }
                },
            )
        }

        composable(Screen.HomeVideos.route) {
            val viewModel = mainViewModel
            val appState by viewModel.appState.collectAsStateWithLifecycle(
                lifecycle = LocalLifecycleOwner.current.lifecycle,
                minActiveState = Lifecycle.State.STARTED,
            )

            // ✅ FIXED: Single LaunchedEffect for consistent loading
            LaunchedEffect(Unit) {
                Log.d("NavGraph-HomeVideos", "🏠 HomeVideos screen entered - Initial state check")
                Log.d("NavGraph-HomeVideos", "  Libraries count: ${appState.libraries.size}")
                Log.d("NavGraph-HomeVideos", "  Is loading: ${appState.isLoading}")

                // Ensure libraries are loaded first
                if (appState.libraries.isEmpty() && !appState.isLoading) {
                    Log.d("NavGraph-HomeVideos", "  📥 Loading initial data...")
                    viewModel.loadInitialData()
                }
            }

            // ✅ FIXED: Add onItemClick functionality for HomeVideos
            HomeVideosScreen(
                onBackClick = { navController.popBackStack() },
                viewModel = viewModel,
                onItemClick = { item ->
                    item.id?.let { id ->
                        when (item.type) {
                            org.jellyfin.sdk.model.api.BaseItemKind.VIDEO -> {
                                navController.navigate(Screen.ItemDetail.createRoute(id.toString()))
                            }
                            org.jellyfin.sdk.model.api.BaseItemKind.PHOTO -> {
                                // Could add photo viewer navigation here
                                navController.navigate(Screen.ItemDetail.createRoute(id.toString()))
                            }
                            else -> {
                                navController.navigate(Screen.ItemDetail.createRoute(id.toString()))
                            }
                        }
                    }
                },
            )
        }

        composable(Screen.Books.route) {
            BooksScreen(
                onBackClick = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.AlbumDetail.route,
            arguments = listOf(
                navArgument(Screen.ALBUM_ID_ARG) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getString(Screen.ALBUM_ID_ARG) ?: return@composable
            AlbumDetailScreen(
                albumId = albumId,
                onBackClick = { navController.popBackStack() },
                mainViewModel = mainViewModel,
            )
        }

        composable(
            route = Screen.ArtistDetail.route,
            arguments = listOf(
                navArgument(Screen.ARTIST_ID_ARG) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val artistId = backStackEntry.arguments?.getString(Screen.ARTIST_ID_ARG) ?: return@composable
            val artistName: String? = null
            ArtistDetailScreen(
                artistId = artistId,
                artistName = artistName,
                onBackClick = { navController.popBackStack() },
                onAlbumClick = { album ->
                    album.id?.let { navController.navigate(Screen.AlbumDetail.createRoute(it.toString())) }
                },
                mainViewModel = mainViewModel,
            )
        }

        composable(
            route = Screen.ItemDetail.route,
            arguments = listOf(
                navArgument(Screen.ITEM_ID_ARG) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString(Screen.ITEM_ID_ARG) ?: return@composable
            ItemDetailScreen(
                itemId = itemId,
                onBackClick = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.Stuff.route,
            arguments = listOf(
                navArgument(Screen.LIBRARY_ID_ARG) { type = NavType.StringType },
                navArgument(Screen.COLLECTION_TYPE_ARG) {
                    type = NavType.StringType
                    nullable = true
                },
            ),
        ) { backStackEntry ->
            val libraryId = backStackEntry.arguments?.getString(Screen.LIBRARY_ID_ARG)
            val collectionType = backStackEntry.arguments?.getString(Screen.COLLECTION_TYPE_ARG)
            if (libraryId.isNullOrBlank()) {
                Log.e("NavGraph", "Stuff navigation cancelled: libraryId is null or blank")
                return@composable
            }
            val viewModel = hiltViewModel<MainAppViewModel>()

            StuffScreen(
                libraryId = libraryId,
                collectionType = collectionType,
                viewModel = viewModel,
                onItemClick = { item ->
                    item.id?.let { id ->
                        navController.navigate(Screen.ItemDetail.createRoute(id.toString()))
                    }
                },
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

            val movie = appState.allItems.find { it.id?.toString() == movieId }
            val detailState by detailViewModel.state.collectAsStateWithLifecycle(lifecycle = lifecycleOwner.lifecycle)

            LaunchedEffect(movieId, movie) {
                if (movie == null && detailState.movie == null && !detailState.isLoading) {
                    detailViewModel.loadMovieDetails(movieId)
                }
            }

            val resolvedMovie = movie ?: detailState.movie

            if (resolvedMovie != null) {
                val relatedItems = appState.allItems.filter { item ->
                    item.id?.toString() != movieId &&
                        item.type == org.jellyfin.sdk.model.api.BaseItemKind.MOVIE &&
                        resolvedMovie.genres?.any { genre -> item.genres?.contains(genre) == true } == true
                }.take(10)

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

            val episode = appState.allItems.find { item ->
                val itemIdString = item.id?.toString()
                itemIdString == episodeId || itemIdString?.equals(episodeId, ignoreCase = true) == true
            } ?: appState.allItems.find { item ->
                item.type == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE &&
                    item.id?.toString()?.equals(episodeId, ignoreCase = true) == true
            }

            when {
                episode != null -> {
                    if (BuildConfig.DEBUG) {
                        Log.d("NavGraph", "TVEpisodeDetail: Found episode ${episode.name} (${episode.id}) in app state")
                    }

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

                    LaunchedEffect(episode.id) {
                        viewModel.sendCastPreview(episode)
                    }
                }
                appState.isLoading -> {
                    if (BuildConfig.DEBUG) {
                        Log.d("NavGraph", "TVEpisodeDetail: App state is loading, showing loading indicator")
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                !appState.errorMessage.isNullOrBlank() -> {
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
                    // Episode not found - try to load it
                    LaunchedEffect(episodeId) {
                        Log.d("NavGraph", "TVEpisodeDetail: Episode $episodeId not found in app state with ${appState.allItems.size} items, attempting to load it")
                        viewModel.loadEpisodeDetails(episodeId)
                    }

                    // Show loading while fetching
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading episode...",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
