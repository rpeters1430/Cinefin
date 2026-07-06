package com.rpeters.jellyfin.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlayerMetadataManagerTest {

    private data class FakeChapter(
        val name: String? = null,
        val startPositionTicks: Long? = null,
        val markerType: Any? = null,
    )

    private enum class FakeMarkerType {
        IntroStart,
        IntroEnd,
        CreditsStart,
        CreditsEnd,
    }

    @Test
    fun extractSkipMarkersFromChapters_usesMarkerTypeWhenNamesAreMissing() {
        val markers = extractSkipMarkersFromChapters(
            listOf(
                FakeChapter(name = null, startPositionTicks = 60_000_000L, markerType = FakeMarkerType.IntroStart),
                FakeChapter(name = null, startPositionTicks = 90_000_000L, markerType = FakeMarkerType.IntroEnd),
                FakeChapter(name = null, startPositionTicks = 500_000_000L, markerType = FakeMarkerType.CreditsStart),
                FakeChapter(name = null, startPositionTicks = 560_000_000L, markerType = FakeMarkerType.CreditsEnd),
            ),
        )

        assertEquals(6_000L, markers.introStartMs)
        assertEquals(9_000L, markers.introEndMs)
        assertEquals(50_000L, markers.outroStartMs)
        assertEquals(56_000L, markers.outroEndMs)
    }

    @Test
    fun extractSkipMarkersFromChapters_fallsBackToNameMatching() {
        val markers = extractSkipMarkersFromChapters(
            listOf(
                FakeChapter(name = "Intro", startPositionTicks = 20_000_000L),
                FakeChapter(name = "Scene 1", startPositionTicks = 40_000_000L),
                FakeChapter(name = "Credits", startPositionTicks = 500_000_000L),
                FakeChapter(name = "Post credits", startPositionTicks = 540_000_000L),
            ),
        )

        assertEquals(2_000L, markers.introStartMs)
        assertEquals(4_000L, markers.introEndMs)
        assertEquals(50_000L, markers.outroStartMs)
        assertEquals(54_000L, markers.outroEndMs)
    }
}
