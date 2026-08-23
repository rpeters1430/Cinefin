package com.rpeters.jellyfin.ui.components.immersive

import org.junit.Assert.assertEquals
import org.junit.Test

class ParallaxHeroSectionTest {
    @Test
    fun `normalized scroll offset compares pixels with pixels`() {
        assertEquals(
            0.5f,
            normalizedParallaxScrollOffset(scrollOffsetPx = 300, heroHeightPx = 600f),
            0.0001f,
        )
    }

    @Test
    fun `normalized scroll offset clamps overscroll to the hero range`() {
        assertEquals(
            1f,
            normalizedParallaxScrollOffset(scrollOffsetPx = 900, heroHeightPx = 600f),
            0.0001f,
        )
    }
}
