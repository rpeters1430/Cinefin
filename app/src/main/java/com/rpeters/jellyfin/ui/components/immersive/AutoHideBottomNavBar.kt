package com.rpeters.jellyfin.ui.components.immersive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Auto-hiding bottom navigation bar for immersive layouts.
 * Hides on scroll down, shows on scroll up.
 *
 * @param visible Whether the nav bar should be visible
 * @param items List of navigation items to display
 * @param selectedItem Currently selected item index
 * @param onItemSelected Callback when an item is selected
 */
@Composable
fun AutoHideBottomNavBar(
    visible: Boolean,
    items: List<NavBarItem>,
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
            initialOffsetY = { it },
        ),
        exit = slideOutVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
            targetOffsetY = { it },
        ),
    ) {
        NavigationBar(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
            windowInsets = WindowInsets.navigationBars,
        ) {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = if (index == selectedItem) item.selectedIcon else item.icon,
                            contentDescription = item.label,
                        )
                    },
                    label = { Text(item.label) },
                    selected = index == selectedItem,
                    onClick = { onItemSelected(index) },
                )
            }
        }
    }
}

/**
 * Auto-hiding bottom nav bar with scroll detection.
 * Automatically hides/shows based on scroll direction.
 *
 * @param scrollOffset Current scroll offset (increases as user scrolls down)
 * @param items List of navigation items to display
 * @param selectedItem Currently selected item index
 * @param onItemSelected Callback when an item is selected
 * @param hideThreshold Scroll distance required to hide nav bar (default 50dp)
 */
@Composable
fun ScrollAwareBottomNavBar(
    scrollOffset: Float,
    items: List<NavBarItem>,
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hideThreshold: Float = 50f,
) {
    var previousScrollOffset by remember { mutableFloatStateOf(0f) }

    val visible by remember(scrollOffset) {
        derivedStateOf {
            val delta = scrollOffset - previousScrollOffset
            val shouldShow = when {
                scrollOffset < hideThreshold -> true // Always show at top
                delta < -10f -> true // Scrolling up
                delta > 10f -> false // Scrolling down
                else -> scrollOffset < hideThreshold // Maintain state unless at top
            }
            previousScrollOffset = scrollOffset
            shouldShow
        }
    }

    AutoHideBottomNavBar(
        visible = visible,
        items = items,
        selectedItem = selectedItem,
        onItemSelected = onItemSelected,
        modifier = modifier,
    )
}

data class NavBarItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val route: String,
)
