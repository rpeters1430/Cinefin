package com.example.jellyfinandroid.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.jellyfinandroid.ui.ShimmerBox
import com.example.jellyfinandroid.ui.accessibility.mediaCardSemantics
import com.example.jellyfinandroid.ui.theme.RatingBronze
import com.example.jellyfinandroid.ui.theme.RatingGold
import com.example.jellyfinandroid.ui.theme.RatingSilver
import com.example.jellyfinandroid.ui.theme.getContentTypeColor
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun MediaCard(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    onClick: (BaseItemDto) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val contentTypeColor = getContentTypeColor(item.type?.toString())
    val coroutineScope = rememberCoroutineScope()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .mediaCardSemantics(item) { onClick(item) }
            .clickable { onClick(item) },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Box {
            // Background Image
            SubcomposeAsyncImage(
                model = getImageUrl(item),
                contentDescription = "${item.name} poster image",
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
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
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
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
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

                // Watch status badges in top right
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
                                Color.Black.copy(alpha = 0.7f),
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
                    Text(
                        text = item.name ?: "Unknown Title",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
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
                                color = Color.White.copy(alpha = 0.9f),
                            )
                        }

                        item.communityRating?.let { rating ->
                            val animatedRating by animateFloatAsState(
                                targetValue = rating.toFloat(),
                                label = "rating_anim",
                            )
                            val ratingColor = when {
                                rating >= 7.5f -> RatingGold
                                rating >= 5.0f -> RatingSilver
                                else -> RatingBronze
                            }
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { animatedRating / 10f },
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
fun RecentlyAddedCard(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    onClick: (BaseItemDto) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val contentTypeColor = getContentTypeColor(item.type?.toString())

    Card(
        modifier = modifier
            .width(140.dp)
            .clickable { onClick(item) },
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
                if (imageUrl != null) {
                    SubcomposeAsyncImage(
                        model = imageUrl,
                        contentDescription = item.name,
                        loading = {
                            ShimmerBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
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
                                    .height(200.dp)
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
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
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
                }

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
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
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
                Text(
                    text = item.name ?: "Unknown Title",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
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
                        val animatedRating by animateFloatAsState(
                            targetValue = rating.toFloat(),
                            label = "rating_anim",
                        )
                        val ratingColor = when {
                            rating >= 7.5f -> RatingGold
                            rating >= 5.0f -> RatingSilver
                            else -> RatingBronze
                        }
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { animatedRating / 10f },
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
