package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.data.offline.DownloadStatus
import com.rpeters.jellyfin.data.offline.OfflineDownload
import com.rpeters.jellyfin.ui.components.ConnectedIndicatorBanner
import com.rpeters.jellyfin.ui.downloads.DownloadsViewModel
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun OfflineLibraryScreen(
    isReconnecting: Boolean,
    isOnline: Boolean,
    onReconnect: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    downloadsViewModel: DownloadsViewModel = hiltViewModel(),
) {
    val downloads by downloadsViewModel.downloads.collectAsStateWithLifecycle()
    val playable = downloads.filter { it.status == DownloadStatus.COMPLETED }
    var showConnectedBanner by rememberSaveable { mutableStateOf(false) }
    var wasOnline by remember { mutableStateOf(isOnline) }

    LaunchedEffect(isOnline) {
        if (!wasOnline && isOnline) {
            showConnectedBanner = true
            delay(3500)
            showConnectedBanner = false
        }
        wasOnline = isOnline
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConnectedIndicatorBanner(isVisible = showConnectedBanner)

        Text(
            text = "Offline Library",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "You're offline. Play downloaded media below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onReconnect,
                enabled = !isReconnecting,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isReconnecting) "Reconnecting..." else "Reconnect")
            }
            OutlinedButton(
                onClick = onBackToLogin,
                modifier = Modifier.weight(1f),
            ) {
                Text("Login Screen")
            }
        }

        if (playable.isEmpty()) {
            Text(
                text = "No downloaded media found on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(playable, key = { it.id }) { download ->
                OfflineDownloadRow(
                    download = download,
                    onPlay = { downloadsViewModel.playOfflineContent(download.jellyfinItemId) },
                    onDelete = { downloadsViewModel.deleteDownload(download.id) },
                )
            }
        }
    }
}

@Composable
private fun OfflineDownloadRow(
    download: OfflineDownload,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    val isEpisode = download.itemType.equals("EPISODE", ignoreCase = true)
    val runtimeMs = (download.runtimeTicks ?: 0L) / 10_000L
    val positionMs = download.lastPlaybackPositionMs ?: 0L
    val watchedRatio = if (runtimeMs > 0L) positionMs.toFloat() / runtimeMs.toFloat() else 0f
    val showProgress = watchedRatio > 0f && watchedRatio < 0.95f

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OfflinePoster(download = download, isEpisode = isEpisode)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = if (isEpisode) {
                        download.seriesName ?: "TV Show"
                    } else {
                        download.itemName
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isEpisode) {
                        buildString {
                            val season = download.seasonNumber?.let { "S${it.toString().padStart(2, '0')}" }
                            val episode = download.episodeNumber?.let { "E${it.toString().padStart(2, '0')}" }
                            if (season != null && episode != null) append("$season$episode • ")
                            append(download.itemName)
                        }
                    } else {
                        download.itemType.replaceFirstChar { it.uppercase() }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildString {
                    download.quality?.label?.let {
                        append(it)
                    }
                    download.productionYear?.let {
                        if (isNotEmpty()) append(" • ")
                        append(it)
                    }
                }
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (showProgress) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(watchedRatio * 100).toInt()}% watched",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = { watchedRatio.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlay) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Text("Play")
                }
                OutlinedButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun OfflinePoster(
    download: OfflineDownload,
    isEpisode: Boolean,
) {
    val localPath = download.thumbnailLocalPath
    val localFile = localPath?.let { File(it) }
    val model: Any? = when {
        localFile?.exists() == true -> localFile
        !download.thumbnailUrl.isNullOrBlank() -> download.thumbnailUrl
        else -> null
    }

    Box(
        modifier = Modifier
            .size(width = 72.dp, height = 108.dp)
            .aspectRatio(2f / 3f),
    ) {
        if (model != null) {
            JellyfinAsyncImage(
                model = model,
                contentDescription = download.itemName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Image(
                imageVector = if (isEpisode) Icons.Default.LiveTv else Icons.Default.LocalMovies,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}
