package com.rpeters.jellyfin.ui.downloads

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.data.offline.DownloadProgress
import com.rpeters.jellyfin.data.offline.DownloadStatus
import com.rpeters.jellyfin.data.offline.OfflineDownload
import com.rpeters.jellyfin.ui.theme.Dimens
import kotlin.math.roundToInt

@androidx.media3.common.util.UnstableApi
@OptInAppExperimentalApis
@Composable
fun DownloadsScreen(
    onNavigateBack: () -> Unit,
    downloadsViewModel: DownloadsViewModel = hiltViewModel(),
) {
    val downloads by downloadsViewModel.downloads.collectAsState()
    val downloadProgress by downloadsViewModel.downloadProgress.collectAsState()
    val storageInfo by downloadsViewModel.storageInfo.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Downloads") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { downloadsViewModel.clearCompletedDownloads() }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear completed")
                }
                IconButton(onClick = { downloadsViewModel.pauseAllDownloads() }) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause all")
                }
            },
        )

        // Storage info card
        storageInfo?.let { info ->
            StorageInfoCard(
                storageInfo = info,
                modifier = Modifier.padding(Dimens.Spacing16),
            )
        }

        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.Spacing16),
                ) {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "No downloads yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Downloaded content will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Dimens.Spacing16),
                verticalArrangement = Arrangement.spacedBy(Dimens.Spacing8),
            ) {
                items(
                    downloads,
                    key = { it.id },
                    contentType = { "download_item" },
                ) { download ->
                    DownloadItem(
                        download = download,
                        progress = downloadProgress[download.id],
                        onPause = { downloadsViewModel.pauseDownload(download.id) },
                        onResume = { downloadsViewModel.resumeDownload(download.id) },
                        onCancel = { downloadsViewModel.cancelDownload(download.id) },
                        onDelete = { downloadsViewModel.deleteDownload(download.id) },
                        onPlay = { downloadsViewModel.playOfflineContent(download.jellyfinItemId) },
                    )
                }
            }
        }
    }
}

@Composable
fun StorageInfoCard(
    storageInfo: com.rpeters.jellyfin.data.offline.OfflineStorageInfo,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.Spacing16),
            verticalArrangement = Arrangement.spacedBy(Dimens.Spacing8),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Storage Usage",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${storageInfo.downloadCount} downloads",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LinearProgressIndicator(
                progress = { storageInfo.usedSpacePercentage / 100f },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    formatBytes(storageInfo.usedSpaceBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatBytes(storageInfo.totalSpaceBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun DownloadItem(
    download: OfflineDownload,
    progress: DownloadProgress?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.Spacing16),
            verticalArrangement = Arrangement.spacedBy(Dimens.Spacing8),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        download.itemName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        download.quality?.label ?: "Original Quality",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DownloadStatusChip(download.status)
            }

            // Progress indicator for active downloads
            if (download.status == DownloadStatus.DOWNLOADING && progress != null) {
                DownloadProgressIndicator(progress)
            }

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (download.status) {
                    DownloadStatus.DOWNLOADING -> {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause")
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Cancel, contentDescription = "Cancel")
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Cancel, contentDescription = "Cancel")
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        IconButton(onClick = onPlay) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    DownloadStatus.FAILED -> {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    else -> {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Cancel, contentDescription = "Cancel")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadStatusChip(status: DownloadStatus) {
    val (color, text) = when (status) {
        DownloadStatus.PENDING -> MaterialTheme.colorScheme.secondary to "Pending"
        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary to "Downloading"
        DownloadStatus.PAUSED -> MaterialTheme.colorScheme.outline to "Paused"
        DownloadStatus.COMPLETED -> Color(0xFF4CAF50) to "Completed"
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error to "Failed"
        DownloadStatus.CANCELLED -> MaterialTheme.colorScheme.outline to "Cancelled"
    }

    Surface(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)),
        color = color.copy(alpha = 0.1f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = Dimens.Spacing8, vertical = Dimens.Spacing4),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
fun DownloadProgressIndicator(progress: DownloadProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Spacing4)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${progress.progressPercent.roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${formatBytes(progress.downloadedBytes)} / ${formatBytes(progress.totalBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LinearProgressIndicator(
            progress = { progress.progressPercent / 100f },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "${formatBytes(progress.downloadSpeedBps)}/s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            progress.remainingTimeMs?.let { remaining ->
                Text(
                    formatDuration(remaining),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0

    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }

    return "%.1f %s".format(size, units[unitIndex])
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}
