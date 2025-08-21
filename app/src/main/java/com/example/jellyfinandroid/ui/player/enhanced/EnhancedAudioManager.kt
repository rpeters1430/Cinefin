package com.example.jellyfinandroid.ui.player.enhanced

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi

data class AudioTrack(
    val id: String,
    val title: String,
    val language: String,
    val languageCode: String,
    val codec: String,
    val channels: Int,
    val sampleRate: Int,
    val bitrate: Long,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isDescriptive: Boolean = false,
    val isCommentary: Boolean = false,
    val channelLayout: String = "", // e.g., "5.1", "7.1", "Stereo"
    val quality: AudioQuality = AudioQuality.STANDARD,
)

enum class AudioQuality {
    STANDARD, HIGH, LOSSLESS, SPATIAL
}

data class AudioSettings(
    val selectedTrackId: String? = null,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val equalizerEnabled: Boolean = false,
    val equalizerBands: List<Float> = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), // 10-band EQ
    val dialogueEnhancement: Float = 0f, // 0-1
    val bassBoost: Float = 0f, // 0-1
    val virtualizerStrength: Float = 0f, // 0-1
    val loudnessEnhancer: Boolean = false,
    val audioDelay: Long = 0L, // milliseconds
    val preferredLanguages: List<String> = listOf("en"),
    val downmixSurround: Boolean = false,
    val nightMode: Boolean = false,
    val audioNormalization: Boolean = true,
    val skipSilence: Boolean = false,
    val audioProfile: AudioProfile = AudioProfile.BALANCED,
)

enum class AudioProfile {
    BALANCED, CINEMA, MUSIC, DIALOGUE, GAME, CUSTOM
}

data class EqualizerBand(
    val frequency: String,
    val gain: Float,
)

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun EnhancedAudioManager(
    availableTracks: List<AudioTrack>,
    currentTrack: AudioTrack?,
    audioSettings: AudioSettings,
    onDismiss: () -> Unit,
    onTrackSelect: (AudioTrack) -> Unit,
    onSettingsChange: (AudioSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Tracks", "Equalizer", "Enhancement", "Settings")

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
                    text = "Audio Settings",
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
                0 -> AudioTracksTab(
                    availableTracks = availableTracks,
                    currentTrack = currentTrack,
                    audioSettings = audioSettings,
                    onTrackSelect = onTrackSelect,
                    onSettingsChange = onSettingsChange,
                )
                1 -> EqualizerTab(
                    audioSettings = audioSettings,
                    onSettingsChange = onSettingsChange,
                )
                2 -> AudioEnhancementTab(
                    audioSettings = audioSettings,
                    onSettingsChange = onSettingsChange,
                )
                3 -> AdvancedAudioSettingsTab(
                    audioSettings = audioSettings,
                    onSettingsChange = onSettingsChange,
                )
            }
        }
    }
}

@Composable
private fun AudioTracksTab(
    availableTracks: List<AudioTrack>,
    currentTrack: AudioTrack?,
    audioSettings: AudioSettings,
    onTrackSelect: (AudioTrack) -> Unit,
    onSettingsChange: (AudioSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Volume control
        item {
            VolumeControlCard(
                volume = audioSettings.volume,
                isMuted = audioSettings.isMuted,
                onVolumeChange = { volume ->
                    onSettingsChange(audioSettings.copy(volume = volume))
                },
                onMuteToggle = { muted ->
                    onSettingsChange(audioSettings.copy(isMuted = muted))
                },
            )
        }

        // Audio tracks
        items(availableTracks) { track ->
            AudioTrackCard(
                track = track,
                isSelected = track.id == currentTrack?.id,
                onSelect = { onTrackSelect(track) },
            )
        }
    }
}

