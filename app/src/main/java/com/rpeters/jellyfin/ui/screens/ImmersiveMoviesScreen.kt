package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.core.util.PerformanceMetricsTracker
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.components.ExpressivePullToRefreshBox
import com.rpeters.jellyfin.ui.components.ExpressiveSimpleEmptyState
import com.rpeters.jellyfin.ui.components.immersive.*
import com.rpeters.jellyfin.ui.components.immersive.rememberImmersivePerformanceConfig
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
    val perfConfig = rememberImmersivePerformanceConfig()
    val listState = rememberLazyListState()

    PerformanceMetricsTracker(
        enabled = com.rpeters.jellyfin.BuildConfig.DEBUG,
        intervalMs = 30000,
    )

    // Use hero height as threshold to avoid flickering within hero
    val topBarVisible = rememberAutoHideTopBarVisible(
        listState = listState,
        nearTopOffsetPx = with(LocalDensity.current) { ImmersiveDimens.HeroHeightPhone.toPx().toInt() },
    )

    // Organize movies into sections for immersive browsing
    val featuredMovies = remember(movies) {
        // ✅ Hero carousel uses Recently Added Movies
        movies.sortedByDescending { it.dateCreated }.take(5)
    }
    val movieSections = remember(movies) { organizeMoviesIntoDiscoverySections(movies) }

    Box(modifier = modifier.fillMaxSize()) {
        ImmersiveScaffold(
            topBarVisible = false,
            topBarTitle = "",
            topBarTranslucent = false,
            floatingActionButton = {
                // ✅ Removed non-functional Filter button, keeping only Search
                FloatingActionGroup(
                    orientation = FabOrientation.Vertical,
                    primaryAction = FabAction(
                        icon = Icons.Default.Search,
                        contentDescription = "Search",
                        onClick = onSearchClick,
                    ),
                    secondaryActions = emptyList(), // ✅ Removed TODO Filter button
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
                if (movies.isEmpty() && !isLoading) {
                    ExpressiveSimpleEmptyState(
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
                            top = 0.dp, // No top padding - hero should be full-bleed behind translucent top bar
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

        // Floating Header Controls (Back and Settings)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Back Button
            Surface(
                onClick = onBackClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp).size(24.dp),
                )
            }

            // Settings Icon
            Surface(
                onClick = { /* TODO: Add settings action */ },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp).size(24.dp),
                )
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
 * Groups movies into Discovery sections.
 */
private fun organizeMoviesIntoDiscoverySections(movies: List<BaseItemDto>): List<MovieSection> {
    if (movies.isEmpty()) return emptyList()

    // Sort by name or other criteria for generic sections
    val sortedMovies = movies.sortedBy { it.name }
    val sections = mutableListOf<MovieSection>()

    // Chunk movies into discovery rows (approx 15 items per row)
    val chunkSize = 15
    val chunks = sortedMovies.chunked(chunkSize)

    if (chunks.isNotEmpty()) {
        sections.add(MovieSection("More Movies", chunks[0]))
    }

    for (i in 1 until chunks.size) {
        if (i > 4) break // Limit to 4 discovery sections as requested
        sections.add(MovieSection("Discover More ${i + 1}", chunks[i]))
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
