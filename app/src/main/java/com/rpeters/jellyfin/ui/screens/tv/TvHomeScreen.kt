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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rpeters.jellyfin.ui.components.tv.TvContentCarousel
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

@Composable
fun TvHomeScreen(
    onItemSelect: (String) -> Unit,
    onLibrarySelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
) {
    val appState by viewModel.appState.collectAsState()
    val focusManager = LocalFocusManager.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvMaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            // Welcome header
            TvHomeHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 56.dp, vertical = 24.dp),
            )

            // Recently added movies
            val recentMovies = appState.recentlyAddedByTypes["MOVIE"]?.take(10) ?: emptyList()
            if (recentMovies.isNotEmpty()) {
                TvContentCarousel(
                    items = recentMovies,
                    title = "Recently Added Movies",
                    onItemFocus = { /* Handle focus if needed */ },
                    onItemSelect = { item ->
                        item.id?.let { onItemSelect(it.toString()) }
                    },
                )
            }

            // TV Shows
            val recentTvShows = appState.recentlyAddedByTypes["SERIES"]?.take(10) ?: emptyList()
            if (recentTvShows.isNotEmpty()) {
                TvContentCarousel(
                    items = recentTvShows,
                    title = "Recently Added TV Shows",
                    onItemFocus = { /* Handle focus if needed */ },
                    onItemSelect = { item ->
                        item.id?.let { onItemSelect(it.toString()) }
                    },
                )
            }

            // Libraries
            if (appState.libraries.isNotEmpty()) {
                TvLibrariesSection(
                    libraries = appState.libraries,
                    onLibrarySelect = onLibrarySelect,
                    modifier = Modifier.fillMaxWidth(),
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
