package com.rpeters.jellyfin.ui.surface.components

import android.content.Context
import com.rpeters.jellyfin.ui.shortcuts.DynamicShortcutManager
import com.rpeters.jellyfin.ui.surface.ModernSurfaceSnapshot
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Publishes launcher shortcuts that mirror the current surface snapshot.
 */
@Singleton
class ShortcutSurfaceManager @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
) {

    suspend fun updateShortcuts(snapshot: ModernSurfaceSnapshot) {
        withContext(Dispatchers.Default) {
            runCatching {
                DynamicShortcutManager.updateContinueWatchingShortcuts(
                    applicationContext,
                    snapshot.continueWatching,
                )
            }.onFailure { throwable ->
                SecureLogger.e(TAG, "Failed to update dynamic shortcuts", throwable)
            }
        }
    }

    companion object {
        private const val TAG = "ShortcutSurfaceManager"
    }
}
