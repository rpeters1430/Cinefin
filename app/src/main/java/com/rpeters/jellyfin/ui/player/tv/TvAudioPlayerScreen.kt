package com.rpeters.jellyfin.ui.player.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.tv.TvScreenFocusScope
import com.rpeters.jellyfin.ui.tv.rememberTvFocusManager
import com.rpeters.jellyfin.ui.viewmodel.AudioPlaybackViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * TV-optimized audio/music player screen with 10-foot UI design
 * Features large album art, readable text, and D-pad navigation
 */
@OptInAppExperimentalApis
@Composable
fun TvAudioPlayerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AudioPlaybackViewModel = hiltViewModel(),
) {
    val focusManager = rememberTvFocusManager()
    val playbackState by viewModel.playbackState.collectAsState()
    val queue by viewModel.queue.collectAsState()

    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showQueue by remember { mutableStateOf(false) }

    // Update position from playback state
    LaunchedEffect(playbackState.currentPosition, playbackState.duration) {
        currentPosition = playbackState.currentPosition
        duration = playbackState.duration
    }

    // Periodic position update when playing
    LaunchedEffect(playbackState.isPlaying) {
        while (isActive && playbackState.isPlaying) {
            delay(500) // Update every 500ms
            currentPosition = playbackState.currentPosition
            duration = playbackState.duration
        }
    }

    TvScreenFocusScope(
        screenKey = "tv_audio_player",
        focusManager = focusManager,
    ) {
        Box(
            modifier = modifier.fillMaxSize(),
        ) {
            // Background with blurred album art
            playbackState.currentMediaItem?.let { mediaItem ->
                val artworkUri = mediaItem.mediaMetadata.artworkUri?.toString()
                if (artworkUri != null) {
                    AsyncImage(
                        model = artworkUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(80.dp),
                        contentScale = ContentScale.Crop,
                        alpha = 0.3f,
                    )
                }
            }

            // Gradient overlay for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.9f),
                            ),
                        ),
                    ),
            )

            // Main content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Header with "Now Playing" text
                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Light,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Album art and track info section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(64.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Album art
                    AlbumArtDisplay(
                        artworkUri = playbackState.currentMediaItem?.mediaMetadata?.artworkUri?.toString(),
                        isPlaying = playbackState.isPlaying,
                        modifier = Modifier.weight(0.4f),
                    )

                    // Track information
                    Column(
                        modifier = Modifier.weight(0.6f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Track title
                        Text(
                            text = playbackState.currentMediaItem?.mediaMetadata?.title?.toString()
                                ?: "No track playing",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // Artist name
                        Text(
                            text = playbackState.currentMediaItem?.mediaMetadata?.artist?.toString()
                                ?: "",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Normal,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // Album name
                        Text(
                            text = playbackState.currentMediaItem?.mediaMetadata?.albumTitle?.toString()
                                ?: "",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Light,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Queue info
                        if (queue.isNotEmpty()) {
                            Text(
                                text = "${queue.size} tracks in queue",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 18.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Playback controls
                TvAudioPlayerControls(
                    isPlaying = playbackState.isPlaying,
                    shuffleEnabled = playbackState.shuffleEnabled,
                    repeatMode = playbackState.repeatMode,
                    currentPosition = currentPosition,
                    duration = duration,
                    onPlayPause = { viewModel.togglePlayPause() },
                    onSkipPrevious = { viewModel.skipToPrevious() },
                    onSkipNext = { viewModel.skipToNext() },
                    onSeek = { positionMs -> viewModel.seekTo(positionMs) },
                    onSeekForward = { viewModel.seekForward() },
                    onSeekBackward = { viewModel.seekBackward() },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onToggleRepeat = { viewModel.toggleRepeat() },
                    onShowQueue = { showQueue = !showQueue },
                    onBack = onBack,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Queue overlay
            AnimatedVisibility(
                visible = showQueue,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                TvAudioQueueDisplay(
                    queue = queue,
                    currentIndex = queue.indexOfFirst { it.mediaId == playbackState.currentMediaItem?.mediaId },
                    onDismiss = { showQueue = false },
                    onSelectTrack = { index ->
                        viewModel.skipToQueueItem(index)
                        showQueue = false
                    },
                    onRemoveTrack = { index ->
                        viewModel.removeFromQueue(index)
                    },
                    onClearQueue = {
                        viewModel.clearQueue()
                        showQueue = false
                    },
                )
            }
        }
    }

    // Handle back button
    DisposableEffect(Unit) {
        onDispose { }
    }
}

/**
 * Album art display with subtle animation when playing
 */
@Composable
private fun AlbumArtDisplay(
    artworkUri: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "album_art_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "album_art_scale",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUri != null) {
            AsyncImage(
                model = artworkUri,
                contentDescription = "Album art",
                modifier = Modifier
                    .size(480.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .then(
                        if (isPlaying) {
                            Modifier
                        } else {
                            Modifier
                        },
                    ),
                contentScale = ContentScale.Crop,
            )
        } else {
            // Placeholder when no artwork
            Box(
                modifier = Modifier
                    .size(480.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                androidx.tv.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.MusicNote,
                    contentDescription = "No artwork",
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                )
            }
        }
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
