@file:Suppress("DEPRECATION")

package com.rpeters.jellyfin.ui.navigation

import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.rpeters.jellyfin.ui.screens.BooksScreen
import com.rpeters.jellyfin.ui.screens.HomeVideosScreen
import com.rpeters.jellyfin.ui.screens.LibraryType
import com.rpeters.jellyfin.ui.screens.MoviesScreen
import com.rpeters.jellyfin.ui.screens.MusicScreen
import com.rpeters.jellyfin.ui.screens.StuffScreen
import com.rpeters.jellyfin.ui.screens.TVEpisodesScreen
import com.rpeters.jellyfin.ui.screens.TVSeasonScreen
import com.rpeters.jellyfin.ui.screens.TVShowsScreen
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.ui.viewmodel.SeasonEpisodesViewModel
import com.rpeters.jellyfin.utils.SecureLogger

/**
 * Media browsing navigation (movies, TV, music, books, home videos).
 */
fun androidx.navigation.NavGraphBuilder.mediaNavGraph(
    navController: NavHostController,
    mainViewModel: MainAppViewModel,
) {
    composable(Screen.Movies.route) {
        val viewModel = mainViewModel
        val appState by viewModel.appState.collectAsStateWithLifecycle(
            lifecycle = LocalLifecycleOwner.current.lifecycle,
            minActiveState = androidx.lifecycle.Lifecycle.State.STARTED,
        )

        var selectedFilter by remember { mutableStateOf(com.rpeters.jellyfin.data.models.MovieFilter.ALL) }
        var selectedSort by remember { mutableStateOf(com.rpeters.jellyfin.data.models.MovieSortOrder.NAME) }
        var viewMode by remember { mutableStateOf(com.rpeters.jellyfin.data.models.MovieViewMode.GRID) }
        val moviesData = viewModel.getLibraryTypeData(LibraryType.MOVIES)
        val isMoviesLoading = appState.isLoadingMovies || appState.isLoading ||
            (appState.libraries.isEmpty() && moviesData.isEmpty())

        LaunchedEffect(appState.libraries, appState.isLoading, appState.isLoadingMovies, moviesData.isEmpty(), appState.errorMessage) {
            SecureLogger.v("NavGraph-Movies", "?? Movies screen state update")
            SecureLogger.v("NavGraph-Movies", "  Libraries count: ${appState.libraries.size}")
            SecureLogger.v("NavGraph-Movies", "  Is loading: ${appState.isLoading}")
            SecureLogger.v(
                "NavGraph-Movies",
                "  Current movies data: ${moviesData.size} items",
            )

            if (appState.errorMessage != null) {
                SecureLogger.v("NavGraph-Movies", "  ?? Error present, skipping auto-load: ${appState.errorMessage}")
                return@LaunchedEffect
            }

            if (appState.libraries.isEmpty() && !appState.isLoading) {
                SecureLogger.v("NavGraph-Movies", "  ?? Loading initial data...")
                viewModel.loadInitialData()
            } else if (
                appState.libraries.isNotEmpty() &&
                moviesData.isEmpty() &&
                !appState.isLoading &&
                !appState.isLoadingMovies
            ) {
                SecureLogger.v(
                    "NavGraph-Movies",
                    "?? Libraries ready (${appState.libraries.size}) - Loading MOVIES data...",
                )
                val availableLibraries =
                    appState.libraries.map { "${it.name}(${it.collectionType})" }
                SecureLogger.v("NavGraph-Movies", "  Available libraries: $availableLibraries")
                viewModel.loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = false)
            }
        }

        MoviesScreen(
            movies = moviesData,
            isLoading = isMoviesLoading,
            isLoadingMore = appState.isLoadingMore,
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
            getImageUrl = { item -> viewModel.getImageUrl(item) },
        )
    }

    composable(Screen.TVShows.route) {
        val viewModel = mainViewModel
        val appState by viewModel.appState.collectAsStateWithLifecycle(
            lifecycle = LocalLifecycleOwner.current.lifecycle,
            minActiveState = androidx.lifecycle.Lifecycle.State.STARTED,
        )
        val tvShowsData = viewModel.getLibraryTypeData(LibraryType.TV_SHOWS)
        val isTvShowsLoading = appState.isLoadingTVShows || appState.isLoading ||
            (appState.libraries.isEmpty() && tvShowsData.isEmpty())

        LaunchedEffect(appState.libraries, appState.isLoading, appState.isLoadingTVShows, tvShowsData.isEmpty(), appState.errorMessage) {
            SecureLogger.v("NavGraph-TVShows", "?? TV shows screen state update")
            SecureLogger.v("NavGraph-TVShows", "  Libraries count: ${appState.libraries.size}")
            SecureLogger.v("NavGraph-TVShows", "  Is loading: ${appState.isLoading}")
            SecureLogger.v(
                "NavGraph-TVShows",
                "  Current TV shows data: ${tvShowsData.size} items",
            )

            if (appState.errorMessage != null) {
                SecureLogger.v("NavGraph-TVShows", "  ?? Error present, skipping auto-load: ${appState.errorMessage}")
                return@LaunchedEffect
            }

            if (appState.libraries.isEmpty() && !appState.isLoading) {
                SecureLogger.v("NavGraph-TVShows", "  ?? Loading initial data...")
                viewModel.loadInitialData()
            } else if (
                appState.libraries.isNotEmpty() &&
                tvShowsData.isEmpty() &&
                !appState.isLoading &&
                !appState.isLoadingTVShows
            ) {
                SecureLogger.v(
                    "NavGraph-TVShows",
                    "?? Libraries ready (${appState.libraries.size}) - Loading TV SHOWS data...",
                )
                val availableLibraries =
                    appState.libraries.map { "${it.name}(${it.collectionType})" }
                SecureLogger.v("NavGraph-TVShows", "  Available libraries: $availableLibraries")
                viewModel.loadLibraryTypeData(LibraryType.TV_SHOWS, forceRefresh = false)
            }
        }

        TVShowsScreen(
            onTVShowClick = { seriesId ->
                try {
                    SecureLogger.v("NavGraph-TVShows", "?? Navigating to TV Seasons: $seriesId")
                    navController.navigate(Screen.TVSeasons.createRoute(seriesId))
                } catch (e: Exception) {
                    SecureLogger.e("NavGraph-TVShows", "Failed to navigate to TV Seasons: $seriesId", e)
                }
            },
            onBackClick = { navController.popBackStack() },
            viewModel = viewModel,
            isLoading = isTvShowsLoading,
        )
    }

    composable(
        route = Screen.TVSeasons.route,
        arguments = listOf(navArgument(Screen.SERIES_ID_ARG) { type = NavType.StringType }),
    ) { backStackEntry ->
        val seriesId = backStackEntry.arguments?.getString(Screen.SERIES_ID_ARG)
        if (seriesId.isNullOrBlank()) {
            SecureLogger.e("NavGraph", "TVSeasons navigation cancelled: seriesId is null or blank")
            return@composable
        }
        val viewModel = androidx.hilt.navigation.compose.hiltViewModel<MainAppViewModel>()
        LocalLifecycleOwner.current

        LaunchedEffect(seriesId) {
            viewModel.loadTVShowDetails(seriesId)
        }

        TVSeasonScreen(
            seriesId = seriesId,
            onBackClick = { navController.popBackStack() },
            getImageUrl = { item -> viewModel.getImageUrl(item) },
            getBackdropUrl = { item -> viewModel.getBackdropUrl(item) },
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
            SecureLogger.e("NavGraph", "TVEpisodes navigation cancelled: seasonId is null or blank")
            return@composable
        }
        val viewModel = androidx.hilt.navigation.compose.hiltViewModel<SeasonEpisodesViewModel>()
        val mainViewModel = androidx.hilt.navigation.compose.hiltViewModel<MainAppViewModel>()
        LocalLifecycleOwner.current

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

    composable(Screen.Music.route) {
        val viewModel = mainViewModel
        val appState by viewModel.appState.collectAsStateWithLifecycle(
            lifecycle = LocalLifecycleOwner.current.lifecycle,
            minActiveState = androidx.lifecycle.Lifecycle.State.STARTED,
        )
        val musicData = viewModel.getLibraryTypeData(LibraryType.MUSIC)

        LaunchedEffect(appState.libraries, appState.isLoading, musicData.isEmpty()) {
            SecureLogger.v("NavGraph-Music", "?? Music screen state update")
            SecureLogger.v("NavGraph-Music", "  Libraries count: ${appState.libraries.size}")
            SecureLogger.v("NavGraph-Music", "  Is loading: ${appState.isLoading}")
            SecureLogger.v(
                "NavGraph-Music",
                "  Current music data: ${musicData.size} items",
            )

            if (appState.libraries.isEmpty() && !appState.isLoading) {
                SecureLogger.v("NavGraph-Music", "  ?? Loading initial data...")
                viewModel.loadInitialData()
            } else if (
                appState.libraries.isNotEmpty() &&
                musicData.isEmpty() &&
                !appState.isLoading
            ) {
                SecureLogger.v(
                    "NavGraph-Music",
                    "?? Libraries ready (${appState.libraries.size}) - Loading MUSIC data...",
                )
                val availableLibraries =
                    appState.libraries.map { "${it.name}(${it.collectionType})" }
                SecureLogger.v("NavGraph-Music", "  Available libraries: $availableLibraries")
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
            minActiveState = androidx.lifecycle.Lifecycle.State.STARTED,
        )

        LaunchedEffect(Unit) {
            SecureLogger.v("NavGraph-HomeVideos", "?? HomeVideos screen entered - Initial state check")
            SecureLogger.v("NavGraph-HomeVideos", "  Libraries count: ${appState.libraries.size}")
            SecureLogger.v("NavGraph-HomeVideos", "  Is loading: ${appState.isLoading}")

            if (appState.libraries.isEmpty() && !appState.isLoading) {
                SecureLogger.v("NavGraph-HomeVideos", "  ?? Loading initial data...")
                viewModel.loadInitialData()
            }
        }

        HomeVideosScreen(
            onBackClick = { navController.popBackStack() },
            viewModel = viewModel,
            onItemClick = { id ->
                val item = appState.itemsByLibrary.values.flatten()
                    .find { it.id?.toString() == id }
                if (item?.type == org.jellyfin.sdk.model.api.BaseItemKind.VIDEO) {
                    navController.navigate(Screen.HomeVideoDetail.createRoute(id))
                } else {
                    navController.navigate(Screen.ItemDetail.createRoute(id))
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
            SecureLogger.e("NavGraph", "Stuff navigation cancelled: libraryId is null or blank")
            return@composable
        }
        val viewModel = androidx.hilt.navigation.compose.hiltViewModel<MainAppViewModel>()
        val lifecycleOwner = LocalLifecycleOwner.current
        val appState by viewModel.appState.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = androidx.lifecycle.Lifecycle.State.STARTED,
        )

        StuffScreen(
            libraryId = libraryId,
            collectionType = collectionType,
            viewModel = viewModel,
            onItemClick = { id ->
                val item = appState.itemsByLibrary.values.flatten()
                    .find { it.id?.toString() == id }
                if (item?.type == org.jellyfin.sdk.model.api.BaseItemKind.VIDEO) {
                    navController.navigate(Screen.HomeVideoDetail.createRoute(id))
                } else {
                    navController.navigate(Screen.ItemDetail.createRoute(id))
                }
            },
        )
    }
}
