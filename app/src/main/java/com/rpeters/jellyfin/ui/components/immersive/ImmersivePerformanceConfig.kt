package com.rpeters.jellyfin.ui.components.immersive

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.rpeters.jellyfin.ui.image.ImageQuality
import com.rpeters.jellyfin.utils.DevicePerformanceProfile

/**
 * Performance configuration for immersive UI screens.
 * Adapts behavior based on device capabilities to ensure smooth performance.
 *
 * **Usage**:
 * ```kotlin
 * @Composable
 * fun ImmersiveHomeScreen(...) {
 *     val perfConfig = rememberImmersivePerformanceConfig()
 *
 *     // Use adaptive settings
 *     ImmersiveHeroCarousel(
 *         items = items.take(perfConfig.maxHeroCarouselItems),
 *         enableParallax = perfConfig.enableParallax,
 *     )
 *
 *     ImmersiveMediaCard(
 *         imageQuality = perfConfig.imageQuality,
 *     )
 * }
 * ```
 *
 * **Performance Tiers**:
 * - **LOW** (2GB RAM, old CPU): Minimal items, low quality, no animations
 * - **MID** (4GB RAM, decent CPU): Balanced items, medium quality, some animations
 * - **HIGH** (8GB+ RAM, powerful CPU): Max items, high quality, all animations
 */
data class ImmersivePerformanceConfig(
    /** Device performance tier */
    val tier: DevicePerformanceProfile.Tier,

    // ===== Item Limits =====
    /** Maximum items in hero carousel (3-10) */
    val maxHeroCarouselItems: Int,

    /** Maximum items per content row (20-50) */
    val maxRowItems: Int,

    /** Maximum items in grid view (50-200) */
    val maxGridItems: Int,

    /** Preload offset for lazy lists (5-15 items ahead) */
    val preloadOffset: Int,

    // ===== Visual Quality =====
    /** Image quality for media cards */
    val imageQuality: ImageQuality,

    /** Image quality for hero carousel (usually higher) */
    val heroImageQuality: ImageQuality,

    // ===== Animations & Effects =====
    /** Enable parallax scrolling effect */
    val enableParallax: Boolean,

    /** Enable auto-scroll in hero carousel */
    val enableAutoScroll: Boolean,

    /** Crossfade duration in milliseconds (0 = instant) */
    val crossfadeDurationMs: Int,

    /** Enable shimmer loading animations */
    val enableShimmer: Boolean,

    // ===== Memory Management =====
    /** Memory cache percentage (12-22%) */
    val memoryCachePercent: Double,

    /** Disk cache size in MB (80-160 MB) */
    val diskCacheSizeMB: Long,
) {
    companion object {
        /**
         * Create performance config for LOW-tier devices.
         * Optimized for 2GB RAM, older CPUs.
         */
        fun forLowTier(): ImmersivePerformanceConfig = ImmersivePerformanceConfig(
            tier = DevicePerformanceProfile.Tier.LOW,
            maxHeroCarouselItems = 3,
            maxRowItems = 20,
            maxGridItems = 50,
            preloadOffset = 5,
            imageQuality = ImageQuality.LOW,
            heroImageQuality = ImageQuality.MEDIUM, // Hero gets slightly better quality
            enableParallax = false, // Parallax can cause jank on low-end devices
            enableAutoScroll = true,
            crossfadeDurationMs = 0, // Instant to save CPU
            enableShimmer = false, // Shimmer animation is expensive
            memoryCachePercent = 0.12,
            diskCacheSizeMB = 80,
        )

        /**
         * Create performance config for MID-tier devices.
         * Balanced for 4GB RAM, decent CPUs.
         */
        fun forMidTier(): ImmersivePerformanceConfig = ImmersivePerformanceConfig(
            tier = DevicePerformanceProfile.Tier.MID,
            maxHeroCarouselItems = 5,
            maxRowItems = 35,
            maxGridItems = 100,
            preloadOffset = 10,
            imageQuality = ImageQuality.MEDIUM,
            heroImageQuality = ImageQuality.HIGH,
            enableParallax = true,
            enableAutoScroll = true,
            crossfadeDurationMs = 200, // Smooth but not too long
            enableShimmer = true,
            memoryCachePercent = 0.18,
            diskCacheSizeMB = 120,
        )

        /**
         * Create performance config for HIGH-tier devices.
         * Maximum quality for 8GB+ RAM, powerful CPUs.
         */
        fun forHighTier(): ImmersivePerformanceConfig = ImmersivePerformanceConfig(
            tier = DevicePerformanceProfile.Tier.HIGH,
            maxHeroCarouselItems = 10,
            maxRowItems = 50,
            maxGridItems = 200,
            preloadOffset = 15,
            imageQuality = ImageQuality.HIGH,
            heroImageQuality = ImageQuality.HIGH,
            enableParallax = true,
            enableAutoScroll = true,
            crossfadeDurationMs = 300, // Smooth and polished
            enableShimmer = true,
            memoryCachePercent = 0.22,
            diskCacheSizeMB = 160,
        )

        /**
         * Detect device tier and create appropriate config.
         */
        fun detect(context: Context): ImmersivePerformanceConfig {
            val profile = DevicePerformanceProfile.detect(context)
            return when (profile.tier) {
                DevicePerformanceProfile.Tier.LOW -> forLowTier()
                DevicePerformanceProfile.Tier.MID -> forMidTier()
                DevicePerformanceProfile.Tier.HIGH -> forHighTier()
            }
        }
    }
}

/**
 * Remember performance config for current device.
 * Cached for performance.
 */
@Composable
fun rememberImmersivePerformanceConfig(): ImmersivePerformanceConfig {
    val context = LocalContext.current
    return remember {
        ImmersivePerformanceConfig.detect(context)
    }
}
