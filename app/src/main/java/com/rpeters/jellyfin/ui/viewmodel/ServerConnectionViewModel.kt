package com.rpeters.jellyfin.ui.viewmodel

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.utils.ServerUrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

// Use the enhanced ConnectionState from ConnectionProgress.kt
// This data class is now defined in the ConnectionProgress.kt file

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "login_preferences")

object PreferencesKeys {
    val SERVER_URL = stringPreferencesKey("server_url")
    val USERNAME = stringPreferencesKey("username")
    val REMEMBER_LOGIN = booleanPreferencesKey("remember_login")
    val BIOMETRIC_AUTH_ENABLED = booleanPreferencesKey("biometric_auth_enabled") // New preference
}

@HiltViewModel
class ServerConnectionViewModel @Inject constructor(
    private val repository: JellyfinRepository,
    private val secureCredentialManager: SecureCredentialManager,
    @ApplicationContext private val context: Context,
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
            val isBiometricAuthEnabled = preferences[PreferencesKeys.BIOMETRIC_AUTH_ENABLED] ?: false

            // ✅ FIX: Handle suspend function calls properly
            val hasSavedPassword = if (savedServerUrl.isNotBlank() && savedUsername.isNotBlank()) {
                secureCredentialManager.getPassword(savedServerUrl, savedUsername) != null
            } else {
                false
            }

            _connectionState.value = _connectionState.value.copy(
                savedServerUrl = savedServerUrl,
                savedUsername = savedUsername,
                rememberLogin = rememberLogin,
                hasSavedPassword = hasSavedPassword,
                isBiometricAuthAvailable = secureCredentialManager.isBiometricAuthAvailable(), // Check biometric availability
                // We don't set biometric auth enabled here because we want to check it separately
            )

            // Auto-login if we have saved credentials and remember login is enabled
            if (rememberLogin && savedServerUrl.isNotBlank() && savedUsername.isNotBlank()) {
                // We don't auto-login if biometric auth is enabled, user needs to trigger it manually
                if (!isBiometricAuthEnabled) {
                    val savedPassword = secureCredentialManager.getPassword(savedServerUrl, savedUsername)
                    if (savedPassword != null) {
                        // Auto-login with saved credentials
                        connectToServer(savedServerUrl, savedUsername, savedPassword)
                    }
                }
            }
        }

        // Observe repository connection state
        viewModelScope.launch {
            repository.isConnected.collect { isConnected ->
                _connectionState.value = _connectionState.value.copy(
                    isConnected = isConnected,
                    isConnecting = false,
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
                errorMessage = "Please fill in all fields",
            )
            return
        }

        // Validate and normalize the server URL
        val normalizedServerUrl = ServerUrlValidator.validateAndNormalizeUrl(serverUrl)
        if (normalizedServerUrl == null) {
            _connectionState.value = _connectionState.value.copy(
                errorMessage = "Invalid server URL. Please enter a valid URL like 'http://192.168.1.100:8096' or 'https://jellyfin.myserver.com'",
            )
            return
        }

        viewModelScope.launch {
            _connectionState.value = _connectionState.value.copy(
                isConnecting = true,
                errorMessage = null,
                connectionPhase = ConnectionPhase.Testing,
                currentUrl = serverUrl,
            )

            // First test server connection with enhanced feedback
            when (val serverResult = repository.testServerConnection(normalizedServerUrl)) {
                is ApiResult.Success -> {
                    val serverInfo = serverResult.data
                    _connectionState.value = _connectionState.value.copy(
                        serverName = serverInfo.serverName,
                        connectionPhase = ConnectionPhase.Authenticating,
                    )

                    // Now authenticate with enhanced feedback
                    when (val authResult = repository.authenticateUser(normalizedServerUrl, username, password)) {
                        is ApiResult.Success -> {
                            // Save credentials only when the user opted in
                            if (_connectionState.value.rememberLogin) {
                                saveCredentials(normalizedServerUrl, username, password)
                            } else {
                                clearSavedCredentials()
                            }
                            _connectionState.value = _connectionState.value.copy(
                                isConnecting = false,
                                isConnected = true,
                                errorMessage = null,
                                connectionPhase = ConnectionPhase.Connected,
                            )
                        }
                        is ApiResult.Error -> {
                            // Clear saved credentials on auth failure
                            clearSavedCredentials()
                            _connectionState.value = _connectionState.value.copy(
                                isConnecting = false,
                                errorMessage = authResult.message,
                                connectionPhase = ConnectionPhase.Error,
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
                        errorMessage = "Cannot connect to server: ${serverResult.message}",
                        connectionPhase = ConnectionPhase.Error,
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

    private suspend fun saveCredentials(serverUrl: String, username: String, password: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SERVER_URL] = serverUrl
            preferences[PreferencesKeys.USERNAME] = username
        }
        secureCredentialManager.savePassword(serverUrl, username, password)
        _connectionState.value = _connectionState.value.copy(
            savedServerUrl = serverUrl,
            savedUsername = username,
            hasSavedPassword = true,
        )
    }

    private suspend fun clearSavedCredentials() {
        val currentState = _connectionState.value
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.SERVER_URL)
            preferences.remove(PreferencesKeys.USERNAME)
        }
        if (currentState.savedServerUrl.isNotBlank() && currentState.savedUsername.isNotBlank()) {
            secureCredentialManager.clearPassword(currentState.savedServerUrl, currentState.savedUsername)
        }
        _connectionState.value = _connectionState.value.copy(
            savedServerUrl = "",
            savedUsername = "",
            hasSavedPassword = false,
        )
    }

    fun setRememberLogin(remember: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.REMEMBER_LOGIN] = remember
            }
            if (!remember) {
                clearSavedCredentials()
            }
            _connectionState.value = _connectionState.value.copy(rememberLogin = remember)
        }
    }

    /**
     * Sets whether biometric authentication is enabled for accessing saved credentials
     */
    fun setBiometricAuthEnabled(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.BIOMETRIC_AUTH_ENABLED] = enabled
            }
            _connectionState.value = _connectionState.value.copy(
                isBiometricAuthAvailable = enabled && secureCredentialManager.isBiometricAuthAvailable(),
            )
        }
    }

    /**
     * Gets a saved password with optional biometric authentication
     */
    suspend fun getSavedPassword(activity: FragmentActivity? = null): String? {
        val currentState = _connectionState.value
        return if (currentState.savedServerUrl.isNotBlank() && currentState.savedUsername.isNotBlank()) {
            secureCredentialManager.getPassword(currentState.savedServerUrl, currentState.savedUsername, activity)
        } else {
            null
        }
    }

    /**
     * Gets a saved password without biometric authentication (for backward compatibility)
     */
    suspend fun getSavedPassword(): String? {
        return getSavedPassword(null)
    }

    fun autoLogin() {
        val currentState = _connectionState.value
        if (currentState.savedServerUrl.isNotBlank() && currentState.savedUsername.isNotBlank()) {
            viewModelScope.launch {
                val savedPassword = getSavedPassword()
                if (savedPassword != null) {
                    connectToServer(
                        currentState.savedServerUrl,
                        currentState.savedUsername,
                        savedPassword,
                    )
                }
            }
        }
    }

    /**
     * Auto-login with biometric authentication if enabled
     */
    fun autoLoginWithBiometric(activity: FragmentActivity) {
        val currentState = _connectionState.value
        if (currentState.savedServerUrl.isNotBlank() && currentState.savedUsername.isNotBlank()) {
            viewModelScope.launch {
                val savedPassword = getSavedPassword(activity)
                if (savedPassword != null) {
                    connectToServer(
                        currentState.savedServerUrl,
                        currentState.savedUsername,
                        savedPassword,
                    )
                }
            }
        }
    }

    fun startQuickConnect() {
        _connectionState.value = _connectionState.value.copy(
            isQuickConnectActive = true,
            errorMessage = null,
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
            quickConnectStatus = "",
        )
    }

    fun updateQuickConnectServerUrl(serverUrl: String) {
        _connectionState.value = _connectionState.value.copy(
            quickConnectServerUrl = serverUrl,
        )
    }

    fun initiateQuickConnect() {
        val serverUrl = _connectionState.value.quickConnectServerUrl

        if (serverUrl.isBlank()) {
            _connectionState.value = _connectionState.value.copy(
                errorMessage = "Please enter the server URL",
            )
            return
        }

        // Validate and normalize the server URL
        val normalizedServerUrl = ServerUrlValidator.validateAndNormalizeUrl(serverUrl)
        if (normalizedServerUrl == null) {
            _connectionState.value = _connectionState.value.copy(
                errorMessage = "Invalid server URL. Please enter a valid URL like 'http://192.168.1.100:8096' or 'https://jellyfin.myserver.com'",
            )
            return
        }

        viewModelScope.launch {
            _connectionState.value = _connectionState.value.copy(
                isConnecting = true,
                errorMessage = null,
                quickConnectStatus = "Connecting to server...",
            )

            // First test server connection
            when (val serverResult = repository.testServerConnection(normalizedServerUrl)) {
                is ApiResult.Success -> {
                    _connectionState.value = _connectionState.value.copy(
                        quickConnectStatus = "Initiating Quick Connect...",
                    )

                    // Now initiate Quick Connect
                    when (val quickConnectResult = repository.initiateQuickConnect(normalizedServerUrl)) {
                        is ApiResult.Success -> {
                            val result = quickConnectResult.data
                            _connectionState.value = _connectionState.value.copy(
                                isConnecting = false,
                                quickConnectCode = result.code ?: "",
                                quickConnectSecret = result.secret ?: "",
                                isQuickConnectPolling = true,
                                quickConnectStatus = "Code generated! Enter this code in your Jellyfin server.",
                            )

                            // Start polling for approval
                            quickConnectPollingJob = viewModelScope.launch {
                                pollQuickConnectState(normalizedServerUrl, result.secret ?: "")
                            }
                        }
                        is ApiResult.Error -> {
                            _connectionState.value = _connectionState.value.copy(
                                isConnecting = false,
                                errorMessage = "Quick Connect initiation failed: ${quickConnectResult.message}",
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
                        errorMessage = "Cannot connect to server: ${serverResult.message}",
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
            viewModelScope.isActive
        ) { // Check if coroutine is still active
            try {
                delay(5000) // Wait 5 seconds between polls
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Job was cancelled, clean up and exit
                quickConnectPollingJob = null
                return
            }

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
                                        quickConnectStatus = "Connected successfully!",
                                    )
                                    return
                                }
                                is ApiResult.Error -> {
                                    _connectionState.value = _connectionState.value.copy(
                                        isQuickConnectPolling = false,
                                        errorMessage = "Authentication failed: ${authResult.message}",
                                    )
                                    return
                                }
                                is ApiResult.Loading -> {
                                    // This shouldn't happen
                                }
                            }
                        }
                        "Expired" -> {
                            _connectionState.value = _connectionState.value.copy(
                                isQuickConnectPolling = false,
                                errorMessage = "Quick Connect code has expired",
                            )
                            return
                        }
                        else -> {
                            // Still waiting for approval
                            _connectionState.value = _connectionState.value.copy(
                                quickConnectStatus = "Waiting for approval... (${attempts + 1}/60)",
                            )
                        }
                    }
                }
                is ApiResult.Error -> {
                    _connectionState.value = _connectionState.value.copy(
                        isQuickConnectPolling = false,
                        errorMessage = "Failed to check Quick Connect state: ${stateResult.message}",
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
                errorMessage = "Quick Connect timed out. Please try again.",
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
