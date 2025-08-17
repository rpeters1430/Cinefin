package com.example.jellyfinandroid.utils

import android.content.Context
import android.content.res.Configuration
import android.view.inputmethod.InputMethodManager

object DeviceTypeUtils {
    enum class DeviceType {
        MOBILE,
        TV,
        TABLET,
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
