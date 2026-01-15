@file:OptInAppExperimentalApis

package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.ShimmerBox
import com.rpeters.jellyfin.ui.accessibility.mediaCardSemantics
import com.rpeters.jellyfin.ui.image.ImageQuality
import com.rpeters.jellyfin.ui.image.ImageSize
import com.rpeters.jellyfin.ui.image.OptimizedImage
import com.rpeters.jellyfin.ui.theme.RatingBronze
import com.rpeters.jellyfin.ui.theme.RatingGold
import com.rpeters.jellyfin.ui.theme.RatingSilver
import com.rpeters.jellyfin.ui.theme.getContentTypeColor
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun MediaCard(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    onClick: (BaseItemDto) -> Unit = {},
    onLongPress: ((BaseItemDto) -> Unit)? = null,
    modifier: Modifier = Modifier,
    enhancedPlaybackUtils: com.rpeters.jellyfin.ui.utils.EnhancedPlaybackUtils? = null,
    cardWidth: Dp = 280.dp,
    cardAspectRatio: Float = 16f / 9f,
) {
    val contentTypeColor = getContentTypeColor(item.type.toString())
    val coroutineScope = rememberCoroutineScope()

    Card(
        modifier = modifier
            .width(cardWidth)
            .aspectRatio(cardAspectRatio)
            .mediaCardSemantics(item) { onClick(item) }
            .combinedClickable(
                onClick = { onClick(item) },
                onLongClick = { onLongPress?.invoke(item) },
            )
            .focusable(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Box {
            // Background Image - Optimized
            OptimizedImage(
                imageUrl = getImageUrl(item),
                contentDescription = "${item.name} poster image",
                modifier = Modifier.fillMaxSize(),
                size = ImageSize.BANNER,
                quality = ImageQuality.HIGH,
                contentScale = ContentScale.Crop,
                cornerRadius = 12.dp,
                loading = {
                    ShimmerBox(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(12.dp),
                    )
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(contentTypeColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "No image available",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )

            // Content type badge with semantic color
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = contentTypeColor.copy(alpha = 0.9f),
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = when (item.type) {
                        BaseItemKind.MOVIE -> "Movie"
                        BaseItemKind.SERIES -> "Series"
                        BaseItemKind.EPISODE -> "Episode"
                        BaseItemKind.AUDIO -> "Music"
                        BaseItemKind.MUSIC_ALBUM -> "Album"
                        BaseItemKind.MUSIC_ARTIST -> "Artist"
                        BaseItemKind.BOOK -> "Book"
                        BaseItemKind.AUDIO_BOOK -> "Audiobook"
                        else -> "Media"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            // Top right badges container
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Playback status indicator
                enhancedPlaybackUtils?.let { utils ->
                    PlaybackStatusIndicator(
                        item = item,
                        enhancedPlaybackUtils = utils,
                        modifier = Modifier,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Favorite indicator
                    if (item.userData?.isFavorite == true) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favorite",
                            tint = Color.Yellow,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    // Watch status badges
                    Box {
                        UnwatchedEpisodeCountBadge(
                            item = item,
                            modifier = Modifier.align(Alignment.TopEnd),
                        )

                        WatchedIndicatorBadge(
                            item = item,
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }
            }

            // Watch progress bar at the bottom
            WatchProgressBar(
                item = item,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )

            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY,
                        ),
                    ),
            ) {
                // Content Overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    MaterialText(
                        text = item.name ?: stringResource(R.string.unknown),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = true,
                        minFontSize = 14.sp,
                        maxFontSize = MaterialTheme.typography.titleMedium.fontSize,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        item.productionYear?.let { year ->
                            Text(
                                text = year.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        item.communityRating?.let { rating ->
                            // ✅ Performance: Removed animation to reduce recomposition overhead
                            val ratingColor = when {
                                rating >= 7.5f -> RatingGold
                                rating >= 5.0f -> RatingSilver
                                else -> RatingBronze
                            }
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { rating / 10f },
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.dp,
                                    color = ratingColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                                Text(
                                    text = rating.toInt().toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ratingColor,
                                )
                            }
                        }
                    }
                }

                getQualityLabel(item)?.let { (label, color) ->
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.9f)),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PosterMediaCard(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    onClick: (BaseItemDto) -> Unit = {},
    onLongPress: ((BaseItemDto) -> Unit)? = null,
    modifier: Modifier = Modifier,
    enhancedPlaybackUtils: com.rpeters.jellyfin.ui.utils.EnhancedPlaybackUtils? = null,
    showTitle: Boolean = true,
    showMetadata: Boolean = true,
    cardWidth: Dp? = null, // Made optional - null means fill available width
) {
    val contentTypeColor = getContentTypeColor(item.type.toString())
    val coroutineScope = rememberCoroutineScope()

    Card(
        modifier = modifier
            .then(
                if (cardWidth != null) {
                    Modifier.width(cardWidth)
                } else {
                    Modifier.fillMaxWidth() // Fill grid cell or available width
                },
            )
            .mediaCardSemantics(item) { onClick(item) }
            .combinedClickable(
                onClick = { onClick(item) },
                onLongClick = { onLongPress?.invoke(item) },
            )
            .focusable(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column {
            Box {
                // Poster Image - Proper 2:3 aspect ratio for movie/TV posters
                OptimizedImage(
                    imageUrl = getImageUrl(item),
                    contentDescription = "${item.name} poster image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f), // Standard poster aspect ratio
                    size = ImageSize.POSTER,
                    quality = ImageQuality.HIGH,
                    contentScale = ContentScale.Fit, // Fit without cropping to prevent squishing
                    cornerRadius = 12.dp,
                    loading = {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f),
                            shape = RoundedCornerShape(
                                topStart = 12.dp,
                                topEnd = 12.dp,
                            ),
                        )
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .background(contentTypeColor.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "No image available",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )

                // Content type badge with semantic color
                Card(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = contentTypeColor.copy(alpha = 0.9f),
                    ),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = when (item.type) {
                            BaseItemKind.MOVIE -> "Movie"
                            BaseItemKind.SERIES -> "Series"
                            BaseItemKind.EPISODE -> "Episode"
                            BaseItemKind.AUDIO -> "Music"
                            BaseItemKind.MUSIC_ALBUM -> "Album"
                            BaseItemKind.MUSIC_ARTIST -> "Artist"
                            BaseItemKind.BOOK -> "Book"
                            BaseItemKind.AUDIO_BOOK -> "Audiobook"
                            else -> "Media"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }

                // Top right badges container
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Playback status indicator
                    enhancedPlaybackUtils?.let { utils ->
                        PlaybackStatusIndicator(
                            item = item,
                            enhancedPlaybackUtils = utils,
                            modifier = Modifier,
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Favorite indicator
                        if (item.userData?.isFavorite == true) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Favorite",
                                tint = Color.Yellow,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        // Watch status badges
                        Box {
                            UnwatchedEpisodeCountBadge(
                                item = item,
                                modifier = Modifier.align(Alignment.TopEnd),
                            )

                            WatchedIndicatorBadge(
                                item = item,
                                modifier = Modifier.align(Alignment.BottomEnd),
                            )
                        }
                    }
                }

                // Watch progress bar at the bottom of image
                WatchProgressBar(
                    item = item,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }

            // Title and metadata section below image
            if (showTitle || showMetadata) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (showTitle) {
                        MaterialText(
                            text = item.name ?: stringResource(R.string.unknown),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            autoSize = true,
                            minFontSize = 12.sp,
                            maxFontSize = MaterialTheme.typography.bodyMedium.fontSize,
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (showMetadata) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            item.productionYear?.let { year ->
                                Text(
                                    text = year.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            item.communityRating?.let { rating ->
                                // ✅ Performance: Removed animation to reduce recomposition overhead
                                val ratingColor = when {
                                    rating >= 7.5f -> RatingGold
                                    rating >= 5.0f -> RatingSilver
                                    else -> RatingBronze
                                }
                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        progress = { rating / 10f },
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 1.5.dp,
                                        color = ratingColor,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    Text(
                                        text = rating.toInt().toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = ratingColor,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecentlyAddedCard(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    onClick: (BaseItemDto) -> Unit = {},
    onLongPress: ((BaseItemDto) -> Unit)? = null,
    modifier: Modifier = Modifier,
    enhancedPlaybackUtils: com.rpeters.jellyfin.ui.utils.EnhancedPlaybackUtils? = null,
) {
    val contentTypeColor = getContentTypeColor(item.type.toString())

    Card(
        modifier = modifier
            .width(140.dp)
            .mediaCardSemantics(item) { onClick(item) }
            .combinedClickable(
                onClick = { onClick(item) },
                onLongClick = { onLongPress?.invoke(item) },
            )
            .focusable(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column {
            Box {
                // ✅ FIX: Load actual images instead of just showing shimmer
                // For episodes, use series poster; for other content, use regular image
                val imageUrl = if (item.type == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE) {
                    getSeriesImageUrl(item)
                } else {
                    getImageUrl(item)
                }
                OptimizedImage(
                    imageUrl = imageUrl,
                    contentDescription = item.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f), // Use proper poster aspect ratio
                    size = ImageSize.CARD,
                    quality = ImageQuality.MEDIUM,
                    contentScale = ContentScale.Crop,
                    cornerRadius = 12.dp,
                    loading = {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f), // Match image aspect ratio
                            shape = RoundedCornerShape(
                                topStart = 12.dp,
                                topEnd = 12.dp,
                            ),
                        )
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f) // Match image aspect ratio
                                .background(contentTypeColor.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "No image available",
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )

                // Content type badge with semantic color
                Card(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = contentTypeColor.copy(alpha = 0.9f),
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = when (item.type) {
                            BaseItemKind.MOVIE -> "Movie"
                            BaseItemKind.SERIES -> "Series"
                            BaseItemKind.EPISODE -> "Episode"
                            BaseItemKind.AUDIO -> "Music"
                            BaseItemKind.MUSIC_ALBUM -> "Album"
                            BaseItemKind.MUSIC_ARTIST -> "Artist"
                            BaseItemKind.BOOK -> "Book"
                            BaseItemKind.AUDIO_BOOK -> "Audiobook"
                            else -> "Media"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                getQualityLabel(item)?.let { (label, color) ->
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.9f)),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                // Top right badges container for RecentlyAddedCard
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Playback status indicator
                    enhancedPlaybackUtils?.let { utils ->
                        PlaybackStatusIndicator(
                            item = item,
                            enhancedPlaybackUtils = utils,
                            modifier = Modifier,
                        )
                    }

                    // Watch status badges positioned with stacking
                    Box {
                        UnwatchedEpisodeCountBadge(
                            item = item,
                            modifier = Modifier.align(Alignment.TopEnd),
                        )

                        WatchedIndicatorBadge(
                            item = item,
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }

                // Watch progress bar for RecentlyAddedCard
                WatchProgressBar(
                    item = item,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }

            Column(modifier = Modifier.padding(12.dp)) {
                MaterialText(
                    text = item.name ?: stringResource(R.string.unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    autoSize = true,
                    minFontSize = 12.sp,
                    maxFontSize = MaterialTheme.typography.bodyMedium.fontSize,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item.productionYear?.let { year ->
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    item.communityRating?.let { rating ->
                        // ✅ Performance: Removed animation to reduce recomposition overhead
                        val ratingColor = when {
                            rating >= 7.5f -> RatingGold
                            rating >= 5.0f -> RatingSilver
                            else -> RatingBronze
                        }
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { rating / 10f },
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = ratingColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Text(
                                text = rating.toInt().toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ratingColor,
                            )
                        }
                    }
                }
            }
        }
    }
}