@Composable
private fun VolumeControlCard(
    volume: Float,
    isMuted: Boolean,
    onVolumeChange: (Float) -> Unit,
    onMuteToggle: (Boolean) -> Unit,
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
                    imageVector = when {
                        isMuted -> Icons.Default.VolumeOff
                        volume < 0.3f -> Icons.Default.VolumeDown
                        else -> Icons.Default.VolumeUp
                    },
                    contentDescription = null,
                )

                Text(
                    text = "Volume",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = if (isMuted) "Muted" else "${(volume * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(
                    onClick = { onMuteToggle(!isMuted) },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isMuted) MaterialTheme.colorScheme.error else Color.Transparent,
                            CircleShape,
                        ),
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeDown,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = if (isMuted) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface,
                    )
                }

                Slider(
                    value = if (isMuted) 0f else volume,
                    onValueChange = { newVolume ->
                        onVolumeChange(newVolume)
                        if (newVolume > 0f && isMuted) {
                            onMuteToggle(false)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        activeTrackColor = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    ),
                )

                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AudioTrackCard(
    track: AudioTrack,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
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
                        text = track.title,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )

                    if (track.isDefault) {
                        FilterChip(
                            onClick = { },
                            label = { Text("Default", fontSize = 10.sp) },
                            selected = false,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                labelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            enabled = false,
                            modifier = Modifier.height(24.dp),
                        )
                    }

                    if (track.isDescriptive) {
                        FilterChip(
                            onClick = { },
                            label = { Text("AD", fontSize = 10.sp) },
                            selected = false,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                labelColor = MaterialTheme.colorScheme.onSecondary,
                            ),
                            enabled = false,
                            modifier = Modifier.height(24.dp),
                        )
                    }

                    if (track.isCommentary) {
                        FilterChip(
                            onClick = { },
                            label = { Text("Commentary", fontSize = 10.sp) },
                            selected = false,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                labelColor = MaterialTheme.colorScheme.onTertiary,
                            ),
                            enabled = false,
                            modifier = Modifier.height(24.dp),
                        )
                    }

                    AudioQualityBadge(quality = track.quality)
                }
            },
            supportingContent = {
                Column {
                    Text("${track.language} (${track.languageCode.uppercase()})")

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${track.codec} • ${track.channels}ch",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text(
                            text = "${track.sampleRate / 1000}kHz",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (track.bitrate > 0) {
                            Text(
                                text = formatBitrate(track.bitrate),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (track.channelLayout.isNotEmpty()) {
                        Text(
                            text = track.channelLayout,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            leadingContent = {
                RadioButton(
                    selected = isSelected,
                    onClick = onSelect,
                )
            },
            trailingContent = {
                AudioTrackIcon(track = track)
            },
        )
    }
}

@Composable
private fun AudioQualityBadge(
    quality: AudioQuality,
    modifier: Modifier = Modifier,
) {
    val (text, color) = when (quality) {
        AudioQuality.STANDARD -> "Standard" to MaterialTheme.colorScheme.outline
        AudioQuality.HIGH -> "HD" to Color.Blue
        AudioQuality.LOSSLESS -> "Lossless" to Color.Green
        AudioQuality.SPATIAL -> "Spatial" to Color.Red
    }

    FilterChip(
        onClick = { },
        label = { Text(text, fontSize = 10.sp) },
        selected = false,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = color,
            labelColor = Color.White,
        ),
        enabled = false,
        modifier = modifier.height(24.dp),
    )
}

@Composable
private fun AudioTrackIcon(
    track: AudioTrack,
    modifier: Modifier = Modifier,
) {
    val icon = when {
        track.quality == AudioQuality.SPATIAL -> Icons.Default.SurroundSound
        track.channels > 2 -> Icons.Default.SurroundSound
        track.isDescriptive -> Icons.Default.RecordVoiceOver
        else -> Icons.Default.AudioFile
    }

    val color = when {
        track.quality == AudioQuality.SPATIAL -> Color.Red
        track.channels > 2 -> Color.Blue
        track.isDescriptive -> MaterialTheme.colorScheme.secondary
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
private fun EqualizerTab(
    audioSettings: AudioSettings,
    onSettingsChange: (AudioSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val equalizerBands = listOf(
        "32Hz", "64Hz", "125Hz", "250Hz", "500Hz",
        "1kHz", "2kHz", "4kHz", "8kHz", "16kHz",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Equalizer enable/disable
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (audioSettings.equalizerEnabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ),
        ) {
            ListItem(
                headlineContent = { Text("Equalizer") },
                supportingContent = { Text("Adjust frequency bands") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = null,
                        tint = if (audioSettings.equalizerEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                },
                trailingContent = {
                    Switch(
                        checked = audioSettings.equalizerEnabled,
                        onCheckedChange = { enabled ->
                            onSettingsChange(audioSettings.copy(equalizerEnabled = enabled))
                        },
                    )
                },
            )
        }

        if (audioSettings.equalizerEnabled) {
            // Preset selection
            AudioProfileSelector(
                currentProfile = audioSettings.audioProfile,
                onProfileChange = { profile ->
                    onSettingsChange(audioSettings.copy(audioProfile = profile))
                },
            )

            // Equalizer bands
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Frequency Bands",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        equalizerBands.forEachIndexed { index, frequency ->
                            EqualizerBandSlider(
                                frequency = frequency,
                                gain = audioSettings.equalizerBands.getOrElse(index) { 0f },
                                onGainChange = { gain ->
                                    val newBands = audioSettings.equalizerBands.toMutableList()
                                    if (index < newBands.size) {
                                        newBands[index] = gain
                                    } else {
                                        while (newBands.size <= index) {
                                            newBands.add(0f)
                                        }
                                        newBands[index] = gain
                                    }
                                    onSettingsChange(audioSettings.copy(equalizerBands = newBands))
                                },
                            )
                        }
                    }

                    // Reset button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        FilterChip(
                            onClick = {
                                val resetBands = List(10) { 0f }
                                onSettingsChange(
                                    audioSettings.copy(
                                        equalizerBands = resetBands,
                                        audioProfile = AudioProfile.BALANCED,
                                    ),
                                )
                            },
                            label = { Text("Reset") },
                            selected = false,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioProfileSelector(
    currentProfile: AudioProfile,
    onProfileChange: (AudioProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val profiles = AudioProfile.values()

    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Audio Presets",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                profiles.forEach { profile ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(
                            index = profile.ordinal,
                            count = profiles.size,
                        ),
                        onClick = { onProfileChange(profile) },
                        selected = currentProfile == profile,
                    ) {
                        Text(
                            text = profile.name.lowercase().replaceFirstChar { it.uppercase() },
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Text(
                text = getProfileDescription(currentProfile),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EqualizerBandSlider(
    frequency: String,
    gain: Float,
    onGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "${gain.toInt()}dB",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )

        Slider(
            value = gain,
            onValueChange = onGainChange,
            valueRange = -12f..12f,
            modifier = Modifier.height(120.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )

        Text(
            text = frequency,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            lineHeight = 10.sp,
        )
    }
}

@Composable
private fun AudioEnhancementTab(
    audioSettings: AudioSettings,
    onSettingsChange: (AudioSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            EnhancementCard(
                title = "Dialogue Enhancement",
                subtitle = "Boost speech clarity and reduce background noise",
                icon = Icons.Default.RecordVoiceOver,
                value = audioSettings.dialogueEnhancement,
                onValueChange = { value ->
                    onSettingsChange(audioSettings.copy(dialogueEnhancement = value))
                },
            )
        }

        item {
            EnhancementCard(
                title = "Bass Boost",
                subtitle = "Enhance low-frequency response",
                icon = Icons.Default.GraphicEq,
                value = audioSettings.bassBoost,
                onValueChange = { value ->
                    onSettingsChange(audioSettings.copy(bassBoost = value))
                },
            )
        }

        item {
            EnhancementCard(
                title = "Virtualizer",
                subtitle = "Create spatial audio from stereo sources",
                icon = Icons.Default.SurroundSound,
                value = audioSettings.virtualizerStrength,
                onValueChange = { value ->
                    onSettingsChange(audioSettings.copy(virtualizerStrength = value))
                },
            )
        }

        item {
            Card {
                ListItem(
                    headlineContent = { Text("Loudness Enhancer") },
                    supportingContent = { Text("Increase perceived volume without clipping") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = audioSettings.loudnessEnhancer,
                            onCheckedChange = { enabled ->
                                onSettingsChange(audioSettings.copy(loudnessEnhancer = enabled))
                            },
                        )
                    },
                )
            }
        }

        item {
            Card {
                ListItem(
                    headlineContent = { Text("Night Mode") },
                    supportingContent = { Text("Reduce dynamic range for quiet listening") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = null,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = audioSettings.nightMode,
                            onCheckedChange = { enabled ->
                                onSettingsChange(audioSettings.copy(nightMode = enabled))
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun EnhancementCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = "${(value * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Slider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AdvancedAudioSettingsTab(
    audioSettings: AudioSettings,
    onSettingsChange: (AudioSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card {
                ListItem(
                    headlineContent = { Text("Audio Normalization") },
                    supportingContent = { Text("Maintain consistent volume levels") },
                    trailingContent = {
                        Switch(
                            checked = audioSettings.audioNormalization,
                            onCheckedChange = { enabled ->
                                onSettingsChange(audioSettings.copy(audioNormalization = enabled))
                            },
                        )
                    },
                )
            }
        }

        item {
            Card {
                ListItem(
                    headlineContent = { Text("Skip Silence") },
                    supportingContent = { Text("Automatically skip silent parts") },
                    trailingContent = {
                        Switch(
                            checked = audioSettings.skipSilence,
                            onCheckedChange = { enabled ->
                                onSettingsChange(audioSettings.copy(skipSilence = enabled))
                            },
                        )
                    },
                )
            }
        }

        item {
            Card {
                ListItem(
                    headlineContent = { Text("Downmix Surround") },
                    supportingContent = { Text("Convert surround sound to stereo") },
                    trailingContent = {
                        Switch(
                            checked = audioSettings.downmixSurround,
                            onCheckedChange = { enabled ->
                                onSettingsChange(audioSettings.copy(downmixSurround = enabled))
                            },
                        )
                    },
                )
            }
        }

        item {
            AudioDelayCard(
                audioDelay = audioSettings.audioDelay,
                onDelayChange = { delay ->
                    onSettingsChange(audioSettings.copy(audioDelay = delay))
                },
            )
        }
    }
}

@Composable
private fun AudioDelayCard(
    audioDelay: Long,
    onDelayChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Audio Delay",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Synchronize audio with video",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = "${audioDelay}ms",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Slider(
                value = audioDelay.toFloat(),
                onValueChange = { delay -> onDelayChange(delay.toLong()) },
                valueRange = -1000f..1000f,
                steps = 39, // 50ms increments
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "-1000ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "+1000ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun getProfileDescription(profile: AudioProfile): String {
    return when (profile) {
        AudioProfile.BALANCED -> "Neutral sound profile for general content"
        AudioProfile.CINEMA -> "Enhanced for movies with boosted dialogue and surround"
        AudioProfile.MUSIC -> "Optimized for music with enhanced clarity and bass"
        AudioProfile.DIALOGUE -> "Focus on speech clarity and vocal enhancement"
        AudioProfile.GAME -> "Tuned for gaming with enhanced positional audio"
        AudioProfile.CUSTOM -> "User-defined equalizer settings"
    }
}

private fun formatBitrate(bitrate: Long): String {
    return when {
        bitrate >= 1_000_000 -> "${bitrate / 1_000_000} Mbps"
        bitrate >= 1_000 -> "${bitrate / 1_000} Kbps"
        else -> "$bitrate bps"
    }
}
