@file:OptIn(ExperimentalMaterial3Api::class)

package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.MediaItemActionsSheet
import com.rpeters.jellyfin.ui.components.shimmer
import com.rpeters.jellyfin.ui.viewmodel.LibraryActionsPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.utils.getItemKey
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Generic library screen used by multiple library types.
 * ✅ FIX: Uses on-demand loading to prevent double refresh issue
 */
@Composable
fun LibraryTypeScreen(
    libraryType: LibraryType,
    onTVShowClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
    libraryActionsPreferencesViewModel: LibraryActionsPreferencesViewModel = hiltViewModel(),
) {
    val appState by viewModel.appState.collectAsState()
    val libraryActionPrefs by libraryActionsPreferencesViewModel.preferences.collectAsStateWithLifecycle()
    var viewMode by remember { mutableStateOf(ViewMode.GRID) }
    var selectedFilter by remember { mutableStateOf(FilterType.getDefault()) }
    var hasRequestedData by remember(libraryType) { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
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

    // ✅ FIX: Use library-specific data from itemsByLibrary map
    // The remember() must depend on itemsByLibrary since getLibraryTypeData() reads from that map
    val libraryItems = remember(libraryType, appState.itemsByLibrary) {
        viewModel.getLibraryTypeData(libraryType)
    }

    val displayItems = remember(libraryItems, selectedFilter) {
        applyFilter(libraryItems, selectedFilter)
    }

    // ✅ FIX: Load data on-demand when screen is first composed
    LaunchedEffect(libraryType) {
        hasRequestedData = true
        viewModel.loadLibraryTypeData(libraryType, forceRefresh = false)
    }

    val isInitialLoading = (appState.isLoading || !hasRequestedData) && libraryItems.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(LibraryScreenDefaults.FilterChipSpacing),
                    ) {
                        Icon(
                            imageVector = libraryType.icon,
                            contentDescription = null,
                            tint = libraryType.color,
                        )
                        Text(
                            text = libraryType.displayName,
                            style = MaterialTheme.typography.headlineSmall,
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
                                    activeContentColor = libraryType.color,
                                ),
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        ViewMode.GRID -> Icons.Default.GridView
                                        ViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList
                                        ViewMode.CAROUSEL -> Icons.Default.ViewCarousel
                                    },
                                    contentDescription = mode.name,
                                    modifier = Modifier.padding(4.dp),
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        // ✅ FIX: Use library-type specific refresh
                        viewModel.loadLibraryTypeData(libraryType, forceRefresh = true)
                    }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            LibraryFilterRow(
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it },
                libraryType = libraryType,
            )

            when {
                isInitialLoading -> {
                    LibraryTypeLoadingPlaceholder(libraryType = libraryType)
                }
                displayItems.isNotEmpty() -> {
                    when (viewMode) {
                        ViewMode.GRID -> GridContent(
                            items = displayItems,
                            libraryType = libraryType,
                            getImageUrl = { viewModel.getImageUrl(it) },
                            onTVShowClick = onTVShowClick,
                            onItemLongPress = handleItemLongPress,
                            isLoadingMore = appState.isLoadingMore,
                            hasMoreItems = appState.hasMoreItems,
                            onLoadMore = { viewModel.loadMoreItems() },
                        )
                        ViewMode.LIST -> ListContent(
                            items = displayItems,
                            libraryType = libraryType,
                            getImageUrl = { viewModel.getImageUrl(it) },
                            onTVShowClick = onTVShowClick,
                            onItemLongPress = handleItemLongPress,
                            isLoadingMore = appState.isLoadingMore,
                            hasMoreItems = appState.hasMoreItems,
                            onLoadMore = { viewModel.loadMoreItems() },
                        )
                        ViewMode.CAROUSEL -> CarouselContent(
                            items = displayItems,
                            libraryType = libraryType,
                            getImageUrl = { viewModel.getImageUrl(it) },
                            onTVShowClick = onTVShowClick,
                            onItemLongPress = handleItemLongPress,
                        )
                    }
                }
                displayItems.isEmpty() && !appState.isLoading && appState.errorMessage == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No items found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (appState.isLoading && displayItems.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = libraryType.color)
                }
            }

            appState.errorMessage?.let { error ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(LibraryScreenDefaults.ContentPadding),
                        )
                    }
                }
            }
        }
    }

    // Show media actions sheet when item is long-pressed
    if (showManageSheet && selectedItem != null) {
        val item = selectedItem!!
        val itemName = item.name ?: stringResource(id = R.string.unknown)
        val deleteSuccessMessage = stringResource(id = R.string.library_actions_delete_success, itemName)
        val deleteFailureTemplate = stringResource(id = R.string.library_actions_delete_failure, itemName, "%s")
        val refreshRequestedMessage = stringResource(id = R.string.library_actions_refresh_requested)

        MediaItemActionsSheet(
            item = item,
            sheetState = sheetState,
            onDismiss = {
                showManageSheet = false
                selectedItem = null
            },
            onPlay = {
                // TODO: Implement play functionality
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Play functionality coming soon")
                }
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
                            "Failed to refresh metadata: ${message ?: "Unknown error"}"
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

@Composable
private fun LibraryTypeLoadingPlaceholder(libraryType: LibraryType) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = LibraryScreenDefaults.GridMinItemSize),
        contentPadding = PaddingValues(LibraryScreenDefaults.ContentPadding),
        verticalArrangement = Arrangement.spacedBy(LibraryScreenDefaults.ContentPadding),
        horizontalArrangement = Arrangement.spacedBy(LibraryScreenDefaults.ItemSpacing),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(LibraryScreenDefaults.LibraryTypePlaceholderCount) {
            Card(
                modifier = Modifier
                    .height(LibraryScreenDefaults.LibraryTypePlaceholderHeight)
                    .shimmer(),
                colors = CardDefaults.cardColors(
                    containerColor = libraryType.color.copy(alpha = LibraryScreenDefaults.PlaceholderContainerAlpha),
                ),
            ) {}
        }
    }
}

