package com.rpeters.jellyfin.ui.components.immersive

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlin.math.abs

// Scales firstVisibleItemIndex into a synthetic scroll position so comparisons stay
// monotonic past the first item, instead of collapsing every non-zero index into a single
// Int.MAX_VALUE sentinel (which froze the hysteresis delta at 0 and left the bar stuck).
// Comfortably larger than any realistic per-item pixel offset.
private const val INDEX_POSITION_SCALE = 1_000_000L

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
    var previousPosition by remember { mutableLongStateOf(0L) }

    LaunchedEffect(listState, nearTopOffsetPx, toggleThresholdPx) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            // Always show at top
            if (index == 0 && offset <= nearTopOffsetPx) {
                isVisible = true
                previousPosition = offset.toLong()
                return@collect
            }

            val position = index.toLong() * INDEX_POSITION_SCALE + offset
            val delta = position - previousPosition

            // Only toggle if scrolled enough
            if (abs(delta) >= toggleThresholdPx) {
                isVisible = delta < 0 // Show when scrolling up, hide when scrolling down
                previousPosition = position
            }
        }
    }

    return isVisible
}

/**
 * Scroll-aware top bar visibility for LazyVerticalGrid.
 */
@Composable
fun rememberAutoHideTopBarVisible(
    gridState: LazyGridState,
    nearTopOffsetPx: Int = 140,
    toggleThresholdPx: Int = 50,
): Boolean {
    var isVisible by remember { mutableStateOf(true) }
    var previousPosition by remember { mutableLongStateOf(0L) }

    LaunchedEffect(gridState, nearTopOffsetPx, toggleThresholdPx) {
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            // Always show at top
            if (index == 0 && offset <= nearTopOffsetPx) {
                isVisible = true
                previousPosition = offset.toLong()
                return@collect
            }

            val position = index.toLong() * INDEX_POSITION_SCALE + offset
            val delta = position - previousPosition

            // Only toggle if scrolled enough
            if (abs(delta) >= toggleThresholdPx) {
                isVisible = delta < 0 // Show when scrolling up, hide when scrolling down
                previousPosition = position
            }
        }
    }

    return isVisible
}
