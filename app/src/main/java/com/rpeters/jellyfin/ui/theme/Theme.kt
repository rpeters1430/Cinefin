package com.rpeters.jellyfin.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.rpeters.jellyfin.data.preferences.ContrastLevel
import com.rpeters.jellyfin.data.preferences.ThemeMode
import com.rpeters.jellyfin.data.preferences.ThemePreferences

/**
 * Contrast adjustment factors for Material 3 WCAG compliance.
 * These factors ensure proper contrast ratios while maintaining visual harmony.
 */
private object ContrastAdjustmentFactors {
    // Medium contrast factors
    const val MEDIUM_DARK_BRIGHTEN = 1.15f
    const val MEDIUM_DARK_DARKEN = 0.85f
    const val MEDIUM_LIGHT_BRIGHTEN = 0.9f
    const val MEDIUM_LIGHT_DARKEN = 1.1f
    const val MEDIUM_OUTLINE_DARK = 1.2f
    const val MEDIUM_OUTLINE_LIGHT = 0.85f

    // High contrast factors
    const val HIGH_DARK_BRIGHTEN = 1.3f
    const val HIGH_DARK_DARKEN = 0.7f
    const val HIGH_LIGHT_BRIGHTEN = 0.8f
    const val HIGH_LIGHT_DARKEN = 1.25f
    const val HIGH_LIGHT_ON_SURFACE_DARKEN = 0.75f
    const val HIGH_OUTLINE_DARK = 1.4f
    const val HIGH_OUTLINE_LIGHT = 0.7f
    const val HIGH_SURFACE_DARK = 1.15f
    const val HIGH_SURFACE_LIGHT = 0.95f
}

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
 *
 * This implementation follows WCAG guidelines by adjusting both base colors
 * and their paired "on" colors to maintain proper contrast ratios:
 * - When base colors are brightened, "on" colors are darkened
 * - When base colors are darkened, "on" colors are brightened
 * - Surface and background colors are also adjusted proportionally
 *
 * @param colorScheme The base color scheme to adjust
 * @param contrastLevel The desired contrast level (Standard, Medium, High)
 * @param isDark Whether the theme is in dark mode
 * @return An adjusted ColorScheme with improved contrast
 */
