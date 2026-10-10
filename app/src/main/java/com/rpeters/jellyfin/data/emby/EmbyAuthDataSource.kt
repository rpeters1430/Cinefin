package com.rpeters.jellyfin.data.emby

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jellyfin.sdk.model.api.AuthenticationResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sign-in against an Emby server. The result is returned as the Jellyfin SDK's
 * [AuthenticationResult] so the rest of the auth flow is the same for both server types.
 */
@Singleton
class EmbyAuthDataSource @Inject constructor(
    private val httpClient: EmbyHttpClient,
) {
    suspend fun signIn(serverUrl: String, username: String, password: String): AuthenticationResult {
        val body = buildJsonObject {
            put("Username", username)
            put("Pw", password)
        }
        val response = httpClient.postJson(serverUrl, AUTHENTICATE_PATH, body)
        return EmbyJsonNormalizer.decode(AuthenticationResult.serializer(), response)
    }

    suspend fun logout(serverUrl: String) {
        httpClient.postEmpty(serverUrl, LOGOUT_PATH)
    }

    private companion object {
        const val AUTHENTICATE_PATH = "Users/AuthenticateByName"
        const val LOGOUT_PATH = "Sessions/Logout"
    }
}
