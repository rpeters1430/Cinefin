package com.rpeters.jellyfin.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
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
import com.rpeters.jellyfin.ui.components.immersive.ImmersiveCardSize
import com.rpeters.jellyfin.ui.components.immersive.ImmersiveHeroCarousel
import com.rpeters.jellyfin.ui.components.immersive.ImmersiveMediaCard
import com.rpeters.jellyfin.ui.components.immersive.ImmersiveScaffold
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jellyfin.sdk.model.api.BaseItemDto

/** Theme and empty-state configuration for [ImmersiveLibraryBrowserScreen]. */
data class ImmersiveLibraryConfig(
    val themeColor: Color,
    val emptyStateIcon: ImageVector,
    val emptyStateTitle: String,
    val emptyStateSubtitle: String,
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
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = 0.dp,
                                start = 0.dp,
                                end = 0.dp,
                                bottom = 120.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight),
                            horizontalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight),
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

                            gridItems(
                                items = items,
                                key = { it.id.toString() },
                            ) { item ->
                                ImmersiveMediaCard(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    title = item.name ?: "Unknown",
                                    subtitle = item.productionYear?.toString() ?: "",
                                    imageUrl = getImageUrl(item) ?: "",
                                    onCardClick = { onItemClick(item.id.toString()) },
                                    onPlayClick = { onItemClick(item.id.toString()) },
                                    cardSize = ImmersiveCardSize.SMALL,
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

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    sortOptions.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(id = option.labelRes)) },
                            onClick = {
                                onSortSelected(index)
                                showSortMenu = false
                            },
                        )
                    }
                }
            }
        }
    }
}
