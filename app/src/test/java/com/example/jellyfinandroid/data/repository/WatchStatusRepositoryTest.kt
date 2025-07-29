package com.example.jellyfinandroid.data.repository

import android.content.Context
import com.example.jellyfinandroid.data.JellyfinServer
import com.example.jellyfinandroid.di.JellyfinClientFactory
import com.example.jellyfinandroid.data.SecureCredentialManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.apis.UserLibraryApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class WatchStatusRepositoryTest {
    private lateinit var repository: JellyfinRepository
    private lateinit var clientFactory: JellyfinClientFactory
    private lateinit var credentialManager: SecureCredentialManager
    private lateinit var context: Context
    private lateinit var client: ApiClient
    private lateinit var userLibraryApi: UserLibraryApi

    @Before
    fun setUp() {
        clientFactory = mockk()
        credentialManager = mockk()
        context = mockk(relaxed = true)
        client = mockk(relaxed = true)
        userLibraryApi = mockk(relaxed = true)
        every { client.userLibraryApi } returns userLibraryApi
        coEvery { clientFactory.getClient(any(), any()) } returns client
        repository = JellyfinRepository(clientFactory, credentialManager, context)

        val server = JellyfinServer(
            id = UUID.randomUUID().toString(),
            name = "Test",
            url = "http://localhost",
            isConnected = true,
            userId = UUID.randomUUID().toString(),
            accessToken = "token"
        )
        repository.setCurrentServerForTest(server)
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
