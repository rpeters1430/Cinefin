package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.stickyHeader
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.util.PerformanceMetricsTracker
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.components.ExpressiveErrorState
import com.rpeters.jellyfin.ui.components.ExpressivePullToRefreshBox
import com.rpeters.jellyfin.ui.components.ExpressiveSimpleEmptyState
import com.rpeters.jellyfin.ui.components.immersive.FabAction
import com.rpeters.jellyfin.ui.components.immersive.FabOrientation
import com.rpeters.jellyfin.ui.components.immersive.FloatingActionGroup
import com.rpeters.jellyfin.ui.components.immersive.ImmersiveHeroCarousel
import com.rpeters.jellyfin.ui.components.immersive.ImmersivePosterCard
import com.rpeters.jellyfin.ui.components.immersive.ImmersiveScaffold
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.utils.getUnwatchedEpisodeCount
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jellyfin.sdk.model.api.BaseItemDto

/** Theme and empty-state configuration for [ImmersiveLibraryBrowserScreen]. */
data class ImmersiveLibraryConfig(
    val themeColor: Color,
    val emptyStateIcon: ImageVector,
    val emptyStateTitle: String,
    val emptyStateSubtitle: String,
    /** Display name shown in the sticky header (e.g. "Movies"). Empty hides the header text. */
    val libraryName: String = "",
)

/** A single entry in the sort dropdown for [ImmersiveLibraryBrowserScreen]. */
data class ImmersiveSortOption(
    val labelRes: Int,
    val key: String,
)

/**
 * Shared immersive library browse screen used by Movies, TV Shows, and Home Videos.
 *
 * Callers are responsible for:
 * - Pre-sorting [items] before passing them in
 * - Building [featuredItems] for the hero carousel (pass empty list to hide carousel)
 * - Providing [buildCarouselItem] to map each featured item to carousel metadata
 */
