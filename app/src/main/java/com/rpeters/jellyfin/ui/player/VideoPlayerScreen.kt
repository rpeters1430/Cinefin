package com.rpeters.jellyfin.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@UnstableApi
@Composable
fun VideoPlayerScreen(
    playerState: VideoPlayerState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onQualityChange: (VideoQuality) -> Unit,
    onAspectRatioChange: (AspectRatioMode) -> Unit,
    onCastClick: () -> Unit,
    onSubtitlesClick: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    onOrientationToggle: () -> Unit,
    onAudioTrackSelect: (TrackInfo) -> Unit,
    onSubtitleTrackSelect: (TrackInfo?) -> Unit,
    onSubtitleDialogDismiss: () -> Unit,
    onCastDeviceSelect: (String) -> Unit,
    onCastDialogDismiss: () -> Unit,
    exoPlayer: ExoPlayer?,
    modifier: Modifier = Modifier,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showAspectRatioMenu by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }

    // Auto-hide controls after 5 seconds (increased from 3)
    LaunchedEffect(controlsVisible, playerState.isPlaying) {
        if (controlsVisible && playerState.isPlaying) {
            delay(5000) // Longer timeout for better UX
            controlsVisible = false
        }
    }

    // Show controls when video starts loading or pauses
    LaunchedEffect(playerState.isLoading, playerState.isPlaying) {
        if (playerState.isLoading || !playerState.isPlaying) {
            controlsVisible = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                )
            },
    ) {
        // Video Player View
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false // We'll use custom controls
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    resizeMode = playerState.selectedAspectRatio.resizeMode
                }
            },
            update = { playerView ->
                playerView.resizeMode = playerState.selectedAspectRatio.resizeMode
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Loading indicator
        if (playerState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
            }
        }

        // Error message
        playerState.error?.let { error ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // Video Controls Overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            VideoControlsOverlay(
                playerState = playerState,
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                onQualityChange = onQualityChange,
                onAspectRatioChange = onAspectRatioChange,
                onCastClick = onCastClick,
                onAudioTracksClick = { showAudioDialog = true },
                onSubtitlesClick = onSubtitlesClick,
                onPictureInPictureClick = onPictureInPictureClick,
                onOrientationToggle = onOrientationToggle,
                showQualityMenu = showQualityMenu,
                onShowQualityMenu = { showQualityMenu = it },
                showAspectRatioMenu = showAspectRatioMenu,
                onShowAspectRatioMenu = { showAspectRatioMenu = it },
            )
        }

        // Casting indicator
        if (playerState.isCasting) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CastConnected,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text(
                            text = "Casting to ${playerState.castDeviceName}",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        // Audio Track Selection Dialog
        if (showAudioDialog) {
            AlertDialog(
                onDismissRequest = { showAudioDialog = false },
                title = { Text("Select Audio Track") },
                text = {
                    Column {
                        playerState.availableAudioTracks.forEach { track ->
                            TextButton(
                                onClick = {
                                    onAudioTrackSelect(track)
                                    showAudioDialog = false
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
                    TextButton(onClick = { showAudioDialog = false }) { Text("Close") }
                },
            )
        }

        // Subtitle Selection Dialog
        if (playerState.showSubtitleDialog) {
            AlertDialog(
                onDismissRequest = onSubtitleDialogDismiss,
                title = { Text("Select Subtitles") },
                text = {
                    Column {
                        // Option to disable subtitles
                        TextButton(
                            onClick = { onSubtitleTrackSelect(null) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Off",
                                fontWeight = if (playerState.selectedSubtitleTrack == null) FontWeight.Bold else FontWeight.Normal,
                            )
                        }

                        // List available subtitle tracks
                        playerState.availableSubtitleTracks.forEach { track ->
                            TextButton(
                                onClick = { onSubtitleTrackSelect(track) },
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
                    TextButton(onClick = onSubtitleDialogDismiss) {
                        Text("Close")
                    }
                },
            )
        }

        // Cast Device Selection Dialog
        if (playerState.showCastDialog) {
            AlertDialog(
                onDismissRequest = onCastDialogDismiss,
                title = { Text("Cast to Device") },
                text = {
                    Column {
                        if (playerState.availableCastDevices.isEmpty()) {
                            Text("No Cast devices found. Make sure your Chromecast or other Cast-enabled device is on the same network.")
                        } else {
                            playerState.availableCastDevices.forEach { device ->
                                TextButton(
                                    onClick = { onCastDeviceSelect(device) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(device)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onCastDialogDismiss) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@UnstableApi
@Composable
private fun VideoControlsOverlay(
    playerState: VideoPlayerState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onQualityChange: (VideoQuality) -> Unit,
    onAspectRatioChange: (AspectRatioMode) -> Unit,
    onCastClick: () -> Unit,
    onSubtitlesClick: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    onOrientationToggle: () -> Unit,
    showQualityMenu: Boolean,
    onShowQualityMenu: (Boolean) -> Unit,
    showAspectRatioMenu: Boolean,
    onShowAspectRatioMenu: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f)),
    ) {
        Text(
            text = playerState.itemName,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlButton(
                    onClick = onPlayPause,
                    imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                )

                if (playerState.duration > 0) {
                    val duration = playerState.duration.toFloat()
                    val progress = playerState.currentPosition.toFloat() / duration
                    val buffered = playerState.bufferedPosition.toFloat() / duration

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = formatTime(playerState.currentPosition),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            LinearProgressIndicator(
                                progress = { buffered },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color.White.copy(alpha = 0.3f),
                                trackColor = Color.White.copy(alpha = 0.1f),
                            )

                            Slider(
                                value = progress,
                                onValueChange = { newProgress ->
                                    val newPosition = (newProgress * playerState.duration).toLong()
                                    onSeek(newPosition)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Text(
                            text = formatTime(playerState.duration),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box {
                        ControlButton(
                            onClick = { onShowQualityMenu(true) },
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Quality Settings",
                        )

                        DropdownMenu(
                            expanded = showQualityMenu,
                            onDismissRequest = { onShowQualityMenu(false) },
                        ) {
                            playerState.availableQualities.forEach { quality ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = quality.label,
                                            fontWeight = if (quality == playerState.selectedQuality) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Normal
                                            },
                                        )
                                    },
                                    onClick = {
                                        onQualityChange(quality)
                                        onShowQualityMenu(false)
                                    },
                                )
                            }
                        }
                    }

                    Box {
                        ControlButton(
                            onClick = { onShowAspectRatioMenu(true) },
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Aspect Ratio: ${playerState.selectedAspectRatio.label}",
                            tint = if (playerState.selectedAspectRatio != AspectRatioMode.FILL) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.White
                            },
                        )

                        DropdownMenu(
                            expanded = showAspectRatioMenu,
                            onDismissRequest = { onShowAspectRatioMenu(false) },
                        ) {
                            playerState.availableAspectRatios.forEach { aspectRatio ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = aspectRatio.label,
                                                fontWeight = if (aspectRatio == playerState.selectedAspectRatio) {
                                                    FontWeight.Bold
                                                } else {
                                                    FontWeight.Normal
                                                },
                                            )
                                            if (aspectRatio == playerState.selectedAspectRatio) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onAspectRatioChange(aspectRatio)
                                        onShowAspectRatioMenu(false)
                                    },
                                )
                            }
                        }
                    }
                    if (playerState.availableAudioTracks.size > 1) {
                        IconButton(onClick = onAudioTracksClick) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = "Audio Tracks",
                                tint = Color.White,
                            )
                        }
                    }

                    // Cast button with device selection
                    CastButton(
                        isCasting = playerState.isCasting,
                        onClick = onCastClick,
                    )
                }

                // Orientation toggle that also enables pop-up mode when exiting fullscreen
                IconButton(onClick = {
                    onOrientationToggle()
                    onPictureInPictureClick()
                }) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Toggle Orientation",
                    )

                    if (playerState.availableSubtitleTracks.isNotEmpty()) {
                        IconButton(onClick = onSubtitlesClick) {
                            Icon(
                                imageVector = Icons.Default.ClosedCaption,
                                contentDescription = "Subtitles",
                                tint = Color.White,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (playerState.duration > 0) 8.dp else 0.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CastButton(
                    isCasting = playerState.isCasting,
                    onClick = onCastClick,
                )

                IconButton(onClick = onPictureInPictureClick) {
                    Icon(
                        imageVector = Icons.Default.PictureInPicture,
                        contentDescription = "Picture in Picture",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun CastButton(
    isCasting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ControlButton(
        onClick = onClick,
        imageVector = if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
        contentDescription = if (isCasting) "Disconnect Cast" else "Cast to Device",
        tint = if (isCasting) Color.Green else Color.White,
        modifier = modifier,
    )
}

@Composable
private fun ControlButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
        )
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
