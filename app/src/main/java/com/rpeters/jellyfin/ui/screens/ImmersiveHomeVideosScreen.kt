package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.util.PerformanceMetricsTracker
import com.rpeters.jellyfin.data.models.HomeVideoSortOrder
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.components.ExpressiveCircularLoading
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
    PerformanceMetricsTracker(
        enabled = BuildConfig.DEBUG,
        intervalMs = 30000,
    )

    if (BuildConfig.DEBUG) {
        SecureLogger.d("ImmersiveHomeVideosScreen", "Screen started")
    }

    val appState by viewModel.appState.collectAsState()
    var sortOrder by remember { mutableStateOf(HomeVideoSortOrder.getDefault()) }
    var showSortMenu by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()
    val topBarVisible = true

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

    // Sort home videos for display
    val sortedVideos = remember(homeVideosItems, sortOrder) {
        sortHomeVideos(homeVideosItems, sortOrder)
    }

    val featuredVideos = remember(sortedVideos) {
        sortedVideos.take(5)
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
                        ExpressiveCircularLoading(
                            size = 24.dp,
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

                    sortedVideos.isEmpty() && !appState.isLoading -> {
                        ExpressiveSimpleEmptyState(
                            icon = Icons.Default.Photo,
                            title = stringResource(R.string.no_home_videos_found),
                            subtitle = stringResource(R.string.adjust_home_videos_filters_hint),
                            iconTint = PhotoYellow,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(ImmersiveDimens.CardWidthSmall),
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
                            if (featuredVideos.isNotEmpty()) {
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

                            items(
                                items = sortedVideos,
                                key = { it.id.toString() },
                            ) { video ->
                                ImmersiveMediaCard(
                                    title = video.name ?: "Home Video",
                                    subtitle = itemSubtitle(video),
                                    imageUrl = viewModel.getImageUrl(video) ?: "",
                                    onCardClick = { onItemClick?.invoke(video.id.toString()) },
                                    onPlayClick = { onItemClick?.invoke(video.id.toString()) },
                                    cardSize = ImmersiveCardSize.SMALL,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sortHomeVideos(
    homeVideos: List<BaseItemDto>,
    sortOrder: HomeVideoSortOrder,
): List<BaseItemDto> {
    if (homeVideos.isEmpty()) return emptyList()

    return when (sortOrder) {
        HomeVideoSortOrder.NAME_ASC -> homeVideos.sortedBy { it.sortName ?: it.name }
        HomeVideoSortOrder.NAME_DESC -> homeVideos.sortedByDescending { it.sortName ?: it.name }
        HomeVideoSortOrder.DATE_ADDED_DESC -> homeVideos.sortedByDescending { it.dateCreated }
        HomeVideoSortOrder.DATE_ADDED_ASC -> homeVideos.sortedBy { it.dateCreated }
        HomeVideoSortOrder.DATE_CREATED_DESC -> homeVideos.sortedByDescending { it.premiereDate ?: it.dateCreated }
        HomeVideoSortOrder.DATE_CREATED_ASC -> homeVideos.sortedBy { it.premiereDate ?: it.dateCreated }
    }
}
