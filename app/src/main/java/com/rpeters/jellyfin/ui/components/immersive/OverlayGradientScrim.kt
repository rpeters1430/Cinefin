package com.rpeters.jellyfin.ui.components.immersive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens

/**
 * Reusable gradient scrim overlay for creating readable text over images.
 * Supports multiple gradient styles: bottom-up, top-down, or full overlay.
 */
@Composable
fun OverlayGradientScrim(
    modifier: Modifier = Modifier,
    style: GradientStyle = GradientStyle.BottomUp,
    startColor: Color = Color.Black.copy(alpha = 0.8f),
    endColor: Color = Color.Transparent,
    height: androidx.compose.ui.unit.Dp = ImmersiveDimens.GradientHeightHero,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier) {
        val gradientBrush = when (style) {
            GradientStyle.BottomUp -> Brush.verticalGradient(
                colors = listOf(endColor, startColor),
                startY = 0f,
                endY = Float.POSITIVE_INFINITY,
            )
            GradientStyle.TopDown -> Brush.verticalGradient(
                colors = listOf(startColor, endColor),
                startY = 0f,
                endY = Float.POSITIVE_INFINITY,
            )
            GradientStyle.FullOverlay -> Brush.verticalGradient(
                colors = listOf(startColor, startColor),
            )
            GradientStyle.CenterFade -> Brush.radialGradient(
                colors = listOf(endColor, startColor),
            )
        }

        Box(
            modifier = Modifier
                .align(
                    when (style) {
                        GradientStyle.BottomUp -> Alignment.BottomCenter
                        GradientStyle.TopDown -> Alignment.TopCenter
                        else -> Alignment.Center
                    },
                )
                .fillMaxWidth()
                .height(if (style == GradientStyle.FullOverlay) height else height)
                .background(gradientBrush),
        )

        content()
    }
}

/**
 * Full-screen overlay scrim for hero sections
 */
@Composable
fun HeroGradientScrim(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Bottom gradient for readability
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(ImmersiveDimens.GradientHeightHero)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black.copy(alpha = 0.9f),
                        ),
                    ),
                ),
        )

        content()
    }
}

enum class GradientStyle {
    BottomUp,
    TopDown,
    FullOverlay,
    CenterFade,
}
