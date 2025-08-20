package com.example.jellyfinandroid.ui.player

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onBackClick: () -> Unit,
    onOrientationToggle: () -> Unit,
    exoPlayer: ExoPlayer?,
    modifier: Modifier = Modifier,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showAspectRatioMenu by remember { mutableStateOf(false) }

    // Auto-hide controls after 3 seconds
    LaunchedEffect(controlsVisible) {
        if (controlsVisible && playerState.isPlaying) {
            delay(3000)
            controlsVisible = false
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
                onSubtitlesClick = onSubtitlesClick,
                onPictureInPictureClick = onPictureInPictureClick,
                onBackClick = onBackClick,
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
    onBackClick: () -> Unit,
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
        // Top Controls
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = playerState.itemName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Quality settings
                Box {
                    IconButton(onClick = { onShowQualityMenu(true) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Quality Settings",
                            tint = Color.White,
                        )
                    }

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

                // Aspect ratio settings
                Box {
                    IconButton(onClick = { onShowAspectRatioMenu(true) }) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Aspect Ratio Settings",
                            tint = Color.White,
                        )
                    }

                    DropdownMenu(
                        expanded = showAspectRatioMenu,
                        onDismissRequest = { onShowAspectRatioMenu(false) },
                    ) {
                        playerState.availableAspectRatios.forEach { aspectRatio ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = aspectRatio.label,
                                        fontWeight = if (aspectRatio == playerState.selectedAspectRatio) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        },
                                    )
                                },
                                onClick = {
                                    onAspectRatioChange(aspectRatio)
                                    onShowAspectRatioMenu(false)
                                },
                            )
                        }
                    }
                }

                // Subtitles button
                IconButton(onClick = onSubtitlesClick) {
                    Icon(
                        imageVector = Icons.Default.ClosedCaption,
                        contentDescription = "Subtitles",
                        tint = Color.White,
                    )
                }

                // Cast button with device selection
                CastButton(
                    isCasting = playerState.isCasting,
                    onClick = onCastClick,
                )

                // Picture in Picture
                IconButton(onClick = onPictureInPictureClick) {
                    Icon(
                        imageVector = Icons.Default.PictureInPicture,
                        contentDescription = "Picture in Picture",
                        tint = Color.White,
                    )
                }

                // Orientation toggle
                IconButton(onClick = onOrientationToggle) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Toggle Orientation",
                        tint = Color.White,
                    )
                }
            }
        }

        // Center Play/Pause Button
        Box(
            modifier = Modifier.align(Alignment.Center),
        ) {
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        CircleShape,
                    ),
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Progress Bar
            if (playerState.duration > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = formatTime(playerState.currentPosition),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        // Buffered progress (background)
                        LinearProgressIndicator(
                            progress = {
                                if (playerState.duration > 0) {
                                    playerState.bufferedPosition.toFloat() / playerState.duration.toFloat()
                                } else {
                                    0f
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White.copy(alpha = 0.3f),
                            trackColor = Color.White.copy(alpha = 0.1f),
                        )

                        // Playback progress
                        Slider(
                            value = if (playerState.duration > 0) {
                                playerState.currentPosition.toFloat() / playerState.duration.toFloat()
                            } else {
                                0f
                            },
                            onValueChange = { progress ->
                                val newPosition = (progress * playerState.duration).toLong()
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
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
    ) {
        Icon(
            imageVector = if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
            contentDescription = if (isCasting) "Disconnect Cast" else "Cast to Device",
            tint = if (isCasting) Color.Green else Color.White,
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
