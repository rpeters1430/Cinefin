package com.rpeters.jellyfin.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
import androidx.tv.material3.MaterialTheme as TvMaterialTheme

/**
 * TV-specific design tokens and constants.
 */
object TvThemeTokens {
    /**
     * Standard scale factor when an element is focused on TV.
     */
    const val FocusedScale = 1.1f
    
    /**
     * Standard border width for focused elements.
     */
    val FocusedBorderWidth = 2.dp
    
    /**
     * Standard glow/halo radius for focused elements.
     */
    val FocusedGlowRadius = 8.dp
}

/**
 * Modifier that applies a standard focus border and scale effect for TV components.
 * Useful for custom components that don't use standard TV Material surfaces.
 */
@Composable
fun Modifier.tvFocusDesign(
    isFocused: Boolean,
    shape: Shape = TvMaterialTheme.shapes.medium,
    focusedBorderColor: Color = TvMaterialTheme.colorScheme.primary,
    focusedScale: Float = TvThemeTokens.FocusedScale,
): Modifier {
    return this
        .scale(if (isFocused) focusedScale else 1.0f)
        .border(
            width = if (isFocused) TvThemeTokens.FocusedBorderWidth else 0.dp,
            color = if (isFocused) focusedBorderColor else Color.Transparent,
            shape = shape
        )
}

/**
 * Provides standard TV focus properties for Material 3 TV components.
 */
@Composable
fun standardTvClickableSurfaceColors() = ClickableSurfaceDefaults.colors(
    containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
    focusedContainerColor = TvMaterialTheme.colorScheme.surface,
    pressedContainerColor = TvMaterialTheme.colorScheme.surfaceVariant,
)

/**
 * Provides standard TV focus border for Material 3 TV components.
 */
@Composable
fun standardTvFocusBorder() = Border(
    border = BorderStroke(
        width = TvThemeTokens.FocusedBorderWidth,
        color = TvMaterialTheme.colorScheme.primary
    )
)

/**
 * Returns a TV Material 3 color scheme using Cinefin brand colors.
 *
 * TV is always displayed in dark mode (lean-back / 10-foot environment).
 * Bright purple/blue/teal variants are chosen so they read clearly against
 * the dark [Neutral10] background at typical viewing distances.
 */
fun cinefinTvColorScheme() = tvDarkColorScheme(
    primary = JellyfinPurple80,
    onPrimary = Color(0xFF000000),
    primaryContainer = JellyfinPurple30,
    onPrimaryContainer = JellyfinPurple80,
    secondary = JellyfinBlue80,
    onSecondary = Color(0xFF000000),
    secondaryContainer = JellyfinBlue30,
    onSecondaryContainer = JellyfinBlue80,
    tertiary = JellyfinTeal80,
    onTertiary = Color(0xFF000000),
    tertiaryContainer = JellyfinTeal30,
    onTertiaryContainer = JellyfinTeal80,
    background = Neutral10,
    onBackground = Color(0xFFE6E1E6),
    surface = Neutral10,
    onSurface = Color(0xFFE6E1E6),
    surfaceVariant = Neutral20,
    onSurfaceVariant = Color(0xFFCAC5CA),
    border = Neutral30,
    borderVariant = Neutral40,
    error = Color(0xFFCF6679),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)
