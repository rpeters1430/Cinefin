package com.rpeters.jellyfin.ui.downloads

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.offline.DownloadProgress
import com.rpeters.jellyfin.data.offline.DownloadStatus
import com.rpeters.jellyfin.data.offline.OfflineDownload
import com.rpeters.jellyfin.data.offline.VideoQuality
import com.rpeters.jellyfin.ui.components.ExpressiveSwitchListItem
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
            onDismissRequest = { },
            title = { Text("Delete all downloads?") },
            text = { Text("This removes all local offline copies from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadsViewModel.deleteAllDownloads()
                    },
                ) {
                    Text("Delete all")
                }
            },
            dismissButton = {
                TextButton(onClick = { }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showClearWatchedConfirmation) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Clear watched downloads?") },
            text = { Text("Removes completed downloads watched at least 90%.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadsViewModel.clearWatchedDownloads()
                    },
                ) {
                    Text("Clear watched")
                }
            },
            dismissButton = {
                TextButton(onClick = { }) {
                    Text("Cancel")
                }
            },
        )
    }

    redownloadTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Redownload in different quality") },
            text = {
                Column {
                    DownloadsViewModel.QUALITY_PRESETS.forEach { quality ->
                        TextButton(
                            onClick = {
                                downloadsViewModel.redownloadDownload(target.id, quality)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(quality.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { downloadsViewModel.clearCompletedDownloads() }) {
                        Icon(Icons.Default.ClearAll, contentDescription = "Clear completed")
                    }
                    IconButton(onClick = { downloadsViewModel.pauseAllDownloads() }) {
                        Icon(Icons.Default.PauseCircle, contentDescription = "Pause all")
                    }
                    IconButton(
                        onClick = { },
                        enabled = downloads.any { it.status == DownloadStatus.COMPLETED },
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = "Clear watched downloads")
                    }
                    IconButton(
                        onClick = { },
                        enabled = downloads.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Delete all downloads")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Storage Usage Section
            storageInfo?.let { info ->
                item(key = "storage_info") {
                    ExpressiveStorageCard(storageInfo = info)
                }
            }

            // Preferences Section
            item(key = "download_prefs") {
                ExpressiveDownloadPreferencesCard(
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
                )
            }

            // Downloads List Header
            if (downloads.isNotEmpty()) {
                item {
                    Text(
                        text = "Active & Completed Downloads",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            items(
                downloads,
                key = { it.id },
                contentType = { "download_item" },
            ) { download ->
                ExpressiveDownloadItem(
                    download = download,
                    progress = downloadProgress[download.id],
                    onPause = { downloadsViewModel.pauseDownload(download.id) },
                    onResume = { downloadsViewModel.resumeDownload(download.id) },
                    onCancel = { downloadsViewModel.cancelDownload(download.id) },
                    onDelete = { downloadsViewModel.deleteDownload(download.id) },
                    onRedownload = { },
                    onOpenDetail = { onOpenItemDetail(download) },
                    onPlay = { downloadsViewModel.playOfflineContent(download.jellyfinItemId) },
                )
            }

            if (downloads.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            )
                            Text(
                                "No active downloads",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ExpressiveStorageCard(
    storageInfo: com.rpeters.jellyfin.data.offline.OfflineStorageInfo,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text(
                        "Storage Usage",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    )
                    Text(
                        "${storageInfo.downloadCount} items downloaded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { storageInfo.usedSpacePercentage / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Used: ${formatBytes(storageInfo.usedSpaceBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Total: ${formatBytes(storageInfo.totalSpaceBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressiveDownloadItem(
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
    val isCompleted = download.status == DownloadStatus.COMPLETED
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) MaterialTheme.colorScheme.surfaceContainerLow 
                            else MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted) 0.dp else 2.dp),
        border = if (isCompleted) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        download.itemName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                download.quality?.label ?: "Original",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = formatBytes(download.fileSize.takeIf { it > 0L } ?: download.downloadedBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                DownloadStatusChipEnhanced(download.status)
            }

            if (download.status == DownloadStatus.DOWNLOADING && progress != null) {
                DownloadProgressIndicatorEnhanced(progress)
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (download.status) {
                    DownloadStatus.DOWNLOADING -> {
                        ActionIconButton(Icons.Default.Pause, "Pause", onPause)
                        ActionIconButton(Icons.Default.Close, "Cancel", onCancel)
                    }
                    DownloadStatus.PAUSED -> {
                        ActionIconButton(Icons.Default.PlayArrow, "Resume", onResume)
                        ActionIconButton(Icons.Default.Close, "Cancel", onCancel)
                    }
                    DownloadStatus.COMPLETED -> {
                        ActionIconButton(Icons.Default.PlayArrow, "Play", onPlay, containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ActionIconButton(Icons.Default.Info, "Details", onOpenDetail)
                        ActionIconButton(Icons.Default.Delete, "Delete", onDelete, contentColor = MaterialTheme.colorScheme.error)
                    }
                    DownloadStatus.FAILED -> {
                        ActionIconButton(Icons.Default.Refresh, "Retry", onResume)
                        ActionIconButton(Icons.Default.Delete, "Delete", onDelete, contentColor = MaterialTheme.colorScheme.error)
                    }
                    else -> {
                        ActionIconButton(Icons.Default.Close, "Cancel", onCancel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    containerColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = Modifier.size(40.dp)
    ) {
        Icon(icon, contentDescription, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ExpressiveDownloadPreferencesCard(
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
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    "Download Settings",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                )
            }

            if (pendingOfflineSyncCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Sync, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                        Text(
                            "Pending sync: $pendingOfflineSyncCount updates",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            ExpressiveSwitchListItem(
                title = "Wi-Fi Only",
                subtitle = "Only download over Wi-Fi networks",
                checked = wifiOnly,
                onCheckedChange = onWifiOnlyChanged,
                leadingIcon = Icons.Default.Wifi
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            QualitySelector(
                currentQualityId = defaultQualityId,
                qualities = qualities,
                onQualitySelected = onDefaultQualitySelected
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            ExpressiveSwitchListItem(
                title = "Auto-clean",
                subtitle = "Remove watched items automatically",
                checked = autoCleanEnabled,
                onCheckedChange = onAutoCleanEnabledChanged,
                leadingIcon = Icons.Default.AutoDelete
            )

            AnimatedVisibility(visible = autoCleanEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Keep watched for $autoCleanWatchedRetentionDays days",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    // Simplified row for retention
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 14, 30).forEach { days ->
                            FilterChip(
                                selected = autoCleanWatchedRetentionDays == days,
                                onClick = { onAutoCleanWatchedRetentionDaysSelected(days) },
                                label = { Text("$days d") }
                            )
                        }
                    }
                    
                    Button(
                        onClick = onRunAutoCleanNow,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.CleaningServices, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Clean Up Now")
                    }
                }
            }
        }
    }
}

@Composable
private fun QualitySelector(
    currentQualityId: String,
    qualities: List<VideoQuality>,
    onQualitySelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Default Quality",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(qualities) { quality ->
                val isSelected = quality.id == currentQualityId
                FilterChip(
                    selected = isSelected,
                    onClick = { onQualitySelected(quality.id) },
                    label = { Text(quality.label) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
    }
}

@Composable
fun DownloadStatusChipEnhanced(status: DownloadStatus) {
    val color = when (status) {
        DownloadStatus.PENDING -> MaterialTheme.colorScheme.secondary
        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
        DownloadStatus.PAUSED -> MaterialTheme.colorScheme.outline
        DownloadStatus.COMPLETED -> Color(0xFF4CAF50)
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
        DownloadStatus.CANCELLED -> MaterialTheme.colorScheme.outline
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = CircleShape,
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Text(
            text = status.name.lowercase().replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
    }
}

@Composable
fun DownloadProgressIndicatorEnhanced(progress: DownloadProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { if (progress.isTranscoding) (progress.transcodingProgress ?: 0f) / 100f else progress.progressPercent / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (progress.isTranscoding) "Transcoding..." else "${formatBytes(progress.downloadSpeedBps)}/s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${progress.progressPercent.roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
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
