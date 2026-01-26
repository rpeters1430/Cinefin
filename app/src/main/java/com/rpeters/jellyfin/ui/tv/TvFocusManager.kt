@file:OptInAppExperimentalApis

package com.rpeters.jellyfin.ui.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.rpeters.jellyfin.OptInAppExperimentalApis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Data class to store focus information for a specific carousel or row
 */
data class CarouselFocusState(
    val carouselId: String,
    val focusedIndex: Int = 0,
    val scrollPosition: Int = 0,
)

/**
 * Central focus management utility for TV screens
 * Handles focus navigation, state persistence, and D-pad interactions
 */
class TvFocusManager {
    private val focusStates = mutableMapOf<String, CarouselFocusState>()
    private var currentCarouselId: String? = null
    private var currentScreenKey: String? = null

    /**
     * Save focus state for a specific carousel/row
     */
    fun saveFocusState(carouselId: String, focusedIndex: Int, scrollPosition: Int = 0) {
        focusStates[carouselId] = CarouselFocusState(
            carouselId = carouselId,
            focusedIndex = focusedIndex,
            scrollPosition = scrollPosition,
        )
        currentCarouselId = carouselId
    }

    /**
     * Restore focus state for a specific carousel/row
     */
    fun getFocusState(carouselId: String): CarouselFocusState? {
        return focusStates[carouselId]
    }

    /**
     * Clear all focus states (useful when leaving a screen)
     */
    fun clearFocusStates() {
        focusStates.clear()
        currentCarouselId = null
    }

    /**
     * Clear focus states for a specific screen
     */
    fun clearScreenFocusStates(screenKey: String) {
        focusStates.entries.removeAll { it.key.startsWith(screenKey) }
        if (currentScreenKey == screenKey) {
            currentScreenKey = null
            currentCarouselId = null
        }
    }

    /**
     * Set the current screen key for namespacing focus states
     */
    fun setCurrentScreen(screenKey: String) {
        currentScreenKey = screenKey
    }

    /**
     * Get a unique carousel ID for the current screen
     */
    fun getCarouselId(localId: String): String {
        return "${currentScreenKey ?: "default"}_$localId"
    }

    /**
     * Handle D-pad navigation between carousels
     */
    fun handleVerticalNavigation(
        direction: FocusDirection,
        focusManager: FocusManager,
        onCarouselChange: ((String) -> Unit)? = null,
    ) {
        when (direction) {
            FocusDirection.Up, FocusDirection.Down -> {
                focusManager.moveFocus(direction)
                onCarouselChange?.invoke(currentCarouselId ?: "")
            }
            else -> { /* Handle other directions if needed */ }
        }
    }
}

/**
 * Composable that provides TV focus management capabilities
 */
@Composable
fun rememberTvFocusManager(): TvFocusManager {
    return remember { TvFocusManager() }
}

/**
 * Composable that handles focus-aware carousel behavior with state persistence
 */
