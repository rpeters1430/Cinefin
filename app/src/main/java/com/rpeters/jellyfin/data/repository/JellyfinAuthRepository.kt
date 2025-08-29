package com.rpeters.jellyfin.data.repository

import android.content.Context
import android.util.Log
import com.rpeters.jellyfin.data.SecureStorage
import com.rpeters.jellyfin.data.model.UserCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.KtorClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinAuthRepository @Inject constructor(
    private val context: Context,
    private val secureStorage: SecureStorage,
) {
    private val authMutex = Mutex()
    private var cachedCredentials: UserCredentials? = null

    companion object {
        private const val TAG = "JellyfinAuthRepository"
    }

    // Your existing authenticateUser method stays the same
    suspend fun authenticateUser(username: String, password: String, serverUrl: String): Result<UserCredentials> = withContext(Dispatchers.IO) {
        Log.d(TAG, "authenticateUser: Attempting authentication for user '$username' on server '$serverUrl'")

        try {
            val jellyfin = Jellyfin()
            val client = jellyfin.createApi(baseUrl = serverUrl) as KtorClient

            Log.d(TAG, "authenticateUser: Authenticating user '$username' on '$serverUrl'")

            val response = client.userApi.authenticateUserByName(
                authenticateUserByName = AuthenticateUserByName(
                    username = username,
                    pw = password,
                ),
            )

            val authResult = response.content
            Log.d(TAG, "authenticateUser: Authentication successful for user '$username'")
            Log.d(TAG, "authenticateUser: New access token: ${authResult.accessToken?.take(10)}...")

            val credentials = UserCredentials(
                username = username,
                password = password, // Store for re-authentication
                serverUrl = serverUrl,
                accessToken = authResult.accessToken ?: "",
                userId = authResult.user?.id ?: "",
                deviceId = client.deviceInfo.id,
            )

            Log.d(TAG, "authenticateUser: Saving credentials for user '$username' on server '$serverUrl'")
            saveCredentials(credentials)
            cachedCredentials = credentials
            Log.d(TAG, "authenticateUser: Saved credentials for user '$username' on server '$serverUrl'")

            Result.success(credentials)
        } catch (e: Exception) {
            Log.e(TAG, "authenticateUser: Failed to authenticate user '$username'", e)
            Result.failure(e)
        }
    }

    /**
     * MISSING METHOD - This is what your MediaRepository is calling!
     * Attempts to refresh the current access token
     */
    suspend fun refreshToken(): Result<UserCredentials> = withContext(Dispatchers.IO) {
        authMutex.withLock {
            Log.d(TAG, "refreshToken: Attempting to refresh token")

            try {
                // Get current credentials
                val currentCreds = cachedCredentials ?: getCurrentCredentials().getOrNull()
                if (currentCreds == null) {
                    Log.e(TAG, "refreshToken: No credentials available for refresh")
                    return@withContext Result.failure(Exception("No credentials available for refresh"))
                }

                Log.d(TAG, "refreshToken: Attempting token refresh for user '${currentCreds.username}'")

                // Try to refresh using the Jellyfin API
                val jellyfin = Jellyfin()
                val client = jellyfin.createApi(baseUrl = currentCreds.serverUrl) as KtorClient

                // Set current token for the refresh attempt
                client.accessToken = currentCreds.accessToken

                try {
                    // Attempt to get user info to validate current token
                    val userResponse = client.userApi.getCurrentUser()

                    // If we get here, token is still valid
                    Log.d(TAG, "refreshToken: Current token is still valid")
                    return@withContext Result.success(currentCreds)
                } catch (e: InvalidStatusException) {
                    if (e.status == 401) {
                        Log.d(TAG, "refreshToken: Current token expired, performing re-authentication")

                        // Token is expired, perform fresh login with stored password
                        return@withContext performReAuthentication(currentCreds)
                    } else {
                        throw e
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "refreshToken: Token refresh failed", e)

                // Try re-authentication as fallback
                cachedCredentials?.let { creds ->
                    Log.d(TAG, "refreshToken: Attempting re-authentication as fallback")
                    return@withContext performReAuthentication(creds)
                }

                Result.failure(e)
            }
        }
    }

    /**
     * Performs fresh authentication using stored credentials
     */
    private suspend fun performReAuthentication(oldCredentials: UserCredentials): Result<UserCredentials> {
        return try {
            Log.d(TAG, "performReAuthentication: Re-authenticating user '${oldCredentials.username}'")

            val jellyfin = Jellyfin()
            val client = jellyfin.createApi(baseUrl = oldCredentials.serverUrl) as KtorClient

            val response = client.userApi.authenticateUserByName(
                authenticateUserByName = AuthenticateUserByName(
                    username = oldCredentials.username,
                    pw = oldCredentials.password,
                ),
            )

            val authResult = response.content
            if (authResult.accessToken.isNullOrEmpty()) {
                throw Exception("No access token received during re-authentication")
            }

            val newCredentials = oldCredentials.copy(
                accessToken = authResult.accessToken!!,
                userId = authResult.user?.id ?: oldCredentials.userId,
            )

            Log.d(TAG, "performReAuthentication: Re-authentication successful")
            Log.d(TAG, "performReAuthentication: New access token: ${newCredentials.accessToken.take(10)}...")

            // Save new credentials
            saveCredentials(newCredentials)
            cachedCredentials = newCredentials

            Result.success(newCredentials)
        } catch (e: Exception) {
            Log.e(TAG, "performReAuthentication: Re-authentication failed for user '${oldCredentials.username}'", e)
            Result.failure(e)
        }
    }

    /**
     * Get current valid credentials, attempting refresh if needed
     */
    suspend fun getValidCredentials(): Result<UserCredentials> {
        return refreshToken()
    }

    /**
     * Clear cached credentials (for logout)
     */
    suspend fun clearCredentials() {
        authMutex.withLock {
            cachedCredentials = null
            secureStorage.remove("jellyfin_credentials")
            Log.d(TAG, "clearCredentials: Credentials cleared")
        }
    }

    // Your existing methods stay the same
    suspend fun getCurrentCredentials(): Result<UserCredentials> = withContext(Dispatchers.IO) {
        try {
            cachedCredentials?.let {
                Log.d(TAG, "getCurrentCredentials: Returning cached credentials")
                return@withContext Result.success(it)
            }

            val credentialsJson = secureStorage.getString("jellyfin_credentials")
            if (credentialsJson.isNullOrEmpty()) {
                Log.d(TAG, "getCurrentCredentials: No credentials found in storage")
                return@withContext Result.failure(Exception("No credentials found"))
            }

            // Parse the stored credentials (you'll need to implement JSON parsing)
            val credentials = parseCredentialsFromJson(credentialsJson)
            cachedCredentials = credentials

            Log.d(TAG, "getCurrentCredentials: Loaded credentials from storage")
            Result.success(credentials)
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentCredentials: Failed to load credentials", e)
            Result.failure(e)
        }
    }

    private suspend fun saveCredentials(credentials: UserCredentials) = withContext(Dispatchers.IO) {
        try {
            // Convert credentials to JSON and store securely
            val credentialsJson = convertCredentialsToJson(credentials)
            secureStorage.putString("jellyfin_credentials", credentialsJson)
            Log.d(TAG, "saveCredentials: Credentials saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "saveCredentials: Failed to save credentials", e)
            throw e
        }
    }

    // You'll need to implement these JSON parsing methods
    private fun parseCredentialsFromJson(json: String): UserCredentials {
        // Implement JSON parsing logic here
        // You can use Gson, Moshi, or kotlinx.serialization
        // For now, this is a placeholder
        TODO("Implement JSON parsing")
    }

    private fun convertCredentialsToJson(credentials: UserCredentials): String {
        // Implement JSON serialization logic here
        TODO("Implement JSON serialization")
    }
}
