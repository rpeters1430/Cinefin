package com.rpeters.jellyfin.ui.screens.tv.adaptive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import com.rpeters.jellyfin.ui.adaptive.AdaptiveLayoutConfig
import com.rpeters.jellyfin.ui.components.tv.TvContentCard
import com.rpeters.jellyfin.ui.components.tv.TvContentCarousel
import com.rpeters.jellyfin.ui.components.tv.TvSkeletonCarousel
import com.rpeters.jellyfin.ui.screens.tv.TvLibrariesSection
import com.rpeters.jellyfin.ui.tv.TvFocusManager
import com.rpeters.jellyfin.ui.tv.TvFocusableGrid
import org.jellyfin.sdk.model.api.BaseItemDto
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

/**
 * Shared representation for TV home content sections.
 */
data class TvHomeMediaSection(
    val id: String,
    val title: String,
    val items: List<BaseItemDto>,
    val isLoading: Boolean,
)

/**
 * TV-first carousel layout that keeps compatibility with existing focus behavior.
 */
@Composable
fun TvCarouselHomeContent(
    layoutConfig: AdaptiveLayoutConfig,
    sections: List<TvHomeMediaSection>,
    focusManager: TvFocusManager,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    onItemSelect: (BaseItemDto) -> Unit,
    libraries: List<BaseItemDto>,
    onLibrarySelect: (String) -> Unit,
    isLoadingLibraries: Boolean,
    initialFocusRequester: FocusRequester,
    firstSectionId: String?,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(layoutConfig.contentPadding)
            .padding(bottom = layoutConfig.spacing),
        verticalArrangement = Arrangement.spacedBy(layoutConfig.spacing),
    ) {
        header()

        sections.forEach { section ->
            TvContentCardSection(
                section = section,
                layoutConfig = layoutConfig,
                focusManager = focusManager,
                onItemSelect = onItemSelect,
                initialFocusRequester = initialFocusRequester.takeIf { firstSectionId == section.id },
            )
        }

        if (libraries.isNotEmpty()) {
            TvLibrariesSection(
                libraries = libraries,
                onLibrarySelect = onLibrarySelect,
                modifier = Modifier.fillMaxWidth(),
                isLoading = isLoadingLibraries,
            )
        }
    }
}

