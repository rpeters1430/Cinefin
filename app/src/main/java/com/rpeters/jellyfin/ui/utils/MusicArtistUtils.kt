package com.rpeters.jellyfin.ui.utils

import com.rpeters.jellyfin.data.music.MusicAlbumGrouping
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Helpers for collapsing "featured artist" variants in music listings.
 *
 * Jellyfin creates a separate MusicArtist entry for every distinct artist string on a track,
 * so an album by "Madonna" with a couple of guest spots also yields entries such as
 * "Madonna feat. Britney Spears" or "Madonna ft. Justin Timberlake". Those show up as duplicate
 * artists in the library even though the user only has one Madonna album.
 */
object MusicArtistUtils {

    /**
     * Returns the lead artist of a collaboration string, e.g. "Madonna feat. Britney Spears"
     * -> "Madonna". Returns the trimmed name unchanged when there is no collaboration marker.
     */
    fun primaryArtistName(name: String): String = MusicAlbumGrouping.primaryArtistName(name)

    /**
     * Drops MusicArtist entries that are only a collaboration variant of another artist already
     * in [items] (e.g. "Madonna feat. X" when "Madonna" is present). Other item types and
     * artists without a matching lead-artist entry are kept unchanged, in order.
     */
    fun collapseFeaturedArtists(items: List<BaseItemDto>): List<BaseItemDto> {
        val artistNames = items
            .asSequence()
            .filter { it.type == BaseItemKind.MUSIC_ARTIST }
            .mapNotNull { it.name?.trim()?.lowercase() }
            .toSet()
        if (artistNames.isEmpty()) return items

        return items.filter { item ->
            if (item.type != BaseItemKind.MUSIC_ARTIST) return@filter true
            val name = item.name?.trim() ?: return@filter true
            val primary = primaryArtistName(name)
            val isVariant = !primary.equals(name, ignoreCase = true)
            !(isVariant && primary.lowercase() in artistNames)
        }
    }
}
