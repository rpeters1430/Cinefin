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

    /**
     * Resolves the canonical album artist. Uses explicit album-artist metadata, falling back to
     * a single unambiguous artist only when album artist fields are absent.
     */
    fun canonicalArtist(album: BaseItemDto): String? {
        return album.albumArtist?.takeIf { it.isNotBlank() }
            ?: album.albumArtists?.firstOrNull { !it.name.isNullOrBlank() }?.name
            ?: album.artistItems?.firstOrNull { !it.name.isNullOrBlank() }?.name
            ?: album.artists?.singleOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun musicBrainzId(album: BaseItemDto): String? =
        album.providerIds?.entries
            ?.firstOrNull { it.key.equals("MusicBrainzAlbum", ignoreCase = true) }
            ?.value?.takeIf { it.isNotBlank() }?.let(::normalized)

    /**
     * Determines whether two [BaseItemDto] objects represent the same album.
     * Matches on normalized album name and normalized primary album artist.
     * Differing production years or distinct MusicBrainz release IDs keep releases separate.
     * Different physical parent directories on disk (or null parent IDs) do not prevent matching.
     */
    fun sameAlbum(first: BaseItemDto, second: BaseItemDto): Boolean {
        if (first.id == second.id) return true
        if (first.type != BaseItemKind.MUSIC_ALBUM || second.type != BaseItemKind.MUSIC_ALBUM) return false

        val title1 = first.name?.takeIf { it.isNotBlank() } ?: return false
        val title2 = second.name?.takeIf { it.isNotBlank() } ?: return false
        if (normalized(title1) != normalized(title2)) return false

        val artist1 = canonicalArtist(first) ?: return false
        val artist2 = canonicalArtist(second) ?: return false
        val primary1 = normalized(primaryArtistName(artist1))
        val primary2 = normalized(primaryArtistName(artist2))
        if (primary1 != primary2) return false

        // Differing MusicBrainz release IDs mean distinct album releases
        val mb1 = musicBrainzId(first)
        val mb2 = musicBrainzId(second)
        if (mb1 != null && mb2 != null && mb1 != mb2) return false

        // Differing production years mean distinct album releases (e.g. self-titled albums)
        val y1 = (first.productionYear as? Number)?.toInt() ?: first.productionYear
        val y2 = (second.productionYear as? Number)?.toInt() ?: second.productionYear
        if (y1 != null && y2 != null && y1 != y2) return false

        return true
    }

    private fun isBetterAlbumRepresentation(candidate: BaseItemDto, current: BaseItemDto): Boolean {
        // 1. Prefer canonical artist without feature marker
        val currentHasFeature = current.albumArtist?.let { featureMarker.containsMatchIn(it) } ?: false
        val candidateHasFeature = candidate.albumArtist?.let { featureMarker.containsMatchIn(it) } ?: false
        if (currentHasFeature && !candidateHasFeature) return true
        if (!currentHasFeature && candidateHasFeature) return false

        // 2. Prefer entry with higher childCount (more tracks)
        val currentChildCount = current.childCount ?: 0
        val candidateChildCount = candidate.childCount ?: 0
        if (candidateChildCount > currentChildCount) return true
        if (currentChildCount > candidateChildCount) return false

        // 3. Prefer entry with productionYear
        if (current.productionYear == null && candidate.productionYear != null) return true
        if (current.productionYear != null && candidate.productionYear == null) return false

        // 4. Prefer entry with image
        if (current.primaryImageAspectRatio == null && candidate.primaryImageAspectRatio != null) return true

        return false
    }

    /** Keep real server IDs for navigation; preserve songs, artists, and unknown albums. */
    fun collapseAlbums(items: List<BaseItemDto>): List<BaseItemDto> {
        val result = mutableListOf<BaseItemDto>()
        for (item in items) {
            if (item.type != BaseItemKind.MUSIC_ALBUM) {
                result.add(item)
                continue
            }
            val existingIndex = result.indexOfFirst { sameAlbum(it, item) }
            if (existingIndex >= 0) {
                val existing = result[existingIndex]
                if (isBetterAlbumRepresentation(item, existing)) {
                    result[existingIndex] = item
                }
            } else {
                result.add(item)
            }
        }
        return result
    }

    fun orderedTracks(tracks: List<BaseItemDto>): List<BaseItemDto> = tracks.distinctBy { it.id }
        .sortedWith(compareBy<BaseItemDto> { it.parentIndexNumber ?: 1 }
            .thenBy { it.indexNumber ?: Int.MAX_VALUE }
            .thenBy { it.sortName ?: it.name })
}
