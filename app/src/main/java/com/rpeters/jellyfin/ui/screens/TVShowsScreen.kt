package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.components.ExpressiveCompactCard
import com.rpeters.jellyfin.ui.components.ExpressiveFullScreenLoading
import com.rpeters.jellyfin.ui.components.ExpressiveMediaCarousel
import com.rpeters.jellyfin.ui.components.MediaItemActionsSheet
import com.rpeters.jellyfin.ui.components.MediaType
import com.rpeters.jellyfin.ui.components.PosterMediaCard
import com.rpeters.jellyfin.ui.theme.MotionTokens
import com.rpeters.jellyfin.ui.theme.MusicGreen
import com.rpeters.jellyfin.ui.theme.SeriesBlue
import com.rpeters.jellyfin.ui.utils.MediaPlayerUtils
import com.rpeters.jellyfin.ui.viewmodel.LibraryActionsPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.utils.getItemKey
import com.rpeters.jellyfin.utils.getRatingAsDouble
import com.rpeters.jellyfin.utils.hasHighRating
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto

enum class TVShowFilter(val displayNameResId: Int) {
    ALL(R.string.filter_all_shows),
    FAVORITES(R.string.filter_favorites_shows),
    UNWATCHED(R.string.filter_unwatched_shows),
    IN_PROGRESS(R.string.filter_in_progress),
    CONTINUING(R.string.filter_continuing),
    ENDED(R.string.filter_ended),
    RECENT(R.string.filter_recent_shows),
    HIGH_RATED(R.string.filter_high_rated_shows),
    ;