@Composable
fun TvFocusableCarousel(
    carouselId: String,
    focusManager: TvFocusManager,
    lazyListState: LazyListState,
    itemCount: Int,
    focusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean, Int) -> Unit = { _, _ -> },
    content: @Composable (Modifier) -> Unit,
) {
    val actualFocusRequester = remember(focusRequester) { focusRequester ?: FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    var focusedIndex by rememberSaveable { mutableIntStateOf(0) }

    // Restore focus state when carousel is created
    LaunchedEffect(carouselId) {
        val savedState = focusManager.getFocusState(carouselId)
        savedState?.let {
            focusedIndex = it.focusedIndex.coerceIn(0, itemCount - 1)
            lazyListState.scrollToItem(it.scrollPosition)
        }
    }

    // Auto-scroll to keep focused item visible
    LaunchedEffect(focusedIndex, isFocused) {
        if (isFocused && focusedIndex >= 0) {
            lazyListState.animateScrollToItem(focusedIndex)
            focusManager.saveFocusState(carouselId, focusedIndex, lazyListState.firstVisibleItemIndex)
        }
    }

    val focusModifier = Modifier
        .focusRequester(actualFocusRequester)
        .focusable()
        .onFocusChanged { focusState ->
            isFocused = focusState.isFocused
            onFocusChanged(isFocused, focusedIndex)
            if (isFocused) {
                focusManager.saveFocusState(carouselId, focusedIndex, lazyListState.firstVisibleItemIndex)
            }
        }
        .onKeyEvent { keyEvent ->
            handleCarouselKeyEvent(
                keyEvent = keyEvent,
                isFocused = isFocused,
                focusedIndex = focusedIndex,
                itemCount = itemCount,
                onIndexChanged = { newIndex ->
                    focusedIndex = newIndex.coerceIn(0, itemCount - 1)
                },
            )
        }

    content(focusModifier)
}

/**
 * Composable that handles focus-aware grid behavior with state persistence
 */
@Composable
fun TvFocusableGrid(
    gridId: String,
    focusManager: TvFocusManager,
    lazyGridState: LazyGridState,
    itemCount: Int,
    columnsCount: Int,
    focusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean, Int) -> Unit = { _, _ -> },
    content: @Composable (Modifier) -> Unit,
) {
    val actualFocusRequester = remember(focusRequester) { focusRequester ?: FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    var focusedIndex by rememberSaveable { mutableIntStateOf(0) }

    // Restore focus state when grid is created
    LaunchedEffect(gridId) {
        val savedState = focusManager.getFocusState(gridId)
        savedState?.let {
            focusedIndex = it.focusedIndex.coerceIn(0, itemCount - 1)
            lazyGridState.scrollToItem(it.scrollPosition)
        }
    }

    // Auto-scroll to keep focused item visible
    LaunchedEffect(focusedIndex, isFocused) {
        if (isFocused && focusedIndex >= 0) {
            lazyGridState.animateScrollToItem(focusedIndex)
            focusManager.saveFocusState(gridId, focusedIndex, lazyGridState.firstVisibleItemIndex)
        }
    }

    val focusModifier = Modifier
        .focusRequester(actualFocusRequester)
        .focusable()
        .onFocusChanged { focusState ->
            isFocused = focusState.isFocused
            onFocusChanged(isFocused, focusedIndex)
            if (isFocused) {
                focusManager.saveFocusState(gridId, focusedIndex, lazyGridState.firstVisibleItemIndex)
            }
        }
        .onKeyEvent { keyEvent ->
            handleGridKeyEvent(
                keyEvent = keyEvent,
                isFocused = isFocused,
                focusedIndex = focusedIndex,
                itemCount = itemCount,
                columnsCount = columnsCount,
                onIndexChanged = { newIndex ->
                    focusedIndex = newIndex.coerceIn(0, itemCount - 1)
                },
            )
        }

    content(focusModifier)
}

/**
 * Handle key events for horizontal carousel navigation
 */
private fun handleCarouselKeyEvent(
    keyEvent: KeyEvent,
    isFocused: Boolean,
    focusedIndex: Int,
    itemCount: Int,
    onIndexChanged: (Int) -> Unit,
): Boolean {
    if (!isFocused || keyEvent.type != KeyEventType.KeyDown || itemCount == 0) {
        return false
    }

    return when (keyEvent.key) {
        Key.DirectionLeft -> {
            if (focusedIndex > 0) {
                onIndexChanged(focusedIndex - 1)
                true
            } else {
                false
            }
        }
        Key.DirectionRight -> {
            if (focusedIndex < itemCount - 1) {
                onIndexChanged(focusedIndex + 1)
                true
            } else {
                false
            }
        }
        else -> false
    }
}

/**
 * Handle key events for grid navigation
 */
private fun handleGridKeyEvent(
    keyEvent: KeyEvent,
    isFocused: Boolean,
    focusedIndex: Int,
    itemCount: Int,
    columnsCount: Int,
    onIndexChanged: (Int) -> Unit,
): Boolean {
    if (!isFocused || keyEvent.type != KeyEventType.KeyDown || itemCount == 0) {
        return false
    }

    return when (keyEvent.key) {
        Key.DirectionLeft -> {
            if (focusedIndex % columnsCount > 0) {
                onIndexChanged(focusedIndex - 1)
                true
            } else {
                false
            }
        }
        Key.DirectionRight -> {
            if (focusedIndex % columnsCount < columnsCount - 1 && focusedIndex < itemCount - 1) {
                onIndexChanged(focusedIndex + 1)
                true
            } else {
                false
            }
        }
        Key.DirectionUp -> {
            if (focusedIndex >= columnsCount) {
                onIndexChanged(focusedIndex - columnsCount)
                true
            } else {
                false
            }
        }
        Key.DirectionDown -> {
            if (focusedIndex + columnsCount < itemCount) {
                onIndexChanged(focusedIndex + columnsCount)
                true
            } else {
                false
            }
        }
        else -> false
    }
}

/**
 * Composable wrapper for TV screens that handles focus management lifecycle
 */
@Composable
fun TvScreenFocusScope(
    screenKey: String,
    focusManager: TvFocusManager,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(screenKey) {
        focusManager.setCurrentScreen(screenKey)
    }

    DisposableEffect(screenKey) {
        onDispose {
            focusManager.clearScreenFocusStates(screenKey)
        }
    }

    content()
}

/**
 * Extension function to handle initial focus request with delay
 */
@Composable
fun FocusRequester.requestInitialFocus(
    condition: Boolean = true,
    delayMs: Long = 100,
) {
    LaunchedEffect(condition) {
        if (condition) {
            delay(delayMs)
            try {
                requestFocus()
            } catch (e: CancellationException) {
                throw e
            }
        }
    }
}