@Composable
private fun TvContentCardSection(
    section: TvHomeMediaSection,
    layoutConfig: AdaptiveLayoutConfig,
    focusManager: TvFocusManager,
    onItemSelect: (BaseItemDto) -> Unit,
    initialFocusRequester: FocusRequester?,
) {
    if (section.isLoading && section.items.isEmpty()) {
        TvSkeletonCarousel(
            title = section.title,
            itemCount = layoutConfig.gridColumns * layoutConfig.maxRows,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    if (section.items.isEmpty()) {
        return
    }

    val carouselId = focusManager.getCarouselId(section.id)

    TvContentCarousel(
        items = section.items,
        title = section.title,
        layoutConfig = layoutConfig,
        focusManager = focusManager,
        onItemSelect = onItemSelect,
        carouselId = carouselId,
        isLoading = section.isLoading,
        focusRequester = initialFocusRequester,
    )
}

/**
 * Tablet and foldable-first layout using multi-column grids with a simultaneous detail pane.
 */
@Composable
fun TabletHomeContent(
    layoutConfig: AdaptiveLayoutConfig,
    sections: List<TvHomeMediaSection>,
    focusManager: TvFocusManager,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    onItemSelect: (BaseItemDto) -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    libraries: List<BaseItemDto>,
    onLibrarySelect: (String) -> Unit,
    isLoadingLibraries: Boolean,
    initialFocusRequester: FocusRequester? = null,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(layoutConfig.contentPadding)
            .padding(bottom = layoutConfig.spacing),
        verticalArrangement = Arrangement.spacedBy(layoutConfig.spacing),
    ) {
        header()

        sections.forEachIndexed { index, section ->
            TabletMediaSection(
                section = section,
                layoutConfig = layoutConfig,
                focusManager = focusManager,
                onItemSelect = onItemSelect,
                getImageUrl = getImageUrl,
                getSeriesImageUrl = getSeriesImageUrl,
                focusRequester = initialFocusRequester.takeIf { index == 0 },
            )
        }

        if (libraries.isNotEmpty()) {
            TvLibrariesSection(
                libraries = libraries,
                onLibrarySelect = onLibrarySelect,
                modifier = Modifier.fillMaxWidth(),
                isLoading = isLoadingLibraries,
            )
        }
    }
}

@Composable
private fun TabletMediaSection(
    section: TvHomeMediaSection,
    layoutConfig: AdaptiveLayoutConfig,
    focusManager: TvFocusManager,
    onItemSelect: (BaseItemDto) -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    focusRequester: FocusRequester?,
) {
    if (section.isLoading && section.items.isEmpty()) {
        TvSkeletonCarousel(
            title = section.title,
            itemCount = layoutConfig.gridColumns * layoutConfig.maxRows,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    if (section.items.isEmpty()) {
        return
    }

    val items = section.items
    val layoutDirection = LocalLayoutDirection.current
    val titlePadding = layoutConfig.headerPadding.calculateStartPadding(layoutDirection)
    val gridState = rememberLazyGridState()
    var focusedIndex by rememberSaveable(section.id, layoutConfig.detailPaneVisibility) {
        mutableIntStateOf(0)
    }

    val carouselId = focusManager.getCarouselId(section.id)
    val columns = layoutConfig.gridColumns.coerceAtLeast(2)
    val detailItem = items.getOrNull(focusedIndex.coerceIn(0, items.lastIndex))

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(layoutConfig.spacing),
    ) {
        TvText(
            text = section.title,
            style = TvMaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = titlePadding),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(layoutConfig.spacing),
            verticalAlignment = Alignment.Top,
        ) {
            TvFocusableGrid(
                gridId = carouselId,
                focusManager = focusManager,
                lazyGridState = gridState,
                itemCount = items.size,
                columnsCount = columns,
                focusRequester = focusRequester,
                onFocusChanged = { isFocused, index ->
                    if (isFocused && index in items.indices) {
                        focusedIndex = index
                    }
                },
            ) { focusModifier ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = focusModifier
                        .weight(0.68f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = layoutConfig.spacing),
                    verticalArrangement = Arrangement.spacedBy(layoutConfig.spacing),
                    horizontalArrangement = Arrangement.spacedBy(layoutConfig.spacing),
                ) {
                    itemsIndexed(items, key = { index, item -> item.id?.toString() ?: index.toString() }) { index, item ->
                        TvContentCard(
                            item = item,
                            onItemFocus = { focusedIndex = index },
                            onItemSelect = { onItemSelect(item) },
                            getImageUrl = getImageUrl,
                            getSeriesImageUrl = getSeriesImageUrl,
                            isFocused = focusedIndex == index,
                            posterWidth = layoutConfig.carouselItemWidth,
                            posterHeight = layoutConfig.carouselItemHeight,
                        )
                    }
                }
            }

            TabletDetailPane(
                item = detailItem,
                layoutConfig = layoutConfig,
            )
        }
    }
}

@Composable
private fun TabletDetailPane(
    item: BaseItemDto?,
    layoutConfig: AdaptiveLayoutConfig,
) {
    Column(
        modifier = Modifier
            .weight(0.32f)
            .fillMaxHeight()
            .padding(PaddingValues(horizontal = layoutConfig.spacing)),
        verticalArrangement = Arrangement.spacedBy(layoutConfig.spacing / 2),
        horizontalAlignment = Alignment.Start,
    ) {
        if (item == null) {
            TvText(
                text = "Select an item to see more details",
                style = TvMaterialTheme.typography.bodyLarge,
                color = TvMaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        TvText(
            text = item.name ?: "Unknown",
            style = TvMaterialTheme.typography.headlineMedium,
            color = TvMaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        item.productionYear?.let { year ->
            TvText(
                text = year.toString(),
                style = TvMaterialTheme.typography.titleMedium,
                color = TvMaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            TvText(
                text = overview,
                style = TvMaterialTheme.typography.bodyMedium,
                color = TvMaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
