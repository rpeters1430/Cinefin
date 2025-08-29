package com.rpeters.jellyfin.ui.image

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.Coil
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.RoundedCornersTransformation
import com.rpeters.jellyfin.ui.ShimmerBox
import okhttp3.OkHttpClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.io.File

/**
 * Optimized image loading system using Coil with intelligent caching,
 * progressive loading, and memory management.
 */

/**
 * Image size presets for different use cases.
 */
enum class ImageSize(val width: Int, val height: Int) {
    THUMBNAIL(150, 225), // Small thumbnails
    CARD(300, 450), // Media cards
    BANNER(1920, 1080), // Banners and backdrops
    POSTER(600, 900), // Large posters
    AVATAR(100, 100), // User avatars
    ICON(48, 48), // Small icons
}

/**
 * Image quality settings for different network conditions.
 */
enum class ImageQuality(val quality: Int, val maxSize: ImageSize) {
    LOW(70, ImageSize.THUMBNAIL),
    MEDIUM(85, ImageSize.CARD),
    HIGH(95, ImageSize.POSTER),
    ORIGINAL(100, ImageSize.BANNER),
}

/**
 * Configuration for the optimized image loader.
 */
data class ImageLoaderConfig(
    val memoryPercent: Double = 0.25, // 25% of available memory
    val diskCacheSizeMB: Long = 100, // 100MB disk cache
    val networkCachePolicy: CachePolicy = CachePolicy.ENABLED,
    val diskCachePolicy: CachePolicy = CachePolicy.ENABLED,
    val memoryCachePolicy: CachePolicy = CachePolicy.ENABLED,
)

/**
 * Create an optimized ImageLoader instance.
 */
fun createOptimizedImageLoader(
    context: Context,
    okHttpClient: OkHttpClient,
    config: ImageLoaderConfig = ImageLoaderConfig(),
): ImageLoader {
    return ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(config.memoryPercent)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(context.cacheDir, "image_cache"))
                .maxSizeBytes(config.diskCacheSizeMB * 1024 * 1024)
                .build()
        }
        .okHttpClient(okHttpClient)
        .respectCacheHeaders(true)
        .build()
}

/**
 * Optimized image loading composable with intelligent sizing and caching.
 */
@Composable
fun OptimizedImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: ImageSize = ImageSize.CARD,
    quality: ImageQuality = ImageQuality.MEDIUM,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Dp = 0.dp,
    placeholder: @Composable (() -> Unit)? = null,
    error: @Composable (() -> Unit)? = null,
    loading: @Composable (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()

    val imageRequest = remember(imageUrl, size, quality) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .size(Size(size.width, size.height))
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .placeholder(ColorDrawable(backgroundColor))
            .error(ColorDrawable(backgroundColor))
            .transformations(
                if (cornerRadius > 0.dp) {
                    listOf(RoundedCornersTransformation(cornerRadius.value))
                } else {
                    emptyList()
                },
            )
            .build()
    }

    SubcomposeAsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
        contentScale = contentScale,
        loading = {
            loading?.invoke() ?: Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ShimmerBox(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(cornerRadius),
                )
            }
        },
        error = {
            error?.invoke() ?: Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                placeholder?.invoke()
            }
        },
    )
}

/**
 * Media-specific image component with automatic sizing based on item type.
 */
@Composable
fun MediaImage(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Dp = 8.dp,
    showPlaceholder: Boolean = true,
) {
    val imageSize = when (item.type) {
        BaseItemKind.MOVIE, BaseItemKind.SERIES -> ImageSize.POSTER
        BaseItemKind.EPISODE -> ImageSize.BANNER
        BaseItemKind.MUSIC_ALBUM -> ImageSize.CARD
        BaseItemKind.MUSIC_ARTIST -> ImageSize.CARD
        else -> ImageSize.CARD
    }

    val quality = ImageQuality.MEDIUM

    OptimizedImage(
        imageUrl = getImageUrl(item),
        contentDescription = "${item.name} ${item.type?.name?.lowercase()} image",
        modifier = modifier,
        size = imageSize,
        quality = quality,
        contentScale = contentScale,
        cornerRadius = cornerRadius,
        placeholder = if (showPlaceholder) {
            {
                MediaPlaceholder(
                    itemType = item.type,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            null
        },
    )
}

/**
 * Avatar image component for user profiles.
 */
@Composable
fun AvatarImage(
    imageUrl: String?,
    userName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    OptimizedImage(
        imageUrl = imageUrl,
        contentDescription = "$userName's avatar",
        modifier = modifier.size(size),
        size = ImageSize.AVATAR,
        quality = ImageQuality.MEDIUM,
        contentScale = ContentScale.Crop,
        cornerRadius = size / 2, // Circular
        placeholder = {
            UserAvatarPlaceholder(
                userName = userName,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

/**
 * Backdrop image component for large background images.
 */
@Composable
fun BackdropImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    overlay: @Composable (() -> Unit)? = null,
) {
    Box(modifier = modifier) {
        OptimizedImage(
            imageUrl = imageUrl,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            size = ImageSize.BANNER,
            quality = ImageQuality.HIGH,
            contentScale = contentScale,
            loading = {
                ShimmerBox(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(0.dp),
                )
            },
        )

        overlay?.invoke()
    }
}

/**
 * Placeholder for media items based on type.
 */
@Composable
private fun MediaPlaceholder(
    itemType: BaseItemKind?,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val iconColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // You could add type-specific icons here
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // Simple colored background for now
            // In a real implementation, you'd show type-specific icons
        }
    }
}

/**
 * User avatar placeholder with initials.
 */
@Composable
private fun UserAvatarPlaceholder(
    userName: String,
    modifier: Modifier = Modifier,
) {
    val initials = userName.split(" ")
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifEmpty { "U" }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = initials,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * Preload images for better user experience.
 */
@Composable
fun rememberImagePreloader(): ImagePreloader {
    val context = LocalContext.current
    return remember { ImagePreloader(context) }
}

/**
 * Image preloader for background loading of images.
 */
class ImagePreloader(private val context: Context) {
    private val imageLoader = createOptimizedImageLoader(
        context,
        Coil.imageLoader(context).okHttpClient,
    )

    /**
     * Preload a list of image URLs in the background.
     */
    suspend fun preloadImages(
        imageUrls: List<String>,
        size: ImageSize = ImageSize.CARD,
        priority: Priority = Priority.LOW,
    ) {
        imageUrls.forEach { url ->
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(Size(size.width, size.height))
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()

            imageLoader.enqueue(request)
        }
    }

    enum class Priority {
        LOW, NORMAL, HIGH
    }
}
