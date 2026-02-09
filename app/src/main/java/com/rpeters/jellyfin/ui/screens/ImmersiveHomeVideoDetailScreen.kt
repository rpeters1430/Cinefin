package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.core.util.PerformanceMetricsTracker
import com.rpeters.jellyfin.ui.components.PlaybackStatusBadge
import com.rpeters.jellyfin.ui.components.getQualityLabel
import com.rpeters.jellyfin.ui.components.immersive.StaticHeroSection
import com.rpeters.jellyfin.ui.components.immersive.rememberImmersivePerformanceConfig
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.ui.utils.PlaybackCapabilityAnalysis
import com.rpeters.jellyfin.ui.utils.findDefaultAudioStream
import com.rpeters.jellyfin.ui.utils.findDefaultVideoStream
import com.rpeters.jellyfin.utils.getFormattedDuration
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.Locale

/**
 * Immersive home video detail screen with Netflix/Disney+ inspired design.
 * Features:
 * - Full-bleed parallax backdrop (480dp height)
 * - Title and metadata overlaid on gradient
 * - Cinematic technical details presentation
 * - Large action buttons in grid layout
 * - Floating back button
 * - Material 3 animations
 */
@OptInAppExperimentalApis
@Composable
fun ImmersiveHomeVideoDetailScreen(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    onBackClick: () -> Unit,
    onPlayClick: (BaseItemDto) -> Unit = {},
    onFavoriteClick: (BaseItemDto) -> Unit = {},
    onShareClick: (BaseItemDto) -> Unit = {},
    onDownloadClick: (BaseItemDto) -> Unit = {},
    onDeleteClick: (BaseItemDto) -> Unit = {},
    onMarkWatchedClick: (BaseItemDto) -> Unit = {},
    onRefresh: () -> Unit = {},
    playbackAnalysis: PlaybackCapabilityAnalysis? = null,
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val perfConfig = rememberImmersivePerformanceConfig()
    var isFavorite by remember { mutableStateOf(item.userData?.isFavorite == true) }
    var isWatched by remember { mutableStateOf(item.userData?.played == true) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val listState = rememberLazyListState()

    PerformanceMetricsTracker(
        enabled = com.rpeters.jellyfin.BuildConfig.DEBUG,
        intervalMs = 30000,
    )

    Box(modifier = modifier.fillMaxSize()) {
        // ✅ Static Hero Background (Fixed)
        StaticHeroSection(
            imageUrl = getBackdropUrl(item),
            height = ImmersiveDimens.HeroHeightPhone,
            contentScale = ContentScale.Crop,
            content = {}, // Content moved to LazyColumn
        )

        // ✅ Scrollable Content Layer
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = 16.dp,
                ),
            ) {
                // Home Video Hero Content (Title, Metadata) - Now scrolls
                item(key = "hero_content") {
                    HomeVideoHeroContent(
                        item = item,
                        playbackAnalysis = playbackAnalysis,
                    )
                }

                // ✅ Solid background spacer to cover hero when scrolled
                item(key = "background_spacer") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.background),
                    )
                }

                // Large Play Button
                item(key = "play_button", contentType = "action") {
                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                        Button(
                            onClick = { onPlayClick(item) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ImmersiveDimens.SpacingContentPadding)
                                .padding(top = 24.dp)
                                .height(56.dp),
                            shape = RoundedCornerShape(ImmersiveDimens.CornerRadiusCinematic),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Play",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // Action Buttons Grid
                item(key = "actions", contentType = "actions") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = ImmersiveDimens.SpacingContentPadding)
                            .padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Row 1: Favorite and Mark Watched
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ImmersiveActionButton(
                                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                label = if (isFavorite) "Favorited" else "Favorite",
                                onClick = {
                                    isFavorite = !isFavorite
                                    onFavoriteClick(item)
                                },
                                modifier = Modifier.weight(1f),
                            )
                            ImmersiveActionButton(
                                icon = if (isWatched) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                label = if (isWatched) "Watched" else "Mark Watched",
                                onClick = {
                                    isWatched = !isWatched
                                    onMarkWatchedClick(item)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // Row 2: Download and Share
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ImmersiveActionButton(
                                icon = Icons.Default.Download,
                                label = "Download",
                                onClick = { onDownloadClick(item) },
                                modifier = Modifier.weight(1f),
                            )
                            ImmersiveActionButton(
                                icon = Icons.Default.Share,
                                label = "Share",
                                onClick = { onShareClick(item) },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // Row 3: Delete (full width)
                        ImmersiveActionButton(
                            icon = Icons.Default.Delete,
                            label = "Delete Video",
                            onClick = { showDeleteConfirmation = true },
                            modifier = Modifier.fillMaxWidth(),
                            isDestructive = true,
                        )
                    }
                }

                // Technical Details Card
                item(key = "technical_details", contentType = "details") {
                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                        ImmersiveHomeVideoTechnicalDetails(
                            item = item,
                            modifier = Modifier
                                .padding(horizontal = ImmersiveDimens.SpacingContentPadding)
                                .padding(top = 24.dp),
                        )
                    }
                }
            }
        }

        // Floating Back Button
        FloatingActionButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
            )
        }

        // Delete Confirmation Dialog
        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Delete Video?") },
                text = {
                    Text(
                        "Are you sure you want to delete \"${item.name}\"? " +
                            "This action cannot be undone.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmation = false
                            onDeleteClick(item)
                            android.widget.Toast.makeText(
                                context,
                                "Delete requested. You may need to refresh to confirm removal.",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
private fun HomeVideoHeroContent(
    item: BaseItemDto,
    playbackAnalysis: PlaybackCapabilityAnalysis?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ImmersiveDimens.HeroHeightPhone)
            .padding(horizontal = ImmersiveDimens.SpacingContentPadding)
            .padding(bottom = 32.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = item.name ?: "Home Video",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Metadata row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item.productionYear?.let { year ->
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }

                item.runTimeTicks?.let { ticks ->
                    val duration = item.getFormattedDuration()
                    duration?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }

                playbackAnalysis?.let { analysis ->
                    PlaybackStatusBadge(analysis = analysis)
                }
            }
        }
    }
}

@Composable
private fun ImmersiveHomeVideoTechnicalDetails(
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
            add("$width×$height")
        }
        videoStream?.codec?.let { add(it.uppercase()) }
        videoStream?.averageFrameRate?.let { frameRate ->
            add(String.format(Locale.US, "%.0f fps", frameRate))
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

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ImmersiveDimens.CornerRadiusCinematic),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 4.dp,
        ),
    ) {
        Column(
            modifier = Modifier.padding(ImmersiveDimens.SpacingContentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Video Information",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            runtime?.let { ImmersiveDetailRow(label = "Duration", value = it) }

            qualityLabel?.let { (label, color) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Quality",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        color = color,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            if (videoDetails.isNotBlank()) {
                ImmersiveDetailRow(label = "Video", value = videoDetails)
            }
            if (audioDetails.isNotBlank()) {
                ImmersiveDetailRow(label = "Audio", value = audioDetails)
            }
            container?.let { ImmersiveDetailRow(label = "Format", value = it) }
            sizeLabel?.let { ImmersiveDetailRow(label = "File Size", value = it) }
            videoStream?.bitRate?.let {
                ImmersiveDetailRow(label = "Video Bitrate", value = "${it / 1000} kbps")
            }
        }
    }
}

@Composable
private fun ImmersiveDetailRow(
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
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ImmersiveActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
) {
    val containerColor = if (isDestructive) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isDestructive) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    ElevatedCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(ImmersiveDimens.CornerRadiusCinematic),
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor,
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
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
