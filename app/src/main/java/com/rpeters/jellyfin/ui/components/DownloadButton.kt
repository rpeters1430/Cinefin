package com.rpeters.jellyfin.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rpeters.jellyfin.data.offline.DownloadStatus
import com.rpeters.jellyfin.ui.downloads.DownloadsViewModel
import org.jellyfin.sdk.model.api.BaseItemDto
import kotlin.math.roundToInt

@androidx.media3.common.util.UnstableApi
@Composable
fun DownloadButton(
    item: BaseItemDto,
    modifier: Modifier = Modifier,
    downloadsViewModel: DownloadsViewModel = hiltViewModel(),
    showText: Boolean = false,
) {
    val downloads by downloadsViewModel.downloads.collectAsState()
    val downloadProgress by downloadsViewModel.downloadProgress.collectAsState()

    val currentDownload = downloads.find { it.jellyfinItemId == item.id.toString() }
    val progress = currentDownload?.let { downloadProgress[it.id] }

    Box(modifier = modifier) {
        when (currentDownload?.status) {
            DownloadStatus.DOWNLOADING -> {
                DownloadingButton(
                    progress = progress?.progressPercent ?: 0f,
                    onPause = { downloadsViewModel.pauseDownload(currentDownload.id) },
                    showText = showText,
                )
            }
            DownloadStatus.PAUSED -> {
                PausedDownloadButton(
                    onResume = { downloadsViewModel.resumeDownload(currentDownload.id) },
                    onCancel = { downloadsViewModel.cancelDownload(currentDownload.id) },
                    showText = showText,
                )
            }
            DownloadStatus.COMPLETED -> {
                CompletedDownloadButton(
                    onPlay = { downloadsViewModel.playOfflineContent(item.id.toString()) },
                    onDelete = { downloadsViewModel.deleteDownload(currentDownload.id) },
                    showText = showText,
                )
            }
            DownloadStatus.FAILED -> {
                FailedDownloadButton(
                    onRetry = { downloadsViewModel.resumeDownload(currentDownload.id) },
                    onDelete = { downloadsViewModel.deleteDownload(currentDownload.id) },
                    showText = showText,
                )
            }
            else -> {
                // No download in progress or pending
                StartDownloadButton(
                    onDownload = { downloadsViewModel.startDownload(item) },
                    showText = showText,
                )
            }
        }
    }
}

@Composable
private fun StartDownloadButton(
    onDownload: () -> Unit,
    showText: Boolean,
) {
    if (showText) {
        OutlinedButton(
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Default.CloudDownload,
                contentDescription = "Download",
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Download")
        }
    } else {
        IconButton(onClick = onDownload) {
            Icon(
                Icons.Default.CloudDownload,
                contentDescription = "Download",
            )
        }
    }
}

@Composable
private fun DownloadingButton(
    progress: Float,
    onPause: () -> Unit,
    showText: Boolean,
) {
    if (showText) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Downloading... ${progress.roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                IconButton(
                    onClick = onPause,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Pause,
                        contentDescription = "Pause",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.size(40.dp),
                strokeWidth = 3.dp,
            )
            IconButton(
                onClick = onPause,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Pause,
                    contentDescription = "Pause",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun PausedDownloadButton(
    onResume: () -> Unit,
    onCancel: () -> Unit,
    showText: Boolean,
) {
    if (showText) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onResume,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Resume",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Resume")
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.Cancel,
                    contentDescription = "Cancel",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Cancel")
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onResume) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Cancel, contentDescription = "Cancel")
            }
        }
    }
}

@Composable
private fun CompletedDownloadButton(
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    showText: Boolean,
) {
    if (showText) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onPlay,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Play Offline")
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete")
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .clip(CircleShape)
                    .size(40.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun FailedDownloadButton(
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    showText: Boolean,
) {
    if (showText) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Retry",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Retry")
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete")
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = "Retry")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
