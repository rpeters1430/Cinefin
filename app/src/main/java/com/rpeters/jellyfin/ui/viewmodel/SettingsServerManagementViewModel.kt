package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.repository.JellyfinUserRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsServerManagementState(
    val activeAction: ServerManagementAction? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null,
) {
    val isRunning: Boolean
        get() = activeAction != null
}

enum class ServerManagementAction {
    RESCAN_LIBRARIES,
    RESTART_SERVER,
    SHUTDOWN_SERVER,
}

@HiltViewModel
class SettingsServerManagementViewModel @Inject constructor(
    private val userRepository: JellyfinUserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsServerManagementState())
    val state: StateFlow<SettingsServerManagementState> = _state.asStateFlow()

    fun rescanLibraries() {
        runAction(
            action = ServerManagementAction.RESCAN_LIBRARIES,
            successMessage = "Library rescan requested.",
        ) {
            userRepository.refreshLibrary()
        }
    }

    fun restartServer() {
        runAction(
            action = ServerManagementAction.RESTART_SERVER,
            successMessage = "Server restart requested.",
        ) {
            userRepository.restartServer()
        }
    }

    fun shutdownServer() {
        runAction(
            action = ServerManagementAction.SHUTDOWN_SERVER,
            successMessage = "Server shutdown requested.",
        ) {
            userRepository.shutdownServer()
        }
    }

    fun clearSuccessMessage() {
        _state.update { it.copy(successMessage = null) }
    }

    fun clearErrorMessage() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun runAction(
        action: ServerManagementAction,
        successMessage: String,
        block: suspend () -> ApiResult<Boolean>,
    ) {
        if (_state.value.isRunning) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    activeAction = action,
                    successMessage = null,
                    errorMessage = null,
                )
            }

            val nextState = try {
                when (val result = block()) {
                    is ApiResult.Success -> {
                        SettingsServerManagementState(successMessage = successMessage)
                    }

                    is ApiResult.Error<*> -> {
                        SettingsServerManagementState(errorMessage = result.message)
                    }

                    is ApiResult.Loading<*> -> {
                        SettingsServerManagementState()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SettingsServerManagementState(errorMessage = e.message ?: "Operation failed")
            }

            _state.value = nextState
        }
    }
}
