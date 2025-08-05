package com.example.jellyfinandroid.data.repository

import android.util.Log
import com.example.jellyfinandroid.data.model.ApiResult
import com.example.jellyfinandroid.data.model.JellyfinError
import com.example.jellyfinandroid.di.JellyfinClientFactory
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.model.api.PublicSystemInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ✅ IMPROVEMENT: Simplified system repository for Phase 2
 */
@Singleton
class JellyfinSystemRepository @Inject constructor(
    private val clientFactory: JellyfinClientFactory,
) {
    companion object {
        private const val TAG = "JellyfinSystemRepository"
    }

    private fun getClient(serverUrl: String): ApiClient {
        return clientFactory.getClient(serverUrl, null)
    }

    private fun <T> handleException(e: Exception, defaultMessage: String = "System error"): ApiResult.Error<T> {
        Log.e(TAG, "$defaultMessage: ${e.message}", e)

        val error = when {
            e.message?.contains("network", ignoreCase = true) == true -> JellyfinError.NetworkError
            e.message?.contains("connection", ignoreCase = true) == true -> JellyfinError.NetworkError
            e.message?.contains("timeout", ignoreCase = true) == true -> JellyfinError.TimeoutError
            e.message?.contains("server", ignoreCase = true) == true -> JellyfinError.ServerError
            e.message?.contains("host", ignoreCase = true) == true -> JellyfinError.NetworkError
            else -> JellyfinError.UnknownError(e.message ?: defaultMessage, e)
        }

        return error.toApiResult()
    }

    /**
     * ✅ IMPROVEMENT: Enhanced server connection testing
     */
    suspend fun testServerConnection(serverUrl: String): ApiResult<PublicSystemInfo> {
        return try {
            val client = getClient(serverUrl)
            val response = client.systemApi.getPublicSystemInfo()
            ApiResult.Success(response.content)
        } catch (e: Exception) {
            handleException<PublicSystemInfo>(e, "Failed to connect to server")
        }
    }

    /**
     * ✅ IMPROVEMENT: Validate server URL format
     */
    fun validateServerUrl(serverUrl: String): Boolean {
        return try {
            val trimmed = serverUrl.trim()
            when {
                trimmed.isBlank() -> false
                !trimmed.startsWith("http://") && !trimmed.startsWith("https://") -> false
                trimmed.contains(" ") -> false
                else -> {
                    // Basic URL validation
                    val url = java.net.URL(trimmed)
                    url.host.isNotBlank()
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * ✅ IMPROVEMENT: Normalize server URL
     */
    fun normalizeServerUrl(serverUrl: String): String {
        val trimmed = serverUrl.trim()
        return when {
            trimmed.isBlank() -> ""
            !trimmed.startsWith("http") -> "https://$trimmed"
            else -> trimmed.trimEnd('/')
        }
    }
}
