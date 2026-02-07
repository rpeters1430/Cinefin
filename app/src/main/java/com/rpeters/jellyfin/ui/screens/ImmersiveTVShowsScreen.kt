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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
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
    isLoading: Boolean,
    onTVShowClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onSearchClick: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
) {
    val listState = rememberLazyListState()

    // Smooth scroll detection for top bar - handled by ImmersiveScaffold overlay
    val topBarVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 200 ||
                listState.firstVisibleItemScrollOffset < (listState.layoutInfo.viewportEndOffset / 4)
        }
    }

    // Organize TV shows into sections for immersive browsing
    val tvShowSections = remember(tvShows) { organizeTVShowsIntoSections(tvShows) }
    val featuredShows = remember(tvShows) { tvShows.take(5) }

    Box(modifier = modifier.fillMaxSize()) {
        ImmersiveScaffold(
            topBarVisible = topBarVisible,
            topBarTitle = stringResource(id = R.string.tv_shows),
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
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = SeriesBlue)
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
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight),
                        contentPadding = PaddingValues(top = 0.dp, bottom = 120.dp),
                    ) {
                        // 1. Hero Carousel (Top 5 Shows)
                        if (featuredShows.isNotEmpty()) {
                            item(key = "tv_shows_hero", contentType = "hero") {
                                val carouselItems = featuredShows.map {
                                    CarouselItem(
                                        id = it.id.toString(),
                                        title = it.name ?: "Unknown",
                                        subtitle = itemSubtitle(it),
                                        imageUrl = getBackdropUrl(it) ?: getImageUrl(it) ?: "",
                                    )
                                }

                                // Apply a subtle parallax translation to fix "scrolling down" visual bug
                                val carouselScrollOffset by remember {
                                    derivedStateOf {
                                        if (listState.firstVisibleItemIndex == 0) {
                                            listState.firstVisibleItemScrollOffset.toFloat() * 0.5f
                                        } else {
                                            0f
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(ImmersiveDimens.HeroHeightPhone)
                                        .graphicsLayer { translationY = carouselScrollOffset }
                                        .clipToBounds(),
                                ) {
                                    ImmersiveHeroCarousel(
                                        items = carouselItems,
                                        onItemClick = { item ->
                                            featuredShows.find { it.id.toString() == item.id }?.id?.let { onTVShowClick(it.toString()) }
                                        },
                                        onPlayClick = { item ->
                                            featuredShows.find { it.id.toString() == item.id }?.id?.let { onTVShowClick(it.toString()) }
                                        },
                                    )
                                }
                            }
                        }

                        // 2. Sections (Recently Added, Favorites, Trending, Genres)
                        items(
                            items = tvShowSections,
                            key = { it.title },
                            contentType = { "tv_show_section" },
                        ) { section ->
                            ImmersiveMediaRow(
                                title = section.title,
                                items = section.items,
                                getImageUrl = getImageUrl,
                                onItemClick = { it.id?.let { id -> onTVShowClick(id.toString()) } },
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
 * Container for TV show sections
 */
private data class TVShowSection(
    val title: String,
    val items: List<BaseItemDto>,
)

/**
 * Groups TV shows into sections for an immersive browse experience.
 */
private fun organizeTVShowsIntoSections(tvShows: List<BaseItemDto>): List<TVShowSection> {
    if (tvShows.isEmpty()) return emptyList()
    val sections = mutableListOf<TVShowSection>()

    // Recently Added
    val recentlyAdded = tvShows.sortedByDescending { it.dateCreated }.take(15)
    if (recentlyAdded.isNotEmpty()) {
        sections.add(TVShowSection("Recently Added", recentlyAdded))
    }

    // Favorites
    val favorites = tvShows.filter { it.userData?.isFavorite == true }.take(15)
    if (favorites.isNotEmpty()) {
        sections.add(TVShowSection("Your Favorites", favorites))
    }

    // Continuing Series (Trending/Active)
    val continuing = tvShows.filter { it.status == "Continuing" }.take(15)
    if (continuing.isNotEmpty()) {
        sections.add(TVShowSection("New Episodes Coming", continuing))
    }

    // Top Rated
    val topRated = tvShows.filter { (it.communityRating ?: 0f) >= 8f }
        .sortedByDescending { it.communityRating }.take(15)
    if (topRated.isNotEmpty()) {
        sections.add(TVShowSection("Top Rated Series", topRated))
    }

    // Action & Adventure
    val action = tvShows.filter {
        it.genres?.any { g ->
            g.contains("Action", true) || g.contains("Adventure", true)
        } == true
    }.take(15)
    if (action.isNotEmpty()) {
        sections.add(TVShowSection("Action & Adventure", action))
    }

    // Comedy
    val comedy = tvShows.filter { it.genres?.any { g -> g.contains("Comedy", true) } == true }.take(15)
    if (comedy.isNotEmpty()) {
        sections.add(TVShowSection("Binge-worthy Comedies", comedy))
    }

    // Drama
    val drama = tvShows.filter { it.genres?.any { g -> g.contains("Drama", true) } == true }.take(15)
    if (drama.isNotEmpty()) {
        sections.add(TVShowSection("Must-Watch Dramas", drama))
    }

    // More TV Shows (Remaining)
    val usedIds = sections.flatMap { it.items.map { s -> s.id } }.toSet()
    val remaining = tvShows.filter { it.id !in usedIds }
    if (remaining.isNotEmpty()) {
        remaining.chunked(15).forEachIndexed { index, chunk ->
            sections.add(TVShowSection(if (index == 0) "More TV Shows" else "Discover More", chunk))
        }
    }

    return sections
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
    val isLoading = appState.isLoadingTVShows

    LaunchedEffect(Unit) {
        if (tvShows.isEmpty()) {
            viewModel.loadLibraryTypeData(LibraryType.TV_SHOWS, forceRefresh = false)
        }
    }

    ImmersiveTVShowsScreen(
        tvShows = tvShows,
        isLoading = isLoading,
        onTVShowClick = onTVShowClick,
        onRefresh = { viewModel.loadLibraryTypeData(LibraryType.TV_SHOWS, forceRefresh = true) },
        onSearchClick = onSearchClick,
        getImageUrl = { viewModel.getImageUrl(it) },
        getBackdropUrl = { viewModel.getBackdropUrl(it) },
        onBackClick = onBackClick,
        modifier = modifier,
    )
}
