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
        // DEBUG MODE: Enable immersive UI by default in debug builds for easy testing
        // In production (BuildConfig.DEBUG = false), these will default to false until
        // enabled remotely via Firebase Console
        val enableImmersiveUIDebug = BuildConfig.DEBUG // Set to true to test immersive UI locally

        val defaults = mapOf(
            // Immersive UI feature flags
            // Master toggle
            "enable_immersive_ui" to enableImmersiveUIDebug,

            // Home & Main Screens
            "immersive_home_screen" to enableImmersiveUIDebug, // ✅ Implemented
            "immersive_library_screen" to enableImmersiveUIDebug, // ✅ Implemented
            "immersive_search_screen" to enableImmersiveUIDebug, // ✅ Implemented
            "immersive_favorites_screen" to enableImmersiveUIDebug, // ✅ Implemented

            // Detail Screens (Granular Control)
            "immersive_movie_detail" to enableImmersiveUIDebug, // ✅ Implemented
            "immersive_tv_show_detail" to enableImmersiveUIDebug, // ✅ Implemented
            "immersive_tv_season" to enableImmersiveUIDebug, // ✅ Implemented
            "immersive_tv_episode_detail" to enableImmersiveUIDebug, // ✅ Implemented
            "immersive_album_detail" to false, // ⏳ Not implemented yet

            // Browse Screens
            "immersive_movies_browse" to enableImmersiveUIDebug, // ✅ Implemented
            "immersive_tv_browse" to enableImmersiveUIDebug, // ✅ Implemented
            "immersive_music_browse" to false, // ⏳ Not implemented yet

            // Legacy grouped flags (deprecated but kept for backwards compatibility)
            "immersive_detail_screens" to false,
            "immersive_browse_screens" to false,
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
