package com.rpeters.jellyfin.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Performance-optimized shimmer placeholder.
 * Uses drawWithCache to avoid recomposition and slower animation for reduced GPU load.
 *
 * Usage:
 * ShimmerBox(modifier = Modifier.size(120.dp, 180.dp), shape = RoundedCornerShape(12.dp))
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    baseColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    highlightColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .optimizedShimmer(baseColor, highlightColor),
    )
}

/**
 * Performance-optimized shimmer effect using drawWithCache.
 * - Slower animation (1800ms vs 1200ms) reduces GPU work
 * - drawWithCache avoids recomposition overhead
 * - Cached brush recreation only when colors change
 */
@Composable
fun Modifier.optimizedShimmer(
    baseColor: Color,
    highlightColor: Color,
): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // ✅ Performance: Slower animation (1800ms) reduces frame updates
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_anim",
    )

    // ✅ Performance: Use drawWithCache to avoid recomposition on each frame
    return this.drawWithCache {
        val width = size.width
        val animOffset = translateAnim * width * 2

        val shimmerBrush = Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset(animOffset - width, 0f),
            end = Offset(animOffset, size.height),
        )

        onDrawBehind {
            drawRect(shimmerBrush)
        }
    }
}

/**
 * Legacy shimmer for backwards compatibility. Prefer optimizedShimmer for better performance.
 */
@Composable
fun Modifier.appShimmer(
    baseColor: Color,
    highlightColor: Color,
): Modifier = optimizedShimmer(baseColor, highlightColor)
