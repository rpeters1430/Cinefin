package com.rpeters.jellyfin.ui.screens

import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.util.PerformanceMetricsTracker
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.ui.components.*
import com.rpeters.jellyfin.ui.components.immersive.*
import com.rpeters.jellyfin.ui.screens.home.*
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.ui.utils.MediaPlayerUtils
import com.rpeters.jellyfin.ui.viewmodel.LibraryActionsPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.MainAppState
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.ui.viewmodel.SurfaceCoordinatorViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/** Stable long-press handler: opens the manage sheet, or shows a snackbar when management is disabled. */
@Composable
private fun rememberItemLongPressHandler(
    managementEnabled: Boolean,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
    managementDisabledMessage: String,
    onLongPress: (BaseItemDto) -> Unit,
): (BaseItemDto) -> Unit = remember(managementEnabled, coroutineScope, managementDisabledMessage) {
    { item: BaseItemDto ->
        if (managementEnabled) {
            onLongPress(item)
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message = managementDisabledMessage)
            }
        }
    }
}

/** Stable play handler: starts playback, or shows a snackbar when no stream URL is available. */
@Composable
private fun rememberPlayHandler(
    viewModel: MainAppViewModel,
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
): (BaseItemDto) -> Unit = remember(viewModel, context, coroutineScope) {
    { item: BaseItemDto ->
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

/** Loads library type data for any library whose items haven't been fetched yet. */
@Composable
private fun LoadMissingLibraryDataEffect(appState: MainAppState, viewModel: MainAppViewModel) {
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
}

/** Auto-hide top bar visibility, tracking the grid on tablet and the list otherwise. */
@Composable
private fun rememberHomeTopBarVisible(
    isTablet: Boolean,
    gridState: LazyGridState,
    listState: LazyListState,
): Boolean {
    val nearTopOffsetPx = with(LocalDensity.current) { ImmersiveDimens.HeroHeightPhone.toPx().toInt() }
    return if (isTablet) {
        rememberAutoHideTopBarVisible(gridState = gridState, nearTopOffsetPx = nearTopOffsetPx)
    } else {
        rememberAutoHideTopBarVisible(listState = listState, nearTopOffsetPx = nearTopOffsetPx)
    }
}

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
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        com.rpeters.jellyfin.ui.navigation.LocalAnimatedVisibilityScope provides animatedVisibilityScope,
    ) {
        val libraryActionPrefs by libraryActionsPreferencesViewModel.preferences.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current
        var selectedItem by remember { mutableStateOf<BaseItemDto?>(null) }
        var showManageSheet by remember { mutableStateOf(false) }
        @Suppress("DEPRECATION")
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        val managementEnabled = libraryActionPrefs.enableManagementActions
        val managementDisabledMessage = stringResource(id = R.string.library_actions_management_disabled)

        // ✅ Performance: Stabilize internal callbacks
        val handleItemLongPress = rememberItemLongPressHandler(
            managementEnabled = managementEnabled,
            coroutineScope = coroutineScope,
            snackbarHostState = snackbarHostState,
            managementDisabledMessage = managementDisabledMessage,
            onLongPress = { item -> selectedItem = item; showManageSheet = true },
        )

        val handlePlay = rememberPlayHandler(
            viewModel = viewModel,
            context = context,
            coroutineScope = coroutineScope,
            snackbarHostState = snackbarHostState,
        )

        LoadMissingLibraryDataEffect(appState = appState, viewModel = viewModel)

        // Calculate window size class for adaptive layout
        val adaptiveConfig = com.rpeters.jellyfin.ui.adaptive.LocalAdaptiveLayoutConfig.current

        // Track scroll state for auto-hiding navigation
        val gridState = rememberLazyGridState()
        val listState = rememberLazyListState()

        // Scroll to top once when the initial data load completes so the hero is always visible.
        // The `initialLoadComplete` flag ensures this only runs on the first successful load,
        // and is not re-triggered by subsequent refreshes.
        var initialLoadComplete by remember { mutableStateOf(false) }
        LaunchedEffect(appState.isLoading) {
            if (!appState.isLoading && !initialLoadComplete) {
                initialLoadComplete = true
                listState.scrollToItem(0)
                gridState.scrollToItem(0)
            }
        }

        // Use hero height as threshold to avoid flickering within hero
        val topBarVisible = rememberHomeTopBarVisible(
            isTablet = adaptiveConfig.isTablet,
            gridState = gridState,
            listState = listState,
        )

        // Density pass: collapsing hero + fading-in top bar (phone only). The hero shrinks
        // from HeroHeightPhone to HeroHeightCollapsed as the user scrolls past it, and a
        // compact 56dp top bar fades in over the same range.
        val heroCollapseRangePx = with(LocalDensity.current) {
            (ImmersiveDimens.HeroHeightPhone - ImmersiveDimens.HeroHeightCollapsed).toPx()
        }
        val heroCollapseFraction by rememberScrollCollapseFraction(
            listState = listState,
            collapseRangePx = heroCollapseRangePx,
        )
        val collapsedHeroHeight = ImmersiveDimens.HeroHeightPhone -
            (ImmersiveDimens.HeroHeightPhone - ImmersiveDimens.HeroHeightCollapsed) * heroCollapseFraction

        Box(modifier = modifier.fillMaxSize()) {
            ImmersiveScaffold(
                // No top bar title, but we pass the visibility state for consistent behavior
                topBarVisible = topBarVisible,
                topBarTitle = "",
                topBarTranslucent = false,
                // ...
                scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
                overlayContent = {
                    ImmersiveHomeOverlayContent(
                        currentServer = currentServer,
                        adaptiveConfig = adaptiveConfig,
                        heroCollapseFraction = heroCollapseFraction,
                        topBarVisible = topBarVisible,
                        onSettingsClick = onSettingsClick,
                        onAiAssistantClick = onAiAssistantClick,
                        onSearchClick = onSearchClick,
                        snackbarHostState = snackbarHostState,
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
                        onAiAssistantClick = onAiAssistantClick,
                        gridState = gridState,
                        listState = listState,
                        windowSizeClass = adaptiveConfig.windowSizeClass,
                        adaptiveConfig = adaptiveConfig,
                        contentPadding = paddingValues,
                        animatedVisibilityScope = animatedVisibilityScope,
                        heroHeight = collapsedHeroHeight,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        ItemManagementSheetHost(
            selectedItem = selectedItem,
            showManageSheet = showManageSheet,
            sheetState = sheetState,
            viewModel = viewModel,
            coroutineScope = coroutineScope,
            snackbarHostState = snackbarHostState,
            onRefresh = onRefresh,
            onPlay = handlePlay,
            onDismiss = {
                showManageSheet = false
                selectedItem = null
            },
            onSheetHidden = { showManageSheet = false },
        )
    }
}

/**
 * Hosts the [MediaItemActionsSheet] bottom sheet for the currently selected item, including its
 * play/delete callbacks with snackbar feedback. A no-op when nothing is selected or the sheet is
 * hidden.
 */
@Composable
private fun ItemManagementSheetHost(
    selectedItem: BaseItemDto?,
    showManageSheet: Boolean,
    sheetState: androidx.compose.material3.SheetState,
    viewModel: MainAppViewModel,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onPlay: (BaseItemDto) -> Unit,
    onDismiss: () -> Unit,
    onSheetHidden: () -> Unit,
) {
    val item = selectedItem ?: return
    if (!showManageSheet) return

    val itemName = item.name ?: stringResource(id = R.string.unknown)
    val deleteSuccessMessage = stringResource(id = R.string.library_actions_delete_success, itemName)
    val deleteFailureTemplate = stringResource(id = R.string.library_actions_delete_failure, itemName, "%s")
    val unknownErrorMessage = stringResource(id = R.string.unknown_error)

    // ✅ Performance: Stabilize bottom sheet callbacks
    val onPlayFromSheet = remember(item) {
        {
            onPlay(item)
            onSheetHidden()
        }
    }
    val onDeleteFromSheet = remember(item, viewModel, deleteSuccessMessage, deleteFailureTemplate) {
        {
                dismissed: Boolean, errorMessage: String? ->
            if (dismissed) {
                viewModel.deleteItem(item) { success, error ->
                    coroutineScope.launch {
                        if (success) {
                            snackbarHostState.showSnackbar(deleteSuccessMessage)
                            onRefresh()
                        } else {
                            snackbarHostState.showSnackbar(
                                deleteFailureTemplate.format(error ?: unknownErrorMessage),
                            )
                        }
                        onSheetHidden()
                    }
                }
            } else {
                onSheetHidden()
            }
        }
    }

    MediaItemActionsSheet(
        item = item,
        sheetState = sheetState,
        onDismiss = onDismiss,
        onPlay = onPlayFromSheet,
        onDelete = onDeleteFromSheet,
    )
}

/** Floating top bar, settings button and Search/AI action buttons overlaid on the home hero. */
@Composable
private fun BoxScope.ImmersiveHomeOverlayContent(
    currentServer: JellyfinServer?,
    adaptiveConfig: com.rpeters.jellyfin.ui.adaptive.AdaptiveLayoutConfig,
    heroCollapseFraction: Float,
    topBarVisible: Boolean,
    onSettingsClick: () -> Unit,
    onAiAssistantClick: () -> Unit,
    onSearchClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    // Density pass: compact 56dp top bar that fades in as the hero collapses.
    if (!adaptiveConfig.isTablet && heroCollapseFraction > 0f) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = heroCollapseFraction },
        ) {
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = currentServer?.name ?: stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }

    // Floating settings icon based on scroll direction
    val haptics = com.rpeters.jellyfin.ui.utils.rememberExpressiveHaptics()

    androidx.compose.animation.AnimatedVisibility(
        visible = topBarVisible,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
        modifier = Modifier.align(Alignment.TopEnd),
    ) {
        Surface(
            onClick = {
                haptics.lightClick()
                onSettingsClick()
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            tonalElevation = 2.dp,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(id = R.string.settings),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(12.dp)
                    .size(24.dp),
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
            .padding(end = 16.dp, bottom = 64.dp), // Just above navigation bar
    ) {
        FloatingActionButton(
            onClick = {
                haptics.lightClick()
                onSearchClick()
            },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(id = R.string.search),
            )
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 16.dp),
    )
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
    onAiAssistantClick: () -> Unit = {},
    gridState: LazyGridState,
    listState: LazyListState,
    windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass,
    adaptiveConfig: com.rpeters.jellyfin.ui.adaptive.AdaptiveLayoutConfig,
    contentPadding: PaddingValues,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
    heroHeight: androidx.compose.ui.unit.Dp = ImmersiveDimens.HeroHeightPhone,
    modifier: Modifier = Modifier,
) {
    // Consolidate all derived state computations
    val contentLists by remember(
        appState.allItems,
        appState.continueWatching,
        appState.nextUp,
        appState.recentlyAddedByTypes,
        adaptiveConfig.continueWatchingLimit,
        adaptiveConfig.rowItemLimit,
        adaptiveConfig.featuredItemsLimit,
    ) {
        derivedStateOf {
            val continueWatching = getContinueWatchingItems(appState, adaptiveConfig.continueWatchingLimit)
            val nextUp = appState.nextUp
                .filter { it.type == BaseItemKind.EPISODE }
                .take(adaptiveConfig.rowItemLimit)
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
                nextUp = nextUp,
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
    // The global nav chrome (NavigationBar on phones, NavigationRail/NavigationDrawer on larger
    // widths - see JellyfinApp.kt) is now permanently visible, so this is a fixed clearance
    // rather than something that needs to react to a hide/show transition.
    val homeContentBottomPadding = 24.dp

    val haptics = com.rpeters.jellyfin.ui.utils.rememberExpressiveHaptics()
    var previousHadFeaturedItems by remember { mutableStateOf(contentLists.featuredItems.isNotEmpty()) }
    val featuredItemsCount = contentLists.featuredItems.size

    LaunchedEffect(featuredItemsCount) {
        val hasFeaturedItems = featuredItemsCount > 0
        val shouldResetScroll = shouldResetHomeScrollForLateHero(
            previousHasHero = previousHadFeaturedItems,
            currentHasHero = hasFeaturedItems,
            firstVisibleItemIndex = listState.firstVisibleItemIndex,
        )
        // Update after evaluating transition so we can detect "hero appeared now" correctly.
        previousHadFeaturedItems = hasFeaturedItems

        if (!adaptiveConfig.isTablet &&
            shouldResetScroll &&
            // Defensive guard in case this effect runs during intermediate list recomposition.
            listState.layoutInfo.totalItemsCount > 0
        ) {
            // Use immediate scroll to avoid showing a visible "bounce" while delayed hero inserts.
            listState.scrollToItem(0)
        }
    }

    ExpressivePullToRefreshBox(
        isRefreshing = appState.isLoading,
        onRefresh = {
            haptics.heavyClick()
            onRefresh()
        },
        modifier = modifier,
        indicatorSize = 48.dp, // Standard expressive size
    ) {
        MobileExpressiveHomeContent(
            appState = appState,
            contentLists = contentLists,
            currentServer = currentServer,
            getImageUrl = getImageUrl,
            getBackdropUrl = getBackdropUrl,
            getSeriesImageUrl = getSeriesImageUrl,
            onItemClick = stableOnItemClick,
            onItemLongPress = stableOnItemLongPress,
            onLibraryClick = onLibraryClick,
            viewingMood = viewingMood,
            listState = listState,
            contentPadding = contentPadding,
            bottomSpacing = homeContentBottomPadding,
            animatedVisibilityScope = animatedVisibilityScope,
            heroHeight = heroHeight,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@VisibleForTesting
internal fun shouldResetHomeScrollForLateHero(
    previousHasHero: Boolean,
    currentHasHero: Boolean,
    firstVisibleItemIndex: Int,
): Boolean = !previousHasHero && currentHasHero && firstVisibleItemIndex <= 1
