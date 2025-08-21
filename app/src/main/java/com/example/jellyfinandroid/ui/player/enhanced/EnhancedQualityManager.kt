package com.example.jellyfinandroid.ui.player.enhanced

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi

data class EnhancedVideoQuality(
    val id: String,
    val label: String,
    val bitrate: Long, // bps
    val width: Int,
    val height: Int,
    val fps: Float,
    val codec: String,
    val fileSize: Long? = null, // bytes
    val estimatedBandwidth: Long, // bps required
    val isOriginal: Boolean = false,
    val isAdaptive: Boolean = false,
    val audioChannels: String? = null,
    val audioCodec: String? = null,
    val hdrFormat: String? = null, // HDR10, DolbyVision, etc.
)

data class NetworkInfo(
    val connectionType: String, // WiFi, Cellular, Ethernet
    val availableBandwidth: Long, // bps
    val currentBandwidth: Long, // bps
    val latency: Int, // ms
    val signalStrength: Int, // 0-100
    val isMetered: Boolean = false,
)

data class PlaybackStatistics(
    val droppedFrames: Int = 0,
    val totalFrames: Int = 0,
    val bufferHealth: Float = 0f, // 0-1
    val averageBitrate: Long = 0L,
    val codecInfo: String = "",
    val resolution: String = "",
    val frameRate: Float = 0f,
)

data class EnhancedPlayerSettings(
    val adaptiveStreamingEnabled: Boolean = true,
    val preferHigherQuality: Boolean = false,
    val maxBitrateWifi: Long = 0L, // 0 = unlimited
    val maxBitrateCellular: Long = 5_000_000L, // 5 Mbps default
    val bufferDuration: Float = 10f, // seconds
    val skipSilence: Boolean = false,
    val normalizeAudio: Boolean = false,
    val enhanceDialogue: Boolean = false,
    val autoSelectSubtitles: Boolean = true,
    val preferredSubtitleLanguage: String = "en",
    val hardwareDecodingEnabled: Boolean = true,
    val saveDataMode: Boolean = false,
    val preloadNextEpisode: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun EnhancedPlayerSettingsDialog(
    currentQuality: EnhancedVideoQuality?,
    availableQualities: List<EnhancedVideoQuality>,
    networkInfo: NetworkInfo,
    playbackStats: PlaybackStatistics,
    settings: EnhancedPlayerSettings,
    onDismiss: () -> Unit,
    onQualitySelect: (EnhancedVideoQuality) -> Unit,
    onSettingsChange: (EnhancedPlayerSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Quality", "Network", "Settings", "Stats")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Video Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                    )
                }
            }

            // Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab Content
            when (selectedTabIndex) {
                0 -> QualitySelectionTab(
                    currentQuality = currentQuality,
                    availableQualities = availableQualities,
                    networkInfo = networkInfo,
                    settings = settings,
                    onQualitySelect = onQualitySelect,
                    onSettingsChange = onSettingsChange,
                )
                1 -> NetworkInfoTab(
                    networkInfo = networkInfo,
                    playbackStats = playbackStats,
                )
                2 -> AdvancedSettingsTab(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                )
                3 -> PlaybackStatsTab(
                    stats = playbackStats,
                    currentQuality = currentQuality,
                )
            }
        }
    }
}

@Composable
private fun QualitySelectionTab(
    currentQuality: EnhancedVideoQuality?,
    availableQualities: List<EnhancedVideoQuality>,
    networkInfo: NetworkInfo,
    settings: EnhancedPlayerSettings,
    onQualitySelect: (EnhancedVideoQuality) -> Unit,
    onSettingsChange: (EnhancedPlayerSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Adaptive streaming toggle
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (settings.adaptiveStreamingEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
            ) {
                ListItem(
                    headlineContent = { Text("Adaptive Streaming") },
                    supportingContent = {
                        Text("Automatically adjust quality based on network conditions")
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = if (settings.adaptiveStreamingEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.adaptiveStreamingEnabled,
                            onCheckedChange = { enabled ->
                                onSettingsChange(settings.copy(adaptiveStreamingEnabled = enabled))
                            },
                        )
                    },
                )
            }
        }

        // Quality recommendation
        if (!settings.adaptiveStreamingEnabled) {
            item {
                QualityRecommendationCard(
                    networkInfo = networkInfo,
                    availableQualities = availableQualities,
                )
            }
        }

        // Quality options
        availableQualities.forEach { quality ->
            item {
                QualityOptionCard(
                    quality = quality,
                    isSelected = currentQuality?.id == quality.id,
                    isRecommended = isQualityRecommended(quality, networkInfo),
                    isDisabled = settings.adaptiveStreamingEnabled,
                    networkInfo = networkInfo,
                    onSelect = { onQualitySelect(quality) },
                )
            }
        }
    }
}

