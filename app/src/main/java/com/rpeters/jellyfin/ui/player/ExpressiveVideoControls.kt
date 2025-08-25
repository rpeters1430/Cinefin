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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FourK
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sd
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
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
    onBackClick: () -> Unit,
    onFullscreenToggle: () -> Unit,
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
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f),
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY,
                        ),
                    ),
            )

            Column {
                // Top Controls Bar (simplified - removed settings, PiP, extra fullscreen)
                ExpressiveTopControls(
                    playerState = playerState,
                    onBackClick = onBackClick,
                    onCastClick = onCastClick,
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
                    onFullscreenToggle = onFullscreenToggle,
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
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (playerState.selectedQuality != null) {
                            Text(
                                text = playerState.selectedQuality.label,
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                // Right side - Cast button only
                AnimatedContent(
                    targetState = playerState.isCasting,
                    label = "cast_button",
                ) { isCasting ->
                    ExpressiveIconButton(
                        icon = if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                        contentDescription = if (isCasting) "Disconnect Cast" else "Cast to Device",
                        onClick = onCastClick,
                        isActive = isCasting,
                    )
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

                        BoxWithConstraints(
                            modifier = Modifier.weight(1f),
                        ) {
                            // Buffer progress (background)
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

                            // Main progress slider
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
                                    inactiveTrackColor = Color.Transparent,
                                ),
                            )
                        }
                    }

                    // Action buttons (right side)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                        )

                        Text(
                            text = formatTime(playerState.duration),
                            color = Color.White.copy(alpha = 0.7f),
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
        qualityLabel?.contains("4K", ignoreCase = true) == true -> Icons.Default.FourK
        qualityLabel?.contains("HD", ignoreCase = true) == true -> Icons.Default.Hd
        qualityLabel?.contains("SD", ignoreCase = true) == true -> Icons.Default.Sd
        qualityLabel?.contains("1080", ignoreCase = true) == true -> Icons.Default.Hd
        qualityLabel?.contains("720", ignoreCase = true) == true -> Icons.Default.Hd
        qualityLabel?.contains("480", ignoreCase = true) == true -> Icons.Default.Sd
        else -> Icons.Default.Movie // Default fallback icon
    }
}

@Composable
private fun ExpressiveIconButton(
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
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        } else {
            Color.White.copy(alpha = 0.1f)
        },
        shape = CircleShape,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun ExpressivePlayButton(
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
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
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
