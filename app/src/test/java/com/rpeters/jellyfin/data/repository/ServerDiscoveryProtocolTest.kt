package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.model.ServerType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ServerDiscoveryProtocolTest {
    @Test
    fun probes_includeDocumentedEmbyBroadcast() {
        assertEquals("who is EmbyServer?", ServerDiscoveryProtocol.probes[ServerType.EMBY])
        assertEquals("Who is JellyfinServer?", ServerDiscoveryProtocol.probes[ServerType.JELLYFIN])
    }

    @Test
    fun sameReply_differentProbe_preservesServerType() {
        val reply = """{"Id":"server-id","Name":"NAS","Address":"http://192.168.1.10:8096/emby"}"""
        val emby = requireNotNull(ServerDiscoveryProtocol.parse(reply, ServerType.EMBY))
        val jellyfin = requireNotNull(ServerDiscoveryProtocol.parse(reply, ServerType.JELLYFIN))
        assertEquals(ServerType.EMBY, emby.serverType)
        assertEquals(ServerType.JELLYFIN, jellyfin.serverType)
        assertEquals("http://192.168.1.10:8096/emby", emby.address)
        assertNull(emby.version)
    }

    @Test
    fun invalidReplies_areIgnored() {
        listOf("not json", "{}", """{"Id":"","Name":"NAS","Address":"http://nas:8096"}""").forEach {
            assertNull(ServerDiscoveryProtocol.parse(it, ServerType.EMBY))
        }
    }
}
