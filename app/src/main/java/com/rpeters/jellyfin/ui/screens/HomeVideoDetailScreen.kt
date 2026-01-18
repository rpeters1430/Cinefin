package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.components.PlaybackStatusBadge
import com.rpeters.jellyfin.ui.components.getQualityLabel
import com.rpeters.jellyfin.ui.utils.PlaybackCapabilityAnalysis
import com.rpeters.jellyfin.ui.utils.findDefaultAudioStream
import com.rpeters.jellyfin.ui.utils.findDefaultVideoStream
import com.rpeters.jellyfin.utils.getFormattedDuration
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.Locale

@OptInAppExperimentalApis
@Composable
fun HomeVideoDetailScreen(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    onBackClick: () -> Unit,
    onPlayClick: (BaseItemDto) -> Unit = {},
    onFavoriteClick: (BaseItemDto) -> Unit = {},
    onShareClick: (BaseItemDto) -> Unit = {},
    playbackAnalysis: PlaybackCapabilityAnalysis? = null,
    modifier: Modifier = Modifier,
) {
    var isFavorite by remember { mutableStateOf(item.userData?.isFavorite == true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.name ?: "Video") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onPlayClick(item) }) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                val backdrop = getBackdropUrl(item)
                if (backdrop != null) {
                    SubcomposeAsyncImage(
                        model = backdrop,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val poster = getImageUrl(item)
                    if (poster != null) {
                        SubcomposeAsyncImage(
                            model = poster,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(120.dp)
                                .height(180.dp),
                        )
                    }
                    Column(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(item.name ?: "", style = MaterialTheme.typography.titleLarge)
                        item.productionYear?.let {
                            Text(it.toString(), style = MaterialTheme.typography.bodyMedium)
                        }
                        item.runTimeTicks?.let { ticks ->
                            val minutes = ticks / LibraryScreenDefaults.TicksToMinutesDivisor
                            Text("$minutes min", style = MaterialTheme.typography.bodyMedium)
                        }
                        playbackAnalysis?.let { analysis ->
                            PlaybackStatusBadge(analysis = analysis)
                        }
                    }
                }
            }
            item {
                HomeVideoTechnicalDetails(
                    item = item,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    IconButton(onClick = {
                        isFavorite = !isFavorite
                        onFavoriteClick(item)
                    }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        )
                    }
                    IconButton(onClick = { onShareClick(item) }) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeVideoTechnicalDetails(
    item: BaseItemDto,
    modifier: Modifier = Modifier,
) {
    val mediaSource = item.mediaSources?.firstOrNull()
    val videoStream = mediaSource?.mediaStreams?.findDefaultVideoStream()
    val audioStream = mediaSource?.mediaStreams?.findDefaultAudioStream()
    val qualityLabel = getQualityLabel(item)

    val videoDetails = buildList {
        val width = videoStream?.width
        val height = videoStream?.height
        if (width != null && height != null) {
            add("${width}x$height")
        }
        videoStream?.codec?.let { add(it.uppercase()) }
        videoStream?.averageFrameRate?.let { frameRate ->
            add(String.format(Locale.US, "%.2f fps", frameRate))
        }
    }.joinToString(" • ")

    val audioDetails = buildList {
        audioStream?.codec?.let { add(it.uppercase()) }
        audioStream?.channels?.let { add("${it}ch") }
        audioStream?.bitRate?.let { add("${it / 1000} kbps") }
    }.joinToString(" • ")

    val runtime = item.getFormattedDuration()
    val sizeLabel = mediaSource?.size?.let { formatFileSize(it) }
    val container = mediaSource?.container?.uppercase()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Video Details",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            runtime?.let { DetailRow(label = "Length", value = it) }

            qualityLabel?.let { (label, color) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Quality",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        color = color,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (videoDetails.isNotBlank()) {
                DetailRow(label = "Video", value = videoDetails)
            }
            if (audioDetails.isNotBlank()) {
                DetailRow(label = "Audio", value = audioDetails)
            }
            container?.let { DetailRow(label = "Container", value = it) }
            sizeLabel?.let { DetailRow(label = "File Size", value = it) }
            videoStream?.bitRate?.let { DetailRow(label = "Video Bitrate", value = "${it / 1000} kbps") }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0

    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }

    return String.format(Locale.US, "%.1f %s", size, units[unitIndex])
}
