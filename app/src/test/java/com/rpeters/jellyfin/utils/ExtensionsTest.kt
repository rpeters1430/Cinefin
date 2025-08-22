package com.rpeters.jellyfin.utils

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.*
import org.junit.Test
import java.util.*

/**
 * Test suite for extension functions.
 */
class ExtensionsTest {

    @Test
    fun `getRatingAsDouble returns correct value when communityRating is valid`() {
        val item = BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.MOVIE,
            communityRating = 8.5f,
            name = "Test Item",
        )
        assertEquals(8.5, item.getRatingAsDouble(), 0.01)
    }

    @Test
    fun `getRatingAsDouble returns 0 when communityRating is null`() {
        val item = BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.MOVIE,
            communityRating = null,
            name = "Test Item",
        )
        assertEquals(0.0, item.getRatingAsDouble(), 0.01)
    }

    @Test
    fun `getRatingAsDouble returns 0 when communityRating is invalid`() {
        val item = BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.MOVIE,
            communityRating = null, // Use null instead of invalid string
            name = "Test Item",
        )
        assertEquals(0.0, item.getRatingAsDouble(), 0.01)
    }

    @Test
    fun `hasHighRating returns true for high rated items`() {
        val item = BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.MOVIE,
            communityRating = 7.5f,
            name = "Test Item",
        )
        assertTrue(item.hasHighRating())
    }

    @Test
    fun `hasHighRating returns false for low rated items`() {
        val item = BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.MOVIE,
            communityRating = 6.5f,
            name = "Test Item",
        )
        assertFalse(item.hasHighRating())
    }

    @Test
    fun `hasHighRating returns false for items with no rating`() {
        val item = BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.MOVIE,
            communityRating = null,
            name = "Test Item",
        )
        assertFalse(item.hasHighRating())
    }

    @Test
    fun `getItemKey returns valid key`() {
        val itemId = UUID.randomUUID()
        val item = BaseItemDto(
            id = itemId,
            type = BaseItemKind.MOVIE,
            name = "Test Item",
        )
        val key = item.getItemKey()

        assertTrue(key.isNotEmpty())
        assertTrue(key.contains("MOVIE"))
        assertTrue(key.contains(itemId.toString()))
    }

    @Test
    fun `generateItemKey creates valid key`() {
        val key = generateItemKey("MOVIE", "123")
        assertEquals("MOVIE_123", key)
    }

    @Test
    fun `isWatched returns correct value`() {
        // Test item with no userData (default false)
        val noDataItem = BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.MOVIE,
            userData = null,
            name = "Test Item",
        )
        assertFalse(noDataItem.isWatched())
    }

    @Test
    fun `getFormattedDuration returns correct format`() {
        // Test with hours
        val itemWithHours = BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.MOVIE,
            runTimeTicks = 72000000000, // 2 hours
            name = "Test Item",
        )
        assertEquals("2h 0m", itemWithHours.getFormattedDuration())

        // Test with minutes only
        val itemWithMinutes = BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.MOVIE,
            runTimeTicks = 18000000000, // 30 minutes
            name = "Test Item",
        )
        assertEquals("30m", itemWithMinutes.getFormattedDuration())

        // Test with null runTimeTicks
        val itemWithoutDuration = BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.MOVIE,
            runTimeTicks = null,
            name = "Test Item",
        )
        assertNull(itemWithoutDuration.getFormattedDuration())
    }
}
