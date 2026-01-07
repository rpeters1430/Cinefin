package com.rpeters.jellyfin.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.data.preferences.AccentColor
import com.rpeters.jellyfin.data.preferences.ContrastLevel
import com.rpeters.jellyfin.data.preferences.ThemeMode
import com.rpeters.jellyfin.ui.components.ExpressiveRadioListItem
import com.rpeters.jellyfin.ui.components.ExpressiveSwitchListItem
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
@OptInAppExperimentalApis
@Composable
fun AppearanceSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ThemePreferencesViewModel = hiltViewModel(),
) {
    val themePreferences by viewModel.themePreferences.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_appearance_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
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
            ExpressiveSettingsCard(
                title = "Theme Mode",
                icon = Icons.Default.DarkMode,
            ) {
                ThemeMode.entries.forEach { mode ->
                    ExpressiveRadioListItem(
                        title = getThemeModeName(mode),
                        subtitle = getThemeModeDescription(mode),
                        selected = themePreferences.themeMode == mode,
                        onSelect = { viewModel.setThemeMode(mode) },
                    )
                }
            }

            ExpressiveSettingsCard(
                title = "Material You",
                icon = Icons.Default.Palette,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ExpressiveSwitchListItem(
                        title = "Dynamic Colors",
                        subtitle = "Use colors from your wallpaper",
                        checked = themePreferences.useDynamicColors,
                        onCheckedChange = { viewModel.setUseDynamicColors(it) },
                        leadingIcon = Icons.Default.Palette,
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
                    ExpressiveSwitchListItem(
                        title = "Themed App Icon",
                        subtitle = "Match icon color to your wallpaper",
                        checked = themePreferences.useThemedIcon,
                        onCheckedChange = { viewModel.setUseThemedIcon(it) },
                        leadingIcon = Icons.Default.Palette,
                    )
                }
            }

            if (!themePreferences.useDynamicColors || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                ExpressiveSettingsCard(
                    title = "Accent Color",
                    icon = Icons.Default.Palette,
                ) {
                    AccentColorGrid(
                        selectedColor = themePreferences.accentColor,
                        onColorSelect = { viewModel.setAccentColor(it) },
                    )
                }
            }

            ExpressiveSettingsCard(
                title = "Contrast",
                icon = Icons.Default.Tonality,
            ) {
                ContrastLevel.entries.forEach { level ->
                    ExpressiveRadioListItem(
                        title = getContrastLevelName(level),
                        subtitle = getContrastLevelDescription(level),
                        selected = themePreferences.contrastLevel == level,
                        onSelect = { viewModel.setContrastLevel(level) },
                    )
                }
            }

            ExpressiveSettingsCard(
                title = "Accessibility",
                icon = Icons.Default.Accessibility,
            ) {
                ExpressiveSwitchListItem(
                    title = "Respect Reduce Motion",
                    subtitle = "Follow system animation preferences",
                    checked = themePreferences.respectReduceMotion,
                    onCheckedChange = { viewModel.setRespectReduceMotion(it) },
                )
            }
        }
    }
}

@Composable
private fun ExpressiveSettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            content()
        }
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
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.inverseOnSurface
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
