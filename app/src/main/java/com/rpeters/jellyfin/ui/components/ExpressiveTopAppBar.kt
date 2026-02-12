package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.R

/**
 * Material 3 Expressive Top App Bar
 *
 * A styled top app bar with circular surfaces for the title and action buttons.
 * Provides visual consistency across screens with shadow elevation and proper spacing.
 */
@Composable
fun ExpressiveTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        },
        navigationIcon = navigationIcon ?: {},
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
        ),
        modifier = modifier,
    )
}

/**
 * Circular action button for use in ExpressiveTopAppBar
 *
 * @param icon The icon to display
 * @param contentDescription Accessibility description
 * @param onClick Click handler
 * @param tint Icon tint color
 * @param modifier Optional modifier
 */
@Composable
fun ExpressiveTopAppBarAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = modifier.padding(horizontal = 4.dp),
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
            )
        }
    }
}

/**
 * Circular action button with loading state for use in ExpressiveTopAppBar
 *
 * @param icon The icon to display when not loading
 * @param contentDescription Accessibility description
 * @param onClick Click handler
 * @param isLoading Whether to show loading indicator
 * @param tint Icon tint color
 * @param modifier Optional modifier
 */
@Composable
fun ExpressiveTopAppBarRefreshAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = modifier.padding(horizontal = 4.dp),
    ) {
        if (isLoading) {
            Box(modifier = Modifier.padding(12.dp)) {
                ExpressiveCircularLoading(
                    size = 24.dp,
                    color = tint,
                )
            }
        } else {
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                )
            }
        }
    }
}

/**
 * Circular action button with dropdown menu for use in ExpressiveTopAppBar
 *
 * @param icon The icon to display
 * @param contentDescription Accessibility description
 * @param expanded Whether the dropdown is expanded
 * @param onExpandedChange Callback when expanded state changes
 * @param tint Icon tint color
 * @param modifier Optional modifier
 * @param menuContent Content of the dropdown menu
 */
@Composable
fun ExpressiveTopAppBarMenuAction(
    icon: ImageVector,
    contentDescription: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    menuContent: @Composable () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = modifier.padding(horizontal = 4.dp),
    ) {
        Box {
            IconButton(onClick = { onExpandedChange(true) }) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
            ) {
                menuContent()
            }
        }
    }
}

/**
 * Circular back navigation button for use in ExpressiveTopAppBar
 */
@Composable
fun ExpressiveBackNavigationIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = modifier.padding(start = 8.dp),
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(id = R.string.navigate_up),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
