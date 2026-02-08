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
        // All immersive UI elements are now enabled by default
        val defaults = mapOf(
            // Immersive UI feature flags
            "enable_immersive_ui" to true,

            // Home & Main Screens
            "immersive_home_screen" to true,
            "immersive_library_screen" to true,
            "immersive_search_screen" to true,
            "immersive_favorites_screen" to true,

            // Detail Screens
            "immersive_movie_detail" to true,
            "immersive_tv_show_detail" to true,
            "immersive_tv_season" to true,
            "immersive_tv_episode_detail" to true,
            "immersive_album_detail" to true,
            "immersive_home_video_detail" to true,

            // Browse Screens
            "immersive_movies_browse" to true,
            "immersive_tv_browse" to true,
            "immersive_home_videos_browse" to true,
            "immersive_music_browse" to true,

            // AI Feature Flags
            "enable_ai_features" to true,
            "ai_force_pro_model" to false,
            "ai_primary_model_name" to "gemini-3-flash-preview",
            "ai_pro_model_name" to "gemini-3-pro-preview",
            "ai_search_keyword_limit" to 5,
            "ai_recommendation_count" to 5,
            "ai_history_context_size" to 10,
            "ai_chat_system_prompt" to "You are Jellyfin AI Assistant. Answer the user's request clearly and briefly (max 120 words). If recommending media, suggest at most 5 titles.",
            "ai_summary_prompt_template" to "Rewrite this into a fresh, spoiler-free summary in exactly 2 short sentences (max 55 words total). Do not copy phrases directly from the overview.\n\nTitle: %s\nOverview: %s\n\nFocus on the core premise only. Do not reveal twists or endings.",

            // Experimental & Utility Flags
            "enable_video_player_gestures" to true,
            "enable_quality_recommendations" to true,
            "video_player_seek_interval_ms" to 10000L,
            "show_transcoding_diagnostics" to true,
            "experimental_player_buffer_ms" to 5000L,

            // Legacy grouped flags
            "immersive_detail_screens" to true,
            "immersive_browse_screens" to true,
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
