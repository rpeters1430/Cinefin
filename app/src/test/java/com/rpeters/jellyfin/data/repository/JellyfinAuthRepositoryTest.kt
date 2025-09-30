package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.repository.common.ApiResult
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.UserApiExtensionsKt
import org.jellyfin.sdk.api.operations.UserApi
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.UserDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class JellyfinAuthRepositoryTest {

    private companion object {
        private const val TOKEN_VALIDITY_DURATION_MS = 50 * 60 * 1000L
        private const val BASE_TIME = 1_000L
        private const val SERVER_URL = "https://demo.jellyfin.org"
        private const val USERNAME = "testuser"
        private const val PASSWORD = "password123"
        private const val SERVER_ID = "server-id"
        private const val ACCESS_TOKEN = "access-token"
    }

    private lateinit var authRepository: JellyfinAuthRepository
    private lateinit var credentialManager: SecureCredentialManager
    private lateinit var jellyfin: Jellyfin
    private lateinit var apiClient: ApiClient
    private lateinit var userApi: UserApi

    private var currentTime = BASE_TIME

    @Before
    fun setup() {
        credentialManager = mockk(relaxUnitFun = true)
        jellyfin = mockk(relaxUnitFun = true)
        apiClient = mockk()
        userApi = mockk()

        mockkStatic(UserApiExtensionsKt::class)
        every { jellyfin.createApi(any(), any(), any(), any(), any()) } returns apiClient
        every { apiClient.userApi } returns userApi

        authRepository = JellyfinAuthRepository(jellyfin, credentialManager) { currentTime }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun authenticateUser_updatesStateWithMockedDependencies() = runTest {
        coEvery { userApi.authenticateUserByName(any()) } returns successfulAuthResponse()
        coJustRun { credentialManager.savePassword(SERVER_URL, USERNAME, PASSWORD) }

        val result = authRepository.authenticateUser(SERVER_URL, USERNAME, PASSWORD)

        assertTrue(result is ApiResult.Success)
        val server = authRepository.getCurrentServer()
        assertNotNull(server)
        assertEquals(SERVER_URL, server?.url)
        assertEquals(USERNAME, server?.username)
        assertEquals(ACCESS_TOKEN, server?.accessToken)
        assertEquals(currentTime, server?.loginTimestamp)
        coVerify(exactly = 1) { credentialManager.savePassword(SERVER_URL, USERNAME, PASSWORD) }
    }

    @Test
    fun isTokenExpired_reflectsInjectedTimeProvider() {
        val initialServer = JellyfinServer(
            id = SERVER_ID,
            name = "Demo Server",
            url = SERVER_URL,
            isConnected = true,
            username = USERNAME,
            accessToken = ACCESS_TOKEN,
            loginTimestamp = BASE_TIME,
        )

        authRepository.seedCurrentServer(initialServer)

        assertFalse(authRepository.isTokenExpired())

        currentTime = BASE_TIME + TOKEN_VALIDITY_DURATION_MS + 1

        assertTrue(authRepository.isTokenExpired())
    }

    @Test
    fun forceReAuthenticate_runsOnceAcrossConcurrentCallers() = runTest {
        val expiredServer = JellyfinServer(
            id = SERVER_ID,
            name = "Demo Server",
            url = SERVER_URL,
            isConnected = true,
            username = USERNAME,
            accessToken = null,
            loginTimestamp = BASE_TIME - TOKEN_VALIDITY_DURATION_MS - 1,
        )
        authRepository.seedCurrentServer(expiredServer)

        coEvery { credentialManager.getPassword(SERVER_URL, USERNAME) } returns PASSWORD
        coEvery { userApi.authenticateUserByName(any()) } returns successfulAuthResponse()
        coJustRun { credentialManager.savePassword(SERVER_URL, USERNAME, PASSWORD) }

        currentTime = expiredServer.loginTimestamp!! + TOKEN_VALIDITY_DURATION_MS + 10

        val results = List(10) { async { authRepository.forceReAuthenticate() } }.awaitAll()

        assertTrue(results.all { it })
        coVerify(exactly = 1) { credentialManager.getPassword(SERVER_URL, USERNAME) }
    }

    @Test
    fun forceReAuthenticate_returnsFalseWhenPasswordMissing() = runTest {
        val initialServer = JellyfinServer(
            id = SERVER_ID,
            name = "Demo Server",
            url = SERVER_URL,
            isConnected = true,
            username = USERNAME,
            accessToken = ACCESS_TOKEN,
            loginTimestamp = BASE_TIME - TOKEN_VALIDITY_DURATION_MS - 1,
        )
        authRepository.seedCurrentServer(initialServer)

        coEvery { credentialManager.getPassword(SERVER_URL, USERNAME) } returns null

        currentTime = BASE_TIME + TOKEN_VALIDITY_DURATION_MS + 100

        val success = authRepository.forceReAuthenticate()

        assertFalse(success)
        assertEquals(initialServer, authRepository.getCurrentServer())
    }

    @Test
    fun authenticateUser_propagatesApiErrorsWithoutMutatingState() = runTest {
        coEvery { userApi.authenticateUserByName(any()) } throws IllegalStateException("boom")

        val result = authRepository.authenticateUser(SERVER_URL, USERNAME, PASSWORD)

        assertTrue(result is ApiResult.Error)
        assertNull(authRepository.getCurrentServer())
    }

    private fun successfulAuthResponse(): Response<AuthenticationResult> {
        val user = UserDto(
            name = USERNAME,
            serverId = SERVER_ID,
            serverName = "Demo",
            id = UUID.randomUUID(),
            primaryImageTag = null,
            hasPassword = true,
            hasConfiguredPassword = true,
            hasConfiguredEasyPassword = false,
        )

        val authResult = AuthenticationResult(
            user = user,
            accessToken = ACCESS_TOKEN,
            serverId = SERVER_ID,
        )

        return Response(authResult, 200, emptyMap())
    }
}
