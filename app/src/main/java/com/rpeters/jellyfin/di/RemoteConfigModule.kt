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
            // AI Feature Flags
            "enable_ai_features" to true,
            "ai_force_pro_model" to false,
            "ai_primary_model_name" to "gemini-3-flash-preview",
            "ai_pro_model_name" to "gemini-3-pro-preview",
            "ai_primary_model_temperature" to 0.7,
            "ai_pro_model_temperature" to 0.8,
            "ai_primary_model_max_tokens" to 2048L,
            "ai_pro_model_max_tokens" to 4096L,
            "ai_search_keyword_limit" to 5L,
            "ai_recommendation_count" to 5L,
            "ai_history_context_size" to 10L,
            "ai_summaries" to true,
            "ai_person_bio" to true,
            "ai_person_bio_context_size" to 15L,
            "ai_thematic_analysis" to true,
            "ai_theme_extraction_limit" to 5L,
            "ai_why_youll_love_this" to true,
            "ai_mood_collections" to true,
            "ai_mood_collections_count" to 3L,
            "ai_mood_collection_size" to 10L,
            "ai_smart_recommendations" to true,
            "ai_smart_recommendations_limit" to 10L,
            "ai_chat_system_prompt" to "You are Jellyfin AI Assistant. Answer the user's request clearly and briefly (max 120 words). If recommending media, suggest at most 5 titles.",
            "ai_summary_prompt_template" to "Rewrite this into a fresh, spoiler-free summary in exactly 2 short sentences (max 55 words total). Do not copy phrases directly from the overview.\n\nTitle: %s\nOverview: %s\n\nFocus on the core premise only. Do not reveal twists or endings.",
            "ai_mood_analysis_prompt_template" to "Based on the following list of recently watched media, describe the user's current viewing mood in one short sentence (e.g., \"You're into sci-fi adventures right now!\" or \"Looks like a comedy weekend.\").\n\nMedia List:\n%s",
            "ai_person_bio_prompt_template" to "Generate a concise 2-3 sentence biography for %s based on their filmography in this library.\n\nContext:\n- Total in library: %d movies, %d TV shows\nNotable movies: %s\nNotable TV shows: %s\n\nFocus on:\n1. Career highlights and known roles (based on titles)\n2. Acting style or typical genres\n3. Presence in this library (%d total appearances)\n\nKeep it engaging and informative. Max 60 words.",
            "ai_theme_extraction_prompt_template" to "Extract %d thematic elements from this content. Focus on deeper themes beyond just genre.\n\nTitle: %s\nGenres: %s\nOverview: %s\n\nExamples of themes: \"redemption\", \"found family\", \"coming of age\", \"moral ambiguity\", \"survival\", \"betrayal\", \"identity crisis\", \"power corruption\", \"revenge\", \"sacrifice\", \"forbidden love\", \"class struggle\"\n\nReturn ONLY a JSON array of theme strings, lowercase, 1-3 words each.\nExample: [\"redemption\", \"found family\", \"survival\"]",
            "ai_why_love_this_prompt_template" to "Based on the user's viewing history, explain why they would enjoy \"%s\" in ONE compelling sentence (max 40 words).\n\nTheir recent watches: %s\n\nAbout \"%s\":\n- Genres: %s\n- Overview: %s\n\nFind connections to their history (similar themes, genres, actors, tone, storytelling style).\n\nFormat: \"You loved [Title] and [Title] - this has the same [specific quality].\"\n\nBe specific and engaging. Focus on WHY, not just WHAT.",
            "ai_mood_collections_prompt_template" to "Based on this library and the current time (%s), create %d mood-based collections.\n\nLibrary sample (first 100 items):\n%s\n\nCreate collections with:\n1. Engaging collection names (e.g., \"Feel-Good Comedies\", \"Mind-Bending Thrillers\", \"Cozy Comfort Shows\")\n2. %d items per collection\n3. Time-appropriate suggestions\n\nReturn as JSON:\n{\n  \"Collection Name 1\": [\"Title 1\", \"Title 2\", ...],\n  \"Collection Name 2\": [\"Title 1\", \"Title 2\", ...],\n  ...\n}\n\nOnly include titles that exist in the library above.",
            "ai_smart_recommendations_prompt_template" to "You are a smart content recommendation engine. Find %d titles from the library that the user would enjoy based on thematic similarities, mood, and tone.\n\nCurrent item: %s\nGenres: %s\nOverview: %s\n\nUser's recent viewing history: %s\n\nAvailable library (sample):\n%s\n\nFind items that match:\n1. Thematic elements (e.g., similar character arcs, moral dilemmas, storytelling style)\n2. Mood and tone (not just genre)\n3. User's demonstrated preferences from history\n\nDO NOT recommend the current item itself.\n\nReturn ONLY a JSON array of title strings, in priority order:\n[\"Title 1\", \"Title 2\", \"Title 3\", ...]\n\nInclude ONLY titles that exist in the library above.",

            // Experimental & Utility Flags
            "enable_video_player_gestures" to true,
            "enable_quality_recommendations" to true,
            "video_player_seek_interval_ms" to 10000L,
            "show_transcoding_diagnostics" to true,
            "experimental_player_buffer_ms" to 5000L,
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
