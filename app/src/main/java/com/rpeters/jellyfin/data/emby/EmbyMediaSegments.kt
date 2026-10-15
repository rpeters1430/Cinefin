package com.rpeters.jellyfin.data.emby

import com.rpeters.jellyfin.data.common.ids.ServerIdCodec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import java.util.UUID

/**
 * Emby has no media segments API. It marks intros and credits on the item's chapters instead,
 * with a `MarkerType` of `IntroStart`, `IntroEnd` or `CreditsStart` and positions in ticks.
 * This turns those markers into the segments Jellyfin's `/MediaSegments/{itemId}` returns, so the
 * player's skip-intro and skip-credits buttons work the same on both servers.
 */
internal object EmbyMediaSegments {
    private const val INTRO_START = "IntroStart"
    private const val INTRO_END = "IntroEnd"
    private const val CREDITS_START = "CreditsStart"

    /**
     * @param item the Emby item JSON, requested with `Fields=Chapters`.
     * @param embyItemId the item's numeric Emby ID.
     * @return an intro segment when both intro markers are present and in order, and a credits
     *   segment running from `CreditsStart` to the end of the item when its runtime is known.
     */
    fun fromItem(item: JsonElement, embyItemId: String): List<MediaSegmentDto> {
        val obj = item as? JsonObject ?: return emptyList()
        val itemUuid = ServerIdCodec.encode(embyItemId) ?: return emptyList()
        val chapters = (obj["Chapters"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        fun markerTicks(type: String): Long? = chapters
            .firstOrNull { (it["MarkerType"] as? JsonPrimitive)?.content.equals(type, ignoreCase = true) }
            ?.let { (it["StartPositionTicks"] as? JsonPrimitive)?.longOrNull }
        val runTimeTicks = (obj["RunTimeTicks"] as? JsonPrimitive)?.longOrNull

        val segments = mutableListOf<MediaSegmentDto>()
        val introStart = markerTicks(INTRO_START)
        val introEnd = markerTicks(INTRO_END)
        if (introStart != null && introEnd != null && introEnd > introStart) {
            segments += segment(itemUuid, MediaSegmentType.INTRO, introStart, introEnd)
        }
        val creditsStart = markerTicks(CREDITS_START)
        if (creditsStart != null && runTimeTicks != null && runTimeTicks > creditsStart) {
            segments += segment(itemUuid, MediaSegmentType.OUTRO, creditsStart, runTimeTicks)
        }
        return segments
    }

    // Stable per item and type, so the same segment keeps its ID across requests.
    private fun segment(itemId: UUID, type: MediaSegmentType, start: Long, end: Long) = MediaSegmentDto(
        id = UUID.nameUUIDFromBytes("$itemId/${type.serialName}".toByteArray()),
        itemId = itemId,
        type = type,
        startTicks = start,
        endTicks = end,
    )
}
