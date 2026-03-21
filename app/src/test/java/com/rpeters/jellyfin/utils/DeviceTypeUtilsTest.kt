package com.rpeters.jellyfin.utils

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTypeUtilsTest {

    @Test
    fun getDeviceType_returnsTv_whenUiModeIsTelevision() {
        val context = createContext(
            uiMode = Configuration.UI_MODE_TYPE_TELEVISION,
            screenLayout = Configuration.SCREENLAYOUT_SIZE_NORMAL,
            hasTouchscreen = true,
            hasLeanback = false,
        )

        assertEquals(DeviceTypeUtils.DeviceType.TV, DeviceTypeUtils.getDeviceType(context))
    }

    @Test
    fun getDeviceType_returnsTv_forLeanbackDeviceWithoutTouchscreen() {
        val context = createContext(
            uiMode = Configuration.UI_MODE_TYPE_NORMAL,
            screenLayout = Configuration.SCREENLAYOUT_SIZE_NORMAL,
            hasTouchscreen = false,
            hasLeanback = true,
        )

        assertEquals(DeviceTypeUtils.DeviceType.TV, DeviceTypeUtils.getDeviceType(context))
    }

    @Test
    fun getDeviceType_returnsTablet_forLargeScreenWithoutTvSignals() {
        val context = createContext(
            uiMode = Configuration.UI_MODE_TYPE_NORMAL,
            screenLayout = Configuration.SCREENLAYOUT_SIZE_LARGE,
            hasTouchscreen = true,
            hasLeanback = false,
        )

        assertEquals(DeviceTypeUtils.DeviceType.TABLET, DeviceTypeUtils.getDeviceType(context))
    }

    @Test
    fun getDeviceType_returnsMobile_forRegularTouchDevice() {
        val context = createContext(
            uiMode = Configuration.UI_MODE_TYPE_NORMAL,
            screenLayout = Configuration.SCREENLAYOUT_SIZE_NORMAL,
            hasTouchscreen = true,
            hasLeanback = false,
        )

        assertEquals(DeviceTypeUtils.DeviceType.MOBILE, DeviceTypeUtils.getDeviceType(context))
    }

    private fun createContext(
        uiMode: Int,
        screenLayout: Int,
        hasTouchscreen: Boolean,
        hasLeanback: Boolean,
    ): Context {
        val configuration = Configuration().apply {
            this.uiMode = uiMode
            this.screenLayout = screenLayout
        }
        val resources = mockk<Resources>()
        val packageManager = mockk<PackageManager>()
        val context = mockk<Context>()

        every { resources.configuration } returns configuration
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) } returns hasTouchscreen
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) } returns hasLeanback
        every { context.resources } returns resources
        every { context.packageManager } returns packageManager

        return context
    }
}
