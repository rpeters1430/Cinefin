package com.rpeters.jellyfin.ui.downloads

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import com.rpeters.jellyfin.data.offline.VideoQuality
import com.rpeters.jellyfin.ui.theme.Dimens
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@androidx.media3.common.util.UnstableApi
@OptInAppExperimentalApis
@Composable
fun DownloadsScreen(
    onNavigateBack: () -> Unit,
    onOpenItemDetail: (OfflineDownload) -> Unit = {},
    downloadsViewModel: DownloadsViewModel = hiltViewModel(),
) {
    val downloads by downloadsViewModel.downloads.collectAsState()
    val downloadProgress by downloadsViewModel.downloadProgress.collectAsState()
    val storageInfo by downloadsViewModel.storageInfo.collectAsState()
    val downloadPreferences by downloadsViewModel.downloadPreferences.collectAsState()
    val pendingOfflineSyncCount by downloadsViewModel.pendingOfflineSyncCount.collectAsState()
    var showDeleteAllConfirmation by remember { mutableStateOf(false) }
    var showClearWatchedConfirmation by remember { mutableStateOf(false) }
    var redownloadTarget by remember { mutableStateOf<OfflineDownload?>(null) }

    if (showDeleteAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirmation = false },
            title = { Text("Delete all downloads?") },
            text = { Text("This removes all local offline copies from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllConfirmation = false
                        downloadsViewModel.deleteAllDownloads()
                    },
                ) {
                    Text("Delete all")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showClearWatchedConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearWatchedConfirmation = false },
            title = { Text("Clear watched downloads?") },
            text = { Text("Removes completed downloads watched at least 90%.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearWatchedConfirmation = false
                        downloadsViewModel.clearWatchedDownloads()
                    },
                ) {
                    Text("Clear watched")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearWatchedConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    redownloadTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { redownloadTarget = null },
            title = { Text("Redownload in different quality") },
            text = {
                Column {
                    DownloadsViewModel.QUALITY_PRESETS.forEach { quality ->
                        TextButton(
                            onClick = {
                                downloadsViewModel.redownloadDownload(target.id, quality)
                                redownloadTarget = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(quality.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { redownloadTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }

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
                IconButton(
                    onClick = { showClearWatchedConfirmation = true },
                    enabled = downloads.any { it.status == DownloadStatus.COMPLETED },
                ) {
                    Icon(Icons.Default.DoneAll, contentDescription = "Clear watched downloads")
                }
                IconButton(
                    onClick = { showDeleteAllConfirmation = true },
                    enabled = downloads.isNotEmpty(),
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Delete all downloads")
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

        DownloadPreferencesCard(
            wifiOnly = downloadPreferences.wifiOnly,
            defaultQualityId = downloadPreferences.defaultQualityId,
            autoCleanEnabled = downloadPreferences.autoCleanEnabled,
            autoCleanWatchedRetentionDays = downloadPreferences.autoCleanWatchedRetentionDays,
            autoCleanMinFreeSpaceGb = downloadPreferences.autoCleanMinFreeSpaceGb,
            pendingOfflineSyncCount = pendingOfflineSyncCount,
            qualities = DownloadsViewModel.QUALITY_PRESETS,
            onWifiOnlyChanged = downloadsViewModel::setWifiOnly,
            onDefaultQualitySelected = downloadsViewModel::setDefaultQuality,
            onAutoCleanEnabledChanged = downloadsViewModel::setAutoCleanEnabled,
            onAutoCleanWatchedRetentionDaysSelected = downloadsViewModel::setAutoCleanWatchedRetentionDays,
            onAutoCleanMinFreeSpaceGbSelected = downloadsViewModel::setAutoCleanMinFreeSpaceGb,
            onRunAutoCleanNow = downloadsViewModel::runAutoCleanNow,
            modifier = Modifier.padding(horizontal = Dimens.Spacing16),
        )

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
                        onRedownload = { redownloadTarget = download },
                        onOpenDetail = { onOpenItemDetail(download) },
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
    onRedownload: () -> Unit,
    onOpenDetail: () -> Unit,
    onPlay: () -> Unit,
) {
    val openDetailEnabled = download.status == DownloadStatus.COMPLETED
    val detailHint = detailAvailabilityHint(download.status)
    Card(
        modifier = Modifier.fillMaxWidth(),
        enabled = openDetailEnabled,
        onClick = {
            if (openDetailEnabled) {
                onOpenDetail()
            }
        },
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
                    Text(
                        text = buildString {
                            val sizeBytes = download.fileSize.takeIf { it > 0L } ?: download.downloadedBytes
                            append(formatBytes(sizeBytes))
                            val timestamp = download.downloadCompleteTime ?: download.downloadStartTime
                            if (timestamp != null) {
                                append(" • ")
                                append(formatTimestamp(timestamp))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DownloadStatusChip(download.status)
            }

            // Progress indicator for active downloads
            if (download.status == DownloadStatus.DOWNLOADING && progress != null) {
                DownloadProgressIndicator(progress, download.quality?.label)
            }
            detailHint?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                        IconButton(onClick = onRedownload) {
                            Icon(Icons.Default.CloudDownload, contentDescription = "Redownload")
                        }
                        IconButton(onClick = onOpenDetail) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open detail")
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
                if (!openDetailEnabled) {
                    IconButton(
                        onClick = {},
                        enabled = false,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open detail (unavailable)")
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadPreferencesCard(
    wifiOnly: Boolean,
    defaultQualityId: String,
    autoCleanEnabled: Boolean,
    autoCleanWatchedRetentionDays: Int,
    autoCleanMinFreeSpaceGb: Int,
    pendingOfflineSyncCount: Int,
    qualities: List<VideoQuality>,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onDefaultQualitySelected: (String) -> Unit,
    onAutoCleanEnabledChanged: (Boolean) -> Unit,
    onAutoCleanWatchedRetentionDaysSelected: (Int) -> Unit,
    onAutoCleanMinFreeSpaceGbSelected: (Int) -> Unit,
    onRunAutoCleanNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var qualityMenuExpanded by remember { mutableStateOf(false) }
    var retentionMenuExpanded by remember { mutableStateOf(false) }
    var minSpaceMenuExpanded by remember { mutableStateOf(false) }
    val selectedQuality = qualities.firstOrNull { it.id == defaultQualityId }
        ?: qualities.firstOrNull()
    val retentionOptions = listOf(7, 14, 30, 60)
    val minSpaceOptionsGb = listOf(2, 5, 10, 20)

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.Spacing16),
            verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
        ) {
            Text(
                text = "Download Preferences",
                style = MaterialTheme.typography.titleMedium,
            )
            if (pendingOfflineSyncCount > 0) {
                Text(
                    text = "Pending watch sync: $pendingOfflineSyncCount update(s). They will sync automatically when online.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Wi-Fi only")
                    Text(
                        text = "Allow downloads only on Wi-Fi/Ethernet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = wifiOnly,
                    onCheckedChange = onWifiOnlyChanged,
                )
            }

            ExposedDropdownMenuBox(
                expanded = qualityMenuExpanded,
                onExpandedChange = { qualityMenuExpanded = !qualityMenuExpanded },
            ) {
                OutlinedTextField(
                    value = selectedQuality?.label ?: "Original Quality",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Default quality") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qualityMenuExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = qualityMenuExpanded,
                    onDismissRequest = { qualityMenuExpanded = false },
                ) {
                    qualities.forEach { quality ->
                        DropdownMenuItem(
                            text = { Text(quality.label) },
                            onClick = {
                                onDefaultQualitySelected(quality.id)
                                qualityMenuExpanded = false
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-clean watched downloads")
                    Text(
                        text = "Automatically removes watched items based on retention and free-space target",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = autoCleanEnabled,
                    onCheckedChange = onAutoCleanEnabledChanged,
                )
            }

            ExposedDropdownMenuBox(
                expanded = retentionMenuExpanded,
                onExpandedChange = { retentionMenuExpanded = !retentionMenuExpanded },
            ) {
                OutlinedTextField(
                    value = "$autoCleanWatchedRetentionDays days",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Watched retention") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = retentionMenuExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    enabled = autoCleanEnabled,
                )
                ExposedDropdownMenu(
                    expanded = retentionMenuExpanded,
                    onDismissRequest = { retentionMenuExpanded = false },
                ) {
                    retentionOptions.forEach { days ->
                        DropdownMenuItem(
                            text = { Text("$days days") },
                            onClick = {
                                onAutoCleanWatchedRetentionDaysSelected(days)
                                retentionMenuExpanded = false
                            },
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = minSpaceMenuExpanded,
                onExpandedChange = { minSpaceMenuExpanded = !minSpaceMenuExpanded },
            ) {
                OutlinedTextField(
                    value = "$autoCleanMinFreeSpaceGb GB",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Min free space target") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = minSpaceMenuExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    enabled = autoCleanEnabled,
                )
                ExposedDropdownMenu(
                    expanded = minSpaceMenuExpanded,
                    onDismissRequest = { minSpaceMenuExpanded = false },
                ) {
                    minSpaceOptionsGb.forEach { gb ->
                        DropdownMenuItem(
                            text = { Text("$gb GB") },
                            onClick = {
                                onAutoCleanMinFreeSpaceGbSelected(gb)
                                minSpaceMenuExpanded = false
                            },
                        )
                    }
                }
            }

            TextButton(
                onClick = onRunAutoCleanNow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Run Auto-clean Now")
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
fun DownloadProgressIndicator(progress: DownloadProgress, qualityLabel: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Spacing4)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    progress.isTranscoding && progress.transcodingProgress != null ->
                        if (qualityLabel != null) "$qualityLabel · Transcoding: ${progress.transcodingProgress.roundToInt()}%"
                        else "Transcoding: ${progress.transcodingProgress.roundToInt()}%"
                    progress.isTranscoding ->
                        if (qualityLabel != null) "$qualityLabel · Transcoding..." else "Transcoding..."
                    else ->
                        if (qualityLabel != null) "$qualityLabel · ${progress.progressPercent.roundToInt()}%"
                        else "${progress.progressPercent.roundToInt()}%"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (progress.isTranscoding) {
                    progress.transcodingEtaMs?.let { "ETA ${formatDuration(it)}" } ?: "Preparing stream..."
                } else if (progress.totalBytes > 0L) {
                    "${formatBytes(progress.downloadedBytes)} / ~${formatBytes(progress.totalBytes)}"
                } else {
                    formatBytes(progress.downloadedBytes)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (progress.isTranscoding && progress.transcodingProgress != null) {
            LinearProgressIndicator(
                progress = { progress.transcodingProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (progress.isTranscoding || progress.totalBytes <= 0L) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (progress.totalBytes > 0L) {
            LinearProgressIndicator(
                progress = { progress.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "${formatBytes(progress.downloadSpeedBps)}/s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!progress.isTranscoding) {
                progress.remainingTimeMs?.let { remaining ->
                    Text(
                        formatDuration(remaining),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (progress.isTranscoding) {
                progress.transcodingEtaMs?.let { remaining ->
                    Text(
                        formatDuration(remaining),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

private fun formatTimestamp(timestampMs: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestampMs))
}

private fun detailAvailabilityHint(status: DownloadStatus): String? {
    return when (status) {
        DownloadStatus.COMPLETED -> null
        DownloadStatus.DOWNLOADING -> "Item details open when the download is completed."
        DownloadStatus.PAUSED -> "Resume download to open item details when complete."
        DownloadStatus.FAILED -> "Retry download to open item details when complete."
        DownloadStatus.CANCELLED -> "Restart download to open item details when complete."
        DownloadStatus.PENDING -> "Item details open when the download is completed."
    }
}
