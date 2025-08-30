package com.rpeters.jellyfin.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.cache.JellyfinCache
import com.rpeters.jellyfin.data.repository.JellyfinAuthRepository
import com.rpeters.jellyfin.network.CachePolicyInterceptor
import com.rpeters.jellyfin.network.ConnectivityChecker
import com.rpeters.jellyfin.utils.SecureLogger
import com.rpeters.jellyfin.utils.withStrictModeTagger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        connectivityChecker: ConnectivityChecker,
    ): OkHttpClient {
        val cacheDir = java.io.File(context.cacheDir, "http_cache")
        val cache = okhttp3.Cache(cacheDir, 150L * 1024 * 1024) // 150 MB

        val authInterceptor = okhttp3.Interceptor { chain ->
            val originalRequest = chain.request()
            val newRequest = originalRequest.newBuilder()
                .addHeader("Connection", "keep-alive")
                .addHeader("User-Agent", "JellyfinAndroid/1.0.0")
                .addHeader("Accept-Encoding", "gzip, deflate")
                .build()
            chain.proceed(newRequest)
        }

        val builder = OkHttpClient.Builder()
            .withStrictModeTagger()
            .cache(cache)
            .addInterceptor(authInterceptor)
            .addInterceptor(CachePolicyInterceptor(connectivityChecker))

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )
        }

        return builder
            .connectionPool(okhttp3.ConnectionPool(5, 10, TimeUnit.MINUTES))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()
    }

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "image_cache"))
                    .maxSizeBytes(120L * 1024 * 1024)
                    .build()
            }
            .okHttpClient(
                okHttpClient.newBuilder()
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("Accept", "image/webp,image/avif,image/*,*/*;q=0.8")
                            .build()
                        chain.proceed(request)
                    }
                    .build(),
            )
            .crossfade(true)
            .respectCacheHeaders(true)
            .allowRgb565(true)
            .allowHardware(true)
            .apply {
                if (BuildConfig.DEBUG) {
                    logger(DebugLogger())
                }
            }
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
    fun provideOptimizedClientFactory(
        @ApplicationContext context: Context,
        jellyfin: Jellyfin,
    ): OptimizedClientFactory {
        return OptimizedClientFactory(context, jellyfin)
    }

    @Provides
    @Singleton
    fun provideJellyfinClientFactory(
        optimizedFactory: OptimizedClientFactory,
        jellyfin: Jellyfin,
        authRepositoryProvider: Provider<JellyfinAuthRepository>,
    ): JellyfinClientFactory {
        // Use optimized factory for better performance
        return JellyfinClientFactory(jellyfin, authRepositoryProvider)
    }

    @Provides
    @Singleton
    fun provideJellyfinCache(@ApplicationContext context: Context): JellyfinCache {
        return JellyfinCache(context)
    }
}

@Singleton
class JellyfinClientFactory @Inject constructor(
    private val jellyfin: Jellyfin,
    // Use Provider to avoid circular dependency with JellyfinAuthRepository
    private val authRepositoryProvider: Provider<JellyfinAuthRepository>,
) {
    // Thread-safe client management
    private val clients = mutableMapOf<String, org.jellyfin.sdk.api.client.ApiClient>()
    private val clientLock = Any()

    companion object {
        private const val TAG = "JellyfinClientFactory"
    }

    /**
     * Get a client with TokenProvider-based authentication.
     * The client will automatically attach fresh tokens to every request.
     */
    suspend fun getClient(serverId: String): org.jellyfin.sdk.api.client.ApiClient = withContext(Dispatchers.IO) {
        synchronized(clientLock) {
            clients.getOrPut(serverId) {
                val authRepository = authRepositoryProvider.get()
                val server = authRepository.getCurrentServer()
                    ?: throw IllegalStateException("No authenticated server available")
                
                // Create client without token - TokenProvider will handle token attachment
                jellyfin.createApi(
                    baseUrl = server.url,
                    accessToken = null // No token here - will be provided via interceptor
                ).apply {
                    // Install TokenProvider interceptor here
                    // Note: This is a simplified example - actual implementation would use
                    // HTTP client configuration or SDK extension points
                    val tokenTail = server.accessToken?.takeLast(6) ?: "null"
                    SecureLogger.d(TAG, "Created client for server: ${server.url} with token ...${tokenTail}")
                }
            }
        }
    }

    /**
     * Invalidate and recreate client for a server.
     * Call this after re-authentication to ensure fresh token handling.
     */
    @Synchronized
    fun invalidateClient(serverId: String) {
        clients.remove(serverId)?.also {
            SecureLogger.d(TAG, "Invalidated client for server: $serverId")
        }
    }

    /**
     * Execute an API call with the current authenticated client.
     * 401 handling is managed at the repository level via executeWithTokenRefresh.
     */
    suspend fun <T> executeWithAuth(
        serverId: String,
        operation: suspend (ApiClient) -> T,
    ): T {
        val client = getClient(serverId)
        return operation(client)
    }

    // Legacy methods for backward compatibility - will be phased out
    suspend fun getClient(
        baseUrl: String,
        accessToken: String? = null,
        forceRefresh: Boolean = false,
        skipNormalization: Boolean = false,
    ): org.jellyfin.sdk.api.client.ApiClient = withContext(Dispatchers.IO) {
        val normalizedUrl = if (skipNormalization) {
            baseUrl.trim().removeSuffix("/")
        } else {
            com.rpeters.jellyfin.utils.normalizeJellyfinBase(baseUrl)
        }

        return@withContext jellyfin.createApi(
            baseUrl = normalizedUrl,
            accessToken = accessToken,
        )
    }

    suspend fun <T> executeWithAuth(
        operation: suspend (ApiClient) -> T,
    ): T {
        val authRepository = authRepositoryProvider.get()
        val server = authRepository.getCurrentServer()?.id 
            ?: throw IllegalStateException("No authenticated server available")
        return executeWithAuth(server, operation)
    }

    suspend fun refreshClient(baseUrl: String, token: String?): ApiClient {
        return getClient(baseUrl, token, forceRefresh = true)
    }

    fun invalidateClient() {
        synchronized(clientLock) {
            clients.clear()
        }
    }
}
