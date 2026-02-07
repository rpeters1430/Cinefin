package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.models.HomeVideoFilter
import com.rpeters.jellyfin.data.models.HomeVideoSortOrder
import com.rpeters.jellyfin.data.models.HomeVideoViewMode
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.components.ExpressiveErrorState
import com.rpeters.jellyfin.ui.components.ExpressivePullToRefreshBox
import com.rpeters.jellyfin.ui.components.ExpressiveSimpleEmptyState
import com.rpeters.jellyfin.ui.components.immersive.*
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.ui.theme.PhotoYellow
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.utils.SecureLogger
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Immersive home videos browse screen with Netflix/Disney+ inspired design.
 * Features:
 * - Full-screen hero carousel for featured videos (if 5+ videos)
 * - Auto-hiding navigation bars
 * - Large media cards (280dp)
 * - Tighter spacing (16dp)
 * - Floating action buttons for view mode/sort/filters
 */
@OptInAppExperimentalApis
@Composable
fun ImmersiveHomeVideosScreen(
    onBackClick: () -> Unit = {},
    onItemClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
) {
    if (BuildConfig.DEBUG) {
        SecureLogger.d("ImmersiveHomeVideosScreen", "Screen started")
    }

    val appState by viewModel.appState.collectAsState()
    var selectedFilter by remember { mutableStateOf(HomeVideoFilter.ALL) }
    var sortOrder by remember { mutableStateOf(HomeVideoSortOrder.getDefault()) }
    var viewMode by remember { mutableStateOf(HomeVideoViewMode.GRID) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showViewModeMenu by remember { mutableStateOf(false) }

    // Track scroll state for auto-hiding navigation
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val topBarVisible = rememberAutoHideTopBarVisible(
        listState = if (viewMode == HomeVideoViewMode.LIST) listState else rememberLazyListState(),
        nearTopOffsetPx = with(LocalDensity.current) { ImmersiveDimens.HeroHeightPhone.toPx().toInt() },
    )

    // Find all Home Videos libraries
    val homeVideosLibraries = remember(appState.libraries) {
        appState.libraries.filter { library ->
            library.collectionType == org.jellyfin.sdk.model.api.CollectionType.HOMEVIDEOS
        }
    }

    // Load home videos if needed
    LaunchedEffect(homeVideosLibraries) {
        if (homeVideosLibraries.isNotEmpty()) {
            homeVideosLibraries.forEach { library ->
                val libraryId = library.id
                val currentItems = appState.itemsByLibrary[libraryId.toString()] ?: emptyList()
                if (currentItems.isEmpty()) {
                    viewModel.loadHomeVideos(libraryId.toString())
                }
            }
        }
    }

    // Get home videos items from all libraries
    val homeVideosItems = remember(appState.itemsByLibrary, homeVideosLibraries) {
        val allItems = mutableListOf<BaseItemDto>()
        homeVideosLibraries.forEach { library ->
            val items = appState.itemsByLibrary[library.id.toString()] ?: emptyList()
            allItems.addAll(items)
        }
        // Filter for videos only
        allItems.filter { item ->
            item.type == BaseItemKind.VIDEO || item.type == BaseItemKind.MOVIE
        }
    }

    // Apply filtering and sorting
    val filteredAndSortedVideos = remember(homeVideosItems, selectedFilter, sortOrder) {
        filterAndSortHomeVideos(homeVideosItems, selectedFilter, sortOrder)
    }

    val featuredVideos = remember(filteredAndSortedVideos) {
        filteredAndSortedVideos.take(5)
    }

    Box(modifier = modifier.fillMaxSize()) {
        ImmersiveScaffold(
            topBarVisible = topBarVisible,
            topBarTitle = stringResource(R.string.home_videos),
            topBarNavigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
            topBarActions = {
                // View Mode Menu
                IconButton(onClick = { showViewModeMenu = true }) {
                    Icon(
                        imageVector = when (viewMode) {
                            HomeVideoViewMode.GRID -> Icons.Default.GridView
                            HomeVideoViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList
                            HomeVideoViewMode.CAROUSEL -> Icons.Default.ViewCarousel
                        },
                        contentDescription = "View Mode",
                    )
                }
                DropdownMenu(
                    expanded = showViewModeMenu,
                    onDismissRequest = { showViewModeMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Grid View") },
                        onClick = {
                            viewMode = HomeVideoViewMode.GRID
                            showViewModeMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.GridView, contentDescription = null)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("List View") },
                        onClick = {
                            viewMode = HomeVideoViewMode.LIST
                            showViewModeMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Carousel View") },
                        onClick = {
                            viewMode = HomeVideoViewMode.CAROUSEL
                            showViewModeMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.ViewCarousel, contentDescription = null)
                        },
                    )
                }

                // Sort Menu
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort",
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    HomeVideoSortOrder.getAllSortOrders().forEach { order ->
                        DropdownMenuItem(
                            text = { Text(stringResource(id = order.displayNameResId)) },
                            onClick = {
                                sortOrder = order
                                showSortMenu = false
                            },
                        )
                    }
                }

                // Refresh Button
                IconButton(
                    onClick = { viewModel.loadInitialData() },
                    enabled = !appState.isLoading,
                ) {
                    if (appState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = PhotoYellow,
                        )
                    }
                }
            },
            topBarTranslucent = true,
            floatingActionButton = {
                // Filter FAB could be added here if needed
            },
        ) { paddingValues ->
            ExpressivePullToRefreshBox(
                isRefreshing = appState.isLoading,
                onRefresh = { viewModel.loadInitialData() },
                modifier = Modifier.fillMaxSize(),
                indicatorColor = PhotoYellow,
                useWavyIndicator = true,
            ) {
                when {
                    appState.errorMessage != null -> {
                        ExpressiveErrorState(
                            title = "Error Loading Home Videos",
                            message = appState.errorMessage ?: stringResource(R.string.unknown_error),
                            icon = Icons.Default.Photo,
                            onRetry = { viewModel.loadInitialData() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    filteredAndSortedVideos.isEmpty() && !appState.isLoading -> {
                        ExpressiveSimpleEmptyState(
                            icon = Icons.Default.Photo,
                            title = stringResource(R.string.no_home_videos_found),
                            subtitle = stringResource(R.string.adjust_home_videos_filters_hint),
                            iconTint = PhotoYellow,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    else -> {
                        when (viewMode) {
                            HomeVideoViewMode.GRID -> {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(ImmersiveDimens.CardWidthMedium),
                                    state = gridState,
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
                                    // Hero Carousel (if 5+ videos)
                                    if (featuredVideos.size >= 5 && gridState.firstVisibleItemIndex == 0) {
                                        item(
                                            key = "hero_carousel",
                                            span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                                        ) {
                                            val carouselItems = featuredVideos.map {
                                                CarouselItem(
                                                    id = it.id.toString(),
                                                    title = it.name ?: "Home Video",
                                                    subtitle = it.productionYear?.toString() ?: "",
                                                    imageUrl = viewModel.getBackdropUrl(it)
                                                        ?: viewModel.getImageUrl(it) ?: "",
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
                                                        featuredVideos.find { it.id.toString() == item.id }
                                                            ?.id?.let { onItemClick?.invoke(it.toString()) }
                                                    },
                                                    onPlayClick = { item ->
                                                        featuredVideos.find { it.id.toString() == item.id }
                                                            ?.id?.let { onItemClick?.invoke(it.toString()) }
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    // Video Grid
                                    items(
                                        items = filteredAndSortedVideos,
                                        key = { it.id.toString() },
                                    ) { video ->
                                        ImmersiveMediaCard(
                                            title = video.name ?: "Home Video",
                                            subtitle = itemSubtitle(video),
                                            imageUrl = viewModel.getImageUrl(video) ?: "",
                                            onCardClick = { onItemClick?.invoke(video.id.toString()) },
                                            cardSize = ImmersiveCardSize.MEDIUM,
                                        )
                                    }
                                }
                            }

                            HomeVideoViewMode.LIST -> {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        top = 0.dp,
                                        bottom = 120.dp,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight),
                                ) {
                                    // Hero Carousel (if 5+ videos)
                                    if (featuredVideos.size >= 5) {
                                        item(key = "hero_carousel") {
                                            val carouselItems = featuredVideos.map {
                                                CarouselItem(
                                                    id = it.id.toString(),
                                                    title = it.name ?: "Home Video",
                                                    subtitle = it.productionYear?.toString() ?: "",
                                                    imageUrl = viewModel.getBackdropUrl(it)
                                                        ?: viewModel.getImageUrl(it) ?: "",
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
                                                        featuredVideos.find { it.id.toString() == item.id }
                                                            ?.id?.let { onItemClick?.invoke(it.toString()) }
                                                    },
                                                    onPlayClick = { item ->
                                                        featuredVideos.find { it.id.toString() == item.id }
                                                            ?.id?.let { onItemClick?.invoke(it.toString()) }
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    // Video List
                                    items(
                                        count = filteredAndSortedVideos.size,
                                        key = { filteredAndSortedVideos[it].id.toString() },
                                    ) { index ->
                                        val video = filteredAndSortedVideos[index]
                                        ImmersiveMediaCard(
                                            title = video.name ?: "Home Video",
                                            subtitle = itemSubtitle(video),
                                            imageUrl = viewModel.getImageUrl(video) ?: "",
                                            onCardClick = { onItemClick?.invoke(video.id.toString()) },
                                            cardSize = ImmersiveCardSize.LARGE,
                                            modifier = Modifier.padding(horizontal = ImmersiveDimens.SpacingRowTight),
                                        )
                                    }
                                }
                            }

                            HomeVideoViewMode.CAROUSEL -> {
                                // Carousel view with horizontal scrolling rows
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        top = 0.dp,
                                        bottom = 120.dp,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight),
                                ) {
                                    // Hero Carousel (if 5+ videos)
                                    if (featuredVideos.size >= 5) {
                                        item(key = "hero_carousel") {
                                            val carouselItems = featuredVideos.map {
                                                CarouselItem(
                                                    id = it.id.toString(),
                                                    title = it.name ?: "Home Video",
                                                    subtitle = it.productionYear?.toString() ?: "",
                                                    imageUrl = viewModel.getBackdropUrl(it)
                                                        ?: viewModel.getImageUrl(it) ?: "",
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
                                                        featuredVideos.find { it.id.toString() == item.id }
                                                            ?.id?.let { onItemClick?.invoke(it.toString()) }
                                                    },
                                                    onPlayClick = { item ->
                                                        featuredVideos.find { it.id.toString() == item.id }
                                                            ?.id?.let { onItemClick?.invoke(it.toString()) }
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    // All Videos Row
                                    item(key = "all_videos") {
                                        ImmersiveMediaRow(
                                            title = "All Home Videos",
                                            items = filteredAndSortedVideos,
                                            getImageUrl = { viewModel.getImageUrl(it) ?: "" },
                                            onItemClick = { onItemClick?.invoke(it.id.toString()) },
                                            size = ImmersiveCardSize.MEDIUM,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun filterAndSortHomeVideos(
    homeVideos: List<BaseItemDto>,
    selectedFilter: HomeVideoFilter,
    sortOrder: HomeVideoSortOrder,
): List<BaseItemDto> {
    if (homeVideos.isEmpty()) return emptyList()

    val filtered = when (selectedFilter) {
        HomeVideoFilter.ALL -> homeVideos
        HomeVideoFilter.FAVORITES -> homeVideos.filter { it.userData?.isFavorite == true }
        HomeVideoFilter.UNWATCHED -> homeVideos.filter { it.userData?.played != true }
        HomeVideoFilter.WATCHED -> homeVideos.filter { it.userData?.played == true }
        HomeVideoFilter.RECENT -> homeVideos.sortedByDescending { it.dateCreated }.take(50)
    }

    return when (sortOrder) {
        HomeVideoSortOrder.NAME_ASC -> filtered.sortedBy { it.sortName ?: it.name }
        HomeVideoSortOrder.NAME_DESC -> filtered.sortedByDescending { it.sortName ?: it.name }
        HomeVideoSortOrder.DATE_ADDED_DESC -> filtered.sortedByDescending { it.dateCreated }
        HomeVideoSortOrder.DATE_ADDED_ASC -> filtered.sortedBy { it.dateCreated }
        HomeVideoSortOrder.DATE_CREATED_DESC -> filtered.sortedByDescending { it.premiereDate ?: it.dateCreated }
        HomeVideoSortOrder.DATE_CREATED_ASC -> filtered.sortedBy { it.premiereDate ?: it.dateCreated }
    }
}
