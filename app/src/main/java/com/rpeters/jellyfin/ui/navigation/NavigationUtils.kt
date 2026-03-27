package com.rpeters.jellyfin.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import java.util.Locale

/**
 * Navigates to a main-destination route using the standard bottom-nav back-stack strategy:
 * pop up to the start destination (saving state), launch single-top, and restore state.
 * Use this for all bottom nav and navigation rail/drawer item clicks.
 */
fun NavController.navigateToMainDestination(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Routes whose current destination should show the main navigation chrome (bottom bar / rail /
 * drawer). Includes top-level media sections so users retain navigation context.
 */
fun shouldShowNavigation(route: String?): Boolean {
    if (route == null) return false
    return route in setOf(
        Screen.Home.route,
        Screen.Library.route,
        Screen.Search.route,
        Screen.Favorites.route,
        Screen.Settings.route,
        Screen.Movies.route,
        Screen.TVShows.route,
        Screen.Music.route,
        Screen.HomeVideos.route,
        Screen.Books.route,
    )
}

/**
 * Shared navigation helpers to keep NavGraph modules small.
 */
fun libraryRouteFor(library: BaseItemDto): String? {
    return try {
        when (library.collectionType) {
            CollectionType.MOVIES -> Screen.Movies.route
            CollectionType.TVSHOWS -> Screen.TVShows.route
            CollectionType.MUSIC -> Screen.Music.route
            CollectionType.BOOKS -> Screen.Books.route
            CollectionType.HOMEVIDEOS -> Screen.HomeVideos.route
            else -> library.id.toString().let { id ->
                val type = library.collectionType?.toString()?.lowercase(Locale.getDefault())
                    ?: "mixed"
                Screen.Stuff.createRoute(id, type)
            }
        }
    } catch (e: CancellationException) {
        throw e
    }
}
