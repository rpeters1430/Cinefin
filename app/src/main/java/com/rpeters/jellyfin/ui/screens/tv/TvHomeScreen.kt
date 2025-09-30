package com.rpeters.jellyfin.ui.screens.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.ui.adaptive.rememberAdaptiveLayoutConfig
import com.rpeters.jellyfin.ui.adaptive.rememberWindowLayoutInfo
import com.rpeters.jellyfin.ui.components.tv.TvEmptyState
import com.rpeters.jellyfin.ui.components.tv.TvErrorBanner
import com.rpeters.jellyfin.ui.components.tv.TvFullScreenLoading
import com.rpeters.jellyfin.ui.screens.tv.adaptive.TabletHomeContent
import com.rpeters.jellyfin.ui.screens.tv.adaptive.TvCarouselHomeContent
import com.rpeters.jellyfin.ui.screens.tv.adaptive.TvHomeMediaSection
import com.rpeters.jellyfin.ui.tv.TvScreenFocusScope
import com.rpeters.jellyfin.ui.tv.rememberTvFocusManager
import com.rpeters.jellyfin.ui.tv.requestInitialFocus
import com.rpeters.jellyfin.ui.tv.tvKeyboardHandler
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemKind
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

private const val RECENT_MOVIES_ID = "recent_movies"
private const val RECENT_TV_SHOWS_ID = "recent_tv_shows"
private const val ALL_MOVIES_ID = "all_movies"
private const val ALL_TV_SHOWS_ID = "all_tv_shows"

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun TvHomeScreen(
    onItemSelect: (String) -> Unit,
    onLibrarySelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
) {
    val appState by viewModel.appState.collectAsState()
    val focusManager = LocalFocusManager.current
    val tvFocusManager = rememberTvFocusManager()
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context as androidx.activity.ComponentActivity)
    val windowLayoutInfo = rememberWindowLayoutInfo()
    val layoutConfig = rememberAdaptiveLayoutConfig(windowSizeClass, windowLayoutInfo)

    // Ensure initial data is loaded when entering the TV home screen
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // loadInitialData has internal guards against concurrent runs
        viewModel.loadInitialData(forceRefresh = false)
    }

    val recentMovies = appState.recentlyAddedByTypes[BaseItemKind.MOVIE.name]?.take(10) ?: emptyList()
    val recentTvShows = appState.recentlyAddedByTypes[BaseItemKind.SERIES.name]?.take(10) ?: emptyList()
    val allMovies = appState.allMovies.take(10)
    val allTvShows = appState.allTVShows.take(10)

    val sections = listOf(
        TvHomeMediaSection(
            id = RECENT_MOVIES_ID,
            title = "Recently Added Movies",
            items = recentMovies,
            isLoading = appState.isLoading,
        ),
        TvHomeMediaSection(
            id = RECENT_TV_SHOWS_ID,
            title = "Recently Added TV Shows",
            items = recentTvShows,
            isLoading = appState.isLoading,
        ),
        TvHomeMediaSection(
            id = ALL_MOVIES_ID,
            title = "Movies",
            items = allMovies,
            isLoading = appState.isLoadingMovies,
        ),
        TvHomeMediaSection(
            id = ALL_TV_SHOWS_ID,
            title = "TV Shows",
            items = allTvShows,
            isLoading = appState.isLoadingTVShows,
        ),
    ).filter { it.items.isNotEmpty() || it.isLoading }

    val firstSectionId = sections.firstOrNull()?.id
    val initialFocusRequester = remember(layoutConfig.shouldShowDualPane) { FocusRequester() }
    initialFocusRequester.requestInitialFocus(
        condition = firstSectionId != null && layoutConfig.supportsFocusNavigation,
    )

    TvScreenFocusScope(
        screenKey = "tv_home",
        focusManager = tvFocusManager,
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(TvMaterialTheme.colorScheme.surface)
                .tvKeyboardHandler(
                    focusManager = focusManager,
                    onHome = { /* Already on home */ },
                    onSearch = { /* Navigate to search */ },
                ),
        ) {
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
                        TvHomeHeader(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(layoutConfig.headerPadding),
                        )
                    },
                    onItemSelect = { item ->
                        item.id?.let { onItemSelect(it.toString()) }
                    },
                    getImageUrl = viewModel::getImageUrl,
                    getSeriesImageUrl = viewModel::getSeriesImageUrl,
                    libraries = appState.libraries,
                    onLibrarySelect = onLibrarySelect,
                    isLoadingLibraries = appState.isLoading,
                    initialFocusRequester = initialFocusRequester.takeIf { firstSectionId != null },
                )
            } else {
                TvCarouselHomeContent(
                    layoutConfig = layoutConfig,
                    sections = sections,
                    focusManager = tvFocusManager,
                    modifier = Modifier.fillMaxSize(),
                    header = {
                        TvHomeHeader(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(layoutConfig.headerPadding),
                        )
                    },
                    onItemSelect = { item ->
                        item.id?.let { onItemSelect(it.toString()) }
                    },
                    libraries = appState.libraries,
                    onLibrarySelect = onLibrarySelect,
                    isLoadingLibraries = appState.isLoading,
                    initialFocusRequester = initialFocusRequester,
                    firstSectionId = firstSectionId,
                )
            }
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
        TvText(
            text = "Welcome to Jellyfin",
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
