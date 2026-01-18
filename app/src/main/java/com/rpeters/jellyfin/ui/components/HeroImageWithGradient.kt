package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.jellyfin.ui.theme.HeroGradient

/**
 * Full-bleed hero image with smooth gradient blend into background
 *
 * Displays a backdrop or hero image that extends to screen edges (horizontally and vertically)
 * with a smooth gradient overlay that blends into the app background on the lower portion.
 *
 * @param imageUrl URL of the backdrop/hero image to display
 * @param contentDescription Accessibility description for the image
 * @param modifier Modifier for the root container
 * @param minHeight Minimum height for the hero section (default: 400.dp)
 * @param aspectRatio Aspect ratio multiplier for hero height based on screen width (default: 1.0f = square)
 * @param loadingContent Composable to show while image is loading
 * @param errorContent Composable to show if image fails to load
 * @param overlayContent Optional composable to overlay on top of the hero (e.g., logo, title)
 */
@Composable
fun HeroImageWithGradient(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    minHeight: Dp = 400.dp,
    aspectRatio: Float = 1.0f,
    loadingContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer),
        )
    },
    errorContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer),
        )
    },
    overlayContent: @Composable () -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val heroHeight = maxOf(maxWidth * aspectRatio, minHeight)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight),
        ) {
            // Backdrop Image - Full bleed extending to edges
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                loading = { loadingContent() },
                error = { errorContent() },
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            )

            // Smooth gradient blend into background on lower side
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = HeroGradient.alphaStops.map { alpha ->
                                MaterialTheme.colorScheme.background.copy(alpha = alpha)
                            },
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY,
                        ),
                    ),
            )

            // Optional overlay content (logo, title, etc.)
            overlayContent()
        }
    }
}

/**
 * Hero image with logo overlay
 *
 * Convenience wrapper around [HeroImageWithGradient] that includes a logo overlay
 * positioned in the bottom center of the hero section.
 *
 * @param imageUrl URL of the backdrop/hero image to display
 * @param logoUrl Optional URL for logo to display over the hero
 * @param contentDescription Accessibility description for the backdrop
 * @param logoContentDescription Accessibility description for the logo
 * @param modifier Modifier for the root container
 * @param minHeight Minimum height for the hero section
 * @param aspectRatio Aspect ratio multiplier for hero height
 * @param loadingContent Composable to show while image is loading
 * @param errorContent Composable to show if image fails to load
 */
@Composable
fun HeroImageWithLogo(
    imageUrl: String?,
    logoUrl: String?,
    contentDescription: String?,
    logoContentDescription: String?,
    modifier: Modifier = Modifier,
    minHeight: Dp = 400.dp,
    aspectRatio: Float = 1.0f,
    loadingContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer),
        )
    },
    errorContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer),
        )
    },
) {
    HeroImageWithGradient(
        imageUrl = imageUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        minHeight = minHeight,
        aspectRatio = aspectRatio,
        loadingContent = loadingContent,
        errorContent = errorContent,
        overlayContent = {
            // Logo overlay (centered on bottom third)
            if (!logoUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp)
                        .padding(bottom = 48.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(logoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = logoContentDescription,
                        loading = { /* No loading state for logos */ },
                        error = { /* Silently fail - title will be shown below */ },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(120.dp),
                    )
                }
            }
        },
    )
}
