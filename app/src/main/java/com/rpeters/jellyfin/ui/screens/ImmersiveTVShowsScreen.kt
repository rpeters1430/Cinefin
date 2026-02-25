package com.rpeters.jellyfin.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
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
import com.rpeters.jellyfin.ui.theme.SeriesBlue
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemKind

private val tvShowsSortOptions = listOf(
    ImmersiveSortOption(labelRes = R.string.sort_title_asc_shows, key = "alphabetical"),
    ImmersiveSortOption(labelRes = R.string.sort_date_added_desc_shows, key = "recently_added"),
    ImmersiveSortOption(labelRes = R.string.sort_year_desc_shows, key = "year_newest"),
)

@OptInAppExperimentalApis
@Composable
fun ImmersiveTVShowsScreenContainer(
    onTVShowClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
) {
    val appState by viewModel.appState.collectAsState()
    val tvShows = viewModel.getLibraryTypeData(LibraryType.TV_SHOWS)
    val recentEpisodes = appState.recentlyAddedByTypes[BaseItemKind.EPISODE.name] ?: emptyList()
    val libraryId = viewModel.getLibraryIdForType(LibraryType.TV_SHOWS)
    val paginationState = libraryId?.let { appState.libraryPaginationState[it] }

    var selectedSortIndex by remember { mutableIntStateOf(0) }
    val sortedShows = remember(tvShows, selectedSortIndex) {
        when (selectedSortIndex) {
            0 -> tvShows.sortedBy { (it.sortName ?: it.name).orEmpty().lowercase() }
            1 -> tvShows.sortedByDescending { it.dateCreated }
            2 -> tvShows.sortedByDescending { it.productionYear ?: 0 }
            else -> tvShows
        }
    }
    val featuredShows = remember(tvShows, recentEpisodes) {
        val fromRecentEpisodes = recentEpisodes
            .filter { it.seriesId != null || it.seriesName != null || it.name != null }
            .distinctBy { it.seriesId ?: it.seriesName ?: it.name }
        if (fromRecentEpisodes.isNotEmpty()) fromRecentEpisodes.take(5)
        else tvShows.sortedByDescending { it.dateCreated }.take(5)
    }

    val emptyTitle = stringResource(id = R.string.no_tv_shows_found)
    val emptySubtitle = stringResource(id = R.string.adjust_tv_shows_filters_hint)

    LaunchedEffect(Unit) {
        if (tvShows.isEmpty()) {
            viewModel.loadLibraryTypeData(LibraryType.TV_SHOWS, forceRefresh = false)
        }
    }

    ImmersiveLibraryBrowserScreen(
        items = sortedShows,
        featuredItems = featuredShows,
        isLoading = appState.isLoadingTVShows,
        isLoadingMore = paginationState?.isLoadingMore ?: false,
        hasMoreItems = paginationState?.hasMore ?: false,
        config = ImmersiveLibraryConfig(
            themeColor = SeriesBlue,
            emptyStateIcon = Icons.Default.Tv,
            emptyStateTitle = emptyTitle,
            emptyStateSubtitle = emptySubtitle,
        ),
        sortOptions = tvShowsSortOptions,
        selectedSortIndex = selectedSortIndex,
        onSortSelected = { selectedSortIndex = it },
        onLoadMore = { libraryId?.let(viewModel::loadMoreLibraryItems) },
        onItemClick = onTVShowClick,
        onCarouselItemClick = onTVShowClick,
        onRefresh = { viewModel.loadLibraryTypeData(LibraryType.TV_SHOWS, forceRefresh = true) },
        onSearchClick = onSearchClick,
        onBackClick = onBackClick,
        getImageUrl = { viewModel.getImageUrl(it) },
        buildCarouselItem = { item ->
            CarouselItem(
                id = (item.seriesId ?: item.id).toString(),
                title = item.seriesName ?: item.name ?: "Unknown",
                subtitle = if (item.seriesId != null) "New Episode Added" else (item.productionYear?.toString() ?: ""),
                imageUrl = viewModel.getBackdropUrl(item) ?: viewModel.getSeriesImageUrl(item) ?: viewModel.getImageUrl(item) ?: "",
            )
        },
        modifier = modifier,
    )
}
