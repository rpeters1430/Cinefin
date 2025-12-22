package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.utils.*
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Screen for managing offline content and displaying offline status.
 *
 * Shows downloaded content, storage usage, connectivity status,
 * and provides offline content management options.
 */
@Composable
fun OfflineScreen(
    offlineManager: OfflineManager,
    onPlayOfflineContent: (BaseItemDto) -> Unit = {},
    onDeleteOfflineContent: (BaseItemDto) -> Unit = {},
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isOnline by offlineManager.isOnline.collectAsStateWithLifecycle()
    val networkType by offlineManager.networkType.collectAsStateWithLifecycle()
    val offlineContent by offlineManager.offlineContent.collectAsStateWithLifecycle()

    var showClearDialog by remember { mutableStateOf(false) }
    var storageInfo by remember { mutableStateOf<OfflineStorageInfo?>(null) }

    // Load storage info
    LaunchedEffect(offlineContent) {
        storageInfo = offlineManager.getOfflineStorageUsage()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.Spacing16),
        verticalArrangement = Arrangement.spacedBy(Dimens.Spacing16),
    ) {
        // Header with back button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing8),
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            Text(
                text = "Offline Content",
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        // Connection status card
        ConnectivityStatusCard(
            isOnline = isOnline,
            networkType = networkType,
        )

        // Storage info card
        storageInfo?.let { info ->
            StorageInfoCard(
                storageInfo = info,
                onClearAll = { showClearDialog = true },
            )
        }

        // Offline content list
        OfflineContentSection(
            offlineContent = offlineContent,
            onPlayContent = onPlayOfflineContent,
            onDeleteContent = onDeleteOfflineContent,
        )
    }

    // Clear all dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Downloads") },
            text = {
                Text("This will delete all downloaded content and free up storage space. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        offlineManager.clearOfflineContent()
                        showClearDialog = false
                        storageInfo = offlineManager.getOfflineStorageUsage()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ConnectivityStatusCard(
    isOnline: Boolean,
    networkType: NetworkType,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOnline) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(Dimens.Spacing16),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
        ) {
            Icon(
                imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                contentDescription = null,
                tint = if (isOnline) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            )

            Column {
                Text(
                    text = if (isOnline) "Online" else "Offline",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isOnline) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                )

                Text(
                    text = when {
                        !isOnline -> "Only downloaded content is available"
                        networkType == NetworkType.WIFI -> "Connected via Wi-Fi"
                        networkType == NetworkType.CELLULAR -> "Connected via cellular data"
                        networkType == NetworkType.ETHERNET -> "Connected via Ethernet"
                        else -> "Connected"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOnline) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    },
                )
            }
        }
    }
}

@Composable
private fun StorageInfoCard(
    storageInfo: OfflineStorageInfo,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                    text = "Storage Usage",
                    style = MaterialTheme.typography.titleMedium,
                )

                if (storageInfo.itemCount > 0) {
                    TextButton(onClick = onClearAll) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(Dimens.Spacing4))
                        Text("Clear All")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${storageInfo.itemCount} items",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = storageInfo.formattedSize,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OfflineContentSection(
    offlineContent: List<BaseItemDto>,
    onPlayContent: (BaseItemDto) -> Unit,
    onDeleteContent: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.Spacing16),
        ) {
            Text(
                text = "Downloaded Content",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(Dimens.Spacing8))

            if (offlineContent.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.Spacing32),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimens.Spacing8),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text(
                            text = "No downloaded content",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        Text(
                            text = "Download content when online to watch offline",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Dimens.Spacing8),
                ) {
                    items(offlineContent, key = { it.id ?: it.name.hashCode() }) { item ->
                        OfflineContentItem(
                            item = item,
                            onPlay = { onPlayContent(item) },
                            onDelete = { onDeleteContent(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineContentItem(
    item: BaseItemDto,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(Dimens.Spacing12)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = when (item.type) {
                        BaseItemKind.MOVIE -> Icons.Default.Movie
                        BaseItemKind.EPISODE -> Icons.Default.Tv
                        BaseItemKind.AUDIO -> Icons.Default.AudioFile
                        else -> Icons.Default.FilePresent
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column {
                    Text(
                        text = item.name ?: stringResource(id = R.string.unknown),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    item.seriesName?.let { seriesName ->
                        if (item.type == BaseItemKind.EPISODE) {
                            Text(
                                text = seriesName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing4),
            ) {
                IconButton(onClick = onPlay) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
