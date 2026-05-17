package com.rpeters.jellyfin.ui.surface.components

import com.rpeters.jellyfin.ui.surface.ModernSurfaceSnapshot
import com.rpeters.jellyfin.utils.SecureLogger
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates future Glance widget updates.
 */
@Singleton
class WidgetSurfaceManager @Inject constructor(
    @ApplicationContext private val context: android.content.Context
) {

    private var lastSnapshot: ModernSurfaceSnapshot? = null

    suspend fun updateWidgets(snapshot: ModernSurfaceSnapshot) {
        withContext(Dispatchers.Default) {
            if (lastSnapshot == snapshot) {
                return@withContext
            }
            lastSnapshot = snapshot
            
            // Trigger Glance widget updates
            try {
                ContinueWatchingWidget().updateAll(context)
                RecentlyAddedWidget().updateAll(context)
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to update widgets", e)
            }

            SecureLogger.d(
                TAG,
                "Widget update scheduled (continueWatching=${snapshot.continueWatching.size}, " +
                    "lifecycle=${snapshot.lifecycleState})",
            )
        }
    }

    companion object {
        private const val TAG = "WidgetSurfaceManager"
    }
}
