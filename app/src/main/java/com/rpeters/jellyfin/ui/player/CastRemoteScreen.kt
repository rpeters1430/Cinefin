package com.rpeters.jellyfin.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.image.rememberScreenWidthHeight

@UnstableApi
@Composable
fun CastRemoteScreen(
    playerState: VideoPlayerState,
    onPauseCast: () -> Unit,
    onResumeCast: () -> Unit,
    onStopCast: () -> Unit,
    onSeekCast: (Long) -> Unit,
    onDisconnectCast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artwork = playerState.castBackdropUrl ?: playerState.castPosterUrl
    val playerColors = rememberVideoPlayerColors()
    val deviceName = playerState.castDeviceName ?: "Cast device"

    Box(
        modifier = modifier
            .background(playerColors.background)
            .testTag("cast_overlay"),
    ) {
        if (!artwork.isNullOrBlank()) {
            JellyfinAsyncImage(
                model = artwork,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.25f,
                requestSize = rememberScreenWidthHeight(360.dp),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            playerColors.gradientStops,
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.CastConnected,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = "Playing on $deviceName",
                        style = MaterialTheme.typography.labelLarge,
                        color = playerColors.overlayContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = playerState.itemName.ifBlank { "Casting" },
                        style = MaterialTheme.typography.titleMedium,
                        color = playerColors.overlayContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onDisconnectCast,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = playerColors.overlayScrim,
                        contentColor = playerColors.overlayContent,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Disconnect cast",
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!playerState.castPosterUrl.isNullOrBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = playerColors.overlayScrim,
                            ),
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .heightIn(max = 320.dp),
                        ) {
                            JellyfinAsyncImage(
                                model = playerState.castPosterUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f),
                                contentScale = ContentScale.Crop,
                                requestSize = rememberScreenWidthHeight(320.dp),
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = playerColors.overlayContent.copy(alpha = 0.7f),
                            modifier = Modifier.size(72.dp),
                        )
                    }

                    if (!playerState.castOverview.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = playerState.castOverview,
                            color = playerColors.overlayContent.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val duration = playerState.castDuration
                val position = playerState.castPosition
                if (duration > 0L) {
                    Text(
                        text = "${formatDuration(position)} / ${formatDuration(duration)}",
                        color = playerColors.overlayContent.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledIconButton(
                        onClick = {
                            val seekBack = (position - 10_000).coerceAtLeast(0L)
                            onSeekCast(seekBack)
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = playerColors.overlayScrim,
                            contentColor = playerColors.overlayContent,
                        ),
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Rewind 10 seconds",
                        )
                    }

                    FilledIconButton(
                        onClick = if (playerState.isCastPlaying) onPauseCast else onResumeCast,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(
                            imageVector = if (playerState.isCastPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.isCastPlaying) "Pause" else "Play",
                            modifier = Modifier.size(32.dp),
                        )
                    }

                    FilledIconButton(
                        onClick = {
                            val seekForward = if (duration > 0L) {
                                (position + 10_000).coerceAtMost(duration)
                            } else {
                                position + 10_000
                            }
                            onSeekCast(seekForward)
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = playerColors.overlayScrim,
                            contentColor = playerColors.overlayContent,
                        ),
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Forward 10 seconds",
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                FilledIconButton(
                    onClick = onStopCast,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = playerColors.overlayScrim,
                        contentColor = playerColors.overlayContent,
                    ),
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop casting",
                    )
                }
            }
        }
    }
}

@Composable
fun CastNowPlayingOverlay(
    playerState: VideoPlayerState,
    onPauseCast: () -> Unit,
    onResumeCast: () -> Unit,
    onStopCast: () -> Unit,
    onSeekCast: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val artwork = playerState.castBackdropUrl ?: playerState.castPosterUrl

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cast_overlay"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        ),
    ) {
        Box {
            if (!artwork.isNullOrBlank()) {
                JellyfinAsyncImage(
                    model = artwork,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentScale = ContentScale.Crop,
                    alpha = 0.45f,
                    requestSize = rememberScreenWidthHeight(220.dp),
                )

                val playerColors = rememberVideoPlayerColors()
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                playerColors.gradientStops,
                            ),
                        ),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CastConnected,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playerState.itemName.ifBlank { "Casting" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        playerState.castDeviceName?.let { deviceName ->
                            Text(
                                text = "Playing on $deviceName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExpressiveIconButton(
                            icon = if (playerState.isCastPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.isCastPlaying) "Pause cast playback" else "Resume cast playback",
                            onClick = if (playerState.isCastPlaying) onPauseCast else onResumeCast,
                            isActive = playerState.isCastPlaying,
                        )
                        ExpressiveIconButton(
                            icon = Icons.Default.Stop,
                            contentDescription = "Stop casting",
                            onClick = onStopCast,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }

                // Seek bar with position/duration
                if (playerState.castDuration > 0) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        var seekPosition by remember { mutableStateOf(playerState.castPosition.toFloat()) }
                        var isSeeking by remember { mutableStateOf(false) }

                        LaunchedEffect(playerState.castPosition) {
                            if (!isSeeking) {
                                seekPosition = playerState.castPosition.toFloat()
                            }
                        }

                        Slider(
                            value = seekPosition,
                            onValueChange = { newValue ->
                                isSeeking = true
                                seekPosition = newValue
                            },
                            onValueChangeFinished = {
                                onSeekCast(seekPosition.toLong())
                                isSeeking = false
                            },
                            valueRange = 0f..playerState.castDuration.toFloat(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            ),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatDuration(if (isSeeking) seekPosition.toLong() else playerState.castPosition),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = formatDuration(playerState.castDuration),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Volume control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (playerState.castVolume > 0f) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "Volume",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Slider(
                        value = playerState.castVolume,
                        onValueChange = onVolumeChange,
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        ),
                    )
                    Text(
                        text = "${(playerState.castVolume * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(36.dp),
                    )
                }
            }
        }
    }
}

@UnstableApi
data class VideoPlayerColors(
    val background: Color,
    val overlayContent: Color,
    val overlayScrim: Color,
    val gradientStops: List<Color>,
    val chipBackground: Color,
    val chipContent: Color,
    val inactiveTrack: Color,
    val controlContainer: Color,
    val disabledControlContainer: Color,
    val disabledIcon: Color,
)

@UnstableApi
@Composable
fun rememberVideoPlayerColors(): VideoPlayerColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        VideoPlayerColors(
            background = Color.Black,
            overlayContent = Color.White,
            overlayScrim = scheme.scrim.copy(alpha = 0.75f),
            gradientStops = listOf(
                scheme.surfaceContainerHighest.copy(alpha = 0.96f),
                scheme.scrim.copy(alpha = 0.35f),
                scheme.surfaceContainerHighest.copy(alpha = 0.96f),
            ),
            chipBackground = scheme.surfaceContainerHighest.copy(alpha = 0.85f),
            chipContent = scheme.onSurface,
            inactiveTrack = scheme.onSurfaceVariant.copy(alpha = 0.3f),
            controlContainer = scheme.surfaceContainerHighest.copy(alpha = 0.75f),
            disabledControlContainer = scheme.surfaceContainer.copy(alpha = 0.4f),
            disabledIcon = scheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
