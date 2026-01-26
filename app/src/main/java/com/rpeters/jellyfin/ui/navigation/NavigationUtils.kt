package com.rpeters.jellyfin.ui.navigation

import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import java.util.Locale

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
    } catch (e: Exception) {
        SecureLogger.e("NavGraph", "Error determining library route for ${library.name}", e)
        null
    }
}
