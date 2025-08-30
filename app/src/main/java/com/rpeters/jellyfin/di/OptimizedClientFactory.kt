package com.rpeters.jellyfin.di

import android.content.Context
import android.util.Log
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.repository.JellyfinAuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Optimized client factory with connection pooling and performance optimizations.
 * Reuses HTTP connections and provides optimized client configuration.
 */
@Singleton
class OptimizedClientFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jellyfin: Jellyfin,
    private val authRepositoryProvider: Provider<JellyfinAuthRepository>,
) {
    private val clients = mutableMapOf<String, ApiClient>()
    private val clientLock = Any()
    companion object {
        private const val TAG = "OptimizedClientFactory"
        private const val CONNECTION_POOL_SIZE = 5
        private const val CONNECTION_KEEP_ALIVE_MINUTES = 5L
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val WRITE_TIMEOUT_SECONDS = 30L
    }

    /**
     * Create optimized HTTP client with connection pooling
     */
    private fun createOptimizedClient(serverUrl: String): ApiClient {
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
            .addInterceptor(createTokenInterceptor())
            .addInterceptor(createOptimizedInterceptor())
            .addInterceptor(createLoggingInterceptor())
            .build()

        return jellyfin.createApi(
            baseUrl = serverUrl,
            accessToken = null,
        )
    }

    /**
     * Token interceptor that fetches the current token at request time
     * with proper synchronization and authentication state checking
     */
    private fun createTokenInterceptor(): Interceptor {
        return Interceptor { chain ->
            val authRepository = authRepositoryProvider.get()
            
            // Check if authentication is in progress and wait if necessary
            if (authRepository.isAuthenticating.value) {
                Log.d("TokenInterceptor", "Authentication in progress, waiting...")
                // Wait for authentication to complete with timeout
                var waitTime = 0L
                val maxWaitTime = 10000L // 10 seconds
                val pollInterval = 100L
                
                while (authRepository.isAuthenticating.value && waitTime < maxWaitTime) {
                    Thread.sleep(pollInterval)
                    waitTime += pollInterval
                }
                
                if (waitTime >= maxWaitTime) {
                    Log.w("TokenInterceptor", "Timeout waiting for authentication to complete")
                }
            }
            
            val token = authRepository.token()
            val tokenTail = token?.takeLast(6) ?: "null"
            Log.d("TokenInterceptor", "Attaching token to request: ...$tokenTail")
            
            val request = chain.request().newBuilder()
                .apply {
                    token?.let { 
                        addHeader("X-Emby-Token", it)
                        addHeader("Authorization", "MediaBrowser Token=\"$it\"")
                    }
                }
                .build()
            chain.proceed(request)
        }
    }

    /**
     * Optimized interceptor with keep-alive and compression
     */
    private fun createOptimizedInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Connection", "keep-alive")
                .addHeader("Accept-Encoding", "gzip, deflate")
                .addHeader("User-Agent", "JellyfinAndroid/1.0")
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
     * Helper function for debug logging
     */
    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }
    
    /**
     * Get an optimized client for a server URL with proper token handling
     */
    fun getOptimizedClient(serverUrl: String): ApiClient {
        return synchronized(clientLock) {
            clients.getOrPut(serverUrl) {
                logDebug("Creating new optimized client for: $serverUrl")
                createOptimizedClient(serverUrl)
            }
        }
    }
    
    /**
     * Invalidate client cache for a server
     */
    fun invalidateClient(serverUrl: String) {
        synchronized(clientLock) {
            clients.remove(serverUrl)?.also {
                logDebug("Invalidated optimized client for: $serverUrl")
            }
        }
    }
    
    /**
     * Clear all cached clients
     */
    fun clearAllClients() {
        synchronized(clientLock) {
            clients.clear()
            logDebug("Cleared all optimized clients")
        }
    }
}
