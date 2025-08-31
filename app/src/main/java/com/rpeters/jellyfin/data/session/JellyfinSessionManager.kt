package com.rpeters.jellyfin.data.session

import com.rpeters.jellyfin.core.LogCategory
import com.rpeters.jellyfin.core.Logger
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.repository.JellyfinAuthRepository
import com.rpeters.jellyfin.data.utils.RepositoryUtils
import com.rpeters.jellyfin.di.OptimizedClientFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central session manager responsible for:
 * - Providing an ApiClient bound to the current token
 * - Proactive token refresh and single-flight re-authentication
 * - 401-aware retry with fresh client after re-authentication
 */
@Singleton
class JellyfinSessionManager @Inject constructor(
    private val authRepository: JellyfinAuthRepository,
    private val optimizedClientFactory: OptimizedClientFactory,
) {
    private val reauthMutex = Mutex()

    private fun currentServerOrThrow(): JellyfinServer =
        authRepository.getCurrentServer() ?: throw IllegalStateException("No authenticated server available")

    /**
     * Returns an ApiClient for the provided server URL, bound to the current token.
     * A new client is created automatically when the token changes.
     */
    fun getClientForUrl(serverUrl: String): ApiClient =
        optimizedClientFactory.getOptimizedClient(serverUrl)

    /**
     * Returns an ApiClient for the current authenticated server.
     */
    fun getClient(): ApiClient = getClientForUrl(currentServerOrThrow().url)

    /**
     * Executes an operation with proactive token validation and a single 401-driven retry.
     */
    suspend fun <T> executeWithAuth(
        operationName: String,
        block: suspend (server: JellyfinServer, client: ApiClient) -> T,
    ): T {
        // Proactive refresh if expired
        if (authRepository.isTokenExpired()) {
            reauthMutex.withLock {
                if (authRepository.isTokenExpired()) {
                    Logger.d(LogCategory.NETWORK, "SessionManager", "Token expired, forcing re-authentication")
                    authRepository.forceReAuthenticate()
                    // Give client cache a moment to rebuild with new token
                    delay(50)
                }
            }
        }

        val server = currentServerOrThrow()
        val client = getClientForUrl(server.url)

        return try {
            block(server, client)
        } catch (e: Exception) {
            if (!RepositoryUtils.is401Error(e)) throw e

            // 401 path: single-flight reauth and retry once
            reauthMutex.withLock {
                // If another coroutine already reauthed, skip
                val inProgress = authRepository.isAuthenticating.first()
                if (!inProgress) {
                    Logger.d(LogCategory.NETWORK, "SessionManager", "$operationName: 401 detected, forcing re-authentication")
                    authRepository.forceReAuthenticate()
                    delay(50)
                } else {
                    Logger.d(LogCategory.NETWORK, "SessionManager", "$operationName: 401 detected, join ongoing re-auth")
                }
            }

            val freshServer = currentServerOrThrow()
            val freshClient = getClientForUrl(freshServer.url)
            block(freshServer, freshClient)
        }
    }

    fun invalidateClients() {
        optimizedClientFactory.clearAllClients()
    }
}
