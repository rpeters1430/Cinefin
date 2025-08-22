package com.rpeters.jellyfin.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * Test suite for PerformanceOptimizations.
 */
class PerformanceOptimizationsTest {

    @Test
    fun `ImageLoadingConfig has correct default values`() {
        val config = ImageLoadingConfig()

        assertEquals(400, config.maxWidth.value.toInt())
        assertEquals(600, config.maxHeight.value.toInt())
        assertEquals(85, config.quality)
        assertTrue(config.enableMemoryCache)
        assertTrue(config.enableDiskCache)
        assertEquals(300, config.crossfadeMs)
    }

    @Test
    fun `PerformanceConstants have reasonable values`() {
        assertTrue(PerformanceConstants.DEFAULT_PAGE_SIZE > 0)
        assertTrue(PerformanceConstants.LARGE_PAGE_SIZE > PerformanceConstants.DEFAULT_PAGE_SIZE)
        assertTrue(PerformanceConstants.PREFETCH_BUFFER > 0)
        assertTrue(PerformanceConstants.MAX_CACHED_IMAGES > 0)
        assertTrue(PerformanceConstants.IMAGE_CACHE_SIZE_MB > 0)
        assertTrue(PerformanceConstants.ANIMATION_DURATION_MS > 0)
        assertTrue(PerformanceConstants.DEBOUNCE_DELAY_MS > 0)
        assertTrue(PerformanceConstants.NETWORK_TIMEOUT_MS > 0)
    }

    @Test
    fun `AdaptivePerformance calculates correct image quality`() {
        // Test low power mode and metered connection
        assertEquals(60, AdaptivePerformance.getOptimalImageQuality(true, true))

        // Test low power mode only
        assertEquals(75, AdaptivePerformance.getOptimalImageQuality(true, false))

        // Test metered connection only
        assertEquals(75, AdaptivePerformance.getOptimalImageQuality(false, true))

        // Test normal conditions
        assertEquals(85, AdaptivePerformance.getOptimalImageQuality(false, false))
    }

    @Test
    fun `AdaptivePerformance calculates correct cache size`() {
        // Test low memory
        assertEquals(25, AdaptivePerformance.getOptimalCacheSize(256))

        // Test medium memory
        assertEquals(50, AdaptivePerformance.getOptimalCacheSize(768))

        // Test high memory
        assertEquals(100, AdaptivePerformance.getOptimalCacheSize(2048))
    }

    @Test
    fun `AdaptivePerformance correctly determines animation reduction`() {
        assertTrue(AdaptivePerformance.shouldReduceAnimations(true))
        assertFalse(AdaptivePerformance.shouldReduceAnimations(false))
    }

    @Test
    fun `PreloadStrategy instances are created`() {
        assertNotNull(PreloadStrategy.Aggressive)
        assertNotNull(PreloadStrategy.Moderate)
        assertNotNull(PreloadStrategy.Conservative)
    }

    @Test
    fun `PreloadConfig has correct default values`() {
        val config = PreloadConfig()

        assertTrue(config.strategy is PreloadStrategy.Moderate)
        assertEquals(5, config.bufferSize)
        assertTrue(config.enablePrefetch)
    }
}
