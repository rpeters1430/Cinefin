package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.rpeters.jellyfin.ui.accessibility.navigationSemantics

/**
 * Accessible navigation components with proper semantic annotations for screen readers.
 */

/**
 * Data class representing a navigation item with accessibility information.
 */
data class AccessibleNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
    val contentDescription: String? = null,
)

/**
 * Accessible navigation bar with proper semantics for each item.
 */
@Composable
fun AccessibleNavigationBar(
    items: List<AccessibleNavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.semantics {
            contentDescription = "Main navigation"
        },
    ) {
        items.forEach { item ->
            AccessibleNavigationBarItem(
                item = item,
                isSelected = currentRoute == item.route,
                onClick = { onNavigate(item.route) },
            )
        }
    }
}

/**
 * Individual navigation bar item with accessibility support.
 */
@Composable
private fun RowScope.AccessibleNavigationBarItem(
    item: AccessibleNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val description = item.contentDescription ?: item.label

    NavigationBarItem(
        icon = {
            androidx.compose.material3.Icon(
                imageVector = item.icon,
                contentDescription = null, // We handle this at the item level
                modifier = Modifier.clearAndSetSemantics { },
            )
        },
        label = {
            Text(
                text = item.label,
                modifier = Modifier.clearAndSetSemantics { },
            )
        },
        selected = isSelected,
        onClick = onClick,
        modifier = Modifier.navigationSemantics(
            label = description,
            isSelected = isSelected,
        ),
    )
}

/**
 * Accessible navigation rail for larger screens.
 */
@Composable
fun AccessibleNavigationRail(
    items: List<AccessibleNavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier.semantics {
            contentDescription = "Navigation rail"
        },
    ) {
        items.forEach { item ->
            AccessibleNavigationRailItem(
                item = item,
                isSelected = currentRoute == item.route,
                onClick = { onNavigate(item.route) },
            )
        }
    }
}

/**
 * Individual navigation rail item with accessibility support.
 */
@Composable
private fun AccessibleNavigationRailItem(
    item: AccessibleNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val description = item.contentDescription ?: item.label

    NavigationRailItem(
        icon = {
            androidx.compose.material3.Icon(
                imageVector = item.icon,
                contentDescription = null, // We handle this at the item level
                modifier = Modifier.clearAndSetSemantics { },
            )
        },
        label = {
            Text(
                text = item.label,
                modifier = Modifier.clearAndSetSemantics { },
            )
        },
        selected = isSelected,
        onClick = onClick,
        modifier = Modifier.navigationSemantics(
            label = description,
            isSelected = isSelected,
        ),
    )
}
