package com.example.jellyfinandroid.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jellyfinandroid.data.repository.ApiResult
import com.example.jellyfinandroid.data.repository.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectionState(
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val errorMessage: String? = null,
    val serverName: String? = null
)

@HiltViewModel
class ServerConnectionViewModel @Inject constructor(
    private val repository: JellyfinRepository
) : ViewModel() {
    
    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    init {
        // Observe repository connection state
        viewModelScope.launch {
            repository.isConnected.collect { isConnected ->
                _connectionState.value = _connectionState.value.copy(
                    isConnected = isConnected,
                    isConnecting = false
                )
            }
        }
    }
    
    fun connectToServer(serverUrl: String, username: String, password: String) {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            _connectionState.value = _connectionState.value.copy(
                errorMessage = "Please fill in all fields"
            )
            return
        }
        
        viewModelScope.launch {
            _connectionState.value = _connectionState.value.copy(
                isConnecting = true,
                errorMessage = null
            )
            
            // First test server connection
            when (val serverResult = repository.testServerConnection(serverUrl)) {
                is ApiResult.Success -> {
                    val serverInfo = serverResult.data
                    _connectionState.value = _connectionState.value.copy(
                        serverName = serverInfo.name
                    )
                    
                    // Now authenticate
                    when (val authResult = repository.authenticateUser(serverUrl, username, password)) {
                        is ApiResult.Success -> {
                            _connectionState.value = _connectionState.value.copy(
                                isConnecting = false,
                                isConnected = true,
                                errorMessage = null
                            )
                        }
                        is ApiResult.Error -> {
                            _connectionState.value = _connectionState.value.copy(
                                isConnecting = false,
                                errorMessage = authResult.message
                            )
                        }
                        is ApiResult.Loading -> {
                            // This shouldn't happen for this call
                        }
                    }
                }
                is ApiResult.Error -> {
                    _connectionState.value = _connectionState.value.copy(
                        isConnecting = false,
                        errorMessage = "Cannot connect to server: ${serverResult.message}"
                    )
                }
                is ApiResult.Loading -> {
                    // This shouldn't happen for this call
                }
            }
        }
    }
    
    fun clearError() {
        _connectionState.value = _connectionState.value.copy(errorMessage = null)
    }
    
    fun startQuickConnect() {
        // TODO: Implement Quick Connect functionality
        _connectionState.value = _connectionState.value.copy(
            errorMessage = "Quick Connect not yet implemented"
        )
    }
}
