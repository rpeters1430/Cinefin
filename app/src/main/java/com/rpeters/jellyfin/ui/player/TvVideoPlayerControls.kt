package com.rpeters.jellyfin.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import com.rpeters.jellyfin.ui.tv.tvKeyboardHandler
import kotlinx.coroutines.delay
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

/**
 * TV-optimized video player controls with D-pad navigation and focus management
 * Designed for 10-foot viewing distance with large, readable text and proper focus indicators
 */
@OptIn(ExperimentalTvMaterial3Api::class, UnstableApi::class)
@Composable
fun TvVideoPlayerControls(
    playerState: VideoPlayerState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onShowSettings: () -> Unit,
    onBack: () -> Unit,
    controlsVisible: Boolean,
    onHideControls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Control focus management
    var focusedControl by remember { mutableIntStateOf(CONTROL_PLAY_PAUSE) }
    val playPauseFocusRequester = remember { FocusRequester() }
    val seekBackFocusRequester = remember { FocusRequester() }
    val seekForwardFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }

    // Request focus on play/pause button when controls become visible
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(100) // Small delay to ensure layout is ready
            playPauseFocusRequester.requestFocus()
        }
    }

    // Auto-hide controls after 5 seconds when playing
    LaunchedEffect(controlsVisible, playerState.isPlaying) {
        if (controlsVisible && playerState.isPlaying) {
            delay(5000)
            onHideControls()
        }
    }

    AnimatedVisibility(
        visible = controlsVisible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.9f),
                        ),
                    ),
                )
                .tvKeyboardHandler(
                    onBack = onBack,
                    onPlayPause = onPlayPause,
                ),
        ) {
            // Top bar with title
            TvTopBar(
                itemName = playerState.itemName,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(48.dp),
            )

            // Center play/pause button
            TvCenterControls(
                isPlaying = playerState.isPlaying,
                isLoading = playerState.isLoading,
                onPlayPause = onPlayPause,
                modifier = Modifier.align(Alignment.Center),
            )

            // Bottom controls with progress bar and buttons
            TvBottomControls(
                playerState = playerState,
                onPlayPause = onPlayPause,
                onSeekBack = onSeekBack,
                onSeekForward = onSeekForward,
                onShowSettings = onShowSettings,
                playPauseFocusRequester = playPauseFocusRequester,
                seekBackFocusRequester = seekBackFocusRequester,
                seekForwardFocusRequester = seekForwardFocusRequester,
                settingsFocusRequester = settingsFocusRequester,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(48.dp),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvTopBar(
    itemName: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvText(
            text = itemName,
            style = TvMaterialTheme.typography.headlineMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvCenterControls(
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isLoading) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = modifier.size(64.dp),
            color = Color.White,
            strokeWidth = 6.dp,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class, UnstableApi::class)
@Composable
private fun TvBottomControls(
    playerState: VideoPlayerState,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onShowSettings: () -> Unit,
    playPauseFocusRequester: FocusRequester,
    seekBackFocusRequester: FocusRequester,
    seekForwardFocusRequester: FocusRequester,
    settingsFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Time and progress bar
        TvProgressSection(playerState = playerState)

        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Seek back button
            TvControlButton(
                icon = Icons.Default.FastRewind,
                contentDescription = "Seek back 10 seconds",
                onClick = onSeekBack,
                focusRequester = seekBackFocusRequester,
                modifier = Modifier
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionRight -> {
                                    playPauseFocusRequester.requestFocus()
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    },
            )

            Spacer(modifier = Modifier.width(32.dp))

            // Play/Pause button (larger and primary)
            TvPlayPauseButton(
                isPlaying = playerState.isPlaying,
                onClick = onPlayPause,
                focusRequester = playPauseFocusRequester,
                modifier = Modifier
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionLeft -> {
                                    seekBackFocusRequester.requestFocus()
                                    true
                                }
                                Key.DirectionRight -> {
                                    seekForwardFocusRequester.requestFocus()
                                    true
                                }
                                Key.DirectionUp -> {
                                    settingsFocusRequester.requestFocus()
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    },
            )

            Spacer(modifier = Modifier.width(32.dp))

            // Seek forward button
            TvControlButton(
                icon = Icons.Default.FastForward,
                contentDescription = "Seek forward 10 seconds",
                onClick = onSeekForward,
                focusRequester = seekForwardFocusRequester,
                modifier = Modifier
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionLeft -> {
                                    playPauseFocusRequester.requestFocus()
                                    true
                                }
                                Key.DirectionRight -> {
                                    settingsFocusRequester.requestFocus()
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    },
            )

            Spacer(modifier = Modifier.width(48.dp))

            // Settings button
            TvControlButton(
                icon = Icons.Default.Settings,
                contentDescription = "Settings",
                onClick = onShowSettings,
                focusRequester = settingsFocusRequester,
                modifier = Modifier
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionLeft -> {
                                    seekForwardFocusRequester.requestFocus()
                                    true
                                }
                                Key.DirectionDown -> {
                                    playPauseFocusRequester.requestFocus()
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, UnstableApi::class)
@Composable
private fun TvProgressSection(
    playerState: VideoPlayerState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Time display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TvText(
                text = formatTime(playerState.currentPosition),
                style = TvMaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
            TvText(
                text = formatTime(playerState.duration),
                style = TvMaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
        }

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.2f)),
        ) {
            if (playerState.duration > 0) {
                val progress = playerState.currentPosition.toFloat() / playerState.duration.toFloat()

                // Buffered progress
                val buffered = playerState.bufferedPosition.toFloat() / playerState.duration.toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth(buffered.coerceIn(0f, 1f))
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.4f)),
                )

                // Current progress
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(TvMaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvControlButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1f,
        label = "tv_control_button_scale",
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .size(80.dp)
            .scale(scale)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            },
        colors = CardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.15f),
            focusedContainerColor = TvMaterialTheme.colorScheme.primary,
        ),
        shape = CardDefaults.shape(shape = CircleShape),
        border = CardDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(3.dp, Color.White),
                shape = CircleShape,
            ),
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.2f else 1f,
        label = "tv_play_pause_scale",
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .size(96.dp)
            .scale(scale)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            },
        colors = CardDefaults.colors(
            containerColor = TvMaterialTheme.colorScheme.primary,
            focusedContainerColor = TvMaterialTheme.colorScheme.primaryContainer,
        ),
        shape = CardDefaults.shape(shape = CircleShape),
        border = CardDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(4.dp, Color.White),
                shape = CircleShape,
            ),
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

