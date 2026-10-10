package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.emby.EmbyAuthDataSource
import com.rpeters.jellyfin.data.emby.EmbyHttpException
import com.rpeters.jellyfin.data.emby.EmbyJsonNormalizer
import com.rpeters.jellyfin.data.model.ServerType
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JellyfinAuthRepositoryEmbyTest {

    private val CONNECT_USER = "connect-user"
    private val LINKED_KEY = "linked-key"
    private val EXPECTED_SERVER = "expected-server"

    private val jellyfin: Jellyfin = mockk(relaxed = true)
    private val credentialManager: SecureCredentialManager = mockk(relaxed = true)
    private val connectionOptimizer: ConnectionOptimizer = mockk(relaxed = true)
    private val embyAuthDataSource: EmbyAuthDataSource = mockk()
    private var embyEnabled = true

    private lateinit var repository: JellyfinAuthRepository

    private val embyAuthResult: AuthenticationResult = EmbyJsonNormalizer.decode(
        AuthenticationResult.serializer(),
        """
        {
          "User": {"Name": "tester", "Id": "e96573aacb144a45b3c4585e3e5071c7", "Policy": {"IsAdministrator": false}},
          "AccessToken": "emby-token",
          "ServerId": "1cd2cfae841648e68c2384b35d829543"
        }
        """.trimIndent(),
    )

    @Before
    fun setUp() {
        embyEnabled = true
        repository = JellyfinAuthRepository(
            jellyfin,
            credentialManager,
            Provider { connectionOptimizer },
            embyAuthDataSource = Provider { embyAuthDataSource },
            isEmbySupportEnabled = { embyEnabled },
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun probeReturns(productName: String?, version: String?) {
        val info = mockk<PublicSystemInfo>(relaxed = true)
        every { info.productName } returns productName
        every { info.version } returns version
        coEvery { connectionOptimizer.testServerConnection(EMBY_URL) } returns ApiResult.Success(info)
    }

    @Test
    fun connectSignIn_wrongServerId_neverSendsLinkedKey() = runTest {
        val connect = mockk<com.rpeters.jellyfin.data.emby.EmbyConnectClient>()
        val auth = JellyfinAuthRepository(
            jellyfin, credentialManager, Provider { connectionOptimizer },
            embyAuthDataSource = Provider { embyAuthDataSource },
            isEmbySupportEnabled = { true }, embyConnectClient = Provider { connect },
        )
        val info = mockk<PublicSystemInfo>(relaxed = true)
        every { info.version } returns "4.11.0.6"
        every { info.productName } returns "Emby Server"
        every { info.id } returns "different-server"
        coEvery { connectionOptimizer.testServerConnection(EMBY_URL) } returns ApiResult.Success(info)
        val result = auth.authenticateWithEmbyConnect(EMBY_URL, CONNECT_USER, LINKED_KEY, EXPECTED_SERVER)
        assertTrue(result is ApiResult.Error)
        coVerify(exactly = 0) { connect.exchange(any(), any(), any()) }
        assertNull(auth.getCurrentServerSync())
    }

    @Test
    fun connectSignIn_savesLinkedCredentialsAndEmbySession() = runTest {
        val connect = mockk<com.rpeters.jellyfin.data.emby.EmbyConnectClient>()
        val auth = JellyfinAuthRepository(
            jellyfin, credentialManager, Provider { connectionOptimizer },
            embyAuthDataSource = Provider { embyAuthDataSource },
            isEmbySupportEnabled = { true }, embyConnectClient = Provider { connect },
        )
        val info = mockk<PublicSystemInfo>(relaxed = true)
        every { info.version } returns "4.11.0.6"
        every { info.productName } returns "Emby Server"
        every { info.id } returns EXPECTED_SERVER
        coEvery { connectionOptimizer.testServerConnection(EMBY_URL) } returns ApiResult.Success(info)
        coEvery { connect.exchange(EMBY_URL, CONNECT_USER, LINKED_KEY) } returns embyAuthResult
        assertTrue(auth.authenticateWithEmbyConnect(EMBY_URL, CONNECT_USER, LINKED_KEY, EXPECTED_SERVER) is ApiResult.Success)
        val saved = requireNotNull(auth.getCurrentServerSync())
        assertEquals(ServerType.EMBY, saved.serverType)
        assertEquals(CONNECT_USER, saved.embyConnectUserId)
        assertEquals(LINKED_KEY, saved.embyConnectAccessKey)
        assertEquals(EXPECTED_SERVER, saved.id)
        val profile = requireNotNull(com.rpeters.jellyfin.data.model.ServerProfile.fromServer(saved))
        assertEquals(LINKED_KEY, profile.toJellyfinServer().embyConnectAccessKey)
    }

    @Test
    fun testServerConnection_embyServerWithSupportEnabled_succeeds() = runTest {
        probeReturns(productName = null, version = "4.11.0.6")

        assertTrue(repository.testServerConnection(EMBY_URL) is ApiResult.Success)
    }

    @Test
    fun testServerConnection_embyServerWithSupportDisabled_explainsInsteadOfBlamingVersion() = runTest {
        embyEnabled = false
        probeReturns(productName = null, version = "4.11.0.6")

        val result = repository.testServerConnection(EMBY_URL)

        assertTrue(result is ApiResult.Error)
        result as ApiResult.Error
        assertEquals(ErrorType.UNSUPPORTED_SERVER_VERSION, result.errorType)
        assertTrue(result.message.contains("Emby"))
        assertFalse(result.message.contains("Jellyfin Server 12"))
    }

    @Test
    fun testServerConnection_embyServerOlderThanMinimum_isRejected() = runTest {
        probeReturns(productName = null, version = "4.7.14.0")

        val result = repository.testServerConnection(EMBY_URL)

        assertTrue(result is ApiResult.Error)
        assertEquals(ErrorType.UNSUPPORTED_SERVER_VERSION, (result as ApiResult.Error).errorType)
        assertTrue(result.message.contains("4.8"))
    }

    @Test
    fun authenticateUser_afterEmbyProbe_signsInThroughEmbyAndMarksServerAsEmby() = runTest {
        probeReturns(productName = null, version = "4.11.0.6")
        coEvery { embyAuthDataSource.signIn(EMBY_URL, "tester", "secret") } returns embyAuthResult
        repository.testServerConnection(EMBY_URL)

        val result = repository.authenticateUser(EMBY_URL, "tester", "secret")

        assertTrue(result is ApiResult.Success)
        val server = repository.getCurrentServerSync()
        assertEquals(ServerType.EMBY, server?.serverType)
        assertEquals("emby-token", server?.accessToken)
        assertEquals("1cd2cfae841648e68c2384b35d829543", server?.id)
        assertEquals("e96573aa-cb14-4a45-b3c4-585e3e5071c7", server?.userId)
        assertEquals("tester", server?.username)
        assertEquals("emby-token", repository.token())
    }

    @Test
    fun authenticateUser_embyRejectsPassword_reportsUnauthorizedAndLeavesNoSession() = runTest {
        probeReturns(productName = null, version = "4.11.0.6")
        coEvery { embyAuthDataSource.signIn(any(), any(), any()) } throws EmbyHttpException(401, "Unauthorized")
        repository.testServerConnection(EMBY_URL)

        val result = repository.authenticateUser(EMBY_URL, "tester", "wrong")

        assertTrue(result is ApiResult.Error)
        result as ApiResult.Error
        assertEquals(ErrorType.UNAUTHORIZED, result.errorType)
        assertTrue(result.message.contains("401"))
        assertNull(repository.getCurrentServerSync())
    }

    @Test
    fun reAuthenticate_embySession_usesEmbySignInWithoutANewProbe() = runTest {
        probeReturns(productName = null, version = "4.11.0.6")
        coEvery { embyAuthDataSource.signIn(EMBY_URL, "tester", "secret") } returns embyAuthResult
        coEvery { credentialManager.getPassword(EMBY_URL, "tester") } returns "secret"
        repository.testServerConnection(EMBY_URL)
        repository.authenticateUser(EMBY_URL, "tester", "secret")

        // A fresh repository that only knows the restored session, as after an app restart.
        val restored = JellyfinAuthRepository(
            jellyfin,
            credentialManager,
            Provider { connectionOptimizer },
            embyAuthDataSource = Provider { embyAuthDataSource },
            isEmbySupportEnabled = { true },
        )
        restored.restorePersistedSession(repository.getCurrentServerSync()!!)

        assertTrue(restored.reAuthenticate())
        coVerify(exactly = 2) { embyAuthDataSource.signIn(EMBY_URL, "tester", "secret") }
        assertEquals(ServerType.EMBY, restored.getCurrentServerSync()?.serverType)
    }

    @Test
    fun logout_embyServer_callsEmbyLogout() = runTest {
        probeReturns(productName = null, version = "4.11.0.6")
        coEvery { embyAuthDataSource.signIn(EMBY_URL, "tester", "secret") } returns embyAuthResult
        coEvery { embyAuthDataSource.logout(EMBY_URL) } returns Unit
        repository.testServerConnection(EMBY_URL)
        repository.authenticateUser(EMBY_URL, "tester", "secret")

        repository.logout()

        coVerify(exactly = 1) { embyAuthDataSource.logout(EMBY_URL) }
        assertNull(repository.getCurrentServerSync())
    }

    @Test
    fun tokenExpiry_bypassedForEmbyServer() = runTest {
        val server = com.rpeters.jellyfin.data.JellyfinServer(
            id = "1",
            name = "Emby",
            url = EMBY_URL,
            serverType = ServerType.EMBY,
            accessToken = "token",
            loginTimestamp = 1000L,
        )
        repository.seedCurrentServer(server)

        assertFalse(repository.isTokenExpired())
        assertFalse(repository.shouldRefreshToken())
    }

    @Test
    fun detect_identifiesServerTypeFromPublicInfo() {
        assertEquals(ServerType.JELLYFIN, ServerType.detect("Jellyfin Server", "12.0.1"))
        assertEquals(ServerType.EMBY, ServerType.detect(null, "4.11.0.6"))
        assertEquals(ServerType.EMBY, ServerType.detect("Emby Server", null))
        assertEquals(ServerType.JELLYFIN, ServerType.detect(null, "10.10.7"))
        assertEquals(ServerType.JELLYFIN, ServerType.detect(null, null))
        assertEquals(ServerType.JELLYFIN, ServerType.detect("", "unknown"))
    }

    private companion object {
        const val EMBY_URL = "https://emby.example.com"
    }
}
