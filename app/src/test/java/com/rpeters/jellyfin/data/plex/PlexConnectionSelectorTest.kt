package com.rpeters.jellyfin.data.plex

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlexConnectionSelectorTest {

    private val testDispatcher = StandardTestDispatcher()
    private val probe = mockk<PlexIdentityProbe>()
    private val selector = PlexConnectionSelector(probe)

    private val remoteHttps = PlexConnection(protocol = "https", uri = REMOTE_HTTPS, local = false)
    private val localHttps = PlexConnection(protocol = "https", uri = LOCAL_HTTPS, local = true)
    private val localHttp = PlexConnection(protocol = "http", uri = LOCAL_HTTP, local = true)
    private val relay = PlexConnection(protocol = "https", uri = RELAY, local = false, relay = true)
    private val remoteHttp = PlexConnection(protocol = "http", uri = REMOTE_HTTP, local = false)

    @Test
    fun rank_prefersLocalHttpsThenLocalHttpThenRemoteThenRelay() {
        val ranked = PlexConnectionSelector.rank(listOf(relay, remoteHttp, remoteHttps, localHttp, localHttps))

        assertEquals(listOf(LOCAL_HTTPS, LOCAL_HTTP, REMOTE_HTTPS, REMOTE_HTTP, RELAY), ranked.map { it.uri })
    }

    @Test
    fun rank_dropsDuplicateUris() {
        assertEquals(listOf(LOCAL_HTTPS), PlexConnectionSelector.rank(listOf(localHttps, localHttps)).map { it.uri })
    }

    @Test
    fun select_everythingAnswers_picksLocalHttpsAndKeepsTheRestAsAlternates() = runTest(testDispatcher) {
        answers(LOCAL_HTTPS, LOCAL_HTTP, REMOTE_HTTPS, RELAY)

        val address = selector.select(server(remoteHttps, relay, localHttp, localHttps))

        assertEquals(PlexServerAddress(LOCAL_HTTPS, listOf(LOCAL_HTTP, REMOTE_HTTPS, RELAY)), address)
    }

    @Test
    fun select_awayFromHome_fallsBackToRemote() = runTest(testDispatcher) {
        answers(REMOTE_HTTPS, RELAY)

        val address = selector.select(server(remoteHttps, relay, localHttp, localHttps))

        assertEquals(REMOTE_HTTPS, address?.uri)
        assertEquals(listOf(LOCAL_HTTPS, LOCAL_HTTP, RELAY), address?.alternateUris)
    }

    @Test
    fun select_onlyRelayAnswers_usesRelay() = runTest(testDispatcher) {
        answers(RELAY)

        assertEquals(RELAY, selector.select(server(remoteHttps, relay, localHttps))?.uri)
    }

    @Test
    fun select_addressAnswersAsADifferentServer_isSkipped() = runTest(testDispatcher) {
        answers(REMOTE_HTTPS)
        coEvery { probe.probe(LOCAL_HTTPS) } returns PlexIdentity("someone-elses-server", null)

        assertEquals(REMOTE_HTTPS, selector.select(server(localHttps, remoteHttps))?.uri)
    }

    @Test
    fun select_slowAddress_timesOutWithoutBlockingTheOthers() = runTest(testDispatcher) {
        answers(REMOTE_HTTPS)
        coEvery { probe.probe(LOCAL_HTTPS) } coAnswers {
            delay(PlexConnectionSelector.PROBE_TIMEOUT_MS * 10)
            PlexIdentity(MACHINE_ID, null)
        }

        val address = selector.select(server(localHttps, remoteHttps))

        assertEquals(REMOTE_HTTPS, address?.uri)
        assertEquals(PlexConnectionSelector.PROBE_TIMEOUT_MS, testScheduler.currentTime)
    }

    @Test
    fun select_nothingAnswers_returnsNull() = runTest(testDispatcher) {
        answers()

        assertNull(selector.select(server(localHttps, remoteHttps, relay)))
    }

    /** Each listed URI answers as [MACHINE_ID]; every other URI is unreachable. */
    private fun answers(vararg reachable: String) {
        coEvery { probe.probe(any()) } returns null
        reachable.forEach { uri -> coEvery { probe.probe(uri) } returns PlexIdentity(MACHINE_ID, "1.41.0") }
    }

    private fun server(vararg connections: PlexConnection) = PlexResource(
        name = "Living Room PMS",
        clientIdentifier = MACHINE_ID,
        provides = "server",
        connections = connections.toList(),
    )

    private companion object {
        const val MACHINE_ID = "machine-owned-1"
        const val LOCAL_HTTPS = "https://10-0-0-5.abc123.plex.direct:32400"
        const val LOCAL_HTTP = "http://10.0.0.5:32400"
        const val REMOTE_HTTPS = "https://203-0-113-9.abc123.plex.direct:32400"
        const val REMOTE_HTTP = "http://203.0.113.9:32400"
        const val RELAY = "https://198-51-100-20.abc123.plex.direct:8443"
    }
}
