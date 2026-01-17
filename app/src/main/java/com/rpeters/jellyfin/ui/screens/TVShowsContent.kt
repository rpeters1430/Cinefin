package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.components.ExpressiveCompactCard
import com.rpeters.jellyfin.ui.components.ExpressiveMediaCarousel
import com.rpeters.jellyfin.ui.components.MediaType
import com.rpeters.jellyfin.ui.components.PosterMediaCard
import com.rpeters.jellyfin.ui.theme.MotionTokens
import com.rpeters.jellyfin.ui.theme.SeriesBlue
import com.rpeters.jellyfin.utils.getItemKey
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
internal fun TVShowFilters(
    selectedFilter: TVShowFilter,
    onFilterSelected: (TVShowFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Basic Filters
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        ) {
            items(
                items = TVShowFilter.getBasicFilters(),
                key = { it.name },
                contentType = { "tv_basic_filter" },
            ) { filter ->
                FilterChip(
                    onClick = { onFilterSelected(filter) },
                    label = {
                        Text(
                            text = stringResource(id = filter.displayNameResId),
                            fontWeight = if (selectedFilter == filter) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    },
                    selected = selectedFilter == filter,
                    leadingIcon = if (filter == TVShowFilter.FAVORITES) {
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

        // Smart Filters
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        ) {
            items(
                items = TVShowFilter.getSmartFilters(),
                key = { it.name },
                contentType = { "tv_smart_filter" },
            ) { filter ->
                FilterChip(
                    onClick = { onFilterSelected(filter) },
                    label = {
                        Text(
                            text = stringResource(id = filter.displayNameResId),
                            fontWeight = if (selectedFilter == filter) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    },
                    selected = selectedFilter == filter,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
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
    onTVShowClick: (String) -> Unit,
    onTVShowLongPress: (BaseItemDto) -> Unit = {},
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = modifier.fillMaxSize(),
                ) {
                    items(
                        items = tvShows,
                        key = { tvShow -> tvShow.getItemKey() },
                        contentType = { "tv_grid_item" },
                    ) { tvShow ->
                        val scale by animateFloatAsState(
                            targetValue = 1.0f,
                            animationSpec = MotionTokens.expressiveEnter,
                            label = "tv_show_card_scale",
                        )

                        PosterMediaCard(
                            item = tvShow,
                            getImageUrl = { getImageUrl(it) },
                            onClick = { tvShow ->
                                onTVShowClick(tvShow.id.toString())
                            },
                            onLongPress = onTVShowLongPress,
                            showTitle = true,
                            showMetadata = true,
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
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
                    modifier = modifier.fillMaxSize(),
                ) {
                    items(
                        items = tvShows,
                        key = { tvShow -> tvShow.getItemKey() },
                        contentType = { "tv_list_item" },
                    ) { tvShow ->
                        val scale by animateFloatAsState(
                            targetValue = 1.0f,
                            animationSpec = MotionTokens.expressiveEnter,
                            label = "tv_show_list_card_scale",
                        )

                        ExpressiveCompactCard(
                            title = tvShow.name ?: stringResource(id = R.string.unknown),
                            subtitle = buildString {
                                tvShow.productionYear?.let { append("$it • ") }
                                tvShow.childCount?.let { append("$it episodes") }
                            },
                            imageUrl = getImageUrl(tvShow) ?: "",
                            onClick = {
                                onTVShowClick(tvShow.id.toString())
                            },
                            leadingIcon = if (tvShow.userData?.isFavorite == true) Icons.Default.Star else null,
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
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
                    title = "TV Shows",
                    items = carouselItems,
                    onItemClick = { item ->
                        onTVShowClick(item.id)
                    },
                    modifier = modifier,
                )
            }
        }
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
                    text = "Loading more TV shows...",
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
                text = "No more TV shows",
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
