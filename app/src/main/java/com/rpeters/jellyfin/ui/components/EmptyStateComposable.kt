package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A reusable empty state component that displays an icon, title, description, and optional action.
 *
 * Follows Material 3 design principles for empty states and error messages.
 *
 * @param icon The icon to display (optional, defaults based on type)
 * @param title The main title text
 * @param description Optional description text providing additional context
 * @param actionLabel Optional action button label
 * @param onActionClick Optional action button click handler
 * @param type The visual style of the empty state (Info, Error, NoResults)
 * @param modifier Modifier for the container
 */
@Composable
fun EmptyStateComposable(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    type: EmptyStateType = EmptyStateType.Info,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            // Icon
            val displayIcon = icon ?: type.defaultIcon
            Icon(
                imageVector = displayIcon,
                contentDescription = null, // Decorative, title provides context
                modifier = Modifier.size(64.dp),
                tint = when (type) {
                    EmptyStateType.Error -> MaterialTheme.colorScheme.error
                    EmptyStateType.Info, EmptyStateType.NoResults -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = when (type) {
                    EmptyStateType.Error -> MaterialTheme.colorScheme.error
                    EmptyStateType.Info, EmptyStateType.NoResults -> MaterialTheme.colorScheme.onSurface
                },
            )

            // Description
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Action button
            if (actionLabel != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(8.dp))
                when (type) {
                    EmptyStateType.Error -> {
                        Button(onClick = onActionClick) {
                            Text(text = actionLabel)
                        }
                    }
                    EmptyStateType.Info, EmptyStateType.NoResults -> {
                        TextButton(onClick = onActionClick) {
                            Text(text = actionLabel)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Visual style variants for empty states
 */
enum class EmptyStateType(val defaultIcon: ImageVector) {
    /**
     * Informational empty state (e.g., "No favorites yet")
     */
    Info(Icons.Default.Info),

    /**
     * Error state (e.g., "Failed to load data")
     */
    Error(Icons.Default.ErrorOutline),

    /**
     * No search results state
     */
    NoResults(Icons.Default.SearchOff),
}