@Composable
private fun GridContent(
    items: List<BaseItemDto>,
    libraryType: LibraryType,
    getImageUrl: (BaseItemDto) -> String?,
    onTVShowClick: ((String) -> Unit)?,
    onItemLongPress: (BaseItemDto) -> Unit,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = LibraryScreenDefaults.GridMinItemSize),
        contentPadding = PaddingValues(LibraryScreenDefaults.ContentPadding),
        verticalArrangement = Arrangement.spacedBy(LibraryScreenDefaults.ContentPadding),
        horizontalArrangement = Arrangement.spacedBy(LibraryScreenDefaults.ItemSpacing),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            items = items,
            key = { item -> item.getItemKey() },
        ) { item ->
            LibraryItemCard(
                item = item,
                libraryType = libraryType,
                getImageUrl = getImageUrl,
                onTVShowClick = onTVShowClick,
                onItemLongPress = onItemLongPress,
                isCompact = true,
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
    onItemLongPress: (BaseItemDto) -> Unit,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(LibraryScreenDefaults.ContentPadding),
        verticalArrangement = Arrangement.spacedBy(LibraryScreenDefaults.ItemSpacing),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            items = items,
            key = { item -> item.getItemKey() },
        ) { item ->
            LibraryItemCard(
                item = item,
                libraryType = libraryType,
                getImageUrl = getImageUrl,
                onTVShowClick = onTVShowClick,
                onItemLongPress = onItemLongPress,
                isCompact = false,
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
    onTVShowClick: ((String) -> Unit)?,
    onItemLongPress: (BaseItemDto) -> Unit,
) {
    val categories = remember(items) { organizeItemsForCarousel(items, libraryType) }
    LazyColumn(
        contentPadding = PaddingValues(vertical = LibraryScreenDefaults.ContentPadding),
        verticalArrangement = Arrangement.spacedBy(LibraryScreenDefaults.SectionSpacing),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(categories.size) { index ->
            val category = categories[index]
            val carouselState = rememberCarouselState { category.items.size }
            CarouselSection(
                title = category.title,
                items = category.items,
                carouselState = carouselState,
                libraryType = libraryType,
                getImageUrl = getImageUrl,
                onTVShowClick = onTVShowClick,
                onItemLongPress = onItemLongPress,
            )
        }
    }
}
