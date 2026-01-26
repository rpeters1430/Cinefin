package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.components.ExpressiveHeroCarousel
import com.rpeters.jellyfin.ui.components.ExpressiveMediaCard
import com.rpeters.jellyfin.ui.components.ExpressiveMediaCarousel
import com.rpeters.jellyfin.ui.components.ExpressiveMediaListItem
import com.rpeters.jellyfin.ui.components.ExpressiveSegmentedListItem
import com.rpeters.jellyfin.ui.components.MediaType
import com.rpeters.jellyfin.ui.components.WatchedIndicatorBadge
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.image.rememberCoilSize
import com.rpeters.jellyfin.ui.theme.MotionTokens
import com.rpeters.jellyfin.ui.theme.SeriesBlue
import com.rpeters.jellyfin.utils.getItemKey
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun TVShowFilters(
    selectedFilter: TVShowFilter,
    onFilterSelected: (TVShowFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(id = R.string.filters),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        val basicSegmentLabel = stringResource(id = R.string.filter_segment_basic)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(
                items = TVShowFilter.getBasicFilters(),
                key = { it.name },
                contentType = { "tv_basic_filter" },
            ) { filter ->
                ExpressiveSegmentedListItem(
                    title = stringResource(id = filter.displayNameResId),
                    segment = basicSegmentLabel,
                    isSelected = selectedFilter == filter,
                    onClick = { onFilterSelected(filter) },
                    modifier = Modifier.width(220.dp),
                )
            }
        }

        Text(
            text = stringResource(id = R.string.filters_smart),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        val smartSegmentLabel = stringResource(id = R.string.filter_segment_smart)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(
                items = TVShowFilter.getSmartFilters(),
                key = { it.name },
                contentType = { "tv_smart_filter" },
            ) { filter ->
                ExpressiveSegmentedListItem(
                    title = stringResource(id = filter.displayNameResId),
                    segment = smartSegmentLabel,
                    isSelected = selectedFilter == filter,
                    onClick = { onFilterSelected(filter) },
                    modifier = Modifier.width(220.dp),
                )
            }
        }
    }
}

