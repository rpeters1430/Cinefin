package com.rpeters.jellyfin.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.inputmethod.InputMethodManager

object DeviceTypeUtils {
    enum class DeviceType {
        MOBILE,
        TV,
        TABLET,
    }

    /**
     * Detects if the current device is an emulator.
     * Useful for disabling hardware-intensive features (like complex shaders) that crash on virtual GPUs.
     */
    fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.PRODUCT.contains("sdk_google") ||
            Build.PRODUCT.contains("google_sdk") ||
            Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("sdk_x86") ||
            Build.PRODUCT.contains("vbox86p") ||
            Build.PRODUCT.contains("emulator") ||
            Build.PRODUCT.contains("simulator")
    }

    fun getDeviceType(context: Context): DeviceType {
        // Check if running on Android TV
        val uiMode = context.resources.configuration.uiMode
        if (uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION) {
            return DeviceType.TV
        }

        // Check if touchscreen is available (TVs typically don't have touchscreens)
        val hasTouchscreen = context.packageManager.hasSystemFeature("android.hardware.touchscreen")
        val hasLeanback = context.packageManager.hasSystemFeature("android.software.leanback")

        if (!hasTouchscreen && hasLeanback) {
            return DeviceType.TV
        }

        // Check screen size for tablet detection
        val screenSize = context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        if (screenSize == Configuration.SCREENLAYOUT_SIZE_LARGE ||
            screenSize == Configuration.SCREENLAYOUT_SIZE_XLARGE
        ) {
            return DeviceType.TABLET
        }

        return DeviceType.MOBILE
    }

    fun isTvDevice(context: Context): Boolean {
        return getDeviceType(context) == DeviceType.TV
    }

    fun isKeyboardAvailable(context: Context): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.size > 0
    }
}
