package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.image.ImageQuality
import com.rpeters.jellyfin.ui.image.ImageSize
import com.rpeters.jellyfin.ui.image.OptimizedImage
import com.rpeters.jellyfin.ui.theme.Dimens
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Performance-optimized carousel with lazy loading, image preloading, and memory management.
 */
@Composable
fun PerformanceOptimizedCarousel(
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String? = getImageUrl,
    onItemClick: (BaseItemDto) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    maxVisibleItems: Int = 50, // Limit items for performance
) {
    if (items.isEmpty()) return

    // Limit items to prevent memory issues
    val limitedItems = remember(items) { items.take(maxVisibleItems) }

    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = Dimens.Spacing16, vertical = Dimens.Spacing8),
        )

        val listState = rememberLazyListState()

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
            contentPadding = PaddingValues(horizontal = Dimens.Spacing16),
        ) {
            itemsIndexed(
                items = limitedItems,
                key = { _, item -> item.id.toString() },
            ) { index, item ->
                OptimizedCarouselCard(
                    item = item,
                    getImageUrl = getImageUrl,
                    getBackdropUrl = getBackdropUrl,
                    getSeriesImageUrl = getSeriesImageUrl,
                    onClick = onItemClick,
                    isVisible = index <= 20, // Only load images for first 20 items
                    modifier = Modifier
                        .height(200.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun OptimizedCarouselCard(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    onClick: (BaseItemDto) -> Unit,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick(item) },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Only load image if visible to save memory
            if (isVisible) {
                val imageUrl = getOptimalImageUrl(item, getImageUrl, getBackdropUrl, getSeriesImageUrl)

                OptimizedImage(
                    imageUrl = imageUrl,
                    contentDescription = "${item.name} image",
                    modifier = Modifier.fillMaxSize(),
                    size = ImageSize.CARD,
                    quality = ImageQuality.MEDIUM,
                    contentScale = ContentScale.Crop,
                    cornerRadius = 12.dp,
                )
            }

            // Content type badge
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Dimens.Spacing8),
                shape = RoundedCornerShape(6.dp),
                color = getContentTypeColor(item.type).copy(alpha = 0.9f),
            ) {
                Text(
                    text = getContentTypeLabel(item.type),
                    style = MaterialTheme.typography.labelSmall,
                    color = getContentTypeContentColor(item.type),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            // Rating badge
            item.communityRating?.let { rating ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Dimens.Spacing8),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                ) {
                    Text(
                        text = "★ ${String.format(java.util.Locale.ROOT, "%.1f", rating)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            // Title overlay
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(Dimens.Spacing12),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Spacing4),
                ) {
                    Text(
                        text = item.name ?: stringResource(R.string.unknown),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // Additional info based on content type
                    when (item.type) {
                        BaseItemKind.EPISODE -> {
                            item.seriesName?.let { seriesName ->
                                Text(
                                    text = seriesName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        BaseItemKind.SERIES, BaseItemKind.MOVIE -> {
                            item.productionYear?.let { year ->
                                Text(
                                    text = year.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        BaseItemKind.AUDIO -> {
                            item.artists?.firstOrNull()?.let { artist ->
                                Text(
                                    text = artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

/**
 * Get the optimal image URL based on content type for better performance.
 */
private fun getOptimalImageUrl(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
): String? {
    return when (item.type) {
        BaseItemKind.EPISODE -> getSeriesImageUrl(item) ?: getImageUrl(item)
        BaseItemKind.AUDIO, BaseItemKind.MUSIC_ALBUM -> getImageUrl(item)
        BaseItemKind.SERIES -> getSeriesImageUrl(item) ?: getBackdropUrl(item) ?: getImageUrl(item)
        BaseItemKind.MOVIE -> getBackdropUrl(item) ?: getImageUrl(item)
        else -> getImageUrl(item)
    }
}

/**
 * Get content type color for visual categorization.
 */
@Composable
private fun getContentTypeColor(type: BaseItemKind?): Color {
    return when (type) {
        BaseItemKind.MOVIE -> MaterialTheme.colorScheme.primary
        BaseItemKind.SERIES -> MaterialTheme.colorScheme.secondary
        BaseItemKind.EPISODE -> MaterialTheme.colorScheme.tertiary
        BaseItemKind.AUDIO -> MaterialTheme.colorScheme.error
        BaseItemKind.MUSIC_ALBUM -> MaterialTheme.colorScheme.error
        BaseItemKind.BOOK -> MaterialTheme.colorScheme.outline
        BaseItemKind.AUDIO_BOOK -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
}

@Composable
private fun getContentTypeContentColor(type: BaseItemKind?): Color {
    return when (type) {
        BaseItemKind.MOVIE -> MaterialTheme.colorScheme.onPrimary
        BaseItemKind.SERIES -> MaterialTheme.colorScheme.onSecondary
        BaseItemKind.EPISODE -> MaterialTheme.colorScheme.onTertiary
        BaseItemKind.AUDIO, BaseItemKind.MUSIC_ALBUM -> MaterialTheme.colorScheme.onError
        BaseItemKind.BOOK, BaseItemKind.AUDIO_BOOK -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * Get content type label for accessibility and user understanding.
 */
private fun getContentTypeLabel(type: BaseItemKind?): String {
    return when (type) {
        BaseItemKind.MOVIE -> "Movie"
        BaseItemKind.SERIES -> "Series"
        BaseItemKind.EPISODE -> "Episode"
        BaseItemKind.AUDIO -> "Music"
        BaseItemKind.MUSIC_ALBUM -> "Album"
        BaseItemKind.MUSIC_ARTIST -> "Artist"
        BaseItemKind.BOOK -> "Book"
        BaseItemKind.AUDIO_BOOK -> "Audiobook"
        else -> "Media"
    }
}
