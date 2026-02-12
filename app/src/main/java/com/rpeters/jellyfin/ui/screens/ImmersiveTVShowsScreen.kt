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
import com.rpeters.jellyfin.ui.theme.SeriesBlue
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Immersive TV shows browse screen with Netflix/Disney+ inspired design.
 * Features:
 * - Full-screen hero carousel showcasing featured series
 * - Content rows grouped by metadata (Recently Added, Favorites, Genres, Trending)
 * - Auto-hiding navigation bars
 * - Large media cards (280dp)
 * - Tighter spacing (16dp)
 * - Floating action buttons for Filter and Search
 */
@OptInAppExperimentalApis
@Composable
fun ImmersiveTVShowsScreen(
    tvShows: List<BaseItemDto>,
    recentEpisodes: List<BaseItemDto>, // Added recent episodes
    isLoading: Boolean,
    onTVShowClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onSearchClick: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?, // Added series image getter
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
) {
    PerformanceMetricsTracker(
        enabled = com.rpeters.jellyfin.BuildConfig.DEBUG,
        intervalMs = 30000,
    )

    // Featured shows carousel - prefer recently added episodes, fallback to TV library data.
    val featuredShows = remember(tvShows, recentEpisodes) {
        val fromRecentEpisodes = recentEpisodes
            .filter { it.seriesId != null || it.seriesName != null || it.name != null }
            .distinctBy { it.seriesId ?: it.seriesName ?: it.name }

        if (fromRecentEpisodes.isNotEmpty()) {
            fromRecentEpisodes.take(5)
        } else {
            tvShows
                .sortedByDescending { it.dateCreated }
                .take(5)
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
                indicatorColor = SeriesBlue,
                useWavyIndicator = true,
            ) {
                if (tvShows.isEmpty() && !isLoading) {
                    ExpressiveSimpleEmptyState(
                        icon = Icons.Default.Tv,
                        title = stringResource(id = R.string.no_tv_shows_found),
                        subtitle = stringResource(id = R.string.adjust_tv_shows_filters_hint),
                        iconTint = SeriesBlue,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(ImmersiveDimens.CardWidthSmall),
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
                        if (featuredShows.isNotEmpty()) {
                            item(
                                key = "tv_shows_hero",
                                span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                            ) {
                                val carouselItems = featuredShows.mapNotNull { item ->
                                    CarouselItem(
                                        id = (item.seriesId ?: item.id).toString(),
                                        title = item.seriesName ?: item.name ?: "Unknown",
                                        subtitle = if (item.seriesId != null) "New Episode Added" else (item.productionYear?.toString() ?: ""),
                                        imageUrl = getBackdropUrl(item) ?: getSeriesImageUrl(item) ?: getImageUrl(item) ?: "",
                                    )
                                }

                                if (carouselItems.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(ImmersiveDimens.HeroHeightPhone)
                                            .clipToBounds(),
                                    ) {
                                        ImmersiveHeroCarousel(
                                            items = carouselItems,
                                            onItemClick = { item -> onTVShowClick(item.id) },
                                            onPlayClick = { item -> onTVShowClick(item.id) },
                                        )
                                    }
                                }
                            }
                        }

                        items(
                            items = tvShows,
                            key = { it.id.toString() },
                        ) { tvShow ->
                            ImmersiveMediaCard(
                                title = tvShow.name ?: "Unknown",
                                subtitle = tvShow.productionYear?.toString() ?: "",
                                imageUrl = getImageUrl(tvShow) ?: "",
                                onCardClick = { onTVShowClick(tvShow.id.toString()) },
                                onPlayClick = { onTVShowClick(tvShow.id.toString()) },
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
 * ViewModel-connected wrapper for ImmersiveTVShowsScreen.
 */
@OptInAppExperimentalApis
@Composable
fun ImmersiveTVShowsScreenContainer(
    onTVShowClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
) {
    val appState by viewModel.appState.collectAsState()
    val tvShows = viewModel.getLibraryTypeData(LibraryType.TV_SHOWS)
    val recentEpisodes = appState.recentlyAddedByTypes[org.jellyfin.sdk.model.api.BaseItemKind.EPISODE.name] ?: emptyList()
    val isLoading = appState.isLoadingTVShows

    LaunchedEffect(Unit) {
        if (tvShows.isEmpty()) {
            viewModel.loadLibraryTypeData(LibraryType.TV_SHOWS, forceRefresh = false)
        }
    }

    ImmersiveTVShowsScreen(
        tvShows = tvShows,
        recentEpisodes = recentEpisodes,
        isLoading = isLoading,
        onTVShowClick = onTVShowClick,
        onRefresh = { viewModel.loadLibraryTypeData(LibraryType.TV_SHOWS, forceRefresh = true) },
        onSearchClick = onSearchClick,
        getImageUrl = { viewModel.getImageUrl(it) },
        getBackdropUrl = { viewModel.getBackdropUrl(it) },
        getSeriesImageUrl = { viewModel.getSeriesImageUrl(it) },
        onBackClick = onBackClick,
        modifier = modifier,
    )
}
