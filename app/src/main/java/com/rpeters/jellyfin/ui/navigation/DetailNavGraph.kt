@file:Suppress("DEPRECATION")

package com.rpeters.jellyfin.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
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
import com.rpeters.jellyfin.core.FeatureFlags
import com.rpeters.jellyfin.ui.downloads.DownloadsViewModel
import com.rpeters.jellyfin.ui.screens.AlbumDetailScreen
import com.rpeters.jellyfin.ui.screens.ArtistDetailScreen
import com.rpeters.jellyfin.ui.screens.HomeVideoDetailScreen
import com.rpeters.jellyfin.ui.screens.ImmersiveAlbumDetailScreen
import com.rpeters.jellyfin.ui.screens.ImmersiveHomeVideoDetailScreen
import com.rpeters.jellyfin.ui.screens.ImmersiveMovieDetailScreen
import com.rpeters.jellyfin.ui.screens.ImmersiveTVEpisodeDetailScreen
import com.rpeters.jellyfin.ui.screens.ItemDetailViewModel
import com.rpeters.jellyfin.ui.screens.MovieDetailScreen
import com.rpeters.jellyfin.ui.screens.TVEpisodeDetailScreen
import com.rpeters.jellyfin.ui.utils.MediaPlayerUtils
import com.rpeters.jellyfin.ui.utils.ShareUtils
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.ui.viewmodel.MovieDetailViewModel
import com.rpeters.jellyfin.ui.viewmodel.RemoteConfigViewModel
import com.rpeters.jellyfin.ui.viewmodel.TVEpisodeDetailViewModel
import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.coroutines.CancellationException

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
        val mainViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<MainAppViewModel>()

        // Feature Flag: Check if immersive album detail should be used
        val remoteConfigViewModel: RemoteConfigViewModel = androidx.hilt.navigation.compose.hiltViewModel()
        val useImmersiveUI = remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.ENABLE_IMMERSIVE_UI) &&
            remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.IMMERSIVE_ALBUM_DETAIL)

        if (BuildConfig.DEBUG) {
            SecureLogger.d(
                "DetailNavGraph",
                "AlbumDetail: enable_immersive_ui=${remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.ENABLE_IMMERSIVE_UI)}, " +
                    "immersive_album_detail=${remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.IMMERSIVE_ALBUM_DETAIL)}, " +
                    "using immersive: $useImmersiveUI",
            )
        }

        if (useImmersiveUI) {
            ImmersiveAlbumDetailScreen(
                albumId = albumId,
                onBackClick = { navController.popBackStack() },
                mainViewModel = mainViewModel,
            )
        } else {
            AlbumDetailScreen(
                albumId = albumId,
                onBackClick = { navController.popBackStack() },
                mainViewModel = mainViewModel,
            )
        }
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
        val mainViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<MainAppViewModel>()
        ArtistDetailScreen(
            artistId = artistId,
            artistName = artistName,
            onBackClick = { navController.popBackStack() },
            onAlbumClick = { album ->
                album.id.let { navController.navigate(Screen.AlbumDetail.createRoute(it.toString())) }
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
        val mainViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<MainAppViewModel>()
        val detailViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<ItemDetailViewModel>()
        val downloadsViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<DownloadsViewModel>()
        LaunchedEffect(videoId) {
            try {
                detailViewModel.load(videoId)
            } catch (e: CancellationException) {
                throw e
            }
        }
        val item by detailViewModel.item
        val playbackAnalysis by detailViewModel.playbackAnalysis
        val error by detailViewModel.error

        // Feature Flag: Check if immersive home video detail should be used
        val remoteConfigViewModel: RemoteConfigViewModel = androidx.hilt.navigation.compose.hiltViewModel()
        val useImmersiveUI = remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.ENABLE_IMMERSIVE_UI) &&
            remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.IMMERSIVE_HOME_VIDEO_DETAIL)

        if (BuildConfig.DEBUG) {
            SecureLogger.d(
                "DetailNavGraph",
                "HomeVideoDetail: enable_immersive_ui=${remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.ENABLE_IMMERSIVE_UI)}, " +
                    "immersive_home_video_detail=${remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.IMMERSIVE_HOME_VIDEO_DETAIL)}, " +
                    "using immersive: $useImmersiveUI",
            )
        }

        item?.let { videoItem ->
            if (useImmersiveUI) {
                ImmersiveHomeVideoDetailScreen(
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
                    onDownloadClick = { video ->
                        downloadsViewModel.startDownload(video)
                    },
                    onDeleteClick = { video ->
                        mainViewModel.deleteItem(video) { success, message ->
                            if (success) {
                                navController.popBackStack()
                            } else {
                                // Show error via Toast
                                android.widget.Toast.makeText(
                                    navController.context,
                                    message ?: "Failed to delete item",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    onMarkWatchedClick = { video ->
                        mainViewModel.toggleWatchedStatus(video)
                    },
                    playbackAnalysis = playbackAnalysis,
                )
            } else {
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
                    onDownloadClick = { video ->
                        downloadsViewModel.startDownload(video)
                    },
                    onDeleteClick = { video ->
                        mainViewModel.deleteItem(video) { success, message ->
                            if (success) {
                                navController.popBackStack()
                            } else {
                                // Show error via Toast
                                android.widget.Toast.makeText(
                                    navController.context,
                                    message ?: "Failed to delete item",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    onMarkWatchedClick = { video ->
                        mainViewModel.toggleWatchedStatus(video)
                    },
                    playbackAnalysis = playbackAnalysis,
                )
            }
        } ?: run {
            val errorMessage = error
            if (errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(errorMessage)
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    com.rpeters.jellyfin.ui.components.ExpressiveCircularLoading()
                }
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
        val mainViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<MainAppViewModel>()
        val detailViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<ItemDetailViewModel>()
        val downloadsViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<DownloadsViewModel>()
        LaunchedEffect(itemId) {
            try {
                detailViewModel.load(itemId)
            } catch (e: CancellationException) {
                throw e
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
                onDownloadClick = { video ->
                    downloadsViewModel.startDownload(video)
                },
                onDeleteClick = { video ->
                    mainViewModel.deleteItem(video) { success, message ->
                        if (success) {
                            navController.popBackStack()
                        } else {
                            // Show error via Toast
                            android.widget.Toast.makeText(
                                navController.context,
                                message ?: "Failed to delete item",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
                onMarkWatchedClick = { video ->
                    mainViewModel.toggleWatchedStatus(video)
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
                com.rpeters.jellyfin.ui.components.ExpressiveCircularLoading()
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
        val mainViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<MainAppViewModel>()
        val detailViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<MovieDetailViewModel>()

        val lifecycleOwner = LocalLifecycleOwner.current
        val appState by mainViewModel.appState.collectAsStateWithLifecycle(
            initialValue = mainViewModel.appState.value,
            lifecycleOwner = lifecycleOwner,
            minActiveState = androidx.lifecycle.Lifecycle.State.STARTED,
        )
        val currentServer by mainViewModel.currentServer.collectAsStateWithLifecycle(
            initialValue = null,
            lifecycleOwner = lifecycleOwner,
        )

        val movie = appState.allItems.find { it.id.toString() == movieId }
        val detailState by detailViewModel.state.collectAsStateWithLifecycle(
            initialValue = detailViewModel.state.value,
            lifecycleOwner = lifecycleOwner,
        )

        LaunchedEffect(movieId, movie) {
            if (movie == null && detailState.movie == null && !detailState.isLoading) {
                detailViewModel.loadMovieDetails(movieId)
            }
        }

        val resolvedMovie = movie ?: detailState.movie

        if (resolvedMovie != null) {
            LaunchedEffect(resolvedMovie.id) {
                mainViewModel.sendCastPreview(resolvedMovie)
            }

            // Feature Flag: Check if immersive movie detail should be used
            val remoteConfigViewModel: RemoteConfigViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val useImmersiveUI = remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.ENABLE_IMMERSIVE_UI) &&
                remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.IMMERSIVE_MOVIE_DETAIL)

            if (BuildConfig.DEBUG) {
                SecureLogger.d(
                    "DetailNavGraph",
                    "MovieDetail: enable_immersive_ui=${remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.ENABLE_IMMERSIVE_UI)}, " +
                        "immersive_movie_detail=${remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.IMMERSIVE_MOVIE_DETAIL)}, " +
                        "using immersive: $useImmersiveUI",
                )
            }

            if (useImmersiveUI) {
                ImmersiveMovieDetailScreen(
                    movie = resolvedMovie,
                    relatedItems = detailState.similarMovies,
                    onBackClick = { navController.popBackStack() },
                    onPlayClick = { movieItem, subtitleIndex ->
                        val streamUrl = mainViewModel.getStreamUrl(movieItem)
                        if (streamUrl != null) {
                            MediaPlayerUtils.playMedia(
                                context = navController.context,
                                streamUrl = streamUrl,
                                item = movieItem,
                                subtitleIndex = subtitleIndex,
                            )
                        }
                    },
                    onFavoriteClick = { mainViewModel.toggleFavorite(resolvedMovie) },
                    onShareClick = { ShareUtils.shareMedia(navController.context, resolvedMovie) },
                    onDeleteClick = { movieItem ->
                        mainViewModel.deleteItem(movieItem) { success, message ->
                            val toastMessage = if (success) {
                                "Item deleted successfully"
                            } else {
                                message ?: "Failed to delete item"
                            }
                            android.widget.Toast.makeText(
                                navController.context,
                                toastMessage,
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                            if (success) {
                                navController.popBackStack()
                            }
                        }
                    },
                    onMarkWatchedClick = { mainViewModel.toggleWatchedStatus(resolvedMovie) },
                    onRelatedMovieClick = { relatedMovieId ->
                        navController.navigate(Screen.MovieDetail.createRoute(relatedMovieId))
                    },
                    onPersonClick = { personId, personName ->
                        navController.navigate(Screen.Search.createRoute(personName))
                    },
                    onRefresh = { detailViewModel.refresh() },
                    isRefreshing = detailState.isLoading || detailState.isSimilarMoviesLoading,
                    playbackAnalysis = detailState.playbackAnalysis,
                    getImageUrl = { item -> mainViewModel.getImageUrl(item) },
                    getBackdropUrl = { item -> mainViewModel.getBackdropUrl(item) },
                    getLogoUrl = { item -> mainViewModel.getLogoUrl(item) },
                    getPersonImageUrl = { person -> mainViewModel.getPersonImageUrl(person) },
                    serverUrl = currentServer?.url,
                    onGenerateAiSummary = { detailViewModel.generateAiSummary() },
                    aiSummary = detailState.aiSummary,
                    isLoadingAiSummary = detailState.isLoadingAiSummary,
                )
            } else {
                MovieDetailScreen(
                    movie = resolvedMovie,
                    relatedItems = detailState.similarMovies,
                    onBackClick = { navController.popBackStack() },
                    onPlayClick = { movieItem, subtitleIndex ->
                        val streamUrl = mainViewModel.getStreamUrl(movieItem)
                        if (streamUrl != null) {
                            MediaPlayerUtils.playMedia(
                                context = navController.context,
                                streamUrl = streamUrl,
                                item = movieItem,
                                subtitleIndex = subtitleIndex,
                            )
                        }
                    },
                    onFavoriteClick = { mainViewModel.toggleFavorite(resolvedMovie) },
                    onShareClick = { ShareUtils.shareMedia(navController.context, resolvedMovie) },
                    onDeleteClick = { movieItem ->
                        mainViewModel.deleteItem(movieItem) { success, message ->
                            val toastMessage = if (success) {
                                "Item deleted successfully"
                            } else {
                                message ?: "Failed to delete item"
                            }
                            android.widget.Toast.makeText(
                                navController.context,
                                toastMessage,
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                            if (success) {
                                navController.popBackStack()
                            }
                        }
                    },
                    onRelatedMovieClick = { relatedMovieId ->
                        navController.navigate(Screen.MovieDetail.createRoute(relatedMovieId))
                    },
                    onPersonClick = { personId, personName ->
                        navController.navigate(Screen.Search.createRoute(personName))
                    },
                    onRefresh = { detailViewModel.refresh() },
                    isRefreshing = detailState.isLoading || detailState.isSimilarMoviesLoading,
                    playbackAnalysis = detailState.playbackAnalysis,
                    getImageUrl = { item -> mainViewModel.getImageUrl(item) },
                    getBackdropUrl = { item -> mainViewModel.getBackdropUrl(item) },
                    getLogoUrl = { item -> mainViewModel.getLogoUrl(item) },
                    getPersonImageUrl = { person -> mainViewModel.getPersonImageUrl(person) },
                    serverUrl = currentServer?.url,
                    onGenerateAiSummary = { detailViewModel.generateAiSummary() },
                    aiSummary = detailState.aiSummary,
                    isLoadingAiSummary = detailState.isLoadingAiSummary,
                )
            }
        } else if (detailState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.rpeters.jellyfin.ui.components.ExpressiveCircularLoading()
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

        val mainViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<MainAppViewModel>()
        val viewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<TVEpisodeDetailViewModel>()
        val lifecycleOwner = LocalLifecycleOwner.current
        val appState by mainViewModel.appState.collectAsStateWithLifecycle(
            initialValue = mainViewModel.appState.value,
            lifecycleOwner = lifecycleOwner,
            minActiveState = androidx.lifecycle.Lifecycle.State.STARTED,
        )
        val detailState by viewModel.state.collectAsStateWithLifecycle(
            initialValue = viewModel.state.value,
            lifecycleOwner = lifecycleOwner,
        )

        val episode = appState.allItems.find { it.id.toString() == episodeId }
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
            episode?.let { viewModel.loadEpisodeDetails(it, series) }
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

                // Feature Flag: Check if immersive episode detail should be used

                val remoteConfigViewModel: RemoteConfigViewModel = androidx.hilt.navigation.compose.hiltViewModel()

                val useImmersiveUI = remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.ENABLE_IMMERSIVE_UI) &&

                    remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.IMMERSIVE_TV_EPISODE_DETAIL)

                if (BuildConfig.DEBUG) {
                    SecureLogger.d(

                        "DetailNavGraph",

                        "TVEpisodeDetail: enable_immersive_ui=${remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.ENABLE_IMMERSIVE_UI)}, " +

                            "immersive_tv_episode_detail=${remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.IMMERSIVE_TV_EPISODE_DETAIL)}, " +

                            "using immersive: $useImmersiveUI",

                    )
                }

                if (useImmersiveUI) {
                    ImmersiveTVEpisodeDetailScreen(

                        episode = episode,

                        seriesInfo = detailState.seriesInfo ?: series,

                        seasonEpisodes = detailState.seasonEpisodes,

                        getImageUrl = { mainViewModel.getImageUrl(it) },

                        getBackdropUrl = { mainViewModel.getBackdropUrl(it) },

                        onBackClick = { navController.popBackStack() },

                        onEpisodeClick = { episodeItem ->

                            navController.navigate(Screen.TVEpisodeDetail.createRoute(episodeItem.id.toString())) {
                                popUpTo(Screen.TVEpisodeDetail.route) { inclusive = true }
                            }
                        },

                        onViewSeriesClick = {
                            val seriesId = series?.id ?: detailState.seriesInfo?.id

                            seriesId?.let { id ->

                                navController.navigate(Screen.TVSeasons.createRoute(id.toString())) {
                                    popUpTo(Screen.TVEpisodeDetail.route) { inclusive = true }
                                }
                            }
                        },

                        onPlayClick = { episodeItem, subtitleIndex ->

                            val streamUrl = mainViewModel.getStreamUrl(episodeItem)

                            if (streamUrl != null) {
                                MediaPlayerUtils.playMedia(

                                    context = navController.context,

                                    streamUrl = streamUrl,

                                    item = episodeItem,

                                    subtitleIndex = subtitleIndex,

                                )
                            }
                        },

                        onDownloadClick = { episodeItem ->

                            mainViewModel.getDownloadUrl(episodeItem)?.let { downloadUrl ->

                                val downloadManager = android.app.DownloadManager.Request(android.net.Uri.parse(downloadUrl))

                                downloadManager.setTitle(episodeItem.name ?: "Episode Download")

                                downloadManager.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                                val manager = navController.context.getSystemService(android.app.DownloadManager::class.java)

                                manager.enqueue(downloadManager)
                            }
                        },

                        onFavoriteClick = { mainViewModel.toggleFavorite(it) },

                        onMarkWatchedClick = { mainViewModel.toggleWatchedStatus(it) },

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
                                if (success) {
                                    navController.popBackStack()
                                }
                            }
                        },

                        onPersonClick = { personId, personName ->
                            navController.navigate(Screen.Search.createRoute(personName))
                        },

                        playbackAnalysis = detailState.playbackAnalysis,

                        onGenerateAiSummary = { viewModel.generateAiSummary() },

                        aiSummary = detailState.aiSummary,

                        isLoadingAiSummary = detailState.isLoadingAiSummary,

                    )
                } else {
                    TVEpisodeDetailScreen(

                        episode = episode,

                        seriesInfo = detailState.seriesInfo ?: series,

                        previousEpisode = detailState.previousEpisode,

                        nextEpisode = detailState.nextEpisode,

                        seasonEpisodes = detailState.seasonEpisodes,

                        getImageUrl = { mainViewModel.getImageUrl(it) },

                        getBackdropUrl = { mainViewModel.getBackdropUrl(it) },

                        onBackClick = { navController.popBackStack() },

                        onPreviousEpisodeClick = { previousEpisodeId ->

                            navController.navigate(Screen.TVEpisodeDetail.createRoute(previousEpisodeId)) {
                                popUpTo(Screen.TVEpisodeDetail.route) { inclusive = true }
                            }
                        },

                        onNextEpisodeClick = { nextEpisodeId ->

                            navController.navigate(Screen.TVEpisodeDetail.createRoute(nextEpisodeId)) {
                                popUpTo(Screen.TVEpisodeDetail.route) { inclusive = true }
                            }
                        },

                        onEpisodeClick = { episodeItem ->

                            navController.navigate(Screen.TVEpisodeDetail.createRoute(episodeItem.id.toString())) {
                                popUpTo(Screen.TVEpisodeDetail.route) { inclusive = true }
                            }
                        },

                        onViewSeriesClick = {
                            val seriesId = series?.id ?: detailState.seriesInfo?.id

                            seriesId?.let { id ->

                                navController.navigate(Screen.TVSeasons.createRoute(id.toString())) {
                                    popUpTo(Screen.TVEpisodeDetail.route) { inclusive = true }
                                }
                            }
                        },

                        onPlayClick = { episodeItem, subtitleIndex ->

                            try {
                                val streamUrl = mainViewModel.getStreamUrl(episodeItem)

                                if (streamUrl != null) {
                                    MediaPlayerUtils.playMedia(

                                        context = navController.context,

                                        streamUrl = streamUrl,

                                        item = episodeItem,

                                        subtitleIndex = subtitleIndex,

                                    )
                                } else {
                                    SecureLogger.e(

                                        "NavGraph",

                                        "No stream URL available for episode: ${episodeItem.name}",

                                    )
                                }
                            } catch (e: CancellationException) {
                                throw e
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
                            } catch (e: CancellationException) {
                                throw e
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

                        onGenerateAiSummary = { viewModel.generateAiSummary() },

                        aiSummary = detailState.aiSummary,

                        isLoadingAiSummary = detailState.isLoadingAiSummary,

                    )
                }

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
                    com.rpeters.jellyfin.ui.components.ExpressiveCircularLoading()
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
                        com.rpeters.jellyfin.ui.components.ExpressiveCircularLoading()
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
