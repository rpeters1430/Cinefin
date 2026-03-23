@file:OptInAppExperimentalApis

package com.rpeters.jellyfin.ui.tv

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
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

/**
 * Data class to store focus information for a specific carousel or row
 */
data class CarouselFocusState(
    val carouselId: String,
    val focusedIndex: Int = 0,
    val scrollPosition: Int = 0,
    val focusedItemKey: String? = null,
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
    fun saveFocusState(
        carouselId: String,
        focusedIndex: Int,
        scrollPosition: Int = 0,
        focusedItemKey: String? = null,
    ) {
        focusStates[carouselId] = CarouselFocusState(
            carouselId = carouselId,
            focusedIndex = focusedIndex,
            scrollPosition = scrollPosition,
            focusedItemKey = focusedItemKey,
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

val LocalTvFocusManager = compositionLocalOf<TvFocusManager?> { null }

/**
 * Composable that provides TV focus management capabilities
 */
@Composable
fun rememberTvFocusManager(): TvFocusManager {
    return LocalTvFocusManager.current ?: remember { TvFocusManager() }
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
    itemKeys: List<String>? = null,
    focusRequester: FocusRequester? = null,
    onExitLeft: (() -> Boolean)? = null,
    onExitRight: (() -> Boolean)? = null,
    onExitUp: (() -> Boolean)? = null,
    onExitDown: (() -> Boolean)? = null,
    onFocusChanged: (Boolean, Int) -> Unit = { _, _ -> },
    content: @Composable (Modifier, Int, List<FocusRequester>) -> Unit,
) {
    val actualFocusRequester = remember(focusRequester) { focusRequester ?: FocusRequester() }
    val itemFocusRequesters = remember(itemCount) { List(itemCount) { FocusRequester() } }
    var hasFocus by remember { mutableStateOf(false) }
    var focusedIndex by rememberSaveable { mutableIntStateOf(0) }

    // Restore focus state when carousel is created
    LaunchedEffect(carouselId) {
        val savedState = focusManager.getFocusState(carouselId)
        savedState?.let {
            focusedIndex = restoreFocusedIndex(
                savedIndex = it.focusedIndex,
                savedItemKey = it.focusedItemKey,
                itemKeys = itemKeys,
                itemCount = itemCount,
            )
            lazyListState.scrollToItem(it.scrollPosition)
        }
    }

    // Auto-scroll to keep focused item visible when the user navigates.
    // Keyed only on focusedIndex/hasFocus so that items loading in (itemCount changing)
    // does not trigger a premature scroll before the layout has settled.
    LaunchedEffect(focusedIndex, hasFocus) {
        if (hasFocus && focusedIndex in 0 until itemCount) {
            lazyListState.animateScrollToItem(focusedIndex)
            focusManager.saveFocusState(
                carouselId = carouselId,
                focusedIndex = focusedIndex,
                scrollPosition = lazyListState.firstVisibleItemIndex,
                focusedItemKey = itemKeys?.getOrNull(focusedIndex),
            )
            // Removed requestFocus() here — TV Compose handles internal item focus
            // natively; manually calling requestFocus() during a scroll animation
            // fights with the framework and causes items to fly off-screen on load.
        }
    }

    val focusModifier = Modifier
        .focusRequester(actualFocusRequester)
        .onFocusChanged { focusState ->
            hasFocus = focusState.hasFocus
            onFocusChanged(hasFocus, focusedIndex)
            if (hasFocus) {
                focusManager.saveFocusState(
                    carouselId = carouselId,
                    focusedIndex = focusedIndex,
                    scrollPosition = lazyListState.firstVisibleItemIndex,
                    focusedItemKey = itemKeys?.getOrNull(focusedIndex),
                )
            }
        }
        .onKeyEvent { keyEvent ->
            handleCarouselKeyEvent(
                keyEvent = keyEvent,
                isFocused = hasFocus,
                focusedIndex = focusedIndex,
                itemCount = itemCount,
                onExitLeft = onExitLeft,
                onExitRight = onExitRight,
                onExitUp = onExitUp,
                onExitDown = onExitDown,
                onIndexChanged = { newIndex ->
                    focusedIndex = newIndex.coerceIn(0, itemCount - 1)
                },
            )
        }

    content(focusModifier, focusedIndex, itemFocusRequesters)
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
    itemKeys: List<String>? = null,
    focusRequester: FocusRequester? = null,
    onExitLeft: (() -> Boolean)? = null,
    onExitRight: (() -> Boolean)? = null,
    onExitUp: (() -> Boolean)? = null,
    onExitDown: (() -> Boolean)? = null,
    onFocusChanged: (Boolean, Int) -> Unit = { _, _ -> },
    content: @Composable (Modifier, Int, List<FocusRequester>) -> Unit,
) {
    val actualFocusRequester = remember(focusRequester) { focusRequester ?: FocusRequester() }
    val itemFocusRequesters = remember(itemCount) { List(itemCount) { FocusRequester() } }
    var hasFocus by remember { mutableStateOf(false) }
    var focusedIndex by rememberSaveable { mutableIntStateOf(0) }

    // Restore focus state when grid is created
    LaunchedEffect(gridId) {
        val savedState = focusManager.getFocusState(gridId)
        savedState?.let {
            focusedIndex = restoreFocusedIndex(
                savedIndex = it.focusedIndex,
                savedItemKey = it.focusedItemKey,
                itemKeys = itemKeys,
                itemCount = itemCount,
            )
            lazyGridState.scrollToItem(it.scrollPosition)
        }
    }

    // Auto-scroll to keep focused item visible when the user navigates.
    // Keyed only on focusedIndex/hasFocus so that items loading in (itemCount changing)
    // does not trigger a premature scroll before the layout has settled.
    LaunchedEffect(focusedIndex, hasFocus) {
        if (hasFocus && focusedIndex in 0 until itemCount) {
            lazyGridState.animateScrollToItem(focusedIndex)
            focusManager.saveFocusState(
                carouselId = gridId,
                focusedIndex = focusedIndex,
                scrollPosition = lazyGridState.firstVisibleItemIndex,
                focusedItemKey = itemKeys?.getOrNull(focusedIndex),
            )
            // Removed requestFocus() here — TV Compose handles internal item focus
            // natively; manually calling requestFocus() during a scroll animation
            // fights with the framework and causes items to fly off-screen on load.
        }
    }

    val focusModifier = Modifier
        .focusRequester(actualFocusRequester)
        .onFocusChanged { focusState ->
            hasFocus = focusState.hasFocus
            onFocusChanged(hasFocus, focusedIndex)
            if (hasFocus) {
                focusManager.saveFocusState(
                    carouselId = gridId,
                    focusedIndex = focusedIndex,
                    scrollPosition = lazyGridState.firstVisibleItemIndex,
                    focusedItemKey = itemKeys?.getOrNull(focusedIndex),
                )
            }
        }
        .onKeyEvent { keyEvent ->
            handleGridKeyEvent(
                keyEvent = keyEvent,
                isFocused = hasFocus,
                focusedIndex = focusedIndex,
                itemCount = itemCount,
                columnsCount = columnsCount,
                onExitLeft = onExitLeft,
                onExitRight = onExitRight,
                onExitUp = onExitUp,
                onExitDown = onExitDown,
                onIndexChanged = { newIndex ->
                    focusedIndex = newIndex.coerceIn(0, itemCount - 1)
                },
            )
        }

    content(focusModifier, focusedIndex, itemFocusRequesters)
}

private fun restoreFocusedIndex(
    savedIndex: Int,
    savedItemKey: String?,
    itemKeys: List<String>?,
    itemCount: Int,
): Int {
    if (itemCount <= 0) return 0
    if (savedItemKey != null && itemKeys != null) {
        val keyIndex = itemKeys.indexOf(savedItemKey)
        if (keyIndex >= 0) {
            return keyIndex
        }
    }
    return savedIndex.coerceIn(0, itemCount - 1)
}

/**
 * Handle key events for horizontal carousel navigation
 */
private fun handleCarouselKeyEvent(
    keyEvent: KeyEvent,
    isFocused: Boolean,
    focusedIndex: Int,
    itemCount: Int,
    onExitLeft: (() -> Boolean)?,
    onExitRight: (() -> Boolean)?,
    onExitUp: (() -> Boolean)?,
    onExitDown: (() -> Boolean)?,
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
                onExitLeft?.invoke() ?: false
            }
        }
        Key.DirectionRight -> {
            if (focusedIndex < itemCount - 1) {
                onIndexChanged(focusedIndex + 1)
                true
            } else {
                onExitRight?.invoke() ?: false
            }
        }
        Key.DirectionUp -> onExitUp?.invoke() ?: false
        Key.DirectionDown -> onExitDown?.invoke() ?: false
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
    onExitLeft: (() -> Boolean)?,
    onExitRight: (() -> Boolean)?,
    onExitUp: (() -> Boolean)?,
    onExitDown: (() -> Boolean)?,
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
                onExitLeft?.invoke() ?: false
            }
        }
        Key.DirectionRight -> {
            if (focusedIndex % columnsCount < columnsCount - 1 && focusedIndex < itemCount - 1) {
                onIndexChanged(focusedIndex + 1)
                true
            } else {
                onExitRight?.invoke() ?: false
            }
        }
        Key.DirectionUp -> {
            if (focusedIndex >= columnsCount) {
                onIndexChanged(focusedIndex - columnsCount)
                true
            } else {
                onExitUp?.invoke() ?: false
            }
        }
        Key.DirectionDown -> {
            if (focusedIndex + columnsCount < itemCount) {
                onIndexChanged(focusedIndex + columnsCount)
                true
            } else {
                onExitDown?.invoke() ?: false
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
