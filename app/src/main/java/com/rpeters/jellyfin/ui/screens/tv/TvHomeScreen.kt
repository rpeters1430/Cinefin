package com.rpeters.jellyfin.ui.screens.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.ui.adaptive.rememberAdaptiveLayoutConfig
import com.rpeters.jellyfin.ui.components.tv.TvContentCarousel
import com.rpeters.jellyfin.ui.components.tv.TvEmptyState
import com.rpeters.jellyfin.ui.components.tv.TvErrorBanner
import com.rpeters.jellyfin.ui.components.tv.TvFullScreenLoading
import com.rpeters.jellyfin.ui.tv.TvScreenFocusScope
import com.rpeters.jellyfin.ui.tv.rememberTvFocusManager
import com.rpeters.jellyfin.ui.tv.tvKeyboardHandler
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemKind
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

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
    val layoutConfig = rememberAdaptiveLayoutConfig(windowSizeClass)

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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(layoutConfig.spacing),
            ) {
                // Welcome header with adaptive padding
                TvHomeHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(layoutConfig.headerPadding),
                )

                // Recently added movies
                val recentMovies = appState.recentlyAddedByTypes[BaseItemKind.MOVIE.name]?.take(10) ?: emptyList()
                TvContentCarousel(
                    items = recentMovies,
                    title = "Recently Added Movies",
                    onItemFocus = { /* Handle focus if needed */ },
                    onItemSelect = { item ->
                        item.id?.let { onItemSelect(it.toString()) }
                    },
                    carouselId = "recent_movies",
                    isLoading = appState.isLoading,
                )

                // TV Shows
                val recentTvShows = appState.recentlyAddedByTypes[BaseItemKind.SERIES.name]?.take(10) ?: emptyList()
                TvContentCarousel(
                    items = recentTvShows,
                    title = "Recently Added TV Shows",
                    onItemFocus = { /* Handle focus if needed */ },
                    onItemSelect = { item ->
                        item.id?.let { onItemSelect(it.toString()) }
                    },
                    carouselId = "recent_tv_shows",
                    isLoading = appState.isLoading,
                )

                // All movies
                TvContentCarousel(
                    items = appState.allMovies.take(10),
                    title = "Movies",
                    onItemFocus = { /* Handle focus if needed */ },
                    onItemSelect = { item ->
                        item.id?.let { onItemSelect(it.toString()) }
                    },
                    carouselId = "all_movies",
                    isLoading = appState.isLoadingMovies,
                )

                // All TV shows
                TvContentCarousel(
                    items = appState.allTVShows.take(10),
                    title = "TV Shows",
                    onItemFocus = { /* Handle focus if needed */ },
                    onItemSelect = { item ->
                        item.id?.let { onItemSelect(it.toString()) }
                    },
                    carouselId = "all_tv_shows",
                    isLoading = appState.isLoadingTVShows,
                )

                // Libraries section
                if (appState.libraries.isNotEmpty()) {
                    TvLibrariesSection(
                        libraries = appState.libraries,
                        onLibrarySelect = onLibrarySelect,
                        modifier = Modifier.fillMaxWidth(),
                        isLoading = appState.isLoading,
                    )
                }
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
