package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.components.ExpressiveCompactCard
import com.rpeters.jellyfin.ui.components.ExpressiveDotsLoading
import com.rpeters.jellyfin.ui.components.ExpressiveFloatingToolbar
import com.rpeters.jellyfin.ui.components.ExpressiveFullScreenLoading
import com.rpeters.jellyfin.ui.components.ExpressiveMediaCard
import com.rpeters.jellyfin.ui.components.ExpressiveMediaCarousel
import com.rpeters.jellyfin.ui.components.MediaType
import com.rpeters.jellyfin.ui.components.ToolbarAction
import com.rpeters.jellyfin.ui.theme.MotionTokens
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.utils.getItemKey
import com.rpeters.jellyfin.utils.getRatingAsDouble
import com.rpeters.jellyfin.utils.hasHighRating
import org.jellyfin.sdk.model.api.BaseItemDto

enum class TVShowFilter(val displayNameResId: Int) {
    ALL(R.string.filter_all_shows),
    FAVORITES(R.string.filter_favorites_shows),
    CONTINUING(R.string.filter_continuing),
    ENDED(R.string.filter_ended),
    RECENT(R.string.filter_recent_shows),
    UNWATCHED(R.string.filter_unwatched_shows),
    IN_PROGRESS(R.string.filter_in_progress),
    HIGH_RATED(R.string.filter_high_rated_shows),
    ;

    companion object {
        fun getAllFilters() = entries
    }
}

enum class TVShowSortOrder(val displayNameResId: Int) {
    TITLE_ASC(R.string.sort_title_asc_shows),
    TITLE_DESC(R.string.sort_title_desc_shows),
    YEAR_DESC(R.string.sort_year_desc_shows),
    YEAR_ASC(R.string.sort_year_asc_shows),
    RATING_DESC(R.string.sort_rating_desc_shows),
    RATING_ASC(R.string.sort_rating_asc_shows),
    DATE_ADDED_DESC(R.string.sort_date_added_desc_shows),
    DATE_ADDED_ASC(R.string.sort_date_added_asc_shows),
    LAST_PLAYED_DESC(R.string.sort_last_played_desc),
    EPISODE_COUNT_DESC(R.string.sort_episode_count_desc),
    EPISODE_COUNT_ASC(R.string.sort_episode_count_asc),
    ;

    companion object {
        fun getDefault() = TITLE_ASC
        fun getAllSortOrders() = entries
    }
}

enum class TVShowViewMode {
    GRID,
    LIST,
    CAROUSEL,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TVShowsScreen(
    onTVShowClick: (String) -> Unit = {},
    onBackClick: () -> Unit = {},
    viewModel: MainAppViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val appState by viewModel.appState.collectAsState()
    var selectedFilter by remember { mutableStateOf(TVShowFilter.ALL) }
    var sortOrder by remember { mutableStateOf(TVShowSortOrder.getDefault()) }
    var viewMode by remember { mutableStateOf(TVShowViewMode.GRID) }
    var showSortMenu by remember { mutableStateOf(false) }

    // TV show data is loaded via NavGraph effect

    // Get items provided by the unified library loader (Series only)
    val tvShowItems = remember(appState.itemsByLibrary, appState.libraries) {
        viewModel.getLibraryTypeData(LibraryType.TV_SHOWS)
    }

    // Apply filtering and sorting with proper keys to prevent unnecessary recomputation
    val filteredAndSortedTVShows = remember(tvShowItems, selectedFilter, sortOrder) {
        if (tvShowItems.isEmpty()) return@remember emptyList()

        val filtered = when (selectedFilter) {
            TVShowFilter.ALL -> tvShowItems
            TVShowFilter.FAVORITES -> tvShowItems.filter { it.userData?.isFavorite == true }
            TVShowFilter.CONTINUING -> tvShowItems.filter { it.status == "Continuing" }
            TVShowFilter.ENDED -> tvShowItems.filter { it.status == "Ended" }
            TVShowFilter.RECENT -> tvShowItems.filter {
                ((it.productionYear as? Number)?.toInt() ?: 0) >= 2020
            }
            TVShowFilter.UNWATCHED -> tvShowItems.filter {
                it.userData?.played != true
            }
            TVShowFilter.IN_PROGRESS -> tvShowItems.filter {
                (it.userData?.playedPercentage ?: 0.0) > 0.0 &&
                    (it.userData?.playedPercentage ?: 0.0) < 100.0
            }
            TVShowFilter.HIGH_RATED -> tvShowItems.filter { it.hasHighRating() }
        }

        when (sortOrder) {
            TVShowSortOrder.TITLE_ASC -> filtered.sortedBy { it.sortName ?: it.name }
            TVShowSortOrder.TITLE_DESC -> filtered.sortedByDescending { it.sortName ?: it.name }
            TVShowSortOrder.YEAR_DESC -> filtered.sortedByDescending {
                (it.productionYear as? Number)?.toInt() ?: 0
            }
            TVShowSortOrder.YEAR_ASC -> filtered.sortedBy {
                (it.productionYear as? Number)?.toInt() ?: 0
            }
            TVShowSortOrder.RATING_DESC -> filtered.sortedByDescending { it.getRatingAsDouble() }
            TVShowSortOrder.RATING_ASC -> filtered.sortedBy { it.getRatingAsDouble() }
            TVShowSortOrder.DATE_ADDED_DESC -> filtered.sortedByDescending { it.dateCreated }
            TVShowSortOrder.DATE_ADDED_ASC -> filtered.sortedBy { it.dateCreated }
            TVShowSortOrder.LAST_PLAYED_DESC -> filtered.sortedByDescending {
                it.userData?.lastPlayedDate
            }
            TVShowSortOrder.EPISODE_COUNT_DESC -> filtered.sortedByDescending {
                it.childCount ?: 0
            }
            TVShowSortOrder.EPISODE_COUNT_ASC -> filtered.sortedBy {
                it.childCount ?: 0
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    // View mode toggle with Expressive animation
                    SingleChoiceSegmentedButtonRow {
                        TVShowViewMode.entries.forEachIndexed { index, mode ->
                            val scale by animateFloatAsState(
                                targetValue = if (viewMode == mode) 1.1f else 1.0f,
                                animationSpec = MotionTokens.expressiveEnter,
                                label = "segmented_button_scale",
                            )
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = TVShowViewMode.entries.size,
                                ),
                                onClick = { viewMode = mode },
                                selected = viewMode == mode,
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                                    inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                                modifier = Modifier.padding(2.dp),
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        TVShowViewMode.GRID -> Icons.Default.GridView
                                        TVShowViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList
                                        TVShowViewMode.CAROUSEL -> Icons.Default.ViewCarousel
                                    },
                                    contentDescription = when (mode) {
                                        TVShowViewMode.GRID -> stringResource(id = R.string.grid_view)
                                        TVShowViewMode.LIST -> stringResource(id = R.string.list_view)
                                        TVShowViewMode.CAROUSEL -> "Carousel view"
                                    },
                                    modifier = Modifier.padding(2.dp),
                                )
                            }
                        }
                    }