    companion object {
        fun getBasicFilters() = listOf(ALL, FAVORITES, UNWATCHED, IN_PROGRESS)
        fun getSmartFilters() = listOf(CONTINUING, ENDED, RECENT, HIGH_RATED)
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

@OptInAppExperimentalApis
@Composable
fun TVShowsScreen(
    onTVShowClick: (String) -> Unit = {},
    onBackClick: () -> Unit = {},
    viewModel: MainAppViewModel = hiltViewModel(),
    libraryActionsPreferencesViewModel: LibraryActionsPreferencesViewModel = hiltViewModel(),
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val appState by viewModel.appState.collectAsState()
    val libraryActionPrefs by libraryActionsPreferencesViewModel.preferences.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf(TVShowFilter.ALL) }
    var sortOrder by remember { mutableStateOf(TVShowSortOrder.getDefault()) }
    var viewMode by remember { mutableStateOf(TVShowViewMode.GRID) }
    var showSortMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedItem by remember { mutableStateOf<BaseItemDto?>(null) }
    var showManageSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val managementEnabled = libraryActionPrefs.enableManagementActions
    val managementDisabledMessage = stringResource(id = R.string.library_actions_management_disabled)

    val handleItemLongPress: (BaseItemDto) -> Unit = { item ->
        if (managementEnabled) {
            selectedItem = item
            showManageSheet = true
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message = managementDisabledMessage)
            }
        }
    }

    val handlePlay: (BaseItemDto) -> Unit = { item ->
        val streamUrl = viewModel.getStreamUrl(item)
        if (streamUrl != null) {
            MediaPlayerUtils.playMedia(context, streamUrl, item)
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Unable to start playback")
            }
        }
    }

    // TV show data is loaded via NavGraph effect

    // Get items provided by the unified library loader (Series only)
    // Don't use remember() here - we want fresh data on every recomposition
    val tvShowItems = viewModel.getLibraryTypeData(LibraryType.TV_SHOWS)
    // Merge NavGraph-provided loading state with the ViewModel flag to avoid flashing an empty state
    val isLoadingState = isLoading || appState.isLoadingTVShows

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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = stringResource(id = R.string.tv_shows),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                },
                actions = {
                    // View mode toggle
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        IconButton(
                            onClick = {
                                viewMode = when (viewMode) {
                                    TVShowViewMode.GRID -> TVShowViewMode.LIST
                                    TVShowViewMode.LIST -> TVShowViewMode.CAROUSEL
                                    TVShowViewMode.CAROUSEL -> TVShowViewMode.GRID
                                }
                            },
                        ) {
                            Icon(
                                imageVector = when (viewMode) {
                                    TVShowViewMode.GRID -> Icons.AutoMirrored.Filled.ViewList
                                    TVShowViewMode.LIST -> Icons.Default.ViewCarousel
                                    TVShowViewMode.CAROUSEL -> Icons.Default.GridView
                                },
                                contentDescription = "Toggle view mode",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // Sort menu
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort",
                                    tint = MaterialTheme.colorScheme.primary,
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
                    }

                    // Refresh button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                        modifier = Modifier.padding(end = 8.dp, start = 4.dp),
                    ) {
                        IconButton(onClick = { viewModel.refreshTVShows() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = MusicGreen,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isLoadingState,
            onRefresh = { viewModel.refreshTVShows() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Filter chips with enhanced styling and organization
                Column {
                // Basic Filters
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    items(TVShowFilter.getBasicFilters(), key = { it.name }) { filter ->
                        FilterChip(
                            onClick = { selectedFilter = filter },
                            label = {
                                Text(
                                    text = stringResource(id = filter.displayNameResId),
                                    fontWeight = if (selectedFilter == filter) FontWeight.SemiBold else FontWeight.Medium,
                                )
                            },
                            selected = selectedFilter == filter,
                            leadingIcon = if (filter == TVShowFilter.FAVORITES) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else {
                                null
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }

                // Smart Filters
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    items(TVShowFilter.getSmartFilters(), key = { it.name }) { filter ->
                        FilterChip(
                            onClick = { selectedFilter = filter },
                            label = {
                                Text(
                                    text = stringResource(id = filter.displayNameResId),
                                    fontWeight = if (selectedFilter == filter) FontWeight.SemiBold else FontWeight.Medium,
                                )
                            },
                            selected = selectedFilter == filter,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        )
                    }
                }
            }

            // Content with Expressive animations
            AnimatedContent(
                targetState = when {
                    isLoadingState -> TVShowContentState.LOADING
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
                            message = appState.errorMessage ?: stringResource(R.string.unknown_error),
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
                        TVShowsContent(
                            tvShows = filteredAndSortedTVShows,
                            viewMode = viewMode,
                            getImageUrl = { item -> viewModel.getImageUrl(item) },
                            onTVShowClick = onTVShowClick,
                            onTVShowLongPress = handleItemLongPress,
                            isLoadingMore = appState.isLoadingTVShows,
                            hasMoreItems = appState.hasMoreTVShows,
                            onLoadMore = { viewModel.loadMoreTVShows() },
                        )
                    }
                }
            }
        }
    }

    // Show media actions sheet when item is long-pressed
    selectedItem?.let { item ->
        if (showManageSheet) {
            val itemName = item.name ?: stringResource(id = R.string.unknown)
            val deleteSuccessMessage = stringResource(id = R.string.library_actions_delete_success, itemName)
            val deleteFailureTemplate = stringResource(id = R.string.library_actions_delete_failure, itemName, "%s")
            val refreshRequestedMessage = stringResource(id = R.string.library_actions_refresh_requested)
            val unknownErrorMessage = stringResource(id = R.string.unknown_error)

            MediaItemActionsSheet(
                item = item,
                sheetState = sheetState,
                onDismiss = {
                    showManageSheet = false
                    selectedItem = null
                },
                onPlay = {
                    handlePlay(item)
                },
                onDelete = { _, _ ->
                    viewModel.deleteItem(item) { success, message ->
                        coroutineScope.launch {
                            val text = if (success) {
                                deleteSuccessMessage
                            } else {
                                String.format(deleteFailureTemplate, message ?: "")
                            }
                            snackbarHostState.showSnackbar(text)
                        }
                    }
                },
                onRefreshMetadata = { _, _ ->
                    viewModel.refreshItemMetadata(item) { success, message ->
                        coroutineScope.launch {
                            val text = if (success) {
                                refreshRequestedMessage
                            } else {
                                "Failed to refresh metadata: ${message ?: unknownErrorMessage}"
                            }
                            snackbarHostState.showSnackbar(text)
                        }
                    }
                },
                onToggleWatched = {
                    viewModel.toggleWatchedStatus(item)
                },
                managementEnabled = managementEnabled,
            )
        }
    }
}

@Composable
private fun TVShowsContent(
    tvShows: List<BaseItemDto>,
    viewMode: TVShowViewMode,
    getImageUrl: (BaseItemDto) -> String?,
    onTVShowClick: (String) -> Unit,
    onTVShowLongPress: (BaseItemDto) -> Unit = {},
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
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = modifier.fillMaxSize(),
                ) {
                    items(
                        items = tvShows,
                        key = { tvShow -> tvShow.getItemKey() },
                    ) { tvShow ->
                        val scale by animateFloatAsState(
                            targetValue = 1.0f,
                            animationSpec = MotionTokens.expressiveEnter,
                            label = "tv_show_card_scale",
                        )

                        PosterMediaCard(
                            item = tvShow,
                            getImageUrl = { getImageUrl(it) },
                            onClick = { tvShow ->
                                tvShow.id?.let { seriesId ->
                                    onTVShowClick(seriesId.toString())
                                }
                            },
                            onLongPress = onTVShowLongPress,
                            showTitle = true,
                            showMetadata = true,
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
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
                        val scale by animateFloatAsState(
                            targetValue = 1.0f,
                            animationSpec = MotionTokens.expressiveEnter,
                            label = "tv_show_list_card_scale",
                        )

                        ExpressiveCompactCard(
                            title = tvShow.name ?: stringResource(id = R.string.unknown),
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
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
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
                val unknownText = stringResource(id = R.string.unknown)
                val carouselItems = remember(tvShows, unknownText) {
                    tvShows.take(20).map { tvShow ->
                        CarouselItem(
                            id = tvShow.id.toString(),
                            title = tvShow.name ?: unknownText,
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoadingMore) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = SeriesBlue,
                    modifier = Modifier.padding(8.dp),
                )
                Text(
                    text = "Loading more TV shows...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (hasMoreItems) {
            TextButton(onClick = onLoadMore) {
                Text("Load more")
            }
        } else {
            Text(
                text = "No more TV shows",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
