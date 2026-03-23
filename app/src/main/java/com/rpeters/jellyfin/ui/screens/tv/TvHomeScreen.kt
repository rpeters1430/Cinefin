package com.rpeters.jellyfin.ui.screens.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.adaptive.rememberAdaptiveLayoutConfig
import com.rpeters.jellyfin.ui.adaptive.rememberWindowLayoutInfo
import com.rpeters.jellyfin.ui.components.tv.TvEmptyState
import com.rpeters.jellyfin.ui.components.tv.TvErrorBanner
import com.rpeters.jellyfin.ui.components.tv.TvFullScreenLoading
import com.rpeters.jellyfin.ui.components.tv.TvHeroCarousel
import com.rpeters.jellyfin.ui.components.tv.TvImmersiveBackground
import com.rpeters.jellyfin.ui.screens.tv.adaptive.TabletHomeContent
import com.rpeters.jellyfin.ui.screens.tv.adaptive.TvCarouselHomeContent
import com.rpeters.jellyfin.ui.screens.tv.adaptive.TvHomeMediaSection
import com.rpeters.jellyfin.ui.theme.CinefinTvTheme
import com.rpeters.jellyfin.ui.tv.TvScreenFocusScope
import com.rpeters.jellyfin.ui.tv.rememberTvFocusManager
import com.rpeters.jellyfin.ui.tv.requestInitialFocus
import com.rpeters.jellyfin.ui.tv.tvKeyboardHandler
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

private const val RECENT_MOVIES_ID = "recent_movies"
private const val RECENT_TV_SHOWS_ID = "recent_tv_shows"
private const val RECENT_TV_SHOW_EPISODES_ID = "recent_tv_show_episodes"
private const val RECENT_STUFF_ID = "recent_stuff"
private const val CONTINUE_WATCHING_ID = "continue_watching"
private const val FAVORITES_ID = "favorites"

