package com.example.jellyfinandroid.data.repository

import android.content.Context
import com.example.jellyfinandroid.data.JellyfinServer
import com.example.jellyfinandroid.data.SecureCredentialManager
import com.example.jellyfinandroid.di.JellyfinClientFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.apis.UserLibraryApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class WatchStatusRepositoryTest {
    private val clientFactory = mockk<JellyfinClientFactory>()
    private val credentialManager = mockk<SecureCredentialManager>()
    private val context = mockk<Context>(relaxed = true)
    private val client = mockk<ApiClient>(relaxed = true)
    private val userLibraryApi = mockk<UserLibraryApi>(relaxed = true)

    private val repository by lazy {
        JellyfinRepository(clientFactory, credentialManager, context).apply {
            val server = JellyfinServer(
                id = UUID.randomUUID().toString(),
                name = "Test",
                url = "http://localhost",
                isConnected = true,
                userId = UUID.randomUUID().toString(),
                accessToken = "token",
            )
            setCurrentServerForTest(server)
        }
    }

    @Before
    fun setUp() {
        every { client.userLibraryApi } returns userLibraryApi
        coEvery { clientFactory.getClient(any(), any()) } returns client
    }

    @Test
    fun markAsWatched_callsApi() = runTest {
        val itemId = UUID.randomUUID()

        val result = repository.markAsWatched(itemId.toString())

        coVerify { userLibraryApi.markItemAsPlayed(itemId, any()) }
        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun markAsWatched_handlesError() = runTest {
        val itemId = UUID.randomUUID()
        coEvery { userLibraryApi.markItemAsPlayed(any(), any()) } throws RuntimeException("boom")

        val result = repository.markAsWatched(itemId.toString())

        assertTrue(result is ApiResult.Error)
    }

    @Test
    fun markAsUnwatched_callsApi() = runTest {
        val itemId = UUID.randomUUID()

        val result = repository.markAsUnwatched(itemId.toString())

        coVerify { userLibraryApi.markItemAsUnplayed(itemId, any()) }
        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun markAsUnwatched_handlesError() = runTest {
        val itemId = UUID.randomUUID()
        coEvery { userLibraryApi.markItemAsUnplayed(any(), any()) } throws RuntimeException("boom")

        val result = repository.markAsUnwatched(itemId.toString())

        assertTrue(result is ApiResult.Error)
    }
}
