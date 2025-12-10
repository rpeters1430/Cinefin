package com.rpeters.jellyfin.ui.components

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
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.core.constants.Constants
import com.rpeters.jellyfin.utils.*
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun UnwatchedEpisodeCountBadge(
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
                    unwatchedCount == 1 -> "1"
                    unwatchedCount > 99 -> "99+"
                    else -> unwatchedCount.toString()
                }
            }
            totalEpisodes > 0 && isNotCompletelyWatched -> {
                when {
                    totalEpisodes == 1 -> "1"
                    totalEpisodes > 99 -> "99+"
                    else -> totalEpisodes.toString()
                }
            }
            else -> null
        }

        displayText?.let { text ->
            val accessibilityLabel = if (unwatchedCount > 0) {
                "$unwatchedCount unwatched episode${if (unwatchedCount != 1) "s" else ""}"
            } else {
                "$totalEpisodes total episode${if (totalEpisodes != 1) "s" else ""}"
            }

            Badge(
                modifier = modifier.semantics {
                    contentDescription = accessibilityLabel
                    role = Role.Image
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
fun WatchedIndicatorBadge(
    item: BaseItemDto,
    modifier: Modifier = Modifier,
) {
    when {
        // Series completely watched indicator
        item.type == BaseItemKind.SERIES && item.isCompletelyWatched() -> {
            Badge(
                modifier = modifier.semantics {
                    contentDescription = "Series completely watched"
                    role = Role.Image
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        // Movie/Episode watched indicator
        (item.type == BaseItemKind.MOVIE || item.type == BaseItemKind.EPISODE) && item.isWatched() -> {
            val description = when (item.type) {
                BaseItemKind.MOVIE -> "Movie watched"
                BaseItemKind.EPISODE -> "Episode watched"
                else -> "Watched"
            }
            Badge(
                modifier = modifier.semantics {
                    contentDescription = description
                    role = Role.Image
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        // Resume indicator for partially watched content
        item.canResume() -> {
            Badge(
                modifier = modifier.semantics {
                    contentDescription = "Resume watching"
                    role = Role.Image
                },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
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
    if (watchedPercentage > 0.0 && watchedPercentage < Constants.Playback.WATCHED_THRESHOLD_PERCENT && !item.isWatched()) {
        val accessibilityLabel = "${watchedPercentage.toInt()}% watched"

        Box(
            modifier = modifier
                .semantics {
                    contentDescription = accessibilityLabel
                    role = Role.Image
                }
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
fun SeriesWatchStatusBadge(
    series: BaseItemDto,
    onToggleSeriesWatchStatus: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (series.type == BaseItemKind.SERIES) {
        val isCompletelyWatched = series.isCompletelyWatched()
        val unwatchedCount = series.getUnwatchedEpisodeCount()

        Badge(
            modifier = modifier
                .clickable { onToggleSeriesWatchStatus(series) },
            containerColor = if (isCompletelyWatched) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondary
            },
            contentColor = if (isCompletelyWatched) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSecondary
            },
        ) {
            Text(
                text = if (isCompletelyWatched) {
                    "✓"
                } else if (unwatchedCount > 0) {
                    unwatchedCount.toString()
                } else {
                    "▶"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

// Backwards compatibility aliases
@Composable
@Deprecated("Use UnwatchedEpisodeCountBadge instead", ReplaceWith("UnwatchedEpisodeCountBadge(item, modifier)"))
fun UnwatchedEpisodeCountOverlay(
    item: BaseItemDto,
    modifier: Modifier = Modifier,
) = UnwatchedEpisodeCountBadge(item, modifier)

@Composable
@Deprecated("Use WatchedIndicatorBadge instead", ReplaceWith("WatchedIndicatorBadge(item, modifier)"))
fun WatchedIndicatorOverlay(
    item: BaseItemDto,
    modifier: Modifier = Modifier,
) = WatchedIndicatorBadge(item, modifier)

@Composable
@Deprecated("Use SeriesWatchStatusBadge instead", ReplaceWith("SeriesWatchStatusBadge(series, onToggleSeriesWatchStatus, modifier)"))
fun SeriesWatchStatusChip(
    series: BaseItemDto,
    onToggleSeriesWatchStatus: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
) = SeriesWatchStatusBadge(series, onToggleSeriesWatchStatus, modifier)
