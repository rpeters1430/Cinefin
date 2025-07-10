@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.jellyfinandroid.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.jellyfinandroid.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Generic library screen used by multiple library types.
 */
@Composable
fun LibraryTypeScreen(
    libraryType: LibraryType,
    onTVShowClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel()
) {
    val appState by viewModel.appState.collectAsState()
    var viewMode by remember { mutableStateOf(ViewMode.GRID) }
    var selectedFilter by remember { mutableStateOf(FilterType.getDefault()) }

    val filteredItems = remember(appState.allItems, libraryType) {
        appState.allItems.filter { libraryType.itemKinds.contains(it.type) }
    }
    val displayItems = remember(filteredItems, selectedFilter) {
        applyFilter(filteredItems, selectedFilter)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(LibraryScreenDefaults.FilterChipSpacing)
                    ) {
                        Icon(
                            imageVector = libraryType.icon,
                            contentDescription = null,
                            tint = libraryType.color
                        )
                        Text(
                            text = libraryType.displayName,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                },
                actions = {
                    SingleChoiceSegmentedButtonRow {
                        ViewMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index, ViewMode.entries.size),
                                onClick = { viewMode = mode },
                                selected = viewMode == mode,
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = libraryType.color.copy(alpha = LibraryScreenDefaults.ColorAlpha),
                                    activeContentColor = libraryType.color
                                )
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        ViewMode.GRID -> Icons.Default.GridView
                                        ViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList
                                        ViewMode.CAROUSEL -> Icons.Default.ViewCarousel
                                    },
                                    contentDescription = mode.name,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.refreshLibraryItems() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LibraryFilterRow(
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it },
                libraryType = libraryType
            )

            AnimatedVisibility(
                visible = displayItems.isNotEmpty(),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier.fillMaxSize()
            ) {
                when (viewMode) {
                    ViewMode.GRID -> GridContent(
                        items = displayItems,
                        libraryType = libraryType,
                        getImageUrl = { viewModel.getImageUrl(it) },
                        onTVShowClick = onTVShowClick,
                        isLoadingMore = appState.isLoadingMore,
                        hasMoreItems = appState.hasMoreItems,
                        onLoadMore = { viewModel.loadMoreItems() }
                    )
                    ViewMode.LIST -> ListContent(
                        items = displayItems,
                        libraryType = libraryType,
                        getImageUrl = { viewModel.getImageUrl(it) },
                        onTVShowClick = onTVShowClick,
                        isLoadingMore = appState.isLoadingMore,
                        hasMoreItems = appState.hasMoreItems,
                        onLoadMore = { viewModel.loadMoreItems() }
                    )
                    ViewMode.CAROUSEL -> CarouselContent(
                        items = displayItems,
                        libraryType = libraryType,
                        getImageUrl = { viewModel.getImageUrl(it) },
                        onTVShowClick = onTVShowClick
                    )
                }
            }

            if (displayItems.isEmpty() && !appState.isLoading && appState.errorMessage == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No items found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (appState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = libraryType.color)
                }
            }

            appState.errorMessage?.let { error ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(LibraryScreenDefaults.ContentPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GridContent(
    items: List<BaseItemDto>,
    libraryType: LibraryType,
    getImageUrl: (BaseItemDto) -> String?,
    onTVShowClick: ((String) -> Unit)?,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit
) {

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = LibraryScreenDefaults.GridMinItemSize),
        contentPadding = PaddingValues(LibraryScreenDefaults.ContentPadding),
        verticalArrangement = Arrangement.spacedBy(LibraryScreenDefaults.ContentPadding),
        horizontalArrangement = Arrangement.spacedBy(LibraryScreenDefaults.ItemSpacing),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items) { item ->
            LibraryItemCard(
                item = item,
                libraryType = libraryType,
                getImageUrl = getImageUrl,
                onTVShowClick = onTVShowClick,
                isCompact = true
            )
        }
        if (hasMoreItems || isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PaginationFooter(isLoadingMore, hasMoreItems, onLoadMore, libraryType)
            }
        }
    }
}

@Composable
private fun ListContent(
    items: List<BaseItemDto>,
    libraryType: LibraryType,
    getImageUrl: (BaseItemDto) -> String?,
    onTVShowClick: ((String) -> Unit)?,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(LibraryScreenDefaults.ContentPadding),
        verticalArrangement = Arrangement.spacedBy(LibraryScreenDefaults.ItemSpacing),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items) { item ->
            LibraryItemCard(
                item = item,
                libraryType = libraryType,
                getImageUrl = getImageUrl,
                onTVShowClick = onTVShowClick,
                isCompact = false
            )
        }
        if (hasMoreItems || isLoadingMore) {
            item {
                PaginationFooter(isLoadingMore, hasMoreItems, onLoadMore, libraryType)
            }
        }
    }
}

@Composable
private fun CarouselContent(
    items: List<BaseItemDto>,
    libraryType: LibraryType,
    getImageUrl: (BaseItemDto) -> String?,
    onTVShowClick: ((String) -> Unit)?
) {
    val categories = remember(items) { organizeItemsForCarousel(items, libraryType) }
    val carouselStates = remember(categories.size) {
        List(categories.size) { rememberCarouselState { categories[it].items.size } }
    }
    LazyColumn(
        contentPadding = PaddingValues(vertical = LibraryScreenDefaults.ContentPadding),
        verticalArrangement = Arrangement.spacedBy(LibraryScreenDefaults.SectionSpacing),
        modifier = Modifier.fillMaxSize()
    ) {
        items(categories.size) { index ->
            val category = categories[index]
            CarouselSection(
                title = category.title,
                items = category.items,
                carouselState = carouselStates[index],
                libraryType = libraryType,
                getImageUrl = getImageUrl,
                onTVShowClick = onTVShowClick
            )
        }
    }
}