@OptIn(ExperimentalFoundationApi::class)
@OptInAppExperimentalApis
@Composable
fun ImmersiveLibraryBrowserScreen(
    items: List<BaseItemDto>,
    featuredItems: List<BaseItemDto>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    config: ImmersiveLibraryConfig,
    sortOptions: List<ImmersiveSortOption>,
    selectedSortIndex: Int,
    onSortSelected: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (String) -> Unit,
    onCarouselItemClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onSearchClick: () -> Unit,
    onBackClick: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    buildCarouselItem: (BaseItemDto) -> CarouselItem?,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    PerformanceMetricsTracker(
        enabled = com.rpeters.jellyfin.BuildConfig.DEBUG,
        intervalMs = 30000,
    )

    // buildCarouselItem is intentionally omitted from the remember key because it is always
    // a stable lambda backed by a Hilt ViewModel. Adding it would cause recomposition on every
    // composable invocation since plain lambdas are not structurally stable in Compose.
    val carouselItems = remember(featuredItems) {
        featuredItems.mapNotNull { buildCarouselItem(it) }
    }

    val errorTitle = stringResource(R.string.library_error_loading_title)
    var showSortMenu by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    // Density pass (item 7): single-select filter chip row in the sticky header, reusing the
    // same FilterType/applyFilter pattern as LibraryFilterRow (LibraryFilters.kt). Filtering
    // here only affects the already-loaded page of items (client-side), same as elsewhere.
    var selectedFilter by remember { mutableStateOf(FilterType.getDefault()) }
    val filteredItems = remember(items, selectedFilter) { applyFilter(items, selectedFilter) }

    LaunchedEffect(gridState, items, hasMoreItems, isLoadingMore) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                val nearEnd = lastVisibleIndex >= (items.lastIndex - 8).coerceAtLeast(0)
                if (nearEnd && hasMoreItems && !isLoadingMore) {
                    onLoadMore()
                }
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ImmersiveScaffold(
            topBarVisible = false,
            topBarTitle = "",
            topBarTranslucent = false,
            floatingActionButton = {
                FloatingActionGroup(
                    orientation = FabOrientation.Vertical,
                    primaryAction = FabAction(
                        icon = Icons.Default.Search,
                        contentDescription = "Search",
                        onClick = onSearchClick,
                    ),
                    secondaryActions = emptyList(),
                )
            },
        ) { _ -> // paddingValues intentionally unused; content fills edge-to-edge
            ExpressivePullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
                indicatorColor = config.themeColor,
                useWavyIndicator = true,
            ) {
                when {
                    errorMessage != null -> {
                        ExpressiveErrorState(
                            title = errorTitle,
                            message = errorMessage,
                            icon = config.emptyStateIcon,
                            onRetry = onRefresh,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    items.isEmpty() && !isLoading -> {
                        ExpressiveSimpleEmptyState(
                            icon = config.emptyStateIcon,
                            title = config.emptyStateTitle,
                            subtitle = config.emptyStateSubtitle,
                            iconTint = config.themeColor,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> {
                        // Density pass: dense 3-column grid (115x173 poster cells, 10dp gaps)
                        // instead of the previous adaptive/160dp overlay-card grid.
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = 0.dp,
                                start = ImmersiveDimens.CardGapGrid,
                                end = ImmersiveDimens.CardGapGrid,
                                bottom = 120.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(ImmersiveDimens.CardGapGrid),
                            horizontalArrangement = Arrangement.spacedBy(ImmersiveDimens.CardGapGrid),
                        ) {
                            if (carouselItems.isNotEmpty()) {
                                item(
                                    key = "library_hero",
                                    span = { GridItemSpan(maxLineSpan) },
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(ImmersiveDimens.HeroHeightPhone + 60.dp)
                                            .clipToBounds(),
                                    ) {
                                        ImmersiveHeroCarousel(
                                            items = carouselItems,
                                            onItemClick = { onCarouselItemClick(it.id) },
                                            onPlayClick = { onCarouselItemClick(it.id) },
                                            pageSpacing = 0.dp,
                                        )
                                    }
                                }
                            }

                            // Sticky header: library name + item count + filter chip row.
                            stickyHeader(key = "library_sticky_header") {
                                LibraryStickyHeader(
                                    libraryName = config.libraryName,
                                    itemCount = items.size,
                                    selectedFilter = selectedFilter,
                                    onFilterSelected = { selectedFilter = it },
                                    themeColor = config.themeColor,
                                )
                            }

                            gridItems(
                                items = filteredItems,
                                key = { it.id.toString() },
                            ) { item ->
                                ImmersivePosterCard(
                                    title = item.name ?: "Unknown",
                                    subtitle = buildItemSubtitle(item),
                                    imageUrl = getImageUrl(item) ?: "",
                                    rating = item.communityRating,
                                    unwatchedEpisodeCount = item.getUnwatchedEpisodeCount().takeIf { it > 0 },
                                    onCardClick = { onItemClick(item.id.toString()) },
                                    posterWidth = ImmersiveDimens.LibraryGridCellWidth,
                                    posterHeight = ImmersiveDimens.LibraryGridCellHeight,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating header: back button + sort dropdown
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
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
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(id = R.string.sort),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp).size(24.dp),
                    )
                }

                if (showSortMenu) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 56.dp),
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            sortOptions.forEachIndexed { index, option ->
                                Text(
                                    text = stringResource(id = option.labelRes),
                                    color = if (index == selectedSortIndex) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier
                                        .clickable {
                                            onSortSelected(index)
                                            showSortMenu = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sticky header for the density-pass library grid (item 7 of the redesign spec): library name,
 * item count, and a single-select filter chip row (reusing [FilterType]/[applyFilter], the same
 * pattern as [LibraryFilterRow] elsewhere in this app).
 */
@Composable
private fun LibraryStickyHeader(
    libraryName: String,
    itemCount: Int,
    selectedFilter: FilterType,
    onFilterSelected: (FilterType) -> Unit,
    themeColor: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (libraryName.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = libraryName,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$itemCount item${if (itemCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterType.getAllFilters().forEach { filter ->
                item {
                    FilterChip(
                        onClick = { onFilterSelected(filter) },
                        label = { Text(filter.displayName) },
                        selected = selectedFilter == filter,
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            selectedContainerColor = themeColor.copy(alpha = 0.18f),
                            selectedLabelColor = themeColor,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Builds a human-readable subtitle for a library item.
 * For TV shows this shows a year range (e.g. "2020–Present" or "2020–2024").
 * For movies and other items this falls back to the production year.
 */
private fun buildItemSubtitle(item: org.jellyfin.sdk.model.api.BaseItemDto): String {
    val startYear = item.productionYear ?: return ""
    val endYear = item.endDate?.year
    return when {
        item.status == "Continuing" -> "$startYear\u2013Present"
        endYear != null && endYear != startYear -> "$startYear\u2013$endYear"
        else -> startYear.toString()
    }
}
