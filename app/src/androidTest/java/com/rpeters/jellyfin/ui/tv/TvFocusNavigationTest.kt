package com.rpeters.jellyfin.ui.tv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class TvFocusNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sharedFocusManager_restoresFocusedGridItem_whenScreenReturns() {
        val sharedManager = TvFocusManager()

        composeRule.setContent {
            CompositionLocalProvider(LocalTvFocusManager provides sharedManager) {
                var showLibraryScreen by remember { mutableStateOf(true) }

                if (showLibraryScreen) {
                    RestorableGridScreen(
                        screenKey = "library_screen",
                        onToggleScreen = { showLibraryScreen = false },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .testTag("detail_screen")
                            .focusable(),
                    )

                    LaunchedEffect(Unit) {
                        showLibraryScreen = true
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("grid_item_0").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("grid_item_0").assertIsFocused()

        composeRule.onNodeWithTag("grid_item_0").performKeyInput {
            pressKey(androidx.compose.ui.input.key.Key.DirectionRight)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("grid_item_1").assertIsFocused()

        composeRule.runOnIdle {
            // toggle away and back to trigger disposal/recreation
        }
        composeRule.onNodeWithTag("grid_item_1").performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("grid_item_1").assertIsFocused()
    }

    @Test
    fun gridExitUp_movesFocusToControl() {
        composeRule.setContent {
            val manager = remember { TvFocusManager() }
            val controlRequester = remember { FocusRequester() }
            val gridRequester = remember { FocusRequester() }

            Column {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .testTag("top_control")
                        .focusRequester(controlRequester)
                        .focusable(),
                )

                TestGrid(
                    manager = manager,
                    screenKey = "search_results_screen",
                    focusRequester = gridRequester,
                    onExitUp = {
                        controlRequester.requestFocus()
                        true
                    },
                )
            }

            gridRequester.requestInitialFocus(delayMs = 50)
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("grid_item_0").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("grid_item_0").assertIsFocused()

        composeRule.onNodeWithTag("grid_item_0").performKeyInput {
            pressKey(androidx.compose.ui.input.key.Key.DirectionUp)
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("top_control").assertIsFocused()
    }

    @Test
    fun carouselExitLeft_movesFocusToDrawerTarget() {
        composeRule.setContent {
            val manager = remember { TvFocusManager() }
            val drawerRequester = remember { FocusRequester() }
            val carouselRequester = remember { FocusRequester() }

            Column {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .testTag("drawer_target")
                        .focusRequester(drawerRequester)
                        .focusable(),
                )

                TestCarousel(
                    manager = manager,
                    screenKey = "home_screen",
                    focusRequester = carouselRequester,
                    onExitLeft = {
                        drawerRequester.requestFocus()
                        true
                    },
                )
            }

            carouselRequester.requestInitialFocus(delayMs = 50)
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("carousel_item_0").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("carousel_item_0").assertIsFocused()

        composeRule.onNodeWithTag("carousel_item_0").performKeyInput {
            pressKey(androidx.compose.ui.input.key.Key.DirectionLeft)
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("drawer_target").assertIsFocused()
    }
}

@Composable
private fun RestorableGridScreen(
    screenKey: String,
    onToggleScreen: () -> Unit,
) {
    val manager = rememberTvFocusManager()
    val gridRequester = remember { FocusRequester() }

    TvScreenFocusScope(
        screenKey = screenKey,
        focusManager = manager,
    ) {
        TestGrid(
            manager = manager,
            screenKey = screenKey,
            focusRequester = gridRequester,
            onItemClick = { index ->
                if (index == 1) {
                    onToggleScreen()
                }
            },
        )
    }

    gridRequester.requestInitialFocus(delayMs = 50)
}

@Composable
private fun TestGrid(
    manager: TvFocusManager,
    screenKey: String,
    focusRequester: FocusRequester,
    onExitUp: (() -> Boolean)? = null,
    onItemClick: (Int) -> Unit = {},
) {
    val items = remember { List(4) { it } }
    val gridState = rememberLazyGridState()

    TvFocusableGrid(
        gridId = "${screenKey}_grid",
        focusManager = manager,
        lazyGridState = gridState,
        itemCount = items.size,
        columnsCount = 2,
        focusRequester = focusRequester,
        onExitUp = onExitUp,
    ) { focusModifier, _, itemFocusRequesters ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = focusModifier,
        ) {
            itemsIndexed(items) { index, _ ->
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .testTag("grid_item_$index")
                        .focusRequester(itemFocusRequesters[index])
                        .focusable()
                        .clickable { onItemClick(index) },
                )
            }
        }
    }
}

@Composable
private fun TestCarousel(
    manager: TvFocusManager,
    screenKey: String,
    focusRequester: FocusRequester,
    onExitLeft: (() -> Boolean)? = null,
) {
    val items = remember { List(3) { it } }
    val listState = rememberLazyListState()

    TvFocusableCarousel(
        carouselId = "${screenKey}_carousel",
        focusManager = manager,
        lazyListState = listState,
        itemCount = items.size,
        focusRequester = focusRequester,
        onExitLeft = onExitLeft,
    ) { focusModifier, _, itemFocusRequesters ->
        LazyRow(
            state = listState,
            modifier = focusModifier,
        ) {
            itemsIndexed(items) { index, _ ->
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .testTag("carousel_item_$index")
                        .focusRequester(itemFocusRequesters[index])
                        .focusable(),
                )
            }
        }
    }
}
