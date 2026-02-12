package com.rpeters.jellyfin.core

/**
 * Centralized feature flag keys for remote config.
 * All feature flags should be defined here for easy reference and type safety.
 */
object FeatureFlags {
    /**
     * AI Feature Flags (existing)
     */
    object AI {
        const val ENABLE_AI_FEATURES = "enable_ai_features"
        const val AI_FORCE_PRO_MODEL = "ai_force_pro_model"
        const val AI_PRIMARY_MODEL_NAME = "ai_primary_model_name"
        const val AI_PRO_MODEL_NAME = "ai_pro_model_name"
        const val AI_CHAT_SYSTEM_PROMPT = "ai_chat_system_prompt"
        const val AI_SUMMARY_PROMPT_TEMPLATE = "ai_summary_prompt_template"
        const val AI_SEARCH_KEYWORD_LIMIT = "ai_search_keyword_limit"
        const val AI_RECOMMENDATION_COUNT = "ai_recommendation_count"
        const val AI_HISTORY_CONTEXT_SIZE = "ai_history_context_size"
    }

    /**
     * Experimental & Utility Flags
     */
    object Experimental {
        /** Enable/disable video player gestures (tap/drag) */
        const val ENABLE_VIDEO_PLAYER_GESTURES = "enable_video_player_gestures"

        /** Enable/disable AI-based quality recommendations */
        const val ENABLE_QUALITY_RECOMMENDATIONS = "enable_quality_recommendations"

        /** Custom seek interval for video player in milliseconds */
        const val VIDEO_PLAYER_SEEK_INTERVAL_MS = "video_player_seek_interval_ms"

        /** Toggle visibility of transcoding diagnostics tool */
        const val SHOW_TRANSCODING_DIAGNOSTICS = "show_transcoding_diagnostics"

        /** Experimental player buffer size in milliseconds */
        const val EXPERIMENTAL_PLAYER_BUFFER_MS = "experimental_player_buffer_ms"
    }
}
