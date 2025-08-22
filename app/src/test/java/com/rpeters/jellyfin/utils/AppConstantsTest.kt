package com.rpeters.jellyfin.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * Test suite for AppConstants.
 */
class AppConstantsTest {

    @Test
    fun `Rating constants have correct values`() {
        assertEquals(7.0, AppConstants.Rating.HIGH_RATING_THRESHOLD, 0.01)
        assertEquals(8.5, AppConstants.Rating.EXCELLENT_RATING_THRESHOLD, 0.01)
        assertEquals(6.0, AppConstants.Rating.GOOD_RATING_THRESHOLD, 0.01)
        assertEquals(5.0, AppConstants.Rating.AVERAGE_RATING_THRESHOLD, 0.01)
    }

    @Test
    fun `Security constants are defined`() {
        assertNotNull(AppConstants.Security.KEY_ALIAS)
        assertNotNull(AppConstants.Security.ENCRYPTION_TRANSFORMATION)
        assertTrue(AppConstants.Security.IV_LENGTH > 0)
    }

    @Test
    fun `Search constants have reasonable values`() {
        assertTrue(AppConstants.Search.SEARCH_DEBOUNCE_MS > 0)
    }

    @Test
    fun `Playback constants have correct values`() {
        assertEquals(5.0, AppConstants.Playback.RESUME_THRESHOLD_PERCENT, 0.01)
        assertEquals(90.0, AppConstants.Playback.WATCHED_THRESHOLD_PERCENT, 0.01)
    }
}
