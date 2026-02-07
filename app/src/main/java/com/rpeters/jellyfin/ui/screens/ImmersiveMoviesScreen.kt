package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.components.ExpressivePullToRefreshBox
import com.rpeters.jellyfin.ui.components.immersive.*
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.ui.theme.MovieRed
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Immersive movies browse screen with Netflix/Disney+ inspired design.
 * Features:
 * - Full-screen hero carousel showcasing top movies
 * - Content rows grouped by metadata (Recently Added, Favorites, Top Rated, Genres)
 * - Auto-hiding navigation bars
 * - Large media cards (280dp)
 * - Tighter spacing (16dp)
 * - Floating action buttons for Filter and Search
 */
@OptInAppExperimentalApis
@Composable
fun ImmersiveMoviesScreen(
    movies: List<BaseItemDto>,
    isLoading: Boolean,
    onMovieClick: (BaseItemDto) -> Unit,
    onRefresh: () -> Unit,
    onSearchClick: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val topBarVisible = rememberAutoHideTopBarVisible(listState = listState)

    // Organize movies into sections for immersive browsing
    val movieSections = remember(movies) { organizeMoviesIntoSections(movies) }
    val featuredMovies = remember(movies) { movies.take(5) }

    Box(modifier = modifier.fillMaxSize()) {
        ImmersiveScaffold(
            topBarVisible = topBarVisible,
            topBarTitle = "Movies",
            topBarNavigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            topBarActions = {
                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = MovieRed)
                    }
                }
            },
            topBarTranslucent = true,
            floatingActionButton = {
                FloatingActionGroup(
                    orientation = FabOrientation.Vertical,
                    primaryAction = FabAction(
                        icon = Icons.Default.Search,
                        contentDescription = "Search",
                        onClick = onSearchClick,
                    ),
                    secondaryActions = listOf(
                        FabAction(
                            icon = Icons.Default.Tune,
                            contentDescription = "Filter",
                            onClick = { /* TODO: Show filter dialog */ },
                        ),
                    ),
                )
            },
        ) { paddingValues ->
            ExpressivePullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(), // No paddingValues.top jump
                indicatorColor = MovieRed,
                useWavyIndicator = true,
            ) {
                val topBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
                if (movies.isEmpty() && !isLoading) {
                    MoviesEmptyState(
                        icon = Icons.Default.Movie,
                        title = "No movies found",
                        subtitle = "Try adding some movies to your library",
                        iconTint = MovieRed,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight),
                        contentPadding = PaddingValues(
                            top = topBarPadding,
                            bottom = 120.dp,
                        ),
                    ) {
                        // 1. Hero Carousel (Top 5 Movies)
                        if (featuredMovies.isNotEmpty()) {
                            item(key = "movies_hero", contentType = "hero") {
                                val carouselItems = featuredMovies.map {
                                    CarouselItem(
                                        id = it.id.toString(),
                                        title = it.name ?: "Unknown",
                                        subtitle = it.productionYear?.toString() ?: "",
                                        imageUrl = getBackdropUrl(it) ?: getImageUrl(it) ?: "",
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(ImmersiveDimens.HeroHeightPhone)
                                        .clipToBounds(),
                                ) {
                                    ImmersiveHeroCarousel(
                                        items = carouselItems,
                                        onItemClick = { item ->
                                            featuredMovies.find { it.id.toString() == item.id }?.let(onMovieClick)
                                        },
                                        onPlayClick = { item ->
                                            featuredMovies.find { it.id.toString() == item.id }?.let(onMovieClick)
                                        },
                                    )
                                }
                            }
                        }

                        // 2. Sections (Recently Added, Top Rated, Genres)
                        items(
                            items = movieSections,
                            key = { it.title },
                            contentType = { "movie_section" },
                        ) { section ->
                            ImmersiveMediaRow(
                                title = section.title,
                                items = section.items,
                                getImageUrl = getImageUrl,
                                onItemClick = onMovieClick,
                                size = ImmersiveCardSize.MEDIUM,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Container for movie sections
 */
private data class MovieSection(
    val title: String,
    val items: List<BaseItemDto>,
)

/**
 * Groups movies into sections for an immersive browse experience.
 */
private fun organizeMoviesIntoSections(movies: List<BaseItemDto>): List<MovieSection> {
    if (movies.isEmpty()) return emptyList()
    val sections = mutableListOf<MovieSection>()

    // Recently Added
    val recentlyAdded = movies.sortedByDescending { it.dateCreated }.take(15)
    if (recentlyAdded.isNotEmpty()) {
        sections.add(MovieSection("Recently Added", recentlyAdded))
    }

    // Favorites
    val favorites = movies.filter { it.userData?.isFavorite == true }.take(15)
    if (favorites.isNotEmpty()) {
        sections.add(MovieSection("Your Favorites", favorites))
    }

    // Top Rated
    val topRated = movies.filter { (it.communityRating ?: 0f) >= 8f }
        .sortedByDescending { it.communityRating }.take(15)
    if (topRated.isNotEmpty()) {
        sections.add(MovieSection("Top Rated Movies", topRated))
    }

    // Action Movies
    val action = movies.filter { it.genres?.any { g -> g.contains("Action", true) } == true }.take(15)
    if (action.isNotEmpty()) {
        sections.add(MovieSection("Action Packed", action))
    }

    // Comedy Movies
    val comedy = movies.filter { it.genres?.any { g -> g.contains("Comedy", true) } == true }.take(15)
    if (comedy.isNotEmpty()) {
        sections.add(MovieSection("Laugh Out Loud", comedy))
    }

    // Sci-Fi & Fantasy
    val sciFi = movies.filter {
        it.genres?.any { g ->
            g.contains("Science Fiction", true) || g.contains("Sci-Fi", true) || g.contains("Fantasy", true)
        } == true
    }.take(15)
    if (sciFi.isNotEmpty()) {
        sections.add(MovieSection("Sci-Fi & Fantasy", sciFi))
    }

    // More Movies (Remaining)
    val usedIds = sections.flatMap { it.items.map { m -> m.id } }.toSet()
    val remaining = movies.filter { it.id !in usedIds }
    if (remaining.isNotEmpty()) {
        remaining.chunked(15).forEachIndexed { index, chunk ->
            sections.add(MovieSection(if (index == 0) "More Movies" else "Even More Movies", chunk))
        }
    }

    return sections
}

/**
 * ViewModel-connected wrapper for ImmersiveMoviesScreen.
 */
@OptInAppExperimentalApis
@Composable
fun ImmersiveMoviesScreenContainer(
    onMovieClick: (BaseItemDto) -> Unit,
    onSearchClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
) {
    val appState by viewModel.appState.collectAsState()
    val movies = viewModel.getLibraryTypeData(LibraryType.MOVIES)
    val isLoading = appState.isLoadingMovies

    LaunchedEffect(Unit) {
        if (movies.isEmpty()) {
            viewModel.loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = false)
        }
    }

    ImmersiveMoviesScreen(
        movies = movies,
        isLoading = isLoading,
        onMovieClick = onMovieClick,
        onRefresh = { viewModel.loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = true) },
        onSearchClick = onSearchClick,
        getImageUrl = { viewModel.getImageUrl(it) },
        getBackdropUrl = { viewModel.getBackdropUrl(it) },
        onBackClick = onBackClick,
        modifier = modifier,
    )
}
