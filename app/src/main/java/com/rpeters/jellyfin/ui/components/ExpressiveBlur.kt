package com.rpeters.jellyfin.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A modifier that applies a real-time blur effect on Android 12+ (API 31).
 * Falls back to no effect on older versions.
 * 
 * @param radius The radius of the blur.
 */
fun Modifier.expressiveBlur(radius: Dp = 20.dp): Modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    this.blur(radius)
} else {
    this
}

/**
 * A surface that provides a "glassmorphism" effect using real-time blur on Android 12+.
 * 
 * @param modifier The modifier to be applied to the surface.
 * @param shape The shape of the surface.
 * @param color The background color (should be translucent for best effect).
 * @param blurRadius The radius of the blur effect.
 * @param content The content to be displayed on the surface.
 */
@Composable
fun ExpressiveBlurSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    blurRadius: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        // Blur layer (only on Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .blur(blurRadius)
                    .background(color.copy(alpha = 0.4f)) // Extra darkening for blur layer
            )
        }
        
        // Surface layer
        Surface(
            modifier = Modifier.matchParentSize(),
            shape = shape,
            color = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Color.Transparent else color,
            content = {}
        )
        
        // Content layer
        Box(
            modifier = Modifier.padding(0.dp) // Reset padding
        ) {
            content()
        }
    }
}
