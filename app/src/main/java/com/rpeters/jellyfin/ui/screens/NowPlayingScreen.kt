package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.Player
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.ExpressiveBlurSurface
import com.rpeters.jellyfin.ui.components.ExpressiveContentCard
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBar
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBarAction
import com.rpeters.jellyfin.ui.components.ExpressiveWavyLinearProgress
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.image.rememberScreenWidthHeight
import com.rpeters.jellyfin.ui.theme.JellyfinExpressiveTheme
import com.rpeters.jellyfin.ui.theme.MusicGreen
import com.rpeters.jellyfin.ui.viewmodel.AudioPlaybackViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Full-screen Now Playing screen for music playback.
 * Features:
 * - Album art display
 * - Track information
 * - Playback controls (play/pause, skip, shuffle, repeat)
 * - Progress bar with seek
 * - Queue management
 */
@OptInAppExperimentalApis
@Composable
fun NowPlayingScreen(
    onNavigateBack: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    viewModel: AudioPlaybackViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()

    // Local state for smooth seeking
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }

    // Update progress periodically when playing
    LaunchedEffect(playbackState.isPlaying) {
        while (isActive && playbackState.isPlaying) {
            delay(100)
            if (!isSeeking) {
                // Progress updates happen via the ViewModel
            }
        }
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(id = R.string.now_playing_title),
                navigationIcon = {
                    ExpressiveTopAppBarAction(
                        icon = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Close",
                        onClick = onNavigateBack,
                    )
                },
                actions = {
                    ExpressiveTopAppBarAction(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        onClick = onOpenQueue,
                    )
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MusicGreen.copy(alpha = 0.26f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                Color.Transparent,
                            ),
                            radius = 1100f,
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                AlbumArtSection(
                    currentMediaItem = playbackState.currentMediaItem,
                    modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.height(28.dp))

                TrackInfoSection(
                    currentMediaItem = playbackState.currentMediaItem,
                    queueSize = queue.size,
                )

                Spacer(modifier = Modifier.height(24.dp))

                ProgressSection(
                    currentPosition = if (isSeeking) seekPosition else currentPosition,
                    duration = duration,
                    onSeekStart = { isSeeking = true },
                    onSeekChange = { seekPosition = it },
                    onSeekEnd = { position ->
                        viewModel.seekTo(position)
                        isSeeking = false
                    },
                )

                Spacer(modifier = Modifier.height(24.dp))

                PlaybackControlsSection(
                    isPlaying = playbackState.isPlaying,
                    shuffleEnabled = playbackState.shuffleEnabled,
                    repeatMode = playbackState.repeatMode,
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onSkipPreviousClick = { viewModel.skipToPrevious() },
                    onSkipNextClick = { viewModel.skipToNext() },
                    onShuffleClick = { viewModel.toggleShuffle() },
                    onRepeatClick = { viewModel.toggleRepeat() },
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun AlbumArtSection(
    currentMediaItem: androidx.media3.common.MediaItem?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .aspectRatio(1f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MusicGreen.copy(alpha = 0.32f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                ),
        )
        ExpressiveContentCard(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .aspectRatio(1f)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(36.dp),
                ),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
            shape = RoundedCornerShape(36.dp),
        ) {
            if (currentMediaItem != null) {
                JellyfinAsyncImage(
                    model = currentMediaItem.mediaMetadata.artworkUri,
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    requestSize = rememberScreenWidthHeight(320.dp),
                )
            } else {
                // Placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(96.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackInfoSection(
    currentMediaItem: androidx.media3.common.MediaItem?,
    queueSize: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = currentMediaItem?.mediaMetadata?.title?.toString() ?: "No track playing",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = currentMediaItem?.mediaMetadata?.artist?.toString() ?: "",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = currentMediaItem?.mediaMetadata?.albumTitle?.toString() ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(16.dp))

        FlowRow(
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaybackMetaChip(
                label = currentMediaItem?.mediaMetadata?.artist?.toString()?.takeIf { it.isNotBlank() }
                    ?: "Unknown artist",
            )
            currentMediaItem?.mediaMetadata?.albumTitle?.toString()
                ?.takeIf { it.isNotBlank() }
                ?.let { album ->
                    PlaybackMetaChip(label = album)
                }
            PlaybackMetaChip(
                label = if (queueSize > 0) "$queueSize in queue" else "Single track",
                accent = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ProgressSection(
    currentPosition: Long,
    duration: Long,
    onSeekStart: () -> Unit,
    onSeekChange: (Long) -> Unit,
    onSeekEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveBlurSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.74f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            val progress = if (duration > 0) {
                currentPosition.toFloat() / duration.toFloat()
            } else {
                0f
            }
            ExpressiveWavyLinearProgress(
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MusicGreen,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            )

            Slider(
                value = if (duration > 0) currentPosition.toFloat() else 0f,
                onValueChange = {
                    onSeekStart()
                    onSeekChange(it.toLong())
                },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                onValueChangeFinished = { onSeekEnd(currentPosition) },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatTime(currentPosition),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatTime(duration),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlaybackControlsSection(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onPlayPauseClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveBlurSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryPlaybackButton(
                    onClick = onShuffleClick,
                    selected = shuffleEnabled,
                    icon = Icons.Filled.Shuffle,
                    contentDescription = "Shuffle",
                )
                SecondaryPlaybackButton(
                    onClick = onSkipPreviousClick,
                    selected = false,
                    icon = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    iconSize = 34.dp,
                )
                FilledIconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(88.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MusicGreen,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(48.dp),
                    )
                }
                SecondaryPlaybackButton(
                    onClick = onSkipNextClick,
                    selected = false,
                    icon = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    iconSize = 34.dp,
                )
                SecondaryPlaybackButton(
                    onClick = onRepeatClick,
                    selected = repeatMode != Player.REPEAT_MODE_OFF,
                    icon = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne
                        else -> Icons.Filled.Repeat
                    },
                    contentDescription = "Repeat",
                )
            }
        }
    }
}

@Composable
private fun PlaybackMetaChip(
    label: String,
    accent: Color = MusicGreen,
) {
    Surface(
        shape = JellyfinExpressiveTheme.shapes.pill,
        color = accent.copy(alpha = 0.14f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SecondaryPlaybackButton(
    onClick: () -> Unit,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    iconSize: androidx.compose.ui.unit.Dp = 28.dp,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Format milliseconds to MM:SS format
 */
private fun formatTime(millis: Long): String {
    val seconds = (millis / 1000).toInt()
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%d:%02d", minutes, remainingSeconds)
}
