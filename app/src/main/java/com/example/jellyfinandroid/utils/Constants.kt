package com.example.jellyfinandroid.utils

/**
 * Application-wide constants
 */
object Constants {

    // Network timeouts
    const val NETWORK_TIMEOUT_SECONDS = 30L
    const val NETWORK_READ_TIMEOUT_SECONDS = 60L
    const val NETWORK_WRITE_TIMEOUT_SECONDS = 30L

    // Authentication
    const val TOKEN_VALIDITY_DURATION_MS = 50 * 60 * 1000L // 50 minutes
    const val TOKEN_REFRESH_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes before expiry

    // Pagination
    const val DEFAULT_PAGE_SIZE = 20
    const val LARGE_PAGE_SIZE = 50
    const val SEARCH_PAGE_SIZE = 30
    const val PREFETCH_BUFFER_SIZE = 3

    // Image loading
    const val IMAGE_CACHE_SIZE_MB = 50
    const val MAX_IMAGE_WIDTH = 800
    const val MAX_IMAGE_HEIGHT = 1200
    const val IMAGE_QUALITY_HIGH = 85
    const val IMAGE_QUALITY_MEDIUM = 75
    const val IMAGE_QUALITY_LOW = 60

    // Animation durations
    const val ANIMATION_DURATION_MS = 300L
    const val FAST_ANIMATION_MS = 150L
    const val SLOW_ANIMATION_MS = 500L

    // Debounce delays
    const val SEARCH_DEBOUNCE_MS = 300L
    const val INPUT_DEBOUNCE_MS = 500L

    // HTTP status codes
    const val HTTP_UNAUTHORIZED = 401
    const val HTTP_FORBIDDEN = 403
    const val HTTP_NOT_FOUND = 404
    const val HTTP_SERVER_ERROR = 500

    // Player settings
    const val PLAYER_CONTROLS_TIMEOUT_MS = 3000L
    const val PLAYER_SEEK_INCREMENT_MS = 10_000L
    const val PLAYER_REWIND_INCREMENT_MS = 10_000L

    // Cache settings
    const val DISK_CACHE_SIZE_MB = 100
    const val MEMORY_CACHE_PERCENT = 0.25f // 25% of available memory
}
