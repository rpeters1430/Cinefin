package com.example.jellyfinandroid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.jellyfinandroid.ui.accessibility.buttonSemantics
import com.example.jellyfinandroid.ui.accessibility.headingSemantics
import com.example.jellyfinandroid.ui.accessibility.imageSemantics

/**
 * Accessible error components with proper semantic annotations and screen reader support.
 */

/**
 * A comprehensive error state component with accessibility features.
 */
@Composable
fun AccessibleErrorState(
    title: String,
    message: String,
    onRetry: (() -> Unit)? = null,
    retryText: String = "Retry",
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null, // Decorative icon
            modifier = Modifier
                .size(64.dp)
                .imageSemantics(null, isDecorative = true),
            tint = MaterialTheme.colorScheme.error,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.headingSemantics(level = 1),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        if (onRetry != null) {
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRetry,
                modifier = Modifier.buttonSemantics(
                    description = "$retryText. Double tap to reload content",
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .imageSemantics(null, isDecorative = true),
                )
                Text(
                    text = retryText,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * Network error state with specific messaging for network issues.
 */
@Composable
fun NetworkErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AccessibleErrorState(
        title = "Network Error",
        message = "Unable to connect to the server. Please check your internet connection and try again.",
        onRetry = onRetry,
        retryText = "Retry Connection",
        modifier = modifier,
    )
}

/**
 * Server error state for when the Jellyfin server is unreachable.
 */
@Composable
fun ServerErrorState(
    serverUrl: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = if (serverUrl != null) {
        "Unable to connect to the Jellyfin server at $serverUrl. The server may be offline or unreachable."
    } else {
        "Unable to connect to the Jellyfin server. Please check your server configuration."
    }

    AccessibleErrorState(
        title = "Server Unavailable",
        message = message,
        onRetry = onRetry,
        retryText = "Retry Connection",
        modifier = modifier,
    )
}

/**
 * Authentication error state for login issues.
 */
@Composable
fun AuthenticationErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AccessibleErrorState(
        title = "Authentication Failed",
        message = "Your login credentials are invalid or have expired. Please log in again.",
        onRetry = onRetry,
        retryText = "Try Again",
        modifier = modifier,
    )
}

/**
 * Empty state component for when no content is available.
 */
@Composable
fun AccessibleEmptyState(
    title: String,
    message: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.headingSemantics(level = 1),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onAction,
                modifier = Modifier.buttonSemantics(
                    description = "$actionText. Double tap to $actionText",
                ),
            ) {
                Text(text = actionText)
            }
        }
    }
}

/**
 * Loading error state for when content failed to load.
 */
@Composable
fun LoadingErrorState(
    contentType: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AccessibleErrorState(
        title = "Failed to Load $contentType",
        message = "We couldn't load the $contentType right now. Please try again.",
        onRetry = onRetry,
        retryText = "Retry",
        modifier = modifier,
    )
}
