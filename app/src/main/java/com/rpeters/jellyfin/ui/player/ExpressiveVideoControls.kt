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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Sd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.ui.components.ExpressiveSelectableMenuItem
import com.rpeters.jellyfin.ui.theme.MotionTokens
import com.rpeters.jellyfin.ui.utils.rememberExpressiveHaptics

@UnstableApi
@Composable
fun ExpressiveVideoControls(
    playerState: VideoPlayerState,
    showPrimaryLoadingUi: Boolean,
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
    onPictureInPictureClick: () -> Unit,
    supportsPip: Boolean,
    isVisible: Boolean,
    overlayContent: Color = Color.White,
    overlayScrim: Color = Color.Black.copy(alpha = 0.7f),
    modifier: Modifier = Modifier,
) {
    val haptics = rememberExpressiveHaptics()
    
    val stableOnPlayPause = remember(onPlayPause, haptics, playerState.isPlaying) {
        {
            if (playerState.isPlaying) haptics.playbackPaused() else haptics.playbackStarted()
            onPlayPause()
        }
    }

    val stableOnSeek = remember(onSeek, haptics) {
        { position: Long ->
            haptics.lightClick()
            onSeek(position)
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = MotionTokens.mediaControlsEnter),
        exit = fadeOut(animationSpec = MotionTokens.mediaControlsExit),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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

            Column(modifier = Modifier.fillMaxSize()) {
                // Top Controls Bar
                ExpressiveTopControls(
                    playerState = playerState,
                    onBackClick = onBackClick,
                    onCastClick = onCastClick,
                    overlayContent = overlayContent,
                    overlayScrim = overlayScrim,
                )

                Spacer(modifier = Modifier.weight(1f))

                // Bottom Progress and Controls (now includes play button and all controls)
                ExpressiveBottomControls(
                    playerState = playerState,
                    showPrimaryLoadingUi = showPrimaryLoadingUi,
                    onSeek = stableOnSeek,
                    onPlayPause = stableOnPlayPause,
                    onAudioClick = onAudioClick,
                    onQualityClick = onQualityClick,
                    onSubtitlesClick = onSubtitlesClick,
                    onAspectRatioChange = onAspectRatioChange,
                    onPlaybackSpeedChange = onPlaybackSpeedChange,
                    onPictureInPictureClick = onPictureInPictureClick,
                    supportsPip = supportsPip,
                    overlayContent = overlayContent,
                    overlayScrim = overlayScrim,
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
    overlayContent: Color,
    overlayScrim: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        renderEffect = android.graphics.RenderEffect.createBlurEffect(
                            16f, 16f, android.graphics.Shader.TileMode.CLAMP
                        ).let { effect ->
                            @Suppress("DEPRECATION")
                            com.rpeters.jellyfin.ui.utils.asComposeRenderEffect(effect)
                        }
                    }
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left side - Back button and title
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ExpressiveIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBackClick,
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = playerState.itemName,
                            color = overlayContent,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // Transcoding Information Display
                        if (playerState.isTranscoding || playerState.isDirectPlaying || playerState.isDirectStreaming) {
                            val (icon, color, text) = when {
                                playerState.isDirectPlaying -> Triple(
                                    Icons.Default.PlayArrow,
                                    Color.Green,
                                    "Direct Play",
                                )
                                playerState.isDirectStreaming -> Triple(
                                    Icons.Default.PlayArrow,
                                    Color(0xFF00BCD4), // Cyan — video copied, audio transcoded
                                    "Direct Stream",
                                )

                                else -> Triple(
                                    Icons.Default.Settings,
                                    Color(0xFFFF9800), // Orange color
                                    "Transcoding",
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                        color = overlayContent.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        } else if (playerState.selectedQuality != null) {
                            Text(
                                text = playerState.selectedQuality.label,
                                color = overlayContent.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                // Right side - Cast button only (PiP moved to bottom controls)
                AnimatedContent(
                    targetState = playerState.isCastConnected,
                    label = "cast_button",
                ) { isConnected ->
                    Box(
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        ExpressiveIconButton(
                            icon = if (isConnected) Icons.Default.CastConnected else Icons.Default.Cast,
                            contentDescription = if (isConnected) "Disconnect from ${playerState.castDeviceName ?: "Cast Device"}" else "Cast to Device",
                            onClick = onCastClick,
                            isActive = isConnected,
                            contentColor = overlayContent,
                            containerColor = overlayScrim.copy(alpha = 0.35f),
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
    showPrimaryLoadingUi: Boolean,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onAudioClick: () -> Unit,
    onQualityClick: () -> Unit,
    onSubtitlesClick: () -> Unit,
    onAspectRatioChange: (AspectRatioMode) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onPictureInPictureClick: () -> Unit,
    supportsPip: Boolean,
    overlayContent: Color,
    overlayScrim: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        renderEffect = android.graphics.RenderEffect.createBlurEffect(
                            16f, 16f, android.graphics.Shader.TileMode.CLAMP
                        ).let { effect ->
                            @Suppress("DEPRECATION")
                            com.rpeters.jellyfin.ui.utils.asComposeRenderEffect(effect)
                        }
                    }
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                val effectiveDuration = playerState.duration.takeIf { it > 0L }
                var sliderPosition by remember(playerState.itemId) { mutableFloatStateOf(0f) }
                var isDragging by remember(playerState.itemId) { mutableStateOf(false) }

                LaunchedEffect(playerState.currentPosition, playerState.duration, isDragging) {
                    if (effectiveDuration != null && !isDragging) {
                        sliderPosition =
                            (playerState.currentPosition.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
                    }
                }

                ExpressiveWavySlider(
                    progress = sliderPosition,
                    bufferedProgress = if (effectiveDuration != null) {
                        (playerState.bufferedPosition.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    onValueChange = { progress ->
                        sliderPosition = progress
                        isDragging = true
                    },
                    onValueChangeFinished = {
                        effectiveDuration?.let { duration ->
                            val newPosition = (sliderPosition * duration).toLong()
                            onSeek(newPosition)
                        }
                        isDragging = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val displayedPosition = if (isDragging && effectiveDuration != null) {
                        (sliderPosition * effectiveDuration).toLong()
                    } else {
                        playerState.currentPosition
                    }
                    Text(
                        text = formatTime(displayedPosition),
                        color = overlayContent,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                    )

                    Text(
                        text = effectiveDuration?.let(::formatTime) ?: "--:--",
                        color = overlayContent.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val showSkipIntro = remember(playerState.introStartMs, playerState.introEndMs, playerState.currentPosition) {
                    val start = playerState.introStartMs
                    val end = playerState.introEndMs
                    start != null && end != null && playerState.currentPosition in start..end
                }
                val showSkipCredits = remember(playerState.outroStartMs, playerState.currentPosition) {
                    val start = playerState.outroStartMs
                    start != null && playerState.currentPosition >= start
                }
                if (showSkipIntro || showSkipCredits) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (showSkipIntro) {
                            PlayerActionChip(
                                label = "Skip Intro",
                                onClick = {
                                    onSeek(playerState.introEndMs ?: (playerState.currentPosition + 10_000))
                                },
                                overlayContent = overlayContent,
                                overlayScrim = overlayScrim,
                            )
                        }
                        if (showSkipCredits) {
                            PlayerActionChip(
                                label = "Skip Credits",
                                onClick = {
                                    onSeek(playerState.outroEndMs ?: (playerState.currentPosition + 10_000))
                                },
                                overlayContent = overlayContent,
                                overlayScrim = overlayScrim,
                            )
                        }
                    }
                }

                // Play/Pause and action buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Left side: Play/Pause and Skip buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Play/Pause button
                        AnimatedContent(
                            targetState = playerState.isPlaying,
                            label = "play_pause_button",
                        ) { playing ->
                            ExpressivePlayButton(
                                icon = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play",
                                onClick = onPlayPause,
                                isLoading = showPrimaryLoadingUi,
                                contentColor = overlayContent,
                            )
                        }

                        // Skip Backward 10s
                        ExpressiveIconButton(
                            icon = Icons.Default.Replay10,
                            contentDescription = "Skip Backward 10s",
                            onClick = { onSeek((playerState.currentPosition - 10000L).coerceAtLeast(0L)) },
                            contentColor = overlayContent,
                            containerColor = overlayScrim.copy(alpha = 0.35f),
                        )

                        // Skip Forward 10s
                        ExpressiveIconButton(
                            icon = Icons.Default.Forward10,
                            contentDescription = "Skip Forward 10s",
                            onClick = { onSeek((playerState.currentPosition + 10000L).coerceAtMost(playerState.duration)) },
                            contentColor = overlayContent,
                            containerColor = overlayScrim.copy(alpha = 0.35f),
                        )
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
                                contentColor = overlayContent,
                                containerColor = overlayScrim.copy(alpha = 0.35f),
                            )

                            DropdownMenu(
                                expanded = showAspectRatioMenu,
                                onDismissRequest = { showAspectRatioMenu = false },
                                containerColor = overlayScrim.copy(alpha = 0.96f),
                            ) {
                                Text(
                                    text = "Aspect Ratio",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = overlayContent,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                                playerState.availableAspectRatios.forEach { mode ->
                                    ExpressiveSelectableMenuItem(
                                        text = mode.label,
                                        selected = mode == playerState.selectedAspectRatio,
                                        onSelectedChange = {
                                            onAspectRatioChange(mode)
                                            showAspectRatioMenu = false
                                        },
                                        textColor = overlayContent,
                                        selectedColor = MaterialTheme.colorScheme.primary,
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
                                contentColor = overlayContent,
                                containerColor = overlayScrim.copy(alpha = 0.35f),
                            )

                            DropdownMenu(
                                expanded = showSpeedMenu,
                                onDismissRequest = { showSpeedMenu = false },
                                containerColor = overlayScrim.copy(alpha = 0.96f),
                            ) {
                                Text(
                                    text = "Playback Speed",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = overlayContent,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                                listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                                    ExpressiveSelectableMenuItem(
                                        text = "${speed}x",
                                        selected = speed == playerState.playbackSpeed,
                                        onSelectedChange = {
                                            onPlaybackSpeedChange(speed)
                                            showSpeedMenu = false
                                        },
                                        textColor = overlayContent,
                                        selectedColor = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }

                        // Audio selection button
                        ExpressiveIconButton(
                            icon = Icons.Default.MusicNote,
                            contentDescription = "Audio Selection",
                            onClick = onAudioClick,
                            contentColor = overlayContent,
                            containerColor = overlayScrim.copy(alpha = 0.35f),
                        )

                        // Quality button with dynamic icon based on current quality
                        ExpressiveIconButton(
                            icon = getQualityIcon(playerState.selectedQuality?.label),
                            contentDescription = "Quality",
                            onClick = onQualityClick,
                            contentColor = overlayContent,
                            containerColor = overlayScrim.copy(alpha = 0.35f),
                        )

                        // Subtitles button
                        ExpressiveIconButton(
                            icon = Icons.Default.ClosedCaption,
                            contentDescription = "Subtitles",
                            onClick = onSubtitlesClick,
                            contentColor = overlayContent,
                            containerColor = overlayScrim.copy(alpha = 0.35f),
                        )

                        // PiP button
                        if (supportsPip) {
                            ExpressiveIconButton(
                                icon = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Picture in Picture",
                                onClick = onPictureInPictureClick,
                                contentColor = overlayContent,
                                containerColor = overlayScrim.copy(alpha = 0.35f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerActionChip(
    label: String,
    onClick: () -> Unit,
    overlayContent: Color,
    overlayScrim: Color,
    highlighted: Boolean = false,
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimary else overlayContent,
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                overlayScrim.copy(alpha = 0.82f)
            },
            labelColor = if (highlighted) MaterialTheme.colorScheme.onPrimary else overlayContent,
        ),
    )
}

/**
 * Custom Wavy Slider for an expressive video playback experience.
 * Combines LinearWavyProgressIndicator for the track with a standard Slider for interaction.
 */
@Composable
private fun ExpressiveWavySlider(
    progress: Float,
    bufferedProgress: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    trackColor: Color = Color.White.copy(alpha = 0.22f),
    bufferedColor: Color = Color.White.copy(alpha = 0.38f),
    progressColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // Track Background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(trackColor),
        )

        // Buffer Indicator
        Box(
            modifier = Modifier
                .fillMaxWidth(bufferedProgress)
                .height(4.dp)
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .background(bufferedColor),
        )

        // Wavy Progress Track (using our shimmed version)
        androidx.compose.material3.LinearWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            color = progressColor,
            trackColor = Color.Transparent,
            amplitude = { 0.2f },
            wavelength = 40.dp,
            waveSpeed = 20.dp,
        )

        // Standard slider for interaction (invisible track)
        Slider(
            value = progress,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = progressColor,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
            ),
        )
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
    contentColor: Color = Color.White,
    containerColor: Color = Color.Black.copy(alpha = 0.3f),
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
            lerp(containerColor, MaterialTheme.colorScheme.primary, 0.55f)
        } else {
            containerColor
        },
        shape = CircleShape,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
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
    contentColor: Color = Color.White,
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
            .size(64.dp)
            .scale(scale),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = contentColor,
        ),
    ) {
        AnimatedContent(
            targetState = isLoading,
            label = "play_button_content",
        ) { loading ->
            if (loading) {
                androidx.compose.material3.CircularWavyProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = contentColor,
                    amplitude = 0.15f,
                    wavelength = 24.dp,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(32.dp),
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
