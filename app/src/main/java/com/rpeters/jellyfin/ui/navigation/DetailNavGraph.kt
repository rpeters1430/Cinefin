@file:Suppress("DEPRECATION")

package com.rpeters.jellyfin.ui.navigation

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.screens.AlbumDetailScreen
import com.rpeters.jellyfin.ui.screens.ArtistDetailScreen
import com.rpeters.jellyfin.ui.screens.HomeVideoDetailScreen
import com.rpeters.jellyfin.ui.screens.ItemDetailViewModel
import com.rpeters.jellyfin.ui.screens.MovieDetailScreen
import com.rpeters.jellyfin.ui.screens.TVEpisodeDetailScreen
import com.rpeters.jellyfin.ui.utils.MediaPlayerUtils
import com.rpeters.jellyfin.ui.utils.ShareUtils
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.ui.viewmodel.MovieDetailViewModel
import com.rpeters.jellyfin.ui.viewmodel.TVEpisodeDetailViewModel
import com.rpeters.jellyfin.utils.SecureLogger

/**
 * Detail and playback-adjacent destinations.
 */
@OptInAppExperimentalApis
fun androidx.navigation.NavGraphBuilder.detailNavGraph(
    navController: NavHostController,
) {
    composable(
        route = Screen.AlbumDetail.route,
        arguments = listOf(
            navArgument(Screen.ALBUM_ID_ARG) { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val albumId =
            backStackEntry.arguments?.getString(Screen.ALBUM_ID_ARG) ?: return@composable
        val mainViewModel = androidx.hilt.navigation.compose.hiltViewModel<MainAppViewModel>()
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
        val artistId =
            backStackEntry.arguments?.getString(Screen.ARTIST_ID_ARG) ?: return@composable
        val artistName: String? = null
        val mainViewModel = androidx.hilt.navigation.compose.hiltViewModel<MainAppViewModel>()
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
        route = Screen.HomeVideoDetail.route,
        arguments = listOf(navArgument(Screen.VIDEO_ID_ARG) { type = NavType.StringType }),
    ) { backStackEntry ->
        val videoId = backStackEntry.arguments?.getString(Screen.VIDEO_ID_ARG)
        if (videoId.isNullOrBlank()) {
            SecureLogger.e("NavGraph", "HomeVideoDetail navigation cancelled: videoId is null or blank")
            return@composable
        }
        val mainViewModel = androidx.hilt.navigation.compose.hiltViewModel<MainAppViewModel>()
        val detailViewModel = androidx.hilt.navigation.compose.hiltViewModel<ItemDetailViewModel>()
        LaunchedEffect(videoId) {
            try {
                detailViewModel.load(videoId)
            } catch (e: Exception) {
                SecureLogger.e("NavGraph", "Error loading video details: ${e.message}", e)
            }
        }
        val item by detailViewModel.item
        val playbackAnalysis by detailViewModel.playbackAnalysis
        val error by detailViewModel.error
        item?.let { videoItem ->
            HomeVideoDetailScreen(
                item = videoItem,
                getImageUrl = { mainViewModel.getImageUrl(it) },
                getBackdropUrl = { mainViewModel.getBackdropUrl(it) },
                onBackClick = { navController.popBackStack() },
                onPlayClick = { video ->
                    val streamUrl = mainViewModel.getStreamUrl(video)
                    if (streamUrl != null) {
                        MediaPlayerUtils.playMedia(
                            context = navController.context,
                            streamUrl = streamUrl,
                            item = video,
                        )
                    }
                },
                onFavoriteClick = { video -> mainViewModel.toggleFavorite(video) },
                onShareClick = { video ->
                    ShareUtils.shareMedia(context = navController.context, item = video)
                },
                playbackAnalysis = playbackAnalysis,
            )
        } ?: if (error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    composable(
        route = Screen.ItemDetail.route,
        arguments = listOf(
            navArgument(Screen.ITEM_ID_ARG) { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val itemId =
            backStackEntry.arguments?.getString(Screen.ITEM_ID_ARG) ?: return@composable
        val mainViewModel = androidx.hilt.navigation.compose.hiltViewModel<MainAppViewModel>()
        val detailViewModel = androidx.hilt.navigation.compose.hiltViewModel<ItemDetailViewModel>()
        LaunchedEffect(itemId) {
            try {
                detailViewModel.load(itemId)
            } catch (e: Exception) {
                SecureLogger.e("NavGraph", "Error loading item details: ${e.message}", e)
            }
        }
        val item by detailViewModel.item
        val playbackAnalysis by detailViewModel.playbackAnalysis
        val error by detailViewModel.error
        item?.let { videoItem ->
            HomeVideoDetailScreen(
                item = videoItem,
                getImageUrl = { mainViewModel.getImageUrl(it) },
                getBackdropUrl = { mainViewModel.getBackdropUrl(it) },
                onBackClick = { navController.popBackStack() },
                onPlayClick = { video ->
                    val streamUrl = mainViewModel.getStreamUrl(video)
                    if (streamUrl != null) {
                        MediaPlayerUtils.playMedia(
                            context = navController.context,
                            streamUrl = streamUrl,
                            item = video,
                        )
                    }
                },
                onFavoriteClick = { video -> mainViewModel.toggleFavorite(video) },
                onShareClick = { video ->
                    ShareUtils.shareMedia(context = navController.context, item = video)
                },
                playbackAnalysis = playbackAnalysis,
            )
        } ?: if (error != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = error ?: stringResource(R.string.unknown_error),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }

    composable(
        route = Screen.MovieDetail.route,
        arguments = listOf(navArgument(Screen.MOVIE_ID_ARG) { type = NavType.StringType }),
    ) { backStackEntry ->
        val movieId = backStackEntry.arguments?.getString(Screen.MOVIE_ID_ARG)
        if (movieId.isNullOrBlank()) {
            SecureLogger.e("NavGraph", "MovieDetail navigation cancelled: movieId is null or blank")
            return@composable
        }
        val mainViewModel = androidx.hilt.navigation.compose.hiltViewModel<MainAppViewModel>()
        val detailViewModel = androidx.hilt.navigation.compose.hiltViewModel<MovieDetailViewModel>()

        val lifecycleOwner = LocalLifecycleOwner.current
        val appState by mainViewModel.appState.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = androidx.lifecycle.Lifecycle.State.STARTED,
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
                relatedItems = relatedItems,
                onBackClick = { navController.popBackStack() },
                onPlayClick = {
                    val streamUrl = mainViewModel.getStreamUrl(resolvedMovie)
                    if (streamUrl != null) {
                        MediaPlayerUtils.playMedia(
                            context = navController.context,
                            streamUrl = streamUrl,
                            item = resolvedMovie,
                        )
                    }
                },
                onFavoriteClick = { mainViewModel.toggleFavorite(resolvedMovie) },
                onShareClick = { ShareUtils.shareMedia(navController.context, resolvedMovie) },
                playbackAnalysis = detailState.playbackAnalysis,
                getImageUrl = { item -> mainViewModel.getImageUrl(item) },
                getBackdropUrl = { item -> mainViewModel.getBackdropUrl(item) },
            )
        } else if (detailState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Movie not found or failed to load")
                Button(onClick = { navController.popBackStack() }) {
                    Text("Go Back")
                }
            }
        }
    }

    composable(
        route = Screen.TVEpisodeDetail.route,
        arguments = listOf(navArgument(Screen.EPISODE_ID_ARG) { type = NavType.StringType }),
    ) { backStackEntry ->
        val episodeId = backStackEntry.arguments?.getString(Screen.EPISODE_ID_ARG)
        if (episodeId.isNullOrBlank()) {
            SecureLogger.e("NavGraph", "TVEpisodeDetail navigation cancelled: episodeId is null or blank")
            return@composable
        }

        val mainViewModel = androidx.hilt.navigation.compose.hiltViewModel<MainAppViewModel>()
        val viewModel = androidx.hilt.navigation.compose.hiltViewModel<TVEpisodeDetailViewModel>()
        val lifecycleOwner = LocalLifecycleOwner.current
        val appState by mainViewModel.appState.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = androidx.lifecycle.Lifecycle.State.STARTED,
        )
        val detailState by viewModel.state.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
        )

        val episode = appState.allItems.find { it.id?.toString() == episodeId }
        val series = episode?.seriesId?.let { seriesId ->
            appState.allItems.find { it.id == seriesId }
        }
        val season = episode?.seasonId?.let { seasonId ->
            appState.allItems.find { it.id == seasonId }
        }

        LaunchedEffect(episodeId, episode) {
            if (episode == null) {
                mainViewModel.loadEpisodeDetails(episodeId)
            }
            episode?.let { viewModel.loadEpisodeAnalysis(it) }
        }

        when {
            episode != null -> {
                val episodeType = episode.type
                if (episodeType != org.jellyfin.sdk.model.api.BaseItemKind.EPISODE) {
                    SecureLogger.w(
                        "NavGraph",
                        "TVEpisodeDetail: Navigated with non-episode item type: $episodeType",
                    )
                }

                TVEpisodeDetailScreen(
                    episode = episode,
                    seriesInfo = series,
                    getImageUrl = { mainViewModel.getImageUrl(it) },
                    getBackdropUrl = { mainViewModel.getBackdropUrl(it) },
                    onBackClick = { navController.popBackStack() },
                    onPlayClick = { episodeItem ->
                        try {
                            val streamUrl = mainViewModel.getStreamUrl(episodeItem)
                            if (streamUrl != null) {
                                MediaPlayerUtils.playMedia(
                                    context = navController.context,
                                    streamUrl = streamUrl,
                                    item = episodeItem,
                                )
                            } else {
                                SecureLogger.e(
                                    "NavGraph",
                                    "No stream URL available for episode: ${episodeItem.name}",
                                )
                            }
                        } catch (e: Exception) {
                            SecureLogger.e("NavGraph", "Failed to play episode: ${e.message}", e)
                        }
                    },
                    onDownloadClick = { episodeItem ->
                        try {
                            mainViewModel.getDownloadUrl(episodeItem)?.let { downloadUrl ->
                                val context = navController.context
                                val downloadManager = android.app.DownloadManager.Request(
                                    android.net.Uri.parse(downloadUrl),
                                )
                                downloadManager.setTitle(episodeItem.name ?: "Episode Download")
                                downloadManager.setDescription("Downloading episode")
                                downloadManager.setNotificationVisibility(
                                    android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                                )

                                val manager =
                                    context.getSystemService(android.app.DownloadManager::class.java)
                                manager.enqueue(downloadManager)

                                android.widget.Toast.makeText(
                                    context,
                                    "Download started: ${episodeItem.name}",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            } ?: run {
                                SecureLogger.e(
                                    "NavGraph",
                                    "Failed to start download for episode: ${episodeItem.name}",
                                )
                            }
                        } catch (e: Exception) {
                            SecureLogger.e("NavGraph", "Failed to download episode: ${e.message}", e)
                        }
                    },
                    onDeleteClick = { episodeItem ->
                        mainViewModel.deleteItem(episodeItem) { success, error ->
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
                        mainViewModel.markAsWatched(episodeItem)
                    },
                    onMarkUnwatchedClick = { episodeItem ->
                        mainViewModel.markAsUnwatched(episodeItem)
                    },
                    onFavoriteClick = { episodeItem ->
                        mainViewModel.toggleFavorite(episodeItem)
                    },
                    playbackAnalysis = detailState.playbackAnalysis,
                )

                LaunchedEffect(episode.id) {
                    mainViewModel.sendCastPreview(episode)
                }
            }

            appState.isLoading -> {
                if (BuildConfig.DEBUG) {
                    SecureLogger.v(
                        "NavGraph",
                        "TVEpisodeDetail: App state is loading, showing loading indicator",
                    )
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            !appState.errorMessage.isNullOrBlank() -> {
                SecureLogger.e(
                    "NavGraph",
                    "TVEpisodeDetail: Error loading episode: ${appState.errorMessage}",
                )
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
                            text = appState.errorMessage ?: stringResource(R.string.unknown_error),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                navController.popBackStack()
                            },
                        ) {
                            Text("Go Back")
                        }
                    }
                }
            }

            else -> {
                SecureLogger.v(
                    "NavGraph",
                    "TVEpisodeDetail: Episode $episodeId not found in app state with ${appState.allItems.size} items",
                )

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
