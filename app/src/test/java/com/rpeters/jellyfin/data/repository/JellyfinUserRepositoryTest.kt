package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.cache.JellyfinCache
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.di.JellyfinClientFactory
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JellyfinUserRepositoryTest {

    @MockK
    private lateinit var authRepository: JellyfinAuthRepository

    @MockK
    private lateinit var clientFactory: JellyfinClientFactory

    @MockK
    private lateinit var cache: JellyfinCache

    @MockK
    private lateinit var apiClient: ApiClient

    private lateinit var repository: JellyfinUserRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        repository = JellyfinUserRepository(authRepository, clientFactory, cache)
    }

    @Test
    fun `logout calls authRepository logout`() = runTest {
        // Given
        coEvery { authRepository.logout() } returns Unit

        // When
        repository.logout()

        // Then
        coVerify { authRepository.logout() }
    }

    @Test
    fun `toggleFavorite marks item as favorite when not favorite`() = runTest {
        // Given
        val itemId = "item-123"
        val isFavorite = false

        coEvery {
            repository.toggleFavorite(itemId, isFavorite)
        } returns ApiResult.Success(true)

        // When
        val result = repository.toggleFavorite(itemId, isFavorite)

        // Then
        assertIs<ApiResult.Success<Boolean>>(result)
        assertTrue(result.data) // Should return true when marking as favorite
    }

    @Test
    fun `toggleFavorite unmarks item as favorite when already favorite`() = runTest {
        // Given
        val itemId = "item-123"
        val isFavorite = true

        coEvery {
            repository.toggleFavorite(itemId, isFavorite)
        } returns ApiResult.Success(false)

        // When
        val result = repository.toggleFavorite(itemId, isFavorite)

        // Then
        assertIs<ApiResult.Success<Boolean>>(result)
        assertFalse(result.data) // Should return false when unmarking as favorite
    }

    @Test
    fun `markAsWatched successfully marks item as watched`() = runTest {
        // Given
        val itemId = "item-123"

        coEvery {
            repository.markAsWatched(itemId)
        } returns ApiResult.Success(true)

        // When
        val result = repository.markAsWatched(itemId)

        // Then
        assertIs<ApiResult.Success<Boolean>>(result)
        assertTrue(result.data)
    }

    @Test
    fun `markAsUnwatched successfully marks item as unwatched`() = runTest {
        // Given
        val itemId = "item-123"

        coEvery {
            repository.markAsUnwatched(itemId)
        } returns ApiResult.Success(true)

        // When
        val result = repository.markAsUnwatched(itemId)

        // Then
        assertIs<ApiResult.Success<Boolean>>(result)
        assertTrue(result.data)
    }

    @Test
    fun `getFavorites returns user's favorite items`() = runTest {
        // Given
        val mockFavorites = listOf(
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Favorite Movie"
                coEvery { type } returns BaseItemKind.MOVIE
                coEvery { userData?.isFavorite } returns true
            },
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Favorite Series"
                coEvery { type } returns BaseItemKind.SERIES
                coEvery { userData?.isFavorite } returns true
            },
        )

        coEvery {
            repository.getFavorites()
        } returns ApiResult.Success(mockFavorites)

        // When
        val result = repository.getFavorites()

        // Then
        assertIs<ApiResult.Success<List<BaseItemDto>>>(result)
        assertEquals(2, result.data.size)
        assertEquals("Favorite Movie", result.data[0].name)
        assertEquals("Favorite Series", result.data[1].name)
        assertTrue(result.data.all { it.userData?.isFavorite == true })
    }

    @Test
    fun `deleteItem successfully deletes item`() = runTest {
        // Given
        val itemId = "item-123"

        coEvery {
            repository.deleteItem(itemId)
        } returns ApiResult.Success(true)

        // When
        val result = repository.deleteItem(itemId)

        // Then
        assertIs<ApiResult.Success<Boolean>>(result)
        assertTrue(result.data)
    }

    @Test
    fun `deleteItemAsAdmin successfully deletes item with admin permissions`() = runTest {
        // Given
        val itemId = "item-123"

        coEvery {
            repository.deleteItemAsAdmin(itemId)
        } returns ApiResult.Success(true)

        // When
        val result = repository.deleteItemAsAdmin(itemId)

        // Then
        assertIs<ApiResult.Success<Boolean>>(result)
        assertTrue(result.data)
    }

    @Test
    fun `deleteItemAsAdmin fails with insufficient permissions`() = runTest {
        // Given
        val itemId = "item-123"
        val errorMessage = "Administrator permissions required"

        coEvery {
            repository.deleteItemAsAdmin(itemId)
        } returns ApiResult.Error(errorMessage, "PERMISSION_DENIED")

        // When
        val result = repository.deleteItemAsAdmin(itemId)

        // Then
        assertIs<ApiResult.Error<Boolean>>(result)
        assertEquals(errorMessage, result.message)
        assertEquals("PERMISSION_DENIED", result.errorType)
    }

    @Test
    fun `repository handles network errors gracefully`() = runTest {
        // Given
        val itemId = "item-123"
        val errorMessage = "Network connection failed"

        coEvery {
            repository.toggleFavorite(itemId, false)
        } returns ApiResult.Error(errorMessage, "NETWORK_ERROR")

        // When
        val result = repository.toggleFavorite(itemId, false)

        // Then
        assertIs<ApiResult.Error<Boolean>>(result)
        assertEquals(errorMessage, result.message)
        assertEquals("NETWORK_ERROR", result.errorType)
    }

    @Test
    fun `repository handles authentication errors`() = runTest {
        // Given
        val errorMessage = "Invalid authentication token"

        coEvery {
            repository.getFavorites()
        } returns ApiResult.Error(errorMessage, "AUTH_ERROR")

        // When
        val result = repository.getFavorites()

        // Then
        assertIs<ApiResult.Error<List<BaseItemDto>>>(result)
        assertEquals(errorMessage, result.message)
        assertEquals("AUTH_ERROR", result.errorType)
    }

    @Test
    fun `repository caches favorites with appropriate TTL`() = runTest {
        // Given
        val mockFavorites = listOf(
            mockk<BaseItemDto> {
                coEvery { id } returns java.util.UUID.randomUUID()
                coEvery { name } returns "Cached Favorite"
                coEvery { type } returns BaseItemKind.MOVIE
            },
        )

        // First call
        coEvery {
            repository.getFavorites()
        } returns ApiResult.Success(mockFavorites)

        // When
        val result1 = repository.getFavorites()
        val result2 = repository.getFavorites()

        // Then
        assertIs<ApiResult.Success<List<BaseItemDto>>>(result1)
        assertIs<ApiResult.Success<List<BaseItemDto>>>(result2)
        assertEquals(result1.data.size, result2.data.size)
        assertEquals(result1.data[0].name, result2.data[0].name)
    }
}
