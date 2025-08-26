package com.rpeters.jellyfin.utils

import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageLoaderInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun initialize() {
        val imageLoader = ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100 * 1024 * 1024) // Increased to 100MB for better caching
                    .build()
            }
            .respectCacheHeaders(false) // Jellyfin images don't change often
            .allowHardware(true) // Enable hardware bitmaps for better performance
            .crossfade(true) // Smooth image transitions
            .crossfade(200) // 200ms fade duration
            .build()
        Coil.setImageLoader(imageLoader)
    }
}