@Composable
private fun QualityRecommendationCard(
    networkInfo: NetworkInfo,
    availableQualities: List<EnhancedVideoQuality>,
    modifier: Modifier = Modifier,
) {
    val recommendedQuality = getRecommendedQuality(networkInfo, availableQualities)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )

            Column {
                Text(
                    text = "Recommended Quality",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )

                Text(
                    text = recommendedQuality?.label ?: "Auto",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )

                Text(
                    text = "Based on ${networkInfo.connectionType} connection (${formatBandwidth(networkInfo.currentBandwidth)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun QualityOptionCard(
    quality: EnhancedVideoQuality,
    isSelected: Boolean,
    isRecommended: Boolean,
    isDisabled: Boolean,
    networkInfo: NetworkInfo,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canStream = quality.estimatedBandwidth <= networkInfo.currentBandwidth
    val effectivelyDisabled = isDisabled || !canStream

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !effectivelyDisabled) { onSelect() }
            .alpha(if (effectivelyDisabled) 0.6f else 1f),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                isRecommended -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp,
        ),
    ) {
        ListItem(
            headlineContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = quality.label,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )

                    if (quality.isOriginal) {
                        FilterChip(
                            onClick = { },
                            label = { Text("Original", fontSize = 10.sp) },
                            selected = false,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                labelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            enabled = false,
                            modifier = Modifier.height(24.dp),
                        )
                    }

                    if (isRecommended) {
                        FilterChip(
                            onClick = { },
                            label = { Text("Recommended", fontSize = 10.sp) },
                            selected = false,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                labelColor = MaterialTheme.colorScheme.onSecondary,
                            ),
                            enabled = false,
                            modifier = Modifier.height(24.dp),
                        )
                    }

                    quality.hdrFormat?.let { hdr ->
                        FilterChip(
                            onClick = { },
                            label = { Text(hdr, fontSize = 10.sp) },
                            selected = false,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                labelColor = MaterialTheme.colorScheme.onTertiary,
                            ),
                            enabled = false,
                            modifier = Modifier.height(24.dp),
                        )
                    }
                }
            },
            supportingContent = {
                Column {
                    Text("${quality.width}×${quality.height} • ${quality.fps}fps • ${quality.codec}")

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Bitrate: ${formatBitrate(quality.bitrate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        quality.fileSize?.let { size ->
                            Text(
                                text = "Size: ${formatFileSize(size)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Network compatibility indicator
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (canStream) Icons.Default.Wifi else Icons.Default.NetworkCell,
                            contentDescription = null,
                            tint = if (canStream) Color.Green else Color(0xFFFF9800),
                            modifier = Modifier.size(16.dp),
                        )

                        Text(
                            text = if (canStream) "Compatible" else "May buffer",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (canStream) Color.Green else Color(0xFFFF9800),
                        )

                        Text(
                            text = "Needs ${formatBandwidth(quality.estimatedBandwidth)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            leadingContent = {
                RadioButton(
                    selected = isSelected,
                    onClick = onSelect,
                    enabled = !effectivelyDisabled,
                )
            },
            trailingContent = {
                QualityIndicatorIcon(quality = quality)
            },
        )
    }
}

@Composable
private fun QualityIndicatorIcon(
    quality: EnhancedVideoQuality,
    modifier: Modifier = Modifier,
) {
    val icon = when {
        quality.isOriginal -> Icons.Default.HighQuality
        quality.height >= 2160 -> Icons.Default.VideoSettings // 4K
        quality.height >= 1080 -> Icons.Default.Settings // HD
        else -> Icons.Default.Speed // SD
    }

    val color = when {
        quality.isOriginal -> MaterialTheme.colorScheme.primary
        quality.height >= 2160 -> Color.Red
        quality.height >= 1080 -> Color.Blue
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = color,
        modifier = modifier.size(24.dp),
    )
}

@Composable
private fun NetworkInfoTab(
    networkInfo: NetworkInfo,
    playbackStats: PlaybackStatistics,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            NetworkStatusCard(networkInfo = networkInfo)
        }

        item {
            BandwidthUsageCard(
                networkInfo = networkInfo,
                playbackStats = playbackStats,
            )
        }

        item {
            BufferHealthCard(playbackStats = playbackStats)
        }
    }
}

