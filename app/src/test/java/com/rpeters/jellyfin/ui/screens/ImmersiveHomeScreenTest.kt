package com.rpeters.jellyfin.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveHomeScreenTest {

    @Test
    fun `shouldResetHomeScrollForLateHero_whenHeroAppearsNearTop_returnsTrue`() {
        assertTrue(
            shouldResetHomeScrollForLateHero(
                previousHasHero = false,
                currentHasHero = true,
                firstVisibleItemIndex = 1,
            ),
        )
    }

    @Test
    fun `shouldResetHomeScrollForLateHero_whenHeroAlreadyPresent_returnsFalse`() {
        assertFalse(
            shouldResetHomeScrollForLateHero(
                previousHasHero = true,
                currentHasHero = true,
                firstVisibleItemIndex = 0,
            ),
        )
    }

    @Test
    fun `shouldResetHomeScrollForLateHero_whenUserScrolledPastTopRows_returnsFalse`() {
        assertFalse(
            shouldResetHomeScrollForLateHero(
                previousHasHero = false,
                currentHasHero = true,
                firstVisibleItemIndex = 2,
            ),
        )
    }
}
