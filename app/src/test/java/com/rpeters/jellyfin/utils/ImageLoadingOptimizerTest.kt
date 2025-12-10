package com.rpeters.jellyfin.utils

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImageLoadingOptimizerTest {

    private val memoryCache: MemoryCache = mockk(relaxed = true)
    private val diskCache: DiskCache = mockk(relaxed = true)
    private val imageLoader: ImageLoader = mockk(relaxed = true) {
        every { memoryCache } returns this@ImageLoadingOptimizerTest.memoryCache
        every { diskCache } returns this@ImageLoadingOptimizerTest.diskCache
    }

    @Test
    fun clearImageCache_clearsCachesOnProvidedDispatcher() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)

        ImageLoadingOptimizer.clearImageCache(imageLoader, dispatcher)

        advanceUntilIdle()

        verify { memoryCache.clear() }
        verify { diskCache.clear() }
    }
}
