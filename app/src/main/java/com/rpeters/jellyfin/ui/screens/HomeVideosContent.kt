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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.models.HomeVideoFilter
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.components.ExpressiveHeroCarousel
import com.rpeters.jellyfin.ui.components.ExpressiveLoadingCard
import com.rpeters.jellyfin.ui.components.ExpressiveMediaCard
import com.rpeters.jellyfin.ui.components.ExpressiveMediaCarousel
import com.rpeters.jellyfin.ui.components.ExpressiveMediaListItem
import com.rpeters.jellyfin.ui.components.ExpressiveSegmentedListItem
import com.rpeters.jellyfin.ui.components.MediaType
import com.rpeters.jellyfin.ui.components.WatchedIndicatorBadge
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.image.rememberCoilSize
import com.rpeters.jellyfin.ui.theme.MotionTokens
import com.rpeters.jellyfin.ui.theme.PhotoYellow
import com.rpeters.jellyfin.utils.getItemKey
import org.jellyfin.sdk.model.api.BaseItemDto
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun HomeVideosLoadingContent(
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
            contentType = { "home_videos_loading_item" },
        ) {
            ExpressiveLoadingCard(
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun HomeVideosContent(
    homeVideos: List<BaseItemDto>,
    viewMode: com.rpeters.jellyfin.data.models.HomeVideoViewMode,
    onHomeVideoClick: (String) -> Unit,
    onHomeVideoLongPress: (BaseItemDto) -> Unit = {},
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Featured videos carousel - most recent or favorites
    val featuredVideos = remember(homeVideos) {
        homeVideos
            .sortedByDescending { it.dateCreated }
            .take(5)
    }

    Column(modifier = modifier) {
        if (featuredVideos.isNotEmpty() && viewMode == com.rpeters.jellyfin.data.models.HomeVideoViewMode.GRID) {
            ExpressiveHeroCarousel(
                items = featuredVideos.map { video ->
                    CarouselItem(
                        id = video.id.toString(),
                        title = video.name ?: "Unknown",
                        subtitle = buildHomeVideoSubtitle(video),
                        imageUrl = getBackdropUrl(video) ?: getImageUrl(video) ?: "",
                        type = MediaType.VIDEO,
                    )
                },
                onItemClick = { item ->
                    onHomeVideoClick(item.id)
                },
                onPlayClick = { item ->
                    onHomeVideoClick(item.id)
                },
                heroHeight = 220.dp,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        // Home videos grid/list/carousel with expressive animations
        AnimatedContent(
            targetState = viewMode,
            transitionSpec = {
                fadeIn(MotionTokens.expressiveEnter) togetherWith
                    fadeOut(MotionTokens.expressiveExit)
            },
            label = "view_mode_transition",
        ) { currentViewMode ->
            when (currentViewMode) {
                com.rpeters.jellyfin.data.models.HomeVideoViewMode.GRID -> {
                    HomeVideosGrid(
                        homeVideos = homeVideos,
                        onHomeVideoClick = onHomeVideoClick,
                        onHomeVideoLongPress = onHomeVideoLongPress,
                        getImageUrl = getImageUrl,
                        isLoadingMore = isLoadingMore,
                        hasMoreItems = hasMoreItems,
                        onLoadMore = onLoadMore,
                    )
                }

                com.rpeters.jellyfin.data.models.HomeVideoViewMode.LIST -> {
                    HomeVideosList(
                        homeVideos = homeVideos,
                        onHomeVideoClick = onHomeVideoClick,
                        onHomeVideoLongPress = onHomeVideoLongPress,
                        getImageUrl = getImageUrl,
                        isLoadingMore = isLoadingMore,
                        hasMoreItems = hasMoreItems,
                        onLoadMore = onLoadMore,
                    )
                }

                com.rpeters.jellyfin.data.models.HomeVideoViewMode.CAROUSEL -> {
                    val unknownText = stringResource(id = R.string.unknown)
                    val carouselItems = remember(homeVideos, unknownText) {
                        homeVideos.take(20).map { video ->
                            CarouselItem(
                                id = video.id.toString(),
                                title = video.name ?: unknownText,
                                subtitle = buildHomeVideoSubtitle(video),
                                imageUrl = getImageUrl(video) ?: "",
                                type = MediaType.VIDEO,
                            )
                        }
                    }

                    ExpressiveMediaCarousel(
                        title = stringResource(R.string.home_videos),
                        items = carouselItems,
                        onItemClick = { item ->
                            onHomeVideoClick(item.id)
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

@Composable
internal fun HomeVideosGrid(
    homeVideos: List<BaseItemDto>,
    onHomeVideoClick: (String) -> Unit,
    onHomeVideoLongPress: (BaseItemDto) -> Unit = {},
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
            items = homeVideos,
            key = { it.getItemKey() },
            contentType = { "home_videos_grid_item" },
        ) { homeVideo ->
            val scale by animateFloatAsState(
                targetValue = 1.0f,
                animationSpec = MotionTokens.expressiveEnter,
                label = "home_video_card_scale",
            )

            ExpressiveMediaCard(
                title = homeVideo.name ?: stringResource(R.string.unknown),
                subtitle = homeVideo.productionYear?.toString() ?: "",
                imageUrl = getImageUrl(homeVideo) ?: "",
                rating = homeVideo.communityRating?.toFloat(),
                isFavorite = homeVideo.userData?.isFavorite == true,
                isWatched = homeVideo.userData?.played == true,
                watchProgress = ((homeVideo.userData?.playedPercentage ?: 0.0) / 100.0).toFloat(),
                onCardClick = { onHomeVideoClick(homeVideo.id.toString()) },
                onMoreClick = { onHomeVideoLongPress(homeVideo) },
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            )
        }

        // Loading/pagination footer
        if (isLoadingMore || hasMoreItems) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HomeVideosPaginationFooter(
                    isLoadingMore = isLoadingMore,
                    hasMoreItems = hasMoreItems,
                    onLoadMore = onLoadMore,
                )
            }
        }
    }
}

@Composable
internal fun HomeVideosList(
    homeVideos: List<BaseItemDto>,
    onHomeVideoClick: (String) -> Unit,
    onHomeVideoLongPress: (BaseItemDto) -> Unit = {},
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
            items = homeVideos,
            key = { it.getItemKey() },
            contentType = { "home_videos_list_item" },
        ) { homeVideo ->
            val scale by animateFloatAsState(
                targetValue = 1.0f,
                animationSpec = MotionTokens.expressiveEnter,
                label = "home_video_list_card_scale",
            )

            ExpressiveMediaListItem(
                title = homeVideo.name ?: stringResource(R.string.unknown),
                subtitle = buildString {
                    homeVideo.runTimeTicks?.let { ticks ->
                        val minutes = (ticks / 10_000_000 / 60).toInt()
                        val hours = minutes / 60
                        val remainingMinutes = minutes % 60
                        if (hours > 0) {
                            append("${hours}h ${remainingMinutes}m")
                        } else {
                            append("${minutes}m")
                        }
                    }
                },
                overline = homeVideo.productionYear?.toString(),
                leadingContent = {
                    JellyfinAsyncImage(
                        model = getImageUrl(homeVideo),
                        contentDescription = homeVideo.name,
                        modifier = Modifier
                            .width(108.dp)
                            .height(72.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                        requestSize = rememberCoilSize(108.dp, 72.dp),
                    )
                },
                trailingContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (homeVideo.userData?.isFavorite == true) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = stringResource(id = R.string.favorites),
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        if (homeVideo.userData?.played == true) {
                            WatchedIndicatorBadge(
                                item = homeVideo,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                onClick = { onHomeVideoClick(homeVideo.id.toString()) },
                onLongClick = { onHomeVideoLongPress(homeVideo) },
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            )
        }

        // Loading/pagination footer
        if (isLoadingMore || hasMoreItems) {
            item {
                HomeVideosPaginationFooter(
                    isLoadingMore = isLoadingMore,
                    hasMoreItems = hasMoreItems,
                    onLoadMore = onLoadMore,
                )
            }
        }
    }
}

@Composable
internal fun HomeVideosPaginationFooter(
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
                    color = PhotoYellow,
                    modifier = Modifier.padding(8.dp),
                )
                Text(
                    text = stringResource(R.string.loading_more_home_videos),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (hasMoreItems) {
            TextButton(onClick = onLoadMore) {
                Text(stringResource(R.string.load_more))
            }
        } else {
            Text(
                text = stringResource(R.string.no_more_home_videos),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Build subtitle text for home video carousel items
 */
private fun buildHomeVideoSubtitle(video: BaseItemDto): String = buildString {
    video.productionYear?.let { year ->
        append(year)
    }
    video.dateCreated?.let { date ->
        if (isNotEmpty()) append(" • ")
        try {
            val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            append(date.format(formatter))
        } catch (e: CancellationException) {
            throw e
        }
    }
    video.runTimeTicks?.let { ticks ->
        val minutes = (ticks / 10_000_000 / 60).toInt()
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        if (isNotEmpty()) append(" • ")
        append(if (hours > 0) "${hours}h ${remainingMinutes}m" else "${minutes}m")
    }
}

@Composable
internal fun HomeVideosFilters(
    selectedFilter: HomeVideoFilter,
    onFilterSelected: (HomeVideoFilter) -> Unit,
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
                items = HomeVideoFilter.getBasicFilters(),
                key = { it.name },
                contentType = { "home_video_basic_filter" },
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

        if (HomeVideoFilter.getSmartFilters().isNotEmpty()) {
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
                    items = HomeVideoFilter.getSmartFilters(),
                    key = { it.name },
                    contentType = { "home_video_smart_filter" },
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
}
