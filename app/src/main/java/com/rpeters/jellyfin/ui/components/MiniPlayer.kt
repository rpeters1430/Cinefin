package com.rpeters.jellyfin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.MediaItem
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.image.rememberCoilSize
import com.rpeters.jellyfin.ui.player.audio.AudioPlaybackState
import com.rpeters.jellyfin.ui.theme.MusicGreen
import com.rpeters.jellyfin.ui.viewmodel.AudioPlaybackViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Mini player component that appears at the bottom of screens when audio is playing.
 * Features:
 * - Compact display of current track
 * - Play/Pause and Skip controls
 * - Progress indicator
 * - Tap to expand to full Now Playing screen
 */
@OptInAppExperimentalApis
@Composable
fun MiniPlayer(
    onExpandClick: () -> Unit,
    viewModel: AudioPlaybackViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()

    // Update progress periodically when playing
    LaunchedEffect(playbackState.isPlaying, playbackState.currentMediaItem) {
        while (isActive && playbackState.isPlaying) {
            delay(1000) // Update every second for mini player
            // Updates happen via ViewModel
        }
    }

    // Only show if there's a current media item
    AnimatedVisibility(
        visible = playbackState.currentMediaItem != null,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(300),
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(300),
        ),
    ) {
        MiniPlayerContent(
            playbackState = playbackState,
            currentPosition = currentPosition,
            duration = duration,
            onExpandClick = onExpandClick,
            onPlayPauseClick = { viewModel.togglePlayPause() },
            onSkipNextClick = { viewModel.skipToNext() },
            modifier = modifier,
        )
    }
}

@Composable
private fun MiniPlayerContent(
    playbackState: AudioPlaybackState,
    currentPosition: Long,
    duration: Long,
    onExpandClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveBlurSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(28.dp),
            )
            .clickable(onClick = onExpandClick),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArtThumbnail(
                    mediaItem = playbackState.currentMediaItem,
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MusicGreen.copy(alpha = 0.15f),
                        contentColor = MusicGreen,
                    ) {
                        Text(
                            text = "Now Playing",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }

                    Text(
                        text = playbackState.currentMediaItem?.mediaMetadata?.title?.toString()
                            ?: "Unknown Track",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = playbackState.currentMediaItem?.mediaMetadata?.artist?.toString()
                            ?: "Unknown Artist",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MusicGreen,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    IconButton(
                        onClick = onSkipNextClick,
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = "Skip Next",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            if (duration > 0) {
                ExpressiveWavyLinearProgress(
                    progress = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .padding(horizontal = 14.dp),
                    color = MusicGreen,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatChipTime(currentPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatChipTime(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumArtThumbnail(
    mediaItem: MediaItem?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MusicGreen.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
                    ),
                ),
                shape = CircleShape,
            )
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (mediaItem?.mediaMetadata?.artworkUri != null) {
            JellyfinAsyncImage(
                model = mediaItem.mediaMetadata.artworkUri,
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                requestSize = rememberCoilSize(56.dp),
                modifier = Modifier.size(56.dp),
            )
        } else {
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
    }
}

/**
 * Extension function to easily add MiniPlayer to any Scaffold
 */
@Composable
fun Modifier.withMiniPlayer(
    onExpandClick: () -> Unit,
    viewModel: AudioPlaybackViewModel = hiltViewModel(),
): Modifier {
    return this.then(
        Modifier.padding(
            bottom = 72.dp, // Make room for mini player
        ),
    )
}

private fun formatChipTime(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
