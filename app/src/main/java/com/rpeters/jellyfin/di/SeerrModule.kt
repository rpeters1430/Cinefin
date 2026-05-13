package com.rpeters.jellyfin.di

import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.preferences.SeerrPreferencesRepository
import com.rpeters.jellyfin.data.repository.SeerrRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SeerrModule {

    @Provides
    @Singleton
    @Named("SeerrHttpClient")
    fun provideSeerrHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideSeerrRepository(
        @Named("SeerrHttpClient") okHttpClient: OkHttpClient,
        preferencesRepository: SeerrPreferencesRepository
    ): SeerrRepository {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        return SeerrRepository(okHttpClient, preferencesRepository, json)
    }

    @Provides
    @Singleton
    fun provideCinefinPluginRepository(
        okHttpClient: OkHttpClient,
        authRepository: com.rpeters.jellyfin.data.repository.IJellyfinAuthRepository
    ): com.rpeters.jellyfin.data.repository.CinefinPluginRepository {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        return com.rpeters.jellyfin.data.repository.CinefinPluginRepository(okHttpClient, authRepository, json)
    }
}
