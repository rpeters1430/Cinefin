package com.example.jellyfinandroid.utils

import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * ✅ PHASE 3: Enhanced extension functions with centralized constants
 */

/**
 * Safely converts community rating to Double with fallback to 0.0
 * Eliminates code duplication across multiple screen files
 */
fun BaseItemDto.getRatingAsDouble(): Double =
    (communityRating as? Number)?.toDouble() ?: 0.0

/**
 * ✅ PHASE 3: Enhanced rating checks using centralized constants
 */
fun BaseItemDto.hasHighRating(): Boolean =
    getRatingAsDouble() >= AppConstants.Rating.HIGH_RATING_THRESHOLD

fun BaseItemDto.hasExcellentRating(): Boolean =
    getRatingAsDouble() >= AppConstants.Rating.EXCELLENT_RATING_THRESHOLD

fun BaseItemDto.hasGoodRating(): Boolean =
    getRatingAsDouble() >= AppConstants.Rating.GOOD_RATING_THRESHOLD

fun BaseItemDto.getRatingCategory(): RatingCategory {
    val rating = getRatingAsDouble()
    return when {
        rating >= AppConstants.Rating.EXCELLENT_RATING_THRESHOLD -> RatingCategory.EXCELLENT
        rating >= AppConstants.Rating.HIGH_RATING_THRESHOLD -> RatingCategory.HIGH
        rating >= AppConstants.Rating.GOOD_RATING_THRESHOLD -> RatingCategory.GOOD
        rating >= AppConstants.Rating.AVERAGE_RATING_THRESHOLD -> RatingCategory.AVERAGE
        else -> RatingCategory.POOR
    }
}

/**
 * ✅ PHASE 3: Rating categories for better UX
 */
enum class RatingCategory(val displayName: String, val color: String) {
    EXCELLENT("Excellent", "#4CAF50"),
    HIGH("High", "#8BC34A"),
    GOOD("Good", "#FFC107"),
    AVERAGE("Average", "#FF9800"),
    POOR("Poor", "#F44336"),
}

/**
 * ✅ PHASE 3: Enhanced media type extensions
 */
fun BaseItemDto.isMovie(): Boolean = type == org.jellyfin.sdk.model.api.BaseItemKind.MOVIE
fun BaseItemDto.isSeries(): Boolean = type == org.jellyfin.sdk.model.api.BaseItemKind.SERIES
fun BaseItemDto.isEpisode(): Boolean = type == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE
fun BaseItemDto.isMusic(): Boolean = type == org.jellyfin.sdk.model.api.BaseItemKind.AUDIO
fun BaseItemDto.isPhoto(): Boolean = type == org.jellyfin.sdk.model.api.BaseItemKind.PHOTO

/**
 * ✅ PHASE 3: Enhanced display utilities
 */
fun BaseItemDto.getDisplayTitle(): String = name ?: "Unknown Title"

fun BaseItemDto.getYear(): Int? = (productionYear as? Number)?.toInt()

fun BaseItemDto.getFormattedDuration(): String? {
    val ticks = runTimeTicks ?: return null
    val totalSeconds = ticks / 10_000_000 // Convert from ticks to seconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }
}

/**
 * ✅ PHASE 3: Watch status utilities
 */
fun BaseItemDto.isWatched(): Boolean = userData?.played == true

fun BaseItemDto.isPartiallyWatched(): Boolean {
    val percentage = userData?.playedPercentage ?: 0.0
    return percentage > 0.0 && percentage < AppConstants.Playback.WATCHED_THRESHOLD_PERCENT
}

fun BaseItemDto.canResume(): Boolean {
    val percentage = userData?.playedPercentage ?: 0.0
    return percentage > AppConstants.Playback.RESUME_THRESHOLD_PERCENT &&
        percentage < AppConstants.Playback.WATCHED_THRESHOLD_PERCENT
}

fun BaseItemDto.getWatchedPercentage(): Double = userData?.playedPercentage ?: 0.0

/**
 * ✅ PHASE 3: Enhanced key generation for lists
 */
fun BaseItemDto.getItemKey(): String =
    generateItemKey(type?.toString() ?: "unknown", id?.toString() ?: "")

/**
 * ✅ PHASE 3: Content key generation utility
 */
fun generateItemKey(prefix: String, id: String): String = "${prefix}_$id"
