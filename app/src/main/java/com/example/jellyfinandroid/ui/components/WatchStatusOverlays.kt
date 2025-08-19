package com.example.jellyfinandroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jellyfinandroid.utils.*
import com.example.jellyfinandroid.utils.AppConstants
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun UnwatchedEpisodeCountOverlay(
    item: BaseItemDto,
    modifier: Modifier = Modifier,
) {
    if (item.type == BaseItemKind.SERIES) {
        // Check multiple conditions for showing episode count
        val unwatchedCount = item.getUnwatchedEpisodeCount()
        val totalEpisodes = item.childCount ?: 0
        val isNotCompletelyWatched = !item.isCompletelyWatched()

        val displayText = when {
            unwatchedCount > 0 -> {
                when {
                    unwatchedCount == 1 -> "1 new"
                    unwatchedCount > 99 -> "99+ new"
                    else -> "$unwatchedCount new"
                }
            }
            totalEpisodes > 0 && isNotCompletelyWatched -> {
                when {
                    totalEpisodes == 1 -> "1 episode"
                    totalEpisodes > 99 -> "99+ episodes"
                    else -> "$totalEpisodes episodes"
                }
            }
            else -> null
        }

        displayText?.let { text ->
            Card(
                modifier = modifier,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
fun WatchedIndicatorOverlay(
    item: BaseItemDto,
    modifier: Modifier = Modifier,
) {
    when {
        // Series completely watched indicator
        item.type == BaseItemKind.SERIES && item.isCompletelyWatched() -> {
            Box(
                modifier = modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Series completely watched",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // Movie/Episode watched indicator
        (item.type == BaseItemKind.MOVIE || item.type == BaseItemKind.EPISODE) && item.isWatched() -> {
            Box(
                modifier = modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = when (item.type) {
                        BaseItemKind.MOVIE -> "Movie watched"
                        BaseItemKind.EPISODE -> "Episode watched"
                        else -> "Watched"
                    },
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // Resume indicator for partially watched content
        item.canResume() -> {
            Box(
                modifier = modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.95f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Resume watching",
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun WatchProgressBar(
    item: BaseItemDto,
    modifier: Modifier = Modifier,
) {
    val watchedPercentage = item.getWatchedPercentage()

    // Only show progress bar for partially watched content (not fully watched)
    if (watchedPercentage > 0.0 && watchedPercentage < AppConstants.Playback.WATCHED_THRESHOLD_PERCENT && !item.isWatched()) {
        Box(
            modifier = modifier
                .background(
                    Color.Black.copy(alpha = 0.7f),
                    RoundedCornerShape(2.dp),
                )
                .padding(1.dp),
        ) {
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(1.dp),
                    )
                    .fillMaxWidth(watchedPercentage.toFloat() / 100f)
                    .height(4.dp),
            )
        }
    }
}

@Composable
fun InteractiveWatchStatusButton(
    item: BaseItemDto,
    onToggleWatchStatus: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
    showBackground: Boolean = true,
) {
    val isWatched = item.isWatched()
    val contentDescription = when {
        item.type == BaseItemKind.SERIES && isWatched -> "Mark series as unwatched"
        item.type == BaseItemKind.SERIES && !isWatched -> "Mark series as watched"
        item.type == BaseItemKind.MOVIE && isWatched -> "Mark movie as unwatched"
        item.type == BaseItemKind.MOVIE && !isWatched -> "Mark movie as watched"
        item.type == BaseItemKind.EPISODE && isWatched -> "Mark episode as unwatched"
        item.type == BaseItemKind.EPISODE && !isWatched -> "Mark episode as watched"
        else -> if (isWatched) "Mark as unwatched" else "Mark as watched"
    }

    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .then(
                if (showBackground) {
                    Modifier.background(
                        if (isWatched) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
                        } else {
                            Color.Black.copy(alpha = 0.6f)
                        },
                    )
                } else {
                    Modifier
                },
            )
            .clickable { onToggleWatchStatus(item) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isWatched) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = contentDescription,
            tint = if (isWatched) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                Color.White.copy(alpha = 0.8f)
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun SeriesWatchStatusChip(
    series: BaseItemDto,
    onToggleSeriesWatchStatus: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (series.type == BaseItemKind.SERIES) {
        val isCompletelyWatched = series.isCompletelyWatched()
        val unwatchedCount = series.getUnwatchedEpisodeCount()

        Card(
            modifier = modifier
                .clickable { onToggleSeriesWatchStatus(series) },
            colors = CardDefaults.cardColors(
                containerColor = if (isCompletelyWatched) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
                } else {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.95f)
                },
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Text(
                text = if (isCompletelyWatched) {
                    "Complete"
                } else if (unwatchedCount > 0) {
                    "$unwatchedCount left"
                } else {
                    "Start"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isCompletelyWatched) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSecondary
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
