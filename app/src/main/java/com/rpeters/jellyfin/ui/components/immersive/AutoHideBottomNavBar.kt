package com.rpeters.jellyfin.ui.components.immersive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Auto-hiding expressive floating navigation bar for immersive layouts.
 * Hides on scroll down, shows on scroll up.
 */
@Composable
fun AutoHideBottomNavBar(
    visible: Boolean,
    items: List<NavBarItem>,
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = com.rpeters.jellyfin.ui.utils.rememberExpressiveHaptics()
    
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
            initialOffsetY = { it },
        ) + fadeIn(),
        exit = slideOutVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
            targetOffsetY = { it },
        ) + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            tonalElevation = 2.dp,
            modifier = Modifier
                .padding(bottom = 12.dp, start = 16.dp, end = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        val isSelected = index == selectedItem
                        
                        Surface(
                            onClick = { 
                                haptics.lightClick()
                                onItemSelected(index) 
                            },
                            shape = MaterialTheme.shapes.extraLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f) else Color.Transparent,
                            modifier = Modifier.size(height = 40.dp, width = if (isSelected) 110.dp else 40.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.icon,
                                    contentDescription = item.label,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp) // Reduced from 20dp
                                )
                                if (isSelected) {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelSmall, // Smaller text
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 4.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
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
