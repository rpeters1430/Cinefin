package com.rpeters.jellyfin.network

import com.rpeters.jellyfin.data.repository.IJellyfinAuthRefreshManager
import com.rpeters.jellyfin.utils.SecureLogger
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authentication interceptor for Jellyfin API requests.
 *
 * Keeps request interception lightweight by reading the current token and
 * delegating refresh orchestration to JellyfinAuthRefreshManager.
 */
@Singleton
class JellyfinAuthInterceptor @Inject constructor(
    private val authRefreshManager: IJellyfinAuthRefreshManager,
    private val deviceIdentityProvider: DeviceIdentityProvider,
) : Interceptor, Authenticator {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val shouldAttachToken = !isAuthenticationRequest(request)

        val token = if (shouldAttachToken) {
            authRefreshManager.scheduleProactiveRefreshIfNeeded()
            val currentToken = authRefreshManager.currentAccessToken()
            if (currentToken == null) {
                SecureLogger.w(TAG, "No access token available while building request")
            }
            currentToken
        } else {
            null
        }

        val updatedRequest = buildRequestWithHeaders(request, token)
        return chain.proceed(updatedRequest)
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        if (!shouldRetry(response)) {
            return null
        }

        val attempt = responseCount(response)
        val token = authRefreshManager.refreshAfterUnauthorized(attempt)

        if (token.isNullOrBlank()) {
            SecureLogger.w(TAG, "Failed to refresh token after 401 (attempt $attempt)")
            return null
        }

        return buildRequestWithHeaders(response.request, token)
    }

    private fun buildRequestWithHeaders(
        request: Request,
        token: String?,
    ): Request {
        val userAgent = "${deviceIdentityProvider.clientName()}/${deviceIdentityProvider.clientVersion()}"
        val builder = request.newBuilder()
            .header(HEADER_CONNECTION, "keep-alive")
            .header(HEADER_USER_AGENT, userAgent)

        builder.header(HEADER_AUTHORIZATION, buildAuthorizationHeader(token))
        if (!token.isNullOrBlank()) {
            builder.header(HEADER_MEDIA_BROWSER_AUTHORIZATION, buildMediaBrowserAuthorization(token))
        }

        if (!token.isNullOrBlank() && !isAuthenticationRequest(request)) {
            builder.header(HEADER_TOKEN, token)
        }

        return builder.build()
    }

    /**
     * Builds the X-Emby-Authorization header value.
     * WARNING: This string contains the authentication token and must NEVER be logged.
     * Only the last 4-6 characters should be logged for debugging purposes.
     */
    private fun buildAuthorizationHeader(token: String?): String {
        return buildString {
            append("MediaBrowser Client=\"")
            append(deviceIdentityProvider.clientName())
            append("\", Device=\"")
            append(deviceIdentityProvider.deviceName())
            append("\", DeviceId=\"")
            append(deviceIdentityProvider.deviceId())
            append("\", Version=\"")
            append(deviceIdentityProvider.clientVersion())
            append("\"")
            if (!token.isNullOrBlank()) {
                append(", Token=\"")
                append(token) // SECURITY: Never log this value
                append("\"")
            }
        }
    }

    private fun buildMediaBrowserAuthorization(token: String): String {
        return "MediaBrowser Token=\"$token\""
    }

    private fun shouldRetry(response: Response): Boolean {
        if (isAuthenticationRequest(response.request)) {
            return false
        }

        if (response.code != 401) {
            return false
        }

        if (responseCount(response) >= MAX_AUTH_RETRIES) {
            SecureLogger.w(TAG, "Max auth retries reached (${responseCount(response)})")
            return false
        }

        return true
    }

    private fun responseCount(response: Response): Int {
        var current: Response? = response
        var count = 1
        while (current?.priorResponse != null) {
            current = current.priorResponse
            count++
        }
        return count
    }

    private fun isAuthenticationRequest(request: Request): Boolean {
        val url = request.url.toString()
        return AUTH_PATHS.any { url.contains(it, ignoreCase = true) }
    }

    companion object {
        private const val TAG = "JellyfinAuthInterceptor"
        private const val HEADER_TOKEN = "X-Emby-Token"
        private const val HEADER_AUTHORIZATION = "X-Emby-Authorization"
        private const val HEADER_MEDIA_BROWSER_AUTHORIZATION = "Authorization"
        private const val HEADER_CONNECTION = "Connection"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val MAX_AUTH_RETRIES = 3
        private val AUTH_PATHS = listOf("/Users/Authenticate", "/Sessions")
    }
}
