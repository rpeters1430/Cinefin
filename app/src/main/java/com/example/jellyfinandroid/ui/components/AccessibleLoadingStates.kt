package com.example.jellyfinandroid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.jellyfinandroid.ui.accessibility.progressSemantics

/**
 * Accessible loading components with proper semantic annotations for screen readers.
 */

/**
 * A centered loading state with accessibility support.
 */
@Composable
fun AccessibleLoadingState(
    message: String = "Loading",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .progressSemantics(message),
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/**
 * Loading state specifically for libraries.
 */
@Composable
fun LibraryLoadingState(
    modifier: Modifier = Modifier,
) {
    AccessibleLoadingState(
        message = "Loading your media libraries",
        modifier = modifier,
    )
}

/**
 * Loading state for media content.
 */
@Composable
fun MediaLoadingState(
    contentType: String = "content",
    modifier: Modifier = Modifier,
) {
    AccessibleLoadingState(
        message = "Loading $contentType",
        modifier = modifier,
    )
}

/**
 * Loading state for search results.
 */
@Composable
fun SearchLoadingState(
    query: String,
    modifier: Modifier = Modifier,
) {
    AccessibleLoadingState(
        message = "Searching for \"$query\"",
        modifier = modifier,
    )
}

/**
 * Progress loading state with a progress indicator.
 */
@Composable
fun ProgressLoadingState(
    message: String,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.progressSemantics(
                    description = message,
                    progress = progress,
                ),
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )

            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Initial app loading state with Jellyfin branding context.
 */
@Composable
fun InitialLoadingState(
    modifier: Modifier = Modifier,
) {
    AccessibleLoadingState(
        message = "Connecting to Jellyfin server",
        modifier = modifier,
    )
}

/**
 * Refresh loading state for pull-to-refresh scenarios.
 */
@Composable
fun RefreshLoadingState(
    contentType: String = "content",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(32.dp)
                    .progressSemantics("Refreshing $contentType"),
                strokeWidth = 3.dp,
            )

            Text(
                text = "Refreshing $contentType",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
