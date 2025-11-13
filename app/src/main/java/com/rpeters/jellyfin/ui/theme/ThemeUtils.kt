package com.rpeters.jellyfin.ui.theme

import androidx.compose.ui.graphics.Color
import com.rpeters.jellyfin.data.preferences.AccentColor
import com.rpeters.jellyfin.data.preferences.ContrastLevel
import com.rpeters.jellyfin.data.preferences.ThemeMode

/**
 * Get a preview color for an accent color selection.
 * Used in the accent color picker to show what each color looks like.
 */
fun getAccentColorForPreview(accentColor: AccentColor): Color {
    return when (accentColor) {
        AccentColor.JELLYFIN_PURPLE -> JellyfinPurple40
        AccentColor.JELLYFIN_BLUE -> JellyfinBlue40
        AccentColor.JELLYFIN_TEAL -> JellyfinTeal40
        AccentColor.MATERIAL_PURPLE -> Color(0xFF6750A4)
        AccentColor.MATERIAL_BLUE -> Color(0xFF0061A4)
        AccentColor.MATERIAL_GREEN -> Color(0xFF006E1C)
        AccentColor.MATERIAL_RED -> Color(0xFFB3261E)
        AccentColor.MATERIAL_ORANGE -> Color(0xFF825500)
    }
}

/**
 * Get a display name for a theme mode.
 */
fun getThemeModeName(themeMode: ThemeMode): String {
    return when (themeMode) {
        ThemeMode.SYSTEM -> "System Default"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
        ThemeMode.AMOLED_BLACK -> "AMOLED Black"
    }
}

/**
 * Get a description for a theme mode.
 */
fun getThemeModeDescription(themeMode: ThemeMode): String {
    return when (themeMode) {
        ThemeMode.SYSTEM -> "Follow system theme setting"
        ThemeMode.LIGHT -> "Always use light theme"
        ThemeMode.DARK -> "Always use dark theme"
        ThemeMode.AMOLED_BLACK -> "Pure black for OLED screens"
    }
}

/**
 * Get a display name for a contrast level.
 */
fun getContrastLevelName(contrastLevel: ContrastLevel): String {
    return when (contrastLevel) {
        ContrastLevel.STANDARD -> "Standard"
        ContrastLevel.MEDIUM -> "Medium"
        ContrastLevel.HIGH -> "High"
    }
}

/**
 * Get a description for a contrast level.
 */
fun getContrastLevelDescription(contrastLevel: ContrastLevel): String {
    return when (contrastLevel) {
        ContrastLevel.STANDARD -> "Default contrast level"
        ContrastLevel.MEDIUM -> "Increased contrast for better readability"
        ContrastLevel.HIGH -> "Maximum contrast for accessibility"
    }
}

/**
 * Get a display name for an accent color.
 */
fun getAccentColorName(accentColor: AccentColor): String {
    return when (accentColor) {
        AccentColor.JELLYFIN_PURPLE -> "Jellyfin Purple"
        AccentColor.JELLYFIN_BLUE -> "Jellyfin Blue"
        AccentColor.JELLYFIN_TEAL -> "Jellyfin Teal"
        AccentColor.MATERIAL_PURPLE -> "Material Purple"
        AccentColor.MATERIAL_BLUE -> "Material Blue"
        AccentColor.MATERIAL_GREEN -> "Material Green"
        AccentColor.MATERIAL_RED -> "Material Red"
        AccentColor.MATERIAL_ORANGE -> "Material Orange"
    }
}
