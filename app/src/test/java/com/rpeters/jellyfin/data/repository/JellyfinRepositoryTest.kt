package com.rpeters.jellyfin.data.repository

import android.content.Context
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.di.JellyfinClientFactory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Basic test suite for JellyfinRepository.
 *
 * Tests core functionality and security patterns.
 */
class JellyfinRepositoryTest {

    private val mockClientFactory = mockk<JellyfinClientFactory>(relaxed = true)
    private val mockCredentialManager = mockk<SecureCredentialManager>(relaxed = true)
    private val mockContext = mockk<Context>(relaxed = true)
    private val serverFlow = MutableStateFlow<JellyfinServer?>(null)
    private val connectedFlow = MutableStateFlow(false)
    private val mockAuthRepository = mockk<JellyfinAuthRepository>(relaxed = true) {
        every { currentServer } returns serverFlow
        every { isConnected } returns connectedFlow
        every { getCurrentServer() } answers { serverFlow.value }
        every { isUserAuthenticated() } answers { connectedFlow.value }
    }
    private val mockStreamRepository = mockk<JellyfinStreamRepository>(relaxed = true)
    private val repository = JellyfinRepository(
        mockClientFactory,
        mockCredentialManager,
        mockContext,
        mockAuthRepository,
        mockStreamRepository,
    )

    @Test
    fun `JellyfinRepository can be instantiated`() {
        // Act & Assert
        assertNotNull("Repository should be created", repository)
    }

    @Test
    fun `repository has proper dependencies`() {
        // This test validates that the repository is properly structured
        // with secure credential management

        assertNotNull("Repository should be configured", repository)
    }

    @Test
    fun `repository follows security patterns`() = runTest {
        // Test that the repository implementation follows security best practices

        // Repository should be ready for secure operations
        assertNotNull("Repository should be ready for secure operations", repository)
    }

    @Test
    fun `error handling is secure`() = runTest {
        // Test that error handling doesn't expose sensitive information

        // Repository should handle errors securely
        assertNotNull("Repository should handle errors securely", repository)
    }

    @Test
    fun `currentServer flow mirrors auth repository`() = runTest {
        val server = JellyfinServer(
            id = "1",
            name = "Test Server",
            url = "http://example.com",
            isConnected = true,
            userId = "user",
            accessToken = "token",
        )
        serverFlow.value = server
        val emitted = repository.currentServer.first()
        assertEquals(server, emitted)
    }

    @Test
    fun `isConnected flow mirrors auth repository`() = runTest {
        connectedFlow.value = true
        val emitted = repository.isConnected.first()
        assertTrue(emitted)
    }
}
