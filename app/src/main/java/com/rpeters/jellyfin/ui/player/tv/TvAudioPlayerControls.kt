package com.rpeters.jellyfin.ui.player.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.rpeters.jellyfin.ui.tv.requestInitialFocus

/**
 * TV-optimized audio player controls with D-pad navigation
 * All controls are accessible via D-pad and have clear focus indicators
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAudioPlayerControls(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    currentPosition: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onShowQueue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Focus management for controls
    val playPauseFocusRequester = remember { FocusRequester() }
    val seekBackFocusRequester = remember { FocusRequester() }
    val seekForwardFocusRequester = remember { FocusRequester() }
    val skipPreviousFocusRequester = remember { FocusRequester() }
    val skipNextFocusRequester = remember { FocusRequester() }
    val shuffleFocusRequester = remember { FocusRequester() }
    val repeatFocusRequester = remember { FocusRequester() }
    val queueFocusRequester = remember { FocusRequester() }
    val backFocusRequester = remember { FocusRequester() }

    var focusedControl by remember { mutableIntStateOf(CONTROL_PLAY_PAUSE) }

    // Request initial focus on play/pause button
    playPauseFocusRequester.requestInitialFocus()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Progress bar and time
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Time labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatTime(currentPosition),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = Color.White,
                )
                Text(
                    text = formatTime(duration),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = Color.White,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.3f),
            )
        }

        // Main playback controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Seek backward
            TvAudioControlButton(
                icon = Icons.Default.FastRewind,
                contentDescription = "Seek backward 10s",
                onClick = onSeekBackward,
                focusRequester = seekBackFocusRequester,
                onFocusChanged = { if (it) focusedControl = CONTROL_SEEK_BACK },
                size = 56.dp,
                iconSize = 32.dp,
            )

            // Skip previous
            TvAudioControlButton(
                icon = Icons.Default.SkipPrevious,
                contentDescription = "Previous track",
                onClick = onSkipPrevious,
                focusRequester = skipPreviousFocusRequester,
                onFocusChanged = { if (it) focusedControl = CONTROL_SKIP_PREVIOUS },
                size = 64.dp,
                iconSize = 36.dp,
            )

            // Play/Pause (primary control)
            TvAudioControlButton(
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                onClick = onPlayPause,
                focusRequester = playPauseFocusRequester,
                onFocusChanged = { if (it) focusedControl = CONTROL_PLAY_PAUSE },
                isPrimary = true,
                size = 80.dp,
                iconSize = 48.dp,
            )

            // Skip next
            TvAudioControlButton(
                icon = Icons.Default.SkipNext,
                contentDescription = "Next track",
                onClick = onSkipNext,
                focusRequester = skipNextFocusRequester,
                onFocusChanged = { if (it) focusedControl = CONTROL_SKIP_NEXT },
                size = 64.dp,
                iconSize = 36.dp,
            )

            // Seek forward
            TvAudioControlButton(
                icon = Icons.Default.FastForward,
                contentDescription = "Seek forward 10s",
                onClick = onSeekForward,
                focusRequester = seekForwardFocusRequester,
                onFocusChanged = { if (it) focusedControl = CONTROL_SEEK_FORWARD },
                size = 56.dp,
                iconSize = 32.dp,
            )
        }

        // Secondary controls row
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Back button
            TvAudioControlButton(
                icon = Icons.Default.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                focusRequester = backFocusRequester,
                onFocusChanged = { if (it) focusedControl = CONTROL_BACK },
                size = 48.dp,
                iconSize = 24.dp,
            )

            // Shuffle button
            TvAudioControlButton(
                icon = Icons.Default.Shuffle,
                contentDescription = "Toggle shuffle",
                onClick = onToggleShuffle,
                focusRequester = shuffleFocusRequester,
                onFocusChanged = { if (it) focusedControl = CONTROL_SHUFFLE },
                isActive = shuffleEnabled,
                size = 48.dp,
                iconSize = 24.dp,
            )

            // Repeat button
            TvAudioControlButton(
                icon = when (repeatMode) {
                    androidx.media3.common.Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                },
                contentDescription = "Toggle repeat",
                onClick = onToggleRepeat,
                focusRequester = repeatFocusRequester,
                onFocusChanged = { if (it) focusedControl = CONTROL_REPEAT },
                isActive = repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF,
                size = 48.dp,
                iconSize = 24.dp,
            )

            // Queue button
            TvAudioControlButton(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = "Show queue",
                onClick = onShowQueue,
                focusRequester = queueFocusRequester,
                onFocusChanged = { if (it) focusedControl = CONTROL_QUEUE },
                size = 48.dp,
                iconSize = 24.dp,
            )
        }
    }
}

/**
 * Reusable TV audio control button with focus support
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvAudioControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    isActive: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 64.dp,
    iconSize: androidx.compose.ui.unit.Dp = 32.dp,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1f,
        label = "button_scale",
    )

    val backgroundColor = when {
        isPrimary && isFocused -> MaterialTheme.colorScheme.primary
        isPrimary -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        isActive && isFocused -> MaterialTheme.colorScheme.secondary
        isActive -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
        isFocused -> Color.White.copy(alpha = 0.9f)
        else -> Color.White.copy(alpha = 0.3f)
    }

    val iconColor = when {
        isPrimary -> Color.White
        isActive -> Color.White
        isFocused -> Color.Black
        else -> Color.White
    }

    Box(
        modifier = modifier
            .scale(scale)
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 3.dp,
                        color = Color.White,
                        shape = CircleShape,
                    )
                } else {
                    Modifier
                },
            )
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                onFocusChanged(focusState.isFocused)
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionCenter) {
                    onClick()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = iconColor,
        )
    }
}

/**
 * Format milliseconds to MM:SS format for display
 */
private fun formatTime(milliseconds: Long): String {
    if (milliseconds <= 0) return "0:00"
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / (1000 * 60)) % 60
    val hours = milliseconds / (1000 * 60 * 60)

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

// Control indices for focus management
private const val CONTROL_BACK = 0
private const val CONTROL_SHUFFLE = 1
private const val CONTROL_REPEAT = 2
private const val CONTROL_QUEUE = 3
private const val CONTROL_SEEK_BACK = 4
private const val CONTROL_SKIP_PREVIOUS = 5
private const val CONTROL_PLAY_PAUSE = 6
private const val CONTROL_SKIP_NEXT = 7
private const val CONTROL_SEEK_FORWARD = 8
