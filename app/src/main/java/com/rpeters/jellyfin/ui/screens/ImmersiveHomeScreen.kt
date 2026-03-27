package com.rpeters.jellyfin.ui.screens

import android.app.Activity
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.util.PerformanceMetricsTracker
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.ui.adaptive.rememberAdaptiveLayoutConfig
import com.rpeters.jellyfin.ui.navigation.LocalNavBarVisible
import com.rpeters.jellyfin.ui.components.*
import com.rpeters.jellyfin.ui.components.immersive.*
import com.rpeters.jellyfin.ui.components.immersive.itemSubtitle
import com.rpeters.jellyfin.ui.screens.home.*
import com.rpeters.jellyfin.ui.theme.Dimens
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.ui.utils.MediaPlayerUtils
import com.rpeters.jellyfin.ui.viewmodel.LibraryActionsPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.MainAppState
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.ui.viewmodel.SurfaceCoordinatorViewModel
import com.rpeters.jellyfin.utils.getItemKey
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType

/**
 * Immersive home screen with Netflix/Disney+ inspired design.
 * Features:
 * - Full-screen hero carousel (480dp height on phone)
 * - Auto-hiding navigation bars
 * - Larger media cards (280dp vs 200dp)
 * - Tighter spacing (16dp vs 24dp)
 * - Full-bleed imagery with gradient overlays
 * - Floating action buttons for Search and AI
 */
