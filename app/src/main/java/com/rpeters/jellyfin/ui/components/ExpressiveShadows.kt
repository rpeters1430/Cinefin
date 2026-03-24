package com.rpeters.jellyfin.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Expressive glow modifier that adds a soft colored shadow/glow around a component.
 * This aligns with the Material 3 Expressive "bloom" and "glow" design language.
 *
 * @param color The color of the glow
 * @param alpha The transparency of the glow
 * @param borderRadius The corner radius of the component (for rectangular shapes)
 * @param blurRadius The softeness of the glow
 * @param spread The distance the glow extends from the component
 * @param offsetY Vertical offset of the glow
 */
fun Modifier.expressiveGlow(
    color: Color,
    alpha: Float = 0.2f,
    borderRadius: Dp = 16.dp,
    blurRadius: Dp = 12.dp,
    spread: Dp = 2.dp,
    offsetY: Dp = 4.dp,
) = this.drawBehind {
    val transparentColor = color.copy(alpha = 0.0f).toArgb()
    val shadowColor = color.copy(alpha = alpha).toArgb()
    
    drawIntoCanvas { canvas ->
        val nativePaint = android.graphics.Paint()
        nativePaint.color = transparentColor
        
        // Use setShadowLayer to create the glow effect
        nativePaint.setShadowLayer(
            blurRadius.toPx(),
            0f,
            offsetY.toPx(),
            shadowColor
        )
        
        // Draw the shadow shape
        val spreadPx = spread.toPx()
        val left = -spreadPx
        val top = -spreadPx
        val right = size.width + spreadPx
        val bottom = size.height + spreadPx
        
        canvas.nativeCanvas.drawRoundRect(
            left,
            top,
            right,
            bottom,
            borderRadius.toPx(),
            borderRadius.toPx(),
            nativePaint
        )
    }
}

/**
 * Higher-level glow for primary expressive components like media cards.
 */
fun Modifier.primaryExpressiveGlow(
    color: Color = Color.Black,
    alpha: Float = 0.15f,
    borderRadius: Dp = 16.dp
) = this.expressiveGlow(
    color = color,
    alpha = alpha,
    borderRadius = borderRadius,
    blurRadius = 16.dp,
    spread = 1.dp,
    offsetY = 6.dp
)
