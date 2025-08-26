package com.rpeters.jellyfin.di

import android.content.Context
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.cache.JellyfinCache
import com.rpeters.jellyfin.data.repository.JellyfinAuthRepository
import com.rpeters.jellyfin.utils.SecureLogger
import com.rpeters.jellyfin.utils.ServerUrlValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().apply {
            // Enhanced interceptor to tag network traffic for StrictMode compliance
            // Apply as both network and application interceptor for complete coverage
            val trafficTagInterceptor = { chain: okhttp3.Interceptor.Chain ->
                val request = chain.request()
                
                // Create a stable, unique tag based on request details
                val url = request.url.toString()
                val method = request.method
                val tagString = "$method:${url.take(50)}" // First 50 chars of URL + method
                val stableTag = tagString.hashCode() and 0x0FFFFFFF // Ensure positive value
                
                // Apply tag for all socket operations during this request
                android.net.TrafficStats.setThreadStatsTag(stableTag)
                
                try {
                    val response = chain.proceed(request)
                    // Ensure tag is maintained during response processing
                    response
                } finally {
                    // Always clear tag after request completes to prevent leak to other operations
                    android.net.TrafficStats.clearThreadStatsTag()
                }
            }
            
            // Apply as network interceptor (runs for each network connection)
            addNetworkInterceptor(trafficTagInterceptor)
            // Apply as application interceptor (runs once per request)
            addInterceptor(trafficTagInterceptor)

            // Add application interceptor with additional headers and connection optimization
            addInterceptor { chain ->
                val originalRequest = chain.request()
                val newRequest = originalRequest.newBuilder()
                    .addHeader("Connection", "keep-alive")
                    .addHeader("User-Agent", "JellyfinAndroid/1.0.0")
                    .addHeader("Accept-Encoding", "gzip, deflate") // Explicit compression
                    .addHeader("Cache-Control", "no-cache") // Prevent caching issues
                    .build()
                
                // Ensure traffic is tagged before any socket operations
                val url = originalRequest.url.toString()
                val method = originalRequest.method
                val tagString = "$method:${url.take(50)}"
                val stableTag = tagString.hashCode() and 0x0FFFFFFF
                android.net.TrafficStats.setThreadStatsTag(stableTag)
                
                try {
                    chain.proceed(newRequest)
                } finally {
                    android.net.TrafficStats.clearThreadStatsTag()
                }
            }

            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    },
                )
            }
        }
            // Optimized connection pool for mobile - fewer connections, longer keep-alive
            .connectionPool(okhttp3.ConnectionPool(5, 10, TimeUnit.MINUTES))
            // Aggressive timeouts to prevent main thread blocking
            .connectTimeout(8, TimeUnit.SECONDS) // Quick connection timeout
            .readTimeout(25, TimeUnit.SECONDS) // Reasonable read timeout
            .writeTimeout(12, TimeUnit.SECONDS) // Quick write timeout
            .retryOnConnectionFailure(true)
            // Enable HTTP/2 for better performance
            .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
            .build()
    }

    @Provides
    @Singleton
    fun provideJellyfinSdk(@ApplicationContext context: Context): Jellyfin {
        return createJellyfin {
            clientInfo = ClientInfo(
                name = "Jellyfin Android",
                version = "1.0.0",
            )
            deviceInfo = DeviceInfo(
                id = "android-jellyfin-client",
                name = "Android Device",
            )
            this.context = context
        }
    }

    @Provides
    @Singleton
    fun provideJellyfinClientFactory(
        jellyfin: Jellyfin,
        authRepositoryProvider: Provider<JellyfinAuthRepository>,
    ): JellyfinClientFactory {
        return JellyfinClientFactory(jellyfin, authRepositoryProvider)
    }

    @Provides
    @Singleton
    fun provideJellyfinCache(@ApplicationContext context: Context, okHttpClient: OkHttpClient): JellyfinCache {
        // Initialize image loading with the same OkHttpClient for consistency
        com.rpeters.jellyfin.utils.ImageLoadingOptimizer.initializeCoil(context, okHttpClient)
        return JellyfinCache(context)
    }
}

