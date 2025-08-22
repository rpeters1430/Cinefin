package com.rpeters.jellyfin.ui.player.enhanced

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.ui.player.AspectRatioMode
import com.rpeters.jellyfin.ui.player.VideoQuality

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun EnhancedVideoControls(
    playerState: EnhancedVideoPlayerState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onQualityChange: (VideoQuality) -> Unit,
    onAspectRatioChange: (AspectRatioMode) -> Unit,
    onCastClick: () -> Unit,
    onSubtitlesClick: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    onBackClick: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Gradient background overlay
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.8f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.9f),
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY,
                    ),
                ),
        )

        Column {
            // Top Controls Bar
            EnhancedTopControlsBar(
                playerState = playerState,
                onBackClick = onBackClick,
                onCastClick = onCastClick,
                onSubtitlesClick = onSubtitlesClick,
                onPictureInPictureClick = onPictureInPictureClick,
                onFullscreenToggle = onFullscreenToggle,
                onSettingsClick = onSettingsClick,
                showMoreMenu = showMoreMenu,
                onShowMoreMenu = { showMoreMenu = it },
            )

            Spacer(modifier = Modifier.weight(1f))

            // Center playback controls
            EnhancedCenterPlaybackControls(
                playerState = playerState,
                onPlayPause = onPlayPause,
                onSeekBy = onSeekBy,
                showSpeedMenu = showSpeedMenu,
                onShowSpeedMenu = { showSpeedMenu = it },
                onSpeedChange = onSpeedChange,
            )

            Spacer(modifier = Modifier.weight(1f))

            // Chapter Navigation (if available)
            if (playerState.chapters.isNotEmpty()) {
                ChapterNavigationBar(
                    chapters = playerState.chapters,
                    currentChapter = playerState.currentChapter,
                    currentPosition = playerState.currentPosition,
                    duration = playerState.duration,
                    onChapterSelect = { chapter ->
                        onSeek(chapter.startTime)
                    },
                )
            }

            // Bottom Progress and Controls
            EnhancedBottomControlsBar(
                playerState = playerState,
                onSeek = onSeek,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnhancedTopControlsBar(
    playerState: EnhancedVideoPlayerState,
    onBackClick: () -> Unit,
    onCastClick: () -> Unit,
    onSubtitlesClick: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    showMoreMenu: Boolean,
    onShowMoreMenu: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
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
                // Left side - Back and title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Go back") } },
                        state = rememberTooltipState(),
                    ) {
                        EnhancedIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            onClick = onBackClick,
                        )
                    }

                    Column {
                        Text(
                            text = playerState.itemName,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            playerState.selectedQuality?.let { quality ->
                                Text(
                                    text = quality.label,
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            if (playerState.playbackSpeed != 1.0f) {
                                Text(
                                    text = "${playerState.playbackSpeed}x",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }

                // Right side - Action buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Subtitles with indicator
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Subtitles") } },
                        state = rememberTooltipState(),
                    ) {
                        EnhancedIconButton(
                            icon = Icons.Default.ClosedCaption,
                            contentDescription = "Subtitles",
                            onClick = onSubtitlesClick,
                        )
                    }

                    // Cast button with connection indicator
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(if (playerState.isCasting) "Disconnect Cast" else "Cast to Device") } },
                        state = rememberTooltipState(),
                    ) {
                        BadgedBox(
                            badge = {
                                if (playerState.isCasting) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                        ) {
                            AnimatedContent(
                                targetState = playerState.isCasting,
                                label = "cast_button",
                            ) { isCasting ->
                                EnhancedIconButton(
                                    icon = if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                                    contentDescription = if (isCasting) "Disconnect Cast" else "Cast to Device",
                                    onClick = onCastClick,
                                    isActive = isCasting,
                                )
                            }
                        }
                    }

                    // Picture in Picture
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Picture in Picture") } },
                        state = rememberTooltipState(),
                    ) {
                        EnhancedIconButton(
                            icon = Icons.Default.PictureInPicture,
                            contentDescription = "Picture in Picture",
                            onClick = onPictureInPictureClick,
                        )
                    }

                    // Fullscreen toggle
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(if (playerState.isFullscreen) "Exit Fullscreen" else "Fullscreen") } },
                        state = rememberTooltipState(),
                    ) {
                        AnimatedContent(
                            targetState = playerState.isFullscreen,
                            label = "fullscreen_button",
                        ) { isFullscreen ->
                            EnhancedIconButton(
                                icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
                                onClick = onFullscreenToggle,
                            )
                        }
                    }

                    // More options menu
                    Box {
                        EnhancedIconButton(
                            icon = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            onClick = { onShowMoreMenu(true) },
                        )

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { onShowMoreMenu(false) },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    onSettingsClick()
                                    onShowMoreMenu(false)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Settings, contentDescription = null)
                                },
                            )

                            DropdownMenuItem(
                                text = { Text("Add Bookmark") },
                                onClick = {
                                    // Handle bookmark
                                    onShowMoreMenu(false)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnhancedCenterPlaybackControls(
    playerState: EnhancedVideoPlayerState,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    showSpeedMenu: Boolean,
    onShowSpeedMenu: (Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Main playback controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Previous/Skip back 30s
            EnhancedSeekButton(
                icon = Icons.Default.Replay30,
                contentDescription = "Skip back 30s",
                onClick = { onSeekBy(-30000) },
                seekAmount = "-30s",
            )

            // Skip back 10s
            EnhancedSeekButton(
                icon = Icons.Default.Replay10,
                contentDescription = "Skip back 10s",
                onClick = { onSeekBy(-10000) },
                seekAmount = "-10s",
            )

            // Main play/pause button
            EnhancedMainPlayButton(
                isPlaying = playerState.isPlaying,
                isLoading = playerState.isLoading,
                onClick = onPlayPause,
            )

            // Skip forward 10s
            EnhancedSeekButton(
                icon = Icons.Default.Forward10,
                contentDescription = "Skip forward 10s",
                onClick = { onSeekBy(10000) },
                seekAmount = "+10s",
            )

            // Skip forward 30s
            EnhancedSeekButton(
                icon = Icons.Default.Forward30,
                contentDescription = "Skip forward 30s",
                onClick = { onSeekBy(30000) },
                seekAmount = "+30s",
            )
        }

        // Speed control
        Box {
            FilledIconButton(
                onClick = { onShowSpeedMenu(true) },
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (playerState.playbackSpeed != 1.0f) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.White.copy(alpha = 0.2f)
                    },
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Playback Speed",
                    tint = if (playerState.playbackSpeed != 1.0f) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        Color.White
                    },
                )
            }

            DropdownMenu(
                expanded = showSpeedMenu,
                onDismissRequest = { onShowSpeedMenu(false) },
            ) {
                playerState.availableSpeeds.forEach { speed ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "${speed}x",
                                fontWeight = if (speed == playerState.playbackSpeed) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                                color = if (speed == playerState.playbackSpeed) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        },
                        onClick = {
                            onSpeedChange(speed)
                            onShowSpeedMenu(false)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterNavigationBar(
    chapters: List<Chapter>,
    currentChapter: Chapter?,
    currentPosition: Long,
    duration: Long,
    onChapterSelect: (Chapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = chapters.isNotEmpty(),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
            ) {
                Text(
                    text = "Chapters",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(chapters) { chapter ->
                        ChapterItem(
                            chapter = chapter,
                            isActive = chapter == currentChapter,
                            onClick = { onChapterSelect(chapter) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterItem(
    chapter: Chapter,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "chapter_scale",
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = if (isActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        } else {
            Color.White.copy(alpha = 0.1f)
        },
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = chapter.title,
                color = if (isActive) MaterialTheme.colorScheme.primary else Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = formatTime(chapter.startTime),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun EnhancedBottomControlsBar(
    playerState: EnhancedVideoPlayerState,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
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
                if (playerState.duration > 0) {
                    // Enhanced progress bar with preview
                    EnhancedProgressBar(
                        currentPosition = playerState.currentPosition,
                        bufferedPosition = playerState.bufferedPosition,
                        duration = playerState.duration,
                        chapters = playerState.chapters,
                        onSeek = onSeek,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Time indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatTime(playerState.currentPosition),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )

                        // Current chapter name (if available)
                        playerState.currentChapter?.let { chapter ->
                            Text(
                                text = chapter.title,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                        }

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

@Composable
private fun EnhancedProgressBar(
    currentPosition: Long,
    bufferedPosition: Long,
    duration: Long,
    chapters: List<Chapter>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        // Buffer progress (background)
        LinearProgressIndicator(
            progress = {
                if (duration > 0) {
                    bufferedPosition.toFloat() / duration.toFloat()
                } else {
                    0f
                }
            },
            modifier = Modifier.fillMaxWidth(),
            color = Color.White.copy(alpha = 0.3f),
            trackColor = Color.White.copy(alpha = 0.1f),
        )

        // Chapter markers
        if (chapters.isNotEmpty() && duration > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                chapters.forEach { chapter ->
                    val progress = chapter.startTime.toFloat() / duration.toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(2.dp, 12.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(1.dp),
                                )
                                .align(Alignment.CenterEnd),
                        )
                    }
                }
            }
        }

        // Main progress slider
        Slider(
            value = if (duration > 0) {
                currentPosition.toFloat() / duration.toFloat()
            } else {
                0f
            },
            onValueChange = { progress ->
                val newPosition = (progress * duration).toLong()
                onSeek(newPosition)
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

@Composable
private fun EnhancedIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    isActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.1f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "icon_scale",
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        color = if (isActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
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
private fun EnhancedMainPlayButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isLoading) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "main_button_scale",
    )

    FilledIconButton(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier
            .size(80.dp)
            .scale(scale),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        AnimatedContent(
            targetState = isLoading to isPlaying,
            label = "main_button_content",
        ) { (loading, playing) ->
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp,
                    strokeCap = ProgressIndicatorDefaults.CircularIndeterminateStrokeCap,
                )
                playing -> Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = "Pause",
                    modifier = Modifier.size(40.dp),
                )
                else -> Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

@Composable
private fun EnhancedSeekButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    seekAmount: String,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.2f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "seek_button_scale",
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                isPressed = true
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        color = Color.White.copy(alpha = 0.15f),
        shape = CircleShape,
    ) {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
            )

            // Seek amount indicator
            Text(
                text = seekAmount,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 10.sp,
                    shadow = Shadow(
                        color = Color.Black,
                        offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                        blurRadius = 2f,
                    ),
                ),
                modifier = Modifier
                    .padding(top = 28.dp)
                    .alpha(0.8f),
            )
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