@Composable
internal fun TVShowsContent(
    tvShows: List<BaseItemDto>,
    viewMode: TVShowViewMode,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    onTVShowClick: (String) -> Unit,
    onTVShowLongPress: (BaseItemDto) -> Unit = {},
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Featured shows carousel - top 5 highly rated shows
    val featuredShows = remember(tvShows) {
        tvShows
            .filter { it.communityRating != null && it.communityRating!! >= 7.5f }
            .sortedByDescending { it.communityRating }
            .take(5)
    }

    Column(modifier = modifier) {
        if (featuredShows.isNotEmpty() && viewMode == TVShowViewMode.GRID) {
            ExpressiveHeroCarousel(
                items = featuredShows.map { show ->
                    CarouselItem(
                        id = show.id.toString(),
                        title = show.name ?: "Unknown",
                        subtitle = buildTVShowSubtitle(show),
                        imageUrl = getBackdropUrl(show) ?: getImageUrl(show) ?: "",
                        type = MediaType.TV_SHOW,
                    )
                },
                onItemClick = { item ->
                    onTVShowClick(item.id)
                },
                onPlayClick = { item ->
                    onTVShowClick(item.id)
                },
                heroHeight = 220.dp,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        AnimatedContent(
            targetState = viewMode,
            transitionSpec = {
                fadeIn(MotionTokens.expressiveEnter) togetherWith
                    fadeOut(MotionTokens.expressiveExit)
            },
            label = "view_mode_transition",
        ) { currentViewMode ->
            when (currentViewMode) {
                TVShowViewMode.GRID -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(200.dp),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    ) {
                        items(
                            items = tvShows,
                            key = { tvShow -> tvShow.getItemKey() },
                            contentType = { "tv_grid_item" },
                        ) { tvShow ->
                            ExpressiveMediaCard(
                                title = tvShow.name ?: stringResource(id = R.string.unknown),
                                subtitle = tvShow.productionYear?.toString().orEmpty(),
                                imageUrl = getImageUrl(tvShow) ?: "",
                                rating = tvShow.communityRating,
                                isFavorite = tvShow.userData?.isFavorite == true,
                                isWatched = tvShow.userData?.played == true,
                                watchProgress = ((tvShow.userData?.playedPercentage ?: 0.0) / 100.0).toFloat(),
                                unwatchedEpisodeCount = tvShow.userData?.unplayedItemCount,
                                onCardClick = { onTVShowClick(tvShow.id.toString()) },
                                onMoreClick = { onTVShowLongPress(tvShow) },
                            )
                        }

                        if (hasMoreItems || isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                TVShowsPaginationFooter(
                                    isLoadingMore = isLoadingMore,
                                    hasMoreItems = hasMoreItems,
                                    onLoadMore = onLoadMore,
                                )
                            }
                        }
                    }
                }

                TVShowViewMode.LIST -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    ) {
                        items(
                            items = tvShows,
                            key = { tvShow -> tvShow.getItemKey() },
                            contentType = { "tv_list_item" },
                        ) { tvShow ->
                            ExpressiveMediaListItem(
                                title = tvShow.name ?: stringResource(id = R.string.unknown),
                                subtitle = buildString {
                                    tvShow.childCount?.let { count -> append("$count episodes") }
                                    tvShow.communityRating?.let { rating ->
                                        if (isNotEmpty()) append(" • ")
                                        append(String.format(Locale.ROOT, "%.1f★", rating))
                                    }
                                },
                                overline = tvShow.productionYear?.toString(),
                                leadingContent = {
                                    val listImageUrl = getBackdropUrl(tvShow) ?: getImageUrl(tvShow)
                                    JellyfinAsyncImage(
                                        model = listImageUrl,
                                        contentDescription = tvShow.name,
                                        modifier = Modifier
                                            .width(128.dp)
                                            .height(72.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop,
                                        requestSize = rememberCoilSize(128.dp, 72.dp),
                                    )
                                },
                                trailingContent = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (tvShow.userData?.isFavorite == true) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = stringResource(id = R.string.favorites),
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        if (tvShow.userData?.played == true) {
                                            WatchedIndicatorBadge(
                                                item = tvShow,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                },
                                onClick = { onTVShowClick(tvShow.id.toString()) },
                                onLongClick = { onTVShowLongPress(tvShow) },
                            )
                        }

                        if (hasMoreItems || isLoadingMore) {
                            item {
                                TVShowsPaginationFooter(
                                    isLoadingMore = isLoadingMore,
                                    hasMoreItems = hasMoreItems,
                                    onLoadMore = onLoadMore,
                                )
                            }
                        }
                    }
                }
                TVShowViewMode.CAROUSEL -> {
                    val unknownText = stringResource(id = R.string.unknown)
                    val carouselItems = remember(tvShows, unknownText) {
                        tvShows.take(20).map { tvShow ->
                            CarouselItem(
                                id = tvShow.id.toString(),
                                title = tvShow.name ?: unknownText,
                                subtitle = tvShow.productionYear?.toString() ?: "",
                                imageUrl = getImageUrl(tvShow) ?: "",
                                type = MediaType.TV_SHOW,
                            )
                        }
                    }

                    ExpressiveMediaCarousel(
                        title = stringResource(id = R.string.tv_shows),
                        items = carouselItems,
                        onItemClick = { item ->
                            onTVShowClick(item.id)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    )
                }
            }
        }
    }
}

/**
 * Build subtitle text for TV show carousel items
 */
private fun buildTVShowSubtitle(show: BaseItemDto): String = buildString {
    show.productionYear?.let { year ->
        append(year)
    }
    show.communityRating?.let { rating ->
        if (isNotEmpty()) append(" • ")
        append("★ ")
        append((rating * 10).roundToInt() / 10f)
    }
    show.childCount?.let { count ->
        if (isNotEmpty()) append(" • ")
        append("$count episodes")
    }
    show.status?.let { status ->
        if (isNotEmpty()) append(" • ")
        append(status)
    }
}

@Composable
internal fun TVShowsPaginationFooter(
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
                    color = SeriesBlue,
                    modifier = Modifier.padding(8.dp),
                )
                Text(
                    text = stringResource(id = R.string.loading_more_tv_shows),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (hasMoreItems) {
            TextButton(onClick = onLoadMore) {
                Text(stringResource(id = R.string.retry))
            }
        } else {
            Text(
                text = stringResource(id = R.string.no_more_tv_shows),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun TVShowsErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TVShowsEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconTint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            val scale by animateFloatAsState(
                targetValue = 1.0f,
                animationSpec = MotionTokens.expressiveEnter,
                label = "empty_icon_scale",
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(32.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                tint = iconTint.copy(alpha = 0.6f),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
