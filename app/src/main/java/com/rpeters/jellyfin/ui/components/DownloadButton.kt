package com.rpeters.jellyfin.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.data.offline.DownloadStatus
import com.rpeters.jellyfin.data.offline.OfflineDownload
import com.rpeters.jellyfin.ui.downloads.DownloadsViewModel
import com.rpeters.jellyfin.utils.SecureLogger
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStreamType
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
    val context = LocalContext.current
    var showQualityDialog by remember { mutableStateOf(false) }
    var redownloadMode by remember { mutableStateOf(false) }
    var pendingQuality by remember { mutableStateOf<com.rpeters.jellyfin.data.offline.VideoQuality?>(null) }

    // Deferred permission launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        SecureLogger.i(
            "DownloadsFlow",
            "POST_NOTIFICATIONS result via DownloadButton: granted=$granted, itemId=${item.id}",
        )
        // After permission choice, proceed with the pending download if any
        val quality = pendingQuality
        if (quality != null) {
            if (redownloadMode) {
                downloadsViewModel.redownloadByItem(item, quality)
            } else {
                downloadsViewModel.startDownload(item, quality)
            }
            pendingQuality = null
            redownloadMode = false
        }
    }

    fun requestPermissionAndStartDownload(quality: com.rpeters.jellyfin.data.offline.VideoQuality) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            SecureLogger.i(
                "DownloadsFlow",
                "Requesting POST_NOTIFICATIONS via DownloadButton for itemId=${item.id}, quality=${quality.id}",
            )
            pendingQuality = quality
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            SecureLogger.i(
                "DownloadsFlow",
                "POST_NOTIFICATIONS already granted (or not required) via DownloadButton for itemId=${item.id}, quality=${quality.id}",
            )
            if (redownloadMode) {
                downloadsViewModel.redownloadByItem(item, quality)
            } else {
                downloadsViewModel.startDownload(item, quality)
            }
            redownloadMode = false
        }
    }

    val currentDownload = remember(downloads, item.id) {
        selectPreferredDownloadForItem(downloads, item.id.toString())
    }
    val progress = currentDownload?.let { downloadProgress[it.id] }

    if (showQualityDialog) {
        QualitySelectionDialog(
            item = item,
            onDismiss = {
                showQualityDialog = false
                redownloadMode = false
            },
            onQualitySelected = { quality ->
                showQualityDialog = false
                requestPermissionAndStartDownload(quality)
            },
            downloadsViewModel = downloadsViewModel,
        )
    }

    Box(modifier = modifier) {
        when (currentDownload?.status) {
            DownloadStatus.PENDING -> {
                PendingDownloadButton(showText = showText)
            }
            DownloadStatus.DOWNLOADING -> {
                DownloadingButton(
                    progress = progress?.progressPercent ?: 0f,
                    isTranscoding = progress?.isTranscoding == true,
                    transcodingProgress = progress?.transcodingProgress,
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
                    onRedownload = {
                        redownloadMode = true
                        showQualityDialog = true
                    },
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
                    onDownload = {
                        redownloadMode = false
                        showQualityDialog = true
                    },
                    showText = showText,
                )
            }
        }
    }
}

private fun selectPreferredDownloadForItem(
    downloads: List<OfflineDownload>,
    itemId: String,
): OfflineDownload? {
    fun statusPriority(status: DownloadStatus): Int {
        return when (status) {
            DownloadStatus.DOWNLOADING -> 6
            DownloadStatus.PENDING -> 5
            DownloadStatus.PAUSED -> 4
            DownloadStatus.FAILED -> 3
            DownloadStatus.COMPLETED -> 2
            DownloadStatus.CANCELLED -> 1
        }
    }

    return downloads
        .asSequence()
        .filter { it.jellyfinItemId == itemId }
        .maxWithOrNull(
            compareBy<OfflineDownload>(
                { statusPriority(it.status) },
                { it.downloadStartTime ?: 0L },
                { it.downloadCompleteTime ?: 0L },
            ),
        )
}

@Composable
private fun PendingDownloadButton(showText: Boolean) {
    if (showText) {
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Queued...")
        }
    } else {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
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
    isTranscoding: Boolean = false,
    transcodingProgress: Float? = null,
    onPause: () -> Unit,
    showText: Boolean,
) {
    val displayText = when {
        isTranscoding && transcodingProgress != null ->
            "Transcoding... ${transcodingProgress.roundToInt()}%"
        isTranscoding ->
            "Transcoding..."
        else ->
            "Downloading... ${progress.roundToInt()}%"
    }
    val displayProgress = when {
        isTranscoding && transcodingProgress != null -> transcodingProgress / 100f
        isTranscoding -> 0f
        else -> progress / 100f
    }

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
                    displayText,
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
                progress = { displayProgress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { displayProgress },
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
    onRedownload: () -> Unit,
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
                onClick = onRedownload,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = "Redownload",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Redownload")
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
            IconButton(onClick = onRedownload) {
                Icon(Icons.Default.CloudDownload, contentDescription = "Redownload")
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