@Composable
private fun NetworkStatusCard(
    networkInfo: NetworkInfo,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when (networkInfo.connectionType) {
                        "WiFi" -> Icons.Default.Wifi
                        else -> Icons.Default.NetworkCell
                    },
                    contentDescription = null,
                )

                Text(
                    text = "Network Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Connection type and signal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Connection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = networkInfo.connectionType,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Signal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${networkInfo.signalStrength}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Bandwidth info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Available Bandwidth",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatBandwidth(networkInfo.availableBandwidth),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Latency",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${networkInfo.latency}ms",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            if (networkInfo.isMetered) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.DataUsage,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Metered Connection - Data usage will be monitored",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF9800),
                    )
                }
            }
        }
    }
}

@Composable
private fun BandwidthUsageCard(
    networkInfo: NetworkInfo,
    playbackStats: PlaybackStatistics,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Bandwidth Usage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )

            // Current usage vs available
            val usagePercentage = if (networkInfo.availableBandwidth > 0) {
                (playbackStats.averageBitrate.toFloat() / networkInfo.availableBandwidth.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Current Usage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${(usagePercentage * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LinearProgressIndicator(
                    progress = { usagePercentage },
                    modifier = Modifier.fillMaxWidth(),
                    color = when {
                        usagePercentage > 0.8f -> Color.Red
                        usagePercentage > 0.6f -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.primary
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatBandwidth(playbackStats.averageBitrate),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = formatBandwidth(networkInfo.availableBandwidth),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun BufferHealthCard(
    playbackStats: PlaybackStatistics,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                playbackStats.bufferHealth > 0.7f -> MaterialTheme.colorScheme.primaryContainer
                playbackStats.bufferHealth > 0.3f -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Buffer Health",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )

            LinearProgressIndicator(
                progress = { playbackStats.bufferHealth },
                modifier = Modifier.fillMaxWidth(),
                color = when {
                    playbackStats.bufferHealth > 0.7f -> Color.Green
                    playbackStats.bufferHealth > 0.3f -> Color(0xFFFF9800)
                    else -> Color.Red
                },
            )

            Text(
                text = when {
                    playbackStats.bufferHealth > 0.7f -> "Excellent - Smooth playback"
                    playbackStats.bufferHealth > 0.3f -> "Good - Minor buffering possible"
                    else -> "Poor - Buffering likely"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AdvancedSettingsTab(
    settings: EnhancedPlayerSettings,
    onSettingsChange: (EnhancedPlayerSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SettingsSection(
                title = "Quality & Performance",
                icon = Icons.Default.HighQuality,
            ) {
                SettingItem(
                    title = "Prefer Higher Quality",
                    subtitle = "Choose quality over smoothness when possible",
                    trailing = {
                        Switch(
                            checked = settings.preferHigherQuality,
                            onCheckedChange = {
                                onSettingsChange(settings.copy(preferHigherQuality = it))
                            },
                        )
                    },
                )

                SettingItem(
                    title = "Hardware Decoding",
                    subtitle = "Use device hardware for better performance",
                    trailing = {
                        Switch(
                            checked = settings.hardwareDecodingEnabled,
                            onCheckedChange = {
                                onSettingsChange(settings.copy(hardwareDecodingEnabled = it))
                            },
                        )
                    },
                )

                SettingItem(
                    title = "Save Data Mode",
                    subtitle = "Reduce quality to save bandwidth",
                    trailing = {
                        Switch(
                            checked = settings.saveDataMode,
                            onCheckedChange = {
                                onSettingsChange(settings.copy(saveDataMode = it))
                            },
                        )
                    },
                )
            }
        }

        item {
            SettingsSection(
                title = "Audio",
                icon = Icons.Default.Speed,
            ) {
                SettingItem(
                    title = "Skip Silence",
                    subtitle = "Automatically skip silent parts",
                    trailing = {
                        Switch(
                            checked = settings.skipSilence,
                            onCheckedChange = {
                                onSettingsChange(settings.copy(skipSilence = it))
                            },
                        )
                    },
                )

                SettingItem(
                    title = "Normalize Audio",
                    subtitle = "Even out volume levels",
                    trailing = {
                        Switch(
                            checked = settings.normalizeAudio,
                            onCheckedChange = {
                                onSettingsChange(settings.copy(normalizeAudio = it))
                            },
                        )
                    },
                )

                SettingItem(
                    title = "Enhance Dialogue",
                    subtitle = "Boost speech clarity",
                    trailing = {
                        Switch(
                            checked = settings.enhanceDialogue,
                            onCheckedChange = {
                                onSettingsChange(settings.copy(enhanceDialogue = it))
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            content()
        }
    }
}

@Composable
private fun SettingItem(
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        ),
        modifier = modifier,
    )
}

@Composable
private fun PlaybackStatsTab(
    stats: PlaybackStatistics,
    currentQuality: EnhancedVideoQuality?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PlaybackInfoCard(
                stats = stats,
                currentQuality = currentQuality,
            )
        }

        item {
            PerformanceStatsCard(stats = stats)
        }
    }
}

@Composable
private fun PlaybackInfoCard(
    stats: PlaybackStatistics,
    currentQuality: EnhancedVideoQuality?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Current Playback Info",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )

            currentQuality?.let { quality ->
                StatsRow("Resolution", "${quality.width}×${quality.height}")
                StatsRow("Frame Rate", "${quality.fps} fps")
                StatsRow("Codec", "${quality.codec} / ${quality.audioCodec ?: "Unknown"}")
                StatsRow("Bitrate", formatBitrate(stats.averageBitrate))
                quality.hdrFormat?.let { hdr ->
                    StatsRow("HDR", hdr)
                }
            }
        }
    }
}

@Composable
private fun PerformanceStatsCard(
    stats: PlaybackStatistics,
    modifier: Modifier = Modifier,
) {
    val dropRate = if (stats.totalFrames > 0) {
        (stats.droppedFrames.toFloat() / stats.totalFrames.toFloat() * 100)
    } else {
        0f
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (dropRate > 5f) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Performance Stats",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )

            StatsRow("Dropped Frames", "${stats.droppedFrames}/${stats.totalFrames}")
            StatsRow("Drop Rate", "${dropRate.toInt()}%")
            StatsRow("Buffer Health", "${(stats.bufferHealth * 100).toInt()}%")

            if (dropRate > 5f) {
                Text(
                    text = "⚠️ High frame drop rate detected. Consider lowering quality.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun StatsRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun isQualityRecommended(quality: EnhancedVideoQuality, networkInfo: NetworkInfo): Boolean {
    return quality.estimatedBandwidth <= networkInfo.currentBandwidth * 0.8 // 80% of available bandwidth
}

private fun getRecommendedQuality(
    networkInfo: NetworkInfo,
    availableQualities: List<EnhancedVideoQuality>,
): EnhancedVideoQuality? {
    val suitableQualities = availableQualities.filter { quality ->
        quality.estimatedBandwidth <= networkInfo.currentBandwidth * 0.8
    }

    return suitableQualities.maxByOrNull { it.height }
}

private fun formatBitrate(bitrate: Long): String {
    return when {
        bitrate >= 1_000_000 -> "${bitrate / 1_000_000} Mbps"
        bitrate >= 1_000 -> "${bitrate / 1_000} Kbps"
        else -> "$bitrate bps"
    }
}

private fun formatBandwidth(bandwidth: Long): String = formatBitrate(bandwidth)

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
}
