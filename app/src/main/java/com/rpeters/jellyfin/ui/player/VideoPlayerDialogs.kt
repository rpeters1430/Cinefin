package com.rpeters.jellyfin.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi

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
        title = { Text("Select Audio Track") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
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
            TextButton(onClick = onDismiss) { Text("Close") }
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
        title = { Text("Subtitles") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
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
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
fun CastDeviceSelectionDialog(
    availableDevices: List<String>,
    onDeviceSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cast to Device") },
        text = {
            Column {
                if (availableDevices.isEmpty()) {
                    Text("No Cast devices found. Make sure your Chromecast or other Cast-enabled device is on the same network.")
                } else {
                    availableDevices.forEach { device ->
                        TextButton(
                            onClick = { onDeviceSelect(device) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(device)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
                    Text("Not Now")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onAccept) {
                    Text("Switch Quality", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
