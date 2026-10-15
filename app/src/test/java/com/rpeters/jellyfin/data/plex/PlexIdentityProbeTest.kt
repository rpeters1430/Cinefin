package com.rpeters.jellyfin.data.plex

import com.rpeters.jellyfin.network.JellyfinAuthInterceptor
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

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

    @Test
    fun probe_skipsTheSessionInterceptor_soNoServerTokenIsSent() = runTest {
        // Any call into this interceptor fails the test: it would add the signed-in server's token.
        val sessionInterceptor = mockk<JellyfinAuthInterceptor>()
        val shared = OkHttpClient.Builder().addInterceptor(sessionInterceptor).build()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"MediaContainer":{"machineIdentifier":"abc123"}}"""))
            server.start()

            val identity = PlexIdentityProbe(dagger.Lazy { shared }).probe(server.url("/").toString())

            assertEquals("abc123", identity?.machineIdentifier)
            val request = server.takeRequest()
            assertNull(request.getHeader("X-Emby-Token"))
            assertNull(request.getHeader(PlexClientHeaders.TOKEN))
            verify(exactly = 0) { sessionInterceptor.intercept(any()) }
        }
    }

    @Test
    fun probe_cancelled_stopsTheHttpCallRightAway() = runTest {
        MockWebServer().use { server ->
            // A server that accepts the connection and then stalls past the probe's own 5 s limit.
            server.enqueue(MockResponse().setHeadersDelay(STALL_SECONDS, TimeUnit.SECONDS))
            server.start()
            val probe = PlexIdentityProbe(dagger.Lazy { OkHttpClient() })

            val started = System.nanoTime()
            val identity = withContext(Dispatchers.Default) {
                withTimeoutOrNull(SHORT_TIMEOUT_MS) { probe.probe(server.url("/").toString()) }
            }
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

            assertNull(identity)
            assertTrue("probe took $elapsedMs ms", elapsedMs < SHORT_TIMEOUT_MS * 4)
        }
    }

    private companion object {
        const val STALL_SECONDS = 10L
        const val SHORT_TIMEOUT_MS = 300L
    }
}
