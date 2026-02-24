package com.rpeters.jellyfin.ui.player.cast

import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.data.preferences.CastPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the Cast system state.
 * Manages the StateFlow and persists session information.
 */
@UnstableApi
@Singleton
class CastStateStore @Inject constructor(
    private val castPreferencesRepository: CastPreferencesRepository,
) {
    private val _castState = MutableStateFlow(CastState())
    val castState: StateFlow<CastState> = _castState.asStateFlow()

    fun update(function: (CastState) -> CastState) {
        _castState.update(function)
    }

    fun setError(error: String?) {
        _castState.update { it.copy(error = error) }
    }

    fun clearError() {
        _castState.update { it.copy(error = null) }
    }

    /**
     * Persists the last used device and session for auto-reconnect.
     */
    fun persistSession(scope: CoroutineScope, deviceName: String?, sessionId: String?) {
        scope.launch {
            castPreferencesRepository.saveLastCastSession(deviceName, sessionId)
        }
    }

    /**
     * Clears persisted session info.
     */
    fun clearPersistedSession(scope: CoroutineScope) {
        scope.launch {
            castPreferencesRepository.clearLastCastSession()
        }
    }

    /**
     * Resets the UI-specific parts of the state (position, playing, etc) 
     * without losing connection status.
     */
    fun resetPlaybackState() {
        _castState.update {
            it.copy(
                isRemotePlaying = false,
                currentPosition = 0L,
                duration = 0L,
            )
        }
    }
}
