package com.rpeters.jellyfin.data.music

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MusicAlbumGroupingTest {
    private val parent = UUID.randomUUID()

    private fun album(artist: String = "Metallica", title: String = "Test Album") = BaseItemDto(
        id = UUID.randomUUID(), type = BaseItemKind.MUSIC_ALBUM, name = title,
        albumArtist = artist, parentId = parent, productionYear = 2020,
    )

    @Test
    fun collapseAlbums_featuredCredits_keepOneAlbumAndAllSongs() {
        val main = album()
        val featured = album("Metallica feat. Guest")
        val track = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.AUDIO,
            name = "Song", artists = listOf("Metallica", "Guest"))
        val artist = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MUSIC_ARTIST, name = "Metallica")
        assertEquals(listOf(main, track, artist),
            MusicAlbumGrouping.collapseAlbums(listOf(main, featured, track, artist)))
        assertTrue(MusicAlbumGrouping.sameAlbum(main, featured))
    }

    @Test
    fun sameAlbum_trackArtistsDoNotOverrideAlbumArtist() {
        val first = album().copy(artists = listOf("Metallica"))
        val second = album().copy(artists = listOf("Guest", "Metallica"))
        assertTrue(MusicAlbumGrouping.sameAlbum(first, second))
    }

    @Test
    fun collapseAlbums_caseAndWhitespace_areNormalized() {
        val first = album()
        assertEquals(listOf(first), MusicAlbumGrouping.collapseAlbums(
            listOf(first, album("  METALLICA featuring Guest ", " test   album "))))
    }

    @Test
    fun sameAlbum_differentArtistsEditionsYearsAndParents_staySeparate() {
        val first = album()
        listOf(album("Megadeth"), album(title = "Test Album (Deluxe)"),
            album().copy(productionYear = 2021), album().copy(parentId = UUID.randomUUID()),
            album().copy(providerIds = mapOf("MusicBrainzAlbum" to "different-release")))
            .forEach { assertFalse(MusicAlbumGrouping.sameAlbum(first, it)) }
    }

    @Test
    fun collapseAlbums_missingIdentity_doesNotMergeUnknownAlbums() {
        val items = listOf(album().copy(albumArtist = null), album().copy(albumArtist = null),
            album().copy(parentId = null), album().copy(parentId = null))
        assertEquals(items, MusicAlbumGrouping.collapseAlbums(items))
    }

    @Test
    fun sameAlbum_punctuatedBandNames_remainIntact() {
        assertFalse(MusicAlbumGrouping.sameAlbum(album("AC/DC"), album("AC")))
        assertFalse(MusicAlbumGrouping.sameAlbum(album("Simon & Garfunkel"), album("Simon")))
    }

    @Test
    fun orderedTracks_multipleArtistsAndDiscs_keepEveryTrackInDiscOrder() {
        val disc1 = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.AUDIO,
            parentIndexNumber = 1, indexNumber = 1, artists = listOf("Metallica"))
        val guest = disc1.copy(id = UUID.randomUUID(), indexNumber = 2, artists = listOf("Metallica", "Guest"))
        val disc2 = disc1.copy(id = UUID.randomUUID(), parentIndexNumber = 2)
        assertEquals(listOf(disc1, guest, disc2),
            MusicAlbumGrouping.orderedTracks(listOf(disc2, guest, disc1, guest)))
    }
}
