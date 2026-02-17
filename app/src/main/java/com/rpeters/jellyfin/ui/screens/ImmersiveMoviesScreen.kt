package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.util.PerformanceMetricsTracker
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.components.ExpressivePullToRefreshBox
import com.rpeters.jellyfin.ui.components.ExpressiveSimpleEmptyState
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
    PerformanceMetricsTracker(
        enabled = com.rpeters.jellyfin.BuildConfig.DEBUG,
        intervalMs = 30000,
    )

    val featuredMovies = remember(movies) {
        // ✅ Hero carousel uses Recently Added Movies
        movies.sortedByDescending { it.dateCreated }.take(5)
    }

    var selectedSort by remember { mutableStateOf(MovieSortOption.ALPHABETICAL) }
    var showSortMenu by remember { mutableStateOf(false) }

    val sortedMovies = remember(movies, selectedSort) {
        when (selectedSort) {
            MovieSortOption.ALPHABETICAL -> movies.sortedBy { (it.sortName ?: it.name).orEmpty().lowercase() }
            MovieSortOption.RECENTLY_ADDED -> movies.sortedByDescending { it.dateCreated }
            MovieSortOption.YEAR_NEWEST -> movies.sortedByDescending { it.productionYear ?: 0 }
        }
    }

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
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp), // ✅ Adaptive columns: more columns on wider screens
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 0.dp,
                            start = ImmersiveDimens.SpacingRowTight,
                            end = ImmersiveDimens.SpacingRowTight,
                            bottom = 120.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight),
                        horizontalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight),
                    ) {
                        if (featuredMovies.isNotEmpty()) {
                            item(
                                key = "movies_hero",
                                span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                            ) {
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
                                        .offset(x = -ImmersiveDimens.SpacingRowTight) // ✅ Pull to left edge
                                        .width(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp) // ✅ Full width
                                        .height(ImmersiveDimens.HeroHeightPhone + 60.dp) // ✅ Increase height to go all the way up
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

                        items(
                            items = sortedMovies,
                            key = { it.id.toString() },
                        ) { movie ->
                            ImmersiveMediaCard(
                                title = movie.name ?: "Unknown",
                                subtitle = movie.productionYear?.toString() ?: "",
                                imageUrl = getImageUrl(movie) ?: "",
                                onCardClick = { onMovieClick(movie) },
                                onPlayClick = { onMovieClick(movie) },
                                cardSize = ImmersiveCardSize.SMALL,
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

            Box {
                Surface(
                    onClick = { showSortMenu = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = stringResource(id = R.string.sort),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp).size(24.dp),
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    MovieSortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(id = option.labelRes)) },
                            onClick = {
                                selectedSort = option
                                showSortMenu = false
                            },
                        )
                    }
                }
            }
        }
    }
}

private enum class MovieSortOption(val labelRes: Int) {
    ALPHABETICAL(R.string.sort_title_asc),
    RECENTLY_ADDED(R.string.sort_date_added_desc),
    YEAR_NEWEST(R.string.sort_year_desc),
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
