package com.example.jellyfinandroid.ui.player.enhanced

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.jellyfinandroid.ui.player.AspectRatioMode
import com.example.jellyfinandroid.ui.player.VideoQuality
import kotlinx.coroutines.delay

@UnstableApi
@Composable
fun EnhancedVideoPlayerScreen(
    playerState: EnhancedVideoPlayerState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onQualityChange: (VideoQuality) -> Unit,
    onAspectRatioChange: (AspectRatioMode) -> Unit,
    onCastClick: () -> Unit,
    onSubtitlesClick: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    onBackClick: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    exoPlayer: ExoPlayer?,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current

    var controlsVisible by remember { mutableStateOf(true) }
    var gestureOverlayVisible by remember { mutableStateOf(false) }
    var gestureIcon by remember { mutableStateOf<ImageVector?>(null) }
    var gestureText by remember { mutableStateOf("") }

    // Auto-hide controls
    LaunchedEffect(controlsVisible, playerState.isPlaying) {
        if (controlsVisible && playerState.isPlaying && !playerState.showSettings) {
            delay(4000)
            controlsVisible = false
        }
    }

    // Hide gesture feedback after showing
    LaunchedEffect(gestureOverlayVisible) {
        if (gestureOverlayVisible) {
            delay(1000)
            gestureOverlayVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDoubleTap = { offset ->
                        val screenWidth = size.width
                        val isRightSide = offset.x > screenWidth / 2

                        if (isRightSide) {
                            onSeekBy(10000) // 10 seconds forward
                            gestureIcon = Icons.Default.Forward10
                            gestureText = "+10s"
                        } else {
                            onSeekBy(-10000) // 10 seconds backward
                            gestureIcon = Icons.Default.Replay10
                            gestureText = "-10s"
                        }

                        gestureOverlayVisible = true
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                )
            }
            .pointerInput(Unit) {
                var initialVolume = 0f
                var initialBrightness = 0f

                detectDragGestures(
                    onDragStart = { offset ->
                        initialVolume = playerState.volume
                        initialBrightness = playerState.brightness
                    },
                    onDrag = { change, dragAmount ->
                        val screenHeight = size.height
                        val screenWidth = size.width
                        val isLeftSide = change.position.x < screenWidth / 2
                        val deltaY = -dragAmount.y / screenHeight // Negative for natural gesture

                        if (isLeftSide) {
                            // Left side - brightness control
                            val newBrightness = (initialBrightness + deltaY).coerceIn(0f, 1f)
                            onBrightnessChange(newBrightness)

                            gestureIcon = Icons.Default.BrightnessHigh
                            gestureText = "${(newBrightness * 100).toInt()}%"
                        } else {
                            // Right side - volume control
                            val newVolume = (initialVolume + deltaY).coerceIn(0f, 1f)
                            onVolumeChange(newVolume)

                            gestureIcon = Icons.Default.VolumeUp
                            gestureText = "${(newVolume * 100).toInt()}%"
                        }

                        gestureOverlayVisible = true
                    },
                )
            },
    ) {
        // Video Player View
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    resizeMode = playerState.selectedAspectRatio.resizeMode
                }
            },
            update = { playerView ->
                playerView.resizeMode = playerState.selectedAspectRatio.resizeMode
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Minimized Player Mode
        if (playerState.isMinimized) {
            MinimizedPlayerOverlay(
                playerState = playerState,
                onPlayPause = onPlayPause,
                onClose = { /* Handle close */ },
                onExpand = { /* Handle expand */ },
            )
        }

        // Loading Indicator with Enhanced Animation
        if (playerState.isLoading) {
            EnhancedLoadingIndicator()
        }

        // Error Display
        playerState.error?.let { error ->
            ErrorDisplay(error = error)
        }

        // Gesture Feedback Overlay
        AnimatedVisibility(
            visible = gestureOverlayVisible,
            enter = scaleIn(spring(stiffness = Spring.StiffnessHigh)) + fadeIn(),
            exit = scaleOut(spring(stiffness = Spring.StiffnessHigh)) + fadeOut(),
        ) {
            GestureOverlay(
                icon = gestureIcon,
                text = gestureText,
            )
        }

        // Enhanced Controls
        AnimatedVisibility(
            visible = controlsVisible && !playerState.isMinimized,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
        ) {
            EnhancedVideoControls(
                playerState = playerState,
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                onSeekBy = onSeekBy,
                onSpeedChange = onSpeedChange,
                onQualityChange = onQualityChange,
                onAspectRatioChange = onAspectRatioChange,
                onCastClick = onCastClick,
                onSubtitlesClick = onSubtitlesClick,
                onPictureInPictureClick = onPictureInPictureClick,
                onBackClick = onBackClick,
                onFullscreenToggle = onFullscreenToggle,
                onSettingsClick = onSettingsClick,
            )
        }

        // Floating Action Buttons for Quick Actions
        if (!playerState.isMinimized && controlsVisible) {
            QuickActionFABs(
                playerState = playerState,
                onCastClick = onCastClick,
                onPictureInPictureClick = onPictureInPictureClick,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }

        // Cast Connection Status
        if (playerState.isCasting) {
            CastStatusIndicator(
                deviceName = playerState.castDeviceName ?: "Unknown Device",
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun MinimizedPlayerOverlay(
    playerState: EnhancedVideoPlayerState,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable { onExpand() },
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                // Thumbnail or play indicator
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                    )
                }

                Column {
                    Text(
                        text = playerState.itemName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )

                    LinearProgressIndicator(
                        progress = {
                            if (playerState.duration > 0) {
                                playerState.currentPosition.toFloat() / playerState.duration.toFloat()
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                }
            }

            IconButtonWithHaptics(
                icon = Icons.Default.ClosedCaption,
                contentDescription = "Close",
                onClick = onClose,
            )
        }
    }
}

@Composable
private fun EnhancedLoadingIndicator(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.padding(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp,
                    strokeCap = ProgressIndicatorDefaults.CircularIndeterminateStrokeCap,
                )

                Text(
                    text = "Loading video...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ErrorDisplay(
    error: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
            modifier = Modifier.padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Playback Error",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun GestureOverlay(
    icon: ImageVector?,
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier.padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }

                Text(
                    text = text,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(
                            color = Color.Black,
                            offset = Offset(2f, 2f),
                            blurRadius = 4f,
                        ),
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionFABs(
    playerState: EnhancedVideoPlayerState,
    onCastClick: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        // Cast FAB with badge if connected
        BadgedBox(
            badge = {
                if (playerState.isCasting) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        ) {
            ExtendedFloatingActionButton(
                onClick = onCastClick,
                icon = {
                    Icon(
                        imageVector = if (playerState.isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                        contentDescription = "Cast",
                    )
                },
                text = { Text(if (playerState.isCasting) "Casting" else "Cast") },
                containerColor = if (playerState.isCasting) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
            )
        }

        // Picture-in-Picture FAB
        ExtendedFloatingActionButton(
            onClick = onPictureInPictureClick,
            icon = {
                Icon(
                    imageVector = Icons.Default.PictureInPicture,
                    contentDescription = "Picture in Picture",
                )
            },
            text = { Text("PiP") },
        )
    }
}

@Composable
private fun CastStatusIndicator(
    deviceName: String,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
        modifier = modifier.padding(16.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
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
                    text = "Casting to $deviceName",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun IconButtonWithHaptics(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.White,
) {
    val hapticFeedback = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        color = Color.White.copy(alpha = 0.1f),
        shape = CircleShape,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.padding(12.dp),
        )
    }
}