@OptIn(UnstableApi::class)
@OptInAppExperimentalApis
@Composable
fun ImmersiveHomeScreen(
    appState: MainAppState,
    currentServer: JellyfinServer?,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSearchClick: () -> Unit = {},
    onAiAssistantClick: () -> Unit = {},
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit = {},
    onLibraryClick: (BaseItemDto) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onNowPlayingClick: () -> Unit = {},
    onAiHealthCheck: () -> Unit = {},
    onGenerateViewingMood: () -> Unit = {},
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    viewModel: MainAppViewModel = hiltViewModel(),
    libraryActionsPreferencesViewModel: LibraryActionsPreferencesViewModel = hiltViewModel(),
) {
    val libraryActionPrefs by libraryActionsPreferencesViewModel.preferences.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedItem by remember { mutableStateOf<BaseItemDto?>(null) }
    var showManageSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val managementEnabled = libraryActionPrefs.enableManagementActions
    val managementDisabledMessage = stringResource(id = R.string.library_actions_management_disabled)

    // ✅ Performance: Stabilize internal callbacks
    val handleItemLongPress = remember(managementEnabled, coroutineScope, managementDisabledMessage) {
        {
                item: BaseItemDto ->
            if (managementEnabled) {
                selectedItem = item
                showManageSheet = true
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(message = managementDisabledMessage)
                }
            }
            Unit
        }
    }

    val handlePlay = remember(viewModel, context, coroutineScope) {
        {
                item: BaseItemDto ->
            val streamUrl = viewModel.getStreamUrl(item)
            if (streamUrl != null) {
                MediaPlayerUtils.playMedia(context, streamUrl, item)
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Unable to start playback")
                }
            }
        }
    }

    LaunchedEffect(appState.libraries) {
        appState.libraries.forEach { library ->
            val libraryId = library.id.toString()
            if (appState.itemsByLibrary[libraryId].isNullOrEmpty()) {
                library.toLibraryTypeOrNull()?.let { libraryType ->
                    viewModel.loadLibraryTypeData(library = library, libraryType = libraryType)
                }
            }
        }
    }

    // Calculate window size class for adaptive layout
    val windowSizeClass = calculateWindowSizeClass(activity = context as Activity)
    val adaptiveConfig = rememberAdaptiveLayoutConfig(windowSizeClass)

    // Track scroll state for auto-hiding navigation
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    
    // Use hero height as threshold to avoid flickering within hero
    val topBarVisible = if (adaptiveConfig.isTablet) {
        rememberAutoHideTopBarVisible(
            gridState = gridState,
            nearTopOffsetPx = with(LocalDensity.current) { ImmersiveDimens.HeroHeightPhone.toPx().toInt() },
        )
    } else {
        rememberAutoHideTopBarVisible(
            listState = listState,
            nearTopOffsetPx = with(LocalDensity.current) { ImmersiveDimens.HeroHeightPhone.toPx().toInt() },
        )
    }

    // Drive global nav bar visibility from the same scroll state as the top bar.
    val globalNavBarVisible = LocalNavBarVisible.current
    LaunchedEffect(topBarVisible) {
        globalNavBarVisible.value = topBarVisible
    }

    Box(modifier = modifier.fillMaxSize()) {
        ImmersiveScaffold(
            // No top bar title, but we pass the visibility state for consistent behavior
            topBarVisible = topBarVisible,
            topBarTitle = "",
            topBarTranslucent = false,
            // ...
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            overlayContent = {
                // Floating settings icon based on scroll direction
                androidx.compose.animation.AnimatedVisibility(
                    visible = topBarVisible,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Surface(
                        onClick = onSettingsClick,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(id = R.string.settings),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp).size(24.dp),
                        )
                    }
                }

                // Floating Search and AI Action Buttons
                androidx.compose.animation.AnimatedVisibility(
                    visible = topBarVisible,
                    enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 120.dp), // Height above mini player
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        FloatingActionButton(
                            onClick = onAiAssistantClick,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = stringResource(id = R.string.ai_assistant),
                            )
                        }

                        FloatingActionButton(
                            onClick = onSearchClick,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(id = R.string.search),
                            )
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                )
            },
        ) { paddingValues ->
            PerformanceMetricsTracker(
                enabled = com.rpeters.jellyfin.BuildConfig.DEBUG,
                intervalMs = 30000,
            )

            LaunchedEffect(appState.continueWatching.size, appState.recentlyAdded.size) {
                if (appState.viewingMood == null &&
                    !appState.isLoadingViewingMood &&
                    (appState.continueWatching.isNotEmpty() || appState.recentlyAdded.isNotEmpty())
                ) {
                    viewModel.generateViewingMood()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                ImmersiveHomeContent(
                    appState = appState,
                    currentServer = currentServer,
                    onRefresh = onRefresh,
                    getImageUrl = getImageUrl,
                    getBackdropUrl = getBackdropUrl,
                    getSeriesImageUrl = getSeriesImageUrl,
                    onItemClick = onItemClick,
                    onItemLongPress = handleItemLongPress,
                    onLibraryClick = onLibraryClick,
                    onGenerateViewingMood = onGenerateViewingMood,
                    gridState = gridState,
                    listState = listState,
                    windowSizeClass = windowSizeClass,
                    adaptiveConfig = adaptiveConfig,
                    contentPadding = paddingValues,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    selectedItem?.let { item ->
        if (showManageSheet) {
            val itemName = item.name ?: stringResource(id = R.string.unknown)
            val deleteSuccessMessage = stringResource(id = R.string.library_actions_delete_success, itemName)
            val deleteFailureTemplate = stringResource(id = R.string.library_actions_delete_failure, itemName, "%s")
            val refreshRequestedMessage = stringResource(id = R.string.library_actions_refresh_requested)
            val unknownErrorMessage = stringResource(id = R.string.unknown_error)

            // ✅ Performance: Stabilize bottom sheet callbacks
            val onDismissSheet = remember {
                {
                    showManageSheet = false
                    selectedItem = null
                }
            }
            val onPlayFromSheet = remember(item) {
                {
                    handlePlay(item)
                    showManageSheet = false
                }
            }
            val onDeleteFromSheet = remember(item, viewModel, deleteSuccessMessage, deleteFailureTemplate) {
                { dismissed: Boolean, errorMessage: String? ->
                    if (dismissed) {
                        viewModel.deleteItem(item) { success, error ->
                            coroutineScope.launch {
                                if (success) {
                                    snackbarHostState.showSnackbar(deleteSuccessMessage)
                                    onRefresh()
                                } else {
                                    snackbarHostState.showSnackbar(
                                        deleteFailureTemplate.format(error ?: unknownErrorMessage)
                                    )
                                }
                                showManageSheet = false
                            }
                        }
                    } else {
                        showManageSheet = false
                    }
                }
            }

            MediaItemActionsSheet(
                item = item,
                sheetState = sheetState,
                onDismiss = onDismissSheet,
                onPlay = onPlayFromSheet,
                onDelete = onDeleteFromSheet,
            )
        }
    }
}

/**
 * Immersive home content with full-bleed hero and tighter spacing
 */
@OptInAppExperimentalApis
@Composable
private fun ImmersiveHomeContent(
    appState: MainAppState,
    currentServer: JellyfinServer?,
    onRefresh: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit = {},
    onItemLongPress: (BaseItemDto) -> Unit = {},
    onLibraryClick: (BaseItemDto) -> Unit = {},
    onGenerateViewingMood: () -> Unit = {},
    gridState: LazyGridState,
    listState: LazyListState,
    windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass,
    adaptiveConfig: com.rpeters.jellyfin.ui.adaptive.AdaptiveLayoutConfig,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // Consolidate all derived state computations
    val contentLists by remember(
        appState.allItems,
        appState.continueWatching,
        appState.recentlyAddedByTypes,
        adaptiveConfig.continueWatchingLimit,
        adaptiveConfig.rowItemLimit,
        adaptiveConfig.featuredItemsLimit,
    ) {
        derivedStateOf {
            val continueWatching = getContinueWatchingItems(appState, adaptiveConfig.continueWatchingLimit)
            val movies = appState.recentlyAddedByTypes[BaseItemKind.MOVIE.name]
                ?.take(adaptiveConfig.rowItemLimit) ?: emptyList()
            val tvShows = appState.recentlyAddedByTypes[BaseItemKind.SERIES.name]
                ?.take(adaptiveConfig.rowItemLimit) ?: emptyList()
            val episodes = appState.recentlyAddedByTypes[BaseItemKind.EPISODE.name]
                ?.take(adaptiveConfig.rowItemLimit) ?: emptyList()
            val music = appState.recentlyAddedByTypes[BaseItemKind.AUDIO.name]
                ?.take(adaptiveConfig.rowItemLimit) ?: emptyList()
            val videos = appState.recentlyAddedByTypes[BaseItemKind.VIDEO.name]
                ?.take(adaptiveConfig.rowItemLimit) ?: emptyList()
            val featured = (movies + tvShows).take(adaptiveConfig.featuredItemsLimit)

            HomeContentLists(
                continueWatching = continueWatching,
                recentMovies = movies,
                recentTVShows = tvShows,
                featuredItems = featured,
                recentEpisodes = episodes,
                recentMusic = music,
                recentVideos = videos,
            )
        }
    }

    val surfaceCoordinatorViewModel: SurfaceCoordinatorViewModel = hiltViewModel()

    LaunchedEffect(surfaceCoordinatorViewModel, contentLists.continueWatching) {
        snapshotFlow {
            contentLists.continueWatching.map { item ->
                val id = item.id.toString()
                Triple(id, item.name, item.seriesName)
            } to contentLists.continueWatching
        }
            .distinctUntilChangedBy { it.first }
            .collectLatest { (_, items) ->
                surfaceCoordinatorViewModel.updateContinueWatching(items)
            }
    }

    val unknownText = stringResource(id = R.string.unknown)
    val stableOnItemClick = remember(onItemClick) { onItemClick }
    val stableOnItemLongPress = remember(onItemLongPress) { onItemLongPress }
    val viewingMood = appState.viewingMood

    ExpressivePullToRefreshBox(
        isRefreshing = appState.isLoading,
        onRefresh = onRefresh,
        modifier = modifier,
        indicatorSize = 48.dp, // Standard expressive size
    ) {
        if (adaptiveConfig.isTablet) {
            ExpressiveBentoGrid(
                contentLists = contentLists,
                windowSizeClass = windowSizeClass,
                getImageUrl = getImageUrl,
                onItemClick = stableOnItemClick,
                onItemLongPress = stableOnItemLongPress,
                gridState = gridState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            MobileExpressiveHomeContent(
                appState = appState,
                contentLists = contentLists,
                getImageUrl = getImageUrl,
                getBackdropUrl = getBackdropUrl,
                getSeriesImageUrl = getSeriesImageUrl,
                onItemClick = stableOnItemClick,
                onItemLongPress = stableOnItemLongPress,
                onLibraryClick = onLibraryClick,
                viewingMood = viewingMood,
                listState = listState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@VisibleForTesting
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileExpressiveHomeContent(
    appState: MainAppState,
    contentLists: HomeContentLists,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit,
    onLibraryClick: (BaseItemDto) -> Unit,
    viewingMood: String?,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val unknownText = stringResource(id = R.string.unknown)
    val libraryRows = remember(appState.libraries, appState.itemsByLibrary) {
        appState.libraries.mapNotNull { library ->
            val libraryId = library.id.toString()
            val items = appState.itemsByLibrary[libraryId]
                .orEmpty()
                .sortedByDescending { it.dateCreated }
                .take(10)
            if (items.isEmpty()) null else library to items
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 0.dp,
            top = contentPadding.calculateTopPadding(),
            end = 0.dp,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Replace wonky carousel with high-impact Recently Added Movies (Last 5)
        val heroMovies = contentLists.recentMovies.take(5)
        if (heroMovies.isNotEmpty() || appState.isLoading) {
            item(key = "recently_added_hero", contentType = "hero_row") {
                if (heroMovies.isNotEmpty()) {
                    val featuredItems = remember(heroMovies, unknownText) {
                        heroMovies.map { movie ->
                            CarouselItem(
                                id = movie.id.toString(),
                                title = movie.name ?: unknownText,
                                subtitle = movie.productionYear?.toString() ?: "",
                                imageUrl = getBackdropUrl(movie) ?: getImageUrl(movie) ?: "",
                                type = MediaType.MOVIE
                            )
                        }
                    }
                    
                    ImmersiveHeroCarousel(
                        items = featuredItems,
                        onItemClick = { selected ->
                            heroMovies.firstOrNull { it.id.toString() == selected.id }?.let(onItemClick)
                        },
                        onPlayClick = { selected ->
                            heroMovies.firstOrNull { it.id.toString() == selected.id }?.let(onItemClick)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Placeholder while loading to prevent layout jump
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(com.rpeters.jellyfin.ui.theme.ImmersiveDimens.HeroHeightPhone)
                    )
                }
            }
        }

        item(key = "libraries", contentType = "libraries") {
            LibraryNavigationCarousel(
                libraries = appState.libraries,
                getImageUrl = getImageUrl,
                onLibraryClick = onLibraryClick,
            )
        }

        if (!viewingMood.isNullOrBlank()) {
            item(key = "viewing_mood", contentType = "viewing_mood") {
                ViewingMoodWidget(
                    viewingMood = viewingMood,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        if (contentLists.continueWatching.isNotEmpty()) {
            item(key = "continue_watching", contentType = "continue_watching") {
                ContinueWatchingSection(
                    items = contentLists.continueWatching,
                    getImageUrl = { item -> getSeriesImageUrl(item) ?: getImageUrl(item) },
                    onItemClick = onItemClick,
                    onItemLongPress = onItemLongPress,
                    cardWidth = 320.dp, // Increased from 176dp
                )
            }
        }

        if (contentLists.recentEpisodes.isNotEmpty()) {
            item(key = "next_up", contentType = "next_up") {
                PosterRowSection(
                    title = stringResource(id = R.string.home_next_up),
                    items = contentLists.recentEpisodes,
                    getImageUrl = { item -> getSeriesImageUrl(item) ?: getImageUrl(item) },
                    onItemClick = onItemClick,
                    onItemLongPress = onItemLongPress,
                    cardWidth = 200.dp, // Increased from 164dp
                )
            }
        }

        items(
            items = libraryRows,
            key = { (library, _) -> library.id.toString() },
            contentType = { "library_recent_row" },
        ) { (library, items) ->
            LibraryRecentSection(
                library = library,
                items = items,
                getImageUrl = { item ->
                    when (item.type) {
                        BaseItemKind.EPISODE -> getSeriesImageUrl(item) ?: getImageUrl(item)
                        BaseItemKind.SERIES -> getSeriesImageUrl(item) ?: getBackdropUrl(item) ?: getImageUrl(item)
                        BaseItemKind.VIDEO -> getBackdropUrl(item) ?: getImageUrl(item)
                        else -> getImageUrl(item)
                    }
                },
                onLibraryClick = onLibraryClick,
                onItemClick = onItemClick,
                onItemLongPress = onItemLongPress,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryNavigationCarousel(
    libraries: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onLibraryClick: (BaseItemDto) -> Unit,
) {
    if (libraries.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionTitle(
            title = stringResource(R.string.home_libraries),
            modifier = Modifier.padding(top = 8.dp),
        )

        val carouselState = rememberCarouselState { libraries.size }
        androidx.compose.material3.carousel.HorizontalUncontainedCarousel(
            state = carouselState,
            itemWidth = 220.dp, // Increased from 176dp
            itemSpacing = 16.dp, // Increased spacing
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp), // Match item width for balanced look
        ) { index ->
            val library = libraries[index]
            LibraryExpressiveCard(
                library = library,
                imageUrl = getImageUrl(library),
                onClick = { onLibraryClick(library) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LibraryRecentSection(
    library: BaseItemDto,
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onLibraryClick: (BaseItemDto) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = library.name ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.library_recently_added_section),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = { onLibraryClick(library) }) {
                Text(text = stringResource(R.string.open))
            }
        }

        when (library.toLibraryTypeOrNull()) {
            LibraryType.STUFF, LibraryType.MUSIC -> HomeLibraryMediaRow(
                items = items,
                getImageUrl = getImageUrl,
                onItemClick = onItemClick,
                onItemLongPress = onItemLongPress,
                cardWidth = 320.dp, // Increased from 240dp
            )
            else -> HomeLibraryPosterRow(
                items = items,
                getImageUrl = getImageUrl,
                onItemClick = onItemClick,
                onItemLongPress = onItemLongPress,
                cardWidth = 200.dp, // Increased from 164dp
            )
        }
    }
}

@Composable
private fun HomeLibraryPosterRow(
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit,
    cardWidth: androidx.compose.ui.unit.Dp,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items = items, key = { it.getItemKey() }) { item ->
            PosterMediaCard(
                item = item,
                getImageUrl = getImageUrl,
                onClick = onItemClick,
                onLongPress = onItemLongPress,
                cardWidth = cardWidth,
                showTitle = true,
                showMetadata = true,
                titleMinLines = 2,
            )
        }
    }
}

@Composable
private fun HomeLibraryMediaRow(
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit,
    cardWidth: androidx.compose.ui.unit.Dp,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items = items, key = { it.getItemKey() }) { item ->
            MediaCard(
                item = item,
                getImageUrl = { getImageUrl(item) },
                onClick = onItemClick,
                onLongPress = onItemLongPress,
                cardWidth = cardWidth,
            )
        }
    }
}

@Composable
private fun LibraryExpressiveCard(
    library: BaseItemDto,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            HeroImageWithGradient(
                imageUrl = imageUrl,
                contentDescription = library.name ?: "Library",
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                ) {
                    Icon(
                        imageVector = library.toLibraryTypeOrNull()?.icon ?: Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(20.dp),
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomStart),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.86f),
                        tonalElevation = 2.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = library.name ?: "Library",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = library.collectionType?.toString()?.replace("_", " ") ?: "Collection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun BaseItemDto.toLibraryTypeOrNull(): LibraryType? = when (collectionType) {
    CollectionType.MOVIES -> LibraryType.MOVIES
    CollectionType.TVSHOWS -> LibraryType.TV_SHOWS
    CollectionType.MUSIC -> LibraryType.MUSIC
    CollectionType.HOMEVIDEOS, CollectionType.BOOKS -> LibraryType.STUFF
    else -> when (collectionType?.toString()?.lowercase()?.replace(" ", "")) {
        "movies" -> LibraryType.MOVIES
        "tvshows" -> LibraryType.TV_SHOWS
        "music" -> LibraryType.MUSIC
        else -> LibraryType.STUFF
    }
}

/**
 * Viewing mood widget with AI-powered mood analysis
 */
@Composable
private fun ViewingMoodWidget(
    viewingMood: String,
    onMoodClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    ExpressiveContentCard(
        onClick = onMoodClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.Spacing8),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(ImmersiveDimens.SpacingContentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(32.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Your Viewing Mood",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = viewingMood,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}
