@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.rpeters.jellyfin.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.ui.theme.MotionTokens

@UnstableApi
@Composable
fun ExpressiveVideoControls(
    playerState: VideoPlayerState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onQualityClick: () -> Unit,
    onAudioClick: () -> Unit, // New audio selection callback
    onCastClick: () -> Unit,
    onSubtitlesClick: () -> Unit,
    onAspectRatioChange: (AspectRatioMode) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onBackClick: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    supportsPip: Boolean,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = MotionTokens.mediaControlsEnter),
        exit = fadeOut(animationSpec = MotionTokens.mediaControlsExit),
        modifier = modifier,
    ) {
        Box {
            // Background gradient overlay
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                                Color.Transparent,
                                Color.Transparent,
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f),
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY,
                        ),
                    ),
            )

            Column {
                // Top Controls Bar
                ExpressiveTopControls(
                    playerState = playerState,
                    onBackClick = onBackClick,
                    onCastClick = onCastClick,
                    onPictureInPictureClick = onPictureInPictureClick,
                    supportsPip = supportsPip,
                )

                Spacer(modifier = Modifier.weight(1f))

                // Bottom Progress and Controls (now includes play button and all controls)
                ExpressiveBottomControls(
                    playerState = playerState,
                    onSeek = onSeek,
                    onPlayPause = onPlayPause,
                    onAudioClick = onAudioClick,
                    onQualityClick = onQualityClick,
                    onSubtitlesClick = onSubtitlesClick,
                    onAspectRatioChange = onAspectRatioChange,
                    onPlaybackSpeedChange = onPlaybackSpeedChange,
                    onFullscreenToggle = onFullscreenToggle,
                )
            }

            // Wavy Status Bar at the very bottom (visible when controls are hidden)
            if (!isVisible && playerState.duration > 0) {
                LinearWavyProgressIndicator(
                    progress = { playerState.currentPosition.toFloat() / playerState.duration.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    trackColor = Color.Transparent,
                    amplitude = { 0.1f },
                    wavelength = 40.dp,
                    waveSpeed = 20.dp,
                )
            }
        }
    }
}

