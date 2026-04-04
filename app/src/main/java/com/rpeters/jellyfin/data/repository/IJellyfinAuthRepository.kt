package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.model.QuickConnectResult
import com.rpeters.jellyfin.data.model.QuickConnectState
import com.rpeters.jellyfin.data.network.TokenProvider
import com.rpeters.jellyfin.data.repository.common.ApiResult
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.PublicSystemInfo

interface IJellyfinAuthRepository : TokenProvider {
    val currentServer: StateFlow<JellyfinServer?>
    val isConnected: StateFlow<Boolean>
    val isAuthenticating: StateFlow<Boolean>

    fun getCurrentServer(): JellyfinServer?
    fun isUserAuthenticated(): Boolean
    fun isTokenExpired(): Boolean
    fun shouldRefreshToken(): Boolean
    fun seedCurrentServer(server: JellyfinServer?)
    fun restorePersistedSession(server: JellyfinServer)

    suspend fun authenticateUser(
        serverUrl: String,
        username: String,
        password: String,
    ): ApiResult<AuthenticationResult>

    suspend fun reAuthenticate(): Boolean
    suspend fun forceReAuthenticate(): Boolean
    suspend fun logout()
    
    suspend fun testServerConnection(serverUrl: String): ApiResult<PublicSystemInfo>
    suspend fun initiateQuickConnect(serverUrl: String): ApiResult<QuickConnectResult>
    suspend fun getQuickConnectState(serverUrl: String, secret: String): ApiResult<QuickConnectState>
    suspend fun isQuickConnectEnabled(serverUrl: String): ApiResult<Boolean>
    suspend fun authenticateWithQuickConnect(serverUrl: String, secret: String): ApiResult<AuthenticationResult>
}
