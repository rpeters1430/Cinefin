package com.rpeters.jellyfin.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Test suite for RatingUtils - specifically the normalizeOfficialRating function.
 * Tests various rating formats from different metadata providers and locales.
 */
class RatingUtilsTest {

    @Test
    fun `normalizeOfficialRating handles null and blank inputs`() {
        assertNull(normalizeOfficialRating(null))
        assertNull(normalizeOfficialRating(""))
        assertNull(normalizeOfficialRating("   "))
    }

    @Test
    fun `normalizeOfficialRating keeps known US ratings as-is`() {
        // Movie ratings
        assertEquals("G", normalizeOfficialRating("G"))
        assertEquals("PG", normalizeOfficialRating("PG"))
        assertEquals("PG-13", normalizeOfficialRating("PG-13"))
        assertEquals("R", normalizeOfficialRating("R"))
        assertEquals("NC-17", normalizeOfficialRating("NC-17"))

        // TV ratings
        assertEquals("TV-Y", normalizeOfficialRating("TV-Y"))
        assertEquals("TV-Y7", normalizeOfficialRating("TV-Y7"))
        assertEquals("TV-G", normalizeOfficialRating("TV-G"))
        assertEquals("TV-PG", normalizeOfficialRating("TV-PG"))
        assertEquals("TV-14", normalizeOfficialRating("TV-14"))
        assertEquals("TV-MA", normalizeOfficialRating("TV-MA"))

        // Not Rated / Unrated
        assertEquals("NR", normalizeOfficialRating("NR"))
        assertEquals("UR", normalizeOfficialRating("UR"))
    }

    @Test
    fun `normalizeOfficialRating handles case insensitivity`() {
        assertEquals("TV-MA", normalizeOfficialRating("tv-ma"))
        assertEquals("TV-MA", normalizeOfficialRating("Tv-Ma"))
        assertEquals("PG-13", normalizeOfficialRating("pg-13"))
        assertEquals("NR", normalizeOfficialRating("not rated"))
        assertEquals("NR", normalizeOfficialRating("unrated"))
    }

    @Test
    fun `normalizeOfficialRating handles rating aliases`() {
        assertEquals("PG-13", normalizeOfficialRating("PG13"))
        assertEquals("NC-17", normalizeOfficialRating("NC17"))
        assertEquals("NR", normalizeOfficialRating("NOT RATED"))
        assertEquals("NR", normalizeOfficialRating("UNRATED"))
    }

    @Test
    fun `normalizeOfficialRating strips country prefixes and preserves known ratings`() {
        // US prefix
        assertEquals("TV-MA", normalizeOfficialRating("US-TV-MA"))
        assertEquals("PG-13", normalizeOfficialRating("US-PG-13"))

        // German ratings
        assertEquals("16+", normalizeOfficialRating("DE-16"))
        assertEquals("12+", normalizeOfficialRating("DE-12"))

        // UK ratings
        assertEquals("15+", normalizeOfficialRating("UK-15"))
        assertEquals("18+", normalizeOfficialRating("GB-18"))

        // French ratings
        assertEquals("12+", normalizeOfficialRating("FR-12"))

        // Other countries
        assertEquals("16+", normalizeOfficialRating("ES-16"))
        assertEquals("16+", normalizeOfficialRating("IT-16"))
        assertEquals("16+", normalizeOfficialRating("NL-16"))
        assertEquals("15+", normalizeOfficialRating("AU-15"))
        assertEquals("14+", normalizeOfficialRating("CA-14"))
    }

    @Test
    fun `normalizeOfficialRating strips system prefixes with various separators`() {
        // FSK (German film rating system) - dash separator
        assertEquals("16+", normalizeOfficialRating("FSK-16"))
        assertEquals("12+", normalizeOfficialRating("FSK-12"))
        assertEquals("18+", normalizeOfficialRating("FSK-18"))

        // FSK with space separator
        assertEquals("6+", normalizeOfficialRating("FSK 6"))

        // PEGI (European game rating system) - space and dash separators
        assertEquals("16+", normalizeOfficialRating("PEGI 16"))
        assertEquals("18+", normalizeOfficialRating("PEGI-18"))
        assertEquals("12+", normalizeOfficialRating("PEGI 12"))

        // BBFC (British film rating) - dash and space separators
        assertEquals("15+", normalizeOfficialRating("BBFC-15"))
        assertEquals("18+", normalizeOfficialRating("BBFC 18"))
    }

    @Test
    fun `normalizeOfficialRating converts numeric ratings to standardized format`() {
        // Numeric ratings with leading dash (the bug mentioned in the issue)
        assertEquals("16+", normalizeOfficialRating("-16"))
        assertEquals("18+", normalizeOfficialRating("-18"))
        assertEquals("12+", normalizeOfficialRating("-12"))

        // Plain numeric ratings
        assertEquals("16+", normalizeOfficialRating("16"))
        assertEquals("18+", normalizeOfficialRating("18"))
        assertEquals("12+", normalizeOfficialRating("12"))
        assertEquals("6+", normalizeOfficialRating("6"))
        assertEquals("0+", normalizeOfficialRating("0"))

        // Numeric ratings already with plus sign
        assertEquals("16+", normalizeOfficialRating("16+"))
        assertEquals("18+", normalizeOfficialRating("18+"))
    }

    @Test
    fun `normalizeOfficialRating handles complex rating formats`() {
        // Multiple prefix combinations
        assertEquals("16+", normalizeOfficialRating("DE-FSK-16"))

        // Numeric with spaces
        assertEquals("16+", normalizeOfficialRating("  16  "))
        assertEquals("16+", normalizeOfficialRating("  -16  "))
    }

    @Test
    fun `normalizeOfficialRating handles edge cases`() {
        // Single digit ages
        assertEquals("7+", normalizeOfficialRating("7"))
        assertEquals("0+", normalizeOfficialRating("0"))

        // Two-digit ages
        assertEquals("99+", normalizeOfficialRating("99"))

        // Unknown ratings (should be preserved as-is after cleanup)
        assertEquals("XXX", normalizeOfficialRating("XXX"))
        assertEquals("APPROVED", normalizeOfficialRating("APPROVED"))
    }

    @Test
    fun `normalizeOfficialRating handles real-world examples from the issue`() {
        // Examples mentioned in the issue
        assertEquals("TV-MA", normalizeOfficialRating("TV-MA")) // Already fine
        assertEquals("16+", normalizeOfficialRating("DE-16")) // German rating
        assertEquals("16+", normalizeOfficialRating("FSK-16")) // German film rating
        assertEquals("16+", normalizeOfficialRating("PEGI 16")) // European game rating
        assertEquals("16+", normalizeOfficialRating("-16")) // Bug: raw numeric with dash
        assertEquals("16+", normalizeOfficialRating("16")) // Plain numeric
    }
}
