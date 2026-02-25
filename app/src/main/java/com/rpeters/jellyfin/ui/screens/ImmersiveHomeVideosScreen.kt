package com.rpeters.jellyfin.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.theme.PhotoYellow
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType

private val homeVideosSortOptions = listOf(
    ImmersiveSortOption(labelRes = R.string.sort_title_asc, key = "name_asc"),
    ImmersiveSortOption(labelRes = R.string.sort_title_desc, key = "name_desc"),
    ImmersiveSortOption(labelRes = R.string.sort_date_added_desc, key = "date_added_desc"),
    ImmersiveSortOption(labelRes = R.string.sort_date_added_asc, key = "date_added_asc"),
    ImmersiveSortOption(labelRes = R.string.sort_date_created_desc, key = "date_created_desc"),
    ImmersiveSortOption(labelRes = R.string.sort_date_created_asc, key = "date_created_asc"),
)

@OptInAppExperimentalApis
@Composable
fun ImmersiveHomeVideosScreenContainer(
    onItemClick: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
) {
    val appState by viewModel.appState.collectAsState()

    val homeVideosLibraries = remember(appState.libraries) {
        appState.libraries.filter { it.collectionType == CollectionType.HOMEVIDEOS }
    }

    LaunchedEffect(homeVideosLibraries) {
        homeVideosLibraries.forEach { library ->
            val currentItems = appState.itemsByLibrary[library.id.toString()] ?: emptyList()
            if (currentItems.isEmpty()) {
                viewModel.loadHomeVideos(library.id.toString())
            }
        }
    }

    val homeVideosLibraryIds = remember(homeVideosLibraries) {
        homeVideosLibraries.map { it.id.toString() }
    }

    val homeVideosItems = remember(appState.itemsByLibrary, homeVideosLibraries) {
        homeVideosLibraries
            .flatMap { appState.itemsByLibrary[it.id.toString()] ?: emptyList() }
            .filter { it.type == BaseItemKind.VIDEO || it.type == BaseItemKind.MOVIE }
    }

    var selectedSortIndex by remember { mutableIntStateOf(0) }
    val sortedVideos = remember(homeVideosItems, selectedSortIndex) {
        sortHomeVideosByIndex(homeVideosItems, selectedSortIndex)
    }

    val featuredVideos = remember(sortedVideos) { sortedVideos.take(5) }

    val isLoadingMore = remember(appState.libraryPaginationState, homeVideosLibraryIds) {
        homeVideosLibraryIds.any { appState.libraryPaginationState[it]?.isLoadingMore == true }
    }
    val hasMoreItems = remember(appState.libraryPaginationState, homeVideosLibraryIds) {
        homeVideosLibraryIds.any { appState.libraryPaginationState[it]?.hasMore == true }
    }

    val emptyTitle = stringResource(id = R.string.no_home_videos_found)
    val emptySubtitle = stringResource(id = R.string.adjust_home_videos_filters_hint)

    ImmersiveLibraryBrowserScreen(
        items = sortedVideos,
        featuredItems = featuredVideos,
        isLoading = appState.isLoading,
        isLoadingMore = isLoadingMore,
        hasMoreItems = hasMoreItems,
        config = ImmersiveLibraryConfig(
            themeColor = PhotoYellow,
            emptyStateIcon = Icons.Default.Photo,
            emptyStateTitle = emptyTitle,
            emptyStateSubtitle = emptySubtitle,
        ),
        sortOptions = homeVideosSortOptions,
        selectedSortIndex = selectedSortIndex,
        onSortSelected = { selectedSortIndex = it },
        onLoadMore = { viewModel.loadMoreHomeVideos(homeVideosLibraries) },
        onItemClick = onItemClick,
        onCarouselItemClick = onItemClick,
        onRefresh = { viewModel.loadInitialData() },
        onSearchClick = { /* Home videos screen does not have a dedicated search action */ },
        onBackClick = onBackClick,
        getImageUrl = { viewModel.getImageUrl(it) },
        buildCarouselItem = { video ->
            CarouselItem(
                id = video.id.toString(),
                title = video.name ?: "Home Video",
                subtitle = video.productionYear?.toString() ?: "",
                imageUrl = viewModel.getBackdropUrl(video) ?: viewModel.getImageUrl(video) ?: "",
            )
        },
        errorMessage = appState.errorMessage,
        modifier = modifier,
    )
}

private fun sortHomeVideosByIndex(videos: List<BaseItemDto>, index: Int): List<BaseItemDto> =
    when (index) {
        0 -> videos.sortedBy { it.sortName ?: it.name }
        1 -> videos.sortedByDescending { it.sortName ?: it.name }
        2 -> videos.sortedByDescending { it.dateCreated }
        3 -> videos.sortedBy { it.dateCreated }
        4 -> videos.sortedByDescending { it.premiereDate ?: it.dateCreated }
        5 -> videos.sortedBy { it.premiereDate ?: it.dateCreated }
        else -> videos
    }
