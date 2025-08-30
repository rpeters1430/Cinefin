package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.di.JellyfinClientFactory
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.operations.UserApi
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthTokenRefreshTest {

    private lateinit var authRepository: JellyfinAuthRepository
    private lateinit var mockCredentialManager: SecureCredentialManager
    private lateinit var mockClientFactory: JellyfinClientFactory
    private lateinit var mockUserApi: UserApi
    private lateinit var mockApiClient: ApiClient

    @Before
    fun setup() {
        // Create mocks
        mockCredentialManager = mockk(relaxed = true)
        mockClientFactory = mockk(relaxed = true)
        mockUserApi = mockk(relaxed = true)
        mockApiClient = mockk(relaxed = true)

        // Setup API client mock
        every { mockApiClient.userApi } returns mockUserApi

        // Initialize repository
        authRepository = JellyfinAuthRepository(mockClientFactory, mockCredentialManager)
    }

    @Test
    fun `executeWithTokenRefresh handles 401 by refreshing token and retrying`() = runTest {
        // Given: A repository with an expired token
        val server = JellyfinServer(
            id = "server1",
            name = "Test Server",
            url = "https://test.jellyfin.org",
            isConnected = true,
            userId = "user1",
            username = "testuser",
            accessToken = "expired_token",
            loginTimestamp = System.currentTimeMillis() - (60 * 60 * 1000) // 1 hour ago
        )

        // Mock the auth repository to return our test server
        val serverFlow = MutableStateFlow(server)
        every { authRepository.currentServer } returns serverFlow
        every { authRepository.getCurrentServer() } answers { serverFlow.value }
        every { authRepository.isTokenExpired() } returns true

        // Mock successful re-authentication
        coEvery { authRepository.forceReAuthenticate() } returns true

        // Mock credential manager to return password
        coEvery { mockCredentialManager.getPassword(any(), any()) } returns "password123"

        // Create a mock client factory that returns our API client
        coEvery { mockClientFactory.getClient(any<String>()) } returns mockApiClient

        // Mock successful authentication response
        val authResult = mockk<AuthenticationResult> {
            every { accessToken } returns "new_token"
            every { serverId } returns "server1"
            every { user } returns mockk {
                every { id } returns java.util.UUID.randomUUID()
                every { name } returns "testuser"
            }
        }

        val authResponse = mockk<org.jellyfin.sdk.model.api.AuthenticationResult> {
            every { content } returns authResult
        }

        coEvery { mockUserApi.authenticateUserByName(any<AuthenticateUserByName>()) } returns authResponse

        // When: We execute an operation that initially fails with 401
        var attemptCount = 0
        var finalResult: String? = null

        try {
            finalResult = authRepository.executeWithTokenRefresh {
                attemptCount++
                if (attemptCount == 1) {
                    // First attempt fails with 401
                    throw InvalidStatusException(401, "Unauthorized")
                } else {
                    // Second attempt succeeds
                    "success"
                }
            }
        } catch (e: Exception) {
            fail("Should not throw exception: ${e.message}")
        }

        // Then: Operation should succeed on retry with fresh token
        assertEquals("Should have made 2 attempts", 2, attemptCount)
        assertEquals("Should return success result", "success", finalResult)
        
        // Verify that forceReAuthenticate was called
        coVerify(exactly = 1) { authRepository.forceReAuthenticate() }
        
        // Verify that client was invalidated
        verify(exactly = 1) { mockClientFactory.invalidateClient() }
    }

    @Test
    fun `executeWithTokenRefresh handles concurrent requests with single-flight reauth`() = runTest {
        // Given: Multiple coroutines attempting operations with expired tokens
        val server = JellyfinServer(
            id = "server1",
            name = "Test Server",
            url = "https://test.jellyfin.org",
            isConnected = true,
            userId = "user1",
            username = "testuser",
            accessToken = "expired_token",
            loginTimestamp = System.currentTimeMillis() - (60 * 60 * 1000) // 1 hour ago
        )

        // Mock the auth repository
        val serverFlow = MutableStateFlow(server)
        every { authRepository.currentServer } returns serverFlow
        every { authRepository.getCurrentServer() } answers { serverFlow.value }
        every { authRepository.isTokenExpired() } returns true

        // Mock authentication in progress
        val authInProgressFlow = MutableStateFlow(false)
        every { authRepository.isAuthenticating } returns authInProgressFlow

        // Mock re-authentication that takes some time
        coEvery { authRepository.forceReAuthenticate() } coAnswers {
            // Simulate delay for re-authentication
            kotlinx.coroutines.delay(100)
            true
        }

        // Mock credential manager
        coEvery { mockCredentialManager.getPassword(any(), any()) } returns "password123"

        // Create a mock client factory
        coEvery { mockClientFactory.getClient(any<String>()) } returns mockApiClient

        // Mock authentication response
        val authResult = mockk<AuthenticationResult> {
            every { accessToken } returns "new_token"
            every { serverId } returns "server1"
            every { user } returns mockk {
                every { id } returns java.util.UUID.randomUUID()
                every { name } returns "testuser"
            }
        }

        val authResponse = mockk<org.jellyfin.sdk.model.api.AuthenticationResult> {
            every { content } returns authResult
        }

        coEvery { mockUserApi.authenticateUserByName(any<AuthenticateUserByName>()) } returns authResponse

        // When: Multiple concurrent operations are executed
        var results = mutableListOf<String>()
        val jobs = mutableListOf<kotlinx.coroutines.Job>()

        repeat(5) { index ->
            val job = kotlinx.coroutines.launch {
                try {
                    val result = authRepository.executeWithTokenRefresh {
                        // Simulate some work
                        kotlinx.coroutines.delay(50)
                        "success_$index"
                    }
                    results.add(result)
                } catch (e: Exception) {
                    fail("Should not throw exception: ${e.message}")
                }
            }
            jobs.add(job)
        }

        // Wait for all jobs to complete
        jobs.forEach { it.join() }

        // Then: All operations should succeed
        assertEquals("All operations should succeed", 5, results.size)
        assertTrue("All results should be success", results.all { it.startsWith("success_") })
        
        // Verify that forceReAuthenticate was called only once (single-flight)
        coVerify(exactly = 1) { authRepository.forceReAuthenticate() }
    }

    @Test
    fun `executeWithTokenRefresh handles token refresh failure`() = runTest {
        // Given: A repository where token refresh fails
        val server = JellyfinServer(
            id = "server1",
            name = "Test Server",
            url = "https://test.jellyfin.org",
            isConnected = true,
            userId = "user1",
            username = "testuser",
            accessToken = "expired_token",
            loginTimestamp = System.currentTimeMillis() - (60 * 60 * 1000) // 1 hour ago
        )

        // Mock the auth repository
        val serverFlow = MutableStateFlow(server)
        every { authRepository.currentServer } returns serverFlow
        every { authRepository.getCurrentServer() } answers { serverFlow.value }
        every { authRepository.isTokenExpired() } returns true

        // Mock failed re-authentication
        coEvery { authRepository.forceReAuthenticate() } returns false

        // When: We execute an operation that fails with 401
        var exceptionCaught: Exception? = null
        try {
            authRepository.executeWithTokenRefresh {
                throw InvalidStatusException(401, "Unauthorized")
            }
        } catch (e: Exception) {
            exceptionCaught = e
        }

        // Then: Should throw authentication exception
        assertNotNull("Should throw exception", exceptionCaught)
        assertTrue("Should be authentication exception", exceptionCaught is Exception)
        assertEquals("Should have correct error message", "Authentication failed: Unable to refresh token", exceptionCaught?.message)
        
        // Verify that forceReAuthenticate was called
        coVerify(exactly = 1) { authRepository.forceReAuthenticate() }
    }

    @Test
    fun `fresh token is used for subsequent requests`() = runTest {
        // Given: A repository that has successfully refreshed its token
        val oldServer = JellyfinServer(
            id = "server1",
            name = "Test Server",
            url = "https://test.jellyfin.org",
            isConnected = true,
            userId = "user1",
            username = "testuser",
            accessToken = "old_token",
            loginTimestamp = System.currentTimeMillis() - (60 * 60 * 1000) // 1 hour ago
        )

        val newServer = oldServer.copy(accessToken = "new_token", loginTimestamp = System.currentTimeMillis())

        // Mock the auth repository to simulate token refresh
        val serverFlow = MutableStateFlow(oldServer)
        every { authRepository.currentServer } returns serverFlow
        every { authRepository.getCurrentServer() } answers { serverFlow.value }
        every { authRepository.isTokenExpired() } returns true

        // Mock successful re-authentication that updates the server
        coEvery { authRepository.forceReAuthenticate() } coAnswers {
            serverFlow.value = newServer
            true
        }

        // Mock credential manager
        coEvery { mockCredentialManager.getPassword(any(), any()) } returns "password123"

        // Create a mock client factory
        coEvery { mockClientFactory.getClient(any<String>()) } returns mockApiClient

        // Mock authentication response
        val authResult = mockk<AuthenticationResult> {
            every { accessToken } returns "new_token"
            every { serverId } returns "server1"
            every { user } returns mockk {
                every { id } returns java.util.UUID.randomUUID()
                every { name } returns "testuser"
            }
        }

        val authResponse = mockk<org.jellyfin.sdk.model.api.AuthenticationResult> {
            every { content } returns authResult
        }

        coEvery { mockUserApi.authenticateUserByName(any<AuthenticateUserByName>()) } returns authResponse

        // When: Multiple operations are executed after token refresh
        val results = mutableListOf<String>()
        
        // First operation (triggers token refresh)
        try {
            val result = authRepository.executeWithTokenRefresh {
                throw InvalidStatusException(401, "Unauthorized")
            }
            results.add(result)
        } catch (e: Exception) {
            fail("First operation should succeed after retry")
        }

        // Second operation (should use fresh token)
        try {
            val result = authRepository.executeWithTokenRefresh {
                "success_with_fresh_token"
            }
            results.add(result)
        } catch (e: Exception) {
            fail("Second operation should succeed with fresh token")
        }

        // Then: Both operations should succeed
        assertEquals("Both operations should succeed", 2, results.size)
        assertEquals("Second operation should succeed", "success_with_fresh_token", results[1])
    }
}