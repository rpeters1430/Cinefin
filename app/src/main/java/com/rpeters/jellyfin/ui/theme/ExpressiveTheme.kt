package com.rpeters.jellyfin.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

@Immutable
data class JellyfinExpressiveColors(
    val sectionContainer: Color,
    val sectionContainerHigh: Color,
    val sectionIconContainer: Color,
    val sectionIconContent: Color,
    val selectionOutline: Color,
    val previewBadgeContainer: Color,
    val previewBadgeContent: Color,
)

@Immutable
data class JellyfinExpressiveShapes(
    val section: Shape,
    val control: Shape,
    val pill: Shape,
    val previewCard: Shape,
)

private val LocalJellyfinExpressiveColors = staticCompositionLocalOf<JellyfinExpressiveColors> {
    error("Jellyfin expressive colors not provided")
}

private val LocalJellyfinExpressiveShapes = staticCompositionLocalOf<JellyfinExpressiveShapes> {
    error("Jellyfin expressive shapes not provided")
}

object JellyfinExpressiveTheme {
    val colors: JellyfinExpressiveColors
        @Composable
        @ReadOnlyComposable
        get() = LocalJellyfinExpressiveColors.current

    val shapes: JellyfinExpressiveShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalJellyfinExpressiveShapes.current
}

@Composable
internal fun ProvideJellyfinExpressiveTheme(
    content: @Composable () -> Unit,
) {
    val colorScheme = androidx.compose.material3.MaterialTheme.colorScheme
    val colors = JellyfinExpressiveColors(
        sectionContainer = colorScheme.surfaceContainerLow,
        sectionContainerHigh = colorScheme.surfaceContainerHigh,
        sectionIconContainer = colorScheme.primaryContainer,
        sectionIconContent = colorScheme.onPrimaryContainer,
        selectionOutline = colorScheme.primary,
        previewBadgeContainer = colorScheme.primary,
        previewBadgeContent = colorScheme.onPrimary,
    )
    val shapes = JellyfinExpressiveShapes(
        section = ShapeTokens.ExtraLarge,
        control = ShapeTokens.Medium,
        pill = ShapeTokens.Full,
        previewCard = ShapeTokens.ExtraLarge,
    )

    CompositionLocalProvider(
        LocalJellyfinExpressiveColors provides colors,
        LocalJellyfinExpressiveShapes provides shapes,
        LocalSpacing provides Spacing(),
        content = content,
    )
}
