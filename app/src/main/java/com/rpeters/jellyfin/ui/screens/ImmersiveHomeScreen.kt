package com.rpeters.jellyfin.ui.screens

import android.app.Activity
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.util.PerformanceMetricsTracker
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.ui.adaptive.rememberAdaptiveLayoutConfig
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
import com.rpeters.jellyfin.utils.SecureLogger
import com.rpeters.jellyfin.utils.getItemKey
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

    // Track scroll state for auto-hiding navigation
    val listState = rememberLazyListState()
    
    // Smooth scroll detection for top bar - handled by ImmersiveScaffold overlay
    val topBarVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 200 ||
            listState.firstVisibleItemScrollOffset < (listState.layoutInfo.viewportEndOffset / 4)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ImmersiveScaffold(
            // Auto-hiding top bar with translucent background
            topBarVisible = topBarVisible,
            topBarTitle = currentServer?.name ?: stringResource(id = R.string.app_name),
            topBarNavigationIcon = if (showBackButton) {
                {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                }
            } else {
                {}
            },
            topBarActions = {
                IconButton(
                    onClick = onRefresh,
                    enabled = !appState.isLoading,
                ) {
                    if (appState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(id = R.string.settings),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
            },
            topBarTranslucent = true,
            // Show bottom bar with mini player
            bottomBarVisible = false, // Using MiniPlayer outside scaffold
            // Floating action group
            floatingActionButton = {
                FloatingActionGroup(
                    orientation = FabOrientation.Vertical,
                    primaryAction = FabAction(
                        icon = Icons.Default.Search,
                        contentDescription = stringResource(id = R.string.search),
                        onClick = onSearchClick,
                    ),
                    secondaryActions = listOf(
                        FabAction(
                            icon = Icons.Default.AutoAwesome,
                            contentDescription = "AI Assistant",
                            onClick = onAiAssistantClick,
                        ),
                    ),
                )
            },
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
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
                    .fillMaxSize()
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
                    listState = listState,
                    modifier = Modifier.fillMaxSize(),
                )

                // Mini player at bottom (overlays content)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    MiniPlayer(onExpandClick = onNowPlayingClick)
                }
            }
        }

        // Snackbar host outside scaffold
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

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
                    showManageSheet = false
                },
                onDelete = { dismissed, _ ->
                    if (dismissed) {
                        coroutineScope.launch {
                            viewModel.deleteItem(item)
                            snackbarHostState.showSnackbar(deleteSuccessMessage)
                            onRefresh()
                            showManageSheet = false
                        }
                    } else {
                        showManageSheet = false
                    }
                },
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
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    // Calculate window size class for adaptive layout
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(activity = context as Activity)
    val adaptiveConfig = rememberAdaptiveLayoutConfig(windowSizeClass)

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

    PullToRefreshBox(
        isRefreshing = appState.isLoading,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight), // Tighter: 16dp vs 24dp
            contentPadding = PaddingValues(bottom = 120.dp), // Space for mini player + FAB
            userScrollEnabled = true,
        ) {
            // Full-screen hero carousel (480dp height, edge-to-edge)
            if (contentLists.featuredItems.isNotEmpty()) {
                item(key = "immersive_hero_carousel", contentType = "immersive_carousel") {
                    val featured = remember(contentLists.featuredItems, unknownText) {
                        contentLists.featuredItems.map {
                            CarouselItem(
                                id = it.id.toString(),
                                title = it.name ?: unknownText,
                                subtitle = itemSubtitle(it),
                                imageUrl = getBackdropUrl(it) ?: getSeriesImageUrl(it)
                                    ?: getImageUrl(it) ?: "",
                            )
                        }
                    }

                    // Apply a subtle parallax translation to fix "scrolling down" visual bug
                    val carouselScrollOffset by remember {
                        derivedStateOf {
                            if (listState.firstVisibleItemIndex == 0) {
                                listState.firstVisibleItemScrollOffset.toFloat() * 0.5f
                            } else 0f
                        }
                    }

                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(ImmersiveDimens.HeroHeightPhone)
                        .graphicsLayer { translationY = carouselScrollOffset }
                        .clipToBounds()
                    ) {
                        ImmersiveHeroCarousel(
                            items = featured,
                            onItemClick = { selected ->
                                contentLists.featuredItems.firstOrNull { it.id.toString() == selected.id }
                                    ?.let(stableOnItemClick)
                            },
                            onPlayClick = { selected ->
                                contentLists.featuredItems.firstOrNull { it.id.toString() == selected.id }
                                    ?.let(stableOnItemClick)
                            },
                        )
                    }
                }
            }

            // Viewing Mood Widget (AI-powered mood analysis)
            if (viewingMood != null) {
                item(key = "viewing_mood", contentType = "ai_widget") {
                    ViewingMoodWidget(
                        viewingMood = viewingMood,
                        modifier = Modifier.padding(horizontal = ImmersiveDimens.SpacingContentPadding),
                    )
                }
            }

            // Continue Watching Section (large immersive cards)
            if (contentLists.continueWatching.isNotEmpty()) {
                item(key = "continue_watching", contentType = "immersive_row") {
                    ImmersiveMediaRow(
                        title = stringResource(id = R.string.shortcut_continue_watching),
                        items = contentLists.continueWatching,
                        getImageUrl = getImageUrl,
                        onItemClick = stableOnItemClick,
                        onItemLongPress = stableOnItemLongPress,
                        size = ImmersiveCardSize.MEDIUM,
                    )
                }
            }

            // Next Up (recent episodes)
            if (contentLists.recentEpisodes.isNotEmpty()) {
                item(key = "next_up", contentType = "immersive_row") {
                    ImmersiveMediaRow(
                        title = stringResource(id = R.string.home_next_up),
                        items = contentLists.recentEpisodes,
                        getImageUrl = { getSeriesImageUrl(it) ?: getImageUrl(it) },
                        onItemClick = stableOnItemClick,
                        onItemLongPress = stableOnItemLongPress,
                        size = ImmersiveCardSize.MEDIUM,
                    )
                }
            }

            // Recently Added Movies
            if (contentLists.recentMovies.isNotEmpty()) {
                item(key = "recent_movies", contentType = "immersive_row") {
                    ImmersiveMediaRow(
                        title = stringResource(id = R.string.home_recently_added_movies),
                        items = contentLists.recentMovies,
                        getImageUrl = getImageUrl,
                        onItemClick = stableOnItemClick,
                        onItemLongPress = stableOnItemLongPress,
                        size = ImmersiveCardSize.MEDIUM,
                    )
                }
            }

            // Recently Added TV Shows
            if (contentLists.recentTVShows.isNotEmpty()) {
                item(key = "recent_tv_shows", contentType = "immersive_row") {
                    ImmersiveMediaRow(
                        title = stringResource(id = R.string.home_recently_added_tv_shows),
                        items = contentLists.recentTVShows,
                        getImageUrl = getImageUrl,
                        onItemClick = stableOnItemClick,
                        onItemLongPress = stableOnItemLongPress,
                        size = ImmersiveCardSize.MEDIUM,
                    )
                }
            }

            // Recently Added Stuff (home videos, etc - larger horizontal cards)
            if (contentLists.recentVideos.isNotEmpty()) {
                item(key = "recent_stuff", contentType = "immersive_row_large") {
                    ImmersiveMediaRow(
                        title = stringResource(id = R.string.home_recently_added_stuff),
                        items = contentLists.recentVideos,
                        getImageUrl = { getBackdropUrl(it) ?: getImageUrl(it) },
                        onItemClick = stableOnItemClick,
                        onItemLongPress = stableOnItemLongPress,
                        size = ImmersiveCardSize.LARGE,
                    )
                }
            }
        }
    }
}

/**
 * Viewing mood widget with AI-powered mood analysis
 */
@Composable
private fun ViewingMoodWidget(
    viewingMood: String,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.Spacing8),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
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
