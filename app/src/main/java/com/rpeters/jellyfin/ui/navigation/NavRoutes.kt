package com.rpeters.jellyfin.ui.navigation

import android.net.Uri

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
    object EnhancedHome : Screen("enhanced_home")
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
    object NowPlaying : Screen("now_playing")
    object AudioQueue : Screen("audio_queue")
    object HomeVideos : Screen("home_videos")
    object Books : Screen("books")
    object Stuff : Screen("stuff/{libraryId}/{collectionType}") {
        fun createRoute(libraryId: String, collectionType: String) =
            "stuff/$libraryId/$collectionType"
    }
    object Search : Screen("search?query={query}") {
        fun createRoute(query: String) = "search?query=${Uri.encode(query)}"
    }
    object AiAssistant : Screen("ai_assistant")
    object Favorites : Screen("favorites")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object AppearanceSettings : Screen("appearance_settings")
    object PlaybackSettings : Screen("playback_settings")
    object DownloadsSettings : Screen("downloads_settings")
    object NotificationsSettings : Screen("notifications_settings")
    object PrivacySettings : Screen("privacy_settings")
    object AccessibilitySettings : Screen("accessibility_settings")
    object PinSettings : Screen("pin_settings")
    object SubtitleSettings : Screen("subtitle_settings")
    object TranscodingDiagnostics : Screen("transcoding_diagnostics")
    object PrivacyPolicy : Screen("privacy_policy")
    object MovieDetail : Screen("movie_detail/{movieId}") {
        fun createRoute(movieId: String) = "movie_detail/$movieId"
    }
    object TVEpisodeDetail : Screen("episode_detail/{episodeId}") {
        fun createRoute(episodeId: String) = "episode_detail/$episodeId"
    }
    object AlbumDetail : Screen("album_detail/{albumId}") {
        fun createRoute(albumId: String) = "album_detail/$albumId"
    }
    object ArtistDetail : Screen("artist_detail/{artistId}") {
        fun createRoute(artistId: String) = "artist_detail/$artistId"
    }
    object PersonDetail : Screen("person_detail/{personId}/{personName}") {
        fun createRoute(personId: String, personName: String) =
            "person_detail/$personId/${Uri.encode(personName)}"
    }
    object HomeVideoDetail : Screen("home_video_detail/{videoId}") {
        fun createRoute(videoId: String) = "home_video_detail/$videoId"
    }
    object ItemDetail : Screen("item_detail/{itemId}") {
        fun createRoute(itemId: String) = "item_detail/$itemId"
    }

    // For navigation arguments
    companion object {
        const val SERIES_ID_ARG = "seriesId"
        const val SEASON_ID_ARG = "seasonId"
        const val MOVIE_ID_ARG = "movieId"
        const val EPISODE_ID_ARG = "episodeId"
        const val ALBUM_ID_ARG = "albumId"
        const val ARTIST_ID_ARG = "artistId"
        const val VIDEO_ID_ARG = "videoId"
        const val ITEM_ID_ARG = "itemId"
        const val LIBRARY_ID_ARG = "libraryId"
        const val COLLECTION_TYPE_ARG = "collectionType"
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