@UnstableApi
@Composable
private fun ExpressiveTopControls(
    playerState: VideoPlayerState,
    onBackClick: () -> Unit,
    onCastClick: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    supportsPip: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier,
    ) {
        Surface(
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left side - Back button and title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ExpressiveIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBackClick,
                    )

                    Column {
                        Text(
                            text = playerState.itemName,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // Transcoding Information Display
                        if (playerState.isTranscoding || playerState.isDirectPlaying) {
                            val (icon, color, text) = when {
                                playerState.isDirectPlaying -> Triple(
                                    Icons.Default.PlayArrow,
                                    Color.Green,
                                    "Direct Play",
                                )
                                playerState.isTranscoding -> Triple(
                                    Icons.Default.Settings,
                                    Color(0xFFFF9800), // Orange color
                                    "Transcoding",
                                )
                                else -> Triple(
                                    Icons.Default.Info,
                                    Color.Gray,
                                    "Unknown",
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = text,
                                    tint = color.copy(alpha = 0.8f),
                                    modifier = Modifier.size(10.dp),
                                )
                                Text(
                                    text = text,
                                    color = color.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                playerState.transcodingReason?.let { reason ->
                                    Text(
                                        text = "• $reason",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        } else if (playerState.selectedQuality != null) {
                            Text(
                                text = playerState.selectedQuality.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                // Right side - PiP and Cast buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // PiP button (only show if supported)
                    if (supportsPip) {
                        ExpressiveIconButton(
                            icon = Icons.Default.PictureInPictureAlt,
                            contentDescription = "Picture in Picture",
                            onClick = onPictureInPictureClick,
                        )
                    }

                    // Cast button (click to disconnect when connected)
                    AnimatedContent(
                        targetState = playerState.isCastConnected,
                        label = "cast_button",
                    ) { isConnected ->
                        ExpressiveIconButton(
                            icon = if (isConnected) Icons.Default.CastConnected else Icons.Default.Cast,
                            contentDescription = if (isConnected) "Disconnect from ${playerState.castDeviceName ?: "Cast Device"}" else "Cast to Device",
                            onClick = onCastClick,
                            isActive = isConnected,
                        )
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun ExpressiveBottomControls(
    playerState: VideoPlayerState,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onAudioClick: () -> Unit,
    onQualityClick: () -> Unit,
    onSubtitlesClick: () -> Unit,
    onAspectRatioChange: (AspectRatioMode) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onFullscreenToggle: () -> Unit,

    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier,
    ) {
        Surface(
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                // Main controls row with play button, progress, and action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Play/Pause button (left side)
                    AnimatedContent(
                        targetState = playerState.isPlaying,
                        label = "play_pause_button",
                    ) { playing ->
                        ExpressivePlayButton(
                            icon = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            onClick = onPlayPause,
                            isLoading = playerState.isLoading,
                        )
                    }

                    // Progress bar (center, expandable)
                    if (playerState.duration > 0) {
                        var sliderPosition by remember { mutableFloatStateOf(0f) }
                        var isDragging by remember { mutableStateOf(false) }

                        LaunchedEffect(playerState.currentPosition, playerState.duration, isDragging) {
                            if (playerState.duration > 0 && !isDragging) {
                                sliderPosition =
                                    playerState.currentPosition.toFloat() / playerState.duration.toFloat()
                            }
                        }

                        Box(
                            modifier = Modifier.weight(1f),
                        ) {
                            // Buffer indicator (background layer) - subtle and less prominent
                            val bufferedProgress = if (playerState.duration > 0) {
                                (playerState.bufferedPosition.toFloat() / playerState.duration.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(bufferedProgress)
                                    .height(4.dp)
                                    .align(Alignment.CenterStart)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)),
                            )

                            // Main progress slider (foreground layer)
                            Slider(
                                value = sliderPosition,
                                onValueChange = { progress ->
                                    sliderPosition = progress
                                    isDragging = true
                                },
                                onValueChangeFinished = {
                                    val newPosition =
                                        (sliderPosition * playerState.duration).toLong()
                                    onSeek(newPosition)
                                    isDragging = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                                ),
                            )
                        }
                    }

                    // Action buttons (right side)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Aspect Ratio button
                        var showAspectRatioMenu by remember { mutableStateOf(false) }
                        Box {
                            ExpressiveIconButton(
                                icon = Icons.Default.AspectRatio,
                                contentDescription = "Aspect Ratio",
                                onClick = { showAspectRatioMenu = true },
                            )

                            DropdownMenu(
                                expanded = showAspectRatioMenu,
                                onDismissRequest = { showAspectRatioMenu = false },
                            ) {
                                playerState.availableAspectRatios.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label) },
                                        onClick = {
                                            onAspectRatioChange(mode)
                                            showAspectRatioMenu = false
                                        },
                                        leadingIcon = {
                                            if (mode == playerState.selectedAspectRatio) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        // Playback Speed button
                        var showSpeedMenu by remember { mutableStateOf(false) }
                        Box {
                            ExpressiveIconButton(
                                icon = Icons.Default.Speed,
                                contentDescription = "Playback Speed",
                                onClick = { showSpeedMenu = true },
                            )

                            DropdownMenu(
                                expanded = showSpeedMenu,
                                onDismissRequest = { showSpeedMenu = false },
                            ) {
                                listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                                    DropdownMenuItem(
                                        text = { Text("${speed}x") },
                                        onClick = {
                                            onPlaybackSpeedChange(speed)
                                            showSpeedMenu = false
                                        },
                                        leadingIcon = {
                                            if (speed == playerState.playbackSpeed) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        // Audio selection button
                        ExpressiveIconButton(
                            icon = Icons.Default.MusicNote,
                            contentDescription = "Audio Selection",
                            onClick = onAudioClick,
                        )

                        // Quality button with dynamic icon based on current quality
                        ExpressiveIconButton(
                            icon = getQualityIcon(playerState.selectedQuality?.label),
                            contentDescription = "Quality",
                            onClick = onQualityClick,
                        )

                        // Subtitles button
                        ExpressiveIconButton(
                            icon = Icons.Default.ClosedCaption,
                            contentDescription = "Subtitles",
                            onClick = onSubtitlesClick,
                        )

                        // Fullscreen button
                        ExpressiveIconButton(
                            icon = Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            onClick = onFullscreenToggle,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Time indicators
                if (playerState.duration > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatTime(playerState.currentPosition),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                        )

                        Text(
                            text = formatTime(playerState.duration),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

// Helper function to get quality icon based on quality text
private fun getQualityIcon(qualityLabel: String?): ImageVector {
    return when {
        qualityLabel?.contains("4K", ignoreCase = true) == true -> Icons.Default.HighQuality
        qualityLabel?.contains("HD", ignoreCase = true) == true -> Icons.Default.Hd
        qualityLabel?.contains("SD", ignoreCase = true) == true -> Icons.Default.Sd
        qualityLabel?.contains("1080", ignoreCase = true) == true -> Icons.Default.Hd
        qualityLabel?.contains("720", ignoreCase = true) == true -> Icons.Default.Hd
        qualityLabel?.contains("480", ignoreCase = true) == true -> Icons.Default.Sd
        else -> Icons.Default.Movie // Default fallback icon
    }
}

@Composable
internal fun ExpressiveIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    isActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.1f else 1f,
        animationSpec = MotionTokens.expressiveEnter,
        label = "icon_scale",
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)
        },
        shape = CircleShape,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
internal fun ExpressivePlayButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isLoading) 0.9f else 1f,
        animationSpec = MotionTokens.mediaPlayEasing,
        label = "play_button_scale",
    )

    FilledIconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp) // Smaller than the original main button
            .scale(scale),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        AnimatedContent(
            targetState = isLoading,
            label = "play_button_content",
        ) { loading ->
            if (loading) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    amplitude = 0.15f,
                    wavelength = 24.dp,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
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
