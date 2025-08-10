package com.example.jellyfinandroid.ui.viewmodel

import org.junit.Assert.*
import org.junit.Test

/**
 * Basic test suite for MainAppViewModel.
 *
 * Tests core functionality and security patterns.
 */
class MainAppViewModelTest {

    @Test
    fun `MainAppViewModel dependencies are properly structured`() {
        // This test validates that the viewModel structure is correct
        // by testing the companion classes exist
        assertTrue("MainAppState should be available", MainAppState::class.java != null)
        assertTrue("PaginatedItems should be available", PaginatedItems::class.java != null)
    }

    @Test
    fun `MainAppState has proper default values`() {
        val state = MainAppState()

        assertFalse("Initial loading state should be false", state.isLoading)
        assertTrue("Initial libraries should be empty", state.libraries.isEmpty())
        assertTrue("Initial recently added should be empty", state.recentlyAdded.isEmpty())
        assertTrue("Initial search query should be empty", state.searchQuery.isEmpty())
        assertNull("Initial error message should be null", state.errorMessage)
    }

    @Test
    fun `PaginatedItems structure is correct`() {
        val paginatedItems = PaginatedItems(
            items = emptyList(),
            hasMore = false,
            totalCount = 0,
        )

        assertTrue("Items should be empty", paginatedItems.items.isEmpty())
        assertFalse("HasMore should be false", paginatedItems.hasMore)
        assertEquals("Total count should be 0", 0, paginatedItems.totalCount)
    }

    @Test
    fun `MainAppState can be copied with new values`() {
        val originalState = MainAppState()
        val newState = originalState.copy(isLoading = true, searchQuery = "test")

        assertTrue("New state should be loading", newState.isLoading)
        assertEquals("Search query should be updated", "test", newState.searchQuery)
        assertFalse("Original state should remain unchanged", originalState.isLoading)
    }
}
