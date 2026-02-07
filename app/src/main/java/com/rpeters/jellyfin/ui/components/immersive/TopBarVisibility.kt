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
 */
@Composable
fun rememberAutoHideTopBarVisible(
    listState: LazyListState,
    nearTopOffsetPx: Int = 140,
    toggleThresholdPx: Int = 24,
): Boolean {
    var isVisible by remember { mutableStateOf(true) }
    var previousIndex by remember { mutableIntStateOf(listState.firstVisibleItemIndex) }
    var previousOffset by remember { mutableIntStateOf(listState.firstVisibleItemScrollOffset) }
    var directionAccumulatorPx by remember { mutableIntStateOf(0) }
    var lastDirection by remember { mutableIntStateOf(0) } // -1 up, +1 down

    LaunchedEffect(listState, nearTopOffsetPx, toggleThresholdPx) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val isNearTop = index == 0 && offset <= nearTopOffsetPx
                if (isNearTop) {
                    isVisible = true
                    directionAccumulatorPx = 0
                    lastDirection = 0
                    previousIndex = index
                    previousOffset = offset
                    return@collect
                }

                val direction = when {
                    index > previousIndex -> 1
                    index < previousIndex -> -1
                    offset > previousOffset -> 1
                    offset < previousOffset -> -1
                    else -> 0
                }

                if (direction != 0) {
                    if (direction != lastDirection) {
                        directionAccumulatorPx = 0
                        lastDirection = direction
                    }

                    val deltaPx = if (index == previousIndex) {
                        abs(offset - previousOffset)
                    } else {
                        toggleThresholdPx
                    }
                    directionAccumulatorPx += deltaPx

                    if (directionAccumulatorPx >= toggleThresholdPx) {
                        isVisible = direction < 0
                        directionAccumulatorPx = 0
                    }
                }

                previousIndex = index
                previousOffset = offset
            }
    }

    return isVisible
}
