package com.rpeters.jellyfin.data.repository.common

import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ApiParameterValidatorTest {

    private val libraryId = "0f8fad5b-d9cb-469f-a165-70867728950e"

    @Test
    fun validateLibraryParams_noParameters_letsServerDecide() {
        val params = ApiParameterValidator.validateLibraryParams()!!
        assertNull(params.parentId)
        assertNull(params.itemTypes)
        assertNull(params.collectionType)
        assertEquals(0, params.startIndex)
        assertEquals(50, params.limit)
    }

    @Test
    fun validateLibraryParams_clampsPagination() {
        val low = ApiParameterValidator.validateLibraryParams(itemTypes = "Movie", startIndex = -5, limit = 0)!!
        assertEquals(0, low.startIndex)
        assertEquals(1, low.limit)

        val high = ApiParameterValidator.validateLibraryParams(itemTypes = "Movie", startIndex = 1_000_000, limit = 5_000)!!
        assertEquals(100_000, high.startIndex)
        assertEquals(200, high.limit)
    }

    @Test
    fun validateLibraryParams_placeholderParentIdsAreDropped() {
        listOf("null", "undefined", "0", "-1", "  ", "NULL").forEach { placeholder ->
            val params = ApiParameterValidator.validateLibraryParams(parentId = placeholder, itemTypes = "Movie")!!
            assertNull("'$placeholder' should be dropped", params.parentId)
        }
    }

    @Test
    fun validateLibraryParams_validParentIdIsKept() {
        val params = ApiParameterValidator.validateLibraryParams(parentId = libraryId, collectionType = "movies")!!
        assertEquals(libraryId, params.parentId)
        assertEquals("Movie", params.itemTypes)
    }

    @Test
    fun validateLibraryParams_unknownItemTypesAreFiltered() {
        val mixed = ApiParameterValidator.validateLibraryParams(itemTypes = "Movie, Bogus ,Series")!!
        assertEquals("Movie,Series", mixed.itemTypes)

        val allUnknown = ApiParameterValidator.validateLibraryParams(itemTypes = "Nope,Bogus")!!
        assertNull(allUnknown.itemTypes)
    }

    @Test
    fun validateLibraryParams_collectionTypeIsNormalisedOrDropped() {
        assertEquals("movies", ApiParameterValidator.validateLibraryParams(collectionType = "  MOVIES ")!!.collectionType)
        assertNull(ApiParameterValidator.validateLibraryParams(collectionType = "unknown-type")!!.collectionType)
    }

    @Test
    fun validateLibraryParams_itemTypesFollowTheCollection() {
        fun types(collection: String, requested: String?) =
            ApiParameterValidator.validateLibraryParams(itemTypes = requested, collectionType = collection)!!.itemTypes

        // movies
        assertEquals("Movie", types("movies", null))
        assertEquals("Movie", types("movies", "Movie,Series"))
        assertEquals("Movie", types("movies", "Series"))
        // tvshows
        assertEquals("Series", types("tvshows", null))
        assertEquals("Series,Episode", types("tvshows", "Series,Episode"))
        assertEquals("Series", types("tvshows", "Movie"))
        // music
        assertEquals("MusicAlbum,MusicArtist,Audio", types("music", null))
        assertEquals("Audio", types("music", "Audio"))
        assertEquals("MusicAlbum,MusicArtist,Audio", types("music", "Movie"))
        // homevideos lets the server decide unless Video was asked for
        assertNull(types("homevideos", null))
        assertEquals("Video", types("homevideos", "Video"))
        assertNull(types("homevideos", "Movie"))
        // photos
        assertEquals("Photo", types("photos", null))
        assertEquals("Photo", types("photos", "Photo"))
        assertEquals("Photo", types("photos", "Movie"))
        // books
        assertEquals("Book,AudioBook", types("books", null))
        assertEquals("AudioBook", types("books", "AudioBook"))
        assertEquals("Book,AudioBook", types("books", "Movie"))
        // playlists
        assertEquals("Playlist", types("playlists", null))
        assertEquals("Playlist", types("playlists", "Playlist"))
        assertEquals("Playlist", types("playlists", "Movie"))
        // other known collections keep the request as is
        assertEquals("Video", types("folders", "Video"))
        assertNull(types("livetv", null))
    }

    @Test
    fun createSafeDefaults_coversEveryCollectionType() {
        assertEquals("Movie", ApiParameterValidator.createSafeDefaults(CollectionType.MOVIES).itemTypes)
        assertEquals("Series", ApiParameterValidator.createSafeDefaults(CollectionType.TVSHOWS).itemTypes)
        assertEquals("MusicAlbum,MusicArtist,Audio", ApiParameterValidator.createSafeDefaults(CollectionType.MUSIC).itemTypes)
        assertNull(ApiParameterValidator.createSafeDefaults(CollectionType.HOMEVIDEOS).itemTypes)
        CollectionType.entries.forEach { type ->
            val defaults = ApiParameterValidator.createSafeDefaults(type)
            assertEquals(0, defaults.startIndex)
            assertEquals(50, defaults.limit)
        }
        assertNotNull(ApiParameterValidator.createSafeDefaults(null))
    }

    @Test
    fun validateSearchParams_rejectsShortOrBlankQueries() {
        assertNull(ApiParameterValidator.validateSearchParams(null))
        assertNull(ApiParameterValidator.validateSearchParams("   "))
        assertNull(ApiParameterValidator.validateSearchParams(" a "))
    }

    @Test
    fun validateSearchParams_mapsKindsAndClampsLimit() {
        val params = ApiParameterValidator.validateSearchParams(
            query = "  dune ",
            itemTypes = listOf(
                BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE, BaseItemKind.AUDIO,
                BaseItemKind.MUSIC_ALBUM, BaseItemKind.MUSIC_ARTIST, BaseItemKind.BOOK, BaseItemKind.AUDIO_BOOK,
                BaseItemKind.VIDEO, BaseItemKind.PHOTO, BaseItemKind.PERSON,
            ),
            limit = 999,
        )!!
        assertEquals("dune", params.query)
        assertEquals("Movie,Series,Episode,Audio,MusicAlbum,MusicArtist,Book,AudioBook,Video,Photo", params.itemTypes)
        assertEquals(200, params.limit)
        assertNull(ApiParameterValidator.validateSearchParams("dune")!!.itemTypes)
    }
}
