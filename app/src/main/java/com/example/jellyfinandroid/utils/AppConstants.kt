package com.example.jellyfinandroid.utils

/**
 * ✅ PHASE 3: Centralized Application Constants
 * Replaces magic numbers and strings throughout the codebase
 */

object AppConstants {
    
    // ✅ PHASE 3: UI Constants
    object UI {
        const val CAROUSEL_ITEM_WIDTH = 320
        const val MOVIE_CARD_WIDTH = 160
        const val MOVIE_CARD_HEIGHT = 240
        const val TV_CARD_WIDTH = 160
        const val TV_CARD_HEIGHT = 240
        const val LIST_ITEM_HEIGHT = 72
        const val TOOLBAR_HEIGHT = 56
        
        // Animation durations
        const val ANIMATION_DURATION_SHORT = 150
        const val ANIMATION_DURATION_MEDIUM = 300
        const val ANIMATION_DURATION_LONG = 500
        
        // Spacing
        const val SPACING_SMALL = 4
        const val SPACING_MEDIUM = 8
        const val SPACING_LARGE = 16
        const val SPACING_EXTRA_LARGE = 24
        
        // Corners
        const val CORNER_RADIUS_SMALL = 4
        const val CORNER_RADIUS_MEDIUM = 8
        const val CORNER_RADIUS_LARGE = 12
        const val CORNER_RADIUS_EXTRA_LARGE = 16
    }
    
    // ✅ PHASE 3: Content Rating Thresholds
    object Rating {
        const val HIGH_RATING_THRESHOLD = 7.0
        const val EXCELLENT_RATING_THRESHOLD = 8.5
        const val GOOD_RATING_THRESHOLD = 6.0
        const val AVERAGE_RATING_THRESHOLD = 5.0
        const val MAX_RATING = 10.0
    }
    
    // ✅ PHASE 3: API and Network Constants
    object Network {
        const val DEFAULT_TIMEOUT = 30_000L
        const val QUICK_TIMEOUT = 10_000L
        const val LONG_TIMEOUT = 60_000L
        const val RETRY_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 1000L
        const val MAX_RETRY_DELAY_MS = 10_000L
        const val RETRY_MULTIPLIER = 2.0
    }
    
    // ✅ PHASE 3: Content Loading Constants
    object Content {
        const val DEFAULT_PAGE_SIZE = 20
        const val SMALL_PAGE_SIZE = 10
        const val LARGE_PAGE_SIZE = 50
        const val CAROUSEL_ITEMS_PER_SECTION = 12
        const val GRID_PREFETCH_BUFFER = 3
        const val LIST_PREFETCH_BUFFER = 5
        const val INFINITE_SCROLL_BUFFER = 3
    }
    
    // ✅ PHASE 3: Cache Constants
    object Cache {
        const val IMAGE_CACHE_SIZE_MB = 50
        const val MEMORY_CACHE_PERCENT = 0.25f // 25% of app memory
        const val DISK_CACHE_SIZE_MB = 100
        const val MAX_CACHED_IMAGES = 200
        const val CACHE_EXPIRY_HOURS = 24
    }
    
    // ✅ PHASE 3: Security Constants
    object Security {
        const val KEY_ALIAS = "JellyfinCredentialKey"
        const val ENCRYPTION_TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val KEY_SIZE = 256
        const val GCM_TAG_LENGTH = 128
    }
    
    // ✅ PHASE 3: QuickConnect Constants
    object QuickConnect {
        const val CODE_LENGTH = 6
        const val SECRET_LENGTH = 32
        const val MAX_POLL_ATTEMPTS = 60 // 5 minutes at 5-second intervals
        const val POLL_INTERVAL_MS = 5000L
        const val CODE_EXPIRY_MINUTES = 10
        const val CODE_CHARACTERS = "0123456789"
    }
    
    // ✅ PHASE 3: Media Type Constants
    object MediaTypes {
        const val MOVIE = "Movie"
        const val SERIES = "Series"
        const val EPISODE = "Episode"
        const val MUSIC_ALBUM = "MusicAlbum"
        const val AUDIO = "Audio"
        const val PHOTO = "Photo"
        const val COLLECTION_FOLDER = "CollectionFolder"
    }
    
    // ✅ PHASE 3: Image Quality Constants
    object ImageQuality {
        const val HIGH_QUALITY = 95
        const val MEDIUM_QUALITY = 85
        const val LOW_QUALITY = 70
        const val THUMBNAIL_QUALITY = 60
        
        // Image sizes
        const val POSTER_WIDTH = 400
        const val POSTER_HEIGHT = 600
        const val BACKDROP_WIDTH = 1920
        const val BACKDROP_HEIGHT = 1080
        const val THUMBNAIL_WIDTH = 200
        const val THUMBNAIL_HEIGHT = 300
    }
    
    // ✅ PHASE 3: Search Constants
    object Search {
        const val MIN_SEARCH_LENGTH = 2
        const val SEARCH_DEBOUNCE_MS = 300L
        const val MAX_SEARCH_RESULTS = 50
        const val SEARCH_HISTORY_LIMIT = 10
    }
    
    // ✅ PHASE 3: Playback Constants
    object Playback {
        const val SEEK_INCREMENT_MS = 10_000L // 10 seconds
        const val SEEK_INCREMENT_LONG_MS = 30_000L // 30 seconds
        const val RESUME_THRESHOLD_PERCENT = 5.0 // Resume if < 5% watched
        const val WATCHED_THRESHOLD_PERCENT = 90.0 // Mark watched if > 90% watched
    }
    
    // ✅ PHASE 3: Error Messages
    object ErrorMessages {
        const val NETWORK_ERROR = "Cannot connect to server. Please check your internet connection."
        const val SERVER_ERROR = "Server error occurred. Please try again later."
        const val AUTH_ERROR = "Authentication failed. Please check your credentials."
        const val NOT_FOUND_ERROR = "Content not found."
        const val TIMEOUT_ERROR = "Request timed out. Please try again."
        const val UNKNOWN_ERROR = "An unexpected error occurred."
        const val NO_CONTENT_ERROR = "No content available."
    }
    
    // ✅ PHASE 3: Validation Constants
    object Validation {
        const val MIN_PASSWORD_LENGTH = 6
        const val MAX_USERNAME_LENGTH = 50
        const val MAX_SERVER_NAME_LENGTH = 100
        const val URL_REGEX = "^https?://[\\w\\-]+(\\.[\\w\\-]+)+([\\w\\-\\.,@?^=%&:/~\\+#]*[\\w\\-\\@?^=%&/~\\+#])?\$"
    }
    
    // ✅ PHASE 3: Feature Flags
    object Features {
        const val ENABLE_OFFLINE_MODE = true
        const val ENABLE_EXPERIMENTAL_UI = false
        const val ENABLE_ANALYTICS = false
        const val ENABLE_CRASH_REPORTING = true
        const val ENABLE_BACKGROUND_SYNC = true
    }
    
    // ✅ PHASE 3: App Metadata
    object App {
        const val NAME = "Jellyfin Android"
        const val PACKAGE_NAME = "com.example.jellyfinandroid"
        const val MIN_SERVER_VERSION = "10.8.0"
        const val SUPPORTED_API_VERSION = "1.0.0"
        const val USER_AGENT = "Jellyfin Android Client"
    }
}
