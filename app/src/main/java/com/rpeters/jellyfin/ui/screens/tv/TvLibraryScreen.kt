package com.rpeters.jellyfin.ui.screens.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

@Composable
fun TvLibraryScreen(
    libraryId: String?,
    onItemSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
) {
    val appState by viewModel.appState.collectAsState()
    val library = appState.libraries.firstOrNull { it.id?.toString() == libraryId }

    // Choose items based on library type
    val items = when (library?.collectionType) {
        org.jellyfin.sdk.model.api.CollectionType.MOVIES -> appState.allMovies
        org.jellyfin.sdk.model.api.CollectionType.TVSHOWS -> appState.allTVShows
        org.jellyfin.sdk.model.api.CollectionType.HOMEVIDEOS -> appState.itemsByLibrary[libraryId] ?: emptyList()
        else -> emptyList()
    }

    // Trigger on-demand loading for Home Videos
    androidx.compose.runtime.LaunchedEffect(libraryId, library?.collectionType) {
        if (library != null && library.collectionType == org.jellyfin.sdk.model.api.CollectionType.HOMEVIDEOS) {
            viewModel.loadHomeVideos(library.id.toString())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(56.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        TvText(
            text = library?.name ?: "Library",
            style = TvMaterialTheme.typography.headlineLarge,
            color = TvMaterialTheme.colorScheme.onSurface,
        )

        if (items.isEmpty()) {
            TvText(
                text = "No items to display",
                style = TvMaterialTheme.typography.bodyLarge,
                color = TvMaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            com.rpeters.jellyfin.ui.components.tv.TvContentCarousel(
                items = items,
                title = "All Items",
                onItemSelect = { baseItem ->
                    baseItem.id?.toString()?.let(onItemSelect)
                },
            )
        }
    }
}
