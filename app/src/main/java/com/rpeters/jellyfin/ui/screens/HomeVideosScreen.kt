package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.rpeters.jellyfin.ui.components.ExpressiveErrorState
import com.rpeters.jellyfin.ui.components.ExpressiveFullScreenLoading
import com.rpeters.jellyfin.ui.components.ExpressivePullToRefreshBox
import com.rpeters.jellyfin.ui.components.ExpressiveSimpleEmptyState
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBar
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBarAction
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBarMenuAction
import com.rpeters.jellyfin.ui.components.MediaItemActionsSheet
import com.rpeters.jellyfin.ui.theme.MusicGreen
import com.rpeters.jellyfin.ui.theme.MotionTokens
import com.rpeters.jellyfin.ui.theme.PhotoYellow
import com.rpeters.jellyfin.ui.utils.MediaPlayerUtils
import com.rpeters.jellyfin.ui.viewmodel.LibraryActionsPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

@OptInAppExperimentalApis
@Composable
fun HomeVideosScreen(
    onBackClick: () -> Unit = {},
    onItemClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
    libraryActionsPreferencesViewModel: LibraryActionsPreferencesViewModel = hiltViewModel(),
) {
    if (BuildConfig.DEBUG) {
        SecureLogger.d("HomeVideosScreen", "HomeVideosScreen started")
    }
    val appState by viewModel.appState.collectAsState()
    val libraryActionPrefs by libraryActionsPreferencesViewModel.preferences.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf(HomeVideoFilter.ALL) }
    var sortOrder by remember { mutableStateOf(HomeVideoSortOrder.getDefault()) }
    var viewMode by remember { mutableStateOf(HomeVideoViewMode.GRID) }
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

    // Find all Home Videos libraries (there might be more than one)
    val homeVideosLibraries = remember(appState.libraries) {
        appState.libraries.filter { library ->
            library.collectionType == org.jellyfin.sdk.model.api.CollectionType.HOMEVIDEOS
        }
    }

    // Load home videos if we have libraries but no data yet
    LaunchedEffect(homeVideosLibraries) {
        if (homeVideosLibraries.isNotEmpty()) {
            homeVideosLibraries.forEach { library ->
                val libraryId = library.id
                val currentItems = appState.itemsByLibrary[libraryId.toString()] ?: emptyList()
                if (currentItems.isEmpty()) {
                    if (BuildConfig.DEBUG) {
                        SecureLogger.d(
                            "HomeVideosScreen",
                            "Loading home videos for library: $libraryId",
                        )
                    }
                    viewModel.loadHomeVideos(libraryId.toString())
                }
            }
        }
    }

    // Get home videos items from all libraries
    val homeVideosItems = remember(appState.itemsByLibrary, homeVideosLibraries) {
        val allItems = mutableListOf<BaseItemDto>()

        homeVideosLibraries.forEach { library ->
            val libraryId = library.id
            val items = appState.itemsByLibrary[libraryId.toString()] ?: emptyList()
            if (BuildConfig.DEBUG) {
                SecureLogger.d(
                    "HomeVideosScreen",
                    "Found ${items.size} items in library: $libraryId",
                )
                if (items.isNotEmpty()) {
                    val typeBreakdown = items.groupBy { it.type.name }.mapValues { it.value.size }
                    SecureLogger.d(
                        "HomeVideosScreen",
                        "Item types in library $libraryId: $typeBreakdown",
                    )
                }
            }
            allItems.addAll(items)
        }

        // Filter for videos only
        val filteredItems = allItems.filter { item ->
            item.type == BaseItemKind.VIDEO || item.type == BaseItemKind.MOVIE
        }

        if (BuildConfig.DEBUG) {
            SecureLogger.d(
                "HomeVideosScreen",
                "Total filtered home video items: ${filteredItems.size}",
            )
        }

        filteredItems
    }

    // Apply filtering and sorting
    val filteredAndSortedHomeVideos = remember(homeVideosItems, selectedFilter, sortOrder) {
        filterAndSortHomeVideos(
            homeVideos = homeVideosItems,
            selectedFilter = selectedFilter,
            sortOrder = sortOrder,
        )
    }

    val homeVideosPaginationStates = remember(appState.libraryPaginationState, homeVideosLibraries) {
        homeVideosLibraries.associate { library ->
            val libraryId = library.id.toString()
            libraryId to appState.libraryPaginationState[libraryId]
        }
    }

    val hasMoreHomeVideos = remember(homeVideosPaginationStates) {
        homeVideosPaginationStates.values.any { it?.hasMore == true }
    }

    val isLoadingMoreHomeVideos = remember(homeVideosPaginationStates) {
        homeVideosPaginationStates.values.any { it?.isLoadingMore == true }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.home_videos),
                actions = {
                    // View mode segmented button
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        SegmentedButton(
                            selected = viewMode == HomeVideoViewMode.GRID,
                            onClick = { viewMode = HomeVideoViewMode.GRID },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = viewMode == HomeVideoViewMode.GRID) {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = "Grid view",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                        ) {
                            Text("Grid", style = MaterialTheme.typography.labelMedium)
                        }
                        SegmentedButton(
                            selected = viewMode == HomeVideoViewMode.LIST,
                            onClick = { viewMode = HomeVideoViewMode.LIST },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = viewMode == HomeVideoViewMode.LIST) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ViewList,
                                        contentDescription = "List view",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                        ) {
                            Text("List", style = MaterialTheme.typography.labelMedium)
                        }
                        SegmentedButton(
                            selected = viewMode == HomeVideoViewMode.CAROUSEL,
                            onClick = { viewMode = HomeVideoViewMode.CAROUSEL },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = viewMode == HomeVideoViewMode.CAROUSEL) {
                                    Icon(
                                        imageVector = Icons.Default.ViewCarousel,
                                        contentDescription = "Carousel view",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                        ) {
                            Text("Carousel", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    // Sort menu
                    ExpressiveTopAppBarMenuAction(
                        icon = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort",
                        expanded = showSortMenu,
                        onExpandedChange = { showSortMenu = it },
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

                    // Refresh button
                    ExpressiveTopAppBarAction(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        onClick = { viewModel.loadInitialData() },
                        tint = MusicGreen,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        ExpressivePullToRefreshBox(
            isRefreshing = appState.isLoading,
            onRefresh = { viewModel.loadInitialData() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            indicatorColor = PhotoYellow,
            indicatorSize = 52.dp,
            useWavyIndicator = true,
        ) {
            // Content with Expressive animations
            val contentState = when {
                appState.isLoading -> HomeVideoContentState.LOADING
                appState.errorMessage != null -> HomeVideoContentState.ERROR
                filteredAndSortedHomeVideos.isEmpty() -> HomeVideoContentState.EMPTY
                else -> HomeVideoContentState.CONTENT
            }

            AnimatedContent(
                targetState = contentState,
                transitionSpec = {
                    fadeIn(MotionTokens.expressiveEnter) + slideInVertically { it / 4 } togetherWith
                        fadeOut(MotionTokens.expressiveExit) + slideOutVertically { -it / 4 }
                },
                label = "home_videos_content",
            ) { state ->
                when (state) {
                    HomeVideoContentState.LOADING -> {
                        HomeVideosLoadingContent(
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    HomeVideoContentState.ERROR -> {
                        ExpressiveErrorState(
                            title = "Error Loading Home Videos",
                            message = appState.errorMessage ?: stringResource(R.string.unknown_error),
                            icon = Icons.Default.Photo,
                            onRetry = { viewModel.loadInitialData() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    HomeVideoContentState.EMPTY -> {
                        ExpressiveSimpleEmptyState(
                            icon = Icons.Default.Photo,
                            title = stringResource(R.string.no_home_videos_found),
                            subtitle = stringResource(R.string.adjust_home_videos_filters_hint),
                            iconTint = PhotoYellow,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    HomeVideoContentState.CONTENT -> {
                        HomeVideosContent(
                            homeVideos = filteredAndSortedHomeVideos,
                            viewMode = viewMode,
                            getImageUrl = { item -> viewModel.getImageUrl(item) },
                            getBackdropUrl = { item -> viewModel.getBackdropUrl(item) },
                            onHomeVideoClick = { id -> onItemClick?.invoke(id) },
                            onHomeVideoLongPress = handleItemLongPress,
                            isLoadingMore = isLoadingMoreHomeVideos,
                            hasMoreItems = hasMoreHomeVideos,
                            onLoadMore = { viewModel.loadMoreHomeVideos(homeVideosLibraries) },
                        )
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

// Content state enum for animated transitions
enum class HomeVideoContentState {
    LOADING,
    ERROR,
    EMPTY,
    CONTENT,
}
