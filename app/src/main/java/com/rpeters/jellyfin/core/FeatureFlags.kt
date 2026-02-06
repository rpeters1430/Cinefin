package com.rpeters.jellyfin.core

/**
 * Centralized feature flag keys for remote config.
 * All feature flags should be defined here for easy reference and type safety.
 */
object FeatureFlags {
    /**
     * Immersive UI Feature Flags
     */
    object ImmersiveUI {
        /** Master toggle for all immersive UI features */
        const val ENABLE_IMMERSIVE_UI = "enable_immersive_ui"

        /** Enable immersive home screen design */
        const val IMMERSIVE_HOME_SCREEN = "immersive_home_screen"

        /** Enable immersive detail screens (movie, TV, album, etc.) */
        const val IMMERSIVE_DETAIL_SCREENS = "immersive_detail_screens"

        /** Enable immersive browse screens (movies, TV shows, music) */
        const val IMMERSIVE_BROWSE_SCREENS = "immersive_browse_screens"

        /** Enable immersive search screen */
        const val IMMERSIVE_SEARCH_SCREEN = "immersive_search_screen"

        /** Enable immersive library screen */
        const val IMMERSIVE_LIBRARY_SCREEN = "immersive_library_screen"
    }

    /**
     * AI Feature Flags (existing)
     */
    object AI {
        const val ENABLE_AI_FEATURES = "enable_ai_features"
        const val AI_FORCE_PRO_MODEL = "ai_force_pro_model"
    }
}
