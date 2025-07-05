package com.example.jellyfinandroid.ui.viewmodel

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jellyfinandroid.data.repository.ApiResult
import com.example.jellyfinandroid.data.repository.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import javax.inject.Inject
import kotlinx.coroutines.delay

data class ConnectionState(
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val errorMessage: String? = null,
    val serverName: String? = null,
    val savedServerUrl: String = "",
    val savedUsername: String = "",
    val rememberLogin: Boolean = false,
    val isQuickConnectActive: Boolean = false,
    val quickConnectServerUrl: String = "",
    val quickConnectCode: String = "",
    val quickConnectSecret: String = "",
    val isQuickConnectPolling: Boolean = false,
    val quickConnectStatus: String = ""
)

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "login_preferences")

object PreferencesKeys {
    val SERVER_URL = stringPreferencesKey("server_url")
    val USERNAME = stringPreferencesKey("username")
    val REMEMBER_LOGIN = booleanPreferencesKey("remember_login")
}

@HiltViewModel
class ServerConnectionViewModel @Inject constructor(
    private val repository: JellyfinRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private var quickConnectPollingJob: Job? = null
    
    init {
        // Load saved credentials and remember login state
        viewModelScope.launch {
            val preferences = context.dataStore.data.first()
            val savedServerUrl = preferences[PreferencesKeys.SERVER_URL] ?: ""
            val savedUsername = preferences[PreferencesKeys.USERNAME] ?: ""
            val rememberLogin = preferences[PreferencesKeys.REMEMBER_LOGIN] ?: false

            _connectionState.value = _connectionState.value.copy(
                savedServerUrl = savedServerUrl,
                savedUsername = savedUsername,
                rememberLogin = rememberLogin
            )
        }
        
        // Observe repository connection state
        viewModelScope.launch {
            repository.isConnected.collect { isConnected ->
                _connectionState.value = _connectionState.value.copy(
                    isConnected = isConnected,
                    isConnecting = false
                )
                
                // Clear saved credentials when disconnected
                if (!isConnected) {
                    clearSavedCredentials()
                }
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
                        serverName = serverInfo.serverName
                    )
                    
                    // Now authenticate
                    when (val authResult = repository.authenticateUser(serverUrl, username, password)) {
                        is ApiResult.Success -> {
                            // Save credentials only when the user opted in
                            if (_connectionState.value.rememberLogin) {
                                saveCredentials(serverUrl, username)
                            } else {
                                clearSavedCredentials()
                            }
                            _connectionState.value = _connectionState.value.copy(
                                isConnecting = false,
                                isConnected = true,
                                errorMessage = null
                            )
                        }
                        is ApiResult.Error -> {
                            // Clear saved credentials on auth failure
                            clearSavedCredentials()
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
    
    private suspend fun saveCredentials(serverUrl: String, username: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SERVER_URL] = serverUrl
            preferences[PreferencesKeys.USERNAME] = username
        }
        _connectionState.value = _connectionState.value.copy(
            savedServerUrl = serverUrl,
            savedUsername = username
        )
    }
    
    private suspend fun clearSavedCredentials() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.SERVER_URL)
            preferences.remove(PreferencesKeys.USERNAME)
        }
        _connectionState.value = _connectionState.value.copy(
            savedServerUrl = "",
            savedUsername = ""
        )
    }

    fun setRememberLogin(remember: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.REMEMBER_LOGIN] = remember
            }
            _connectionState.value = _connectionState.value.copy(rememberLogin = remember)
        }
    }
    
    fun startQuickConnect() {
        _connectionState.value = _connectionState.value.copy(
            isQuickConnectActive = true,
            errorMessage = null
        )
    }
    
    fun cancelQuickConnect() {
        quickConnectPollingJob?.cancel()
        quickConnectPollingJob = null
        _connectionState.value = _connectionState.value.copy(
            isQuickConnectActive = false,
            quickConnectCode = "",
            quickConnectServerUrl = "",
            quickConnectSecret = "",
            isQuickConnectPolling = false,
            quickConnectStatus = ""
        )
    }
    
    fun updateQuickConnectServerUrl(serverUrl: String) {
        _connectionState.value = _connectionState.value.copy(
            quickConnectServerUrl = serverUrl
        )
    }
    
    fun initiateQuickConnect() {
        val serverUrl = _connectionState.value.quickConnectServerUrl
        
        if (serverUrl.isBlank()) {
            _connectionState.value = _connectionState.value.copy(
                errorMessage = "Please enter the server URL"
            )
            return
        }
        
        viewModelScope.launch {
            _connectionState.value = _connectionState.value.copy(
                isConnecting = true,
                errorMessage = null,
                quickConnectStatus = "Connecting to server..."
            )
            
            // First test server connection
            when (val serverResult = repository.testServerConnection(serverUrl)) {
                is ApiResult.Success -> {
                    _connectionState.value = _connectionState.value.copy(
                        quickConnectStatus = "Initiating Quick Connect..."
                    )
                    
                    // Now initiate Quick Connect
                    when (val quickConnectResult = repository.initiateQuickConnect(serverUrl)) {
                        is ApiResult.Success -> {
                            val result = quickConnectResult.data
                            _connectionState.value = _connectionState.value.copy(
                                isConnecting = false,
                                quickConnectCode = result.code ?: "",
                                quickConnectSecret = result.secret ?: "",
                                isQuickConnectPolling = true,
                                quickConnectStatus = "Code generated! Enter this code in your Jellyfin server."
                            )
                            
                            // Start polling for approval
                            quickConnectPollingJob = viewModelScope.launch {
                                pollQuickConnectState(serverUrl, result.secret ?: "")
                            }
                        }
                        is ApiResult.Error -> {
                            _connectionState.value = _connectionState.value.copy(
                                isConnecting = false,
                                errorMessage = "Quick Connect initiation failed: ${quickConnectResult.message}"
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
    
    private suspend fun pollQuickConnectState(serverUrl: String, secret: String) {
        var attempts = 0
        val maxAttempts = 60 // 5 minutes with 5-second intervals
        
        while (attempts < maxAttempts && 
               _connectionState.value.isQuickConnectPolling && 
               viewModelScope.isActive) { // Check if coroutine is still active
            delay(5000) // Wait 5 seconds between polls
            
            when (val stateResult = repository.getQuickConnectState(serverUrl, secret)) {
                is ApiResult.Success -> {
                    val state = stateResult.data
                    when (state.state) {
                        "Approved" -> {
                            // User approved the connection, authenticate
                            when (val authResult = repository.authenticateWithQuickConnect(serverUrl, secret)) {
                                is ApiResult.Success -> {
                                    _connectionState.value = _connectionState.value.copy(
                                        isConnected = true,
                                        isQuickConnectActive = false,
                                        isQuickConnectPolling = false,
                                        quickConnectStatus = "Connected successfully!"
                                    )
                                    return
                                }
                                is ApiResult.Error -> {
                                    _connectionState.value = _connectionState.value.copy(
                                        isQuickConnectPolling = false,
                                        errorMessage = "Authentication failed: ${authResult.message}"
                                    )
                                    return
                                }
                                is ApiResult.Loading -> {
                                    // This shouldn't happen
                                }
                            }
                        }
                        "Denied" -> {
                            _connectionState.value = _connectionState.value.copy(
                                isQuickConnectPolling = false,
                                errorMessage = "Quick Connect was denied by the server"
                            )
                            return
                        }
                        "Expired" -> {
                            _connectionState.value = _connectionState.value.copy(
                                isQuickConnectPolling = false,
                                errorMessage = "Quick Connect code has expired"
                            )
                            return
                        }
                        else -> {
                            // Still waiting for approval
                            _connectionState.value = _connectionState.value.copy(
                                quickConnectStatus = "Waiting for approval... (${attempts + 1}/60)"
                            )
                        }
                    }
                }
                is ApiResult.Error -> {
                    _connectionState.value = _connectionState.value.copy(
                        isQuickConnectPolling = false,
                        errorMessage = "Failed to check Quick Connect state: ${stateResult.message}"
                    )
                    return
                }
                is ApiResult.Loading -> {
                    // This shouldn't happen for this call
                }
            }
            
            attempts++
        }
        
        // Timeout
        if (attempts >= maxAttempts) {
            _connectionState.value = _connectionState.value.copy(
                isQuickConnectPolling = false,
                errorMessage = "Quick Connect timed out. Please try again."
            )
        }
        
        // Clean up the job reference when polling completes
        quickConnectPollingJob = null
    }
    
    override fun onCleared() {
        super.onCleared()
        // Cancel any ongoing quick connect polling when ViewModel is destroyed
        quickConnectPollingJob?.cancel()
    }
}
