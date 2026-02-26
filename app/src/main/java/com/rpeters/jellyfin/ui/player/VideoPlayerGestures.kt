package com.rpeters.jellyfin.ui.player

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

object VideoPlayerGestureConstants {
    const val SEEK_AMOUNT_MS = 10_000L
    const val MIN_VERTICAL_DRAG_PX = 5f
    const val NORMALIZATION_FRACTION = 0.5f
    const val BRIGHTNESS_MIN_DELTA = 0.01f
    const val DEFAULT_BRIGHTNESS = 0.5f
    const val GESTURE_UPDATE_MIN_INTERVAL_MS = 50L
    const val CENTER_TAP_BOUNDARY_FRACTION = 0.33f
    val PLAYBACK_SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
}

/**
 * Modifier extension to handle video player gestures (tap, double-tap, vertical drag).
 */
fun Modifier.videoPlayerGestures(
    onTap: (isCenterTap: Boolean) -> Unit,
    onDoubleTap: (isRightSide: Boolean) -> Unit,
    onVerticalDrag: (isLeftSide: Boolean, deltaY: Float) -> Unit,
): Modifier = this
    .pointerInput(Unit) {
        detectTapGestures(
            onTap = { offset ->
                val centerBoundary = VideoPlayerGestureConstants.CENTER_TAP_BOUNDARY_FRACTION
                val isCenterTap =
                    offset.x in (size.width * centerBoundary)..(size.width * (1f - centerBoundary)) &&
                        offset.y in (size.height * centerBoundary)..(size.height * (1f - centerBoundary))
                onTap(isCenterTap)
            },
            onDoubleTap = { offset ->
                onDoubleTap(offset.x > size.width / 2)
            },
        )
    }
    .pointerInput(Unit) {
        detectDragGestures { change, _ ->
            val deltaY = change.previousPosition.y - change.position.y
            onVerticalDrag(change.position.x < size.width / 2, deltaY)
        }
    }
