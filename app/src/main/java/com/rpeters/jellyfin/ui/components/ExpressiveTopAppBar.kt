package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
 * Styled top app bar built from official Material 3 top app bar and icon button primitives.
 *
 * The title pill remains custom because there is no dedicated expressive top app bar API here.
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
 * Top app bar action button built from official Material 3 icon button components.
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
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.padding(horizontal = 4.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = tint,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
        )
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
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.padding(horizontal = 4.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = tint,
        ),
    ) {
        if (isLoading) {
            Box(modifier = Modifier.size(24.dp)) {
                ExpressiveCircularLoading(
                    size = 24.dp,
                    color = tint,
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
            )
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
    Box(modifier = modifier.padding(horizontal = 4.dp)) {
        FilledTonalIconButton(
            onClick = { onExpandedChange(true) },
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = tint,
            ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
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

/**
 * Circular back navigation button for use in ExpressiveTopAppBar
 */
@Composable
fun ExpressiveBackNavigationIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.padding(start = 8.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(id = R.string.navigate_up),
        )
    }
}
