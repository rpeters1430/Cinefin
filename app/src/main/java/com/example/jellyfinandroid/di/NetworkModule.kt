package com.example.jellyfinandroid.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.example.jellyfinandroid.BuildConfig
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
            }
        }
            .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideJellyfinSdk(@ApplicationContext context: Context): Jellyfin {
        return createJellyfin {
            clientInfo = ClientInfo(
                name = "Jellyfin Android",
                version = "1.0.0"
            )
            deviceInfo = DeviceInfo(
                id = "android-jellyfin-client",
                name = "Android Device"
            )
            this.context = context
        }
    }
    
    @Provides
    @Singleton
    fun provideJellyfinClientFactory(jellyfin: Jellyfin): JellyfinClientFactory {
        return JellyfinClientFactory(jellyfin)
    }
}

@Singleton
class JellyfinClientFactory @Inject constructor(
    private val jellyfin: Jellyfin
) {
    private var currentClient: org.jellyfin.sdk.api.client.ApiClient? = null
    private var currentBaseUrl: String? = null
    private var currentToken: String? = null
    
    fun getClient(baseUrl: String, accessToken: String? = null): org.jellyfin.sdk.api.client.ApiClient {
        val normalizedUrl = baseUrl.trimEnd('/') + "/"
        
        if (currentToken != accessToken || currentBaseUrl != normalizedUrl || currentClient == null) {
            currentClient = jellyfin.createApi(
                baseUrl = normalizedUrl,
                accessToken = accessToken
            )
            currentBaseUrl = normalizedUrl
            currentToken = accessToken
        }
        
        return currentClient ?: throw IllegalStateException("Failed to create Jellyfin API client for URL: $normalizedUrl")
    }
    
    fun invalidateClient() {
        currentClient = null
        currentBaseUrl = null
        currentToken = null
    }
}
