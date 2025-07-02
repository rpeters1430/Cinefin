package com.example.jellyfinandroid.di

import com.example.jellyfinandroid.network.JellyfinApiService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .addInterceptor(JellyfinClientInterceptor())
            .addInterceptor(RetryInterceptor())
            .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideJellyfinApiServiceFactory(
        okHttpClient: OkHttpClient,
        json: Json
    ): JellyfinApiServiceFactory {
        return JellyfinApiServiceFactory(okHttpClient, json)
    }
}

@Singleton
class JellyfinApiServiceFactory @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private var currentApiService: JellyfinApiService? = null
    private var currentBaseUrl: String? = null
    
    fun getApiService(baseUrl: String, accessToken: String? = null): JellyfinApiService {
        val normalizedUrl = baseUrl.trimEnd('/') + "/"
        val serviceKey = "$normalizedUrl|$accessToken"
        
        if (currentApiService == null || currentBaseUrl != serviceKey) {
            val clientBuilder = okHttpClient.newBuilder()
            
            // Add auth interceptor if token is provided
            if (accessToken != null) {
                clientBuilder.addInterceptor { chain ->
                    val originalRequest = chain.request()
                    val authenticatedRequest = originalRequest.newBuilder()
                        .header("Authorization", "MediaBrowser Token=$accessToken")
                        .build()
                    chain.proceed(authenticatedRequest)
                }
            }
            
            val retrofit = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(clientBuilder.build())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            
            currentApiService = retrofit.create(JellyfinApiService::class.java)
            currentBaseUrl = serviceKey
        }
        
        return currentApiService!!
    }
}

class JellyfinClientInterceptor : Interceptor {
    companion object {
        private const val CLIENT_NAME = "Jellyfin Android"
        private const val CLIENT_VERSION = "1.0.0"
        private const val DEVICE_NAME = "Android Device"
        private const val DEVICE_ID = "android-jellyfin-client"
    }
    
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()
            .header("X-Emby-Client", CLIENT_NAME)
            .header("X-Emby-Client-Version", CLIENT_VERSION)
            .header("X-Emby-Device-Name", DEVICE_NAME)
            .header("X-Emby-Device-Id", DEVICE_ID)
        
        // Add X-Emby-Authorization header for authentication endpoints
        if (originalRequest.url.encodedPath.contains("AuthenticateByName")) {
            val authHeader = "MediaBrowser Client=\"$CLIENT_NAME\", Device=\"$DEVICE_NAME\", DeviceId=\"$DEVICE_ID\", Version=\"$CLIENT_VERSION\""
            requestBuilder.header("X-Emby-Authorization", authHeader)
        }
        
        return chain.proceed(requestBuilder.build())
    }
}

class RetryInterceptor : Interceptor {
    companion object {
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
    }
    
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        var response = chain.proceed(chain.request())
        var retryCount = 0
        
        while (!response.isSuccessful && retryCount < MAX_RETRIES) {
            val responseCode = response.code
            
            // Only retry on server errors (5xx) or timeout-related issues
            if (responseCode in 500..599 || responseCode == 408) {
                response.close()
                
                try {
                    Thread.sleep(RETRY_DELAY_MS * (retryCount + 1))
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
                
                response = chain.proceed(chain.request())
                retryCount++
            } else {
                break
            }
        }
        
        return response
    }
}
