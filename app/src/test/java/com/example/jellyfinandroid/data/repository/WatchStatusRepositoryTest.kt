package com.example.jellyfinandroid.data.repository

import android.content.Context
import com.example.jellyfinandroid.data.JellyfinServer
import com.example.jellyfinandroid.data.SecureCredentialManager
import com.example.jellyfinandroid.di.JellyfinClientFactory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class WatchStatusRepositoryTest {
    private val clientFactory = mockk<JellyfinClientFactory>(relaxed = true)
    private val credentialManager = mockk<SecureCredentialManager>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private fun createRepositoryWithServer(): JellyfinRepository {
        val server = JellyfinServer(
            id = "a4b6-4a3e-8b0a-0a8b0a0a8b0a",
            name = "Test",
            url = "http://localhost",
            isConnected = true,
            userId = "c8d9-4f7e-9a1b-1b2c3d4e5f6a",
            accessToken = "token",
        )
        val authRepository = mockk<JellyfinAuthRepository>(relaxed = true) {
            every { getCurrentServer() } returns server
            every { currentServer } returns MutableStateFlow(server)
            every { isConnected } returns MutableStateFlow(true)
        }
        val streamRepository = mockk<JellyfinStreamRepository>(relaxed = true)
        return JellyfinRepository(clientFactory, credentialManager, context, authRepository, streamRepository)
    }

    @Test
    fun repository_instantiates() {
        val authRepository = mockk<JellyfinAuthRepository>(relaxed = true) {
            every { currentServer } returns MutableStateFlow(null)
            every { isConnected } returns MutableStateFlow(false)
            every { getCurrentServer() } returns null
            every { isUserAuthenticated() } returns false
        }
        val streamRepository = mockk<JellyfinStreamRepository>(relaxed = true)
        val repository = JellyfinRepository(clientFactory, credentialManager, context, authRepository, streamRepository)
        assertNotNull(repository)
    }

    @Test
    fun markAsWatched_returnsErrorWhenApiUnavailable() = runTest {
        val repository = createRepositoryWithServer()
        val itemId = UUID.randomUUID()

        val result = repository.markAsWatched(itemId.toString())

        // Without explicit API stubs, this should surface as an Error path gracefully
        assertTrue(result is ApiResult.Error)
    }

    @Test
    fun markAsUnwatched_returnsErrorWhenApiUnavailable() = runTest {
        val repository = createRepositoryWithServer()
        val itemId = UUID.randomUUID()

        val result = repository.markAsUnwatched(itemId.toString())

        assertTrue(result is ApiResult.Error)
    }
}
