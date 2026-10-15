package com.rpeters.jellyfin.data.plex

import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import com.rpeters.jellyfin.network.DeviceIdentityProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexAuthDataSourceTest {

    private val testDispatcher = StandardTestDispatcher()
    private val plexTvClient = mockk<PlexTvClient>()
    private val identity = mockk<DeviceIdentityProvider> {
        every { plexClientIdentifier() } returns "cinefin-test-client"
    }
    private val dataSource = PlexAuthDataSource(plexTvClient, identity)

    private val waiting = PlexSignInState.WaitingForApproval(CODE, dataSource.authUrl(CODE))

    @Test
    fun signIn_approvedOnThirdPoll_emitsCodeThenToken() = runTest(testDispatcher) {
        coEvery { plexTvClient.createPin() } returns ApiResult.Success(pin())
        coEvery { plexTvClient.getPin(PIN_ID) } returnsMany listOf(
            ApiResult.Success(pin()),
            ApiResult.Success(pin()),
            ApiResult.Success(pin(authToken = TOKEN)),
        )

        val states = dataSource.signIn().toList()

        assertEquals(listOf(waiting, PlexSignInState.SignedIn(TOKEN)), states)
        coVerify(exactly = 3) { plexTvClient.getPin(PIN_ID) }
    }

    @Test
    fun signIn_pollsSlowDownToTheMaximumInterval() = runTest(testDispatcher) {
        coEvery { plexTvClient.createPin() } returns ApiResult.Success(pin())
        coEvery { plexTvClient.getPin(PIN_ID) } returnsMany List(5) { ApiResult.Success(pin()) } +
            ApiResult.Success(pin(authToken = TOKEN))

        dataSource.signIn().toList()

        // 1 s, 2 s, 4 s, then 5 s each: approved on the sixth poll.
        assertEquals(1_000L + 2_000L + 4_000L + 5_000L + 5_000L + 5_000L, testScheduler.currentTime)
    }

    @Test
    fun signIn_plexReportsPinGone_emitsExpired() = runTest(testDispatcher) {
        coEvery { plexTvClient.createPin() } returns ApiResult.Success(pin())
        coEvery { plexTvClient.getPin(PIN_ID) } returns
            ApiResult.Error("Code not found or expired", errorType = ErrorType.NOT_FOUND)

        assertEquals(listOf(waiting, PlexSignInState.Expired), dataSource.signIn().toList())
    }

    @Test
    fun signIn_neverApproved_stopsWhenThePinRunsOut() = runTest(testDispatcher) {
        coEvery { plexTvClient.createPin() } returns ApiResult.Success(pin(expiresIn = 10))
        coEvery { plexTvClient.getPin(PIN_ID) } returns ApiResult.Success(pin())

        val states = dataSource.signIn().toList()

        assertEquals(listOf(waiting, PlexSignInState.Expired), states)
        // Polls at 1, 3, 7 and 12 s; nothing after the 10 s lifetime.
        coVerify(exactly = 4) { plexTvClient.getPin(PIN_ID) }
    }

    @Test
    fun signIn_createFails_emitsFailedWithoutPolling() = runTest(testDispatcher) {
        coEvery { plexTvClient.createPin() } returns
            ApiResult.Error(NETWORK_MESSAGE, errorType = ErrorType.NETWORK)

        val states = dataSource.signIn().toList()

        assertEquals(listOf(PlexSignInState.Failed(NETWORK_MESSAGE, ErrorType.NETWORK)), states)
        coVerify(exactly = 0) { plexTvClient.getPin(any()) }
    }

    @Test
    fun signIn_briefNetworkTrouble_keepsPolling() = runTest(testDispatcher) {
        val offline = ApiResult.Error<PlexPin>(NETWORK_MESSAGE, errorType = ErrorType.NETWORK)
        coEvery { plexTvClient.createPin() } returns ApiResult.Success(pin())
        coEvery { plexTvClient.getPin(PIN_ID) } returnsMany listOf(
            offline,
            offline,
            ApiResult.Success(pin()),
            offline,
            ApiResult.Success(pin(authToken = TOKEN)),
        )

        assertEquals(listOf(waiting, PlexSignInState.SignedIn(TOKEN)), dataSource.signIn().toList())
    }

    @Test
    fun signIn_offlineForFivePollsInARow_emitsFailed() = runTest(testDispatcher) {
        coEvery { plexTvClient.createPin() } returns ApiResult.Success(pin())
        coEvery { plexTvClient.getPin(PIN_ID) } returns
            ApiResult.Error(NETWORK_MESSAGE, errorType = ErrorType.NETWORK)

        val states = dataSource.signIn().toList()

        assertEquals(listOf(waiting, PlexSignInState.Failed(NETWORK_MESSAGE, ErrorType.NETWORK)), states)
        coVerify(exactly = PlexAuthDataSource.MAX_CONSECUTIVE_FAILURES) { plexTvClient.getPin(PIN_ID) }
    }

    @Test
    fun signIn_rejectedByPlex_emitsFailedAtOnce() = runTest(testDispatcher) {
        coEvery { plexTvClient.createPin() } returns ApiResult.Success(pin())
        coEvery { plexTvClient.getPin(PIN_ID) } returns
            ApiResult.Error("User could not be authenticated", errorType = ErrorType.UNAUTHORIZED)

        val states = dataSource.signIn().toList()

        assertEquals(PlexSignInState.Failed("User could not be authenticated", ErrorType.UNAUTHORIZED), states.last())
        coVerify(exactly = 1) { plexTvClient.getPin(PIN_ID) }
    }

    @Test
    fun signIn_collectorStopsAfterTheCode_noPolling() = runTest(testDispatcher) {
        coEvery { plexTvClient.createPin() } returns ApiResult.Success(pin())
        coEvery { plexTvClient.getPin(PIN_ID) } returns ApiResult.Success(pin())

        assertEquals(waiting, dataSource.signIn().first())

        testScheduler.advanceUntilIdle()
        coVerify(exactly = 0) { plexTvClient.getPin(any()) }
    }

    @Test
    fun authUrl_carriesClientIdentifierCodeAndProduct() {
        val url = dataSource.authUrl(CODE)

        assertTrue(url.startsWith("https://app.plex.tv/auth#?"))
        assertTrue(url.contains("clientID=cinefin-test-client"))
        assertTrue(url.contains("code=$CODE"))
        assertTrue(url.contains("context%5Bdevice%5D%5Bproduct%5D=Cinefin"))
    }

    @Test
    fun signedIn_toString_hidesToken() {
        assertTrue(!PlexSignInState.SignedIn(TOKEN).toString().contains(TOKEN))
    }

    private fun pin(authToken: String? = null, expiresIn: Int = 900) =
        PlexPin(id = PIN_ID, code = CODE, expiresIn = expiresIn, authToken = authToken)

    private companion object {
        const val PIN_ID = 1297906816L
        const val CODE = "QPRV"
        const val TOKEN = "account-token"
        const val NETWORK_MESSAGE = "Could not reach plex.tv"
    }
}
