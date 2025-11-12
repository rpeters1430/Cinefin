package com.rpeters.jellyfin.ui.surface.components

import com.rpeters.jellyfin.ui.surface.ModernSurfaceSnapshot
import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the surface snapshot to system notifications. The actual notification
 * rendering will be wired in future tasks.
 */
@Singleton
class NotificationSurfaceManager @Inject constructor() {

    private var lastNotificationIds: Set<String> = emptySet()

    suspend fun updateNotifications(snapshot: ModernSurfaceSnapshot) {
        withContext(Dispatchers.Default) {
            val notificationIds = snapshot.notifications.map { it.id }.toSet()
            if (notificationIds == lastNotificationIds) {
                return@withContext
            }
            lastNotificationIds = notificationIds
            if (snapshot.notifications.isNotEmpty()) {
                SecureLogger.d(
                    TAG,
                    "Notification update pending (count=${snapshot.notifications.size})",
                )
            }
        }
    }

    companion object {
        private const val TAG = "NotificationSurfaceManager"
    }
}