@OptIn(ExperimentalTvMaterial3Api::class)
@OptInAppExperimentalApis
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
    val focusManager = LocalFocusManager.current
    val tvFocusManager = rememberTvFocusManager()
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context as androidx.activity.ComponentActivity)
    val windowLayoutInfo = rememberWindowLayoutInfo()
    val layoutConfig = rememberAdaptiveLayoutConfig(windowSizeClass, windowLayoutInfo)
    val tvLayout = CinefinTvTheme.layout

    // State for the immersive background
    var focusedBackdrop by remember { mutableStateOf<String?>(null) }

    // Ensure initial data is loaded when entering the TV home screen
    LaunchedEffect(Unit) {
        // loadInitialData has internal guards against concurrent runs
        viewModel.loadInitialData(forceRefresh = false)
        viewModel.loadFavorites()
    }

    val continueWatching = appState.continueWatching
        .sortedWith(tvInProgressComparator())
        .take(10)

    val recentMovies = appState.recentlyAddedByTypes[BaseItemKind.MOVIE.name]?.take(10) ?: emptyList()
    val recentTvShows = appState.recentlyAddedByTypes[BaseItemKind.SERIES.name]?.take(10) ?: emptyList()
    val recentTvShowEpisodes = appState.recentlyAddedByTypes[BaseItemKind.EPISODE.name]?.take(10) ?: emptyList()

    // "Stuff" (Home Videos) - usually BaseItemKind.VIDEO in recentlyAddedByTypes
    val recentStuff = appState.recentlyAddedByTypes[BaseItemKind.VIDEO.name]?.take(10) ?: emptyList()
    val favorites = appState.favorites.take(10)

    val featuredItems = remember(
        continueWatching,
        recentTvShowEpisodes,
        recentMovies,
        recentTvShows,
        recentStuff,
        layoutConfig.featuredItemsLimit,
    ) {
        buildTvFeaturedItems(
            continueWatching = continueWatching,
            nextUpEpisodes = recentTvShowEpisodes,
            recentMovies = recentMovies,
            recentTvShows = recentTvShows,
            recentStuff = recentStuff,
            maxItems = layoutConfig.featuredItemsLimit.coerceAtMost(6),
        )
    }

    val sections = listOf(
        TvHomeMediaSection(
            id = CONTINUE_WATCHING_ID,
            title = "Continue Watching",
            items = continueWatching,
            isLoading = appState.isLoading,
            cardWidth = 184.dp,
            cardHeight = 276.dp,
        ),
        TvHomeMediaSection(
            id = RECENT_TV_SHOW_EPISODES_ID,
            title = "Next Up",
            items = recentTvShowEpisodes,
            isLoading = appState.isLoading,
            cardWidth = 168.dp,
            cardHeight = 252.dp,
        ),
        TvHomeMediaSection(
            id = RECENT_MOVIES_ID,
            title = "Recent Movies",
            items = recentMovies,
            isLoading = appState.isLoading,
            cardWidth = 168.dp,
            cardHeight = 252.dp,
        ),
        TvHomeMediaSection(
            id = RECENT_TV_SHOWS_ID,
            title = "Recent TV Shows",
            items = recentTvShows,
            isLoading = appState.isLoading,
            cardWidth = 168.dp,
            cardHeight = 252.dp,
        ),
        TvHomeMediaSection(
            id = FAVORITES_ID,
            title = "Favorites",
            items = favorites,
            isLoading = appState.isLoading && favorites.isEmpty(),
            cardWidth = 168.dp,
            cardHeight = 252.dp,
        ),
        TvHomeMediaSection(
            id = RECENT_STUFF_ID,
            title = "Recent Stuff",
            items = recentStuff,
            isLoading = appState.isLoading,
            cardWidth = 228.dp,
            cardHeight = 128.dp,
        ),
    ).filter { it.items.isNotEmpty() || it.isLoading }

    val firstSectionId = sections.firstOrNull()?.id
    val initialFocusRequester = remember(layoutConfig.shouldShowDualPane) { FocusRequester() }
    initialFocusRequester.requestInitialFocus(
        condition = (featuredItems.isNotEmpty() || firstSectionId != null) && layoutConfig.supportsFocusNavigation,
    )

    TvScreenFocusScope(
        screenKey = screenKey,
        focusManager = tvFocusManager,
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .tvKeyboardHandler(
                    focusManager = focusManager,
                    onHome = { /* Already on home */ },
                    onSearch = onSearch,
                ),
        ) {
            // Background Layer
            TvImmersiveBackground(backdropUrl = focusedBackdrop)

            // Content Layer
            Box(modifier = Modifier.fillMaxSize()) {
                // Show loading state if still loading
                if (appState.isLoading || appState.isLoadingMovies || appState.isLoadingTVShows) {
                    TvFullScreenLoading(message = "Loading your media...")
                    return@Box
                }

                // Show error state if there's an error
                appState.errorMessage?.let { error ->
                    TvErrorBanner(
                        title = "Connection Error",
                        message = error,
                        onRetry = {
                            viewModel.loadInitialData(forceRefresh = true)
                        },
                        onDismiss = { viewModel.clearError() },
                    )
                    return@Box
                }

                // Show empty state if no content
                if (appState.libraries.isEmpty() && appState.recentlyAdded.isEmpty()) {
                    TvEmptyState(
                        title = "No Content Available",
                        message = "Connect to your Jellyfin server and add some media to get started.",
                        onAction = {
                            viewModel.loadInitialData(forceRefresh = true)
                        },
                        actionText = "Refresh",
                    )
                    return@Box
                }

                if (layoutConfig.shouldShowDualPane) {
                    TabletHomeContent(
                        layoutConfig = layoutConfig,
                        sections = sections,
                        focusManager = tvFocusManager,
                        modifier = Modifier.fillMaxSize(),
                        header = {
                            Column {
                                TvHomeHeader(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(layoutConfig.headerPadding),
                                )
                                TvHeroCarousel(
                                    featuredItems = featuredItems,
                                    onItemClick = { item -> onItemSelect(item.id.toString()) },
                                    onPlayClick = { item ->
                                        onPlay(item.id.toString(), item.name ?: "", 0L)
                                    },
                                    getHeroImageUrl = { item ->
                                        viewModel.getBackdropUrl(item)
                                            ?: viewModel.getLogoUrl(item)
                                            ?: viewModel.getSeriesImageUrl(item)
                                            ?: viewModel.getImageUrl(item)
                                    },
                                    modifier = Modifier
                                        .padding(bottom = tvLayout.heroBottomSpacing)
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft) {
                                                focusManager.moveFocus(FocusDirection.Left)
                                                true
                                            } else {
                                                false
                                            }
                                        },
                                    focusRequester = initialFocusRequester,
                                )

                                // Library Cards under Carousel
                                if (appState.libraries.isNotEmpty()) {
                                    TvLibrariesSection(
                                        libraries = appState.libraries,
                                        onLibrarySelect = onLibrarySelect,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = tvLayout.heroBottomSpacing),
                                        isLoading = appState.isLoading,
                                    )
                                }
                            }
                        },
                        onItemFocus = { item ->
                            focusedBackdrop = viewModel.getBackdropUrl(item)
                        },
                        onItemSelect = { item ->
                            onItemSelect(item.id.toString())
                        },
                        focusBridgeManager = focusManager,
                        getImageUrl = viewModel::getImageUrl,
                        getSeriesImageUrl = viewModel::getSeriesImageUrl,
                        libraries = emptyList(), // Moved to header
                        onLibrarySelect = onLibrarySelect,
                        isLoadingLibraries = appState.isLoading,
                        initialFocusRequester = if (featuredItems.isEmpty()) initialFocusRequester else null,
                    )
                } else {
                    if (featuredItems.isEmpty() && sections.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(layoutConfig.contentPadding),
                            verticalArrangement = Arrangement.spacedBy(tvLayout.sectionSpacing),
                        ) {
                            TvHomeHeader(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(layoutConfig.headerPadding),
                            )

                            if (appState.libraries.isNotEmpty()) {
                                TvLibrariesSection(
                                    libraries = appState.libraries,
                                    onLibrarySelect = onLibrarySelect,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = tvLayout.heroBottomSpacing),
                                    isLoading = appState.isLoading,
                                )
                            }

                            TvText(
                                text = "Your TV home is connected, but featured content is still catching up.",
                                style = TvMaterialTheme.typography.bodyLarge,
                                color = TvMaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                modifier = Modifier.padding(horizontal = layoutConfig.headerPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)),
                            )
                        }
                    } else {
                        TvCarouselHomeContent(
                            layoutConfig = layoutConfig,
                            sections = sections,
                            focusManager = tvFocusManager,
                            modifier = Modifier.fillMaxSize(),
                            header = {
                                Column {
                                    TvHomeHeader(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(layoutConfig.headerPadding),
                                    )
                                    TvHeroCarousel(
                                        featuredItems = featuredItems,
                                        onItemClick = { item -> onItemSelect(item.id.toString()) },
                                        onPlayClick = { item ->
                                            onPlay(item.id.toString(), item.name ?: "", 0L)
                                        },
                                        getHeroImageUrl = { item ->
                                            viewModel.getBackdropUrl(item)
                                                ?: viewModel.getLogoUrl(item)
                                                ?: viewModel.getSeriesImageUrl(item)
                                                ?: viewModel.getImageUrl(item)
                                        },
                                        modifier = Modifier
                                            .padding(bottom = tvLayout.heroBottomSpacing)
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft) {
                                                    focusManager.moveFocus(FocusDirection.Left)
                                                    true
                                                } else {
                                                    false
                                                }
                                            },
                                        focusRequester = initialFocusRequester,
                                    )

                                    if (appState.libraries.isNotEmpty()) {
                                        TvLibrariesSection(
                                            libraries = appState.libraries,
                                            onLibrarySelect = onLibrarySelect,
                                            modifier = Modifier.fillMaxWidth().padding(bottom = tvLayout.heroBottomSpacing),
                                            isLoading = appState.isLoading,
                                        )
                                    }
                                }
                            },
                            onItemFocus = { item ->
                                focusedBackdrop = viewModel.getBackdropUrl(item)
                            },
                            onItemSelect = { item ->
                                onItemSelect(item.id.toString())
                            },
                            focusBridgeManager = focusManager,
                            libraries = emptyList(),
                            onLibrarySelect = onLibrarySelect,
                            isLoadingLibraries = appState.isLoading,
                            initialFocusRequester = if (featuredItems.isEmpty()) initialFocusRequester else null,
                            firstSectionId = firstSectionId,
                        )
                    }
                }
            }
        }
    }
}

