package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.MediaItem
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.components.EmptyStateComposable
import com.rpeters.jellyfin.ui.components.EmptyStateType
import com.rpeters.jellyfin.ui.components.ExpressiveBackNavigationIcon
import com.rpeters.jellyfin.ui.components.ExpressiveBlurSurface
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBar
import com.rpeters.jellyfin.ui.components.ExpressiveWavyLinearProgress
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.theme.MusicGreen
import com.rpeters.jellyfin.ui.viewmodel.AudioPlaybackViewModel

/**
 * Screen displaying the current audio playback queue.
 * Features:
 * - List of queued tracks
 * - Current track highlighting
 * - Tap to skip to track
 * - Swipe to remove
 * - Clear all option
 */
@OptIn(ExperimentalMaterial3Api::class)
@OptInAppExperimentalApis
@Composable
fun AudioQueueScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: AudioPlaybackViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val queue by viewModel.queue.collectAsState()

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "Queue",
                navigationIcon = {
                    ExpressiveBackNavigationIcon(onClick = onNavigateBack)
                },
                actions = {
                    if (queue.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearQueue() },
                        ) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }
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
                                MusicGreen.copy(alpha = 0.22f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                androidx.compose.ui.graphics.Color.Transparent,
                            ),
                            radius = 1200f,
                        ),
                    ),
            )
            if (queue.isEmpty()) {
                EmptyStateComposable(
                    icon = Icons.Filled.MusicNote,
                    title = "Queue is empty",
                    description = "Add some music to get started",
                    type = EmptyStateType.Info,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        QueueOverviewCard(
                            currentMediaItem = playbackState.currentMediaItem,
                            queueSize = queue.size,
                        )
                    }
                    itemsIndexed(
                        items = queue,
                        key = { index, item -> "${item.mediaId}_$index" },
                    ) { index, item ->
                        val isCurrentTrack = playbackState.currentMediaItem?.mediaId == item.mediaId

                        QueueItem(
                            mediaItem = item,
                            index = index + 1,
                            isCurrentTrack = isCurrentTrack,
                            onItemClick = { viewModel.skipToQueueItem(index) },
                            onRemove = { viewModel.removeFromQueue(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueOverviewCard(
    currentMediaItem: MediaItem?,
    queueSize: Int,
) {
    ExpressiveBlurSurface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                RoundedCornerShape(28.dp),
            ),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = currentMediaItem?.mediaMetadata?.title?.toString() ?: "Up next",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QueueMetaChip("$queueSize tracks")
                currentMediaItem?.mediaMetadata?.artist?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { QueueMetaChip(it) }
                QueueMetaChip("Swipe to remove", accent = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun QueueMetaChip(
    label: String,
    accent: androidx.compose.ui.graphics.Color = MusicGreen,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.14f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@OptInAppExperimentalApis
@Composable
private fun QueueItem(
    mediaItem: MediaItem,
    index: Int,
    isCurrentTrack: Boolean,
    onItemClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color.Transparent,
                                MaterialTheme.colorScheme.errorContainer,
                            ),
                        ),
                        shape = RoundedCornerShape(24.dp),
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        enableDismissFromStartToEnd = false,
        modifier = modifier,
    ) {
        ExpressiveBlurSurface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (isCurrentTrack) {
                        MusicGreen.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    },
                    shape = RoundedCornerShape(24.dp),
                )
                .clickable(onClick = onItemClick),
            shape = RoundedCornerShape(24.dp),
            color = if (isCurrentTrack) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.76f)
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.width(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isCurrentTrack) {
                            FilledIconButton(
                                onClick = onItemClick,
                                modifier = Modifier.size(30.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MusicGreen,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "Now playing",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            Text(
                                text = "$index",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MusicGreen.copy(alpha = 0.22f),
                                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
                                    ),
                                ),
                                shape = CircleShape,
                            )
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        val artworkUri = mediaItem.mediaMetadata.artworkUri
                        if (artworkUri != null) {
                            JellyfinAsyncImage(
                                model = artworkUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mediaItem.mediaMetadata.title?.toString() ?: "Unknown",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isCurrentTrack) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isCurrentTrack) {
                                MusicGreen
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        val artist = mediaItem.mediaMetadata.artist?.toString()
                        if (!artist.isNullOrBlank()) {
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remove from queue",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                if (isCurrentTrack) {
                    ExpressiveWavyLinearProgress(
                        progress = 0.38f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = MusicGreen,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    )
                }
            }
        }
    }
}
