package com.rpeters.jellyfin.ui.screens

import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.util.PerformanceMetricsTracker
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.ui.components.*
import com.rpeters.jellyfin.ui.components.aiAura
import com.rpeters.jellyfin.ui.components.immersive.*
import com.rpeters.jellyfin.ui.navigation.LocalNavBarVisible
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
                            .padding(end = 16.dp, bottom = 88.dp), // Just above navigation bar
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    haptics.heavyClick()
                                    onAiAssistantClick()
                                },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.aiAura(),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = stringResource(id = R.string.ai_assistant),
                                )
                            }

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
                        windowSizeClass = adaptiveConfig.windowSizeClass,
                        adaptiveConfig = adaptiveConfig,
                        contentPadding = paddingValues,
                        animatedVisibilityScope = animatedVisibilityScope,
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
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
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
    val isGlobalNavBarVisible = LocalNavBarVisible.current.value
    val homeContentBottomPadding by animateDpAsState(
        targetValue = if (isGlobalNavBarVisible) 24.dp else 8.dp,
        label = "homeContentBottomPadding",
    )

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
        if (adaptiveConfig.isTablet) {
            ExpressiveBentoGrid(
                contentLists = contentLists,
                windowSizeClass = windowSizeClass,
                getImageUrl = getImageUrl,
                onItemClick = stableOnItemClick,
                onItemLongPress = stableOnItemLongPress,
                onAiAssistantClick = onAiAssistantClick,
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
                bottomSpacing = homeContentBottomPadding,
                animatedVisibilityScope = animatedVisibilityScope,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@VisibleForTesting
internal fun shouldResetHomeScrollForLateHero(
    previousHasHero: Boolean,
    currentHasHero: Boolean,
    firstVisibleItemIndex: Int,
): Boolean = !previousHasHero && currentHasHero && firstVisibleItemIndex <= 1
