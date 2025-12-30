package com.rpeters.jellyfin.ui.player

import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.rpeters.jellyfin.R

/**
 * Composable wrapper for Google Cast MediaRouteButton.
 * Provides the standard Cast icon that users expect and handles
 * device discovery automatically through the Cast framework.
 *
 * This button integrates directly with CastContext and shows the
 * Cast device picker dialog when tapped.
 *
 * @param modifier Modifier to be applied to the button
 * @param tint Color for the button icon (defaults to onSurface)
 */
@Composable
fun MediaRouteButton(
    modifier: Modifier = Modifier,
    tint: Int = MaterialTheme.colorScheme.onSurface.toArgb(),
) {
    val context = LocalContext.current

    // Create a themed context with opaque background for MediaRouter
    // This prevents crashes when MediaRouter tries to calculate contrast ratios
    // with translucent/transparent backgrounds
    val themedContext = remember(context) {
        ContextThemeWrapper(context, R.style.Theme_MediaRouter_Opaque)
    }

    AndroidView(
        factory = { _ ->
            MediaRouteButton(themedContext).apply {
                // Initialize the Cast button with the CastContext
                CastButtonFactory.setUpMediaRouteButton(themedContext, this)

                // Set content description for accessibility
                contentDescription = "Cast to device"
            }
        },
        modifier = modifier.size(48.dp), // Standard touch target size
    )
}

/**
 * Helper function to check if Cast is available in the current context.
 * Can be used to conditionally show the MediaRouteButton.
 */
@Composable
fun rememberIsCastAvailable(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            CastContext.getSharedInstance(context)
            true
        } catch (e: Exception) {
            false
        }
    }
}
