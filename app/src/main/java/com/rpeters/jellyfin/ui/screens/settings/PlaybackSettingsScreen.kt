package com.rpeters.jellyfin.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.preferences.AudioChannelPreference
import com.rpeters.jellyfin.data.preferences.ResumePlaybackMode
import com.rpeters.jellyfin.data.preferences.TranscodingQuality
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
                title = { Text("Playback Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "Streaming Quality",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            item {
                BitrateSetting(
                    title = "WiFi Max Bitrate",
                    currentValue = prefs.maxBitrateWifi,
                    onValueSelected = viewModel::setMaxBitrateWifi,
                )
            }

            item {
                BitrateSetting(
                    title = "Cellular Max Bitrate",
                    currentValue = prefs.maxBitrateCellular,
                    onValueSelected = viewModel::setMaxBitrateCellular,
                )
            }

            item {
                EnumSetting(
                    title = "Transcoding Quality",
                    subtitle = "Override automatic quality decisions",
                    currentValue = prefs.transcodingQuality,
                    values = TranscodingQuality.entries,
                    onValueSelected = viewModel::setTranscodingQuality,
                    labelProvider = { it.label },
                )
            }

            item {
                EnumSetting(
                    title = "Audio Channels",
                    subtitle = "Preferred maximum audio channel count",
                    currentValue = prefs.audioChannels,
                    values = AudioChannelPreference.entries,
                    onValueSelected = viewModel::setAudioChannels,
                    labelProvider = { it.label },
                )
            }

            item {
                LanguageSetting(
                    title = "Preferred Audio Language",
                    subtitle = "Default audio track language",
                    currentValue = prefs.preferredAudioLanguage,
                    onValueSelected = viewModel::setPreferredAudioLanguage,
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            item {
                Text(
                    text = "Behavior",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            item {
                SwitchSetting(
                    title = "Auto-play Next Episode",
                    subtitle = "Automatically play the next episode when current one ends",
                    checked = prefs.autoPlayNextEpisode,
                    onCheckedChange = viewModel::setAutoPlayNextEpisode,
                )
            }

            item {
                EnumSetting(
                    title = "Resume Playback",
                    subtitle = "What to do when resuming partially watched content",
                    currentValue = prefs.resumePlaybackMode,
                    values = ResumePlaybackMode.entries,
                    onValueSelected = viewModel::setResumePlaybackMode,
                    labelProvider = { it.label },
                )
            }
        }
    }
}

@Composable
private fun BitrateSetting(
    title: String,
    currentValue: Int,
    onValueSelected: (Int) -> Unit,
) {
    val bitrates = listOf(
        120_000_000 to "120 Mbps (4K)",
        80_000_000 to "80 Mbps",
        40_000_000 to "40 Mbps (1080p)",
        20_000_000 to "20 Mbps",
        10_000_000 to "10 Mbps (720p)",
        5_000_000 to "5 Mbps",
        3_000_000 to "3 Mbps (480p)",
    )

    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Box {
            Text(
                text = bitrates.find { it.first == currentValue }?.second ?: "${currentValue / 1_000_000} Mbps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(vertical = 8.dp),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                bitrates.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onValueSelected(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun <T : Enum<T>> EnumSetting(
    title: String,
    subtitle: String,
    currentValue: T,
    values: List<T>,
    onValueSelected: (T) -> Unit,
    labelProvider: (T) -> String,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            Text(
                text = labelProvider(currentValue),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(vertical = 8.dp),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                values.forEach { value ->
                    DropdownMenuItem(
                        text = { Text(labelProvider(value)) },
                        onClick = {
                            onValueSelected(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageSetting(
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

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            Text(
                text = languages.find { it.first == currentValue }?.second ?: "No preference",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(vertical = 8.dp),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                languages.forEach { (code, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onValueSelected(code)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
