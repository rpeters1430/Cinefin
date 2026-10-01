package com.rpeters.jellyfin.ui.utils

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class MusicArtistUtilsTest {

    private fun item(name: String, type: BaseItemKind = BaseItemKind.MUSIC_ARTIST) =
        BaseItemDto(id = UUID.randomUUID(), name = name, type = type)

    @Test
    fun primaryArtistName_featuredVariants_returnLeadArtist() {
        assertEquals(LEAD_ARTIST, MusicArtistUtils.primaryArtistName("$LEAD_ARTIST feat. Britney Spears"))
        assertEquals(LEAD_ARTIST, MusicArtistUtils.primaryArtistName("$LEAD_ARTIST ft. Justin Timberlake"))
        assertEquals(LEAD_ARTIST, MusicArtistUtils.primaryArtistName("$LEAD_ARTIST Featuring Kanye West"))
        assertEquals(LEAD_ARTIST, MusicArtistUtils.primaryArtistName("$LEAD_ARTIST & Justin Timberlake"))
        assertEquals(LEAD_ARTIST, MusicArtistUtils.primaryArtistName("$LEAD_ARTIST (feat. Nicki Minaj)"))
        assertEquals(LEAD_ARTIST, MusicArtistUtils.primaryArtistName("$LEAD_ARTIST, Maluma"))
    }

    @Test
    fun primaryArtistName_plainName_returnsNameUnchanged() {
        assertEquals(LEAD_ARTIST, MusicArtistUtils.primaryArtistName("  $LEAD_ARTIST "))
        assertEquals("Feather", MusicArtistUtils.primaryArtistName("Feather"))
        assertEquals("Within Temptation", MusicArtistUtils.primaryArtistName("Within Temptation"))
    }

    @Test
    fun collapseFeaturedArtists_leadArtistPresent_dropsVariants() {
        val madonna = item(LEAD_ARTIST)
        val album = item("Hard Candy", BaseItemKind.MUSIC_ALBUM)
        val items = listOf(
            madonna,
            item("$LEAD_ARTIST feat. Justin Timberlake"),
            album,
            item("${LEAD_ARTIST.lowercase()} & Kanye West"),
        )

        val result = MusicArtistUtils.collapseFeaturedArtists(items)

        assertEquals(listOf(madonna, album), result)
    }

    @Test
    fun collapseFeaturedArtists_leadArtistMissing_keepsEntries() {
        val items = listOf(
            item("Simon & Garfunkel"),
            item("Earth, Wind & Fire"),
            item(LEAD_ARTIST),
        )

        val result = MusicArtistUtils.collapseFeaturedArtists(items)

        assertEquals(items, result)
    }

    @Test
    fun collapseFeaturedArtists_nonArtistItems_areNeverDropped() {
        val items = listOf(
            item(LEAD_ARTIST),
            item("$LEAD_ARTIST feat. Britney Spears", BaseItemKind.AUDIO),
        )

        val result = MusicArtistUtils.collapseFeaturedArtists(items)

        assertEquals(items, result)
    }

    private companion object {
        const val LEAD_ARTIST = "Madonna"
    }
}
