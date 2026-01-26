package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.data.models.MovieFilter
import com.rpeters.jellyfin.data.models.MovieSortOrder
import com.rpeters.jellyfin.data.models.MovieViewMode
import com.rpeters.jellyfin.ui.components.ExpressivePullToRefreshBox
import com.rpeters.jellyfin.ui.theme.MovieRed
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemDto

@OptInAppExperimentalApis
@Composable
fun MoviesScreen(
    movies: List<BaseItemDto>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    selectedFilter: MovieFilter,
    onFilterChange: (MovieFilter) -> Unit,
    selectedSort: MovieSortOrder,
    onSortChange: (MovieSortOrder) -> Unit,
    viewMode: MovieViewMode,
    onViewModeChange: (MovieViewMode) -> Unit,
    onMovieClick: (BaseItemDto) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier,
) {
    var showSortMenu by remember { mutableStateOf(false) }

    // Filter and sort movies
    val filteredAndSortedMovies = remember(movies, selectedFilter, selectedSort) {
        filterAndSortMovies(
            movies = movies,
            selectedFilter = selectedFilter,
            selectedSort = selectedSort,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MoviesTopBar(
                viewMode = viewMode,
                onViewModeChange = onViewModeChange,
                showSortMenu = showSortMenu,
                onShowSortMenuChange = { showSortMenu = it },
                onSortChange = onSortChange,
                onRefresh = onRefresh,
            )
        },
    ) { paddingValues ->
        // Determine current screen state
        val currentState = when {
            isLoading -> MovieScreenState.LOADING
            movies.isEmpty() -> MovieScreenState.EMPTY
            else -> MovieScreenState.CONTENT
        }

        ExpressivePullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            indicatorColor = MovieRed,
            indicatorSize = 52.dp,
            useWavyIndicator = true,
        ) {
            AnimatedContent(
                targetState = currentState,
                transitionSpec = {
                    fadeIn() + slideInVertically { it / 2 } togetherWith fadeOut() + slideOutVertically { it / 2 }
                },
                label = "movies_content_animation",
            ) { state ->
                when (state) {
                    MovieScreenState.LOADING -> {
                        MoviesLoadingContent(
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    MovieScreenState.EMPTY -> {
                        MoviesEmptyState(
                            icon = Icons.Default.Movie,
                            title = "No movies found",
                            subtitle = "Try adjusting your filters or add some movies to your library",
                            iconTint = MovieRed,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    MovieScreenState.CONTENT -> {
                        MoviesContent(
                            filteredAndSortedMovies = filteredAndSortedMovies,
                            viewMode = viewMode,
                            onMovieClick = onMovieClick,
                            getImageUrl = getImageUrl,
                            getBackdropUrl = getBackdropUrl,
                            isLoadingMore = isLoadingMore,
                            hasMoreItems = hasMoreItems,
                            onLoadMore = onLoadMore,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

private fun filterAndSortMovies(
    movies: List<BaseItemDto>,
    selectedFilter: MovieFilter,
    selectedSort: MovieSortOrder,
): List<BaseItemDto> {
    return movies.filter { movie ->
        when (selectedFilter) {
            MovieFilter.ALL -> true
            MovieFilter.FAVORITES -> movie.userData?.isFavorite == true
            MovieFilter.UNWATCHED -> movie.userData?.played != true
            MovieFilter.WATCHED -> movie.userData?.played == true
            MovieFilter.RECENT_RELEASES -> {
                val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                (movie.productionYear ?: 0) >= currentYear - 2
            }

            MovieFilter.HIGH_RATED -> (movie.communityRating?.toDouble() ?: 0.0) >= 7.5
            MovieFilter.ACTION -> movie.genres?.any {
                it.contains(
                    "Action",
                    ignoreCase = true,
                )
            } == true

            MovieFilter.COMEDY -> movie.genres?.any {
                it.contains(
                    "Comedy",
                    ignoreCase = true,
                )
            } == true

            MovieFilter.DRAMA -> movie.genres?.any {
                it.contains(
                    "Drama",
                    ignoreCase = true,
                )
            } == true

            MovieFilter.SCI_FI -> movie.genres?.any {
                it.contains("Science Fiction", ignoreCase = true) ||
                    it.contains("Sci-Fi", ignoreCase = true) ||
                    it.contains("Fantasy", ignoreCase = true)
            } == true
        }
    }.sortedWith { movie1, movie2 ->
        when (selectedSort) {
            MovieSortOrder.NAME -> (movie1.name ?: "").compareTo(movie2.name ?: "")
            MovieSortOrder.YEAR -> ((movie2.productionYear as? Number)?.toInt() ?: 0).compareTo(
                (movie1.productionYear as? Number)?.toInt() ?: 0,
            )

            MovieSortOrder.RATING -> (movie2.communityRating?.toDouble() ?: 0.0).compareTo(
                movie1.communityRating?.toDouble() ?: 0.0,
            )

            MovieSortOrder.RECENTLY_ADDED -> movie2.dateCreated?.compareTo(
                movie1.dateCreated ?: java.time.LocalDateTime.MIN,
            ) ?: 0

            MovieSortOrder.RUNTIME -> (movie2.runTimeTicks ?: 0L).compareTo(
                movie1.runTimeTicks ?: 0L,
            )
        }
    }
}

// Content state enum for animated transitions
enum class MovieScreenState {
    LOADING,
    EMPTY,
    CONTENT,
}

/**
 * ViewModel-connected wrapper for MoviesScreen
 * Used in navigation to display movies from the ViewModel
 */
@OptInAppExperimentalApis
@Composable
fun MoviesScreenContainer(
    onMovieClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
) {
    val appState by viewModel.appState.collectAsState()
    var selectedFilter by remember { mutableStateOf(MovieFilter.ALL) }
    var selectedSort by remember { mutableStateOf(MovieSortOrder.NAME) }
    var viewMode by remember { mutableStateOf(MovieViewMode.GRID) }

    // Load movies data when libraries are available
    LaunchedEffect(appState.libraries) {
        if (appState.libraries.isNotEmpty()) {
            if (com.rpeters.jellyfin.BuildConfig.DEBUG) {
                android.util.Log.d("MoviesScreenContainer", "Libraries loaded: ${appState.libraries.size}, triggering movie data load")
            }
            // Small delay to ensure state is properly set
            kotlinx.coroutines.delay(100)
            viewModel.loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = false)
        } else {
            if (com.rpeters.jellyfin.BuildConfig.DEBUG) {
                android.util.Log.d("MoviesScreenContainer", "Libraries not yet loaded, waiting...")
            }
        }
    }

    // Additional safety check: if libraries are empty but we should be loading, trigger library loading
    LaunchedEffect(Unit) {
        if (appState.libraries.isEmpty()) {
            if (com.rpeters.jellyfin.BuildConfig.DEBUG) {
                android.util.Log.d("MoviesScreenContainer", "Libraries empty on composition, ensuring libraries are loaded")
            }
            // This will trigger library loading if not already done
            viewModel.loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = false)
        }
    }

    val movies = viewModel.getLibraryTypeData(LibraryType.MOVIES)
    val isLoading = appState.isLoadingMovies
    val isLoadingMore = appState.isLoadingMore
    val hasMoreItems = appState.hasMoreMovies

    // Debug logging to track state
    LaunchedEffect(movies.size, isLoading) {
        if (com.rpeters.jellyfin.BuildConfig.DEBUG) {
            android.util.Log.d("MoviesScreenContainer", "State: movies=${movies.size}, isLoading=$isLoading, libraries=${appState.libraries.size}")
        }
    }

    MoviesScreen(
        movies = movies,
        isLoading = isLoading,
        isLoadingMore = isLoadingMore,
        hasMoreItems = hasMoreItems,
        selectedFilter = selectedFilter,
        onFilterChange = { selectedFilter = it },
        selectedSort = selectedSort,
        onSortChange = { selectedSort = it },
        viewMode = viewMode,
        onViewModeChange = { viewMode = it },
        onMovieClick = onMovieClick,
        onRefresh = { viewModel.loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = true) },
        onLoadMore = { viewModel.loadMoreMovies() },
        getImageUrl = { viewModel.getImageUrl(it) },
        getBackdropUrl = { viewModel.getBackdropUrl(it) },
        modifier = modifier,
    )
}
