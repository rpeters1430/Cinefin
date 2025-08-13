package com.example.jellyfinandroid.utils

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.*
import org.junit.Test
import java.util.*

/**
 * Test suite for RatingCategory enum.
 */
class RatingCategoryTest {

    @Test
    fun `RatingCategory enum has correct values`() {
        val excellent = RatingCategory.EXCELLENT
        assertEquals("Excellent", excellent.displayName)
        assertEquals("#4CAF50", excellent.color)

        val high = RatingCategory.HIGH
        assertEquals("High", high.displayName)
        assertEquals("#8BC34A", high.color)

        val good = RatingCategory.GOOD
        assertEquals("Good", good.displayName)
        assertEquals("#FFC107", good.color)

        val average = RatingCategory.AVERAGE
        assertEquals("Average", average.displayName)
        assertEquals("#FF9800", average.color)

        val poor = RatingCategory.POOR
        assertEquals("Poor", poor.displayName)
        assertEquals("#F44336", poor.color)
    }

    @Test
    fun `getRatingCategory returns correct categories`() {
        val itemExcellent = BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.MOVIE,
            communityRating = 9.0f,
            name = "Test Item",
        )
        assertEquals(RatingCategory.EXCELLENT, itemExcellent.getRatingCategory())

        val itemHigh = BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.MOVIE,
            communityRating = 8.0f,
            name = "Test Item",
        )
        assertEquals(RatingCategory.HIGH, itemHigh.getRatingCategory())

        val itemGood = BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.MOVIE,
            communityRating = 6.5f,
            name = "Test Item",
        )
        assertEquals(RatingCategory.GOOD, itemGood.getRatingCategory())

        val itemAverage = BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.MOVIE,
            communityRating = 5.5f,
            name = "Test Item",
        )
        assertEquals(RatingCategory.AVERAGE, itemAverage.getRatingCategory())

        val itemPoor = BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.MOVIE,
            communityRating = 3.0f,
            name = "Test Item",
        )
        assertEquals(RatingCategory.POOR, itemPoor.getRatingCategory())
    }
}
