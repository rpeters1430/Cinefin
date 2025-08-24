package com.rpeters.jellyfin.data.models

import com.rpeters.jellyfin.R

/**
 * Filter options for movies
 */
enum class MovieFilter(val displayNameResId: Int) {
    ALL(R.string.filter_all_movies),
    FAVORITES(R.string.filter_favorites),
    UNWATCHED(R.string.filter_unwatched),
    WATCHED(R.string.filter_recent);

    companion object {
        fun getAllFilters() = entries
    }
}

/**
 * Sort order options for movies
 */
enum class MovieSortOrder(val displayNameResId: Int) {
    NAME(R.string.sort_title_asc),
    YEAR(R.string.sort_year_desc),
    RATING(R.string.sort_rating_desc),
    RECENTLY_ADDED(R.string.sort_date_added_desc),
    RUNTIME(R.string.sort_runtime_desc);
}

/**
 * View mode options for movies
 */
enum class MovieViewMode {
    GRID,
    LIST
}

/**
 * Library type definitions
 */
enum class LibraryType {
    MOVIES,
    SHOWS,
    MUSIC,
    BOOKS,
    AUDIOBOOKS,
    PHOTOS,
    MIXED
}