package com.rpeters.jellyfin.data.repository.common

import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private const val MOVIE = "Movie"
private const val SERIES = "Series"
private const val VIDEO = "Video"
private const val PHOTO = "Photo"
private const val PLAYLIST = "Playlist"
private const val MUSIC_TYPES = "MusicAlbum,MusicArtist,Audio"
private const val MOVIES = "movies"
private const val TV_SHOWS = "tvshows"
private const val MUSIC = "music"
private const val HOME_VIDEOS = "homevideos"
private const val PHOTOS = "photos"
private const val BOOKS = "books"
private const val PLAYLISTS = "playlists"
private const val BOOK_TYPES = "Book,AudioBook"

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
        val low = ApiParameterValidator.validateLibraryParams(itemTypes = MOVIE, startIndex = -5, limit = 0)!!
        assertEquals(0, low.startIndex)
        assertEquals(1, low.limit)

        val high = ApiParameterValidator.validateLibraryParams(itemTypes = MOVIE, startIndex = 1_000_000, limit = 5_000)!!
        assertEquals(100_000, high.startIndex)
        assertEquals(200, high.limit)
    }

    @Test
    fun validateLibraryParams_placeholderParentIdsAreDropped() {
        listOf("null", "undefined", "0", "-1", "  ", "NULL").forEach { placeholder ->
            val params = ApiParameterValidator.validateLibraryParams(parentId = placeholder, itemTypes = MOVIE)!!
            assertNull("'$placeholder' should be dropped", params.parentId)
        }
    }

    @Test
    fun validateLibraryParams_validParentIdIsKept() {
        val params = ApiParameterValidator.validateLibraryParams(parentId = libraryId, collectionType = MOVIES)!!
        assertEquals(libraryId, params.parentId)
        assertEquals(MOVIE, params.itemTypes)
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
        assertEquals(MOVIES, ApiParameterValidator.validateLibraryParams(collectionType = "  MOVIES ")!!.collectionType)
        assertNull(ApiParameterValidator.validateLibraryParams(collectionType = "unknown-type")!!.collectionType)
    }

    @Test
    fun validateLibraryParams_itemTypesFollowTheCollection() {
        fun types(collection: String, requested: String?) =
            ApiParameterValidator.validateLibraryParams(itemTypes = requested, collectionType = collection)!!.itemTypes

        // movies
        assertEquals(MOVIE, types(MOVIES, null))
        assertEquals(MOVIE, types(MOVIES, "Movie,Series"))
        assertEquals(MOVIE, types(MOVIES, SERIES))
        // tvshows
        assertEquals(SERIES, types(TV_SHOWS, null))
        assertEquals("Series,Episode", types(TV_SHOWS, "Series,Episode"))
        assertEquals(SERIES, types(TV_SHOWS, MOVIE))
        // music
        assertEquals(MUSIC_TYPES, types(MUSIC, null))
        assertEquals("Audio", types(MUSIC, "Audio"))
        assertEquals(MUSIC_TYPES, types(MUSIC, MOVIE))
        // homevideos lets the server decide unless Video was asked for
        assertNull(types(HOME_VIDEOS, null))
        assertEquals(VIDEO, types(HOME_VIDEOS, VIDEO))
        assertNull(types(HOME_VIDEOS, MOVIE))
        // photos
        assertEquals(PHOTO, types(PHOTOS, null))
        assertEquals(PHOTO, types(PHOTOS, PHOTO))
        assertEquals(PHOTO, types(PHOTOS, MOVIE))
        // books
        assertEquals(BOOK_TYPES, types(BOOKS, null))
        assertEquals("AudioBook", types(BOOKS, "AudioBook"))
        assertEquals(BOOK_TYPES, types(BOOKS, MOVIE))
        // playlists
        assertEquals(PLAYLIST, types(PLAYLISTS, null))
        assertEquals(PLAYLIST, types(PLAYLISTS, PLAYLIST))
        assertEquals(PLAYLIST, types(PLAYLISTS, MOVIE))
        // other known collections keep the request as is
        assertEquals(VIDEO, types("folders", VIDEO))
        assertNull(types("livetv", null))
    }

    @Test
    fun createSafeDefaults_coversEveryCollectionType() {
        assertEquals(MOVIE, ApiParameterValidator.createSafeDefaults(CollectionType.MOVIES).itemTypes)
        assertEquals(SERIES, ApiParameterValidator.createSafeDefaults(CollectionType.TVSHOWS).itemTypes)
        assertEquals(MUSIC_TYPES, ApiParameterValidator.createSafeDefaults(CollectionType.MUSIC).itemTypes)
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
