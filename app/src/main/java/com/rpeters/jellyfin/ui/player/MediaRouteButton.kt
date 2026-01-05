package com.rpeters.jellyfin.ui.player

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext

/**
 * Helper function to find the FragmentActivity from a Context.
 * MediaRouteButton requires a FragmentActivity to show the Cast dialog.
 * This function handles all types of context wrappers including ContextThemeWrapper.
 */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var context: Context? = this
    while (context != null) {
        when (context) {
            is FragmentActivity -> return context
            is ContextWrapper -> context = context.baseContext
            else -> return null
        }
    }
    return null
}

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

    // Find the FragmentActivity from the context chain
    // MediaRouteButton requires a FragmentActivity to show the Cast device picker dialog
    val fragmentActivity = remember(context) {
        context.findFragmentActivity()
    }

    // If no FragmentActivity is found, don't show the button
    // This prevents the crash: "The activity must be a subclass of FragmentActivity"
    if (fragmentActivity == null) {
        android.util.Log.w("MediaRouteButton", "No FragmentActivity found in context chain - Cast button will not be shown")
        return
    }

    AndroidView(
        factory = { _ ->
            // CRITICAL FIX: Create MediaRouteButton with the FragmentActivity directly
            // DO NOT wrap in ContextThemeWrapper as it breaks the FragmentActivity lookup
            // The MediaRouteButton internally calls getActivity() which needs to find
            // a FragmentActivity to show the cast dialog
            MediaRouteButton(fragmentActivity).apply {
                // Initialize the Cast button with the FragmentActivity context
                // IMPORTANT: Use fragmentActivity, NOT applicationContext, to ensure
                // the Cast framework has access to activity-scoped authentication state
                CastButtonFactory.setUpMediaRouteButton(fragmentActivity, this)

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
