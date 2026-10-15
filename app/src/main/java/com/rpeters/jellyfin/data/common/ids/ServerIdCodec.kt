package com.rpeters.jellyfin.data.common.ids

import java.util.UUID

/**
 * Emby and Plex identify library items with numeric strings ("1035"), while the Jellyfin SDK
 * models the app uses type every item ID as a [UUID]. A codec maps a numeric server ID to a
 * recognisable UUID and back, so those items can live in the SDK models.
 *
 * Each codec stamps a 4-character ASCII [marker] into the top of the UUID, so IDs from different
 * sources never collide and can be told apart: `EMBY` for Emby items, `PLEX` for Plex ratingKeys
 * and part IDs, `PLXT` for Plex tag IDs (people, genres).
 *
 * Encode when server JSON comes in; decode wherever an ID goes back out to the server.
 * IDs that are already GUIDs (Emby user and server IDs) are left alone in both directions.
 *
 * The companion's `encode` is the Emby codec, kept for the existing Emby callers; its `decode`,
 * `isEncoded` and `decodeAllIn` accept an ID from any marker.
 */
class ServerIdCodec private constructor(val marker: String) {
    private val markerMsb: Long = marker.toByteArray(Charsets.US_ASCII).fold(0L) { acc, byte ->
        (acc shl 8) or (byte.toLong() and 0xFF)
    } shl 32 or VERSION_4_BITS

    // The fixed first three groups of every UUID this codec makes, e.g. "454d4259-0000-4000-".
    internal val prefix: String = "%08x-0000-4000-".format(markerMsb ushr 32)

    /** @return the UUID for a numeric server ID, or null when [serverId] is not a non-negative number. */
    fun encode(serverId: String): UUID? {
        val number = serverId.toLongOrNull() ?: return null
        if (number < 0 || number > NUMBER_MASK) return null
        return UUID(markerMsb, VARIANT_BITS or number)
    }

    fun isEncoded(id: UUID): Boolean =
        id.mostSignificantBits == markerMsb && (id.leastSignificantBits and NUMBER_MASK.inv()) == VARIANT_BITS

    /** @return the server ID: the original number when [id] is this codec's, or the UUID unchanged. */
    fun decode(id: UUID): String =
        if (isEncoded(id)) (id.leastSignificantBits and NUMBER_MASK).toString() else id.toString()

    override fun toString(): String = "ServerIdCodec($marker)"

    companion object {
        // A version-4 nibble so the result is a well-formed UUID.
        private const val VERSION_4_BITS = 0x4000L

        // The variant bits (10xx) that RFC 4122 expects at the top of the low half.
        private const val VARIANT_BITS = Long.MIN_VALUE
        private const val NUMBER_MASK = 0x3FFFFFFFFFFFFFFFL

        /** Emby item IDs. */
        val EMBY = ServerIdCodec("EMBY")

        /** Plex ratingKeys and media part IDs. */
        val PLEX = ServerIdCodec("PLEX")

        /** Plex tag IDs: people, genres, studios. */
        val PLEX_TAG = ServerIdCodec("PLXT")

        private val codecs = listOf(EMBY, PLEX, PLEX_TAG)

        private val ENCODED_ID = Regex("[0-9a-f]{8}-0000-4000-[89ab][0-9a-f]{3}-[0-9a-f]{12}", RegexOption.IGNORE_CASE)

        private fun codecFor(id: UUID): ServerIdCodec? = codecs.firstOrNull { it.isEncoded(id) }

        /** The Emby codec's [encode]. */
        fun encode(embyId: String): UUID? = EMBY.encode(embyId)

        /** True when [id] was made by any codec. */
        fun isEncoded(id: UUID): Boolean = codecFor(id) != null

        /** @return the server ID for an ID made by any codec, or the UUID unchanged. */
        fun decode(id: UUID): String = codecFor(id)?.decode(id) ?: id.toString()

        /** As [decode], for IDs the app carries around as strings. Non-UUID input is returned as is. */
        fun decode(id: String): String {
            val uuid = runCatching { UUID.fromString(id) }.getOrNull() ?: return id
            return codecFor(uuid)?.decode(uuid) ?: id
        }

        /**
         * Replaces every encoded ID inside [text] (a URL, typically) with its server number, for
         * every marker. Text without encoded IDs is returned as is.
         */
        fun decodeAllIn(text: String): String {
            if (codecs.none { text.contains(it.prefix, ignoreCase = true) }) return text
            return ENCODED_ID.replace(text) { match -> decode(match.value) }
        }
    }
}
