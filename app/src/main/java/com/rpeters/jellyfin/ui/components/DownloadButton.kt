@file:OptInAppExperimentalApis

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.offline.DownloadProgress
import com.rpeters.jellyfin.data.offline.DownloadStatus
import com.rpeters.jellyfin.ui.downloads.DownloadsViewModel
import com.rpeters.jellyfin.ui.components.ExpressiveWavyCircularLoading
import com.rpeters.jellyfin.ui.components.ExpressiveWavyCircularProgress
import com.rpeters.jellyfin.ui.components.ExpressiveWavyLinearProgress
import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.coroutines.flow.flowOf
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
    val context = LocalContext.current
    var showQualityDialog by remember { mutableStateOf(false) }
    var redownloadMode by remember { mutableStateOf(false) }
    var pendingQuality by remember { mutableStateOf<com.rpeters.jellyfin.data.offline.VideoQuality?>(null) }
    val itemId = item.id.toString()
    val currentDownloadFlow = remember(downloadsViewModel, itemId) {
        downloadsViewModel.observeCurrentDownload(itemId)
    }
    val currentDownload by currentDownloadFlow.collectAsStateWithLifecycle(initialValue = null)
    val progressFlow = remember(downloadsViewModel, currentDownload?.id) {
        currentDownload?.id?.let(downloadsViewModel::observeDownloadProgress) ?: flowOf<DownloadProgress?>(null)
    }
    val progress by progressFlow.collectAsStateWithLifecycle(initialValue = null)

    // Deferred permission launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
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

    val activeDownload = currentDownload

    Box(modifier = modifier) {
        when (activeDownload?.status) {
            DownloadStatus.PENDING -> {
                PendingDownloadButton(showText = showText)
            }
            DownloadStatus.DOWNLOADING -> {
                DownloadingButton(
                    progress = progress?.progressPercent ?: 0f,
                    isTranscoding = progress?.isTranscoding == true,
                    transcodingProgress = progress?.transcodingProgress,
                    onPause = { downloadsViewModel.pauseDownload(activeDownload.id) },
                    showText = showText,
                )
            }
            DownloadStatus.PAUSED -> {
                PausedDownloadButton(
                    onResume = { downloadsViewModel.resumeDownload(activeDownload.id) },
                    onCancel = { downloadsViewModel.cancelDownload(activeDownload.id) },
                    showText = showText,
                )
            }
            DownloadStatus.COMPLETED -> {
                CompletedDownloadButton(
                    onPlay = { downloadsViewModel.playOfflineContent(itemId) },
                    onRedownload = {
                        redownloadMode = true
                        showQualityDialog = true
                    },
                    onDelete = { downloadsViewModel.deleteDownload(activeDownload.id) },
                    showText = showText,
                )
            }
            DownloadStatus.FAILED -> {
                FailedDownloadButton(
                    onRetry = { downloadsViewModel.resumeDownload(activeDownload.id) },
                    onDelete = { downloadsViewModel.deleteDownload(activeDownload.id) },
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

@Composable
private fun PendingDownloadButton(showText: Boolean) {
    if (showText) {
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            ExpressiveWavyCircularLoading(modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(id = R.string.download_queued))
        }
    } else {
        Box(contentAlignment = Alignment.Center) {
            ExpressiveWavyCircularLoading(modifier = Modifier.size(40.dp))
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
            Text(stringResource(id = R.string.download))
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
            ExpressiveWavyLinearProgress(
                progress = displayProgress,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        Box(contentAlignment = Alignment.Center) {
            ExpressiveWavyCircularProgress(
                progress = displayProgress,
                modifier = Modifier.size(40.dp),
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
                Text(stringResource(id = R.string.resume))
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
                Text(stringResource(id = R.string.cancel))
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
                Text(stringResource(id = R.string.play_offline))
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
                Text(stringResource(id = R.string.redownload))
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
                Text(stringResource(id = R.string.delete))
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
                Text(stringResource(id = R.string.retry))
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
                Text(stringResource(id = R.string.delete))
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
