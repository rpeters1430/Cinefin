package com.rpeters.jellyfin.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.ui.platform.LocalContext

/**
 * Haptic feedback helper using Android 16 (BAKLAVA) frequency-aware composition primitives.
 * Falls back gracefully on devices that don't support the required primitives.
 */
class ExpressiveHaptics(
    private val hapticFeedback: HapticFeedback,
    private val vibrator: Vibrator? = null
) {

    fun lightClick() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun click() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun heavyClick() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /** Playback started/resumed — rising frequency curve. */
    fun playbackStarted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && vibrator != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_TICK,
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
            )
        ) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.8f)
                .compose()
            vibrator.vibrate(effect)
        } else {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /** Playback paused — falling frequency curve. */
    fun playbackPaused() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && vibrator != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_TICK,
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
            )
        ) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.3f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.6f)
                .compose()
            vibrator.vibrate(effect)
        } else {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    /** Subtle tick for seek-bar dragging. */
    fun seekTick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && vibrator != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
        ) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.4f)
                .compose()
            vibrator.vibrate(effect)
        } else {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    /** Stronger feedback when a limit is reached (end of list, slider boundary). */
    fun limitReached() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && vibrator != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)
        ) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                .compose()
            vibrator.vibrate(effect)
        } else {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}

@Composable
fun rememberExpressiveHaptics(): ExpressiveHaptics {
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val vibrator = remember(context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
    return remember(hapticFeedback, vibrator) { ExpressiveHaptics(hapticFeedback, vibrator) }
}
