package com.rpeters.jellyfin.data.emby

import com.rpeters.jellyfin.ui.screens.LibraryType
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbyMixedLibraryTest {

    @Test
    fun normalize_mixedCollectionType_keepsLibraryWithNullType() {
        val body = requireNotNull(javaClass.classLoader?.getResource("emby/user_views_mixed.json")).readText()

        val views = EmbyJsonNormalizer.decode(BaseItemDtoQueryResult.serializer(), body)

        assertEquals(listOf("Movies", "Everything", "No Type"), views.items.map { it.name })
        assertEquals(CollectionType.MOVIES, views.items[0].collectionType)
        assertNull(views.items[1].collectionType)
        assertNull(views.items[2].collectionType)
    }

    @Test
    fun itemKindsFor_mixedLibrary_includesMoviesAndSeries() {
        val kinds = LibraryType.STUFF.itemKindsFor(collectionType = null)

        assertTrue(BaseItemKind.MOVIE in kinds)
        assertTrue(BaseItemKind.SERIES in kinds)
        assertTrue(kinds.containsAll(LibraryType.STUFF.itemKinds))
    }

    @Test
    fun itemKindsFor_typedLibrary_isUnchanged() {
        assertEquals(LibraryType.STUFF.itemKinds, LibraryType.STUFF.itemKindsFor(CollectionType.BOOKS))
        assertEquals(LibraryType.MOVIES.itemKinds, LibraryType.MOVIES.itemKindsFor(null))
    }
}
