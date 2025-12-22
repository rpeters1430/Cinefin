package com.rpeters.jellyfin.ui.screens.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.adaptive.rememberAdaptiveLayoutConfig
import com.rpeters.jellyfin.ui.screens.LibraryType
import com.rpeters.jellyfin.ui.tv.rememberTvFocusManager
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

@OptInAppExperimentalApis
@Composable
fun TvLibraryScreen(
    libraryId: String?,
    onItemSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context as android.app.Activity)
    val layoutConfig = rememberAdaptiveLayoutConfig(windowSizeClass)
    val focusManager = rememberTvFocusManager()
    val appState by viewModel.appState.collectAsState()
    val library = appState.libraries.firstOrNull { it.id?.toString() == libraryId }

    // Choose items based on library type - use itemsByLibrary for library-specific data
    val items = when (library?.collectionType) {
        org.jellyfin.sdk.model.api.CollectionType.MOVIES ->
            appState.itemsByLibrary[libraryId] ?: emptyList()
        org.jellyfin.sdk.model.api.CollectionType.TVSHOWS ->
            appState.itemsByLibrary[libraryId] ?: emptyList()
        org.jellyfin.sdk.model.api.CollectionType.MUSIC ->
            appState.itemsByLibrary[libraryId] ?: emptyList()
        org.jellyfin.sdk.model.api.CollectionType.HOMEVIDEOS ->
            appState.itemsByLibrary[libraryId] ?: emptyList()
        else ->
            appState.itemsByLibrary[libraryId] ?: emptyList()
    }

    // Determine if this specific library is loading
    val isLibraryLoading = when (library?.collectionType) {
        org.jellyfin.sdk.model.api.CollectionType.MOVIES -> appState.isLoadingMovies
        org.jellyfin.sdk.model.api.CollectionType.TVSHOWS -> appState.isLoadingTVShows
        else -> appState.isLoading
    }

    // Trigger on-demand loading based on library type
    androidx.compose.runtime.LaunchedEffect(libraryId, library?.collectionType) {
        library?.let { lib ->
            when (lib.collectionType) {
                org.jellyfin.sdk.model.api.CollectionType.MOVIES -> {
                    viewModel.loadLibraryTypeData(lib, LibraryType.MOVIES)
                }
                org.jellyfin.sdk.model.api.CollectionType.TVSHOWS -> {
                    viewModel.loadLibraryTypeData(lib, LibraryType.TV_SHOWS)
                }
                org.jellyfin.sdk.model.api.CollectionType.MUSIC -> {
                    viewModel.loadLibraryTypeData(lib, LibraryType.MUSIC)
                }
                org.jellyfin.sdk.model.api.CollectionType.HOMEVIDEOS -> {
                    viewModel.loadHomeVideos(lib.id.toString())
                }
                else -> {
                    // For other types, try loading as home videos
                    viewModel.loadHomeVideos(lib.id.toString())
                }
            }
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

        if (isLibraryLoading && items.isEmpty()) {
            com.rpeters.jellyfin.ui.components.tv.TvSkeletonCarousel(
                title = "Loading...",
                itemCount = 6,
            )
        } else if (items.isEmpty()) {
            com.rpeters.jellyfin.ui.components.tv.TvEmptyState(
                title = "No Items Found",
                message = "This library appears to be empty or still loading.",
                onAction = {
                    // Retry loading
                    library?.let { lib ->
                        when (lib.collectionType) {
                            org.jellyfin.sdk.model.api.CollectionType.MOVIES ->
                                viewModel.loadLibraryTypeData(lib, LibraryType.MOVIES, forceRefresh = true)
                            org.jellyfin.sdk.model.api.CollectionType.TVSHOWS ->
                                viewModel.loadLibraryTypeData(lib, LibraryType.TV_SHOWS, forceRefresh = true)
                            org.jellyfin.sdk.model.api.CollectionType.MUSIC ->
                                viewModel.loadLibraryTypeData(lib, LibraryType.MUSIC, forceRefresh = true)
                            else ->
                                viewModel.loadHomeVideos(lib.id.toString())
                        }
                    }
                },
                actionText = "Retry",
            )
        } else {
            com.rpeters.jellyfin.ui.components.tv.TvContentCarousel(
                items = items,
                title = "All Items",
                layoutConfig = layoutConfig,
                focusManager = focusManager,
                onItemSelect = { baseItem ->
                    baseItem.id?.toString()?.let(onItemSelect)
                },
            )
        }
    }
}
