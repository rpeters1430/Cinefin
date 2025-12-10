package com.rpeters.jellyfin.utils

import android.app.ActivityManager
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DevicePerformanceProfileTest {

    private val context: Context = mockk()
    private val activityManager: ActivityManager = mockk()

    @Before
    fun setUp() {
        DevicePerformanceProfile.clearCachedProfile()
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
    }

    @After
    fun tearDown() {
        DevicePerformanceProfile.clearCachedProfile()
    }

    @Test
    fun detect_returnsHighTierForHighMemoryClass() {
        every { activityManager.isLowRamDevice } returns false
        every { activityManager.largeMemoryClass } returns 260

        val profile = DevicePerformanceProfile.detect(context)

        assertEquals(DevicePerformanceProfile.Tier.HIGH, profile.tier)
        assertEquals(0.22, profile.memoryCachePercent)
        assertEquals(160, profile.diskCacheSizeMb)
    }

    @Test
    fun detect_returnsMidTierForMidMemoryClass() {
        every { activityManager.isLowRamDevice } returns false
        every { activityManager.largeMemoryClass } returns 200

        val profile = DevicePerformanceProfile.detect(context)

        assertEquals(DevicePerformanceProfile.Tier.MID, profile.tier)
        assertEquals(0.18, profile.memoryCachePercent)
        assertEquals(120, profile.diskCacheSizeMb)
    }

    @Test
    fun detect_returnsLowTierForLowRamDevices() {
        every { activityManager.isLowRamDevice } returns true
        every { activityManager.largeMemoryClass } returns 512

        val profile = DevicePerformanceProfile.detect(context)

        assertEquals(DevicePerformanceProfile.Tier.LOW, profile.tier)
        assertEquals(0.12, profile.memoryCachePercent)
        assertEquals(80, profile.diskCacheSizeMb)
    }

    @Test
    fun detect_cachesResultToAvoidRepeatedChecks() {
        every { activityManager.isLowRamDevice } returns false
        every { activityManager.largeMemoryClass } returns 260

        val first = DevicePerformanceProfile.detect(context)

        every { activityManager.isLowRamDevice } returns true
        every { activityManager.largeMemoryClass } returns 64

        val second = DevicePerformanceProfile.detect(context)

        assertSame(first, second)
    }
}
