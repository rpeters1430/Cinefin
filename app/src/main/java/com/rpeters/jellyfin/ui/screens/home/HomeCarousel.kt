package com.rpeters.jellyfin.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.accessibility.getAccessibilityDescription
import com.rpeters.jellyfin.ui.components.ExpressiveCardType
import com.rpeters.jellyfin.ui.components.ExpressiveMediaCard
import com.rpeters.jellyfin.ui.image.ImageQuality
import com.rpeters.jellyfin.ui.image.ImageSize
import com.rpeters.jellyfin.ui.image.OptimizedImage
import com.rpeters.jellyfin.ui.theme.Dimens
import org.jellyfin.sdk.model.api.BaseItemDto

@OptInAppExperimentalApis
@Composable
fun HomeCarousel(
    movies: List<BaseItemDto>,
    getBackdropUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = Dimens.Spacing16, vertical = Dimens.Spacing8),
        )
        val carouselState = rememberCarouselState { movies.size }
        HorizontalCenteredHeroCarousel(
            state = carouselState,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            maxItemWidth = 320.dp,
            itemSpacing = 16.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) { index ->
            val movie = movies[index]
            CarouselMovieCard(
                movie = movie,
                getBackdropUrl = getBackdropUrl,
                onClick = onItemClick,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
            )
        }
    }
}

@Composable
private fun CarouselMovieCard(
    movie: BaseItemDto,
    getBackdropUrl: (BaseItemDto) -> String?,
    onClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .semantics { contentDescription = movie.getAccessibilityDescription() }
            .clickable { onClick(movie) }
            .focusable(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            OptimizedImage(
                imageUrl = getBackdropUrl(movie),
                contentDescription = "${movie.name} backdrop",
                modifier = Modifier.fillMaxSize(),
                size = ImageSize.BANNER,
                quality = ImageQuality.HIGH,
                contentScale = ContentScale.Crop,
                cornerRadius = 16.dp,
            )
            movie.communityRating?.let { rating ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Dimens.Spacing12),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        text = "★ ${String.format(java.util.Locale.ROOT, "%.1f", rating)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = Dimens.Spacing8, vertical = Dimens.Spacing4),
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimens.Spacing8),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Dimens.Spacing16),
            ) {
                Text(
                    text = movie.name ?: "Unknown Movie",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
            }
        }
    }
}

/**
 * Enhanced Material 3 Expressive Carousel for all content types
 * Supports Movies, TV Shows, Episodes, Music, and more
 */
@OptInAppExperimentalApis
@Composable
fun EnhancedContentCarousel(
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String? = getImageUrl,
    onItemClick: (BaseItemDto) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = Dimens.Spacing16, vertical = Dimens.Spacing8),
        )
        val carouselState = rememberCarouselState(initialItem = 1) { items.size }
        HorizontalMultiBrowseCarousel(
            state = carouselState,
            preferredItemWidth = 220.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
            itemSpacing = Dimens.Spacing16,
            contentPadding = PaddingValues(horizontal = 64.dp),
        ) { index ->
            val item = items[index]
            val info = carouselItemDrawInfo
            val progress by animateFloatAsState(
                targetValue = if (info.maxSize == 0f) 0f else info.size / info.maxSize,
                label = "carousel_item_scale",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = 0.85f + (0.15f * progress)
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                ExpressiveMediaCard(
                    title = item.name ?: "Unknown Title",
                    subtitle = when (item.type?.toString()) {
                        "Episode" -> item.seriesName ?: ""
                        "Series" -> item.productionYear?.toString() ?: ""
                        "Audio" -> item.artists?.firstOrNull() ?: ""
                        "Movie" -> item.productionYear?.toString() ?: ""
                        else -> ""
                    },
                    imageUrl = when (item.type?.toString()) {
                        "Episode" -> getSeriesImageUrl(item) ?: getImageUrl(item) ?: ""
                        "Audio", "MusicAlbum" -> getImageUrl(item) ?: ""
                        "Series" -> getSeriesImageUrl(item) ?: getBackdropUrl(item) ?: getImageUrl(item) ?: ""
                        else -> getBackdropUrl(item) ?: getImageUrl(item) ?: ""
                    },
                    rating = item.communityRating?.toFloat(),
                    onCardClick = { onItemClick(item) },
                    onPlayClick = { onItemClick(item) },
                    cardType = ExpressiveCardType.ELEVATED,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun CarouselContentCard(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    onClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .semantics { contentDescription = item.getAccessibilityDescription() }
            .clickable { onClick(item) }
            .focusable(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Use appropriate image based on content type
            val imageUrl = when (item.type?.toString()) {
                "Episode" -> getSeriesImageUrl(item) ?: getImageUrl(item)
                "Audio", "MusicAlbum" -> getImageUrl(item)
                "Series" -> getSeriesImageUrl(item) ?: getBackdropUrl(item) ?: getImageUrl(item)
                else -> getBackdropUrl(item) ?: getImageUrl(item)
            }

            OptimizedImage(
                imageUrl = imageUrl,
                contentDescription = "${item.name} image",
                modifier = Modifier.fillMaxSize(),
                size = ImageSize.BANNER,
                quality = ImageQuality.HIGH,
                contentScale = ContentScale.Crop,
                cornerRadius = 16.dp,
            )

            // Rating badge
            item.communityRating?.let { rating ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        text = "★ ${String.format(java.util.Locale.ROOT, "%.1f", rating)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // Content overlay with title and additional info
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            ) {
                Text(
                    text = item.name ?: "Unknown Title",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )

                // Additional info based on content type
                when (item.type?.toString()) {
                    "Episode" -> {
                        item.seriesName?.let { seriesName ->
                            Text(
                                text = seriesName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                maxLines = 1,
                            )
                        }
                    }
                    "Series" -> {
                        item.productionYear?.let { year ->
                            Text(
                                text = year.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                maxLines = 1,
                            )
                        }
                    }
                    "Audio" -> {
                        item.artists?.firstOrNull()?.let { artist ->
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeCarouselPreview() {
    HomeCarousel(
        movies = emptyList(),
        getBackdropUrl = { null },
        onItemClick = {},
        title = "Recently Added Movies",
    )
}
