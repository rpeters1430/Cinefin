package com.rpeters.jellyfin.ui.accessibility

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.utils.AppResources
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Accessibility extensions and helpers for the Jellyfin Android app.
 * Provides consistent content descriptions and semantic properties.
 */

/**
 * Creates a content description for a media item based on its type and metadata.
 */
fun BaseItemDto.getAccessibilityDescription(): String {
    val itemType = when (this.type) {
        BaseItemKind.MOVIE -> "Movie"
        BaseItemKind.SERIES -> "TV Series"
        BaseItemKind.EPISODE -> "Episode"
        BaseItemKind.SEASON -> "Season"
        BaseItemKind.AUDIO -> "Music"
        BaseItemKind.MUSIC_ALBUM -> "Album"
        BaseItemKind.MUSIC_ARTIST -> "Artist"
        BaseItemKind.BOOK -> "Book"
        BaseItemKind.AUDIO_BOOK -> "Audiobook"
        BaseItemKind.VIDEO -> "Video"
        BaseItemKind.COLLECTION_FOLDER -> "Library"
        else -> "Media item"
    }

    val name = this.name ?: AppResources.getString(R.string.unknown)
    val description = StringBuilder("$itemType: $name")

    // Add production year if available
    this.productionYear?.let { year ->
        description.append(", from $year")
    }

    // Add runtime for movies/episodes
    if (this.type == BaseItemKind.MOVIE || this.type == BaseItemKind.EPISODE) {
        this.runTimeTicks?.let { ticks ->
            val minutes = (ticks / 600000000L).toInt()
            val hours = minutes / 60
            val remainingMinutes = minutes % 60

            when {
                hours > 0 -> description.append(", $hours hours and $remainingMinutes minutes")
                minutes > 0 -> description.append(", $minutes minutes")
            }
        }
    }

    // Add episode information
    if (this.type == BaseItemKind.EPISODE) {
        val seasonNumber = this.parentIndexNumber
        val episodeNumber = this.indexNumber
        if (seasonNumber != null && episodeNumber != null) {
            description.append(", Season $seasonNumber Episode $episodeNumber")
        }
    }

    // Add community rating if available
    this.communityRating?.let { rating ->
        description.append(", rated ${String.format("%.1f", rating)} stars")
    }

    // Add played/unplayed status
    val isWatched = this.userData?.played == true
    if (isWatched) {
        description.append(", watched")
    } else {
        description.append(", unwatched")
    }

    return description.toString()
}

/**
 * Creates a content description for a media item's favorite status.
 */
fun BaseItemDto.getFavoriteAccessibilityDescription(): String {
    val isFavorite = this.userData?.isFavorite == true
    val itemName = this.name ?: "item"
    return if (isFavorite) {
        "Remove $itemName from favorites"
    } else {
        "Add $itemName to favorites"
    }
}

/**
 * Creates a content description for play/pause button state.
 */
fun getPlayPauseAccessibilityDescription(isPlaying: Boolean, itemName: String?): String {
    val name = itemName ?: "media"
    return if (isPlaying) {
        "Pause $name"
    } else {
        "Play $name"
    }
}

/**
 * Creates a content description for loading states.
 */
fun getLoadingAccessibilityDescription(contentType: String): String {
    return "Loading $contentType"
}

/**
 * Modifier extension for media cards with proper accessibility semantics.
 */
fun Modifier.mediaCardSemantics(
    item: BaseItemDto,
    onClick: () -> Unit,
): Modifier = this.semantics(mergeDescendants = true) {
    contentDescription = item.getAccessibilityDescription()
    role = Role.Button

    // Add action descriptions
    val itemType = when (item.type) {
        BaseItemKind.MOVIE, BaseItemKind.EPISODE, BaseItemKind.VIDEO -> "Play"
        BaseItemKind.SERIES, BaseItemKind.SEASON -> "Browse"
        BaseItemKind.COLLECTION_FOLDER -> "Open library"
        else -> "View details"
    }
    stateDescription = "Double tap to $itemType"
}

/**
 * Modifier extension for heading text with proper semantics.
 */
fun Modifier.headingSemantics(level: Int = 1): Modifier = this.semantics {
    heading()
    role = Role.Button
}

/**
 * Modifier extension for buttons with clear action descriptions.
 */
fun Modifier.buttonSemantics(
    description: String,
    enabled: Boolean = true,
): Modifier = this.semantics {
    contentDescription = description
    role = Role.Button
    if (!enabled) {
        stateDescription = "Disabled"
    }
}

/**
 * Modifier extension for toggle buttons (like favorite, watched status).
 */
fun Modifier.toggleSemantics(
    description: String,
    isToggled: Boolean,
    enabled: Boolean = true,
): Modifier = this.semantics {
    contentDescription = description
    role = Role.Switch
    stateDescription = if (isToggled) "On" else "Off"
    if (!enabled) {
        stateDescription = "Disabled"
    }
}

/**
 * Modifier extension for search input fields.
 */
fun Modifier.searchSemantics(
    placeholder: String,
    currentQuery: String,
): Modifier = this.semantics {
    contentDescription = if (currentQuery.isEmpty()) {
        "Search field, $placeholder"
    } else {
        "Search field with query: $currentQuery"
    }
    role = Role.DropdownList
}

/**
 * Modifier extension for progress indicators.
 */
fun Modifier.progressSemantics(
    description: String,
    progress: Float? = null,
): Modifier = this.semantics {
    contentDescription = progress?.let {
        "$description, ${(it * 100).toInt()}% complete"
    } ?: description
    // Note: ProgressBar role is not available in current Compose version
    // The progress information is conveyed through contentDescription instead
}

/**
 * Modifier extension for image content with fallback descriptions.
 */
fun Modifier.imageSemantics(
    contentDescription: String?,
    isDecorative: Boolean = false,
): Modifier = if (isDecorative) {
    this.clearAndSetSemantics { }
} else {
    this.semantics {
        this.contentDescription = contentDescription ?: "Image"
    }
}

/**
 * Modifier extension for navigation elements.
 */
fun Modifier.navigationSemantics(
    label: String,
    isSelected: Boolean = false,
): Modifier = this.semantics {
    contentDescription = label
    role = Role.Tab
    if (isSelected) {
        stateDescription = "Selected"
    }
}
