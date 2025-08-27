package com.rpeters.jellyfin.di

import android.content.Context
import android.util.Log
import com.rpeters.jellyfin.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.jellyfin.sdk.api.client.ApiClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optimized client factory with connection pooling and performance optimizations.
 * Reuses HTTP connections and provides optimized client configuration.
 */
@Singleton
class OptimizedClientFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "OptimizedClientFactory"
        private const val CONNECTION_POOL_SIZE = 5
        private const val CONNECTION_KEEP_ALIVE_MINUTES = 5L
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val WRITE_TIMEOUT_SECONDS = 30L
    }

    private val clientCache = mutableMapOf<String, ApiClient>()
    private val clientMutex = Mutex()

    /**
     * Get or create cached API client for better performance
     */
    suspend fun getClient(serverUrl: String, accessToken: String? = null): ApiClient {
        val cacheKey = "$serverUrl:$accessToken"

        return clientMutex.withLock {
            clientCache[cacheKey]?.let { cachedClient ->
                logDebug("Reusing cached client for: $serverUrl")
                return@withLock cachedClient
            }

            logDebug("Creating new optimized client for: $serverUrl")

            // Create new client with optimized configuration
            val newClient = createOptimizedClient(serverUrl, accessToken)
            clientCache[cacheKey] = newClient
            newClient
        }
    }

    /**
     * Create optimized HTTP client with connection pooling
     */
    private fun createOptimizedClient(serverUrl: String, accessToken: String?): ApiClient {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectionPool(
                ConnectionPool(
                    CONNECTION_POOL_SIZE,
                    CONNECTION_KEEP_ALIVE_MINUTES,
                    TimeUnit.MINUTES,
                ),
            )
            .addInterceptor(createOptimizedInterceptor(accessToken))
            .addInterceptor(createLoggingInterceptor())
            .build()

        return ApiClient.Builder()
            .baseUrl(serverUrl)
            .httpClient(okHttpClient)
            .build()
    }

    /**
     * Optimized interceptor with keep-alive and compression
     */
    private fun createOptimizedInterceptor(accessToken: String?): Interceptor {
        return Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Connection", "keep-alive")
                .addHeader("Accept-Encoding", "gzip, deflate")
                .addHeader("User-Agent", "JellyfinAndroid/1.0")
                .apply {
                    accessToken?.let { token ->
                        addHeader("X-Emby-Token", token)
                    }
                }
                .build()

            chain.proceed(request)
        }
    }

    /**
     * Logging interceptor for debug builds
     */
    private fun createLoggingInterceptor(): Interceptor {
        return HttpLoggingInterceptor { message ->
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "HTTP: $message")
            }
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    /**
     * Clear client cache (useful for testing or memory management)
     */
    suspend fun clearCache() {
        clientMutex.withLock {
            logDebug("Clearing client cache (${clientCache.size} clients)")
            clientCache.clear()
        }
    }

    /**
     * Get cache statistics for debugging
     */
    fun getCacheStats(): String {
        return "Cached clients: ${clientCache.size}"
    }

    /**
     * Helper function for debug logging
     */
    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }
}
