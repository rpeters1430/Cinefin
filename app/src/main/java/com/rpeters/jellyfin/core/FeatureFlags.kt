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

        // ===== Home & Main Screens =====
        /** Enable immersive home screen design */
        const val IMMERSIVE_HOME_SCREEN = "immersive_home_screen"

        /** Enable immersive library screen */
        const val IMMERSIVE_LIBRARY_SCREEN = "immersive_library_screen"

        /** Enable immersive search screen */
        const val IMMERSIVE_SEARCH_SCREEN = "immersive_search_screen"

        /** Enable immersive favorites screen */
        const val IMMERSIVE_FAVORITES_SCREEN = "immersive_favorites_screen"

        // ===== Detail Screens (Granular Control) =====
        /** Enable immersive movie detail screen */
        const val IMMERSIVE_MOVIE_DETAIL = "immersive_movie_detail"

        /** Enable immersive TV show detail screen */
        const val IMMERSIVE_TV_SHOW_DETAIL = "immersive_tv_show_detail"

        /** Enable immersive TV season screen */
        const val IMMERSIVE_TV_SEASON = "immersive_tv_season"

        /** Enable immersive TV episode detail screen */
        const val IMMERSIVE_TV_EPISODE_DETAIL = "immersive_tv_episode_detail"

        /** Enable immersive album detail screen */
        const val IMMERSIVE_ALBUM_DETAIL = "immersive_album_detail"

        // ===== Browse Screens =====
        /** Enable immersive movies browse screen */
        const val IMMERSIVE_MOVIES_BROWSE = "immersive_movies_browse"

        /** Enable immersive TV shows browse screen */
        const val IMMERSIVE_TV_BROWSE = "immersive_tv_browse"

        /** Enable immersive music browse screen */
        const val IMMERSIVE_MUSIC_BROWSE = "immersive_music_browse"

        // ===== Legacy Grouped Flags (Deprecated - use specific flags above) =====
        /** @deprecated Use specific detail screen flags instead */
        @Deprecated("Use IMMERSIVE_MOVIE_DETAIL, IMMERSIVE_TV_SHOW_DETAIL, etc.")
        const val IMMERSIVE_DETAIL_SCREENS = "immersive_detail_screens"

        /** @deprecated Use specific browse screen flags instead */
        @Deprecated("Use IMMERSIVE_MOVIES_BROWSE, IMMERSIVE_TV_BROWSE, etc.")
        const val IMMERSIVE_BROWSE_SCREENS = "immersive_browse_screens"
    }

    /**
     * AI Feature Flags (existing)
     */
    object AI {
        const val ENABLE_AI_FEATURES = "enable_ai_features"
        const val AI_FORCE_PRO_MODEL = "ai_force_pro_model"
    }
}