private fun tvInProgressComparator(): Comparator<org.jellyfin.sdk.model.api.BaseItemDto> {
    return compareByDescending<org.jellyfin.sdk.model.api.BaseItemDto> { it.isInProgress() }
        .thenByDescending { it.type == BaseItemKind.EPISODE }
        .thenByDescending { it.playbackProgressRatio() }
}

private fun org.jellyfin.sdk.model.api.BaseItemDto.isInProgress(): Boolean {
    val playbackTicks = userData?.playbackPositionTicks ?: 0L
    val isPlayed = userData?.played == true
    return playbackTicks > 0L && !isPlayed
}

private fun org.jellyfin.sdk.model.api.BaseItemDto.playbackProgressRatio(): Double {
    val playbackTicks = userData?.playbackPositionTicks ?: return 0.0
    val runtimeTicks = runTimeTicks ?: return 0.0
    if (runtimeTicks <= 0L) return 0.0
    return (playbackTicks.toDouble() / runtimeTicks.toDouble()).coerceIn(0.0, 1.0)
}

private fun buildTvFeaturedItems(
    continueWatching: List<BaseItemDto>,
    nextUpEpisodes: List<BaseItemDto>,
    recentMovies: List<BaseItemDto>,
    recentTvShows: List<BaseItemDto>,
    recentStuff: List<BaseItemDto>,
    maxItems: Int,
): List<BaseItemDto> {
    val prioritizedCandidates = buildList {
        addAll(continueWatching.sortedWith(tvInProgressComparator()))
        addAll(nextUpEpisodes)
        addAll(recentMovies)
        addAll(recentTvShows)
        addAll(recentStuff)
    }

    return prioritizedCandidates
        .distinctBy { it.id }
        .filter { item ->
            !item.name.isNullOrBlank() && hasHeroVisuals(item)
        }
        .take(maxItems)
}

private fun hasHeroVisuals(item: BaseItemDto): Boolean {
    return item.backdropImageTags?.isNotEmpty() == true ||
        item.imageTags?.isNotEmpty() == true ||
        item.parentBackdropItemId != null ||
        item.parentLogoItemId != null ||
        item.seriesId != null
}

@Composable
fun TvHomeHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TvText(
            text = "Welcome to Cinefin",
            style = TvMaterialTheme.typography.headlineMedium,
            color = TvMaterialTheme.colorScheme.onSurface,
        )

        TvText(
            text = "Browse your media collection",
            style = TvMaterialTheme.typography.bodyLarge,
            color = TvMaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
