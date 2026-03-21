package com.rpeters.jellyfin.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
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

@Immutable
data class CinefinTvLayoutTokens(
    val drawerWidth: Dp = 300.dp,
    val drawerPadding: Dp = 24.dp,
    val drawerItemSpacing: Dp = 14.dp,
    val screenHorizontalPadding: Dp = 72.dp,
    val screenTopPadding: Dp = 56.dp,
    val sectionSpacing: Dp = 28.dp,
    val cardSpacing: Dp = 24.dp,
    val heroBottomSpacing: Dp = 32.dp,
    val contentBottomPadding: Dp = 56.dp,
    val formMaxWidthFraction: Float = 0.68f,
    val formFieldHeight: Dp = 72.dp,
)

private val LocalCinefinTvLayoutTokens = staticCompositionLocalOf { CinefinTvLayoutTokens() }

object CinefinTvTheme {
    val layout: CinefinTvLayoutTokens
        @Composable
        get() = LocalCinefinTvLayoutTokens.current
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

@Composable
fun CinefinTvTheme(
    accentColor: com.rpeters.jellyfin.data.preferences.AccentColor = com.rpeters.jellyfin.data.preferences.AccentColor.JELLYFIN_PURPLE,
    content: @Composable () -> Unit,
) {
    TvMaterialTheme(colorScheme = cinefinTvColorScheme(accentColor = accentColor)) {
        CompositionLocalProvider(
            LocalCinefinTvLayoutTokens provides CinefinTvLayoutTokens(),
            content = content,
        )
    }
}

/**
 * Returns a TV Material 3 color scheme based on user preferences.
 *
 * TV is always displayed in dark mode (lean-back / 10-foot environment).
 * Bright purple/blue/teal variants are chosen so they read clearly against
 * the dark [Neutral10] background at typical viewing distances.
 */
@Composable
fun cinefinTvColorScheme(
    accentColor: com.rpeters.jellyfin.data.preferences.AccentColor = com.rpeters.jellyfin.data.preferences.AccentColor.JELLYFIN_PURPLE
): androidx.tv.material3.ColorScheme {
    // Get the base color scheme from our standard M3 schemes
    val baseScheme = getDarkColorScheme(accentColor)
    
    return tvDarkColorScheme(
        primary = baseScheme.primary,
        onPrimary = baseScheme.onPrimary,
        primaryContainer = baseScheme.primaryContainer,
        onPrimaryContainer = baseScheme.onPrimaryContainer,
        secondary = baseScheme.secondary,
        onSecondary = baseScheme.onSecondary,
        secondaryContainer = baseScheme.secondaryContainer,
        onSecondaryContainer = baseScheme.onSecondaryContainer,
        tertiary = baseScheme.tertiary,
        onTertiary = baseScheme.onTertiary,
        tertiaryContainer = baseScheme.tertiaryContainer,
        onTertiaryContainer = baseScheme.onTertiaryContainer,
        background = Neutral10, // Maintain deep dark background for TV
        onBackground = Color(0xFFE6E1E6),
        surface = Neutral10,
        onSurface = Color(0xFFE6E1E6),
        surfaceVariant = Neutral20,
        onSurfaceVariant = Color(0xFFCAC5CA),
        border = Neutral30,
        borderVariant = Neutral40,
        error = baseScheme.error,
        onError = baseScheme.onError,
        errorContainer = baseScheme.errorContainer,
        onErrorContainer = baseScheme.onErrorContainer,
    )
}
