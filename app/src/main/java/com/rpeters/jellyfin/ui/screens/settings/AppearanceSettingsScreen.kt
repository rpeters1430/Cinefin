package com.rpeters.jellyfin.ui.screens.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.preferences.AccentColor
import com.rpeters.jellyfin.data.preferences.AppFont
import com.rpeters.jellyfin.data.preferences.ContrastLevel
import com.rpeters.jellyfin.data.preferences.ThemeMode
import com.rpeters.jellyfin.ui.components.ExpressiveBackNavigationIcon
import com.rpeters.jellyfin.ui.components.ExpressiveContentCard
import com.rpeters.jellyfin.ui.components.ExpressiveRadioListItem
import com.rpeters.jellyfin.ui.components.ExpressiveSwitchListItem
import com.rpeters.jellyfin.ui.components.ExpressiveTextButton
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBar
import com.rpeters.jellyfin.ui.theme.JellyfinExpressiveTheme
import com.rpeters.jellyfin.ui.theme.getAccentColorForPreview
import com.rpeters.jellyfin.ui.theme.getAccentColorName
import com.rpeters.jellyfin.ui.theme.getContrastLevelDescription
import com.rpeters.jellyfin.ui.theme.getContrastLevelName
import com.rpeters.jellyfin.ui.theme.getThemeModeName
import com.rpeters.jellyfin.ui.theme.toFontFamily
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
            ExpressiveTopAppBar(
                title = stringResource(R.string.settings_appearance_title),
                navigationIcon = {
                    ExpressiveBackNavigationIcon(onClick = onNavigateBack)
                },
                actions = {
                    ExpressiveTextButton(onClick = viewModel::resetToDefaults) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset")
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Theme Preview Card
            ThemePreviewCard(appFont = themePreferences.appFont)

            // Theme Mode Section
            ExpressiveSettingsCard(
                title = "Theme Mode",
                icon = Icons.Default.DarkMode,
            ) {
                ThemeModeRow(
                    selectedMode = themePreferences.themeMode,
                    onModeSelect = { viewModel.setThemeMode(it) }
                )
            }

            // Material You / Accent Color Section
            ExpressiveSettingsCard(
                title = "Color System",
                icon = Icons.Default.Palette,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ExpressiveSwitchListItem(
                        title = "Dynamic Colors",
                        subtitle = "Colors extracted from your wallpaper",
                        checked = themePreferences.useDynamicColors,
                        onCheckedChange = { viewModel.setUseDynamicColors(it) },
                        leadingIcon = Icons.Default.AutoAwesome,
                    )
                    
                    AnimatedVisibility(
                        visible = !themePreferences.useDynamicColors,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "Custom Accent Color",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            AccentColorRow(
                                selectedColor = themePreferences.accentColor,
                                onColorSelect = { viewModel.setAccentColor(it) }
                            )
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        ExpressiveSwitchListItem(
                            title = "Themed App Icon",
                            subtitle = "Adapt app icon to your wallpaper colors",
                            checked = themePreferences.useThemedIcon,
                            onCheckedChange = { viewModel.setUseThemedIcon(it) },
                            leadingIcon = Icons.Default.ColorLens,
                        )
                    }
                } else {
                    // Pre-Android 12: Always show accent colors
                    Text(
                        text = "Choose your brand color",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    AccentColorRow(
                        selectedColor = themePreferences.accentColor,
                        onColorSelect = { viewModel.setAccentColor(it) }
                    )
                }
            }

            // Typography Section
            ExpressiveSettingsCard(
                title = "Typography",
                icon = Icons.Default.FontDownload,
            ) {
                FontPreview(fontFamily = themePreferences.appFont.toFontFamily())
                AppFont.entries.forEach { font ->
                    ExpressiveRadioListItem(
                        title = getAppFontName(font),
                        subtitle = getAppFontDescription(font),
                        selected = themePreferences.appFont == font,
                        onSelect = { viewModel.setAppFont(font) },
                    )
                }
            }

            // Contrast Section
            ExpressiveSettingsCard(
                title = "Contrast",
                icon = Icons.Default.Contrast,
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

            // Accessibility Section
            ExpressiveSettingsCard(
                title = "Accessibility",
                icon = Icons.Default.Accessibility,
            ) {
                ExpressiveSwitchListItem(
                    title = "Respect Reduce Motion",
                    subtitle = "Disable heavy animations if system set to reduce motion",
                    checked = themePreferences.respectReduceMotion,
                    onCheckedChange = { viewModel.setRespectReduceMotion(it) },
                )
            }

            ExpressiveSettingsCard(
                title = "Layout",
                icon = Icons.Default.AutoAwesome,
                description = "System bar and surface treatment",
            ) {
                ExpressiveSwitchListItem(
                    title = "Edge-to-Edge",
                    subtitle = "Extend content behind the status and navigation bars",
                    checked = themePreferences.enableEdgeToEdge,
                    onCheckedChange = { viewModel.setEnableEdgeToEdge(it) },
                    leadingIcon = Icons.Default.AutoAwesome,
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ThemePreviewCard(
    appFont: AppFont,
    modifier: Modifier = Modifier
) {
    ExpressiveContentCard(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        containerColor = JellyfinExpressiveTheme.colors.sectionContainerHigh,
        shape = JellyfinExpressiveTheme.shapes.previewCard,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background mockup
            Column(modifier = Modifier.fillMaxSize()) {
                // Fake Top Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        )
                    }
                }
                
                // Fake Content
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Large Card Mockup
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                            )
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f))
                            )
                        }
                    }
                    
                    // Column of smaller items
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(3) { index ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (index == 0) 
                                        MaterialTheme.colorScheme.secondaryContainer 
                                    else MaterialTheme.colorScheme.surfaceContainerLowest
                                ),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (index == 0) MaterialTheme.colorScheme.onSecondaryContainer
                                                else MaterialTheme.colorScheme.outline
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(if (index == 1) 40.dp else 60.dp)
                                            .height(4.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (index == 0) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Preview Label
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                color = JellyfinExpressiveTheme.colors.previewBadgeContainer,
                contentColor = JellyfinExpressiveTheme.colors.previewBadgeContent,
                shape = JellyfinExpressiveTheme.shapes.pill,
                tonalElevation = 6.dp
            ) {
                Text(
                    text = "LIVE PREVIEW",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = appFont.toFontFamily()),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun FontPreview(
    fontFamily: FontFamily,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Aa",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = "Cinefin app font preview",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = fontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ThemeModeRow(
    selectedMode: ThemeMode,
    onModeSelect: (ThemeMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ThemeModeCard(
            mode = ThemeMode.LIGHT,
            icon = Icons.Default.LightMode,
            selected = selectedMode == ThemeMode.LIGHT,
            onClick = { onModeSelect(ThemeMode.LIGHT) },
            modifier = Modifier.weight(1f)
        )
        ThemeModeCard(
            mode = ThemeMode.DARK,
            icon = Icons.Default.DarkMode,
            selected = selectedMode == ThemeMode.DARK,
            onClick = { onModeSelect(ThemeMode.DARK) },
            modifier = Modifier.weight(1f)
        )
        ThemeModeCard(
            mode = ThemeMode.SYSTEM,
            icon = Icons.Default.Settings,
            selected = selectedMode == ThemeMode.SYSTEM,
            onClick = { onModeSelect(ThemeMode.SYSTEM) },
            modifier = Modifier.weight(1f)
        )
        ThemeModeCard(
            mode = ThemeMode.AMOLED_BLACK,
            icon = Icons.Default.Tonality,
            selected = selectedMode == ThemeMode.AMOLED_BLACK,
            onClick = { onModeSelect(ThemeMode.AMOLED_BLACK) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ThemeModeCard(
    mode: ThemeMode,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer 
                    else JellyfinExpressiveTheme.colors.sectionContainer,
        label = "container"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer 
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "content"
    )
    val borderStroke = if (selected) 2.dp else 1.dp
    val borderColor = if (selected) JellyfinExpressiveTheme.colors.selectionOutline
                    else MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier
            .clip(JellyfinExpressiveTheme.shapes.control)
            .background(containerColor)
            .border(borderStroke, borderColor, JellyfinExpressiveTheme.shapes.control)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = getThemeModeName(mode).split(" ").first(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun AccentColorRow(
    selectedColor: AccentColor,
    onColorSelect: (AccentColor) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
    ) {
        items(AccentColor.entries) { color ->
            AccentColorCircle(
                color = color,
                selected = selectedColor == color,
                onClick = { onColorSelect(color) }
            )
        }
    }
}

@Composable
private fun AccentColorCircle(
    color: AccentColor,
    selected: Boolean,
    onClick: () -> Unit
) {
    val previewColor = getAccentColorForPreview(color)
    val size by animateDpAsState(targetValue = if (selected) 56.dp else 48.dp, label = "size")
    val borderColor by animateColorAsState(
        targetValue = if (selected) JellyfinExpressiveTheme.colors.selectionOutline else Color.Transparent,
        label = "border"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(64.dp)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .shadow(if (selected) 8.dp else 2.dp, CircleShape)
                .clip(CircleShape)
                .background(previewColor)
                .then(
                    if (selected) Modifier.border(3.dp, borderColor, CircleShape)
                    else Modifier
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = if (previewColor.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            text = getAccentColorName(color).split(" ").last(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun ExpressiveSettingsCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ExpressiveContentCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = JellyfinExpressiveTheme.colors.sectionContainer,
        shape = JellyfinExpressiveTheme.shapes.section,
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
                        .clip(JellyfinExpressiveTheme.shapes.control)
                        .background(JellyfinExpressiveTheme.colors.sectionIconContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = JellyfinExpressiveTheme.colors.sectionIconContent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
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

private fun getAppFontName(font: AppFont): String {
    return when (font) {
        AppFont.DEFAULT -> "Default"
        AppFont.SANS_SERIF -> "Sans-Serif"
        AppFont.SERIF -> "Serif"
        AppFont.MONOSPACE -> "Monospace"
    }
}

private fun getAppFontDescription(font: AppFont): String {
    return when (font) {
        AppFont.DEFAULT -> "System default font"
        AppFont.SANS_SERIF -> "Clean and modern"
        AppFont.SERIF -> "Classic and elegant"
        AppFont.MONOSPACE -> "Fixed-width coding font"
    }
}
