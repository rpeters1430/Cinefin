package com.rpeters.jellyfin.ui.screens.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.adaptive.rememberAdaptiveLayoutConfig
import com.rpeters.jellyfin.ui.adaptive.rememberWindowLayoutInfo
import com.rpeters.jellyfin.ui.components.tv.TvContentCard
import com.rpeters.jellyfin.ui.components.tv.TvEmptyState
import com.rpeters.jellyfin.ui.components.tv.TvFullScreenLoading
import com.rpeters.jellyfin.ui.components.tv.TvImmersiveBackground
import com.rpeters.jellyfin.ui.tv.TvFocusableGrid
import com.rpeters.jellyfin.ui.tv.TvScreenFocusScope
import com.rpeters.jellyfin.ui.tv.rememberTvFocusManager
import com.rpeters.jellyfin.ui.tv.tvKeyboardHandler
import com.rpeters.jellyfin.ui.screens.LibraryType
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import kotlin.math.min
import org.jellyfin.sdk.model.api.CollectionType
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

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
    val windowSizeClass = calculateWindowSizeClass(context as android.app.Activity)
    val windowLayoutInfo = rememberWindowLayoutInfo()
    val layoutConfig = rememberAdaptiveLayoutConfig(windowSizeClass, windowLayoutInfo)
    val configuration = LocalConfiguration.current
    val localFocusManager = LocalFocusManager.current
    val focusManager = rememberTvFocusManager()
    val appState by viewModel.appState.collectAsState()
    val library = appState.libraries.firstOrNull { it.id.toString() == libraryId }
        ?: when (libraryId) {
            "movies"     -> appState.libraries.firstOrNull {
                it.collectionType == CollectionType.MOVIES
            }
            "tvshows"    -> appState.libraries.firstOrNull {
                it.collectionType == CollectionType.TVSHOWS
            }
            "music"      -> appState.libraries.firstOrNull {
                it.collectionType == CollectionType.MUSIC
            }
            "homevideos" -> appState.libraries.firstOrNull {
                it.collectionType == CollectionType.HOMEVIDEOS ||
                    it.collectionType?.name == "PHOTOS"
            } ?: appState.libraries.firstOrNull { it.collectionType == null }
            else -> null
        }
    
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
                    .padding(horizontal = 56.dp)
                    .padding(top = 48.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                TvText(
                    text = library?.name ?: when (libraryId) {
                        "movies"    -> "Movies"
                        "tvshows"   -> "TV Shows"
                        "music"     -> "Music"
                        "homevideos" -> "Stuff"
                        "favorites" -> "Favorites"
                        else        -> "Library"
                    },
                    style = TvMaterialTheme.typography.displaySmall,
                    color = Color.White,
                    modifier = Modifier.onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft) {
                            localFocusManager.moveFocus(FocusDirection.Left)
                            true
                        } else {
                            false
                        }
                    }
                )

                if (isLibraryLoading && items.isEmpty()) {
                    TvFullScreenLoading(message = "Loading items...")
                } else if (items.isEmpty()) {
                    TvEmptyState(
                        title = "No Items Found",
                        message = "This library appears to be empty.",
                        onAction = { viewModel.loadInitialData(forceRefresh = true) },
                        actionText = "Refresh",
                    )
                } else {
                    val gridState = rememberLazyGridState()
                    val horizontalScreenPadding = 56.dp * 2
                    val gridSpacing = 24.dp
                    val availableWidth = (configuration.screenWidthDp.dp - horizontalScreenPadding).coerceAtLeast(0.dp)
                    val maxColumnsForCardWidth =
                        ((availableWidth + gridSpacing) / (layoutConfig.carouselItemWidth + gridSpacing))
                            .toInt()
                            .coerceAtLeast(2)
                    val columns = min(layoutConfig.gridColumns.coerceAtLeast(4), maxColumnsForCardWidth)

                    LaunchedEffect(gridState, paginationKey, hasMoreItems, isLoadingMoreItems, items.size) {
                        val key = paginationKey ?: return@LaunchedEffect
                        if (key == "favorites") return@LaunchedEffect

                        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                            .collect { lastVisibleIndex ->
                                val nearEnd = lastVisibleIndex >= (items.lastIndex - 8).coerceAtLeast(0)
                                if (nearEnd && hasMoreItems && !isLoadingMoreItems) {
                                    viewModel.loadMoreLibraryItems(key)
                                }
                            }
                    }

                    TvFocusableGrid(
                        gridId = "${screenKey}_grid",
                        focusManager = focusManager,
                        lazyGridState = gridState,
                        itemCount = items.size,
                        columnsCount = columns,
                        onExitLeft = {
                            localFocusManager.moveFocus(FocusDirection.Left)
                        },
                        onFocusChanged = { isFocused, index ->
                            if (isFocused && index in items.indices) {
                                focusedBackdrop = viewModel.getBackdropUrl(items[index])
                            }
                        }
                    ) { focusModifier, wrapperFocusedIndex, itemFocusRequesters ->
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            state = gridState,
                            modifier = focusModifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 56.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            itemsIndexed(
                                items = items,
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
                                    posterWidth = layoutConfig.carouselItemWidth,
                                    posterHeight = layoutConfig.carouselItemHeight,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
