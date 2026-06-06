package com.rpeters.jellyfin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.rpeters.jellyfin.ui.navigation.BottomNavItem

/**
 * An expressive floating navigation bar for the phone experience.
 * Replaces the standard bottom navigation bar with a floating toolbar-style layout.
 */
@Composable
fun ExpressiveFloatingNavBar(
    items: List<BottomNavItem>,
    currentDestination: NavDestination?,
    onNavigate: (BottomNavItem) -> Unit,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val useCompactButtons = items.size > 5

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        // Outer container handles the floating gap and centering
        // Increased horizontal padding for a more "pill" floating look
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            ExpressiveBlurSurface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                modifier = Modifier
                    .wrapContentWidth(),
                borderRadius = 28.dp,
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                        ),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEach { item ->
                        val isSelected = currentDestination?.hierarchy?.any {
                            it.route == item.route
                        } == true

                        val haptics = com.rpeters.jellyfin.ui.utils.rememberExpressiveHaptics()

                        ExpressiveNavBarButton(
                            icon = item.icon.icon,
                            contentDescription = item.title,
                            label = item.title,
                            selected = isSelected,
                            useCompactLayout = useCompactButtons,
                            onClick = {
                                haptics.lightClick()
                                onNavigate(item)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressiveNavBarButton(
    icon: ImageVector,
    contentDescription: String,
    label: String,
    selected: Boolean,
    useCompactLayout: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        },
        modifier = Modifier.size(
            height = 48.dp,
            width = if (selected && !useCompactLayout) 116.dp else 48.dp,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
            if (selected && !useCompactLayout) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 6.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
