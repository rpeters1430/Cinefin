package com.rpeters.jellyfin.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class RatingColorTest {

    @Test
    fun `getCommunityRatingColor returns green at and above 7`() {
        assertEquals(OfficialRatingGreen, getCommunityRatingColor(7.0f))
        assertEquals(OfficialRatingGreen, getCommunityRatingColor(9.5f))
    }

    @Test
    fun `getCommunityRatingColor returns amber between 5 and 6point9`() {
        assertEquals(OfficialRatingAmber, getCommunityRatingColor(5.0f))
        assertEquals(OfficialRatingAmber, getCommunityRatingColor(6.9f))
    }

    @Test
    fun `getCommunityRatingColor returns red below 5`() {
        assertEquals(OfficialRatingRed, getCommunityRatingColor(4.9f))
        assertEquals(OfficialRatingRed, getCommunityRatingColor(0.0f))
    }

    @Test
    fun `getCriticRatingColor returns green at and above 60`() {
        assertEquals(OfficialRatingGreen, getCriticRatingColor(60f))
        assertEquals(OfficialRatingGreen, getCriticRatingColor(100f))
    }

    @Test
    fun `getCriticRatingColor returns red below 60`() {
        assertEquals(OfficialRatingRed, getCriticRatingColor(59.9f))
        assertEquals(OfficialRatingRed, getCriticRatingColor(0f))
    }
}
