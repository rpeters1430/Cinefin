package com.rpeters.jellyfin.ui.tv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Items to be displayed in the Android TV Navigation Drawer.
 */
sealed class TvNavigationItem(
    val route: String,
    val title: String,
    val icon: ImageVector,
) {
    object Home : TvNavigationItem("tv_home", "Home", Icons.Default.Home)
    object Movies : TvNavigationItem("tv_movies", "Movies", Icons.Default.Movie)
    object TvShows : TvNavigationItem("tv_shows", "TV Shows", Icons.Default.Tv)
    object Music : TvNavigationItem("tv_music", "Music", Icons.Default.LibraryMusic)
    object Stuff : TvNavigationItem("tv_stuff", "Stuff", Icons.Default.VideoLibrary)
    object Search : TvNavigationItem("tv_search", "Search", Icons.Default.Search)
    object Favorites : TvNavigationItem("tv_favorites", "Favorites", Icons.Default.Favorite)
    object Settings : TvNavigationItem("tv_settings", "Settings", Icons.Default.Settings)

    companion object {
        val items = listOf(Home, Movies, TvShows, Music, Stuff, Search, Favorites, Settings)

        fun items(favoritesEnabled: Boolean = true): List<TvNavigationItem> =
            if (favoritesEnabled) items else items.filter { it != Favorites }
    }
}
