package com.rpeters.jellyfin.data.common.ids

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ServerIdCodecTest {

    @Test
    fun encodeThenDecode_numericIds_roundTrip() {
        listOf("0", "1", "1035", "15941", "4294967296", "4611686018427387903").forEach { embyId ->
            val encoded = ServerIdCodec.encode(embyId)

            assertEquals(embyId, encoded?.let(ServerIdCodec::decode))
        }
    }

    @Test
    fun encode_numericId_producesWellFormedVersion4Uuid() {
        val encoded = ServerIdCodec.encode("1035")!!

        assertEquals(4, encoded.version())
        assertEquals(2, encoded.variant())
        assertEquals(encoded, UUID.fromString(encoded.toString()))
    }

    @Test
    fun encode_differentIds_produceDifferentUuids() {
        assertNotEquals(ServerIdCodec.encode("1035"), ServerIdCodec.encode("1036"))
    }

    @Test
    fun encode_nonNumericOrOutOfRange_returnsNull() {
        listOf("", "abc", "-1", "mediasource_1035", "e96573aacb144a45b3c4585e3e5071c7", "4611686018427387904")
            .forEach { assertNull(it, ServerIdCodec.encode(it)) }
    }

    @Test
    fun isEncoded_distinguishesEmbyIdsFromOrdinaryUuids() {
        assertTrue(ServerIdCodec.isEncoded(ServerIdCodec.encode("1035")!!))
        assertFalse(ServerIdCodec.isEncoded(UUID.fromString("e96573aa-cb14-4a45-b3c4-585e3e5071c7")))
        assertFalse(ServerIdCodec.isEncoded(UUID(0L, 0L)))
    }

    @Test
    fun decode_ordinaryUuid_isReturnedUnchanged() {
        val userId = UUID.fromString("e96573aa-cb14-4a45-b3c4-585e3e5071c7")

        assertEquals(userId.toString(), ServerIdCodec.decode(userId))
    }

    @Test
    fun decodeString_handlesEncodedOrdinaryAndNonUuidInput() {
        val encoded = ServerIdCodec.encode("11032")!!.toString()

        assertEquals("11032", ServerIdCodec.decode(encoded))
        assertEquals("e96573aa-cb14-4a45-b3c4-585e3e5071c7", ServerIdCodec.decode("e96573aa-cb14-4a45-b3c4-585e3e5071c7"))
        assertEquals("mediasource_11032", ServerIdCodec.decode("mediasource_11032"))
        assertEquals("11032", ServerIdCodec.decode("11032"))
    }

    @Test
    fun plexAndTagCodecs_roundTripAndDoNotCollideWithEmby() {
        val emby = ServerIdCodec.EMBY.encode("1035")!!
        val plex = ServerIdCodec.PLEX.encode("1035")!!
        val tag = ServerIdCodec.PLEX_TAG.encode("1035")!!

        assertEquals(3, setOf(emby, plex, tag).size)
        assertEquals("1035", ServerIdCodec.PLEX.decode(plex))
        assertEquals("1035", ServerIdCodec.PLEX_TAG.decode(tag))
        assertTrue(plex.toString().startsWith("504c4558-0000-4000-"))
        assertTrue(tag.toString().startsWith("504c5854-0000-4000-"))
        assertEquals(4, plex.version())
        assertEquals(2, tag.variant())
    }

    @Test
    fun markerCodec_onlyDecodesItsOwnIds() {
        val plex = ServerIdCodec.PLEX.encode("77")!!

        assertFalse(ServerIdCodec.EMBY.isEncoded(plex))
        assertEquals(plex.toString(), ServerIdCodec.EMBY.decode(plex))
        assertEquals(ServerIdCodec.encode("77"), ServerIdCodec.EMBY.encode("77"))
    }

    @Test
    fun companionDecode_acceptsEveryMarker() {
        assertTrue(ServerIdCodec.isEncoded(ServerIdCodec.PLEX.encode("12")!!))
        assertEquals("12", ServerIdCodec.decode(ServerIdCodec.PLEX.encode("12")!!))
        assertEquals("34", ServerIdCodec.decode(ServerIdCodec.PLEX_TAG.encode("34")!!.toString()))
    }

    @Test
    fun decodeAllIn_replacesIdsOfEveryMarkerAndLeavesOtherTextAlone() {
        val emby = ServerIdCodec.EMBY.encode("1035")
        val plex = ServerIdCodec.PLEX.encode("4410")
        val tag = ServerIdCodec.PLEX_TAG.encode("9")
        val other = "e96573aa-cb14-4a45-b3c4-585e3e5071c7"

        assertEquals(
            "/a/1035/b/4410?c=9&u=$other",
            ServerIdCodec.decodeAllIn("/a/$emby/b/$plex?c=$tag&u=$other"),
        )
        assertEquals("/Videos/$other/stream", ServerIdCodec.decodeAllIn("/Videos/$other/stream"))
    }
}
