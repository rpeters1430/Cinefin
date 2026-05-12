package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.preferences.SeerrPreferences
import com.rpeters.jellyfin.data.preferences.SeerrPreferencesRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import com.rpeters.jellyfin.data.model.SeerrSearchResult
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SeerrRepositoryTest {

    private lateinit var apiService: SeerrApiService
    private lateinit var preferencesRepository: SeerrPreferencesRepository
    private lateinit var repository: SeerrRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        apiService = mockk()
        preferencesRepository = mockk()
        val okHttpClient = mockk<okhttp3.OkHttpClient>()
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        
        every { preferencesRepository.seerrPreferencesFlow } returns flowOf(
            SeerrPreferences(baseUrl = "https://seerr.example.com", apiKey = "test-api-key", isEnabled = true)
        )

        repository = object : SeerrRepository(okHttpClient, preferencesRepository, json) {
            override suspend fun getConnection(): SeerrConnection? = SeerrConnection(apiService, "test-api-key")
        }
    }

    @Test
    fun `search returns success when api call succeeds`() = runTest {
        val mockResult = SeerrSearchResult(page = 1, totalPages = 1, totalResults = 1, results = emptyList())
        coEvery { apiService.search(any(), any(), any()) } returns Response.success(mockResult)

        val result = repository.search("matrix")

        assertTrue(result is ApiResult.Success)
        assertEquals(mockResult, (result as ApiResult.Success).data)
    }

    @Test
    fun `search retries on network error`() = runTest {
        val mockResult = SeerrSearchResult(page = 1, totalPages = 1, totalResults = 1, results = emptyList())
        
        // Fail twice, then succeed
        coEvery { apiService.search(any(), any(), any()) } throws java.io.IOException("Network error") andThenThrows java.io.IOException("Network error") andThen Response.success(mockResult)

        val result = repository.search("matrix")

        assertTrue(result is ApiResult.Success)
        assertEquals(mockResult, (result as ApiResult.Success).data)
    }

    @Test
    fun `search returns error after max retries`() = runTest {
        coEvery { apiService.search(any(), any(), any()) } throws java.io.IOException("Network error")

        val result = repository.search("matrix")

        assertTrue(result is ApiResult.Error)
        assertEquals(ErrorType.NETWORK, (result as ApiResult.Error).errorType)
    }

    @Test
    fun `getTrending returns success when api call succeeds`() = runTest {
        val mockResult = SeerrSearchResult(page = 1, totalPages = 1, totalResults = 1, results = emptyList())
        coEvery { apiService.getTrending(any(), any()) } returns Response.success(mockResult)

        val result = repository.getTrending()

        assertTrue(result is ApiResult.Success)
        assertEquals(mockResult, (result as ApiResult.Success).data)
    }
}
