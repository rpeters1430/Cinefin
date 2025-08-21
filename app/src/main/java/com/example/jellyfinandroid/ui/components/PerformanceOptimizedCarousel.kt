package com.example.jellyfinandroid.ui.components

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jellyfinandroid.ui.image.ImageQuality
import com.example.jellyfinandroid.ui.image.ImageSize
import com.example.jellyfinandroid.ui.image.OptimizedImage
import com.example.jellyfinandroid.ui.image.rememberImagePreloader
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        val listState = rememberLazyListState()
        val imagePreloader = rememberImagePreloader()

        // Preload visible images
        LaunchedEffect(limitedItems) {
            val imagesToPreload = limitedItems.take(10).mapNotNull { item ->
                getOptimalImageUrl(item, getImageUrl, getBackdropUrl, getSeriesImageUrl)
            }
            imagePreloader.preloadImages(imagesToPreload, ImageSize.CARD)
        }

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            itemsIndexed(
                items = limitedItems,
                key = { _, item -> item.id ?: "" },
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
                    .padding(8.dp),
                shape = RoundedCornerShape(6.dp),
                color = getContentTypeColor(item.type).copy(alpha = 0.9f),
            ) {
                Text(
                    text = getContentTypeLabel(item.type),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            // Rating badge
            item.communityRating?.let { rating ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                ) {
                    Text(
                        text = "★ ${String.format(java.util.Locale.ROOT, "%.1f", rating)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            // Title overlay
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = item.name ?: "Unknown Title",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
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
                                    color = Color.White.copy(alpha = 0.8f),
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
                                    color = Color.White.copy(alpha = 0.8f),
                                )
                            }
                        }
                        BaseItemKind.AUDIO -> {
                            item.artists?.firstOrNull()?.let { artist ->
                                Text(
                                    text = artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f),
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
