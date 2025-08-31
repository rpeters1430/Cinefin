package com.rpeters.jellyfin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.ui.utils.PlaybackRecommendation
import com.rpeters.jellyfin.ui.utils.RecommendationType
import kotlinx.coroutines.delay

/**
 * Display playback recommendations as non-intrusive notifications
 */
@Composable
fun PlaybackRecommendationNotification(
    recommendations: List<PlaybackRecommendation>,
    modifier: Modifier = Modifier,
    onDismiss: (PlaybackRecommendation) -> Unit = {},
    autoHideDelayMs: Long = 5000L,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        itemsIndexed(recommendations) { index, recommendation ->
            PlaybackRecommendationCard(
                recommendation = recommendation,
                onDismiss = { onDismiss(recommendation) },
                autoHideDelayMs = autoHideDelayMs,
            )
        }
    }
}

/**
 * Individual playback recommendation card
 */
@Composable
fun PlaybackRecommendationCard(
    recommendation: PlaybackRecommendation,
    onDismiss: () -> Unit,
    autoHideDelayMs: Long = 5000L,
    modifier: Modifier = Modifier,
) {
    var isVisible by remember { mutableStateOf(true) }

    LaunchedEffect(recommendation) {
        if (recommendation.type != RecommendationType.ERROR) {
            delay(autoHideDelayMs)
            isVisible = false
            delay(300) // Animation time
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        val (backgroundColor, textColor, icon) = when (recommendation.type) {
            RecommendationType.OPTIMAL -> Triple(
                Color(0xFF4CAF50),
                Color.White,
                Icons.Filled.CheckCircle,
            )
            RecommendationType.INFO -> Triple(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
                Icons.Filled.Info,
            )
            RecommendationType.WARNING -> Triple(
                Color(0xFFFF9800),
                Color.White,
                Icons.Filled.Warning,
            )
            RecommendationType.ERROR -> Triple(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                Icons.Filled.Error,
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(24.dp),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = recommendation.message,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                        color = textColor,
                    )

                    if (recommendation.details.isNotEmpty()) {
                        Text(
                            text = recommendation.details,
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.9f),
                        )
                    }
                }

                IconButton(
                    onClick = {
                        isVisible = false
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/**
 * Floating playback recommendation overlay
 */
@Composable
fun FloatingPlaybackRecommendations(
    recommendations: List<PlaybackRecommendation>,
    modifier: Modifier = Modifier,
    onDismiss: (PlaybackRecommendation) -> Unit = {},
) {
    AnimatedVisibility(
        visible = recommendations.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp,
        ) {
            PlaybackRecommendationNotification(
                recommendations = recommendations,
                onDismiss = onDismiss,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/**
 * Compact playback status with expandable recommendations
 */
@Composable
fun CompactPlaybackStatus(
    hasRecommendations: Boolean,
    recommendationCount: Int,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = hasRecommendations,
        modifier = modifier,
    ) {
        Card(
            modifier = Modifier.padding(8.dp),
            onClick = onExpand,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
            shape = RoundedCornerShape(20.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )

                Text(
                    text = "$recommendationCount tip${if (recommendationCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )

                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = "Expand",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/**
 * In-context playback recommendation for detail screens
 */
@Composable
fun InContextPlaybackRecommendation(
    recommendation: PlaybackRecommendation,
    modifier: Modifier = Modifier,
) {
    val (borderColor, backgroundColor) = when (recommendation.type) {
        RecommendationType.OPTIMAL -> Pair(Color(0xFF4CAF50), Color(0xFF4CAF50).copy(alpha = 0.1f))
        RecommendationType.INFO -> Pair(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        RecommendationType.WARNING -> Pair(Color(0xFFFF9800), Color(0xFFFF9800).copy(alpha = 0.1f))
        RecommendationType.ERROR -> Pair(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val icon = when (recommendation.type) {
                RecommendationType.OPTIMAL -> Icons.Filled.CheckCircle
                RecommendationType.INFO -> Icons.Filled.Info
                RecommendationType.WARNING -> Icons.Filled.Warning
                RecommendationType.ERROR -> Icons.Filled.Error
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = borderColor,
                modifier = Modifier.size(20.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = recommendation.message,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (recommendation.details.isNotEmpty()) {
                    Text(
                        text = recommendation.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
