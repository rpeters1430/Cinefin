package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.Player
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.ExpressiveContentCard
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBar
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBarAction
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.image.rememberScreenWidthHeight
import com.rpeters.jellyfin.ui.theme.MusicGreen
import com.rpeters.jellyfin.ui.utils.rememberMediaColorPalette
import com.rpeters.jellyfin.ui.viewmodel.AudioPlaybackViewModel
import java.util.Locale

/**
 * Full-screen Now Playing screen for music playback.
 *
 * Deliberately minimal: artwork, title/artist/album, a single seek bar and one row of
 * transport controls. Queue and playback speed live in the top bar so the main area stays
 * focused on what's playing.
 */
@OptInAppExperimentalApis
@Composable
fun NowPlayingScreen(
    onNavigateBack: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    viewModel: AudioPlaybackViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()

    val defaultPrimary = MaterialTheme.colorScheme.primary
    val defaultBackground = MaterialTheme.colorScheme.background
    val artworkUrl = remember(playbackState.currentMediaItem) {
        playbackState.currentMediaItem?.mediaMetadata?.artworkUri?.toString()
    }
    val palette = rememberMediaColorPalette(
        imageUrl = artworkUrl,
        defaultPrimary = defaultPrimary,
        defaultBackground = defaultBackground,
    )

    // Local state for smooth seeking
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }

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
                    PlaybackSpeedButton(
                        playbackSpeed = playbackState.playbackSpeed,
                        onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                    )
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
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.darkMuted.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                )
                .padding(paddingValues),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Equal flexible spacers above and below center the content block as a whole.
                Spacer(modifier = Modifier.weight(1f))

                AlbumArtSection(
                    currentMediaItem = playbackState.currentMediaItem,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(32.dp))

                TrackInfoSection(
                    currentMediaItem = playbackState.currentMediaItem,
                    queuePosition = playbackState.currentMediaItemIndex
                        .takeIf { playbackState.currentMediaItem != null && queue.size > 1 && it in queue.indices }
                        ?.let { index -> index + 1 to queue.size },
                )

                Spacer(modifier = Modifier.height(20.dp))

                ProgressSection(
                    isSeeking = isSeeking,
                    currentPosition = if (isSeeking) seekPosition else playbackState.currentPosition,
                    duration = playbackState.duration,
                    onSeekStart = { isSeeking = true },
                    onSeekChange = { seekPosition = it },
                    onSeekEnd = { position ->
                        viewModel.seekTo(position)
                        isSeeking = false
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))

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

                Spacer(modifier = Modifier.weight(1f))
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
        ExpressiveContentCard(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .aspectRatio(1f),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
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
    queuePosition: Pair<Int, Int>?,
    modifier: Modifier = Modifier,
) {
    val metadata = currentMediaItem?.mediaMetadata
    val artist = metadata?.artist?.toString()?.takeIf { it.isNotBlank() }
    val album = metadata?.albumTitle?.toString()?.takeIf { it.isNotBlank() }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = metadata?.title?.toString() ?: "No track playing",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (artist != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Album and queue position share one muted line instead of separate chips.
        val secondaryLine = listOfNotNull(
            album,
            queuePosition?.let { (position, total) -> "$position of $total" },
        ).joinToString(" \u2022 ")
        if (secondaryLine.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = secondaryLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProgressSection(
    isSeeking: Boolean,
    currentPosition: Long,
    duration: Long,
    onSeekStart: () -> Unit,
    onSeekChange: (Long) -> Unit,
    onSeekEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingSeekPosition by remember { mutableStateOf(currentPosition.toFloat()) }
    val seekRange = 0f..duration.toFloat().coerceAtLeast(1f)
    val sliderState = remember(seekRange) {
        SliderState(
            value = if (duration > 0) pendingSeekPosition else 0f,
            trackRange = seekRange,
        )
    }

    LaunchedEffect(currentPosition, isSeeking, duration) {
        if (!isSeeking) {
            val target = if (duration > 0) currentPosition.toFloat() else 0f
            pendingSeekPosition = target
            sliderState.value = target.coerceIn(sliderState.trackRange)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            state = sliderState,
            onValueChange = {
                onSeekStart()
                pendingSeekPosition = it
                sliderState.value = it
                onSeekChange(it.toLong())
            },
            onValueChangeFinished = { onSeekEnd(pendingSeekPosition.toLong()) },
            colors = SliderDefaults.colors(
                thumbColor = MusicGreen,
                activeTrackColor = MusicGreen,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(currentPosition),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleControlButton(
            onClick = onShuffleClick,
            active = shuffleEnabled,
            icon = Icons.Filled.Shuffle,
            contentDescription = if (shuffleEnabled) "Shuffle on" else "Shuffle off",
        )
        IconButton(onClick = onSkipPreviousClick, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Filled.SkipPrevious,
                contentDescription = "Previous",
                modifier = Modifier.size(36.dp),
            )
        }
        FilledIconButton(
            onClick = onPlayPauseClick,
            modifier = Modifier.size(76.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MusicGreen,
                contentColor = Color.White,
            ),
        ) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(40.dp),
            )
        }
        IconButton(onClick = onSkipNextClick, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = "Next",
                modifier = Modifier.size(36.dp),
            )
        }
        ToggleControlButton(
            onClick = onRepeatClick,
            active = repeatMode != Player.REPEAT_MODE_OFF,
            icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
            contentDescription = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> "Repeat one"
                Player.REPEAT_MODE_ALL -> "Repeat all"
                else -> "Repeat off"
            },
        )
    }
}

/** Shuffle/repeat toggle: plain icon, tinted with the accent color when active. */
@Composable
private fun ToggleControlButton(
    onClick: () -> Unit,
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) MusicGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(26.dp),
        )
    }
}

/** Compact speed toggle for the top bar; cycles through common speeds on tap. */
@Composable
private fun PlaybackSpeedButton(
    playbackSpeed: Float,
    onSpeedChange: (Float) -> Unit,
) {
    val speeds = remember { listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f) }
    val label = if (playbackSpeed == 1.0f) {
        "1x"
    } else {
        "${String.format(Locale.US, "%.2f", playbackSpeed).trimEnd('0').trimEnd('.')}x"
    }
    TextButton(
        onClick = {
            val nextIndex = (speeds.indexOfFirst { kotlin.math.abs(it - playbackSpeed) < 0.05f } + 1) % speeds.size
            onSpeedChange(speeds[nextIndex])
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (playbackSpeed != 1.0f) MusicGreen else MaterialTheme.colorScheme.onSurfaceVariant,
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
    return String.format(Locale.getDefault(), "%d:%02d", minutes, remainingSeconds)
}
