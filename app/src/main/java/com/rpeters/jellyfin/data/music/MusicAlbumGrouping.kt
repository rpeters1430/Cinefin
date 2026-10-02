package com.rpeters.jellyfin.data.music

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.Locale

/** Shared album identity for browsing and playback. Track artist credits are not album identity. */
object MusicAlbumGrouping {
    private val featureMarker = Regex(
        """\s*(?:[(\[]\s*)?(?:\bfeat\b\.?|\bft\b\.?|\bfeaturing\b)\s*""",
        RegexOption.IGNORE_CASE,
    )

    fun primaryArtistName(name: String): String {
        val trimmed = name.trim()
        val match = featureMarker.find(trimmed) ?: return trimmed
        return trimmed.substring(0, match.range.first).trim().ifEmpty { trimmed }
    }

    private fun normalized(value: String) = value.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    private data class AlbumKey(
        val title: String,
        val artist: String,
        val parentId: String,
        val year: Int?,
        val releaseId: String?,
    )

    private fun key(album: BaseItemDto): AlbumKey? {
        if (album.type != BaseItemKind.MUSIC_ALBUM) return null
        val title = album.name?.takeIf { it.isNotBlank() } ?: return null
        val artist = album.albumArtist?.takeIf { it.isNotBlank() }
            ?: album.albumArtists?.firstOrNull { !it.name.isNullOrBlank() }?.name
            ?: album.artists?.firstOrNull { it.isNotBlank() }
            ?: return null
        // Missing scope/artist metadata is not evidence that two albums are the same.
        val parent = album.parentId ?: return null
        val releaseId = album.providerIds?.entries
            ?.firstOrNull { it.key.equals("MusicBrainzAlbum", ignoreCase = true) }
            ?.value?.takeIf { it.isNotBlank() }?.let(::normalized)
        return AlbumKey(
            title = normalized(title),
            artist = normalized(primaryArtistName(artist)),
            parentId = parent.toString(),
            year = album.productionYear,
            releaseId = releaseId,
        )
    }

    fun sameAlbum(first: BaseItemDto, second: BaseItemDto): Boolean =
        first.id == second.id || key(first)?.let { it == key(second) } == true

    /** Keep real server IDs for navigation; preserve songs, artists, and unknown albums. */
    fun collapseAlbums(items: List<BaseItemDto>): List<BaseItemDto> {
        val seen = mutableSetOf<AlbumKey>()
        return items.filter { item -> key(item)?.let { seen.add(it) } ?: true }
    }

    fun orderedTracks(tracks: List<BaseItemDto>): List<BaseItemDto> = tracks.distinctBy { it.id }
        .sortedWith(compareBy<BaseItemDto> { it.parentIndexNumber ?: 1 }
            .thenBy { it.indexNumber ?: Int.MAX_VALUE }
            .thenBy { it.sortName ?: it.name })
}
