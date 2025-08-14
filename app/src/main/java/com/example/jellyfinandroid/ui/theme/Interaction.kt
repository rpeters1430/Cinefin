package com.example.jellyfinandroid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Material 3 State Layer tokens for interactive components
 * Provides consistent interaction feedback across the app
 */
object StateLayerTokens {

    // State layer opacity values from Material 3 spec
    const val DraggedOpacity = 0.16f
    const val FocusOpacity = 0.12f
    const val HoverOpacity = 0.08f
    const val PressedOpacity = 0.12f

    // Disabled state opacity
    const val DisabledContainerOpacity = 0.12f
    const val DisabledContentOpacity = 0.38f
}

/**
 * Extension functions for easy access to state layer colors
 */
@Composable
@ReadOnlyComposable
fun Color.withStateLayer(opacity: Float): Color {
    return this.copy(alpha = opacity)
}

/**
 * Material 3 interaction colors for consistent feedback
 */
object InteractionTokens {

    @Composable
    @ReadOnlyComposable
    fun onSurfacePressed(): Color = MaterialTheme.colorScheme.onSurface.withStateLayer(StateLayerTokens.PressedOpacity)

    @Composable
    @ReadOnlyComposable
    fun onSurfaceHover(): Color = MaterialTheme.colorScheme.onSurface.withStateLayer(StateLayerTokens.HoverOpacity)

    @Composable
    @ReadOnlyComposable
    fun onSurfaceFocus(): Color = MaterialTheme.colorScheme.onSurface.withStateLayer(StateLayerTokens.FocusOpacity)

    @Composable
    @ReadOnlyComposable
    fun primaryPressed(): Color = MaterialTheme.colorScheme.primary.withStateLayer(StateLayerTokens.PressedOpacity)

    @Composable
    @ReadOnlyComposable
    fun primaryHover(): Color = MaterialTheme.colorScheme.primary.withStateLayer(StateLayerTokens.HoverOpacity)

    @Composable
    @ReadOnlyComposable
    fun primaryFocus(): Color = MaterialTheme.colorScheme.primary.withStateLayer(StateLayerTokens.FocusOpacity)
}
