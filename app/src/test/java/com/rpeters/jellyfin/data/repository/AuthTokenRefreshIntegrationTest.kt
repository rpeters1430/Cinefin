package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.di.JellyfinClientFactory
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthTokenRefreshIntegrationTest {

    private lateinit var baseRepository: BaseJellyfinRepository
    private lateinit var mockAuthRepository: JellyfinAuthRepository
    private lateinit var mockClientFactory: JellyfinClientFactory
    private lateinit var mockApiClient: ApiClient

    @Before
    fun setup() {
        // Create mocks
        mockAuthRepository = mockk(relaxed = true)
        mockClientFactory = mockk(relaxed = true)
        mockApiClient = mockk(relaxed = true)

        // Create a test implementation of BaseJellyfinRepository
        baseRepository = object : BaseJellyfinRepository(mockAuthRepository, mockClientFactory, mockk(relaxed = true)) {}
    }

    @Test
    fun `test that executeWithTokenRefresh handles 401 correctly`() = runTest {
        // Given: A server configuration
        val server = JellyfinServer(
            id = "server1",
            name = "Test Server",
            url = "https://test.jellyfin.org",
            isConnected = true,
            userId = "user1",
            username = "testuser",
            accessToken = "test_token",
            loginTimestamp = System.currentTimeMillis()
        )

        // Mock the auth repository
        every { mockAuthRepository.getCurrentServer() } returns server
        every { mockAuthRepository.isTokenExpired() } returns false

        // Mock successful re-authentication
        coEvery { mockAuthRepository.forceReAuthenticate() } returns true

        // Mock client factory
        coEvery { mockClientFactory.getClient(any<String>()) } returns mockApiClient
        every { mockClientFactory.invalidateClient() } just runs

        // When: We execute an operation that fails with 401 then succeeds
        var attemptCount = 0
        val result = baseRepository.executeWithTokenRefresh {
            attemptCount++
            if (attemptCount == 1) {
                throw InvalidStatusException(401, "Unauthorized")
            } else {
                "success"
            }
        }

        // Then: Operation should succeed on retry
        assertEquals("Should return success result", "success", result)
        assertEquals("Should have made 2 attempts", 2, attemptCount)

        // Verify that forceReAuthenticate was called
        coVerify(exactly = 1) { mockAuthRepository.forceReAuthenticate() }

        // Verify that client was invalidated
        verify(exactly = 1) { mockClientFactory.invalidateClient() }
    }
}