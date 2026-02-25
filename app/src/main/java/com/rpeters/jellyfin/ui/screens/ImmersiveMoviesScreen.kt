package com.rpeters.jellyfin.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.theme.MovieRed
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel

private val moviesSortOptions = listOf(
    ImmersiveSortOption(labelRes = R.string.sort_title_asc, key = "alphabetical"),
    ImmersiveSortOption(labelRes = R.string.sort_date_added_desc, key = "recently_added"),
    ImmersiveSortOption(labelRes = R.string.sort_year_desc, key = "year_newest"),
)

@OptInAppExperimentalApis
@Composable
fun ImmersiveMoviesScreenContainer(
    onMovieClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
) {
    val appState by viewModel.appState.collectAsState()
    val movies = viewModel.getLibraryTypeData(LibraryType.MOVIES)
    val libraryId = viewModel.getLibraryIdForType(LibraryType.MOVIES)
    val paginationState = libraryId?.let { appState.libraryPaginationState[it] }

    var selectedSortIndex by remember { mutableIntStateOf(0) }
    val sortedMovies = remember(movies, selectedSortIndex) {
        when (selectedSortIndex) {
            0 -> movies.sortedBy { (it.sortName ?: it.name).orEmpty().lowercase() }
            1 -> movies.sortedByDescending { it.dateCreated }
            2 -> movies.sortedByDescending { it.productionYear ?: 0 }
            else -> movies
        }
    }
    val featuredMovies = remember(movies) {
        movies.sortedByDescending { it.dateCreated }.take(5)
    }

    LaunchedEffect(Unit) {
        if (movies.isEmpty()) {
            viewModel.loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = false)
        }
    }

    ImmersiveLibraryBrowserScreen(
        items = sortedMovies,
        featuredItems = featuredMovies,
        isLoading = appState.isLoadingMovies,
        isLoadingMore = paginationState?.isLoadingMore ?: false,
        hasMoreItems = paginationState?.hasMore ?: false,
        config = ImmersiveLibraryConfig(
            themeColor = MovieRed,
            emptyStateIcon = Icons.Default.Movie,
            emptyStateTitle = "No movies found",
            emptyStateSubtitle = "Try adding some movies to your library",
        ),
        sortOptions = moviesSortOptions,
        selectedSortIndex = selectedSortIndex,
        onSortSelected = { selectedSortIndex = it },
        onLoadMore = { libraryId?.let(viewModel::loadMoreLibraryItems) },
        onItemClick = onMovieClick,
        onCarouselItemClick = onMovieClick,
        onRefresh = { viewModel.loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = true) },
        onSearchClick = onSearchClick,
        onBackClick = onBackClick,
        getImageUrl = { viewModel.getImageUrl(it) },
        buildCarouselItem = { movie ->
            CarouselItem(
                id = movie.id.toString(),
                title = movie.name ?: "Unknown",
                subtitle = movie.productionYear?.toString() ?: "",
                imageUrl = viewModel.getBackdropUrl(movie) ?: viewModel.getImageUrl(movie) ?: "",
            )
        },
        modifier = modifier,
    )
}
