package com.rpeters.jellyfin.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * A surface that provides a semi-transparent frosted appearance.
 * On Android 12+ (API 31+), a hardware-accelerated RenderEffect blur is applied
 * and clipped to the component's boundaries using graphicsLayer to prevent color bleeding.
 * On older devices, it gracefully falls back to a clean translucent surface color.
 *
 * @param modifier The modifier to be applied to the surface.
 * @param shape The shape of the surface.
 * @param color The background color (should be translucent for best effect).
 * @param content The content to be displayed on the surface.
 */
@Composable
fun ExpressiveBlurSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        // Background blurred layer
        Surface(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = android.graphics.RenderEffect.createBlurEffect(
                            20f,
                            20f,
                            android.graphics.Shader.TileMode.CLAMP
                        ).asComposeRenderEffect()
                    }
                    clip = true
                    this.shape = shape
                },
            shape = shape,
            color = color,
            content = {}
        )
        
        // Unblurred content layer on top
        Box(
            modifier = Modifier,
            content = content
        )
    }
}

