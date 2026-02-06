package com.rpeters.jellyfin.ui.components.immersive

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * Immersive scaffold with auto-hiding navigation and support for floating action buttons.
 * Designed for media-first layouts with minimal chrome.
 *
 * Features:
 * - Auto-hiding top app bar (translucent)
 * - Auto-hiding bottom nav bar
 * - Support for floating action buttons
 * - Scroll-aware behavior
 * - Full-bleed content area
 *
 * @param modifier Modifier for the scaffold
 * @param topBarVisible Whether the top bar should be visible
 * @param topBarTitle Title for the top app bar
 * @param topBarNavigationIcon Navigation icon for the top bar
 * @param topBarActions Action icons for the top bar
 * @param topBarTranslucent Whether the top bar should be translucent
 * @param bottomBarVisible Whether the bottom bar should be visible
 * @param bottomBarItems Items for the bottom navigation bar
 * @param selectedBottomBarItem Currently selected bottom bar item
 * @param onBottomBarItemSelected Callback for bottom bar item selection
 * @param floatingActionButton Floating action button content
 * @param floatingActionButtonPosition Position of the FAB
 * @param containerColor Background color of the scaffold
 * @param contentColor Content color for the scaffold
 * @param scrollBehavior Scroll behavior for the top app bar
 * @param content Main content of the scaffold
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImmersiveScaffold(
    modifier: Modifier = Modifier,
    topBarVisible: Boolean = true,
    topBarTitle: String = "",
    topBarNavigationIcon: @Composable () -> Unit = {},
    topBarActions: @Composable RowScope.() -> Unit = {},
    topBarTranslucent: Boolean = true,
    bottomBarVisible: Boolean = true,
    bottomBarItems: List<NavBarItem> = emptyList(),
    selectedBottomBarItem: Int = 0,
    onBottomBarItemSelected: (Int) -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val finalScrollBehavior = scrollBehavior ?: TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.then(
            if (scrollBehavior != null) {
                Modifier.nestedScroll(finalScrollBehavior.nestedScrollConnection)
            } else {
                Modifier
            },
        ),
        topBar = {
            if (topBarTitle.isNotEmpty()) {
                AutoHideTopAppBar(
                    visible = topBarVisible,
                    title = topBarTitle,
                    navigationIcon = topBarNavigationIcon,
                    actions = topBarActions,
                    translucent = topBarTranslucent,
                    scrollBehavior = finalScrollBehavior,
                )
            }
        },
        bottomBar = {
            if (bottomBarItems.isNotEmpty()) {
                AutoHideBottomNavBar(
                    visible = bottomBarVisible,
                    items = bottomBarItems,
                    selectedItem = selectedBottomBarItem,
                    onItemSelected = onBottomBarItemSelected,
                )
            }
        },
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // Edge-to-edge for immersive experience
    ) { paddingValues ->
        content(paddingValues)
    }
}

/**
 * Simplified immersive scaffold for screens without navigation.
 * Perfect for detail screens and full-screen media views.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleImmersiveScaffold(
    modifier: Modifier = Modifier,
    floatingActionButton: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable (PaddingValues) -> Unit,
) {
    ImmersiveScaffold(
        modifier = modifier,
        topBarVisible = false,
        bottomBarVisible = false,
        floatingActionButton = floatingActionButton,
        containerColor = containerColor,
        content = content,
    )
}
