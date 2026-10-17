package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.core.FeatureFlags
import com.rpeters.jellyfin.data.plex.PlexAuthDataSource
import com.rpeters.jellyfin.data.plex.PlexConnectionSelector
import com.rpeters.jellyfin.data.plex.PlexProfiles
import com.rpeters.jellyfin.data.plex.PlexResource
import com.rpeters.jellyfin.data.plex.PlexSignInState
import com.rpeters.jellyfin.data.plex.PlexTvClient
import com.rpeters.jellyfin.data.plex.PlexTvUser
import com.rpeters.jellyfin.data.repository.RemoteConfigRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.session.ProfileSwitcher
import com.rpeters.jellyfin.di.ApplicationScope
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A server on the plex.tv account, as the picker shows it. Carries no token. */
data class PlexServerOption(
    /** The server's machine identifier. */
    val id: String,
    val name: String,
    val owned: Boolean,
    /** Who shared the server; null for the account's own servers. */
    val ownerName: String?,
    val online: Boolean,
) {
    companion object {
        fun from(resource: PlexResource) = PlexServerOption(
            id = resource.clientIdentifier,
            name = resource.name,
            owned = resource.owned,
            ownerName = resource.sourceTitle?.takeIf { !resource.owned && it.isNotBlank() },
            online = resource.presence,
        )
    }
}

/** Why the picked server could not be added. */
enum class PlexServerProblem {
    /** None of its addresses answered as that server. */
    UNREACHABLE,

    /** plex.tv gave no token for this shared server. */
    NO_ACCESS,
}

/** Where "Sign in with Plex" is, from the PIN to the server picker. */
sealed interface PlexSignInStep {
    data object Idle : PlexSignInStep

    /** Asking plex.tv for a PIN. */
    data object Starting : PlexSignInStep

    data class WaitingForApproval(val code: String, val authUrl: String) : PlexSignInStep

    data object LoadingServers : PlexSignInStep

    data class ChooseServer(
        val servers: List<PlexServerOption>,
        /** The server being connected to; the picker is locked meanwhile. */
        val connectingTo: String? = null,
        val problem: PlexServerProblem? = null,
        val problemServerName: String? = null,
    ) : PlexSignInStep

    /** Signed in, but the account has no servers of its own or shared with it. */
    data object NoServers : PlexSignInStep

    /** The code ran out before it was approved. */
    data object Expired : PlexSignInStep

    data class Failed(val message: String) : PlexSignInStep
}

/**
 * Backs "Sign in with Plex" on the connection screen (KD6): plex.tv PIN, server list, connection
 * choice, then the new profile becomes the active session through [ProfileSwitcher].
 */
@UnstableApi
@HiltViewModel
class PlexSignInViewModel @Inject constructor(
    private val authDataSource: PlexAuthDataSource,
    private val plexTvClient: PlexTvClient,
    private val connectionSelector: PlexConnectionSelector,
    private val profileSwitcher: ProfileSwitcher,
    private val remoteConfigRepository: RemoteConfigRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val _step = MutableStateFlow<PlexSignInStep>(PlexSignInStep.Idle)
    val step: StateFlow<PlexSignInStep> = _step.asStateFlow()

    val isAvailable: Boolean
        get() = remoteConfigRepository.getBoolean(FeatureFlags.Experimental.ENABLE_PLEX_SUPPORT)

    private var job: Job? = null
    private var account: SignedInAccount? = null
    private var servers: Map<String, PlexResource> = emptyMap()

    /** Starts a new sign-in, dropping any earlier one. Also used to retry. */
    fun start() {
        reset()
        _step.value = PlexSignInStep.Starting
        job = viewModelScope.launch {
            authDataSource.signIn().collect { state ->
                when (state) {
                    is PlexSignInState.WaitingForApproval ->
                        _step.value = PlexSignInStep.WaitingForApproval(state.code, state.authUrl)
                    is PlexSignInState.SignedIn -> loadServers(state.accountToken)
                    PlexSignInState.Expired -> _step.value = PlexSignInStep.Expired
                    is PlexSignInState.Failed -> _step.value = PlexSignInStep.Failed(state.message)
                }
            }
        }
    }

    private suspend fun loadServers(accountToken: String) {
        _step.value = PlexSignInStep.LoadingServers
        val (userResult, serversResult) = coroutineScope {
            val user = async { plexTvClient.getUser(accountToken) }
            val found = async { plexTvClient.getServers(accountToken) }
            user.await() to found.await()
        }
        val user = (userResult as? ApiResult.Success)?.data
        val found = (serversResult as? ApiResult.Success)?.data
        if (user == null || found == null) {
            val error = userResult as? ApiResult.Error<*> ?: serversResult as? ApiResult.Error<*>
            _step.value = PlexSignInStep.Failed(error?.message ?: "Could not load your Plex servers")
            return
        }
        account = SignedInAccount(accountToken, user)
        servers = found.associateBy { it.clientIdentifier }
        _step.value = if (found.isEmpty()) {
            PlexSignInStep.NoServers
        } else {
            PlexSignInStep.ChooseServer(found.map(PlexServerOption::from).sortedWith(PICKER_ORDER))
        }
    }

    fun selectServer(serverId: String) {
        val choosing = _step.value as? PlexSignInStep.ChooseServer ?: return
        if (choosing.connectingTo != null) return
        val signedIn = account ?: return
        val server = servers[serverId] ?: return
        _step.value = choosing.copy(connectingTo = serverId, problem = null, problemServerName = null)
        job = viewModelScope.launch {
            val address = connectionSelector.select(server)
            val profile = address?.let {
                PlexProfiles.create(server, it, signedIn.user, signedIn.accountToken, System.currentTimeMillis())
            }
            if (profile == null) {
                val problem = if (address == null) PlexServerProblem.UNREACHABLE else PlexServerProblem.NO_ACCESS
                SecureLogger.w(TAG, "Could not add Plex server: $problem")
                _step.value = choosing.copy(problem = problem, problemServerName = server.name)
                return@launch
            }
            // The switch signs in, which navigates away and clears this ViewModel; it must finish.
            applicationScope.launch { profileSwitcher.addAndSwitch(profile) }.join()
            forgetAccount()
        }
    }

    /** Stops polling and forgets the account token; nothing is saved. */
    fun cancel() = reset()

    private fun reset() {
        job?.cancel()
        job = null
        forgetAccount()
    }

    private fun forgetAccount() {
        account = null
        servers = emptyMap()
        _step.value = PlexSignInStep.Idle
    }

    private class SignedInAccount(val accountToken: String, val user: PlexTvUser)

    companion object {
        private const val TAG = "PlexSignInViewModel"

        /** Own servers first, then online ones, then by name. */
        private val PICKER_ORDER = compareByDescending<PlexServerOption> { it.owned }
            .thenByDescending { it.online }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }
}
