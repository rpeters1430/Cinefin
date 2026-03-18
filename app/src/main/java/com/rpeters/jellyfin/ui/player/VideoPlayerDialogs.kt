package com.rpeters.jellyfin.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.player.cast.DiscoveryState

@UnstableApi
@Composable
fun AudioTrackSelectionDialog(
    availableTracks: List<TrackInfo>,
    onTrackSelect: (TrackInfo) -> Unit,
    onDismiss: () -> Unit,
    maxHeight: Dp = LocalConfiguration.current.screenHeightDp.dp * 0.6f,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.select_audio_track)) },
        text = {
            SelectionDialogContent(maxHeight = maxHeight) {
                if (availableTracks.isEmpty()) {
                    EmptySelectionMessage(message = "No audio tracks are available for this video.")
                } else {
                    availableTracks.forEach { track ->
                        TextButton(
                            onClick = {
                                onTrackSelect(track)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = track.displayName,
                                fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.close)) }
        },
    )
}

@UnstableApi
@Composable
fun QualitySelectionDialog(
    availableQualities: List<VideoQuality>,
    selectedQuality: VideoQuality?,
    onQualitySelect: (VideoQuality?) -> Unit,
    onDismiss: () -> Unit,
    maxHeight: Dp = LocalConfiguration.current.screenHeightDp.dp * 0.6f,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.select_quality)) },
        text = {
            SelectionDialogContent(maxHeight = maxHeight) {
                TextButton(
                    onClick = {
                        onQualitySelect(null)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(id = R.string.quality_auto),
                        fontWeight = if (selectedQuality == null) FontWeight.Bold else FontWeight.Normal,
                    )
                }

                if (availableQualities.isEmpty()) {
                    EmptySelectionMessage(message = "No alternate quality options are available right now.")
                }

                availableQualities.forEach { quality ->
                    TextButton(
                        onClick = {
                            onQualitySelect(quality)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = quality.label,
                            fontWeight = if (quality == selectedQuality) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.close)) }
        },
    )
}

@UnstableApi
@Composable
fun SubtitleTrackSelectionDialog(
    availableTracks: List<TrackInfo>,
    selectedTrack: TrackInfo?,
    onTrackSelect: (TrackInfo?) -> Unit,
    onDismiss: () -> Unit,
    maxHeight: Dp = LocalConfiguration.current.screenHeightDp.dp * 0.6f,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.subtitles)) },
        text = {
            SelectionDialogContent(maxHeight = maxHeight) {
                TextButton(
                    onClick = {
                        onTrackSelect(null)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Off",
                        fontWeight = if (selectedTrack == null) FontWeight.Bold else FontWeight.Normal,
                    )
                }

                if (availableTracks.isEmpty()) {
                    EmptySelectionMessage(message = "No subtitle tracks are available for this video.")
                }

                availableTracks.forEach { track ->
                    TextButton(
                        onClick = {
                            onTrackSelect(track)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = track.displayName,
                            fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.close)) }
        },
    )
}

@Composable
private fun SelectionDialogContent(
    maxHeight: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 12.dp)
                .verticalScroll(scrollState),
            content = content,
        )
        DialogScrollbar(
            scrollState = scrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(4.dp),
        )
    }
}

@Composable
private fun DialogScrollbar(
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
) {
    val maxValue = scrollState.maxValue
    val viewportSize = scrollState.viewportSize
    if (maxValue <= 0 || viewportSize <= 0) return

    val totalContentHeight = maxValue + viewportSize
    val visibleFraction = (viewportSize.toFloat() / totalContentHeight.toFloat()).coerceIn(0.15f, 1f)
    val offsetFraction = (scrollState.value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)

    Box(
        modifier = modifier.drawWithContent {
            drawRoundRect(
                color = trackColor,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width / 2f, size.width / 2f),
            )

            val handleHeight = size.height * visibleFraction
            val handleY = (size.height - handleHeight) * offsetFraction
            drawRoundRect(
                color = thumbColor,
                topLeft = androidx.compose.ui.geometry.Offset(0f, handleY),
                size = androidx.compose.ui.geometry.Size(size.width, handleHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width / 2f, size.width / 2f),
            )
        },
    )
}

@Composable
private fun EmptySelectionMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
fun CastDeviceSelectionDialog(
    availableDevices: List<String>,
    discoveryState: DiscoveryState,
    onDeviceSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(id = R.string.cast_to_device))
                if (discoveryState == DiscoveryState.DISCOVERING) {
                    Spacer(modifier = Modifier.width(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        text = {
            Column {
                when {
                    discoveryState == DiscoveryState.DISCOVERING && availableDevices.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Scanning for devices...",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    availableDevices.isEmpty() && discoveryState == DiscoveryState.TIMEOUT -> {
                        Text(stringResource(id = R.string.no_cast_devices_found_detailed))
                    }
                    availableDevices.isEmpty() && discoveryState == DiscoveryState.IDLE -> {
                        Text(stringResource(id = R.string.no_devices_found))
                    }
                    else -> {
                        availableDevices.forEach { device ->
                            TextButton(
                                onClick = { onDeviceSelect(device) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = device,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        },
    )
}

/**
 * Quality recommendation notification that appears when adaptive bitrate monitor
 * suggests switching to a lower quality due to buffering or bandwidth issues.
 */
@Composable
fun QualityRecommendationNotification(
    recommendation: com.rpeters.jellyfin.data.playback.QualityRecommendation,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Quality recommendation",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quality Recommendation",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = recommendation.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = "Switch to ${recommendation.recommendedQuality.label} quality?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.not_now))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onAccept) {
                    Text(stringResource(id = R.string.switch_quality), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
