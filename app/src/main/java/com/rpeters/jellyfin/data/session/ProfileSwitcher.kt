package com.rpeters.jellyfin.data.session

import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.core.constants.Constants
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.cache.JellyfinCache
import com.rpeters.jellyfin.data.model.ServerProfile
import com.rpeters.jellyfin.data.preferences.ServerProfileRepository
import com.rpeters.jellyfin.data.repository.IJellyfinAuthRepository
import com.rpeters.jellyfin.di.OptimizedClientFactory
import com.rpeters.jellyfin.ui.player.CastManager
import com.rpeters.jellyfin.ui.player.audio.AudioServiceConnection
import com.rpeters.jellyfin.ui.viewmodel.common.SharedAppStateManager
import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ProfileSwitchResult {
    /** The profile is now the active session. */
    data class Switched(val profile: ServerProfile) : ProfileSwitchResult

    /** The profile is saved but has no usable token; the user has to sign in to it again. */
    data class SignInRequired(val profile: ServerProfile) : ProfileSwitchResult

    data object NotFound : ProfileSwitchResult
}

/**
 * The one place that changes which saved profile is the active session.
 *
 * Anything that holds data belonging to a server has to be cleared here, in [resetServerScopedState],
 * or it will show one server's content under another profile.
 */
@UnstableApi
@Singleton
class ProfileSwitcher @Inject constructor(
    private val profileRepository: ServerProfileRepository,
    private val activeSessionStore: ActiveSessionStore,
    private val authRepository: IJellyfinAuthRepository,
    private val clientFactory: OptimizedClientFactory,
    private val cache: JellyfinCache,
    private val sharedAppStateManager: SharedAppStateManager,
    private val castManager: CastManager,
    private val audioServiceConnection: AudioServiceConnection,
    private val secureCredentialManager: SecureCredentialManager,
) {
    private val switchMutex = Mutex()

    private val _profileChanged = MutableSharedFlow<ServerProfile?>(extraBufferCapacity = 1)

    /**
     * Emits after the active session changed: the new profile, or null when the last profile was
     * removed. Screens that keep server data in a ViewModel reload on this.
     */
    val profileChanged: SharedFlow<ServerProfile?> = _profileChanged.asSharedFlow()

    suspend fun switchTo(profileId: String): ProfileSwitchResult = switchMutex.withLock {
        val saved = profileRepository.current()
        val target = saved.profiles.firstOrNull { it.id == profileId } ?: return ProfileSwitchResult.NotFound
        if (saved.activeProfileId == profileId && authRepository.getCurrentServerSync()?.accessToken == target.accessToken) {
            return ProfileSwitchResult.Switched(target)
        }
        if (!target.hasUsableToken()) {
            return ProfileSwitchResult.SignInRequired(target)
        }

        val connectUserId = target.embyConnectUserId
        val connectKey = target.embyConnectAccessKey
        if (connectUserId != null && connectKey != null) {
            val result = authRepository.authenticateWithEmbyConnect(target.serverUrl, connectUserId, connectKey, target.serverId)
            if (result !is com.rpeters.jellyfin.data.repository.common.ApiResult.Success) {
                return ProfileSwitchResult.SignInRequired(target)
            }
            val refreshed = authRepository.getCurrentServerSync() ?: return ProfileSwitchResult.SignInRequired(target)
            val updatedProfile = ServerProfile.fromServer(refreshed) ?: return ProfileSwitchResult.SignInRequired(target)
            resetServerScopedState()
            profileRepository.saveAndActivate(updatedProfile)
            activeSessionStore.save(refreshed)
            _profileChanged.tryEmit(updatedProfile)
            return ProfileSwitchResult.Switched(updatedProfile)
        }
        resetServerScopedState()
        profileRepository.setActive(profileId)
        val server = target.toJellyfinServer()
        activeSessionStore.save(server)
        authRepository.restorePersistedSession(server)
        _profileChanged.tryEmit(target)
        ProfileSwitchResult.Switched(target)
    }

    /**
     * Removes a saved profile with its token and saved password. When it was the active one, the
     * session ends and no other profile is activated; the caller decides where to go next.
     *
     * @return true when the removed profile was the active session.
     */
    suspend fun remove(profileId: String): Boolean = switchMutex.withLock {
        val saved = profileRepository.current()
        val wasActive = saved.activeProfileId == profileId
        val removed = profileRepository.remove(profileId) ?: return false

        try {
            secureCredentialManager.clearPassword(removed.serverUrl, removed.username)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SecureLogger.w(TAG, "Could not clear the saved password for a removed profile", e)
        }

        if (wasActive) {
            resetServerScopedState()
            activeSessionStore.clear()
            // In-memory sign-out only; the profile's saved password was already cleared above.
            authRepository.seedCurrentServer(null)
            _profileChanged.tryEmit(null)
        }
        wasActive
    }

    /**
     * Ends the running session but keeps its profile saved, so the connection screen can be used
     * to add another server. The suspended profile can be switched back to at any time.
     */
    suspend fun suspendActiveSession() = switchMutex.withLock {
        resetServerScopedState()
        activeSessionStore.clear()
        authRepository.seedCurrentServer(null)
        _profileChanged.tryEmit(null)
    }

    private suspend fun resetServerScopedState() {
        runStep("stop audio playback") { audioServiceConnection.stopPlayback() }
        runStep("end cast session") {
            if (castManager.castState.value.isConnected) {
                castManager.disconnectCastSession(userInitiated = true)
            }
        }
        runStep("clear SDK clients") { clientFactory.clearAllClients() }
        runStep("clear item cache") { cache.clearAllCache() }
        runStep("clear shared app state") { sharedAppStateManager.clearCache() }
    }

    /** One failing step must not leave the switch half done. */
    private suspend fun runStep(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SecureLogger.w(TAG, "Profile switch: could not $name", e)
        }
    }

    private fun ServerProfile.hasUsableToken(): Boolean {
        if (accessToken.isNullOrBlank()) return false
        val savedAt = loginTimestamp ?: return true
        return System.currentTimeMillis() - savedAt <= Constants.SESSION_TOKEN_MAX_AGE_MS
    }

    companion object {
        private const val TAG = "ProfileSwitcher"
    }
}
