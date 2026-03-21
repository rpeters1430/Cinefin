package com.rpeters.jellyfin.ui.screens.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.adaptive.rememberAdaptiveLayoutConfig
import com.rpeters.jellyfin.ui.adaptive.rememberWindowLayoutInfo
import com.rpeters.jellyfin.ui.components.tv.TvContentCard
import com.rpeters.jellyfin.ui.components.tv.TvEmptyState
import com.rpeters.jellyfin.ui.components.tv.TvFullScreenLoading
import com.rpeters.jellyfin.ui.components.tv.TvImmersiveBackground
import com.rpeters.jellyfin.ui.screens.LibraryType
import com.rpeters.jellyfin.ui.theme.CinefinTvTheme
import com.rpeters.jellyfin.ui.tv.TvFocusableGrid
import com.rpeters.jellyfin.ui.tv.TvScreenFocusScope
import com.rpeters.jellyfin.ui.tv.rememberTvFocusManager
import com.rpeters.jellyfin.ui.tv.tvKeyboardHandler
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.CollectionType
import kotlin.math.min
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.FilterChip
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

@OptIn(ExperimentalTvMaterial3Api::class)
@OptInAppExperimentalApis
@Composable
fun TvLibraryScreen(
    libraryId: String?,
    onItemSelect: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
    screenKey: String = libraryId?.let { "tv_library_$it" } ?: "tv_library_all",
) {
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context as androidx.activity.ComponentActivity)
    val windowLayoutInfo = rememberWindowLayoutInfo()
    val layoutConfig = rememberAdaptiveLayoutConfig(windowSizeClass, windowLayoutInfo)
    val tvLayout = CinefinTvTheme.layout
    val configuration = LocalConfiguration.current
    val localFocusManager = LocalFocusManager.current
    val focusManager = rememberTvFocusManager()
    val appState by viewModel.appState.collectAsState()
    val library = appState.libraries.firstOrNull { it.id.toString() == libraryId }
        ?: when (libraryId) {
            "movies" -> appState.libraries.firstOrNull {
                it.collectionType == CollectionType.MOVIES
            }
            "tvshows" -> appState.libraries.firstOrNull {
                it.collectionType == CollectionType.TVSHOWS
            }
            "music" -> appState.libraries.firstOrNull {
                it.collectionType == CollectionType.MUSIC
            }
            "homevideos" -> appState.libraries.firstOrNull {
                it.collectionType == CollectionType.HOMEVIDEOS ||
                    it.collectionType?.name == "PHOTOS"
            } ?: appState.libraries.firstOrNull { it.collectionType == null }
            else -> null
        }
    val routeConfig = remember(libraryId, library?.name) {
        tvLibraryRouteConfig(libraryId = libraryId, resolvedLibraryName = library?.name)
    }
    var selectedSort by rememberSaveable(screenKey) { mutableStateOf(routeConfig.defaultSort) }
    var selectedFilter by rememberSaveable(screenKey) { mutableStateOf(routeConfig.defaultFilter) }

    var focusedBackdrop by remember { mutableStateOf<String?>(null) }

    // Ensure libraries are loaded first
    LaunchedEffect(appState.libraries, appState.isLoading) {
        if (appState.libraries.isEmpty() && !appState.isLoading) {
            viewModel.loadInitialData()
        }
    }

    // Choose items based on library type
    val items = if (libraryId == "favorites") {
        appState.favorites
    } else {
        appState.itemsByLibrary[library?.id?.toString() ?: libraryId] ?: emptyList()
    }
    val displayItems = remember(items, selectedSort, selectedFilter) {
        items
            .filterBy(selectedFilter)
            .sortBy(selectedSort)
    }
    val paginationKey = library?.id?.toString() ?: libraryId
    val paginationState = paginationKey?.let { appState.libraryPaginationState[it] }
    val hasMoreItems = paginationState?.hasMore == true
    val isLoadingMoreItems = paginationState?.isLoadingMore == true

    // Determine if this specific library is loading
    val isLibraryLoading = when (library?.collectionType) {
        CollectionType.MOVIES -> appState.isLoadingMovies
        CollectionType.TVSHOWS -> appState.isLoadingTVShows
        else -> appState.isLoading
    }

    // Load favorites when navigating to the favorites synthetic route
    LaunchedEffect(libraryId) {
        if (libraryId == "favorites") {
            viewModel.loadFavorites()
        }
    }

    // Trigger on-demand loading
    LaunchedEffect(libraryId, library?.collectionType, appState.libraries) {
        if (appState.libraries.isEmpty()) return@LaunchedEffect

        library?.let { lib ->
            when (lib.collectionType) {
                CollectionType.MOVIES -> viewModel.loadLibraryTypeData(lib, LibraryType.MOVIES)
                CollectionType.TVSHOWS -> viewModel.loadLibraryTypeData(lib, LibraryType.TV_SHOWS)
                CollectionType.MUSIC -> viewModel.loadLibraryTypeData(lib, LibraryType.MUSIC)
                CollectionType.HOMEVIDEOS -> viewModel.loadLibraryTypeData(lib, LibraryType.STUFF)
                else -> viewModel.loadLibraryTypeData(lib, LibraryType.STUFF)
            }
        }
    }

    TvScreenFocusScope(
        screenKey = screenKey,
        focusManager = focusManager,
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .tvKeyboardHandler(
                    focusManager = localFocusManager,
                    onBack = onBack,
                    onSearch = onSearch,
                ),
        ) {
            // Background Layer
            TvImmersiveBackground(backdropUrl = focusedBackdrop)

            // Content Layer
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = tvLayout.screenHorizontalPadding)
                    .padding(top = tvLayout.screenTopPadding),
                verticalArrangement = Arrangement.spacedBy(tvLayout.sectionSpacing),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft) {
                            localFocusManager.moveFocus(FocusDirection.Left)
                            true
                        } else {
                            false
                        }
                    },
                ) {
                    TvText(
                        text = routeConfig.title,
                        style = TvMaterialTheme.typography.displaySmall,
                        color = Color.White,
                    )

                    TvText(
                        text = routeConfig.subtitle,
                        style = TvMaterialTheme.typography.bodyLarge,
                        color = TvMaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (isLibraryLoading && displayItems.isEmpty()) {
                    TvFullScreenLoading(message = routeConfig.loadingMessage)
                } else if (items.isEmpty()) {
                    TvEmptyState(
                        title = routeConfig.emptyTitle,
                        message = routeConfig.emptyMessage,
                        onAction = { viewModel.loadInitialData(forceRefresh = true) },
                        actionText = routeConfig.emptyActionLabel,
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            routeConfig.filterOptions.forEach { filter ->
                                FilterChip(
                                    selected = selectedFilter == filter,
                                    onClick = { selectedFilter = filter },
                                ) {
                                    TvText(filter.label)
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            routeConfig.sortOptions.forEach { sort ->
                                FilterChip(
                                    selected = selectedSort == sort,
                                    onClick = { selectedSort = sort },
                                ) {
                                    TvText(sort.label)
                                }
                            }
                        }
                    }

                    val gridState = rememberLazyGridState()
                    val horizontalScreenPadding = tvLayout.screenHorizontalPadding * 2
                    val gridSpacing = tvLayout.cardSpacing
                    val availableWidth = (configuration.screenWidthDp.dp - horizontalScreenPadding).coerceAtLeast(0.dp)
                    val maxColumnsForCardWidth =
                        ((availableWidth + gridSpacing) / (routeConfig.cardWidth + gridSpacing))
                            .toInt()
                            .coerceAtLeast(2)
                    val columns = min(routeConfig.minColumns, maxColumnsForCardWidth)

                    LaunchedEffect(gridState, paginationKey, hasMoreItems, isLoadingMoreItems, displayItems.size) {
                        val key = paginationKey ?: return@LaunchedEffect
                        if (key == "favorites") return@LaunchedEffect

                        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                            .collect { lastVisibleIndex ->
                                val nearEnd = lastVisibleIndex >= (displayItems.lastIndex - 8).coerceAtLeast(0)
                                if (nearEnd && hasMoreItems && !isLoadingMoreItems) {
                                    viewModel.loadMoreLibraryItems(key)
                                }
                            }
                    }

                    TvFocusableGrid(
                        gridId = "${screenKey}_grid",
                        focusManager = focusManager,
                        lazyGridState = gridState,
                        itemCount = displayItems.size,
                        columnsCount = columns,
                        itemKeys = displayItems.map { it.id.toString() },
                        onExitLeft = {
                            localFocusManager.moveFocus(FocusDirection.Left)
                        },
                        onFocusChanged = { isFocused, index ->
                            if (isFocused && index in displayItems.indices) {
                                focusedBackdrop = viewModel.getBackdropUrl(displayItems[index])
                            }
                        },
                    ) { focusModifier, wrapperFocusedIndex, itemFocusRequesters ->
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            state = gridState,
                            modifier = focusModifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = tvLayout.contentBottomPadding),
                            verticalArrangement = Arrangement.spacedBy(tvLayout.cardSpacing),
                            horizontalArrangement = Arrangement.spacedBy(tvLayout.cardSpacing),
                        ) {
                            itemsIndexed(
                                items = displayItems,
                                key = { _, item -> item.id.toString() },
                            ) { index, item ->
                                TvContentCard(
                                    item = item,
                                    onItemFocus = {
                                        focusedBackdrop = viewModel.getBackdropUrl(item)
                                    },
                                    onItemSelect = { onItemSelect(item.id.toString()) },
                                    getImageUrl = viewModel::getImageUrl,
                                    getSeriesImageUrl = viewModel::getSeriesImageUrl,
                                    focusRequester = itemFocusRequesters[index],
                                    isFocused = wrapperFocusedIndex == index,
                                    posterWidth = routeConfig.cardWidth,
                                    posterHeight = routeConfig.cardHeight,
                                )
                            }

                            if (isLoadingMoreItems) {
                                item(span = { GridItemSpan(columns) }) {
                                    TvText(
                                        text = "Loading more...",
                                        style = TvMaterialTheme.typography.bodyLarge,
                                        color = TvMaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 12.dp),
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

private data class TvLibraryRouteConfig(
    val title: String,
    val subtitle: String,
    val loadingMessage: String,
    val emptyTitle: String,
    val emptyMessage: String,
    val emptyActionLabel: String = "Refresh",
    val cardWidth: Dp,
    val cardHeight: Dp,
    val minColumns: Int,
    val sortOptions: List<TvLibrarySortOption>,
    val defaultSort: TvLibrarySortOption,
    val filterOptions: List<TvLibraryFilterOption>,
    val defaultFilter: TvLibraryFilterOption,
)

private enum class TvLibrarySortOption(val label: String) {
    RECENT("Recent"),
    TITLE_ASC("A-Z"),
    TITLE_DESC("Z-A"),
    YEAR_DESC("Year"),
}

private enum class TvLibraryFilterOption(val label: String) {
    ALL("All"),
    IN_PROGRESS("In Progress"),
    UNWATCHED("Unwatched"),
}

private fun tvLibraryRouteConfig(
    libraryId: String?,
    resolvedLibraryName: String?,
): TvLibraryRouteConfig {
    return when (libraryId) {
        "movies" -> TvLibraryRouteConfig(
            title = resolvedLibraryName ?: "Movies",
            subtitle = "Recent releases, catalog titles, and long-form browsing.",
            loadingMessage = "Loading movies...",
            emptyTitle = "No Movies Found",
            emptyMessage = "Your movie library is empty or still syncing from Jellyfin.",
            cardWidth = 192.dp,
            cardHeight = 288.dp,
            minColumns = 6,
            sortOptions = listOf(TvLibrarySortOption.RECENT, TvLibrarySortOption.TITLE_ASC, TvLibrarySortOption.YEAR_DESC),
            defaultSort = TvLibrarySortOption.RECENT,
            filterOptions = listOf(TvLibraryFilterOption.ALL, TvLibraryFilterOption.IN_PROGRESS, TvLibraryFilterOption.UNWATCHED),
            defaultFilter = TvLibraryFilterOption.ALL,
        )
        "tvshows" -> TvLibraryRouteConfig(
            title = resolvedLibraryName ?: "TV Shows",
            subtitle = "Series-first browsing with quick access to shows worth continuing.",
            loadingMessage = "Loading TV shows...",
            emptyTitle = "No TV Shows Found",
            emptyMessage = "Series will appear here once your TV library is available.",
            cardWidth = 192.dp,
            cardHeight = 288.dp,
            minColumns = 6,
            sortOptions = listOf(TvLibrarySortOption.RECENT, TvLibrarySortOption.TITLE_ASC, TvLibrarySortOption.YEAR_DESC),
            defaultSort = TvLibrarySortOption.RECENT,
            filterOptions = listOf(TvLibraryFilterOption.ALL, TvLibraryFilterOption.IN_PROGRESS, TvLibraryFilterOption.UNWATCHED),
            defaultFilter = TvLibraryFilterOption.ALL,
        )
        "music" -> TvLibraryRouteConfig(
            title = resolvedLibraryName ?: "Music",
            subtitle = "Album-focused browsing for music collections on a TV screen.",
            loadingMessage = "Loading music...",
            emptyTitle = "No Music Found",
            emptyMessage = "Albums and audio items will appear here once your music library syncs.",
            cardWidth = 186.dp,
            cardHeight = 186.dp,
            minColumns = 6,
            sortOptions = listOf(TvLibrarySortOption.TITLE_ASC, TvLibrarySortOption.TITLE_DESC, TvLibrarySortOption.RECENT),
            defaultSort = TvLibrarySortOption.TITLE_ASC,
            filterOptions = listOf(TvLibraryFilterOption.ALL),
            defaultFilter = TvLibraryFilterOption.ALL,
        )
        "homevideos" -> TvLibraryRouteConfig(
            title = resolvedLibraryName ?: "Stuff",
            subtitle = "Home videos and personal media with wider, video-first cards.",
            loadingMessage = "Loading stuff...",
            emptyTitle = "No Stuff Found",
            emptyMessage = "Home videos and personal media will show up here when available.",
            cardWidth = 240.dp,
            cardHeight = 136.dp,
            minColumns = 5,
            sortOptions = listOf(TvLibrarySortOption.RECENT, TvLibrarySortOption.TITLE_ASC, TvLibrarySortOption.YEAR_DESC),
            defaultSort = TvLibrarySortOption.RECENT,
            filterOptions = listOf(TvLibraryFilterOption.ALL, TvLibraryFilterOption.IN_PROGRESS, TvLibraryFilterOption.UNWATCHED),
            defaultFilter = TvLibraryFilterOption.ALL,
        )
        "favorites" -> TvLibraryRouteConfig(
            title = "Favorites",
            subtitle = "Your saved picks across movies, series, music, and personal media.",
            loadingMessage = "Loading favorites...",
            emptyTitle = "No Favorites Yet",
            emptyMessage = "Mark items as favorites to pin them into this TV-friendly collection.",
            cardWidth = 192.dp,
            cardHeight = 288.dp,
            minColumns = 6,
            sortOptions = listOf(TvLibrarySortOption.RECENT, TvLibrarySortOption.TITLE_ASC, TvLibrarySortOption.YEAR_DESC),
            defaultSort = TvLibrarySortOption.RECENT,
            filterOptions = listOf(TvLibraryFilterOption.ALL, TvLibraryFilterOption.IN_PROGRESS, TvLibraryFilterOption.UNWATCHED),
            defaultFilter = TvLibraryFilterOption.ALL,
        )
        else -> TvLibraryRouteConfig(
            title = resolvedLibraryName ?: "Library",
            subtitle = "Browse the full collection for this Jellyfin library.",
            loadingMessage = "Loading items...",
            emptyTitle = "No Items Found",
            emptyMessage = "This library appears to be empty.",
            cardWidth = 192.dp,
            cardHeight = 288.dp,
            minColumns = 6,
            sortOptions = listOf(TvLibrarySortOption.RECENT, TvLibrarySortOption.TITLE_ASC, TvLibrarySortOption.YEAR_DESC),
            defaultSort = TvLibrarySortOption.RECENT,
            filterOptions = listOf(TvLibraryFilterOption.ALL, TvLibraryFilterOption.IN_PROGRESS, TvLibraryFilterOption.UNWATCHED),
            defaultFilter = TvLibraryFilterOption.ALL,
        )
    }
}

private fun List<org.jellyfin.sdk.model.api.BaseItemDto>.filterBy(
    filter: TvLibraryFilterOption,
): List<org.jellyfin.sdk.model.api.BaseItemDto> {
    return when (filter) {
        TvLibraryFilterOption.ALL -> this
        TvLibraryFilterOption.IN_PROGRESS -> filter {
            val playbackTicks = it.userData?.playbackPositionTicks ?: 0L
            val played = it.userData?.played == true
            playbackTicks > 0L && !played
        }
        TvLibraryFilterOption.UNWATCHED -> filter { it.userData?.played != true }
    }
}

private fun List<org.jellyfin.sdk.model.api.BaseItemDto>.sortBy(
    sort: TvLibrarySortOption,
): List<org.jellyfin.sdk.model.api.BaseItemDto> {
    return when (sort) {
        TvLibrarySortOption.RECENT -> sortedByDescending { it.dateCreated ?: it.premiereDate }
        TvLibrarySortOption.TITLE_ASC -> sortedBy { (it.sortName ?: it.name).orEmpty().lowercase() }
        TvLibrarySortOption.TITLE_DESC -> sortedByDescending { (it.sortName ?: it.name).orEmpty().lowercase() }
        TvLibrarySortOption.YEAR_DESC -> sortedByDescending { it.productionYear ?: Int.MIN_VALUE }
    }
}
