package com.rpeters.jellyfin.ui.components.immersive

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.abs

/**
 * Scroll-aware top bar visibility with hysteresis to avoid flicker at low scroll velocity.
 *
 * @param listState The LazyListState to monitor
 * @param nearTopOffsetPx Offset from top where bar is always visible (should be >= hero height)
 * @param toggleThresholdPx Minimum scroll distance to trigger hide/show
 */
@Composable
fun rememberAutoHideTopBarVisible(
    listState: LazyListState,
    nearTopOffsetPx: Int = 140,
    toggleThresholdPx: Int = 50,
): Boolean {
    var isVisible by remember { mutableStateOf(true) }
    var previousOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState, nearTopOffsetPx, toggleThresholdPx) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            // Calculate total scroll position
            val totalOffset = if (index == 0) offset else Int.MAX_VALUE

            // Always show at top
            if (totalOffset <= nearTopOffsetPx) {
                isVisible = true
                previousOffset = totalOffset
                return@collect
            }

            // Calculate scroll delta
            val delta = totalOffset - previousOffset

            // Only toggle if scrolled enough
            if (abs(delta) >= toggleThresholdPx) {
                isVisible = delta < 0 // Show when scrolling up, hide when scrolling down
                previousOffset = totalOffset
            }
        }
    }

    return isVisible
}
