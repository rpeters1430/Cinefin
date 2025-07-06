package com.example.jellyfinandroid.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
    val showInNavigation: Boolean = true
) {
    CONNECT("Connect", Icons.Default.Home, false), // Hidden from navigation
    HOME("Home", Icons.Default.Home),
    LIBRARY("Library", Icons.AutoMirrored.Filled.List),
    MOVIES("Movies", Icons.Default.Movie, false), // Hidden from bottom nav
    TV_SHOWS("TV Shows", Icons.Default.Tv, false), // Hidden from bottom nav
    MUSIC("Music", Icons.Default.MusicNote, false), // Hidden from bottom nav
    STUFF("Stuff", Icons.Default.Widgets, false), // Hidden from bottom nav
    SEARCH("Search", Icons.Default.Search),
    FAVORITES("Favorites", Icons.Default.Favorite),
    PROFILE("Profile", Icons.Default.AccountBox),
} 