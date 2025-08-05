package com.example.jellyfinandroid.ui.navigation

/**
 * Sealed class representing all possible screens in the app.
 * Each screen is represented as an object that extends this sealed class.
 */
sealed class Screen(val route: String) {
    // Authentication flow
    object ServerConnection : Screen("server_connection")
    object QuickConnect : Screen("quick_connect")

    // Parent route for main application screens
    object Main : Screen("main")

    // Main app flow
    object Home : Screen("home")
    object Library : Screen("library")
    object Movies : Screen("movies")
    object TVShows : Screen("tv_shows")
    object TVSeasons : Screen("tv_seasons/{seriesId}") {
        fun createRoute(seriesId: String) = "tv_seasons/$seriesId"
    }
    object TVEpisodes : Screen("tv_episodes/{seasonId}") {
        fun createRoute(seasonId: String) = "tv_episodes/$seasonId"
    }
    object Music : Screen("music")
    object Search : Screen("search")
    object Favorites : Screen("favorites")
    object Profile : Screen("profile")
    object MovieDetail : Screen("movie_detail/{movieId}") {
        fun createRoute(movieId: String) = "movie_detail/$movieId"
    }
    object TVEpisodeDetail : Screen("episode_detail/{episodeId}") {
        fun createRoute(episodeId: String) = "episode_detail/$episodeId"
    }

    // For navigation arguments
    companion object {
        const val SERIES_ID_ARG = "seriesId"
        const val SEASON_ID_ARG = "seasonId"
        const val MOVIE_ID_ARG = "movieId"
        const val EPISODE_ID_ARG = "episodeId"
    }
}

/**
 * Sealed class representing navigation destinations that should be shown in the bottom navigation.
 */
sealed class BottomNavItem(val route: String, val title: String, val icon: AppDestinations) {
    object Home : BottomNavItem(
        route = Screen.Home.route,
        title = "Home",
        icon = AppDestinations.HOME,
    )

    object Library : BottomNavItem(
        route = Screen.Library.route,
        title = "Library",
        icon = AppDestinations.LIBRARY,
    )

    object Search : BottomNavItem(
        route = Screen.Search.route,
        title = "Search",
        icon = AppDestinations.SEARCH,
    )

    object Favorites : BottomNavItem(
        route = Screen.Favorites.route,
        title = "Favorites",
        icon = AppDestinations.FAVORITES,
    )

    object Profile : BottomNavItem(
        route = Screen.Profile.route,
        title = "Profile",
        icon = AppDestinations.PROFILE,
    )

    companion object {
        val bottomNavItems = listOf(Home, Library, Search, Favorites, Profile)
    }
}
