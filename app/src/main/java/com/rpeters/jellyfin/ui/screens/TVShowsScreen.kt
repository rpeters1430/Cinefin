package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
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
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.ExpressiveErrorState
import com.rpeters.jellyfin.ui.components.ExpressiveFullScreenLoading
import com.rpeters.jellyfin.ui.components.ExpressivePullToRefreshBox
import com.rpeters.jellyfin.ui.components.ExpressiveSimpleEmptyState
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBar
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBarAction
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBarMenuAction
import com.rpeters.jellyfin.ui.components.MediaItemActionsSheet
import com.rpeters.jellyfin.ui.theme.MotionTokens
import com.rpeters.jellyfin.ui.theme.MusicGreen
import com.rpeters.jellyfin.ui.theme.SeriesBlue
import com.rpeters.jellyfin.ui.utils.MediaPlayerUtils
import com.rpeters.jellyfin.ui.viewmodel.LibraryActionsPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
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
        filterAndSortTvShows(
            tvShowItems = tvShowItems,
            selectedFilter = selectedFilter,
            sortOrder = sortOrder,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(id = R.string.tv_shows),
                actions = {
                    // View mode segmented button
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        SegmentedButton(
                            selected = viewMode == TVShowViewMode.GRID,
                            onClick = { viewMode = TVShowViewMode.GRID },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = viewMode == TVShowViewMode.GRID) {
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
                            selected = viewMode == TVShowViewMode.LIST,
                            onClick = { viewMode = TVShowViewMode.LIST },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = viewMode == TVShowViewMode.LIST) {
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
                            selected = viewMode == TVShowViewMode.CAROUSEL,
                            onClick = { viewMode = TVShowViewMode.CAROUSEL },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = viewMode == TVShowViewMode.CAROUSEL) {
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

                    // Refresh button
                    ExpressiveTopAppBarAction(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        onClick = { viewModel.refreshTVShows() },
                        tint = MusicGreen,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        ExpressivePullToRefreshBox(
            isRefreshing = isLoadingState,
            onRefresh = { viewModel.refreshTVShows() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            indicatorColor = SeriesBlue,
            indicatorSize = 52.dp,
            useWavyIndicator = true,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                TVShowFilters(
                    selectedFilter = selectedFilter,
                    onFilterSelected = { selectedFilter = it },
                )

                // Content with Expressive animations
                val contentState = when {
                    isLoadingState -> TVShowContentState.LOADING
                    appState.errorMessage != null -> TVShowContentState.ERROR
                    filteredAndSortedTVShows.isEmpty() -> TVShowContentState.EMPTY
                    else -> TVShowContentState.CONTENT
                }

                AnimatedContent(
                    targetState = contentState,
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
                                title = "Error Loading TV Shows",
                                message = appState.errorMessage ?: stringResource(R.string.unknown_error),
                                icon = Icons.Default.Tv,
                                onRetry = { viewModel.refreshTVShows() },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        TVShowContentState.EMPTY -> {
                            ExpressiveSimpleEmptyState(
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
}

private fun filterAndSortTvShows(
    tvShowItems: List<BaseItemDto>,
    selectedFilter: TVShowFilter,
    sortOrder: TVShowSortOrder,
): List<BaseItemDto> {
    if (tvShowItems.isEmpty()) return emptyList()

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

    return when (sortOrder) {
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

// Content state enum for animated transitions
enum class TVShowContentState {
    LOADING,
    ERROR,
    EMPTY,
    CONTENT,
}
