package com.rpeters.jellyfin.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.rpeters.jellyfin.data.preferences.AccentColor
import com.rpeters.jellyfin.data.preferences.ContrastLevel
import com.rpeters.jellyfin.data.preferences.ThemeMode
import com.rpeters.jellyfin.data.preferences.ThemePreferences

/**
 * Jellyfin application's Material 3 theme with full Material You support.
 *
 * This theme supports:
 * - Dynamic colors from system wallpaper (Android 12+)
 * - Multiple theme modes (System, Light, Dark, AMOLED Black)
 * - Custom accent colors when dynamic colors are disabled
 * - Contrast level adjustments (Standard, Medium, High)
 * - Edge-to-edge layout support
 *
 * @param themePreferences User's theme preferences
 * @param content Composable content to display inside the theme
 */
@Composable
fun JellyfinAndroidTheme(
    themePreferences: ThemePreferences = ThemePreferences.DEFAULT,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    // Determine if dark theme should be used
    val darkTheme = when (themePreferences.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED_BLACK -> true
    }

    // Select the appropriate color scheme
    val colorScheme = when {
        // Use dynamic colors if enabled and supported (Android 12+)
        themePreferences.useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (themePreferences.themeMode == ThemeMode.AMOLED_BLACK) {
                // Apply AMOLED black background to dynamic colors
                applyAmoledBlackToDynamicColors(dynamicDarkColorScheme(context))
            } else if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }

        // Use AMOLED Black theme
        themePreferences.themeMode == ThemeMode.AMOLED_BLACK -> {
            getAmoledBlackColorScheme(themePreferences.accentColor)
        }

        // Use custom accent color schemes
        darkTheme -> {
            getDarkColorScheme(themePreferences.accentColor)
        }

        else -> {
            getLightColorScheme(themePreferences.accentColor)
        }
    }

    // Apply contrast level adjustments
    val adjustedColorScheme = applyContrastLevel(
        colorScheme = colorScheme,
        contrastLevel = themePreferences.contrastLevel,
        isDark = darkTheme,
    )

    MaterialTheme(
        colorScheme = adjustedColorScheme,
        typography = Typography,
        shapes = JellyfinShapes,
        content = content,
    )
}

/**
 * Legacy theme function for backward compatibility.
 * This maintains the original signature but internally converts to ThemePreferences.
 *
 * @param darkTheme whether to use the dark color scheme
 * @param dynamicColor whether to use dynamic color on Android 12+ devices
 * @param content composable content to display inside the theme
 */
@Composable
fun JellyfinAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val themePreferences = ThemePreferences(
        themeMode = when {
            darkTheme -> ThemeMode.DARK
            else -> ThemeMode.LIGHT
        },
        useDynamicColors = dynamicColor,
    )

    JellyfinAndroidTheme(
        themePreferences = themePreferences,
        content = content,
    )
}

/**
 * Apply AMOLED black background to dynamic color scheme.
 * Replaces background and surface colors with pure black.
 */
private fun applyAmoledBlackToDynamicColors(colorScheme: ColorScheme): ColorScheme {
    return colorScheme.copy(
        background = androidx.compose.ui.graphics.Color.Black,
        surface = androidx.compose.ui.graphics.Color.Black,
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
        surfaceContainerLowest = androidx.compose.ui.graphics.Color.Black,
        surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF0D0D0D),
        surfaceContainer = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
        surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF262626),
        surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF333333),
    )
}

/**
 * Apply contrast level adjustments to color scheme.
 * Increases color contrast for better readability and accessibility.
 */
private fun applyContrastLevel(
    colorScheme: ColorScheme,
    contrastLevel: ContrastLevel,
    isDark: Boolean,
): ColorScheme {
    return when (contrastLevel) {
        ContrastLevel.STANDARD -> colorScheme

        ContrastLevel.MEDIUM -> colorScheme.copy(
            primary = if (isDark) {
                adjustBrightness(colorScheme.primary, 1.1f)
            } else {
                adjustBrightness(colorScheme.primary, 0.9f)
            },
            secondary = if (isDark) {
                adjustBrightness(colorScheme.secondary, 1.1f)
            } else {
                adjustBrightness(colorScheme.secondary, 0.9f)
            },
            tertiary = if (isDark) {
                adjustBrightness(colorScheme.tertiary, 1.1f)
            } else {
                adjustBrightness(colorScheme.tertiary, 0.9f)
            },
        )

        ContrastLevel.HIGH -> colorScheme.copy(
            primary = if (isDark) {
                adjustBrightness(colorScheme.primary, 1.2f)
            } else {
                adjustBrightness(colorScheme.primary, 0.8f)
            },
            secondary = if (isDark) {
                adjustBrightness(colorScheme.secondary, 1.2f)
            } else {
                adjustBrightness(colorScheme.secondary, 0.8f)
            },
            tertiary = if (isDark) {
                adjustBrightness(colorScheme.tertiary, 1.2f)
            } else {
                adjustBrightness(colorScheme.tertiary, 0.8f)
            },
            outline = if (isDark) {
                adjustBrightness(colorScheme.outline, 1.3f)
            } else {
                adjustBrightness(colorScheme.outline, 0.7f)
            },
        )
    }
}

/**
 * Adjust the brightness of a color by a factor.
 * Factor > 1.0 increases brightness, factor < 1.0 decreases brightness.
 */
private fun adjustBrightness(
    color: androidx.compose.ui.graphics.Color,
    factor: Float,
): androidx.compose.ui.graphics.Color {
    return androidx.compose.ui.graphics.Color(
        red = (color.red * factor).coerceIn(0f, 1f),
        green = (color.green * factor).coerceIn(0f, 1f),
        blue = (color.blue * factor).coerceIn(0f, 1f),
        alpha = color.alpha,
    )
}
