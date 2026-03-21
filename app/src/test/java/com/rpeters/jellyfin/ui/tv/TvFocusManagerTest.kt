package com.rpeters.jellyfin.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvFocusManagerTest {

    @Test
    fun saveAndRestoreFocusState_returnsSavedValues() {
        val manager = TvFocusManager()

        manager.saveFocusState(
            carouselId = "home_continue_watching",
            focusedIndex = 4,
            scrollPosition = 2,
            focusedItemKey = "item-4",
        )

        val restored = manager.getFocusState("home_continue_watching")
        assertEquals(4, restored?.focusedIndex)
        assertEquals(2, restored?.scrollPosition)
        assertEquals("home_continue_watching", restored?.carouselId)
        assertEquals("item-4", restored?.focusedItemKey)
    }

    @Test
    fun clearFocusStates_removesAllSavedState() {
        val manager = TvFocusManager()
        manager.saveFocusState("home_row", 1, 0)
        manager.saveFocusState("library_row", 2, 1)

        manager.clearFocusStates()

        assertNull(manager.getFocusState("home_row"))
        assertNull(manager.getFocusState("library_row"))
    }

    @Test
    fun clearScreenFocusStates_onlyRemovesNamespacedEntries() {
        val manager = TvFocusManager()
        manager.saveFocusState("home_row_1", 1, 0)
        manager.saveFocusState("home_row_2", 2, 1)
        manager.saveFocusState("search_row_1", 3, 2)

        manager.clearScreenFocusStates("home")

        assertNull(manager.getFocusState("home_row_1"))
        assertNull(manager.getFocusState("home_row_2"))
        assertEquals(3, manager.getFocusState("search_row_1")?.focusedIndex)
    }

    @Test
    fun getCarouselId_usesCurrentScreenNamespace() {
        val manager = TvFocusManager()
        manager.setCurrentScreen("tv_home")

        assertEquals("tv_home_featured", manager.getCarouselId("featured"))
    }
}
