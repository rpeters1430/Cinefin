package com.rpeters.jellyfin.di

import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.repository.FirebaseRemoteConfigRepository
import com.rpeters.jellyfin.data.repository.RemoteConfigRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RemoteConfigModule {

    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig {
        val remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) {
                0 // Frequent fetches for development
            } else {
                3600 // 1 hour for production
            }
        }
        remoteConfig.setConfigSettingsAsync(configSettings)

        // Set default values for feature flags
        val defaults = mapOf(
            // Immersive UI feature flags
            "enable_immersive_ui" to false,
            "immersive_home_screen" to false,
            "immersive_detail_screens" to false,
            "immersive_browse_screens" to false,
            "immersive_search_screen" to false,
            "immersive_library_screen" to false,
        )
        remoteConfig.setDefaultsAsync(defaults)

        return remoteConfig
    }

    @Provides
    @Singleton
    fun provideRemoteConfigRepository(
        remoteConfig: FirebaseRemoteConfig,
    ): RemoteConfigRepository {
        return FirebaseRemoteConfigRepository(remoteConfig)
    }
}
