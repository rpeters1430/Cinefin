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
 * Helper to provide consistent haptic feedback across the app.
 * Updated for Android 16 (API 36) with frequency-aware haptic curves.
 */
class ExpressiveHaptics(
    private val hapticFeedback: HapticFeedback,
    private val vibrator: Vibrator? = null
) {
    
    /**
     * Subtle click feedback for standard interactions.
     */
    fun lightClick() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    
    /**
     * Standard click feedback.
     */
    fun click() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    
    /**
     * Strong feedback for important actions or long-press start.
     */
    fun heavyClick() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /**
     * Playback started/resumed haptic.
     * Android 16+: Uses a rising frequency curve for a "start" feel.
     */
    fun playbackStarted() {
        if (Build.VERSION.SDK_INT >= 36 && vibrator != null) {
            // Android 16 Haptic Curve (Baklava)
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.8f)
                .compose()
            vibrator.vibrate(effect)
        } else {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /**
     * Playback paused haptic.
     * Android 16+: Uses a falling frequency curve for a "stop" feel.
     */
    fun playbackPaused() {
        if (Build.VERSION.SDK_INT >= 36 && vibrator != null) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.3f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.6f)
                .compose()
            vibrator.vibrate(effect)
        } else {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
}

@Composable
fun rememberExpressiveHaptics(): ExpressiveHaptics {
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val vibrator = remember(context) { 
        context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator 
    }
    return remember(hapticFeedback, vibrator) { ExpressiveHaptics(hapticFeedback, vibrator) }
}
