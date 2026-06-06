package com.rpeters.jellyfin.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A surface that provides a semi-transparent frosted appearance.
 * On Android 12+ (API 31+), a hardware-accelerated RenderEffect blur is applied
 * and clipped to the component's boundaries using graphicsLayer to prevent color bleeding.
 *
 * Features a dynamic, light-reflecting glass border and a premium depth glow.
 * On older devices, it gracefully falls back to a clean translucent surface color.
 *
 * @param modifier The modifier to be applied to the surface.
 * @param shape The shape of the surface.
 * @param color The background color (should be translucent for best effect).
 * @param border Optional custom border stroke. If null, a premium adaptive glass border is applied.
 * @param borderRadius Custom corner radius for the drop shadow glow.
 * @param content The content to be displayed on the surface.
 */
@Composable
fun ExpressiveBlurSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    border: BorderStroke? = null,
    borderRadius: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val resolvedBorder = border ?: remember(onSurfaceColor) {
        BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    onSurfaceColor.copy(alpha = 0.15f),
                    onSurfaceColor.copy(alpha = 0.02f)
                )
            )
        )
    }

    Box(
        modifier = modifier
            .expressiveGlow(
                color = Color.Black,
                alpha = 0.06f,
                borderRadius = borderRadius
            )
    ) {
        // Background blurred layer
        Surface(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = android.graphics.RenderEffect.createBlurEffect(
                            25f,
                            25f,
                            android.graphics.Shader.TileMode.CLAMP
                        ).asComposeRenderEffect()
                    }
                    clip = true
                    this.shape = shape
                },
            shape = shape,
            color = color,
            border = resolvedBorder,
            content = {}
        )
        
        // Unblurred content layer on top
        Box(
            modifier = Modifier,
            content = content
        )
    }
}
