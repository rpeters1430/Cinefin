package com.rpeters.jellyfin.ui.screens

import android.app.Activity
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.util.PerformanceMetricsTracker
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.components.ExpressiveBackNavigationIcon
import com.rpeters.jellyfin.ui.components.ExpressiveHeroCarousel
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBar
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBarAction
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBarRefreshAction
import com.rpeters.jellyfin.ui.components.MediaCard
import com.rpeters.jellyfin.ui.components.MediaItemActionsSheet
import com.rpeters.jellyfin.ui.components.MiniPlayer
import com.rpeters.jellyfin.ui.components.PosterMediaCard
import com.rpeters.jellyfin.ui.components.WatchProgressBar
import com.rpeters.jellyfin.ui.image.ImageSize
import com.rpeters.jellyfin.ui.image.OptimizedImage
import com.rpeters.jellyfin.ui.utils.MediaPlayerUtils
import com.rpeters.jellyfin.ui.viewmodel.LibraryActionsPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.MainAppState
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.ui.viewmodel.SurfaceCoordinatorViewModel
import com.rpeters.jellyfin.utils.SecureLogger
import com.rpeters.jellyfin.utils.getItemKey
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@OptInAppExperimentalApis
@Composable
fun HomeScreen(
    appState: MainAppState,
    currentServer: JellyfinServer?,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSearchClick: () -> Unit = {},
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit = {},
    onLibraryClick: (BaseItemDto) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onNowPlayingClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    viewModel: MainAppViewModel = hiltViewModel(),
    libraryActionsPreferencesViewModel: LibraryActionsPreferencesViewModel = hiltViewModel(),
) {
    val libraryActionPrefs by libraryActionsPreferencesViewModel.preferences.collectAsStateWithLifecycle()
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

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            ExpressiveTopAppBar(
                title = currentServer?.name ?: stringResource(id = R.string.app_name),
                navigationIcon = {
                    if (showBackButton) {
                        ExpressiveBackNavigationIcon(onClick = onBackClick)
                    }
                },
                actions = {
                    ExpressiveTopAppBarRefreshAction(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        onClick = onRefresh,
                        isLoading = appState.isLoading,
                    )
                    ExpressiveTopAppBarAction(
                        icon = Icons.Default.Settings,
                        contentDescription = stringResource(id = R.string.settings),
                        onClick = onSettingsClick,
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                },
            )
        },
        bottomBar = {
            MiniPlayer(onExpandClick = onNowPlayingClick)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onSearchClick,
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(id = R.string.search),
                )
            }
        },
        containerColor = Color.Transparent, // Let gradient show through
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surface,
                    MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ),
        ),
    ) { paddingValues ->
        // Performance monitoring
        PerformanceMetricsTracker(
            enabled = com.rpeters.jellyfin.BuildConfig.DEBUG,
            intervalMs = 30000, // 30 seconds
        )

        Box(modifier = Modifier.padding(paddingValues)) {
            HomeContent(
                appState = appState,
                currentServer = currentServer,
                onRefresh = onRefresh,
                getImageUrl = getImageUrl,
                getBackdropUrl = getBackdropUrl,
                getSeriesImageUrl = getSeriesImageUrl,
                onItemClick = onItemClick,
                onItemLongPress = handleItemLongPress,
                onLibraryClick = onLibraryClick,
                modifier = Modifier.fillMaxSize(),
            )
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

private fun itemSubtitle(item: BaseItemDto): String = when (item.type) {
    BaseItemKind.EPISODE -> item.seriesName ?: ""
    BaseItemKind.SERIES -> item.productionYear?.toString() ?: ""
    BaseItemKind.AUDIO -> item.artists?.firstOrNull() ?: ""
    BaseItemKind.MOVIE -> item.productionYear?.toString() ?: ""
    else -> ""
}

private fun BaseItemDto.toCarouselItem(
    titleOverride: String,
    subtitleOverride: String,
    imageUrl: String,
): CarouselItem = CarouselItem(
    id = this.id.toString(),
    title = titleOverride,
    subtitle = subtitleOverride,
    imageUrl = imageUrl,
)

@OptInAppExperimentalApis
@Composable
fun HomeContent(
    appState: MainAppState,
    currentServer: JellyfinServer?,
    onRefresh: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit = {},
    onItemLongPress: (BaseItemDto) -> Unit = {},
    onLibraryClick: (BaseItemDto) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Calculate window size class for adaptive layout
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(activity = context as Activity)
    val isTablet = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    val layoutConfig = rememberHomeLayoutConfig()

    // ✅ DEBUG: Log received state for UI troubleshooting
    LaunchedEffect(appState.libraries.size, appState.recentlyAddedByTypes.size) {
        if (com.rpeters.jellyfin.BuildConfig.DEBUG) {
            SecureLogger.d(
                "HomeScreen",
                "Received state - Libraries: ${appState.libraries.size}, " +
                    "RecentlyAddedByTypes: ${appState.recentlyAddedByTypes.mapValues { it.value.size }}",
            )
        }
    }

    // Consolidate all derived state computations into a single derivedStateOf for better performance
    val contentLists by remember(
        appState.allItems,
        appState.continueWatching,
        appState.recentlyAddedByTypes,
        layoutConfig.continueWatchingLimit,
        layoutConfig.rowItemLimit,
        layoutConfig.featuredItemsLimit,
    ) {
        derivedStateOf {
            val continueWatching = getContinueWatchingItems(appState, layoutConfig.continueWatchingLimit)
            val movies = appState.recentlyAddedByTypes[BaseItemKind.MOVIE.name]
                ?.take(layoutConfig.rowItemLimit) ?: emptyList()
            val tvShows = appState.recentlyAddedByTypes[BaseItemKind.SERIES.name]
                ?.take(layoutConfig.rowItemLimit) ?: emptyList()
            val episodes = appState.recentlyAddedByTypes[BaseItemKind.EPISODE.name]
                ?.take(layoutConfig.rowItemLimit) ?: emptyList()
            val music = appState.recentlyAddedByTypes[BaseItemKind.AUDIO.name]
                ?.take(layoutConfig.rowItemLimit) ?: emptyList()
            val videos = appState.recentlyAddedByTypes[BaseItemKind.VIDEO.name]
                ?.take(layoutConfig.rowItemLimit) ?: emptyList()
            val featured = (movies + tvShows).take(layoutConfig.featuredItemsLimit)

            HomeContentLists(
                continueWatching = continueWatching,
                recentMovies = movies,
                recentTVShows = tvShows,
                featuredItems = featured,
                recentEpisodes = episodes,
                recentMusic = music,
                recentVideos = videos,
            )
        }
    }

    val rowSections = remember(contentLists) {
        listOf(
            HomeRowSectionConfig(
                key = HomeSectionKeys.NEXT_UP,
                contentType = HomeSectionContentTypes.POSTER_ROW,
                titleRes = R.string.home_next_up,
                items = contentLists.recentEpisodes,
                rowKind = HomeRowKind.POSTER,
                imageSelector = HomeImageSelector.SERIES_OR_DEFAULT,
            ),
            HomeRowSectionConfig(
                key = HomeSectionKeys.RECENT_MOVIES,
                contentType = HomeSectionContentTypes.POSTER_ROW,
                titleRes = R.string.home_recently_added_movies,
                items = contentLists.recentMovies,
                rowKind = HomeRowKind.POSTER,
                imageSelector = HomeImageSelector.DEFAULT,
            ),
            HomeRowSectionConfig(
                key = HomeSectionKeys.RECENT_TV_SHOWS,
                contentType = HomeSectionContentTypes.POSTER_ROW,
                titleRes = R.string.home_recently_added_tv_shows,
                items = contentLists.recentTVShows,
                rowKind = HomeRowKind.POSTER,
                imageSelector = HomeImageSelector.DEFAULT,
            ),
            HomeRowSectionConfig(
                key = HomeSectionKeys.RECENT_STUFF,
                contentType = HomeSectionContentTypes.MEDIA_ROW,
                titleRes = R.string.home_recently_added_stuff,
                items = contentLists.recentVideos,
                rowKind = HomeRowKind.MEDIA,
                imageSelector = HomeImageSelector.BACKDROP_OR_DEFAULT,
            ),
        ).filter { it.items.isNotEmpty() }
    }

    val surfaceCoordinatorViewModel: SurfaceCoordinatorViewModel = hiltViewModel()

    LaunchedEffect(surfaceCoordinatorViewModel, contentLists.continueWatching) {
        snapshotFlow {
            contentLists.continueWatching.mapNotNull { item ->
                val id = item.id.toString()
                Triple(id, item.name, item.seriesName)
            } to contentLists.continueWatching
        }
            .distinctUntilChangedBy { it.first }
            .collectLatest { (_, items) ->
                surfaceCoordinatorViewModel.updateContinueWatching(items)
            }
    }

    val imageProviders = remember(getImageUrl, getSeriesImageUrl, getBackdropUrl) {
        mapOf<HomeImageSelector, (BaseItemDto) -> String?>(
            HomeImageSelector.DEFAULT to getImageUrl,
            HomeImageSelector.SERIES_OR_DEFAULT to { item ->
                getSeriesImageUrl(item) ?: getImageUrl(item)
            },
            HomeImageSelector.BACKDROP_OR_DEFAULT to { item ->
                getBackdropUrl(item) ?: getImageUrl(item)
            },
        )
    }

    val unknownText = stringResource(id = R.string.unknown)
    val stableOnItemClick = remember(onItemClick) { onItemClick }
    val stableOnItemLongPress = remember(onItemLongPress) { onItemLongPress }

    val listState = rememberLazyListState()

    PullToRefreshBox(
        isRefreshing = appState.isLoading,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        if (isTablet) {
            // Tablet layout: Use grids for better space utilization
            TabletHomeLayout(
                contentLists = contentLists,
                layoutConfig = layoutConfig,
                windowSizeClass = windowSizeClass,
                getImageUrl = getImageUrl,
                getBackdropUrl = getBackdropUrl,
                getSeriesImageUrl = getSeriesImageUrl,
                onItemClick = stableOnItemClick,
                onItemLongPress = stableOnItemLongPress,
                unknownText = unknownText,
            )
        } else {
            // Phone layout: Use carousel-based vertical scrolling
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(layoutConfig.sectionSpacing),
                userScrollEnabled = true,
            ) {
                if (contentLists.featuredItems.isNotEmpty()) {
                    item(key = "featured", contentType = "carousel") {
                        val featured = remember(contentLists.featuredItems, unknownText) {
                            contentLists.featuredItems.map {
                                it.toCarouselItem(
                                    titleOverride = it.name ?: unknownText,
                                    subtitleOverride = itemSubtitle(it),
                                    imageUrl = getBackdropUrl(it) ?: getSeriesImageUrl(it)
                                        ?: getImageUrl(it) ?: "",
                                )
                            }
                        }
                        ExpressiveHeroCarousel(
                            items = featured,
                            onItemClick = { selected ->
                                contentLists.featuredItems.firstOrNull { it.id.toString() == selected.id }
                                    ?.let(stableOnItemClick)
                            },
                            onPlayClick = { selected ->
                                contentLists.featuredItems.firstOrNull { it.id.toString() == selected.id }
                                    ?.let(stableOnItemClick)
                            },
                            heroHeight = layoutConfig.heroHeight,
                            horizontalPadding = layoutConfig.heroHorizontalPadding,
                            pageSpacing = layoutConfig.heroPageSpacing,
                        )
                    }
                }

                // Continue Watching Section
                if (contentLists.continueWatching.isNotEmpty()) {
                    item(key = "continue_watching", contentType = "continueWatching") {
                        ContinueWatchingSection(
                            items = contentLists.continueWatching,
                            getImageUrl = getImageUrl,
                            onItemClick = stableOnItemClick,
                            onItemLongPress = stableOnItemLongPress,
                            cardWidth = layoutConfig.continueWatchingCardWidth,
                        )
                    }
                }

                // Media row sections
                rowSections.forEach { section ->
                    item(key = section.key, contentType = section.contentType) {
                        val imageProvider = imageProviders.getValue(section.imageSelector)
                        val title = stringResource(id = section.titleRes)

                        when (section.rowKind) {
                            HomeRowKind.POSTER -> {
                                PosterRowSection(
                                    title = title,
                                    items = section.items,
                                    getImageUrl = imageProvider,
                                    onItemClick = stableOnItemClick,
                                    onItemLongPress = stableOnItemLongPress,
                                    cardWidth = layoutConfig.posterCardWidth,
                                )
                            }
                            HomeRowKind.SQUARE -> {
                                SquareRowSection(
                                    title = title,
                                    items = section.items,
                                    getImageUrl = imageProvider,
                                    onItemClick = stableOnItemClick,
                                    onItemLongPress = stableOnItemLongPress,
                                    cardWidth = layoutConfig.mediaCardWidth,
                                )
                            }
                            HomeRowKind.MEDIA -> {
                                MediaRowSection(
                                    title = title,
                                    items = section.items,
                                    getImageUrl = imageProvider,
                                    onItemClick = stableOnItemClick,
                                    onItemLongPress = stableOnItemLongPress,
                                    cardWidth = layoutConfig.mediaCardWidth,
                                )
                            }
                        }
                    }
                }

                // Add extra space at the bottom for MiniPlayer
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

/**
 * Tablet-optimized home layout using grids for better space utilization.
 */
@OptInAppExperimentalApis
@Composable
private fun TabletHomeLayout(
    contentLists: HomeContentLists,
    layoutConfig: HomeLayoutConfig,
    windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit,
    unknownText: String,
) {
    // Calculate grid columns based on window size
    val gridColumns = when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Medium -> 3 // 600-840dp: 3 columns
        else -> 4 // > 840dp: 4 columns
    }

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(layoutConfig.sectionSpacing),
    ) {
        // Hero Carousel
        if (contentLists.featuredItems.isNotEmpty()) {
            item(key = "featured", contentType = "carousel") {
                val featured = remember(contentLists.featuredItems, unknownText) {
                    contentLists.featuredItems.map {
                        it.toCarouselItem(
                            titleOverride = it.name ?: unknownText,
                            subtitleOverride = itemSubtitle(it),
                            imageUrl = getBackdropUrl(it) ?: getSeriesImageUrl(it)
                                ?: getImageUrl(it) ?: "",
                        )
                    }
                }
                ExpressiveHeroCarousel(
                    items = featured,
                    onItemClick = { selected ->
                        contentLists.featuredItems.firstOrNull { it.id.toString() == selected.id }
                            ?.let(onItemClick)
                    },
                    onPlayClick = { selected ->
                        contentLists.featuredItems.firstOrNull { it.id.toString() == selected.id }
                            ?.let(onItemClick)
                    },
                    heroHeight = layoutConfig.heroHeight,
                    horizontalPadding = layoutConfig.heroHorizontalPadding,
                    pageSpacing = layoutConfig.heroPageSpacing,
                )
            }
        }

        // Continue Watching Grid
        if (contentLists.continueWatching.isNotEmpty()) {
            item(key = "continue_watching_header", contentType = "section_header") {
                Text(
                    text = "Continue Watching",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item(key = "continue_watching_grid", contentType = "grid") {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier
                        .height((200.dp * ((contentLists.continueWatching.size + gridColumns - 1) / gridColumns)))
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false,
                ) {
                    items(
                        items = contentLists.continueWatching,
                        key = { it.getItemKey() },
                    ) { item ->
                        PosterMediaCard(
                            item = item,
                            getImageUrl = { getSeriesImageUrl(it) ?: getImageUrl(it) },
                            onClick = onItemClick,
                            onLongPress = onItemLongPress,
                        )
                    }
                }
            }
        }

        // Next Up Grid
        if (contentLists.recentEpisodes.isNotEmpty()) {
            item(key = "next_up_header", contentType = "section_header") {
                Text(
                    text = stringResource(id = R.string.home_next_up),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item(key = "next_up_grid", contentType = "grid") {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier
                        .height((200.dp * ((contentLists.recentEpisodes.size.coerceAtMost(gridColumns * 2) + gridColumns - 1) / gridColumns)))
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false,
                ) {
                    items(
                        items = contentLists.recentEpisodes.take(gridColumns * 2),
                        key = { it.getItemKey() },
                    ) { item ->
                        PosterMediaCard(
                            item = item,
                            getImageUrl = { getSeriesImageUrl(it) ?: getImageUrl(it) },
                            onClick = onItemClick,
                            onLongPress = onItemLongPress,
                        )
                    }
                }
            }
        }

        // Recently Added Movies Grid
        if (contentLists.recentMovies.isNotEmpty()) {
            item(key = "recent_movies_header", contentType = "section_header") {
                Text(
                    text = stringResource(id = R.string.home_recently_added_movies),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item(key = "recent_movies_grid", contentType = "grid") {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier
                        .height((200.dp * ((contentLists.recentMovies.size.coerceAtMost(gridColumns * 2) + gridColumns - 1) / gridColumns)))
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false,
                ) {
                    items(
                        items = contentLists.recentMovies.take(gridColumns * 2),
                        key = { it.getItemKey() },
                    ) { item ->
                        PosterMediaCard(
                            item = item,
                            getImageUrl = getImageUrl,
                            onClick = onItemClick,
                            onLongPress = onItemLongPress,
                        )
                    }
                }
            }
        }

        // Recently Added TV Shows Grid
        if (contentLists.recentTVShows.isNotEmpty()) {
            item(key = "recent_tv_header", contentType = "section_header") {
                Text(
                    text = stringResource(id = R.string.home_recently_added_tv_shows),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item(key = "recent_tv_grid", contentType = "grid") {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier
                        .height((200.dp * ((contentLists.recentTVShows.size.coerceAtMost(gridColumns * 2) + gridColumns - 1) / gridColumns)))
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false,
                ) {
                    items(
                        items = contentLists.recentTVShows.take(gridColumns * 2),
                        key = { it.getItemKey() },
                    ) { item ->
                        PosterMediaCard(
                            item = item,
                            getImageUrl = getImageUrl,
                            onClick = onItemClick,
                            onLongPress = onItemLongPress,
                        )
                    }
                }
            }
        }

        // Recently Added Videos (Horizontal cards)
        if (contentLists.recentVideos.isNotEmpty()) {
            item(key = "recent_videos_header", contentType = "section_header") {
                Text(
                    text = stringResource(id = R.string.home_recently_added_stuff),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item(key = "recent_videos_grid", contentType = "grid") {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2), // 2 columns for horizontal cards
                    modifier = Modifier
                        .height((140.dp * ((contentLists.recentVideos.size.coerceAtMost(4) + 1) / 2)))
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false,
                ) {
                    items(
                        items = contentLists.recentVideos.take(4),
                        key = { it.getItemKey() },
                    ) { item ->
                        MediaCard(
                            item = item,
                            getImageUrl = { getBackdropUrl(it) ?: getImageUrl(it) },
                            onClick = onItemClick,
                            onLongPress = onItemLongPress,
                        )
                    }
                }
            }
        }

        // Add extra space at the bottom for MiniPlayer
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Immutable
private data class HomeLayoutConfig(
    val sectionSpacing: Dp,
    val heroHeight: Dp,
    val heroHorizontalPadding: Dp,
    val heroPageSpacing: Dp,
    val featuredItemsLimit: Int,
    val rowItemLimit: Int,
    val continueWatchingLimit: Int,
    val continueWatchingCardWidth: Dp,
    val posterCardWidth: Dp,
    val mediaCardWidth: Dp,
)

@Stable
private data class HomeContentLists(
    val continueWatching: List<BaseItemDto>,
    val recentMovies: List<BaseItemDto>,
    val recentTVShows: List<BaseItemDto>,
    val featuredItems: List<BaseItemDto>,
    val recentEpisodes: List<BaseItemDto>,
    val recentMusic: List<BaseItemDto>,
    val recentVideos: List<BaseItemDto>,
)

@Stable
private data class HomeRowSectionConfig(
    val key: String,
    val contentType: String,
    @StringRes val titleRes: Int,
    val items: List<BaseItemDto>,
    val rowKind: HomeRowKind,
    val imageSelector: HomeImageSelector,
)

private object HomeSectionKeys {
    const val NEXT_UP = "next_up"
    const val RECENT_MOVIES = "recent_movies"
    const val RECENT_TV_SHOWS = "recent_tvshows"
    const val RECENT_STUFF = "recent_stuff"
}

private object HomeSectionContentTypes {
    const val POSTER_ROW = "poster_row"
    const val MUSIC_ROW = "music_row"
    const val MEDIA_ROW = "media_row"
}

private enum class HomeRowKind {
    POSTER,
    SQUARE,
    MEDIA,
}

private enum class HomeImageSelector {
    DEFAULT,
    SERIES_OR_DEFAULT,
    BACKDROP_OR_DEFAULT,
}

@Composable
private fun rememberHomeLayoutConfig(): HomeLayoutConfig {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val isCompactWidth = screenWidth < 600
    val isUltraCompact = screenWidth < 380

    return remember(screenWidth) {
        if (isCompactWidth) {
            HomeLayoutConfig(
                sectionSpacing = 16.dp,
                heroHeight = 400.dp,
                heroHorizontalPadding = 12.dp,
                heroPageSpacing = 8.dp,
                featuredItemsLimit = 6,
                rowItemLimit = 12,
                continueWatchingLimit = 6,
                continueWatchingCardWidth = if (isUltraCompact) 138.dp else 160.dp,
                posterCardWidth = if (isUltraCompact) 132.dp else 144.dp,
                mediaCardWidth = 260.dp,
            )
        } else {
            HomeLayoutConfig(
                sectionSpacing = 24.dp,
                heroHeight = 480.dp,
                heroHorizontalPadding = 16.dp,
                heroPageSpacing = 12.dp,
                featuredItemsLimit = 10,
                rowItemLimit = 15,
                continueWatchingLimit = 8,
                continueWatchingCardWidth = 180.dp,
                posterCardWidth = 160.dp,
                mediaCardWidth = 300.dp,
            )
        }
    }
}

@Composable
fun SearchResultsContent(
    searchResults: List<BaseItemDto>,
    isSearching: Boolean,
    errorMessage: String?,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (isSearching) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(
                            text = "Searching...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        errorMessage?.let { error ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        if (searchResults.isEmpty() && !isSearching && errorMessage == null) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "No results found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val groupedResults = searchResults.groupBy { it.type }
        groupedResults.forEach { (type, items) ->
            item {
                Text(
                    text = when (type) {
                        BaseItemKind.MOVIE -> "Movies"
                        BaseItemKind.SERIES -> "TV Shows"
                        BaseItemKind.EPISODE -> "Episodes"
                        BaseItemKind.AUDIO -> "Music"
                        BaseItemKind.MUSIC_ALBUM -> "Albums"
                        BaseItemKind.MUSIC_ARTIST -> "Artists"
                        BaseItemKind.BOOK -> "Books"
                        BaseItemKind.AUDIO_BOOK -> "Audiobooks"
                        else -> type.toString()
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            items(
                items = items.chunked(2),
                key = { rowItems -> rowItems.firstOrNull()?.getItemKey() ?: "" },
                contentType = { "search_result_row" },
            ) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowItems.forEach { item ->
                        if (item.type == BaseItemKind.MOVIE || item.type == BaseItemKind.SERIES) {
                            PosterMediaCard(
                                item = item,
                                getImageUrl = getImageUrl,
                                modifier = Modifier.weight(1f),
                                showTitle = true,
                                showMetadata = true,
                            )
                        } else {
                            MediaCard(
                                item = item,
                                getImageUrl = getImageUrl,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun getContinueWatchingItems(appState: MainAppState, maxItems: Int = 8): List<BaseItemDto> {
    val sourceItems = if (appState.continueWatching.isNotEmpty()) {
        appState.continueWatching
    } else {
        appState.allItems
    }

    return sourceItems.filter { item ->
        val percentage = item.userData?.playedPercentage ?: 0.0
        percentage > 0.0 && percentage < 100.0 &&
            (
                item.type == BaseItemKind.MOVIE ||
                    item.type == BaseItemKind.EPISODE ||
                    item.type == BaseItemKind.VIDEO
                )
    }.sortedByDescending { it.userData?.lastPlayedDate }.take(maxItems)
}

@Composable
private fun ContinueWatchingSection(
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit = {},
    cardWidth: Dp = 160.dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                text = "Continue Watching",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        val rowState = rememberLazyListState()
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(
                items = items,
                key = { it.getItemKey() },
                contentType = { "continue_watching_item" },
            ) { item ->
                ContinueWatchingCard(
                    item = item,
                    getImageUrl = getImageUrl,
                    onItemClick = onItemClick,
                    onItemLongPress = onItemLongPress,
                    cardWidth = cardWidth,
                )
            }
        }
    }
}

@OptInAppExperimentalApis
@Composable
private fun ContinueWatchingCard(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit = {},
    cardWidth: Dp = 160.dp,
    modifier: Modifier = Modifier,
) {
    val watchedPercentage = item.userData?.playedPercentage ?: 0.0

    ElevatedCard(
        modifier = modifier.width(cardWidth),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.combinedClickable(
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongPress(item) },
            ),
        ) {
            Box {
                OptimizedImage(
                    imageUrl = getImageUrl(item),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    size = ImageSize.POSTER,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )

                WatchProgressBar(
                    item = item,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(8.dp),
                )
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.name ?: stringResource(id = R.string.unknown),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Reserve a consistent second text line to avoid LazyRow height "jumping"
                // when episode cards (which show series name) scroll into view.
                val seriesName = if (item.type == BaseItemKind.EPISODE) item.seriesName.orEmpty() else ""
                Text(
                    text = seriesName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = "${watchedPercentage.roundToInt()}% watched",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PosterRowSection(
    title: String,
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit = {},
    cardWidth: Dp = 150.dp,
    modifier: Modifier = Modifier,
) {
    HomeRowSection(
        title = title,
        modifier = modifier,
    ) {
        items(
            items = items,
            key = { it.getItemKey() },
            contentType = { "poster_media_card" },
        ) { item ->
            PosterMediaCard(
                item = item,
                getImageUrl = getImageUrl,
                onClick = onItemClick,
                onLongPress = onItemLongPress,
                cardWidth = cardWidth,
                showTitle = true,
                showMetadata = true,
                titleMinLines = 2,
            )
        }
    }
}

@Composable
private fun SquareRowSection(
    title: String,
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit = {},
    cardWidth: Dp = 280.dp,
    modifier: Modifier = Modifier,
) {
    HomeRowSection(
        title = title,
        modifier = modifier,
    ) {
        items(
            items = items,
            key = { it.getItemKey() },
            contentType = { "square_media_card" },
        ) { item ->
            MediaCard(
                item = item,
                getImageUrl = getImageUrl,
                onClick = onItemClick,
                onLongPress = onItemLongPress,
                cardWidth = cardWidth,
            )
        }
    }
}

@Composable
private fun MediaRowSection(
    title: String,
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit = {},
    cardWidth: Dp = 280.dp,
    modifier: Modifier = Modifier,
) {
    HomeRowSection(
        title = title,
        modifier = modifier,
    ) {
        items(
            items = items,
            key = { it.getItemKey() },
            contentType = { "media_card" },
        ) { item ->
            MediaCard(
                item = item,
                getImageUrl = getImageUrl,
                onClick = onItemClick,
                onLongPress = onItemLongPress,
                cardWidth = cardWidth,
            )
        }
    }
}

@Composable
private fun HomeRowSection(
    title: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(16.dp),
    content: LazyListScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        HomeSectionTitle(title = title)

        val listState = rememberLazyListState()

        LazyRow(
            state = listState,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            content = content,
        )
    }
}

@Composable
private fun HomeSectionTitle(title: String, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
