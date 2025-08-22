package com.rpeters.jellyfin.utils

/**
 * ✅ PHASE 3: Centralized Application Constants
 * Replaces magic numbers and strings throughout the codebase
 */

object AppConstants {

    // ✅ PHASE 3: Content Rating Thresholds
    object Rating {
        const val HIGH_RATING_THRESHOLD = 7.0
        const val EXCELLENT_RATING_THRESHOLD = 8.5
        const val GOOD_RATING_THRESHOLD = 6.0
        const val AVERAGE_RATING_THRESHOLD = 5.0
    }

    // ✅ PHASE 3: Security Constants
    object Security {
        const val KEY_ALIAS = "JellyfinCredentialKey"
        const val ENCRYPTION_TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }

    // ✅ PHASE 3: Search Constants
    object Search {
        const val SEARCH_DEBOUNCE_MS = 300L
    }

    // ✅ PHASE 3: Playback Constants
    object Playback {
        const val RESUME_THRESHOLD_PERCENT = 5.0 // Resume if < 5% watched
        const val WATCHED_THRESHOLD_PERCENT = 90.0 // Mark watched if > 90% watched
    }
}
