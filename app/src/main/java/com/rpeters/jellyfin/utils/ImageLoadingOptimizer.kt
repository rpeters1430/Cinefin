package com.rpeters.jellyfin.utils

import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowHardware
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.util.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File

/**
 * Optimizes image loading with Coil to prevent memory leaks and improve performance
 */
@OptIn(ExperimentalCoilApi::class)
object ImageLoadingOptimizer {
    private const val TAG = "ImageLoadingOptimizer"

    fun initializeCoil(context: Context, okHttpClient: OkHttpClient) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val imageLoader = ImageLoader.Builder(context)
                    .memoryCache {
                        MemoryCache.Builder()
                            .maxSizePercent(context, 0.20) // Increase to 20% for better performance
                            .build()
                    }
                    .diskCache {
                        DiskCache.Builder()
                            .directory(getCacheDirectory(context))
                            .maxSizeBytes(120 * 1024 * 1024) // Fixed 120MB cache
                            .cleanupDispatcher(Dispatchers.IO)
                            .build()
                    }
                    .components {
                        add(
                            OkHttpNetworkFetcherFactory(
                                callFactory = {
                                    okHttpClient.newBuilder()
                                        .addInterceptor { chain ->
                                            val request = chain.request().newBuilder()
                                                .header("Accept", "image/webp,image/avif,image/*,*/*;q=0.8")
                                                .build()
                                            chain.proceed(request)
                                        }
                                        .build()
                                },
                            ),
                        )
                    }
                    .crossfade(100) // Fast crossfade
                    .respectCacheHeaders(true) // Honor server cache headers
                    .allowRgb565(true) // Use less memory per image
                    .allowHardware(true) // Use hardware bitmaps when possible
                    .apply {
                        if (com.rpeters.jellyfin.BuildConfig.DEBUG) {
                            logger(DebugLogger())
                        }
                    }
                    .build()

                // Set as singleton image loader
                SingletonImageLoader.setSafe { imageLoader }
                Log.d(TAG, "Coil image loader initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Coil image loader", e)
            }
        }
    }

    private fun getCacheDirectory(context: Context): File {
        return File(context.cacheDir, "image_cache").apply {
            mkdirs()
        }
    }

    fun clearImageCache(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SingletonImageLoader.get(context).memoryCache?.clear()
                SingletonImageLoader.get(context).diskCache?.clear()
                Log.d(TAG, "Image cache cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear image cache", e)
            }
        }
    }
}
