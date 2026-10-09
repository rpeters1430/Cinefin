package com.rpeters.jellyfin.data.emby

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
}