/**
 * TV-optimized settings dialog with D-pad navigation
 */
@OptIn(ExperimentalTvMaterial3Api::class, UnstableApi::class)
@Composable
fun TvPlayerSettingsDialog(
    playerState: VideoPlayerState,
    onDismiss: () -> Unit,
    onAudioTrackSelect: (TrackInfo) -> Unit,
    onSubtitleTrackSelect: (TrackInfo?) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabFocusRequesters = remember { List(4) { FocusRequester() } }

    // Request focus on first tab when dialog opens
    LaunchedEffect(Unit) {
        delay(100)
        tabFocusRequesters[0].requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .tvKeyboardHandler(onBack = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            onClick = { /* Non-interactive */ },
            colors = CardDefaults.colors(
                containerColor = TvMaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .padding(48.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                // Title
                TvText(
                    text = "Player Settings",
                    style = TvMaterialTheme.typography.headlineMedium,
                    color = TvMaterialTheme.colorScheme.onSurface,
                )

                // Audio tracks
                if (playerState.availableAudioTracks.isNotEmpty()) {
                    TvSettingsSection(
                        title = "Audio Track",
                        items = playerState.availableAudioTracks.map { it.displayName },
                        selectedIndex = playerState.availableAudioTracks.indexOfFirst { it.isSelected },
                        onItemSelect = { index ->
                            onAudioTrackSelect(playerState.availableAudioTracks[index])
                        },
                        focusRequester = tabFocusRequesters[0],
                    )
                }

                // Subtitles
                if (playerState.availableSubtitleTracks.isNotEmpty()) {
                    TvSettingsSection(
                        title = "Subtitles",
                        items = listOf("Off") + playerState.availableSubtitleTracks.map { it.displayName },
                        selectedIndex = if (playerState.selectedSubtitleTrack == null) {
                            0
                        } else {
                            playerState.availableSubtitleTracks.indexOfFirst { it.isSelected } + 1
                        },
                        onItemSelect = { index ->
                            if (index == 0) {
                                onSubtitleTrackSelect(null)
                            } else {
                                onSubtitleTrackSelect(playerState.availableSubtitleTracks[index - 1])
                            }
                        },
                        focusRequester = tabFocusRequesters[1],
                    )
                }

                // Playback speed
                TvSettingsSection(
                    title = "Playback Speed",
                    items = listOf("0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x"),
                    selectedIndex = when (playerState.playbackSpeed) {
                        0.75f -> 0
                        1.0f -> 1
                        1.25f -> 2
                        1.5f -> 3
                        1.75f -> 4
                        2.0f -> 5
                        else -> 1
                    },
                    onItemSelect = { index ->
                        val speed = when (index) {
                            0 -> 0.75f
                            1 -> 1.0f
                            2 -> 1.25f
                            3 -> 1.5f
                            4 -> 1.75f
                            5 -> 2.0f
                            else -> 1.0f
                        }
                        onPlaybackSpeedChange(speed)
                    },
                    focusRequester = tabFocusRequesters[2],
                )

                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(tabFocusRequesters[3]),
                ) {
                    TvText("Close")
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSettingsSection(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onItemSelect: (Int) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TvText(
            text = title,
            style = TvMaterialTheme.typography.titleLarge,
            color = TvMaterialTheme.colorScheme.onSurface,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEachIndexed { index, item ->
                val isFirst = index == 0
                Button(
                    onClick = { onItemSelect(index) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (isFirst) it.focusRequester(focusRequester) else it },
                    colors = if (index == selectedIndex) {
                        ButtonDefaults.colors(
                            containerColor = TvMaterialTheme.colorScheme.primary,
                            contentColor = TvMaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        ButtonDefaults.colors(
                            containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
                            contentColor = TvMaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                ) {
                    TvText(
                        text = item,
                        fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

// Control focus indices
private const val CONTROL_SEEK_BACK = 0
private const val CONTROL_PLAY_PAUSE = 1
private const val CONTROL_SEEK_FORWARD = 2
private const val CONTROL_SETTINGS = 3
