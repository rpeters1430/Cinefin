package com.rpeters.jellyfin.data.plex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Bodies follow the documented `/identity` shape; replace with captures in Phase 0. */
class PlexIdentityProbeTest {

    @Test
    fun parse_jsonBody_readsMachineIdentifierAndVersion() {
        val identity = PlexIdentityProbe.parse(
            """{"MediaContainer":{"size":0,"apiVersion":"1.1.1","claimed":true,""" +
                """"machineIdentifier":"abc123","version":"1.41.0.8992-8463ad060"}}""",
        )

        assertEquals(PlexIdentity("abc123", "1.41.0.8992-8463ad060"), identity)
    }

    @Test
    fun parse_xmlBody_readsMachineIdentifierAndVersion() {
        val identity = PlexIdentityProbe.parse(
            """<?xml version="1.0" encoding="UTF-8"?>
<MediaContainer size="0" claimed="1" machineIdentifier="abc123" version="1.41.0.8992-8463ad060">
</MediaContainer>""",
        )

        assertEquals(PlexIdentity("abc123", "1.41.0.8992-8463ad060"), identity)
    }

    @Test
    fun parse_notPlex_returnsNull() {
        listOf(
            """{"ServerName":"Jellyfin","Version":"10.11.0"}""",
            """{"MediaContainer":{"size":0}}""",
            "<html><body>Not found</body></html>",
            "",
            "{broken",
        ).forEach { assertNull(it, PlexIdentityProbe.parse(it)) }
    }
}