@Singleton
class JellyfinClientFactory @Inject constructor(
    private val jellyfin: Jellyfin,
    // Use Provider to avoid circular dependency with JellyfinAuthRepository
    private val authRepositoryProvider: Provider<JellyfinAuthRepository>,
) {
    // Use volatile to ensure thread-safe visibility across coroutines
    @Volatile
    private var currentClient: org.jellyfin.sdk.api.client.ApiClient? = null

    @Volatile
    private var currentBaseUrl: String? = null

    @Volatile
    private var currentToken: String? = null

    // Synchronization object for thread-safe client creation
    private val clientLock = Any()

    companion object {
        private const val TAG = "JellyfinClientFactory"
        private const val MAX_AUTH_RETRIES = 1
    }

    /**
     * Create Jellyfin API client on background thread to avoid StrictMode violations.
     * The client creation involves file I/O operations during static initialization
     * of the Ktor HTTP client, which must be done off the main thread.
     */
    suspend fun getClient(baseUrl: String, accessToken: String? = null): org.jellyfin.sdk.api.client.ApiClient = withContext(Dispatchers.IO) {
        // Validate and normalize the URL properly
        val validatedUrl = ServerUrlValidator.validateAndNormalizeUrl(baseUrl)
            ?: throw IllegalArgumentException("Invalid server URL: $baseUrl")
        val normalizedUrl = validatedUrl.trimEnd('/') + "/"

        // Use synchronized block to prevent race conditions during client creation
        synchronized(clientLock) {
            if (currentToken != accessToken || currentBaseUrl != normalizedUrl || currentClient == null) {
                // This is where the StrictMode violation was occurring - Ktor/ServiceLoader static init
                currentClient = jellyfin.createApi(
                    baseUrl = normalizedUrl,
                    accessToken = accessToken,
                )
                currentBaseUrl = normalizedUrl
                currentToken = accessToken
            }

            return@synchronized currentClient ?: throw IllegalStateException("Failed to create Jellyfin API client for URL: $normalizedUrl")
        }
    }

    /**
     * Execute an API call with automatic 401 handling and re-authentication.
     * This provides centralized 401 handling at the client level.
     *
     * Usage in repositories:
     * ```kotlin
     * return clientFactory.executeWithAuth { client ->
     *     client.userLibraryApi.getUserLibraries(...)
     * }
     * ```
     *
     * This replaces the need for individual repositories to handle 401 errors
     * and maintains authentication state centrally.
     */
    suspend fun <T> executeWithAuth(
        operation: suspend (ApiClient) -> T,
    ): T {
        return executeWithAuthRetry(operation, retryCount = 0)
    }

    private suspend fun <T> executeWithAuthRetry(
        operation: suspend (ApiClient) -> T,
        retryCount: Int,
    ): T {
        val authRepository = authRepositoryProvider.get()
        val currentServer = authRepository.getCurrentServer()
            ?: throw IllegalStateException("No authenticated server available")

        // Client creation is now properly done on background thread
        val client = getClient(currentServer.url, currentServer.accessToken)

        return try {
            operation(client)
        } catch (e: InvalidStatusException) {
            // Check if this is a 401 error
            val is401 = e.message?.contains("401") == true

            if (is401 && retryCount < MAX_AUTH_RETRIES) {
                SecureLogger.w(TAG, "401 Unauthorized detected, attempting re-authentication")

                // Attempt re-authentication
                val reAuthSuccess = authRepository.reAuthenticate()

                if (reAuthSuccess) {
                    SecureLogger.auth(TAG, "Re-authentication successful, retrying operation", true)

                    // Invalidate client to ensure fresh token is used
                    invalidateClient()

                    // Retry the operation with incremented count
                    return executeWithAuthRetry(operation, retryCount + 1)
                } else {
                    SecureLogger.auth(TAG, "Re-authentication failed, user will be logged out", false)
                    throw e
                }
            } else {
                // Not a 401 error or max retries reached
                if (is401 && retryCount >= MAX_AUTH_RETRIES) {
                    SecureLogger.w(TAG, "Max auth retries reached for 401 error, giving up")
                }
                throw e
            }
        } catch (e: Exception) {
            // For non-401 exceptions, just rethrow
            throw e
        }
    }

    fun invalidateClient() {
        synchronized(clientLock) {
            currentClient = null
            currentBaseUrl = null
            currentToken = null
        }
    }
}
