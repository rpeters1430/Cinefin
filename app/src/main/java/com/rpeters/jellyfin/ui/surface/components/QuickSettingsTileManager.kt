package com.rpeters.jellyfin.ui.surface.components

import com.rpeters.jellyfin.ui.surface.ModernSurfaceSnapshot
import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Placeholder quick settings tile manager. Future work will translate the
 * snapshot into an active tile state with playback controls.
 */
@Singleton
class QuickSettingsTileManager @Inject constructor() {

    private var lastNowPlayingId: String? = null

    suspend fun updateQuickSettings(snapshot: ModernSurfaceSnapshot) {
        withContext(Dispatchers.Default) {
            val nowPlayingId = snapshot.nowPlaying?.itemId
            if (nowPlayingId == lastNowPlayingId) {
                return@withContext
            }
            lastNowPlayingId = nowPlayingId
            SecureLogger.d(
                TAG,
                "Quick settings tile update requested (nowPlaying=" +
                    "${snapshot.nowPlaying?.itemId ?: "none"})",
            )
        }
    }

    companion object {
        private const val TAG = "QuickSettingsTileMgr"
    }
}
