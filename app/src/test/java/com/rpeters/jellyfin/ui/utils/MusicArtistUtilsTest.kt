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
        assertEquals(LEAD_ARTIST, MusicArtistUtils.primaryArtistName("$LEAD_ARTIST (feat. Nicki Minaj)"))
        assertEquals(LEAD_ARTIST, MusicArtistUtils.primaryArtistName("$LEAD_ARTIST [Ft Maluma]"))
    }

    @Test
    fun primaryArtistName_plainName_returnsNameUnchanged() {
        assertEquals(LEAD_ARTIST, MusicArtistUtils.primaryArtistName("  $LEAD_ARTIST "))
        listOf("Feather", "Within Temptation", SIMON_AND_GARFUNKEL, AC_DC, EARTH_WIND_AND_FIRE).forEach { name ->
            assertEquals(name, MusicArtistUtils.primaryArtistName(name))
        }
    }

    @Test
    fun collapseFeaturedArtists_leadArtistPresent_dropsVariants() {
        val madonna = item(LEAD_ARTIST)
        val album = item("Hard Candy", BaseItemKind.MUSIC_ALBUM)
        val items = listOf(
            madonna,
            item("$LEAD_ARTIST feat. Justin Timberlake"),
            album,
            item("${LEAD_ARTIST.lowercase()} featuring Kanye West"),
        )

        val result = MusicArtistUtils.collapseFeaturedArtists(items)

        assertEquals(listOf(madonna, album), result)
    }

    @Test
    fun collapseFeaturedArtists_leadArtistMissing_keepsEntries() {
        val items = listOf(
            item(SIMON_AND_GARFUNKEL),
            item(EARTH_WIND_AND_FIRE),
            item(LEAD_ARTIST),
        )

        val result = MusicArtistUtils.collapseFeaturedArtists(items)

        assertEquals(items, result)
    }

    @Test
    fun collapseFeaturedArtists_punctuatedBandNames_areKeptEvenWhenFirstWordIsAnArtist() {
        val items = listOf(
            item("Simon"),
            item(SIMON_AND_GARFUNKEL),
            item("AC"),
            item(AC_DC),
            item("Earth"),
            item(EARTH_WIND_AND_FIRE),
        )

        val result = MusicArtistUtils.collapseFeaturedArtists(items)

        assertEquals(items, result)
    }

    @Test
    fun collapseFeaturedArtists_nonArtistItems_areNeverDropped() {
        val items = listOf(
            item(LEAD_ARTIST),
            item("$LEAD_ARTIST feat. Kylie Minogue", BaseItemKind.AUDIO),
        )

        val result = MusicArtistUtils.collapseFeaturedArtists(items)

        assertEquals(items, result)
    }

    private companion object {
        const val LEAD_ARTIST = "Madonna"
        const val SIMON_AND_GARFUNKEL = "Simon & Garfunkel"
        const val AC_DC = "AC/DC"
        const val EARTH_WIND_AND_FIRE = "Earth, Wind & Fire"
    }
}
