package com.rpeters.jellyfin.utils

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * Describes the device's performance tier to tune caches and animations.
 */
data class DevicePerformanceProfile(
    val tier: Tier,
    val memoryCachePercent: Double,
    val diskCacheSizeMb: Long,
) {
    enum class Tier { HIGH, MID, LOW }

    companion object {
        fun detect(context: Context): DevicePerformanceProfile {
            val activityManager = context.getSystemService<ActivityManager>()
            val isLowRamDevice = activityManager?.isLowRamDevice ?: false
            val memoryClassMb = activityManager?.largeMemoryClass ?: 128

            val tier = when {
                !isLowRamDevice && memoryClassMb >= 256 -> Tier.HIGH
                !isLowRamDevice && memoryClassMb >= 192 -> Tier.MID
                else -> Tier.LOW
            }

            return when (tier) {
                Tier.HIGH -> DevicePerformanceProfile(
                    tier = tier,
                    memoryCachePercent = 0.22,
                    diskCacheSizeMb = 160,
                )

                Tier.MID -> DevicePerformanceProfile(
                    tier = tier,
                    memoryCachePercent = 0.18,
                    diskCacheSizeMb = 120,
                )

                Tier.LOW -> DevicePerformanceProfile(
                    tier = tier,
                    memoryCachePercent = 0.12,
                    diskCacheSizeMb = 80,
                )
            }
        }
    }
}
