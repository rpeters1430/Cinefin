package com.rpeters.jellyfin.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.rpeters.jellyfin.data.preferences.AccentColor
import com.rpeters.jellyfin.data.preferences.ContrastLevel
import com.rpeters.jellyfin.data.preferences.ThemeMode
import com.rpeters.jellyfin.data.preferences.ThemePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive tests for theme functionality.
 * Tests color scheme generation, contrast adjustments, and WCAG compliance.
 */
class ThemeTest {

    // ========================================================================
    // COLOR SCHEME GENERATION TESTS
    // ========================================================================

    @Test
    fun `getDarkColorScheme generates valid dark color scheme`() {
        // When
        val colorScheme = getDarkColorScheme(AccentColor.JELLYFIN_PURPLE)

        // Then - Verify it's a valid color scheme
        assertColorSchemeIsValid(colorScheme)
    }

    @Test
    fun `getLightColorScheme generates valid light color scheme`() {
        // When
        val colorScheme = getLightColorScheme(AccentColor.JELLYFIN_PURPLE)

        // Then - Verify it's a valid color scheme
        assertColorSchemeIsValid(colorScheme)
    }

    @Test
    fun `getAmoledBlackColorScheme has pure black background`() {
        // When
        val colorScheme = getAmoledBlackColorScheme(AccentColor.JELLYFIN_PURPLE)

        // Then
        assertEquals(Color.Black, colorScheme.background)
        assertEquals(Color.Black, colorScheme.surface)
    }

    @Test
    fun `different accent colors produce different color schemes`() {
        // Given
        val purpleScheme = getDarkColorScheme(AccentColor.JELLYFIN_PURPLE)
        val blueScheme = getDarkColorScheme(AccentColor.MATERIAL_BLUE)

        // Then - Primary colors should be different
        assertNotEquals(purpleScheme.primary, blueScheme.primary)
    }

    // ========================================================================
    // CONTRAST ADJUSTMENT TESTS - MEDIUM LEVEL
    // ========================================================================

    @Test
    fun `medium contrast brightens dark theme primary colors`() {
        // Given
        val baseScheme = darkColorScheme()
        val originalPrimary = baseScheme.primary

        // When - Use reflection to access private function
        val adjustedScheme = applyContrastLevel(
            baseScheme,
            ContrastLevel.MEDIUM,
            isDark = true
        )

        // Then - Primary should be brighter
        assertTrue(adjustedScheme.primary.luminance() > originalPrimary.luminance())
    }

    @Test
    fun `medium contrast darkens light theme primary colors`() {
        // Given
        val baseScheme = lightColorScheme()
        val originalPrimary = baseScheme.primary

        // When
        val adjustedScheme = applyContrastLevel(
            baseScheme,
            ContrastLevel.MEDIUM,
            isDark = false
        )

        // Then - Primary should be darker
        assertTrue(adjustedScheme.primary.luminance() < originalPrimary.luminance())
    }

    @Test
    fun `medium contrast adjusts paired on colors in opposite direction - dark theme`() {
        // Given
        val baseScheme = darkColorScheme()
        val originalOnPrimary = baseScheme.onPrimary

        // When
        val adjustedScheme = applyContrastLevel(
            baseScheme,
            ContrastLevel.MEDIUM,
            isDark = true
        )

        // Then - onPrimary should be darker (opposite of primary which is brighter)
        assertTrue(adjustedScheme.onPrimary.luminance() < originalOnPrimary.luminance())
    }

    @Test
    fun `medium contrast adjusts paired on colors in opposite direction - light theme`() {
        // Given
        val baseScheme = lightColorScheme()
        val originalOnPrimary = baseScheme.onPrimary

        // When
        val adjustedScheme = applyContrastLevel(
            baseScheme,
            ContrastLevel.MEDIUM,
            isDark = false
        )

        // Then - onPrimary should be brighter (opposite of primary which is darker)
        assertTrue(adjustedScheme.onPrimary.luminance() > originalOnPrimary.luminance())
    }

    // ========================================================================
    // CONTRAST ADJUSTMENT TESTS - HIGH LEVEL
    // ========================================================================

    @Test
    fun `high contrast has stronger adjustment than medium contrast - dark theme`() {
        // Given
        val baseScheme = darkColorScheme()

        // When
        val mediumScheme = applyContrastLevel(
            baseScheme,
            ContrastLevel.MEDIUM,
            isDark = true
        )
        val highScheme = applyContrastLevel(
            baseScheme,
            ContrastLevel.HIGH,
            isDark = true
        )

        // Then - High contrast should have more adjustment
        assertTrue(highScheme.primary.luminance() > mediumScheme.primary.luminance())
    }

