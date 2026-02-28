package com.rpeters.jellyfin.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.preferences.AudioChannelPreference
import com.rpeters.jellyfin.data.preferences.ResumePlaybackMode
import com.rpeters.jellyfin.data.preferences.TranscodingQuality
import com.rpeters.jellyfin.ui.components.ExpressiveRadioListItem
import com.rpeters.jellyfin.ui.components.ExpressiveSwitchListItem
import com.rpeters.jellyfin.ui.viewmodel.PlaybackPreferencesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaybackPreferencesViewModel = hiltViewModel(),
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Playback Settings",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Streaming Quality Section
            ExpressivePlaybackSection(
                title = "Streaming Quality",
                icon = Icons.Default.HighQuality
            ) {
                Text(
                    text = "WiFi Max Bitrate",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                BitrateSelector(
                    currentValue = prefs.maxBitrateWifi,
                    onValueSelected = viewModel::setMaxBitrateWifi
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Cellular Max Bitrate",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                BitrateSelector(
                    currentValue = prefs.maxBitrateCellular,
                    onValueSelected = viewModel::setMaxBitrateCellular
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                ExpressivePlaybackOption(
                    title = "Transcoding Quality",
                    subtitle = "Override automatic quality decisions",
                    icon = Icons.Default.Tune,
                    currentValue = prefs.transcodingQuality,
                    values = TranscodingQuality.entries,
                    onValueSelected = viewModel::setTranscodingQuality,
                    labelProvider = { it.label }
                )
            }

            // Audio & Language Section
            ExpressivePlaybackSection(
                title = "Audio & Language",
                icon = Icons.Default.AudioFile
            ) {
                ExpressivePlaybackOption(
                    title = "Audio Channels",
                    subtitle = "Preferred maximum audio channel count",
                    icon = Icons.Default.SurroundSound,
                    currentValue = prefs.audioChannels,
                    values = AudioChannelPreference.entries,
                    onValueSelected = viewModel::setAudioChannels,
                    labelProvider = { it.label }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                LanguageSettingEnhanced(
                    title = "Preferred Audio Language",
                    subtitle = "Default audio track language",
                    currentValue = prefs.preferredAudioLanguage,
                    onValueSelected = viewModel::setPreferredAudioLanguage
                )
            }

            // Playback Behavior Section
            ExpressivePlaybackSection(
                title = "Playback Behavior",
                icon = Icons.Default.PlayCircle
            ) {
                ExpressiveSwitchListItem(
                    title = "Auto-play Next Episode",
                    subtitle = "Automatically play the next episode when current one ends",
                    checked = prefs.autoPlayNextEpisode,
                    onCheckedChange = viewModel::setAutoPlayNextEpisode,
                    leadingIcon = Icons.Default.SkipNext
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Text(
                    text = "Resume Playback",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                ResumeModeRow(
                    selectedMode = prefs.resumePlaybackMode,
                    onModeSelect = viewModel::setResumePlaybackMode
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BitrateSelector(
    currentValue: Int,
    onValueSelected: (Int) -> Unit
) {
    val bitrates = listOf(
        120_000_000 to "4K",
        40_000_000 to "1080p",
        10_000_000 to "720p",
        3_000_000 to "480p"
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(bitrates) { (value, label) ->
            val isSelected = currentValue == value
            FilterChip(
                selected = isSelected,
                onClick = { onValueSelected(value) },
                label = { Text(label) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
        item {
            // Custom option if current value is not in the list
            if (bitrates.none { it.first == currentValue }) {
                FilterChip(
                    selected = true,
                    onClick = { },
                    label = { Text("${currentValue / 1_000_000} Mbps") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            }
        }
    }
}

@Composable
private fun ResumeModeRow(
    selectedMode: ResumePlaybackMode,
    onModeSelect: (ResumePlaybackMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ResumePlaybackMode.entries.forEach { mode ->
            val isSelected = selectedMode == mode
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onModeSelect(mode) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = when(mode) {
                            ResumePlaybackMode.ASK -> Icons.Default.QuestionMark
                            ResumePlaybackMode.ALWAYS -> Icons.Default.PlayArrow
                            ResumePlaybackMode.NEVER -> Icons.Default.Replay
                        },
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun <T : Enum<T>> ExpressivePlaybackOption(
    title: String,
    subtitle: String,
    icon: ImageVector,
    currentValue: T,
    values: List<T>,
    onValueSelected: (T) -> Unit,
    labelProvider: (T) -> String,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            Text(
                text = labelProvider(currentValue),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                values.forEach { value ->
                    DropdownMenuItem(
                        text = { Text(labelProvider(value)) },
                        onClick = {
                            onValueSelected(value)
                            expanded = false
                        },
                        trailingIcon = if (currentValue == value) {
                            { Icon(Icons.Default.Check, null) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageSettingEnhanced(
    title: String,
    subtitle: String,
    currentValue: String?,
    onValueSelected: (String?) -> Unit,
) {
    val languages = listOf(
        null to "No preference",
        "eng" to "English",
        "spa" to "Spanish",
        "fra" to "French",
        "deu" to "German",
        "ita" to "Italian",
        "por" to "Portuguese",
        "rus" to "Russian",
        "jpn" to "Japanese",
        "kor" to "Korean",
        "chi" to "Chinese",
        "ara" to "Arabic",
        "hin" to "Hindi",
    )

    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Language,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            Text(
                text = languages.find { it.first == currentValue }?.second ?: "No preference",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                languages.forEach { (code, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onValueSelected(code)
                            expanded = false
                        },
                        trailingIcon = if (currentValue == code) {
                            { Icon(Icons.Default.Check, null) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressivePlaybackSection(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                )
            }
            content()
        }
    }
}
