package com.rpeters.jellyfin.ui.player.cast

import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the Cast SDK session lifecycle and orchestrates state updates.
 */
@UnstableApi
@Singleton
class CastSessionController @Inject constructor(
    private val stateStore: CastStateStore,
    private val discoveryController: CastDiscoveryController,
    private val playbackController: CastPlaybackController,
) {

    fun createSessionListener(scope: CoroutineScope, onUpdate: () -> Unit): SessionManagerListener<CastSession> {
        return object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                if (BuildConfig.DEBUG) SecureLogger.d("CastSession", "Started: $sessionId")
                
                playbackController.registerCallback(session.remoteMediaClient, onUpdate)
                discoveryController.stopDiscovery()

                val deviceName = session.castDevice?.friendlyName
                stateStore.update { state ->
                    state.copy(
                        isConnected = true,
                        deviceName = deviceName,
                        isCasting = true,
                        error = null,
                    )
                }
                stateStore.persistSession(scope, deviceName, sessionId)
            }

            override fun onSessionEnded(session: CastSession, error: Int) {
                if (BuildConfig.DEBUG) SecureLogger.d("CastSession", "Ended")
                
                playbackController.unregisterCallback(session.remoteMediaClient)
                stateStore.update { state ->
                    state.copy(
                        isConnected = false,
                        deviceName = null,
                        isCasting = false,
                        isRemotePlaying = false,
                    )
                }
                stateStore.clearPersistedSession(scope)
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                if (BuildConfig.DEBUG) SecureLogger.d("CastSession", "Resumed")
                
                playbackController.registerCallback(session.remoteMediaClient, onUpdate)
                val deviceName = session.castDevice?.friendlyName
                
                stateStore.update { state ->
                    state.copy(
                        isConnected = true,
                        deviceName = deviceName,
                        isCasting = true,
                        error = null,
                    )
                }
            }

            override fun onSessionSuspended(session: CastSession, reason: Int) {
                stateStore.update { it.copy(isCasting = false, isRemotePlaying = false) }
            }

            override fun onSessionStarting(session: CastSession) {}
            override fun onSessionStartFailed(session: CastSession, error: Int) {
                stateStore.setError("Failed to start Cast session (error $error)")
            }
            override fun onSessionEnding(session: CastSession) {}
            override fun onSessionResuming(session: CastSession, sessionId: String) {}
            override fun onSessionResumeFailed(session: CastSession, error: Int) {
                stateStore.clearPersistedSession(scope)
            }
        }
    }
}
