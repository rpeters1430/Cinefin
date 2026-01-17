package com.rpeters.jellyfin.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.ui.theme.Quality4K
import com.rpeters.jellyfin.ui.theme.QualityHD
import com.rpeters.jellyfin.ui.theme.QualitySD
import com.rpeters.jellyfin.ui.utils.EnhancedPlaybackUtils
import com.rpeters.jellyfin.ui.utils.PlaybackCapabilityAnalysis
import com.rpeters.jellyfin.ui.utils.PlaybackMethod
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStreamType
import java.util.Locale

/**
 * Get quality label and color for a media item
 */
fun getQualityLabel(item: BaseItemDto): Pair<String, Color>? {
    val mediaSource = item.mediaSources?.firstOrNull() ?: return null
    val videoStream = mediaSource.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }
    val width = videoStream?.width ?: 0
    return when {
        width >= 3800 -> "4K" to Quality4K
        width >= 1900 -> "HD" to QualityHD
        width > 0 -> "SD" to QualitySD
        mediaSource.container?.contains("4k", ignoreCase = true) == true -> "4K" to Quality4K
        mediaSource.container?.contains("hd", ignoreCase = true) == true -> "HD" to QualityHD
        else -> null
    }
}

/**
 * Playback capability status indicator for media cards
 */
@Composable
fun PlaybackStatusIndicator(
    item: BaseItemDto,
    enhancedPlaybackUtils: EnhancedPlaybackUtils,
    modifier: Modifier = Modifier,
) {
    var analysis by remember { mutableStateOf<PlaybackCapabilityAnalysis?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(item.id) {
        coroutineScope.launch {
            try {
                analysis = enhancedPlaybackUtils.analyzePlaybackCapabilities(item)
            } catch (e: Exception) {
                // Silently handle analysis failures to prevent UI disruption
                analysis = null
            }
        }
    }

    analysis?.let { analysisData ->
        PlaybackStatusBadge(
            analysis = analysisData,
            modifier = modifier,
        )
    }
}

/**
 * Static playback status badge component
 */
@Composable
fun PlaybackStatusBadge(
    analysis: PlaybackCapabilityAnalysis,
    modifier: Modifier = Modifier,
) {
    val (statusText, statusColor, statusIcon) = when (analysis.preferredMethod) {
        PlaybackMethod.DIRECT_PLAY -> Triple(
            "Direct Play",
            Color(0xFF4CAF50), // Green
            Icons.Filled.PlayCircle,
        )
        PlaybackMethod.TRANSCODING -> Triple(
            "Transcode",
            Color(0xFFFF9800), // Orange
            Icons.Filled.Transform,
        )
        PlaybackMethod.UNAVAILABLE -> Triple(
            "Unavailable",
            Color(0xFFF44336), // Red
            Icons.Filled.Error,
        )
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * Detailed playback capabilities display for detail screens
 */
@Composable
fun PlaybackCapabilityDetails(
    analysis: PlaybackCapabilityAnalysis,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Playback Capabilities",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaybackStatusBadge(analysis = analysis)

                Text(
                    text = analysis.expectedQuality,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (analysis.details.isNotEmpty()) {
                Text(
                    text = analysis.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Codecs",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = analysis.codecs,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Column {
                    Text(
                        text = "Container",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = analysis.container,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (analysis.estimatedBandwidth > 0) {
                val bandwidthMbps = analysis.estimatedBandwidth / 1_000_000f
                Text(
                    text = "Estimated Bandwidth: ${"%.1f".format(bandwidthMbps)} Mbps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Network quality indicator with animated status
 */
@Composable
fun NetworkQualityIndicator(
    qualityLevel: Float, // 0.0 to 1.0
    modifier: Modifier = Modifier,
) {
    val animatedQuality by animateFloatAsState(
        targetValue = qualityLevel,
        label = "network_quality",
    )

    val (qualityText, qualityColor) = when {
        animatedQuality >= 0.8f -> "Excellent" to Color(0xFF4CAF50)
        animatedQuality >= 0.6f -> "Good" to Color(0xFF8BC34A)
        animatedQuality >= 0.4f -> "Fair" to Color(0xFFFF9800)
        animatedQuality >= 0.2f -> "Poor" to Color(0xFFFF5722)
        else -> "Very Poor" to Color(0xFFF44336)
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = qualityColor.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimary),
            )
            Text(
                text = qualityText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
