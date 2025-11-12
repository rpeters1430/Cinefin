package com.rpeters.jellyfin.ui.theme

import androidx.compose.ui.graphics.Color
import com.rpeters.jellyfin.data.preferences.AccentColor

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
fun getThemeModeName(themeMode: com.rpeters.jellyfin.data.preferences.ThemeMode): String {
    return when (themeMode) {
        com.rpeters.jellyfin.data.preferences.ThemeMode.SYSTEM -> "System Default"
        com.rpeters.jellyfin.data.preferences.ThemeMode.LIGHT -> "Light"
        com.rpeters.jellyfin.data.preferences.ThemeMode.DARK -> "Dark"
        com.rpeters.jellyfin.data.preferences.ThemeMode.AMOLED_BLACK -> "AMOLED Black"
    }
}

/**
 * Get a display name for a contrast level.
 */
fun getContrastLevelName(contrastLevel: com.rpeters.jellyfin.data.preferences.ContrastLevel): String {
    return when (contrastLevel) {
        com.rpeters.jellyfin.data.preferences.ContrastLevel.STANDARD -> "Standard"
        com.rpeters.jellyfin.data.preferences.ContrastLevel.MEDIUM -> "Medium"
        com.rpeters.jellyfin.data.preferences.ContrastLevel.HIGH -> "High"
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
