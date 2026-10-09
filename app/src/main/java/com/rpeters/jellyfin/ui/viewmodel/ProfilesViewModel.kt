package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.data.model.ServerProfile
import com.rpeters.jellyfin.data.preferences.ServerProfileRepository
import com.rpeters.jellyfin.data.preferences.ServerProfiles
import com.rpeters.jellyfin.data.session.ProfileSwitchResult
import com.rpeters.jellyfin.data.session.ProfileSwitcher
import com.rpeters.jellyfin.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfilesUiState(
    val isSwitching: Boolean = false,
    /** Set when the chosen profile has no usable token and the user has to sign in again. */
    val signInRequiredFor: ServerProfile? = null,
)

/**
 * Backs the saved-profile switcher on the profile screen and the connection screen.
 */
@UnstableApi
@HiltViewModel
class ProfilesViewModel @Inject constructor(
    serverProfileRepository: ServerProfileRepository,
    private val profileSwitcher: ProfileSwitcher,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    val profiles: StateFlow<ServerProfiles> = serverProfileRepository.serverProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerProfiles())

    private val _uiState = MutableStateFlow(ProfilesUiState())
    val uiState: StateFlow<ProfilesUiState> = _uiState.asStateFlow()

    /**
     * Runs on the application scope: a switch changes the session, which navigates away and can
     * clear this ViewModel before the reset has finished.
     */
    fun switchTo(profileId: String, onSwitched: () -> Unit = {}) {
        if (_uiState.value.isSwitching) return
        _uiState.update { it.copy(isSwitching = true, signInRequiredFor = null) }
        applicationScope.launch {
            val result = try {
                profileSwitcher.switchTo(profileId)
            } finally {
                _uiState.update { it.copy(isSwitching = false) }
            }
            when (result) {
                is ProfileSwitchResult.Switched -> viewModelScope.launch { onSwitched() }
                is ProfileSwitchResult.SignInRequired -> _uiState.update { it.copy(signInRequiredFor = result.profile) }
                ProfileSwitchResult.NotFound -> Unit
            }
        }
    }

    fun remove(profileId: String) {
        applicationScope.launch { profileSwitcher.remove(profileId) }
    }

    /** Ends the current session, keeping its profile, so the connection screen can add another. */
    fun beginAddServer() {
        applicationScope.launch { profileSwitcher.suspendActiveSession() }
    }

    fun consumeSignInRequired() {
        _uiState.update { it.copy(signInRequiredFor = null) }
    }
}
