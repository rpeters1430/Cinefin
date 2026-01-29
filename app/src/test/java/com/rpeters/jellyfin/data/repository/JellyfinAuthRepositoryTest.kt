package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.operations.QuickConnectApi
import org.jellyfin.sdk.api.operations.UserApi
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.UserDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.util.UUID
import org.jellyfin.sdk.model.api.QuickConnectResult as SdkQuickConnectResult

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JellyfinAuthRepositoryTest {

    private lateinit var repository: JellyfinAuthRepository
    private lateinit var credentialManager: SecureCredentialManager
    private lateinit var jellyfin: Jellyfin
    private lateinit var apiClient: ApiClient
    private lateinit var quickConnectApi: QuickConnectApi
    private lateinit var userApi: UserApi

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)

        credentialManager = mockk(relaxed = true)
        jellyfin = mockk(relaxed = true)
        apiClient = mockk(relaxed = true)
        quickConnectApi = mockk(relaxed = true)
        userApi = mockk(relaxed = true)

        mockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0

        every { jellyfin.createApi(any(), any()) } returns apiClient
        every { apiClient.quickConnectApi } returns quickConnectApi
        every { apiClient.userApi } returns userApi

        repository = JellyfinAuthRepository(jellyfin, credentialManager)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `initiateQuickConnect returns success`() = runTest {
        val sdkResult = buildQuickConnectResult(authenticated = false)
        coEvery { quickConnectApi.initiateQuickConnect() } returns sdkResponse(sdkResult)

        val result = repository.initiateQuickConnect(SERVER_URL)

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals(sdkResult.code, data.code)
        assertEquals(sdkResult.secret, data.secret)
    }

    @Test
    fun `getQuickConnectState returns pending when not yet approved`() = runTest {
        coEvery { quickConnectApi.getQuickConnectState(any()) } returns sdkResponse(
            buildQuickConnectResult(authenticated = false),
        )

        val result = repository.getQuickConnectState(SERVER_URL, SECRET)

        assertTrue(result is ApiResult.Success)
        assertEquals("Pending", (result as ApiResult.Success).data.state)
    }

    @Test
    fun `getQuickConnectState returns approved when authenticated`() = runTest {
        coEvery { quickConnectApi.getQuickConnectState(any()) } returns sdkResponse(
            buildQuickConnectResult(authenticated = true),
        )

        val result = repository.getQuickConnectState(SERVER_URL, SECRET)

        assertTrue(result is ApiResult.Success)
        assertEquals("Approved", (result as ApiResult.Success).data.state)
    }

    @Test
    fun `getQuickConnectState maps denied status`() = runTest {
        coEvery { quickConnectApi.getQuickConnectState(any()) } throws InvalidStatusException(401)

        val result = repository.getQuickConnectState(SERVER_URL, SECRET)

        assertTrue(result is ApiResult.Success)
        assertEquals("Denied", (result as ApiResult.Success).data.state)
    }

    @Test
    fun `getQuickConnectState maps expired status`() = runTest {
        coEvery { quickConnectApi.getQuickConnectState(any()) } throws InvalidStatusException(404)

        val result = repository.getQuickConnectState(SERVER_URL, SECRET)

        assertTrue(result is ApiResult.Success)
        assertEquals("Expired", (result as ApiResult.Success).data.state)
    }

    @Test
    fun `authenticateWithQuickConnect seeds server state`() = runTest {
        val authResult = buildAuthResult(accessToken = "token-123", username = "QuickConnectUser")
        coEvery { userApi.authenticateWithQuickConnect(any<org.jellyfin.sdk.model.api.QuickConnectDto>()) } returns sdkResponse(authResult)

        val result = repository.authenticateWithQuickConnect(SERVER_URL, SECRET)

        assertTrue(result is ApiResult.Success)
        assertEquals(authResult, (result as ApiResult.Success).data)
        assertEquals("token-123", repository.token())
        val currentServer = repository.currentServer.value
        assertNotNull(currentServer)
        assertEquals(SERVER_URL, currentServer?.url)
        assertEquals("QuickConnectUser", currentServer?.username)
    }

    @Test
    fun `authenticateUser does not persist credentials implicitly`() = runTest {
        val authResult = buildAuthResult(accessToken = "token-abc", username = "User")
        coEvery { userApi.authenticateUserByName(any()) } returns sdkResponse(authResult)

        val result = repository.authenticateUser(SERVER_URL, "User", "password")

        assertTrue(result is ApiResult.Success)
        assertEquals("token-abc", repository.token())
        assertEquals("token-abc", repository.currentServer.value?.accessToken)
        coVerify(exactly = 0) { credentialManager.savePassword(any(), any(), any()) }
    }

    @Test
    fun `authenticateWithQuickConnect propagates unauthorized error`() = runTest {
        coEvery { userApi.authenticateWithQuickConnect(any<org.jellyfin.sdk.model.api.QuickConnectDto>()) } throws InvalidStatusException(401)

        val result = repository.authenticateWithQuickConnect(SERVER_URL, SECRET)

        assertTrue(result is ApiResult.Error)
        assertEquals(ErrorType.UNAUTHORIZED, (result as ApiResult.Error).errorType)
    }

    private fun buildQuickConnectResult(authenticated: Boolean): SdkQuickConnectResult {
        return SdkQuickConnectResult(
            authenticated = authenticated,
            secret = SECRET,
            code = "123-ABC",
            deviceId = "device-id",
            deviceName = "device",
            appName = "app",
            appVersion = "1.0",
            dateAdded = LocalDateTime.now(),
        )
    }

    private fun buildAuthResult(accessToken: String, username: String): AuthenticationResult {
        val user = UserDto(
            name = username,
            serverId = "server-id",
            serverName = "server",
            id = UUID.randomUUID(),
            primaryImageTag = null,
            hasPassword = true,
            hasConfiguredPassword = true,
            hasConfiguredEasyPassword = false,
            enableAutoLogin = null,
            lastLoginDate = null,
            lastActivityDate = null,
            configuration = null,
            policy = null,
            primaryImageAspectRatio = null,
        )
        return AuthenticationResult(
            user = user,
            accessToken = accessToken,
            serverId = "server-id",
        )
    }

    private fun <T> sdkResponse(content: T): Response<T> = Response(content, 200, emptyMap())

    companion object {
        private const val SERVER_URL = "https://demo.jellyfin.org"
        private const val SECRET = "secret-value"
    }
}
