package com.rpeters.jellyfin.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

/**
 * A pulsing glow effect for AI-powered components.
 */
@Composable
fun Modifier.aiAura(
    enabled: Boolean = true,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.tertiary,
): Modifier {
    if (!enabled) return this

    val infiniteTransition = rememberInfiniteTransition(label = "ai_aura")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    return this.drawBehind {
        val radius = size.minDimension / 2 * pulseScale
        val brush = Brush.sweepGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.4f),
                secondaryColor.copy(alpha = 0.4f),
                primaryColor.copy(alpha = 0.4f),
            )
        )
        
        scale(pulseScale) {
            drawCircle(
                brush = brush,
                radius = radius,
                center = center,
                alpha = 0.3f
            )
        }
    }
}
