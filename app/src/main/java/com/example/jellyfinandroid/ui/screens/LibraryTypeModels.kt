package com.example.jellyfinandroid.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.jellyfinandroid.ui.theme.BookPurple
import com.example.jellyfinandroid.ui.theme.MovieRed
import com.example.jellyfinandroid.ui.theme.MusicGreen
import com.example.jellyfinandroid.ui.theme.SeriesBlue
import com.example.jellyfinandroid.utils.getRatingAsDouble
import com.example.jellyfinandroid.utils.hasHighRating
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Shared constants for library screens.
 */
object LibraryScreenDefaults {
    // Layout constants
    val GridMinItemSize = 160.dp
    val CardElevation = 4.dp
    val CardCornerRadius = 12.dp
    val ContentPadding = 16.dp
    val ItemSpacing = 12.dp
    val SectionSpacing = 24.dp
    val FilterChipSpacing = 8.dp

    // Carousel constants
    const val CarouselItemsPerSection = 10
    val CarouselHeight = 280.dp
    val CarouselPreferredItemWidth = 200.dp
    val CarouselItemSpacing = 8.dp

    // Card dimensions
    val CompactCardImageHeight = 240.dp
    val CompactCardWidth = 180.dp
    val CompactCardPadding = 12.dp
    val ListCardImageWidth = 100.dp
    val ListCardImageHeight = 140.dp
    val ListCardImageRadius = 8.dp
    val ListCardPadding = 12.dp

    // Icon sizes
    val ViewModeIconSize = 16.dp
    val FilterIconSize = 16.dp
    val CardActionIconSize = 48.dp
    val ListCardIconSize = 32.dp
    val EmptyStateIconSize = 64.dp

    // Other constants
    const val TicksToMinutesDivisor = 600000000L
    val FavoriteIconPadding = 8.dp
    val ListItemFavoriteIconPadding = 4.dp

    // Alpha values
    const val ColorAlpha = 0.2f
    const val IconAlpha = 0.6f
}

/** Simple container for carousel sections. */
data class CarouselCategory(
    val title: String,
    val items: List<BaseItemDto>,
)

/** Available library categories. */
enum class LibraryType(
    val displayName: String,
    val icon: ImageVector,
    val color: Color,
    val itemKinds: List<BaseItemKind>,
) {
    MOVIES(
        displayName = "Movies",
        icon = Icons.Default.Movie,
        color = MovieRed,
        itemKinds = listOf(BaseItemKind.MOVIE),
    ),
    TV_SHOWS(
        displayName = "TV Shows",
        icon = Icons.Default.Tv,
        color = SeriesBlue,
        itemKinds = listOf(BaseItemKind.SERIES, BaseItemKind.EPISODE),
    ),
    MUSIC(
        displayName = "Music",
        icon = Icons.Default.MusicNote,
        color = MusicGreen,
        itemKinds = listOf(BaseItemKind.AUDIO, BaseItemKind.MUSIC_ALBUM, BaseItemKind.MUSIC_ARTIST),
    ),
    STUFF(
        displayName = "Stuff",
        icon = Icons.Default.Widgets,
        color = BookPurple,
        itemKinds = listOf(BaseItemKind.BOOK, BaseItemKind.AUDIO_BOOK, BaseItemKind.VIDEO, BaseItemKind.PHOTO),
    ),
}

/** Different ways the library can be displayed. */
enum class ViewMode { GRID, LIST, CAROUSEL }

/**
 * Organize items into sections used by the carousel view.
 */
fun organizeItemsForCarousel(items: List<BaseItemDto>, libraryType: LibraryType): List<CarouselCategory> {
    if (items.isEmpty()) return emptyList()

    val categories = mutableListOf<CarouselCategory>()

    val recentItems = items.sortedByDescending { it.dateCreated }.take(10)
    if (recentItems.isNotEmpty()) {
        categories.add(CarouselCategory("Recently Added", recentItems))
    }

    val favoriteItems = items.filter { it.userData?.isFavorite == true }
        .take(LibraryScreenDefaults.CarouselItemsPerSection)
    if (favoriteItems.isNotEmpty()) {
        categories.add(CarouselCategory("Favorites", favoriteItems))
    }

    val highRatedItems = items
        .filter { it.hasHighRating() }
        .sortedByDescending { it.getRatingAsDouble() }
        .take(LibraryScreenDefaults.CarouselItemsPerSection)
    if (highRatedItems.isNotEmpty()) {
        categories.add(CarouselCategory("Highly Rated", highRatedItems))
    }

    when (libraryType) {
        LibraryType.MOVIES -> {
            val recentReleases = items
                .filter { ((it.productionYear as? Number)?.toInt() ?: 0) >= 2020 }
                .sortedByDescending { (it.productionYear as? Number)?.toInt() ?: 0 }
                .take(LibraryScreenDefaults.CarouselItemsPerSection)
            if (recentReleases.isNotEmpty()) {
                categories.add(CarouselCategory("Recent Releases", recentReleases))
            }
        }
        LibraryType.TV_SHOWS -> {
            val continuingSeries = items
                .filter { it.type == BaseItemKind.SERIES && it.status == "Continuing" }
                .take(LibraryScreenDefaults.CarouselItemsPerSection)
            if (continuingSeries.isNotEmpty()) {
                categories.add(CarouselCategory("Continuing Series", continuingSeries))
            }
        }
        LibraryType.MUSIC -> {
            val albumsByArtist = items
                .filter { it.type == BaseItemKind.MUSIC_ALBUM }
                .groupBy { it.albumArtist }
                .values.firstOrNull()
                ?.take(LibraryScreenDefaults.CarouselItemsPerSection)
            if (!albumsByArtist.isNullOrEmpty()) {
                categories.add(CarouselCategory("Popular Artist Albums", albumsByArtist))
            }
        }
        LibraryType.STUFF -> {
            val books = items.filter { it.type == BaseItemKind.BOOK }.take(8)
            if (books.isNotEmpty()) {
                categories.add(CarouselCategory("Books", books))
            }
            val audioBooks = items.filter { it.type == BaseItemKind.AUDIO_BOOK }.take(8)
            if (audioBooks.isNotEmpty()) {
                categories.add(CarouselCategory("Audiobooks", audioBooks))
            }
        }
    }

    val usedItems = categories.flatMap { it.items }.toSet()
    val remainingItems = items.filterNot { it in usedItems }

    if (remainingItems.isNotEmpty()) {
        remainingItems.chunked(LibraryScreenDefaults.CarouselItemsPerSection).forEachIndexed { index, chunk ->
            val title = if (categories.isEmpty() && index == 0) {
                "All ${libraryType.displayName}"
            } else {
                "More ${libraryType.displayName}"
            }
            categories.add(CarouselCategory(title, chunk))
        }
    }

    return categories
}