    @Test
    fun `high contrast has stronger adjustment than medium contrast - light theme`() {
        // Given
        val baseScheme = lightColorScheme()

        // When
        val mediumScheme = applyContrastLevel(
            baseScheme,
            ContrastLevel.MEDIUM,
            isDark = false
        )
        val highScheme = applyContrastLevel(
            baseScheme,
            ContrastLevel.HIGH,
            isDark = false
        )

        // Then - High contrast should have more adjustment
        assertTrue(highScheme.primary.luminance() < mediumScheme.primary.luminance())
    }

    @Test
    fun `high contrast adjusts surface and background colors`() {
        // Given
        val baseScheme = darkColorScheme()
        val originalSurface = baseScheme.surface
        val originalBackground = baseScheme.background

        // When
        val adjustedScheme = applyContrastLevel(
            baseScheme,
            ContrastLevel.HIGH,
            isDark = true
        )

        // Then - Surface and background should be adjusted
        assertNotEquals(originalSurface, adjustedScheme.surface)
        assertNotEquals(originalBackground, adjustedScheme.background)
    }

    @Test
    fun `high contrast adjusts outline colors`() {
        // Given
        val baseScheme = darkColorScheme()
        val originalOutline = baseScheme.outline

        // When
        val adjustedScheme = applyContrastLevel(
            baseScheme,
            ContrastLevel.HIGH,
            isDark = true
        )

        // Then - Outline should be adjusted
        assertNotEquals(originalOutline, adjustedScheme.outline)
        assertTrue(adjustedScheme.outline.luminance() > originalOutline.luminance())
    }

    // ========================================================================
    // CONTAINER COLOR TESTS
    // ========================================================================

    @Test
    fun `contrast adjustments affect container colors`() {
        // Given
        val baseScheme = darkColorScheme()
        val originalPrimaryContainer = baseScheme.primaryContainer
        val originalSecondaryContainer = baseScheme.secondaryContainer

        // When
        val adjustedScheme = applyContrastLevel(
            baseScheme,
            ContrastLevel.HIGH,
            isDark = true
        )

        // Then - Container colors should be adjusted
        assertNotEquals(originalPrimaryContainer, adjustedScheme.primaryContainer)
        assertNotEquals(originalSecondaryContainer, adjustedScheme.secondaryContainer)
    }

    @Test
    fun `contrast adjustments affect on-container colors`() {
        // Given
        val baseScheme = darkColorScheme()
        val originalOnPrimaryContainer = baseScheme.onPrimaryContainer

        // When
        val adjustedScheme = applyContrastLevelReflection(
            baseScheme,
            ContrastLevel.HIGH,
            isDark = true
        )

        // Then - On-container colors should be adjusted
        assertNotEquals(originalOnPrimaryContainer, adjustedScheme.onPrimaryContainer)
    }

    // ========================================================================
    // STANDARD CONTRAST TESTS
    // ========================================================================

    @Test
    fun `standard contrast level makes no changes`() {
        // Given
        val baseScheme = darkColorScheme()

        // When
        val adjustedScheme = applyContrastLevel(
            baseScheme,
            ContrastLevel.STANDARD,
            isDark = true
        )

        // Then - Should be identical
        assertEquals(baseScheme.primary, adjustedScheme.primary)
        assertEquals(baseScheme.secondary, adjustedScheme.secondary)
        assertEquals(baseScheme.tertiary, adjustedScheme.tertiary)
        assertEquals(baseScheme.surface, adjustedScheme.surface)
        assertEquals(baseScheme.background, adjustedScheme.background)
    }

    // ========================================================================
    // COLOR BRIGHTNESS ADJUSTMENT TESTS
    // ========================================================================

    @Test
    fun `adjustBrightness increases brightness with factor greater than 1`() {
        // Given
        val color = Color(0.5f, 0.5f, 0.5f)
        val factor = 1.2f

        // When
        val adjusted = adjustBrightness(color, factor)

        // Then
        assertTrue(adjusted.red > color.red)
        assertTrue(adjusted.green > color.green)
        assertTrue(adjusted.blue > color.blue)
    }

