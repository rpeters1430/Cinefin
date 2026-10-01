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
        assertEquals("Madonna", MusicArtistUtils.primaryArtistName("Madonna feat. Britney Spears"))
        assertEquals("Madonna", MusicArtistUtils.primaryArtistName("Madonna ft. Justin Timberlake"))
        assertEquals("Madonna", MusicArtistUtils.primaryArtistName("Madonna Featuring Kanye West"))
        assertEquals("Madonna", MusicArtistUtils.primaryArtistName("Madonna & Justin Timberlake"))
        assertEquals("Madonna", MusicArtistUtils.primaryArtistName("Madonna (feat. Nicki Minaj)"))
        assertEquals("Madonna", MusicArtistUtils.primaryArtistName("Madonna, Maluma"))
    }

    @Test
    fun primaryArtistName_plainName_returnsNameUnchanged() {
        assertEquals("Madonna", MusicArtistUtils.primaryArtistName("  Madonna "))
        assertEquals("Feather", MusicArtistUtils.primaryArtistName("Feather"))
        assertEquals("Within Temptation", MusicArtistUtils.primaryArtistName("Within Temptation"))
    }

    @Test
    fun collapseFeaturedArtists_leadArtistPresent_dropsVariants() {
        val madonna = item("Madonna")
        val album = item("Hard Candy", BaseItemKind.MUSIC_ALBUM)
        val items = listOf(
            madonna,
            item("Madonna feat. Justin Timberlake"),
            album,
            item("madonna & Kanye West"),
        )

        val result = MusicArtistUtils.collapseFeaturedArtists(items)

        assertEquals(listOf(madonna, album), result)
    }

    @Test
    fun collapseFeaturedArtists_leadArtistMissing_keepsEntries() {
        val items = listOf(
            item("Simon & Garfunkel"),
            item("Earth, Wind & Fire"),
            item("Madonna"),
        )

        val result = MusicArtistUtils.collapseFeaturedArtists(items)

        assertEquals(items, result)
    }

    @Test
    fun collapseFeaturedArtists_nonArtistItems_areNeverDropped() {
        val items = listOf(
            item("Madonna"),
            item("Madonna feat. Britney Spears", BaseItemKind.AUDIO),
        )

        val result = MusicArtistUtils.collapseFeaturedArtists(items)

        assertEquals(items, result)
    }
}
