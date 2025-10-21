package com.rpeters.jellyfin.network

import android.util.Log
import com.rpeters.jellyfin.data.repository.JellyfinAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class JellyfinAuthInterceptor @Inject constructor(
    private val authRepositoryProvider: Provider<JellyfinAuthRepository>,
) : Interceptor, Authenticator {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val shouldAttachToken = !isAuthenticationRequest(request)
        val repository = authRepositoryProvider.get()

        val token = if (shouldAttachToken) {
            ensureFreshToken(repository)
            repository.getCurrentServer()?.accessToken
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

        val repository = authRepositoryProvider.get()
        val attempt = responseCount(response)
        backoff(attempt)

        val refreshed = runBlocking(Dispatchers.IO) {
            repository.forceReAuthenticate()
        }

        if (!refreshed) {
            Log.w(TAG, "Failed to refresh token after 401 (attempt $attempt)")
            return null
        }

        val token = repository.getCurrentServer()?.accessToken
        return token?.let {
            buildRequestWithHeaders(response.request, it)
        }
    }

    private fun ensureFreshToken(repository: JellyfinAuthRepository) {
        if (!repository.isUserAuthenticated()) {
            return
        }

        if (!repository.isTokenExpired()) {
            return
        }

        runBlocking(Dispatchers.IO) {
            val refreshed = repository.forceReAuthenticate()
            if (!refreshed) {
                Log.w(TAG, "Token refresh failed during request preparation")
            }
        }
    }

    private fun buildRequestWithHeaders(
        request: Request,
        token: String?,
    ): Request {
        val builder = request.newBuilder()
            .header(HEADER_CONNECTION, "keep-alive")
            .header(HEADER_ACCEPT_ENCODING, "gzip, deflate")
            .header(HEADER_USER_AGENT, USER_AGENT)

        if (!token.isNullOrBlank() && !isAuthenticationRequest(request)) {
            builder.header(HEADER_TOKEN, token)
        }

        return builder.build()
    }

    private fun shouldRetry(response: Response): Boolean {
        if (isAuthenticationRequest(response.request)) {
            return false
        }

        if (response.code != 401) {
            return false
        }

        if (responseCount(response) >= MAX_AUTH_RETRIES) {
            Log.w(TAG, "Max auth retries reached (${responseCount(response)})")
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

    private fun backoff(attempt: Int) {
        val delayMillis = when {
            attempt <= RETRY_BACKOFF_MS.size -> RETRY_BACKOFF_MS[attempt - 1]
            else -> RETRY_BACKOFF_MS.last()
        }

        if (delayMillis <= 0) {
            return
        }

        try {
            Thread.sleep(delayMillis)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun isAuthenticationRequest(request: Request): Boolean {
        val url = request.url.toString()
        return AUTH_PATHS.any { url.contains(it, ignoreCase = true) }
    }

    companion object {
        private const val TAG = "JellyfinAuthInterceptor"
        private const val HEADER_TOKEN = "X-Emby-Token"
        private const val HEADER_CONNECTION = "Connection"
        private const val HEADER_ACCEPT_ENCODING = "Accept-Encoding"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val USER_AGENT = "JellyfinAndroid/1.0.0"
        private const val MAX_AUTH_RETRIES = 3
        private val AUTH_PATHS = listOf("/Users/Authenticate", "/Sessions")
        private val RETRY_BACKOFF_MS = longArrayOf(0L, 250L, TimeUnit.SECONDS.toMillis(1))
    }
}