                    // Sort menu
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(id = R.string.sort),
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            TVShowSortOrder.getAllSortOrders().forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(id = order.displayNameResId)) },
                                    onClick = {
                                        sortOrder = order
                                        showSortMenu = false
                                    },
                                )
                            }
                        }
                    }

                    IconButton(onClick = { viewModel.refreshTVShows() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(id = R.string.refresh),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Filter chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(TVShowFilter.getAllFilters()) { filter ->
                    FilterChip(
                        onClick = { selectedFilter = filter },
                        label = { Text(stringResource(id = filter.displayNameResId)) },
                        selected = selectedFilter == filter,
                        leadingIcon = if (filter == TVShowFilter.FAVORITES) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.padding(2.dp),
                                )
                            }
                        } else {
                            null
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }

            // Content with Expressive animations
            AnimatedContent(
                targetState = when {
                    appState.isLoadingTVShows && tvShowItems.isEmpty() -> TVShowContentState.LOADING
                    appState.errorMessage != null -> TVShowContentState.ERROR
                    filteredAndSortedTVShows.isEmpty() -> TVShowContentState.EMPTY
                    else -> TVShowContentState.CONTENT
                },
                transitionSpec = {
                    fadeIn(MotionTokens.expressiveEnter) + slideInVertically { it / 4 } togetherWith
                        fadeOut(MotionTokens.expressiveExit) + slideOutVertically { -it / 4 }
                },
                label = "tv_shows_content",
            ) { contentState ->
                when (contentState) {
                    TVShowContentState.LOADING -> {
                        ExpressiveFullScreenLoading(
                            message = "Loading TV Shows...",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    TVShowContentState.ERROR -> {
                        ExpressiveErrorState(
                            message = appState.errorMessage ?: "Unknown error",
                            onRetry = { viewModel.refreshTVShows() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    TVShowContentState.EMPTY -> {
                        ExpressiveEmptyState(
                            icon = Icons.Default.Tv,
                            title = stringResource(id = R.string.no_tv_shows_found),
                            subtitle = stringResource(id = R.string.adjust_tv_shows_filters_hint),
                            iconTint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    TVShowContentState.CONTENT -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            TVShowsContent(
                                tvShows = filteredAndSortedTVShows,
                                viewMode = viewMode,
                                getImageUrl = { item -> viewModel.getBackdropUrl(item) },
                                onTVShowClick = onTVShowClick,
                                isLoadingMore = appState.isLoadingTVShows,
                                hasMoreItems = appState.hasMoreTVShows,
                                onLoadMore = { viewModel.loadMoreTVShows() },
                            )

                            // Add ExpressiveFloatingToolbar for TV shows
                            if (filteredAndSortedTVShows.isNotEmpty()) {
                                ExpressiveFloatingToolbar(
                                    isVisible = filteredAndSortedTVShows.isNotEmpty(),
                                    onPlayClick = { /* TODO: Implement play functionality */ },
                                    onQueueClick = { /* TODO: Implement queue functionality */ },
                                    onDownloadClick = { /* TODO: Implement download functionality */ },
                                    onCastClick = { /* TODO: Implement cast functionality */ },
                                    onFavoriteClick = { /* TODO: Implement favorite functionality */ },
                                    onShareClick = { /* TODO: Implement share functionality */ },
                                    onMoreClick = { /* TODO: Implement more options functionality */ },
                                    primaryAction = ToolbarAction.PLAY,
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TVShowsContent(
    tvShows: List<BaseItemDto>,
    viewMode: TVShowViewMode,
    getImageUrl: (BaseItemDto) -> String?,
    onTVShowClick: (String) -> Unit,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = viewMode,
        transitionSpec = {
            fadeIn(MotionTokens.expressiveEnter) togetherWith
                fadeOut(MotionTokens.expressiveExit)
        },
        label = "view_mode_transition",
    ) { currentViewMode ->
        when (currentViewMode) {
            TVShowViewMode.GRID -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = modifier.fillMaxSize(),
                ) {
                    items(
                        items = tvShows,
                        key = { tvShow -> tvShow.getItemKey() },
                    ) { tvShow ->
                        ExpressiveMediaCard(
                            title = tvShow.name ?: "Unknown",
                            subtitle = tvShow.productionYear?.toString() ?: "",
                            imageUrl = getImageUrl(tvShow) ?: "",
                            rating = tvShow.communityRating?.toFloat(),
                            isFavorite = tvShow.userData?.isFavorite == true,
                            onCardClick = {
                                tvShow.id?.let { seriesId ->
                                    onTVShowClick(seriesId.toString())
                                }
                            },
                            onPlayClick = {
                                tvShow.id?.let { seriesId ->
                                    onTVShowClick(seriesId.toString())
                                }
                            },
                        )
                    }

                    if (hasMoreItems || isLoadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            TVShowsPaginationFooter(
                                isLoadingMore = isLoadingMore,
                                hasMoreItems = hasMoreItems,
                                onLoadMore = onLoadMore,
                            )
                        }
                    }
                }
            }

            TVShowViewMode.LIST -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = modifier.fillMaxSize(),
                ) {
                    items(
                        items = tvShows,
                        key = { tvShow -> tvShow.getItemKey() },
                    ) { tvShow ->
                        ExpressiveCompactCard(
                            title = tvShow.name ?: "Unknown",
                            subtitle = buildString {
                                tvShow.productionYear?.let { append("$it • ") }
                                tvShow.childCount?.let { append("$it episodes") }
                            },
                            imageUrl = getImageUrl(tvShow) ?: "",
                            onClick = {
                                tvShow.id?.let { seriesId ->
                                    onTVShowClick(seriesId.toString())
                                }
                            },
                            leadingIcon = if (tvShow.userData?.isFavorite == true) Icons.Default.Star else null,
                        )
                    }

                    if (hasMoreItems || isLoadingMore) {
                        item {
                            TVShowsPaginationFooter(
                                isLoadingMore = isLoadingMore,
                                hasMoreItems = hasMoreItems,
                                onLoadMore = onLoadMore,
                            )
                        }
                    }
                }
            }

            TVShowViewMode.CAROUSEL -> {
                val carouselItems = remember(tvShows) {
                    tvShows.take(20).map { tvShow ->
                        CarouselItem(
                            id = tvShow.id.toString(),
                            title = tvShow.name ?: "Unknown",
                            subtitle = tvShow.productionYear?.toString() ?: "",
                            imageUrl = getImageUrl(tvShow) ?: "",
                            type = MediaType.TV_SHOW,
                        )
                    }
                }

                ExpressiveMediaCarousel(
                    title = "TV Shows",
                    items = carouselItems,
                    onItemClick = { item ->
                        onTVShowClick(item.id)
                    },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun TVShowsPaginationFooter(
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        if (hasMoreItems && !isLoadingMore) {
            onLoadMore()
        }
    }

    AnimatedVisibility(
        visible = isLoadingMore || !hasMoreItems,
        enter = fadeIn(MotionTokens.expressiveEnter) + slideInVertically { it },
        exit = fadeOut(MotionTokens.expressiveExit) + slideOutVertically { -it },
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoadingMore) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ExpressiveDotsLoading(
                        modifier = Modifier.padding(8.dp),
                    )
                    Text(
                        text = "Loading more TV Shows...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (!hasMoreItems) {
                Text(
                    text = stringResource(id = R.string.no_more_tv_shows),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// Content state enum for animated transitions
enum class TVShowContentState {
    LOADING,
    ERROR,
    EMPTY,
    CONTENT,
}

// Expressive Error State component
@Composable
private fun ExpressiveErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

// Expressive Empty State component
@Composable
private fun ExpressiveEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconTint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            val scale by animateFloatAsState(
                targetValue = 1.0f,
                animationSpec = MotionTokens.expressiveEnter,
                label = "empty_icon_scale",
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(32.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                tint = iconTint.copy(alpha = 0.6f),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
