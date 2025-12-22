package com.rpeters.jellyfin.utils

import android.content.Context
import androidx.annotation.VisibleForTesting
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.util.DebugLogger
import com.rpeters.jellyfin.utils.DevicePerformanceProfile.Companion.detect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Optimizes image loading with Coil to prevent memory leaks and improve performance
 */
@OptIn(ExperimentalCoilApi::class)
object ImageLoadingOptimizer {
    private const val TAG = "ImageLoadingOptimizer"
    private const val BYTES_PER_MEGABYTE = 1024L * 1024L

    fun initializeCoil(context: Context, okHttpClient: OkHttpClient) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val performanceProfile = detect(context)
                val imageLoader = buildImageLoader(context, okHttpClient, performanceProfile)
                SingletonImageLoader.setSafe { imageLoader }
                SecureLogger.d(TAG, "Coil image loader initialized successfully")
            }.onFailure { throwable ->
                SecureLogger.e(TAG, "Failed to initialize Coil image loader", throwable)
            }
        }
    }

    // Coil 3.x: Cache directory is now handled by resolve()
    // This function is no longer needed but kept for backward compatibility
    private fun getCacheDirectory(context: Context): File {
        return context.cacheDir.resolve("image_cache").apply {
            mkdirs()
        }
    }

    fun clearImageCache(context: Context) {
        clearImageCache(SingletonImageLoader.get(context))
    }

    @VisibleForTesting
    internal fun buildImageLoader(
        context: Context,
        okHttpClient: OkHttpClient,
        performanceProfile: DevicePerformanceProfile,
    ): ImageLoader {
        val imageHttpClient = okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Accept", "image/webp,image/avif,image/*,*/*;q=0.8")
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, performanceProfile.memoryCachePercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(performanceProfile.diskCacheSizeMb * BYTES_PER_MEGABYTE)
                    .cleanupCoroutineContext(Dispatchers.IO)
                    .build()
            }
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = imageHttpClient,
                    ),
                )
            }
            // Coil 3.x: crossfade, respectCacheHeaders, allowRgb565, allowHardware
            // are now request-level options, not builder options
            .apply {
                if (com.rpeters.jellyfin.BuildConfig.DEBUG) {
                    logger(DebugLogger())
                }
            }
            .build()
    }

    @VisibleForTesting
    internal fun clearImageCache(
        imageLoader: ImageLoader,
        dispatcher: kotlin.coroutines.CoroutineContext = Dispatchers.IO,
    ) {
        CoroutineScope(dispatcher).launch {
            runCatching {
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
                SecureLogger.d(TAG, "Image cache cleared")
            }.onFailure { throwable ->
                SecureLogger.e(TAG, "Failed to clear image cache", throwable)
            }
        }
    }
}
