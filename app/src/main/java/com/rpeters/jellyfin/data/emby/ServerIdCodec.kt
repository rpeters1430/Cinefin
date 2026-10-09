package com.rpeters.jellyfin.data.emby

import java.util.UUID

/**
 * Emby identifies library items with numeric strings ("1035"), while the Jellyfin SDK models the
 * app uses type every item ID as a [UUID]. This maps a numeric Emby ID to a recognisable UUID and
 * back, so Emby items can live in the SDK models.
 *
 * Encode when Emby JSON comes in; decode wherever an ID goes back out to an Emby server.
 * IDs that are already GUIDs (Emby user and server IDs) are left alone in both directions.
 */
object ServerIdCodec {
    // "EMBY" in ASCII, then a version-4 nibble so the result is a well-formed UUID.
    private const val MARKER_MSB = 0x454D425900004000L

    // The variant bits (10xx) that RFC 4122 expects at the top of the low half.
    private const val VARIANT_BITS = Long.MIN_VALUE
    private const val NUMBER_MASK = 0x3FFFFFFFFFFFFFFFL

    /** @return the UUID for a numeric Emby ID, or null when [embyId] is not a non-negative number. */
    fun encode(embyId: String): UUID? {
        val number = embyId.toLongOrNull() ?: return null
        if (number < 0 || number > NUMBER_MASK) return null
        return UUID(MARKER_MSB, VARIANT_BITS or number)
    }

    fun isEncoded(id: UUID): Boolean =
        id.mostSignificantBits == MARKER_MSB && (id.leastSignificantBits and NUMBER_MASK.inv()) == VARIANT_BITS

    /** @return the Emby ID to send to the server: the original number, or the UUID unchanged. */
    fun decode(id: UUID): String =
        if (isEncoded(id)) (id.leastSignificantBits and NUMBER_MASK).toString() else id.toString()

    /**
     * Replaces every encoded ID inside [text] (a URL, typically) with its Emby number. Text
     * without encoded IDs is returned as is.
     */
    fun decodeAllIn(text: String): String {
        if (!text.contains(MARKER_PREFIX, ignoreCase = true)) return text
        return ENCODED_ID.replace(text) { match -> decode(match.value) }
    }

    // The fixed first three groups of every encoded UUID, for a cheap containment check.
    private const val MARKER_PREFIX = "454d4259-0000-4000-"
    private val ENCODED_ID = Regex("454d4259-0000-4000-[89ab][0-9a-f]{3}-[0-9a-f]{12}", RegexOption.IGNORE_CASE)

    /** As [decode], for IDs the app carries around as strings. Non-UUID input is returned as is. */
    fun decode(id: String): String {
        val uuid = runCatching { UUID.fromString(id) }.getOrNull() ?: return id
        return if (isEncoded(uuid)) decode(uuid) else id
    }
}