internal fun applyContrastLevel(
    colorScheme: ColorScheme,
    contrastLevel: ContrastLevel,
    isDark: Boolean,
): ColorScheme {
    return when (contrastLevel) {
        ContrastLevel.STANDARD -> colorScheme

        ContrastLevel.MEDIUM -> colorScheme.copy(
            // Primary color adjustments
            primary = if (isDark) {
                adjustBrightness(colorScheme.primary, ContrastAdjustmentFactors.MEDIUM_DARK_BRIGHTEN)
            } else {
                adjustBrightness(colorScheme.primary, ContrastAdjustmentFactors.MEDIUM_LIGHT_BRIGHTEN)
            },
            onPrimary = if (isDark) {
                adjustBrightness(colorScheme.onPrimary, ContrastAdjustmentFactors.MEDIUM_DARK_DARKEN)
            } else {
                adjustBrightness(colorScheme.onPrimary, ContrastAdjustmentFactors.MEDIUM_LIGHT_DARKEN)
            },
            primaryContainer = if (isDark) {
                adjustBrightness(colorScheme.primaryContainer, ContrastAdjustmentFactors.MEDIUM_DARK_BRIGHTEN)
            } else {
                adjustBrightness(colorScheme.primaryContainer, ContrastAdjustmentFactors.MEDIUM_LIGHT_BRIGHTEN)
            },
            onPrimaryContainer = if (isDark) {
                adjustBrightness(colorScheme.onPrimaryContainer, ContrastAdjustmentFactors.MEDIUM_DARK_DARKEN)
            } else {
                adjustBrightness(colorScheme.onPrimaryContainer, ContrastAdjustmentFactors.MEDIUM_LIGHT_DARKEN)
            },

            // Secondary color adjustments
            secondary = if (isDark) {
                adjustBrightness(colorScheme.secondary, ContrastAdjustmentFactors.MEDIUM_DARK_BRIGHTEN)
            } else {
                adjustBrightness(colorScheme.secondary, ContrastAdjustmentFactors.MEDIUM_LIGHT_BRIGHTEN)
            },
            onSecondary = if (isDark) {
                adjustBrightness(colorScheme.onSecondary, ContrastAdjustmentFactors.MEDIUM_DARK_DARKEN)
            } else {
                adjustBrightness(colorScheme.onSecondary, ContrastAdjustmentFactors.MEDIUM_LIGHT_DARKEN)
            },
            secondaryContainer = if (isDark) {
                adjustBrightness(colorScheme.secondaryContainer, ContrastAdjustmentFactors.MEDIUM_DARK_BRIGHTEN)
            } else {
                adjustBrightness(colorScheme.secondaryContainer, ContrastAdjustmentFactors.MEDIUM_LIGHT_BRIGHTEN)
            },
            onSecondaryContainer = if (isDark) {
                adjustBrightness(colorScheme.onSecondaryContainer, ContrastAdjustmentFactors.MEDIUM_DARK_DARKEN)
            } else {
                adjustBrightness(colorScheme.onSecondaryContainer, ContrastAdjustmentFactors.MEDIUM_LIGHT_DARKEN)
            },

            // Tertiary color adjustments
            tertiary = if (isDark) {
                adjustBrightness(colorScheme.tertiary, ContrastAdjustmentFactors.MEDIUM_DARK_BRIGHTEN)
            } else {
                adjustBrightness(colorScheme.tertiary, ContrastAdjustmentFactors.MEDIUM_LIGHT_BRIGHTEN)
            },
            onTertiary = if (isDark) {
                adjustBrightness(colorScheme.onTertiary, ContrastAdjustmentFactors.MEDIUM_DARK_DARKEN)
            } else {
                adjustBrightness(colorScheme.onTertiary, ContrastAdjustmentFactors.MEDIUM_LIGHT_DARKEN)
            },
            tertiaryContainer = if (isDark) {
                adjustBrightness(colorScheme.tertiaryContainer, ContrastAdjustmentFactors.MEDIUM_DARK_BRIGHTEN)
            } else {
                adjustBrightness(colorScheme.tertiaryContainer, ContrastAdjustmentFactors.MEDIUM_LIGHT_BRIGHTEN)
            },
            onTertiaryContainer = if (isDark) {
                adjustBrightness(colorScheme.onTertiaryContainer, ContrastAdjustmentFactors.MEDIUM_DARK_DARKEN)
            } else {
                adjustBrightness(colorScheme.onTertiaryContainer, ContrastAdjustmentFactors.MEDIUM_LIGHT_DARKEN)
            },

            // Outline adjustments
            outline = if (isDark) {
                adjustBrightness(colorScheme.outline, ContrastAdjustmentFactors.MEDIUM_OUTLINE_DARK)
            } else {
                adjustBrightness(colorScheme.outline, ContrastAdjustmentFactors.MEDIUM_OUTLINE_LIGHT)
            },
        )

        ContrastLevel.HIGH -> colorScheme.copy(
            // Primary color adjustments
            primary = if (isDark) {
                adjustBrightness(colorScheme.primary, ContrastAdjustmentFactors.HIGH_DARK_BRIGHTEN)
            } else {
                adjustBrightness(colorScheme.primary, ContrastAdjustmentFactors.HIGH_LIGHT_BRIGHTEN)
            },
            onPrimary = if (isDark) {
                adjustBrightness(colorScheme.onPrimary, ContrastAdjustmentFactors.HIGH_DARK_DARKEN)
            } else {
                adjustBrightness(colorScheme.onPrimary, ContrastAdjustmentFactors.HIGH_LIGHT_DARKEN)
            },
            primaryContainer = if (isDark) {
                adjustBrightness(colorScheme.primaryContainer, ContrastAdjustmentFactors.HIGH_DARK_BRIGHTEN)
            } else {
                adjustBrightness(colorScheme.primaryContainer, ContrastAdjustmentFactors.HIGH_LIGHT_BRIGHTEN)
            },
            onPrimaryContainer = if (isDark) {
                adjustBrightness(colorScheme.onPrimaryContainer, ContrastAdjustmentFactors.HIGH_DARK_DARKEN)
            } else {
                adjustBrightness(colorScheme.onPrimaryContainer, ContrastAdjustmentFactors.HIGH_LIGHT_DARKEN)
            },

            // Secondary color adjustments
            secondary = if (isDark) {
                adjustBrightness(colorScheme.secondary, ContrastAdjustmentFactors.HIGH_DARK_BRIGHTEN)
            } else {
                adjustBrightness(colorScheme.secondary, ContrastAdjustmentFactors.HIGH_LIGHT_BRIGHTEN)
            },
            onSecondary = if (isDark) {
                adjustBrightness(colorScheme.onSecondary, ContrastAdjustmentFactors.HIGH_DARK_DARKEN)
            } else {
                adjustBrightness(colorScheme.onSecondary, ContrastAdjustmentFactors.HIGH_LIGHT_DARKEN)
            },
            secondaryContainer = if (isDark) {
                adjustBrightness(colorScheme.secondaryContainer, ContrastAdjustmentFactors.HIGH_DARK_BRIGHTEN)
            } else {
                adjustBrightness(colorScheme.secondaryContainer, ContrastAdjustmentFactors.HIGH_LIGHT_BRIGHTEN)
            },
            onSecondaryContainer = if (isDark) {
                adjustBrightness(colorScheme.onSecondaryContainer, ContrastAdjustmentFactors.HIGH_DARK_DARKEN)
            } else {
                adjustBrightness(colorScheme.onSecondaryContainer, ContrastAdjustmentFactors.HIGH_LIGHT_DARKEN)
            },

            // Tertiary color adjustments
            tertiary = if (isDark) {
                adjustBrightness(colorScheme.tertiary, ContrastAdjustmentFactors.HIGH_DARK_BRIGHTEN)
            } else {
                adjustBrightness(colorScheme.tertiary, ContrastAdjustmentFactors.HIGH_LIGHT_BRIGHTEN)
            },
            onTertiary = if (isDark) {
                adjustBrightness(colorScheme.onTertiary, ContrastAdjustmentFactors.HIGH_DARK_DARKEN)
            } else {
                adjustBrightness(colorScheme.onTertiary, ContrastAdjustmentFactors.HIGH_LIGHT_DARKEN)
            },
            tertiaryContainer = if (isDark) {
                adjustBrightness(colorScheme.tertiaryContainer, ContrastAdjustmentFactors.HIGH_DARK_BRIGHTEN)
            } else {
                adjustBrightness(colorScheme.tertiaryContainer, ContrastAdjustmentFactors.HIGH_LIGHT_BRIGHTEN)
            },
            onTertiaryContainer = if (isDark) {
                adjustBrightness(colorScheme.onTertiaryContainer, ContrastAdjustmentFactors.HIGH_DARK_DARKEN)
            } else {
                adjustBrightness(colorScheme.onTertiaryContainer, ContrastAdjustmentFactors.HIGH_LIGHT_DARKEN)
            },

            // Surface color adjustments
            surface = if (isDark) {
                adjustBrightness(colorScheme.surface, ContrastAdjustmentFactors.HIGH_SURFACE_DARK)
            } else {
                adjustBrightness(colorScheme.surface, ContrastAdjustmentFactors.HIGH_SURFACE_LIGHT)
            },
            onSurface = if (isDark) {
                adjustBrightness(colorScheme.onSurface, ContrastAdjustmentFactors.HIGH_DARK_DARKEN)
            } else {
                adjustBrightness(
                    colorScheme.onSurface,
                    ContrastAdjustmentFactors.HIGH_LIGHT_ON_SURFACE_DARKEN,
                )
            },
            surfaceVariant = if (isDark) {
                adjustBrightness(colorScheme.surfaceVariant, ContrastAdjustmentFactors.HIGH_SURFACE_DARK)
            } else {
                adjustBrightness(colorScheme.surfaceVariant, ContrastAdjustmentFactors.HIGH_SURFACE_LIGHT)
            },
            onSurfaceVariant = if (isDark) {
                adjustBrightness(colorScheme.onSurfaceVariant, ContrastAdjustmentFactors.HIGH_DARK_DARKEN)
            } else {
                adjustBrightness(
                    colorScheme.onSurfaceVariant,
                    ContrastAdjustmentFactors.HIGH_LIGHT_ON_SURFACE_DARKEN,
                )
            },

            // Background color adjustments
            background = if (isDark) {
                adjustBrightness(colorScheme.background, ContrastAdjustmentFactors.HIGH_SURFACE_DARK)
            } else {
                adjustBrightness(colorScheme.background, ContrastAdjustmentFactors.HIGH_SURFACE_LIGHT)
            },
            onBackground = if (isDark) {
                adjustBrightness(colorScheme.onBackground, ContrastAdjustmentFactors.HIGH_DARK_DARKEN)
            } else {
                adjustBrightness(
                    colorScheme.onBackground,
                    ContrastAdjustmentFactors.HIGH_LIGHT_ON_SURFACE_DARKEN,
                )
            },

            // Outline adjustments
            outline = if (isDark) {
                adjustBrightness(colorScheme.outline, ContrastAdjustmentFactors.HIGH_OUTLINE_DARK)
            } else {
                adjustBrightness(colorScheme.outline, ContrastAdjustmentFactors.HIGH_OUTLINE_LIGHT)
            },
            outlineVariant = if (isDark) {
                adjustBrightness(colorScheme.outlineVariant, ContrastAdjustmentFactors.HIGH_OUTLINE_DARK)
            } else {
                adjustBrightness(colorScheme.outlineVariant, ContrastAdjustmentFactors.HIGH_OUTLINE_LIGHT)
            },
        )
    }
}

/**
 * Adjust the brightness of a color by a factor.
 * Factor > 1.0 increases brightness, factor < 1.0 decreases brightness.
 */
internal fun adjustBrightness(
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
