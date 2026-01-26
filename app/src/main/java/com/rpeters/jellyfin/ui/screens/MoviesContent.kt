package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.models.MovieFilter
import com.rpeters.jellyfin.data.models.MovieViewMode
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.components.ExpressiveCompactCard
import com.rpeters.jellyfin.ui.components.ExpressiveHeroCarousel
import com.rpeters.jellyfin.ui.components.ExpressiveLoadingCard
import com.rpeters.jellyfin.ui.components.MediaType
import com.rpeters.jellyfin.ui.components.PosterMediaCard
import com.rpeters.jellyfin.ui.theme.MotionTokens
import com.rpeters.jellyfin.ui.theme.MovieRed
import com.rpeters.jellyfin.utils.getItemKey
import org.jellyfin.sdk.model.api.BaseItemDto
import kotlin.math.roundToInt

@Composable
internal fun MoviesLoadingContent(
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            count = 20,
            key = { it },
            contentType = { "movies_loading_item" },
        ) {
            ExpressiveLoadingCard(
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun MoviesContent(
    filteredAndSortedMovies: List<BaseItemDto>,
    viewMode: MovieViewMode,
    onMovieClick: (BaseItemDto) -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Featured movies carousel - top 5 highly rated movies
        val featuredMovies = remember(filteredAndSortedMovies) {
            filteredAndSortedMovies
                .filter { it.communityRating != null && it.communityRating!! >= 7.5f }
                .sortedByDescending { it.communityRating }
                .take(5)
        }

        if (featuredMovies.isNotEmpty()) {
            ExpressiveHeroCarousel(
                items = featuredMovies.map { movie ->
                    CarouselItem(
                        id = movie.id.toString(),
                        title = movie.name ?: "Unknown",
                        subtitle = buildMovieSubtitle(movie),
                        imageUrl = getBackdropUrl(movie) ?: getImageUrl(movie) ?: "",
                        type = MediaType.MOVIE,
                    )
                },
                onItemClick = { item ->
                    featuredMovies.find { it.id.toString() == item.id }?.let { movie ->
                        onMovieClick(movie)
                    }
                },
                onPlayClick = { item ->
                    featuredMovies.find { it.id.toString() == item.id }?.let { movie ->
                        onMovieClick(movie)
                    }
                },
                heroHeight = 220.dp,
                modifier = Modifier.padding(bottom = 16.dp),
            )
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
internal fun MoviesGrid(
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
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            items = movies,
            key = { it.getItemKey() },
            contentType = { "movies_grid_item" },
        ) { movie ->
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
internal fun MoviesList(
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
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = movies,
            key = { it.getItemKey() },
            contentType = { "movies_list_item" },
        ) { movie ->
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
internal fun MoviesPaginationFooter(
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

/**
 * Build subtitle text for movie carousel items
 */
private fun buildMovieSubtitle(movie: BaseItemDto): String = buildString {
    movie.productionYear?.let { year ->
        append(year)
    }
    movie.communityRating?.let { rating ->
        if (isNotEmpty()) append(" • ")
        append("★ ")
        append((rating * 10).roundToInt() / 10f)
    }
    movie.runTimeTicks?.let { ticks ->
        val minutes = (ticks / 10_000_000 / 60).toInt()
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        if (isNotEmpty()) append(" • ")
        append(if (hours > 0) "${hours}h ${remainingMinutes}m" else "${minutes}m")
    }
}

@Composable
internal fun MoviesEmptyState(
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
