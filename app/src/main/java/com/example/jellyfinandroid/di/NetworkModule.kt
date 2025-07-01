package com.example.jellyfinandroid.di

import com.example.jellyfinandroid.network.JellyfinApiService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
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
    
    fun getApiService(baseUrl: String): JellyfinApiService {
        val normalizedUrl = baseUrl.trimEnd('/') + "/"
        
        if (currentApiService == null || currentBaseUrl != normalizedUrl) {
            val retrofit = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            
            currentApiService = retrofit.create(JellyfinApiService::class.java)
            currentBaseUrl = normalizedUrl
        }
        
        return currentApiService!!
    }
}
