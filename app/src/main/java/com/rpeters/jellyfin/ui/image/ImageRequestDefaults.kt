package com.rpeters.jellyfin.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size
import com.rpeters.jellyfin.R

/**
 * Shared Coil request helpers to enforce caching, placeholders, and sizing hints.
 *
 * Use these helpers with [JellyfinAsyncImage] instead of building [ImageRequest] instances
 * manually. Additional per-request options should be provided via the [builder] lambda to
 * keep configuration centralized.
 */
fun ImageRequest.Builder.applyDefaultImageOptions(size: Size? = null): ImageRequest.Builder {
    size?.let { size(it) }
    return placeholder(R.drawable.ic_image_placeholder)
        .error(R.drawable.ic_image_error)
        .fallback(R.drawable.ic_image_placeholder)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .networkCachePolicy(CachePolicy.ENABLED)
}

@Composable
fun rememberDefaultImageRequest(
    model: Any?,
    size: Size?,
    builder: ImageRequest.Builder.() -> Unit = {},
): ImageRequest {
    val context = LocalContext.current
    return remember(model, size, builder) {
        ImageRequest.Builder(context)
            .data(model)
            .applyDefaultImageOptions(size)
            .apply(builder)
            .build()
    }
}

@Composable
fun rememberCoilSize(width: Dp, height: Dp): Size {
    val density = LocalDensity.current
    val widthPx = with(density) { width.roundToPx() }
    val heightPx = with(density) { height.roundToPx() }
    return remember(widthPx, heightPx) { Size(widthPx, heightPx) }
}

@Composable
fun rememberCoilSize(square: Dp): Size = rememberCoilSize(square, square)

@Composable
fun rememberScreenWidthHeight(height: Dp): Size {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val widthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val heightPx = with(density) { height.roundToPx() }
    return remember(widthPx, heightPx) { Size(widthPx, heightPx) }
}

@Composable
/**
 * Jellyfin wrapper around Coil's [AsyncImage] that applies shared request defaults.
 *
 * @param model The image model (e.g., URL or resource) to load.
 * @param contentDescription Semantic description for accessibility.
 * @param modifier Optional modifier for sizing/positioning.
 * @param contentScale Scaling strategy for the image content.
 * @param alpha Alpha to apply to the rendered image.
 * @param requestSize Optional explicit [Size] hint; use [rememberCoilSize] to memoize.
 * @param builder Optional request customizations (e.g., `crossfade(true)`). Avoid passing
 * pre-built [ImageRequest] instances; prefer configuring the builder here so defaults remain
 * centralized.
 */
fun JellyfinAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f,
    requestSize: Size? = null,
    builder: ImageRequest.Builder.() -> Unit = {},
) {
    val context = LocalContext.current
    val imageRequest = rememberDefaultImageRequest(model, requestSize, builder)
    AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alpha = alpha,
        imageLoader = SingletonImageLoader.get(context),
    )
}
