package com.rpeters.jellyfin.di

import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.preferences.ArrPreferencesRepository
import com.rpeters.jellyfin.data.repository.RadarrRepository
import com.rpeters.jellyfin.data.repository.SonarrRepository
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
object ArrModule {

    @Provides
    @Singleton
    @Named("ArrHttpClient")
    fun provideArrHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideSonarrRepository(
        @Named("ArrHttpClient") okHttpClient: OkHttpClient,
        preferencesRepository: ArrPreferencesRepository,
    ): SonarrRepository {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        return SonarrRepository(okHttpClient, preferencesRepository, json)
    }

    @Provides
    @Singleton
    fun provideRadarrRepository(
        @Named("ArrHttpClient") okHttpClient: OkHttpClient,
        preferencesRepository: ArrPreferencesRepository,
    ): RadarrRepository {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        return RadarrRepository(okHttpClient, preferencesRepository, json)
    }
}
