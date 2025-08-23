package com.rpeters.jellyfin.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween

/**
 * Material 3 Expressive Motion tokens for consistent animations across the app
 * Following Material Design 3 Expressive motion principles (2024-2025)
 */
object MotionTokens {

    // Material 3 Expressive Easing curves (updated for 2025)
    val ExpressiveEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EmphasizedEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val AccelerateEasing: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val DecelerateEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    // New Expressive motion curves for media interactions
    val MediaPlayEasing: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val MediaSeekEasing: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val CarouselScrollEasing: Easing = CubicBezierEasing(0.1f, 0.0f, 0.1f, 1.0f)

    // Duration tokens
    const val DurationShort1 = 50
    const val DurationShort2 = 100
    const val DurationShort3 = 150
    const val DurationShort4 = 200
    const val DurationMedium1 = 250
    const val DurationMedium2 = 300
    const val DurationMedium3 = 350
    const val DurationMedium4 = 400
    const val DurationLong1 = 450
    const val DurationLong2 = 500
    const val DurationLong3 = 550
    const val DurationLong4 = 600
    const val DurationExtraLong1 = 700
    const val DurationExtraLong2 = 800
    const val DurationExtraLong3 = 900
    const val DurationExtraLong4 = 1000

    // Common animation specs
    val emphasizedEnter = tween<Float>(
        durationMillis = DurationMedium2,
        easing = EmphasizedEasing,
    )

    val emphasizedExit = tween<Float>(
        durationMillis = DurationShort4,
        easing = EmphasizedEasing,
    )

    val standardEnter = tween<Float>(
        durationMillis = DurationMedium1,
        easing = StandardEasing,
    )

    val standardExit = tween<Float>(
        durationMillis = DurationShort2,
        easing = StandardEasing,
    )

    // Material 3 Expressive motion patterns (2024-2025)
    val expressiveEnter = tween<Float>(
        durationMillis = DurationMedium3,
        easing = ExpressiveEasing,
    )

    val expressiveExit = tween<Float>(
        durationMillis = DurationMedium1,
        easing = ExpressiveEasing,
    )

    // Media-specific motion patterns
    val mediaControlsEnter = tween<Float>(
        durationMillis = DurationShort4,
        easing = MediaPlayEasing,
    )

    val mediaControlsExit = tween<Float>(
        durationMillis = DurationShort3,
        easing = MediaPlayEasing,
    )

    val mediaPlayEasing = tween<Float>(
        durationMillis = DurationShort4,
        easing = MediaPlayEasing,
    )

    val mediaSeekEasing = tween<Float>(
        durationMillis = DurationShort3,
        easing = MediaSeekEasing,
    )

    val carouselScroll = tween<Float>(
        durationMillis = DurationMedium2,
        easing = CarouselScrollEasing,
    )

    val fabMenuExpand = tween<Float>(
        durationMillis = DurationMedium1,
        easing = EmphasizedEasing,
    )

    val fabMenuCollapse = tween<Float>(
        durationMillis = DurationShort4,
        easing = AccelerateEasing,
    )
}
