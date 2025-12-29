package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.models.MovieFilter
import com.rpeters.jellyfin.data.models.MovieSortOrder
import com.rpeters.jellyfin.data.models.MovieViewMode
import com.rpeters.jellyfin.ui.components.ExpressiveCompactCard
import com.rpeters.jellyfin.ui.components.ExpressiveLoadingCard
import com.rpeters.jellyfin.ui.components.PosterMediaCard
import com.rpeters.jellyfin.ui.theme.MotionTokens
import com.rpeters.jellyfin.ui.theme.MovieRed
import com.rpeters.jellyfin.ui.theme.MusicGreen
import com.rpeters.jellyfin.utils.getItemKey
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
    modifier: Modifier = Modifier,
) {
    var showSortMenu by remember { mutableStateOf(false) }

    // Filter and sort movies
    val filteredAndSortedMovies = movies.filter { movie ->
        when (selectedFilter) {
            MovieFilter.ALL -> true
            MovieFilter.FAVORITES -> movie.userData?.isFavorite == true
            MovieFilter.UNWATCHED -> movie.userData?.played != true
            MovieFilter.WATCHED -> movie.userData?.played == true
            MovieFilter.RECENT_RELEASES -> {
                val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                (movie.productionYear as? Int ?: 0) >= currentYear - 2
            }

            MovieFilter.HIGH_RATED -> (movie.communityRating as? Double ?: 0.0) >= 7.5
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
            MovieSortOrder.YEAR -> (movie2.productionYear ?: 0).compareTo(
                movie1.productionYear ?: 0,
            )

            MovieSortOrder.RATING -> (movie2.communityRating ?: 0f).compareTo(
                movie1.communityRating ?: 0f,
            )

            MovieSortOrder.RECENTLY_ADDED -> movie2.dateCreated?.compareTo(
                movie1.dateCreated ?: java.time.LocalDateTime.MIN,
            ) ?: 0

            MovieSortOrder.RUNTIME -> (movie2.runTimeTicks ?: 0L).compareTo(
                movie1.runTimeTicks ?: 0L,
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "Movies",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                },
                actions = {
                    // View mode toggle
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        IconButton(
                            onClick = {
                                onViewModeChange(
                                    when (viewMode) {
                                        MovieViewMode.GRID -> MovieViewMode.LIST
                                        MovieViewMode.LIST -> MovieViewMode.GRID
                                    },
                                )
                            },
                        ) {
                            Icon(
                                imageVector = when (viewMode) {
                                    MovieViewMode.GRID -> Icons.AutoMirrored.Filled.ViewList
                                    MovieViewMode.LIST -> Icons.Default.GridView
                                },
                                contentDescription = "Toggle view mode",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // Sort menu
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                            ) {
                                MovieSortOrder.entries.forEach { sortOrder ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = sortOrder.displayNameResId)) },
                                        onClick = {
                                            onSortChange(sortOrder)
                                            showSortMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // Refresh button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                        modifier = Modifier.padding(end = 8.dp, start = 4.dp),
                    ) {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = MusicGreen,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { paddingValues ->
        // Determine current screen state
        val currentState = when {
            isLoading -> MovieScreenState.LOADING
            movies.isEmpty() -> MovieScreenState.EMPTY
            else -> MovieScreenState.CONTENT
        }

        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(paddingValues),
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
                        ExpressiveEmptyState(
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
                            selectedFilter = selectedFilter,
                            onFilterChange = onFilterChange,
                            viewMode = viewMode,
                            onMovieClick = onMovieClick,
                            getImageUrl = getImageUrl,
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

@Composable
private fun MoviesLoadingContent(
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            count = 20,
            key = { it },
        ) {
            ExpressiveLoadingCard(
                modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun MoviesContent(
    filteredAndSortedMovies: List<BaseItemDto>,
    selectedFilter: MovieFilter,
    onFilterChange: (MovieFilter) -> Unit,
    viewMode: MovieViewMode,
    onMovieClick: (BaseItemDto) -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Filter chips with enhanced styling and organization
        Column {
            // Basic Filters
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            ) {
                items(
                    items = MovieFilter.getBasicFilters(),
                    key = { it },
                ) { filter ->
                    FilterChip(
                        onClick = { onFilterChange(filter) },
                        label = {
                            Text(
                                text = stringResource(id = filter.displayNameResId),
                                fontWeight = if (selectedFilter == filter) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        },
                        selected = selectedFilter == filter,
                        leadingIcon = if (filter == MovieFilter.FAVORITES) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else {
                            null
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            // Smart & Genre Filters
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            ) {
                items(
                    items = MovieFilter.getSmartFilters() + MovieFilter.getGenreFilters(),
                    key = { it },
                ) { filter ->
                    FilterChip(
                        onClick = { onFilterChange(filter) },
                        label = {
                            Text(
                                text = stringResource(id = filter.displayNameResId),
                                fontWeight = if (selectedFilter == filter) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        },
                        selected = selectedFilter == filter,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (filter) {
                                in MovieFilter.getGenreFilters() -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.secondaryContainer
                            },
                            selectedLabelColor = when (filter) {
                                in MovieFilter.getGenreFilters() -> MaterialTheme.colorScheme.onTertiaryContainer
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            },
                        ),
                    )
                }
            }
        }

        // Movies grid/list with expressive animations
        when (viewMode) {
            MovieViewMode.GRID -> {
                MoviesGrid(
                    movies = filteredAndSortedMovies,
                    onMovieClick = onMovieClick,
                    getImageUrl = getImageUrl,
                    isLoadingMore = isLoadingMore,
                    hasMoreItems = hasMoreItems,
                    onLoadMore = onLoadMore,
                )
            }

            MovieViewMode.LIST -> {
                MoviesList(
                    movies = filteredAndSortedMovies,
                    onMovieClick = onMovieClick,
                    getImageUrl = getImageUrl,
                    isLoadingMore = isLoadingMore,
                    hasMoreItems = hasMoreItems,
                    onLoadMore = onLoadMore,
                )
            }
        }
    }
}

@Composable
private fun MoviesGrid(
    movies: List<BaseItemDto>,
    onMovieClick: (BaseItemDto) -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(movies, key = { it.getItemKey() }) { movie ->
            val scale by animateFloatAsState(
                targetValue = 1.0f,
                animationSpec = MotionTokens.expressiveEnter,
                label = "movie_card_scale",
            )

            PosterMediaCard(
                item = movie,
                getImageUrl = getImageUrl,
                onClick = onMovieClick,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
                showTitle = true,
                showMetadata = true,
            )
        }

        // Loading/pagination footer
        if (isLoadingMore || hasMoreItems) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                MoviesPaginationFooter(
                    isLoadingMore = isLoadingMore,
                    hasMoreItems = hasMoreItems,
                    onLoadMore = onLoadMore,
                )
            }
        }
    }
}

@Composable
private fun MoviesList(
    movies: List<BaseItemDto>,
    onMovieClick: (BaseItemDto) -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(movies, key = { it.getItemKey() }) { movie ->
            val scale by animateFloatAsState(
                targetValue = 1.0f,
                animationSpec = MotionTokens.expressiveEnter,
                label = "movie_list_card_scale",
            )

            ExpressiveCompactCard(
                title = movie.name ?: stringResource(R.string.unknown),
                subtitle = buildString {
                    movie.productionYear?.let { year ->
                        append(year)
                    }
                    movie.runTimeTicks?.let { ticks ->
                        val minutes = (ticks / 10_000_000 / 60).toInt()
                        val hours = minutes / 60
                        val remainingMinutes = minutes % 60
                        val runtime =
                            if (hours > 0) " • ${hours}h ${remainingMinutes}m" else " • ${minutes}m"
                        append(runtime)
                    }
                },
                imageUrl = getImageUrl(movie) ?: "",
                onClick = { onMovieClick(movie) },
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            )
        }

        // Loading/pagination footer
        if (isLoadingMore || hasMoreItems) {
            item {
                MoviesPaginationFooter(
                    isLoadingMore = isLoadingMore,
                    hasMoreItems = hasMoreItems,
                    onLoadMore = onLoadMore,
                )
            }
        }
    }
}

@Composable
private fun MoviesPaginationFooter(
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoadingMore) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    color = MovieRed,
                    modifier = Modifier.padding(8.dp),
                )
                Text(
                    text = "Loading more movies...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (hasMoreItems) {
            TextButton(onClick = onLoadMore) {
                Text("Load more")
            }
        } else {
            Text(
                text = "No more movies",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Expressive Empty State component
@Composable
private fun ExpressiveEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(48.dp),
        ) {
            val scale by animateFloatAsState(
                targetValue = 1.0f,
                animationSpec = MotionTokens.expressiveEnter,
                label = "empty_icon_scale",
            )

            Surface(
                shape = CircleShape,
                color = iconTint.copy(alpha = 0.1f),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .padding(24.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    tint = iconTint,
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
