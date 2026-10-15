package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.plex.PlexIdentity
import com.rpeters.jellyfin.data.plex.PlexIdentityProbe
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Provider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JellyfinAuthRepositoryPlexTest {

    private val jellyfin: Jellyfin = mockk(relaxed = true)
    private val credentialManager: SecureCredentialManager = mockk(relaxed = true)
    private val connectionOptimizer: ConnectionOptimizer = mockk(relaxed = true)
    private val plexProbe: PlexIdentityProbe = mockk()

    private fun repository(plexEnabled: Boolean) = JellyfinAuthRepository(
        jellyfin,
        credentialManager,
        Provider { connectionOptimizer },
        plexIdentityProbe = Provider { plexProbe },
        isPlexSupportEnabled = { plexEnabled },
    )

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testServerConnection_plexServerWithFlagOff_saysPlexIsNotAvailableYet() = runTest {
        coEvery { connectionOptimizer.testServerConnection(PLEX_URL) } returns ApiResult.Error("404")
        coEvery { plexProbe.probe(PLEX_URL) } returns PlexIdentity("machine-1", "1.41.0")

        val result = repository(plexEnabled = false).testServerConnection(PLEX_URL)

        result as ApiResult.Error
        assertEquals("This is a Plex server. Plex support is not available yet.", result.message)
        assertEquals(ErrorType.UNSUPPORTED_SERVER_VERSION, result.errorType)
    }

    @Test
    fun testServerConnection_plexServerWithFlagOn_doesNotShowJellyfinError() = runTest {
        coEvery { connectionOptimizer.testServerConnection(PLEX_URL) } returns ApiResult.Error("404")
        coEvery { plexProbe.probe(PLEX_URL) } returns PlexIdentity("machine-1", "1.41.0")

        val result = repository(plexEnabled = true).testServerConnection(PLEX_URL)

        result as ApiResult.Error
        assertTrue(result.message.startsWith("This is a Plex server."))
    }

    @Test
    fun testServerConnection_unreachableNonPlexServer_keepsOriginalError() = runTest {
        coEvery { connectionOptimizer.testServerConnection(PLEX_URL) } returns ApiResult.Error("Connection refused")
        coEvery { plexProbe.probe(PLEX_URL) } returns null

        val result = repository(plexEnabled = true).testServerConnection(PLEX_URL)

        assertEquals("Connection refused", (result as ApiResult.Error).message)
    }

    @Test
    fun testServerConnection_jellyfinServer_neverProbesPlex() = runTest {
        val info = mockk<PublicSystemInfo>(relaxed = true)
        every { info.productName } returns "Jellyfin Server"
        every { info.version } returns "12.0.0"
        coEvery { connectionOptimizer.testServerConnection(PLEX_URL) } returns ApiResult.Success(info)

        assertTrue(repository(plexEnabled = true).testServerConnection(PLEX_URL) is ApiResult.Success)
        coVerify(exactly = 0) { plexProbe.probe(any()) }
    }

    private companion object {
        const val PLEX_URL = "https://plex.example.com:32400"
    }
}
