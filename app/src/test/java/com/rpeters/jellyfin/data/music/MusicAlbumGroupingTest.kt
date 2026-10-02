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
    fun sameAlbum_differentArtistsEditionsAndYears_staySeparate() {
        val first = album().copy(providerIds = mapOf("MusicBrainzAlbum" to "main-release"))
        listOf(
            album("Megadeth"),
            album(title = "Test Album (Deluxe)"),
            album().copy(productionYear = 2021),
            album().copy(providerIds = mapOf("MusicBrainzAlbum" to "different-release")),
        ).forEach { assertFalse(MusicAlbumGrouping.sameAlbum(first, it)) }
    }

    @Test
    fun sameAlbum_differentParentsAndNullParents_groupTogether() {
        val first = album()
        val differentParent = album().copy(parentId = UUID.randomUUID())
        val nullParent1 = album().copy(parentId = null)
        val nullParent2 = album().copy(parentId = null)

        assertTrue(MusicAlbumGrouping.sameAlbum(first, differentParent))
        assertTrue(MusicAlbumGrouping.sameAlbum(nullParent1, nullParent2))
        assertTrue(MusicAlbumGrouping.sameAlbum(first, nullParent1))
    }

    @Test
    fun sameAlbum_missingYearOrReleaseIdOnOneVariant_groupsTogether() {
        val main = album().copy(providerIds = mapOf("MusicBrainzAlbum" to "mb-release-1"))
        val variantMissingYear = album("Metallica feat. Guest").copy(productionYear = null)
        val variantMissingMb = album("Metallica feat. Guest").copy(providerIds = null)

        assertTrue(MusicAlbumGrouping.sameAlbum(main, variantMissingYear))
        assertTrue(MusicAlbumGrouping.sameAlbum(main, variantMissingMb))
    }

    @Test
    fun collapseAlbums_prefersLeadArtistWithoutFeatureMarker() {
        val featured = album("Metallica feat. Guest", "Test Album")
        val main = album("Metallica", "Test Album")
        assertEquals(listOf(main), MusicAlbumGrouping.collapseAlbums(listOf(featured, main)))
    }

    @Test
    fun collapseAlbums_missingIdentity_doesNotMergeUnknownAlbums() {
        val items = listOf(
            album().copy(albumArtist = null, albumArtists = null, artists = null),
            album().copy(albumArtist = null, albumArtists = null, artists = null),
            album().copy(name = null),
        )
        assertEquals(items, MusicAlbumGrouping.collapseAlbums(items))
    }

    @Test
    fun sameAlbum_multipleContributingArtistsWithoutAlbumArtist_returnsFalse() {
        val album1 = album().copy(albumArtist = null, albumArtists = null, artists = listOf("Artist A", "Artist B"))
        val album2 = album().copy(albumArtist = null, albumArtists = null, artists = listOf("Artist A", "Artist C"))
        assertFalse(MusicAlbumGrouping.sameAlbum(album1, album2))
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
