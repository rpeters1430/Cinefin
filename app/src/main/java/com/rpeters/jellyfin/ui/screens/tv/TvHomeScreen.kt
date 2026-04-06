package com.rpeters.jellyfin.ui.screens.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.*
import com.rpeters.jellyfin.ui.components.tv.*
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.image.rememberCoilSize
import com.rpeters.jellyfin.ui.theme.CinefinTvTheme
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import com.rpeters.jellyfin.ui.tv.*

import com.rpeters.jellyfin.ui.components.tv.TvContentCard
import com.rpeters.jellyfin.ui.components.tv.TvPlaybackProgressBar

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHomeScreen(
    onItemSelect: (String) -> Unit,
    onLibrarySelect: (String) -> Unit,
    onSearch: () -> Unit = {},
    onPlay: (itemId: String, itemName: String, startMs: Long) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
    screenKey: String = "tv_home",
) {
    val appState by viewModel.appState.collectAsState()
    var focusedBackdrop by remember { mutableStateOf<String?>(null) }
    val focusManager = rememberTvFocusManager()
    val localFocusManager = LocalFocusManager.current
    val tvLayout = CinefinTvTheme.layout
    
    // Initial focus requester for the first row's first item
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.loadInitialData(forceRefresh = false)
        viewModel.loadFavorites()
    }

    // Request initial focus when data is loaded
    firstItemFocusRequester.requestInitialFocus(
        condition = appState.libraries.isNotEmpty() || appState.continueWatching.isNotEmpty(),
        delayMs = 800 // Increased delay to ensure layout is stable
    )

    TvScreenFocusScope(screenKey = screenKey, focusManager = focusManager) {
        Box(modifier = modifier.fillMaxSize()) {
            // Background layer
            TvImmersiveBackground(backdropUrl = focusedBackdrop)

            // Loading state
            if (appState.isLoading && appState.libraries.isEmpty()) {
                TvFullScreenLoading(message = "Syncing your library...")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = tvLayout.screenTopPadding,
                        bottom = tvLayout.contentBottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(tvLayout.sectionSpacing),
                ) {
                    // Welcome Header
                    item {
                        TvHomeHeader(
                            modifier = Modifier
                                .padding(
                                    start = tvLayout.screenHorizontalPadding,
                                    bottom = 8.dp
                                )
                        )
                    }

                    // Libraries Row
                    if (appState.libraries.isNotEmpty()) {
                        item {
                            TvSectionRow(
                                title = "Your Libraries",
                                sectionPadding = tvLayout.screenHorizontalPadding,
                                items = appState.libraries,
                                focusManager = focusManager,
                                carouselId = "libraries_row",
                                focusRequester = firstItemFocusRequester,
                                onExitLeft = { 
                                    localFocusManager.moveFocus(FocusDirection.Left)
                                    true 
                                },
                                onItemFocus = { focusedBackdrop = viewModel.getBackdropUrl(it) },
                                onItemClick = { onLibrarySelect(it.id.toString()) },
                                content = { library, isFocused, focusRequester ->
                                    LibraryCard(
                                        library = library,
                                        isFocused = isFocused,
                                        focusRequester = focusRequester,
                                        onClick = { onLibrarySelect(library.id.toString()) }
                                    )
                                }
                            )
                        }
                    }

                    // Continue Watching
                    if (appState.continueWatching.isNotEmpty()) {
                        item {
                            TvSectionRow(
                                title = "Continue Watching",
                                sectionPadding = tvLayout.screenHorizontalPadding,
                                items = appState.continueWatching.take(10),
                                focusManager = focusManager,
                                carouselId = "continue_watching_row",
                                onExitLeft = { 
                                    localFocusManager.moveFocus(FocusDirection.Left)
                                    true 
                                },
                                onItemFocus = { focusedBackdrop = viewModel.getBackdropUrl(it) },
                                onItemClick = { onItemSelect(it.id.toString()) },
                                content = { item, isFocused, focusRequester ->
                                    TvContentCard(
                                        item = item,
                                        onItemFocus = { focusedBackdrop = viewModel.getBackdropUrl(item) },
                                        onItemSelect = { onItemSelect(item.id.toString()) },
                                        getImageUrl = { viewModel.getSeriesImageUrl(it) ?: viewModel.getImageUrl(it) },
                                        getSeriesImageUrl = viewModel::getSeriesImageUrl,
                                        focusRequester = focusRequester,
                                        isFocused = isFocused,
                                        posterWidth = 260.dp,
                                        posterHeight = 146.dp
                                    )
                                }
                            )
                        }
                    }

                    // Recently Added Movies
                    val recentMovies = appState.recentlyAddedByTypes[BaseItemKind.MOVIE.name].orEmpty()
                    if (recentMovies.isNotEmpty()) {
                        item {
                            TvSectionRow(
                                title = "Recently Added Movies",
                                sectionPadding = tvLayout.screenHorizontalPadding,
                                items = recentMovies.take(15),
                                focusManager = focusManager,
                                carouselId = "recent_movies_row",
                                onExitLeft = { 
                                    localFocusManager.moveFocus(FocusDirection.Left)
                                    true 
                                },
                                onItemFocus = { focusedBackdrop = viewModel.getBackdropUrl(it) },
                                onItemClick = { onItemSelect(it.id.toString()) },
                                content = { item, isFocused, focusRequester ->
                                    TvContentCard(
                                        item = item,
                                        onItemFocus = { focusedBackdrop = viewModel.getBackdropUrl(item) },
                                        onItemSelect = { onItemSelect(item.id.toString()) },
                                        getImageUrl = viewModel::getImageUrl,
                                        getSeriesImageUrl = viewModel::getSeriesImageUrl,
                                        focusRequester = focusRequester,
                                        isFocused = isFocused,
                                        posterWidth = 150.dp,
                                        posterHeight = 225.dp
                                    )
                                }
                            )
                        }
                    }

                    // Recently Added Shows
                    val recentShows = appState.recentlyAddedByTypes[BaseItemKind.SERIES.name].orEmpty()
                    if (recentShows.isNotEmpty()) {
                        item {
                            TvSectionRow(
                                title = "Latest TV Shows",
                                sectionPadding = tvLayout.screenHorizontalPadding,
                                items = recentShows.take(15),
                                focusManager = focusManager,
                                carouselId = "recent_shows_row",
                                onExitLeft = { 
                                    localFocusManager.moveFocus(FocusDirection.Left)
                                    true 
                                },
                                onItemFocus = { focusedBackdrop = viewModel.getBackdropUrl(it) },
                                onItemClick = { onItemSelect(it.id.toString()) },
                                content = { item, isFocused, focusRequester ->
                                    TvContentCard(
                                        item = item,
                                        onItemFocus = { focusedBackdrop = viewModel.getBackdropUrl(item) },
                                        onItemSelect = { onItemSelect(item.id.toString()) },
                                        getImageUrl = viewModel::getImageUrl,
                                        getSeriesImageUrl = viewModel::getSeriesImageUrl,
                                        focusRequester = focusRequester,
                                        isFocused = isFocused,
                                        posterWidth = 150.dp,
                                        posterHeight = 225.dp
                                    )
                                }
                            )
                        }
                    }
                    
                    // Stuff / Home Videos
                    val recentStuff = appState.recentlyAddedByTypes[BaseItemKind.VIDEO.name].orEmpty()
                    if (recentStuff.isNotEmpty()) {
                        item {
                            TvSectionRow(
                                title = "Recent Stuff",
                                sectionPadding = tvLayout.screenHorizontalPadding,
                                items = recentStuff.take(15),
                                focusManager = focusManager,
                                carouselId = "recent_stuff_row",
                                onExitLeft = { 
                                    localFocusManager.moveFocus(FocusDirection.Left)
                                    true 
                                },
                                onItemFocus = { focusedBackdrop = viewModel.getBackdropUrl(it) },
                                onItemClick = { onItemSelect(it.id.toString()) },
                                content = { item, isFocused, focusRequester ->
                                    TvContentCard(
                                        item = item,
                                        onItemFocus = { focusedBackdrop = viewModel.getBackdropUrl(item) },
                                        onItemSelect = { onItemSelect(item.id.toString()) },
                                        getImageUrl = viewModel::getImageUrl,
                                        getSeriesImageUrl = viewModel::getSeriesImageUrl,
                                        focusRequester = focusRequester,
                                        isFocused = isFocused,
                                        posterWidth = 240.dp,
                                        posterHeight = 135.dp
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSectionRow(
    title: String,
    sectionPadding: Dp,
    items: List<BaseItemDto>,
    focusManager: TvFocusManager,
    carouselId: String,
    onItemFocus: (BaseItemDto) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    focusRequester: FocusRequester? = null,
    onExitLeft: (() -> Boolean)? = null,
    content: @Composable (BaseItemDto, Boolean, FocusRequester) -> Unit
) {
    val lazyListState = rememberLazyListState()
    
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = sectionPadding),
            color = Color.White.copy(alpha = 0.9f),
        )

        TvFocusableCarousel(
            carouselId = carouselId,
            focusManager = focusManager,
            lazyListState = lazyListState,
            itemCount = items.size,
            itemKeys = items.map { it.id.toString() },
            focusRequester = focusRequester,
            onExitLeft = onExitLeft,
        ) { focusModifier, wrapperFocusedIndex, itemFocusRequesters ->
            LazyRow(
                state = lazyListState,
                modifier = focusModifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = sectionPadding),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                itemsIndexed(items, key = { _, it -> it.id.toString() }) { index, item ->
                    val isItemFocused = index == wrapperFocusedIndex
                    content(item, isItemFocused, itemFocusRequesters[index])
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LibraryCard(
    library: BaseItemDto,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .size(160.dp, 80.dp)
            .focusRequester(focusRequester),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        ),
        scale = CardDefaults.scale(focusedScale = 1.1f),
        glow = CardDefaults.glow(
            focusedGlow = Glow(
                elevationColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                elevation = 16.dp
            )
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = library.name ?: "Library",
                style = MaterialTheme.typography.titleMedium,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
fun TvHomeHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Welcome to Cinefin",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = "Browse your media collection",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
