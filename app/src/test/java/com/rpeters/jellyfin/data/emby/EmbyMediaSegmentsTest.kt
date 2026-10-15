package com.rpeters.jellyfin.data.emby

import com.rpeters.jellyfin.data.common.ids.ServerIdCodec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbyMediaSegmentsTest {

    @Test
    fun fromItem_withIntroAndCreditsMarkers_returnsIntroAndOutroSegments() {
        val segments = EmbyMediaSegments.fromItem(fixture("episode_with_markers.json"), "2041")

        val intro = segments.single { it.type == MediaSegmentType.INTRO }
        assertEquals(900_000_000L, intro.startTicks)
        assertEquals(1_800_000_000L, intro.endTicks)
        val outro = segments.single { it.type == MediaSegmentType.OUTRO }
        assertEquals(25_200_000_000L, outro.startTicks)
        assertEquals(26_400_000_000L, outro.endTicks)
        assertEquals(ServerIdCodec.encode("2041"), intro.itemId)
    }

    @Test
    fun fromItem_isStableAcrossCalls() {
        val first = EmbyMediaSegments.fromItem(fixture("episode_with_markers.json"), "2041")
        val second = EmbyMediaSegments.fromItem(fixture("episode_with_markers.json"), "2041")

        assertEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun fromItem_withoutMarkers_returnsEmptyList() {
        assertTrue(EmbyMediaSegments.fromItem(fixture("episode_without_markers.json"), "2042").isEmpty())
    }

    @Test
    fun fromItem_withNoChaptersOrNonNumericId_returnsEmptyList() {
        assertTrue(EmbyMediaSegments.fromItem(JsonObject(emptyMap()), "2042").isEmpty())
        assertTrue(EmbyMediaSegments.fromItem(fixture("episode_with_markers.json"), "not-a-number").isEmpty())
    }

    @Test
    fun fromItem_introEndMissing_skipsIntroButKeepsCredits() {
        val item = Json.parseToJsonElement(
            """{"RunTimeTicks":1000,"Chapters":[{"StartPositionTicks":10,"MarkerType":"IntroStart"},""" +
                """{"StartPositionTicks":900,"MarkerType":"CreditsStart"}]}""",
        )

        val segments = EmbyMediaSegments.fromItem(item, "7")

        assertEquals(listOf(MediaSegmentType.OUTRO), segments.map { it.type })
    }

    private fun fixture(name: String): JsonElement {
        val text = requireNotNull(javaClass.classLoader?.getResource("emby/$name")) { "Missing fixture $name" }.readText()
        return Json.parseToJsonElement(text)
    }
}
