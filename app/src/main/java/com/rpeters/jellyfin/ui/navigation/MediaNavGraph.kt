@file:Suppress("DEPRECATION")

package com.rpeters.jellyfin.ui.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.rpeters.jellyfin.ui.screens.AudioQueueScreen
import com.rpeters.jellyfin.ui.screens.BooksScreen
import com.rpeters.jellyfin.ui.screens.HomeVideosScreen
import com.rpeters.jellyfin.ui.screens.LibraryType
import com.rpeters.jellyfin.ui.screens.LibraryTypeScreen
import com.rpeters.jellyfin.ui.screens.MoviesScreenContainer
import com.rpeters.jellyfin.ui.screens.MusicScreen
import com.rpeters.jellyfin.ui.screens.NowPlayingScreen
import com.rpeters.jellyfin.ui.screens.TVEpisodesScreen
import com.rpeters.jellyfin.ui.screens.TVSeasonScreen
import com.rpeters.jellyfin.ui.screens.TVShowsScreen
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.ui.viewmodel.SeasonEpisodesViewModel
import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Media browsing navigation (movies, TV, music, books, home videos).
 */
fun androidx.navigation.NavGraphBuilder.mediaNavGraph(
    navController: NavHostController,
    mainViewModel: MainAppViewModel,
) {
    composable(Screen.Movies.route) {
        MoviesScreenContainer(
            onMovieClick = { item ->
                item.id.let { movieId ->
                    navController.navigate(Screen.MovieDetail.createRoute(movieId.toString()))
                }
            },
            viewModel = mainViewModel,
        )
    }

    composable(Screen.TVShows.route) {
        TVShowsScreen(
            onTVShowClick = { seriesId ->
                try {
                    SecureLogger.v("NavGraph-TVShows", "Navigating to TV Seasons: $seriesId")
                    navController.navigate(Screen.TVSeasons.createRoute(seriesId))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    SecureLogger.e("NavGraph-TVShows", "Failed to navigate to TV Seasons: $seriesId", e)
                }
            },
            viewModel = mainViewModel,
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
        val viewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<MainAppViewModel>()
        LocalLifecycleOwner.current

        LaunchedEffect(seriesId) {
            viewModel.loadTVShowDetails(seriesId)
        }

        TVSeasonScreen(
            seriesId = seriesId,
            onBackClick = { navController.popBackStack() },
            getImageUrl = { item -> viewModel.getImageUrl(item) },
            getBackdropUrl = { item -> viewModel.getBackdropUrl(item) },
            getLogoUrl = { item -> viewModel.getLogoUrl(item) },
            onSeasonClick = { seasonId ->
                navController.navigate(Screen.TVEpisodes.createRoute(seasonId))
            },
            onSeriesClick = { targetSeriesId ->
                navController.navigate(Screen.TVSeasons.createRoute(targetSeriesId)) {
                    launchSingleTop = true
                }
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
        val viewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<SeasonEpisodesViewModel>()
        val mainViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<MainAppViewModel>()
        LocalLifecycleOwner.current

        LaunchedEffect(seasonId) {
            viewModel.loadEpisodes(seasonId)
        }

        TVEpisodesScreen(
            seasonId = seasonId,
            onBackClick = { navController.popBackStack() },
            getImageUrl = { item -> mainViewModel.getImageUrl(item) },
            onEpisodeClick = { episode ->
                episode.id.let { episodeId ->
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
                item.id.let { id ->
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

    composable(Screen.NowPlaying.route) {
        NowPlayingScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenQueue = {
                navController.navigate(Screen.AudioQueue.route)
            },
        )
    }

    composable(Screen.AudioQueue.route) {
        AudioQueueScreen(
            onNavigateBack = { navController.popBackStack() },
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
                    .find { it.id.toString() == id }
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
        if (libraryId.isNullOrBlank()) {
            SecureLogger.e("NavGraph", "Stuff navigation cancelled: libraryId is null or blank")
            return@composable
        }
        val viewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<MainAppViewModel>()
        val lifecycleOwner = LocalLifecycleOwner.current
        val appState by viewModel.appState.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = androidx.lifecycle.Lifecycle.State.STARTED,
        )

        LibraryTypeScreen(
            libraryType = LibraryType.STUFF,
            onItemClick = { item ->
                item.id.toString().let { id ->
                    when (item.type) {
                        BaseItemKind.VIDEO -> navController.navigate(Screen.HomeVideoDetail.createRoute(id))
                        BaseItemKind.SERIES -> navController.navigate(Screen.TVSeasons.createRoute(id))
                        else -> navController.navigate(Screen.ItemDetail.createRoute(id))
                    }
                }
            },
            viewModel = viewModel,
        )
    }
}
