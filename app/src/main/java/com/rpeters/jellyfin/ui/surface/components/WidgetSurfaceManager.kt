package com.rpeters.jellyfin.ui.surface.components

import com.rpeters.jellyfin.ui.surface.ModernSurfaceSnapshot
import com.rpeters.jellyfin.utils.SecureLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coordinates future Glance widget updates. Currently acts as a centralized
 * logger so engineers can verify when the coordinator dispatches updates.
 */
@Singleton
class WidgetSurfaceManager @Inject constructor() {

    private var lastSnapshot: ModernSurfaceSnapshot? = null

    suspend fun updateWidgets(snapshot: ModernSurfaceSnapshot) {
        withContext(Dispatchers.Default) {
            if (lastSnapshot == snapshot) {
                return@withContext
            }
            lastSnapshot = snapshot
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
