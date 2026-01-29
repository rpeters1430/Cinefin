package com.rpeters.jellyfin.ui.viewmodel

import android.content.Context
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.constants.Constants
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import com.rpeters.jellyfin.data.security.CertificatePinningManager
import com.rpeters.jellyfin.data.security.PinningValidationException
import com.rpeters.jellyfin.ui.components.ConnectionPhase
import com.rpeters.jellyfin.ui.components.ConnectionState
import com.rpeters.jellyfin.ui.components.PinningAlertReason
import com.rpeters.jellyfin.ui.components.PinningAlertState
import com.rpeters.jellyfin.utils.ServerUrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// Use the enhanced ConnectionState from ConnectionProgress.kt
// This data class is now defined in the ConnectionProgress.kt file

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "login_preferences")

object PreferencesKeys {
    val SERVER_URL = stringPreferencesKey("server_url")
    val USERNAME = stringPreferencesKey("username")
    val REMEMBER_LOGIN = booleanPreferencesKey("remember_login")
    val BIOMETRIC_AUTH_ENABLED = booleanPreferencesKey("biometric_auth_enabled") // New preference
    val BIOMETRIC_REQUIRE_STRONG = booleanPreferencesKey("biometric_require_strong")
}

@HiltViewModel
class ServerConnectionViewModel @Inject constructor(
    private val repository: JellyfinRepository,
    private val secureCredentialManager: SecureCredentialManager,
    private val certificatePinningManager: CertificatePinningManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var quickConnectPollingJob: Job? = null
    private var lastAttempt: ConnectionAttempt? = null

    companion object {
        private const val AUTO_LOGIN_DEBOUNCE_MS = 2_000L
        private var lastAutoLoginKey: String? = null
        private var lastAutoLoginAttemptMs: Long = 0L
        private val autoLoginLock = Any()
    }

    init {
        // Load saved credentials and remember login state
        viewModelScope.launch {
            val preferences = context.dataStore.data.first()
            val savedServerUrl = preferences[PreferencesKeys.SERVER_URL] ?: ""
            val savedUsername = preferences[PreferencesKeys.USERNAME] ?: ""
            val rememberPreference = preferences[PreferencesKeys.REMEMBER_LOGIN]
            var rememberLogin = rememberPreference ?: false
            val biometricPreference = preferences[PreferencesKeys.BIOMETRIC_AUTH_ENABLED]
            val isBiometricAuthEnabled = biometricPreference ?: false
            val requireStrongBiometric = preferences[PreferencesKeys.BIOMETRIC_REQUIRE_STRONG] ?: false
            val biometricCapability = secureCredentialManager.getBiometricCapability(requireStrongBiometric)

            // ✅ FIX: Handle suspend function calls properly
            val hasSavedPassword = if (savedServerUrl.isNotBlank() && savedUsername.isNotBlank()) {
                secureCredentialManager.hasSavedPassword(savedServerUrl, savedUsername)
            } else {
                false
            }

            // If credentials exist but the toggle was never persisted, opt the user back in
            // This handles migration for existing users who expect "Remember Login" to be on
            if (hasSavedPassword && rememberPreference == null) {
                updateRememberLoginPreference(true)
                rememberLogin = true
            }

            _connectionState.value = _connectionState.value.copy(
                savedServerUrl = savedServerUrl,
                savedUsername = savedUsername,
                rememberLogin = rememberLogin,
                hasSavedPassword = hasSavedPassword,
                isBiometricAuthEnabled = isBiometricAuthEnabled,
                isBiometricAuthAvailable = isBiometricAuthEnabled && biometricCapability.isAvailable,
                isUsingWeakBiometric = isBiometricAuthEnabled && biometricCapability.isWeakOnly && biometricCapability.isAvailable,
                requireStrongBiometric = requireStrongBiometric,
                // We don't set biometric auth enabled here because we want to check it separately
            )

            // Auto-login if we have saved credentials and remember login is enabled
            if (rememberLogin && savedServerUrl.isNotBlank() && savedUsername.isNotBlank() && hasSavedPassword) {
                android.util.Log.d("ServerConnectionVM", "Auto-login conditions met. Biometric enabled: $isBiometricAuthEnabled, Biometric available: ${biometricCapability.isAvailable}")
                val autoLoginKey = "$savedServerUrl|$savedUsername"
                if (!shouldAutoLoginNow(autoLoginKey)) {
                    android.util.Log.d("ServerConnectionVM", "Auto-login debounced for key: $autoLoginKey")
                    return@launch
                }

                // We don't auto-login if biometric auth is enabled, user needs to trigger it manually
                // However, if biometric is enabled but not available on this device, disable it and auto-login
                if (isBiometricAuthEnabled && !biometricCapability.isAvailable) {
                    android.util.Log.d("ServerConnectionVM", "Biometric auth was enabled but is no longer available - disabling it")
                    // Biometric auth was enabled but is no longer available - disable it
                    context.dataStore.edit { preferences ->
                        preferences[PreferencesKeys.BIOMETRIC_AUTH_ENABLED] = false
                    }
                    updateBiometricCapabilityState(enabled = false, requireStrongBiometric = requireStrongBiometric)
                    // Continue with auto-login below
                }

                if (!isBiometricAuthEnabled || !biometricCapability.isAvailable) {
                    android.util.Log.d("ServerConnectionVM", "🔵 AUTO-LOGIN: Attempting to retrieve password for serverUrl='$savedServerUrl', username='$savedUsername'")
                    val savedPassword = secureCredentialManager.getPassword(savedServerUrl, savedUsername)
                    if (savedPassword != null) {
                        android.util.Log.d("ServerConnectionVM", "🟢 AUTO-LOGIN: Password retrieved successfully (length: ${savedPassword.length})")
                        android.util.Log.d("ServerConnectionVM", "Attempting auto-login for user: $savedUsername")
                        // Auto-login with saved credentials
                        connectToServer(savedServerUrl, savedUsername, savedPassword, isAutoLogin = true)
                    } else {
                        android.util.Log.e("ServerConnectionVM", "🔴 AUTO-LOGIN FAILED: saved password is NULL despite hasSavedPassword=$hasSavedPassword")
                        android.util.Log.e("ServerConnectionVM", "🔴 AUTO-LOGIN FAILED: serverUrl='$savedServerUrl', username='$savedUsername'")
                    }
                } else {
                    android.util.Log.d("ServerConnectionVM", "Auto-login skipped: biometric auth is enabled and available")
                }
            } else {
                android.util.Log.d("ServerConnectionVM", "Auto-login skipped - rememberLogin:$rememberLogin, hasUrl:${savedServerUrl.isNotBlank()}, hasUsername:${savedUsername.isNotBlank()}, hasPassword:$hasSavedPassword")
            }
        }

        // Observe repository connection state
        viewModelScope.launch {
            repository.isConnected.collect { isConnected ->
                _connectionState.value = _connectionState.value.copy(
                    isConnected = isConnected,
                    isConnecting = false,
                )

                // ✅ FIX: Don't automatically clear saved credentials when disconnected
                // This was causing "Remember Login" to fail because credentials were being
                // cleared whenever the connection was lost. Credentials should only be
                // cleared when the user explicitly logs out or disables "Remember Login".
            }
        }
    }

    fun connectToServer(serverUrl: String, username: String, password: String, isAutoLogin: Boolean = false) {
        // Debounce duplicate connection attempts
        val state = _connectionState.value
        if (state.isConnecting || state.isConnected) {
            return
        }
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

        lastAttempt = ConnectionAttempt(
            serverUrl = normalizedServerUrl,
            username = username,
            password = password,
            isAutoLogin = isAutoLogin,
        )
        _connectionState.value = _connectionState.value.copy(
            isConnecting = true,
            errorMessage = null,
            connectionPhase = ConnectionPhase.Testing,
            currentUrl = serverUrl,
            pinningAlert = null,
        )

        viewModelScope.launch {
            // First test server connection with enhanced feedback (IO dispatcher)
            val serverResult = withContext(Dispatchers.IO) {
                repository.testServerConnection(normalizedServerUrl)
            }
            when (serverResult) {
                is ApiResult.Success -> {
                    val serverInfo = serverResult.data
                    _connectionState.value = _connectionState.value.copy(
                        serverName = serverInfo.serverName,
                        connectionPhase = ConnectionPhase.Authenticating,
                    )

                    // Now authenticate with enhanced feedback (IO dispatcher)
                    val authResult = withContext(Dispatchers.IO) {
                        repository.authenticateUser(normalizedServerUrl, username, password)
                    }
                    when (authResult) {
                        is ApiResult.Success -> {
                            // CRITICAL: Save credentials BEFORE setting isConnected = true
                            // This ensures the DataStore operation completes before any navigation
                            // that might cancel the ViewModel scope (password save uses NonCancellable
                            // for extra protection, but proper ordering is still best practice)
                            if (_connectionState.value.rememberLogin) {
                                saveCredentials(normalizedServerUrl, username, password)
                            } else {
                                clearSavedCredentials()
                            }

                            // Now it's safe to set connected state which may trigger navigation
                            _connectionState.value = _connectionState.value.copy(
                                isConnecting = false,
                                isConnected = true,
                                errorMessage = null,
                                connectionPhase = ConnectionPhase.Connected,
                                pinningAlert = null,
                            )
                        }
                        is ApiResult.Error -> {
                            if (handlePinningError(authResult)) {
                                return@launch
                            }
                            // ✅ FIX: Don't clear saved credentials on auth failure during auto-login
                            // Only clear credentials if this is a manual login attempt and the error
                            // is specifically an authentication error (401/403/Unauthorized)
                            // Network errors or temporary failures should never clear saved credentials
                            if (!isAutoLogin &&
                                (
                                    authResult.message.contains("401") ||
                                        authResult.message.contains("403") ||
                                        authResult.message.contains("Unauthorized") ||
                                        authResult.message.contains("Invalid username or password")
                                    )
                            ) {
                                // Only clear for actual auth failures on manual login, not auto-login
                                clearSavedCredentials()
                            }
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
                    if (!handlePinningError(serverResult)) {
                        _connectionState.value = _connectionState.value.copy(
                            isConnecting = false,
                            errorMessage = "Cannot connect to server: ${serverResult.message}",
                            connectionPhase = ConnectionPhase.Error,
                        )
                    }
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

    fun showError(message: String) {
        _connectionState.value = _connectionState.value.copy(errorMessage = message)
    }

    fun dismissPinningAlert() {
        _connectionState.value = _connectionState.value.copy(
            pinningAlert = null,
            isConnecting = false,
        )
    }

    fun temporarilyTrustPin() {
        val pinningAlert = _connectionState.value.pinningAlert ?: return
        viewModelScope.launch {
            val pinsToTrust = if (pinningAlert.attemptedPins.isNotEmpty()) {
                pinningAlert.attemptedPins
            } else {
                pinningAlert.certificateDetails.map { it.pin }
            }
            if (pinsToTrust.isEmpty()) {
                _connectionState.value = _connectionState.value.copy(
                    pinningAlert = null,
                    isConnecting = false,
                    errorMessage = context.getString(R.string.pinning_no_pins_available),
                )
                return@launch
            }
            certificatePinningManager.allowTemporaryTrust(
                pinningAlert.hostname,
                pinsToTrust,
            )
            _connectionState.value = _connectionState.value.copy(
                pinningAlert = null,
                errorMessage = null,
                isConnecting = false,
            )
            lastAttempt?.let { attempt ->
                connectToServer(
                    attempt.serverUrl,
                    attempt.username,
                    attempt.password,
                    attempt.isAutoLogin,
                )
            }
        }
    }

    private fun handlePinningError(error: ApiResult.Error<*>): Boolean {
        val pinningException = error.cause?.findPinningException()
        if (error.errorType != ErrorType.PINNING && pinningException == null) {
            return false
        }

        val exception = pinningException
        val pinningAlert = exception?.toAlertState()
        val message = when (exception) {
            is PinningValidationException.PinExpired -> context.getString(
                R.string.pinning_expired_error,
                exception.hostname,
            )
            is PinningValidationException.PinMismatch -> context.getString(
                R.string.pinning_mismatch_error,
                exception.hostname,
            )
            else -> error.message
        }

        _connectionState.value = _connectionState.value.copy(
            isConnecting = false,
            errorMessage = message,
            connectionPhase = ConnectionPhase.Error,
            pinningAlert = pinningAlert,
        )

        return true
    }

    private fun Throwable.findPinningException(): PinningValidationException? {
        var current: Throwable? = this
        while (current != null) {
            if (current is PinningValidationException) {
                return current
            }
            current = current.cause
        }
        return null
    }

    private fun PinningValidationException.toAlertState(): PinningAlertState {
        val reason = when (this) {
            is PinningValidationException.PinExpired -> PinningAlertReason.EXPIRED
            else -> PinningAlertReason.MISMATCH
        }

        return PinningAlertState(
            hostname = hostname,
            reason = reason,
            certificateDetails = certificateDetails,
            firstSeenEpochMillis = pinRecord?.firstSeenEpochMillis,
            expiresAtEpochMillis = pinRecord?.expiresAtEpochMillis,
            attemptedPins = attemptedPins,
        )
    }

    private suspend fun updateRememberLoginPreference(remember: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.REMEMBER_LOGIN] = remember
        }
        _connectionState.value = _connectionState.value.copy(rememberLogin = remember)
    }

    private suspend fun saveCredentials(serverUrl: String, username: String, password: String) {
        android.util.Log.d("ServerConnectionVM", "🔵 saveCredentials: CALLED with serverUrl='$serverUrl', username='$username'")
        // CRITICAL: Normalize the URL using the same function that SecureCredentialManager uses
        // to ensure consistent key generation for password encryption/decryption
        val normalizedUrl = com.rpeters.jellyfin.utils.normalizeServerUrl(serverUrl)
        android.util.Log.d("ServerConnectionVM", "saveCredentials: Normalized URL: '$normalizedUrl'")

        android.util.Log.d("ServerConnectionVM", "saveCredentials: Saving URL and username to DataStore...")
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SERVER_URL] = normalizedUrl
            preferences[PreferencesKeys.USERNAME] = username
        }
        android.util.Log.d("ServerConnectionVM", "saveCredentials: URL and username saved to DataStore ✅")

        android.util.Log.d("ServerConnectionVM", "saveCredentials: Calling secureCredentialManager.savePassword()...")
        secureCredentialManager.savePassword(normalizedUrl, username, password)
        android.util.Log.d("ServerConnectionVM", "saveCredentials: secureCredentialManager.savePassword() COMPLETED ✅")

        android.util.Log.d("ServerConnectionVM", "Saved credentials with normalized URL: $normalizedUrl (original: $serverUrl)")
        _connectionState.value = _connectionState.value.copy(
            savedServerUrl = normalizedUrl,
            savedUsername = username,
            hasSavedPassword = true,
        )
        android.util.Log.d("ServerConnectionVM", "🟢 saveCredentials: FINISHED - hasSavedPassword=true")
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
            updateRememberLoginPreference(remember)
            if (!remember) {
                clearSavedCredentials()
            }
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
            updateBiometricCapabilityState(enabled = enabled)
        }
    }

    fun setRequireStrongBiometric(requireStrongBiometric: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.BIOMETRIC_REQUIRE_STRONG] = requireStrongBiometric
            }
            updateBiometricCapabilityState(requireStrongBiometric = requireStrongBiometric)
            try {
                secureCredentialManager.applyCredentialAuthenticationRequirement(requireStrongBiometric)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ServerConnectionVM", "Failed to update credential auth requirement", e)
            }
        }
    }

    private fun updateBiometricCapabilityState(
        enabled: Boolean? = null,
        requireStrongBiometric: Boolean? = null,
    ) {
        val currentState = _connectionState.value
        val resolvedEnabled = enabled ?: currentState.isBiometricAuthEnabled
        val resolvedRequireStrong = requireStrongBiometric ?: currentState.requireStrongBiometric
        val capability = secureCredentialManager.getBiometricCapability(resolvedRequireStrong)
        _connectionState.value = currentState.copy(
            isBiometricAuthEnabled = resolvedEnabled,
            isBiometricAuthAvailable = resolvedEnabled && capability.isAvailable,
            isUsingWeakBiometric = resolvedEnabled && capability.isWeakOnly && capability.isAvailable,
            requireStrongBiometric = resolvedRequireStrong,
        )
    }

    /**
     * Gets a saved password with optional biometric authentication
     */
    suspend fun getSavedPassword(
        activity: FragmentActivity? = null,
        requireStrongBiometric: Boolean = _connectionState.value.requireStrongBiometric,
    ): String? {
        val currentState = _connectionState.value
        return if (currentState.savedServerUrl.isNotBlank() && currentState.savedUsername.isNotBlank()) {
            secureCredentialManager.getPassword(
                currentState.savedServerUrl,
                currentState.savedUsername,
                activity,
                requireStrongBiometric,
            )
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
                        isAutoLogin = true,
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
        val biometricCapability = secureCredentialManager.getBiometricCapability(currentState.requireStrongBiometric)

        if (!biometricCapability.isAvailable) {
            showError(context.getString(R.string.biometric_unavailable_error))
            return
        }

        if (currentState.savedServerUrl.isBlank() || currentState.savedUsername.isBlank()) {
            showError(context.getString(R.string.biometric_no_credentials_error))
            return
        }

        if (!currentState.hasSavedPassword) {
            showError(context.getString(R.string.biometric_no_credentials_error))
            return
        }

        viewModelScope.launch {
            val savedPassword = getSavedPassword(activity, currentState.requireStrongBiometric)
            if (savedPassword != null) {
                connectToServer(
                    currentState.savedServerUrl,
                    currentState.savedUsername,
                    savedPassword,
                    isAutoLogin = true,
                )
            } else {
                showError(context.getString(R.string.biometric_failed_error))
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
                                quickConnectCode = result.code,
                                quickConnectSecret = result.secret,
                                isQuickConnectPolling = true,
                                quickConnectStatus = "Code generated! Enter this code in your Jellyfin server.",
                            )

                            // Start polling for approval
                            quickConnectPollingJob = viewModelScope.launch {
                                pollQuickConnectState(normalizedServerUrl, result.secret)
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
        val maxAttempts = 150 // 5 minutes with 2-second intervals (300 seconds / 2 = 150 attempts)

        while (attempts < maxAttempts &&
            _connectionState.value.isQuickConnectPolling &&
            viewModelScope.isActive
        ) { // Check if coroutine is still active
            try {
                delay(Constants.QUICK_CONNECT_POLL_INTERVAL_MS) // Wait between polls (2 seconds as per spec)
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
                        "Denied" -> {
                            _connectionState.value = _connectionState.value.copy(
                                isQuickConnectActive = false,
                                isQuickConnectPolling = false,
                                errorMessage = "Quick Connect request was denied",
                            )
                            return
                        }
                        else -> {
                            // Still waiting for approval
                            _connectionState.value = _connectionState.value.copy(
                                quickConnectStatus = "Waiting for approval... (${attempts + 1}/150)",
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

    /**
     * Explicit logout method that clears saved credentials and disconnects
     */
    fun logout() {
        viewModelScope.launch {
            // Clear saved credentials when user explicitly logs out
            clearSavedCredentials()

            // Reset connection state
            _connectionState.value = ConnectionState()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Cancel any ongoing quick connect polling when ViewModel is destroyed
        quickConnectPollingJob?.cancel()
    }

    private fun shouldAutoLoginNow(key: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        synchronized(autoLoginLock) {
            if (lastAutoLoginKey == key && now - lastAutoLoginAttemptMs < AUTO_LOGIN_DEBOUNCE_MS) {
                return false
            }
            lastAutoLoginKey = key
            lastAutoLoginAttemptMs = now
            return true
        }
    }
}

private data class ConnectionAttempt(
    val serverUrl: String,
    val username: String,
    val password: String,
    val isAutoLogin: Boolean,
)
