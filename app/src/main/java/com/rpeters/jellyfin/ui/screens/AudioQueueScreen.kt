package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.MediaItem
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
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
            TopAppBar(
                title = {
                    Column {
                        Text("Queue")
                        Text(
                            text = "${queue.size} tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        if (queue.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        text = "Queue is empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Add some music to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
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

                // Bottom padding
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

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
                    .background(MaterialTheme.colorScheme.errorContainer)
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
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onItemClick),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isCurrentTrack) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Track number or playing indicator
                Box(
                    modifier = Modifier.width(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isCurrentTrack) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Now playing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Text(
                            text = "$index",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Album art
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp))
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

                // Track info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mediaItem.mediaMetadata.title?.toString() ?: "Unknown",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isCurrentTrack) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isCurrentTrack) {
                            MaterialTheme.colorScheme.primary
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

                // Remove button
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
        }
    }
}