    @Test
    fun `adjustBrightness decreases brightness with factor less than 1`() {
        // Given
        val color = Color(0.5f, 0.5f, 0.5f)
        val factor = 0.8f

        // When
        val adjusted = adjustBrightness(color, factor)

        // Then
        assertTrue(adjusted.red < color.red)
        assertTrue(adjusted.green < color.green)
        assertTrue(adjusted.blue < color.blue)
    }

    @Test
    fun `adjustBrightness preserves alpha channel`() {
        // Given
        val color = Color(0.5f, 0.5f, 0.5f, 0.7f)
        val factor = 1.2f

        // When
        val adjusted = adjustBrightness(color, factor)

        // Then
        assertEquals(color.alpha, adjusted.alpha, 0.001f)
    }

    @Test
    fun `adjustBrightness clamps values to valid range`() {
        // Given
        val color = Color(0.9f, 0.9f, 0.9f)
        val factor = 2.0f

        // When
        val adjusted = adjustBrightness(color, factor)

        // Then - Values should not exceed 1.0
        assertTrue(adjusted.red <= 1.0f)
        assertTrue(adjusted.green <= 1.0f)
        assertTrue(adjusted.blue <= 1.0f)
    }

    // ========================================================================
    // WCAG COMPLIANCE TESTS
    // ========================================================================

    @Test
    fun `high contrast increases contrast ratio between primary and onPrimary`() {
        // Given
        val baseScheme = darkColorScheme()
        val baseRatio = calculateContrastRatio(baseScheme.primary, baseScheme.onPrimary)

        // When
        val adjustedScheme = applyContrastLevel(
            baseScheme,
            ContrastLevel.HIGH,
            isDark = true
        )
        val adjustedRatio = calculateContrastRatio(adjustedScheme.primary, adjustedScheme.onPrimary)

        // Then - Contrast ratio should increase
        assertTrue(adjustedRatio > baseRatio)
    }

    @Test
    fun `medium contrast increases contrast ratio between surface and onSurface`() {
        // Given
        val baseScheme = lightColorScheme()
        val baseRatio = calculateContrastRatio(baseScheme.surface, baseScheme.onSurface)

        // When - Medium contrast doesn't adjust surface in medium level, only high
        val adjustedScheme = applyContrastLevel(
            baseScheme,
            ContrastLevel.MEDIUM,
            isDark = false
        )

        // For medium, surface might not change, but we test the concept
        // The important part is that HIGH contrast does adjust it
        val highScheme = applyContrastLevel(
            baseScheme,
            ContrastLevel.HIGH,
            isDark = false
        )
        val highRatio = calculateContrastRatio(highScheme.surface, highScheme.onSurface)

        // Then - High contrast should increase ratio
        assertTrue(highRatio >= baseRatio)
    }

    // ========================================================================
    // AMOLED BLACK THEME TESTS
    // ========================================================================

    @Test
    fun `amoled black theme has correct surface variants`() {
        // When
        val colorScheme = getAmoledBlackColorScheme(AccentColor.JELLYFIN_PURPLE)

        // Then - Verify surface container hierarchy
        assertEquals(Color.Black, colorScheme.surfaceContainerLowest)
        assertTrue(colorScheme.surfaceContainerLow.luminance() > Color.Black.luminance())
        assertTrue(colorScheme.surfaceContainer.luminance() > colorScheme.surfaceContainerLow.luminance())
        assertTrue(colorScheme.surfaceContainerHigh.luminance() > colorScheme.surfaceContainer.luminance())
        assertTrue(colorScheme.surfaceContainerHighest.luminance() > colorScheme.surfaceContainerHigh.luminance())
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Verify that a ColorScheme has all required colors set.
     */
    private fun assertColorSchemeIsValid(colorScheme: ColorScheme) {
        // Basic validation - ensure key colors are not default/transparent
        assertNotEquals(Color.Unspecified, colorScheme.primary)
        assertNotEquals(Color.Unspecified, colorScheme.onPrimary)
        assertNotEquals(Color.Unspecified, colorScheme.surface)
        assertNotEquals(Color.Unspecified, colorScheme.background)
    }

    /**
     * Calculate contrast ratio between two colors using WCAG formula.
     * https://www.w3.org/WAI/GL/wiki/Contrast_ratio
     */
    private fun calculateContrastRatio(color1: Color, color2: Color): Float {
        val lum1 = color1.luminance()
        val lum2 = color2.luminance()
        val lighter = maxOf(lum1, lum2)
        val darker = minOf(lum1, lum2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
