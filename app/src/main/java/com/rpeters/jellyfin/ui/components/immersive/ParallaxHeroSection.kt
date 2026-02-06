package com.rpeters.jellyfin.ui.components.immersive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens

/**
 * Reusable parallax hero section with gradient overlay and content.
 * Supports scroll offset for parallax effect.
 *
 * @param imageUrl URL of the hero background image
 * @param scrollOffset Scroll offset for parallax effect (0f = top, 1f = fully scrolled)
 * @param height Height of the hero section
 * @param parallaxFactor Strength of parallax effect (0f = none, 1f = full)
 * @param content Content to overlay on the hero image
 */
@Composable
fun ParallaxHeroSection(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    scrollOffset: Float = 0f,
    height: Dp = ImmersiveDimens.HeroHeightPhone,
    parallaxFactor: Float = 0.5f,
    contentScale: ContentScale = ContentScale.Crop,
    showGradient: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        // Background image with parallax effect
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = contentScale,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    // Parallax effect: move image slower than scroll
                    translationY = scrollOffset * parallaxFactor * height.toPx()
                }
        )

        // Gradient overlay for text readability
        if (showGradient) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Black.copy(alpha = 0.7f),
                                    Color.Black.copy(alpha = 0.9f)
                                ),
                                startY = 0f,
                                endY = size.height
                            )
                        )
                    }
            )
        }

        // Content overlay
        content()
    }
}

/**
 * Simplified hero section without parallax for static screens
 */
@Composable
fun StaticHeroSection(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    height: Dp = ImmersiveDimens.HeroHeightPhone,
    contentScale: ContentScale = ContentScale.Crop,
    content: @Composable BoxScope.() -> Unit = {}
) {
    ParallaxHeroSection(
        imageUrl = imageUrl,
        modifier = modifier,
        scrollOffset = 0f,
        height = height,
        parallaxFactor = 0f,
        contentScale = contentScale,
        content = content
    )
}
