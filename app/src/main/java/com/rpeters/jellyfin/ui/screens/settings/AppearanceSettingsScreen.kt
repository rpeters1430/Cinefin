package com.rpeters.jellyfin.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.data.preferences.AccentColor
import com.rpeters.jellyfin.data.preferences.ContrastLevel
import com.rpeters.jellyfin.data.preferences.ThemeMode
import com.rpeters.jellyfin.ui.theme.getAccentColorForPreview
import com.rpeters.jellyfin.ui.theme.getAccentColorName
import com.rpeters.jellyfin.ui.theme.getContrastLevelDescription
import com.rpeters.jellyfin.ui.theme.getContrastLevelName
import com.rpeters.jellyfin.ui.theme.getThemeModeDescription
import com.rpeters.jellyfin.ui.theme.getThemeModeName
import com.rpeters.jellyfin.ui.viewmodel.ThemePreferencesViewModel

/**
 * Settings screen for theme customization.
 * Provides controls for all Material You dynamic theming features.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ThemePreferencesViewModel = hiltViewModel(),
) {
    val themePreferences by viewModel.themePreferences.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Theme Mode Section
            SettingsSection(
                title = "Theme Mode",
                icon = Icons.Default.DarkMode,
            ) {
                ThemeMode.entries.forEach { mode ->
                    ThemeModeOption(
                        mode = mode,
                        selected = themePreferences.themeMode == mode,
                        onSelect = { viewModel.setThemeMode(mode) },
                    )
                }
            }

            HorizontalDivider()

            // Dynamic Colors Section
            SettingsSection(
                title = "Material You",
                icon = Icons.Default.Palette,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchSetting(
                        title = "Dynamic Colors",
                        description = "Use colors from your wallpaper",
                        checked = themePreferences.useDynamicColors,
                        onCheckedChange = { viewModel.setUseDynamicColors(it) },
                    )
                } else {
                    Text(
                        text = "Dynamic colors require Android 12 or higher",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SwitchSetting(
                        title = "Themed App Icon",
                        description = "Match icon color to your wallpaper",
                        checked = themePreferences.useThemedIcon,
                        onCheckedChange = { viewModel.setUseThemedIcon(it) },
                    )
                }
            }

            HorizontalDivider()

            // Accent Color Section (shown when dynamic colors are off)
            if (!themePreferences.useDynamicColors || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                SettingsSection(
                    title = "Accent Color",
                    icon = Icons.Default.Palette,
                ) {
                    AccentColorGrid(
                        selectedColor = themePreferences.accentColor,
                        onColorSelect = { viewModel.setAccentColor(it) },
                    )
                }

                HorizontalDivider()
            }

            // Contrast Level Section
            SettingsSection(
                title = "Contrast",
                icon = Icons.Default.Tonality,
            ) {
                ContrastLevel.entries.forEach { level ->
                    ContrastLevelOption(
                        level = level,
                        selected = themePreferences.contrastLevel == level,
                        onSelect = { viewModel.setContrastLevel(level) },
                    )
                }
            }

            HorizontalDivider()

            // Additional Settings
            SettingsSection(
                title = "Accessibility",
                icon = Icons.Default.Accessibility,
            ) {
                SwitchSetting(
                    title = "Respect Reduce Motion",
                    description = "Follow system animation preferences",
                    checked = themePreferences.respectReduceMotion,
                    onCheckedChange = { viewModel.setRespectReduceMotion(it) },
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "$title section icon",
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        content()
    }
}

@Composable
private fun ThemeModeOption(
    mode: ThemeMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = getThemeModeName(mode),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = getThemeModeDescription(mode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun AccentColorGrid(
    selectedColor: AccentColor,
    onColorSelect: (AccentColor) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccentColor.entries.chunked(3).forEach { rowColors ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowColors.forEach { color ->
                    AccentColorOption(
                        color = color,
                        selected = selectedColor == color,
                        onSelect = { onColorSelect(color) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - rowColors.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AccentColorOption(
    color: AccentColor,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewColor = getAccentColorForPreview(color)
    val contentColor = if (previewColor.luminance() > 0.5f) {
        Color.Black
    } else {
        Color.White
    }
    Card(
        onClick = onSelect,
        modifier = modifier.height(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = previewColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    modifier = Modifier.size(24.dp),
                    tint = contentColor,
                )
            } else {
                Spacer(modifier = Modifier.size(24.dp))
            }
            Text(
                text = getAccentColorName(color),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun ContrastLevelOption(
    level: ContrastLevel,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = getContrastLevelName(level),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = getContrastLevelDescription(level),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
