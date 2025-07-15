package com.example.jellyfinandroid.data.repository

import com.example.jellyfinandroid.data.JellyfinServer
import com.example.jellyfinandroid.data.SecureCredentialManager
import com.example.jellyfinandroid.data.model.QuickConnectConstants
import com.example.jellyfinandroid.di.JellyfinClientFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response as RetrofitResponse
import java.util.UUID

/**
 * Comprehensive test suite for JellyfinRepository.
 * 
 * Tests authentication, server connections, data fetching, user actions,
 * error handling, and security-related functionality.
 */
class JellyfinRepositoryTest {

    private lateinit var repository: JellyfinRepository
    private lateinit var mockClientFactory: JellyfinClientFactory
    private lateinit var mockCredentialManager: SecureCredentialManager
    private lateinit var mockApiClient: ApiClient
    private lateinit var mockSystemApi: org.jellyfin.sdk.api.client.extensions.SystemApi
    private lateinit var mockUserApi: org.jellyfin.sdk.api.client.extensions.UserApi
    private lateinit var mockItemsApi: org.jellyfin.sdk.api.client.extensions.ItemsApi

    @Before
    fun setup() {
        mockClientFactory = mockk()
        mockCredentialManager = mockk()
        mockApiClient = mockk()
        mockSystemApi = mockk()
        mockUserApi = mockk()
        mockItemsApi = mockk()

        every { mockApiClient.systemApi } returns mockSystemApi
        every { mockApiClient.userApi } returns mockUserApi
        every { mockApiClient.itemsApi } returns mockItemsApi
        every { mockClientFactory.getClient(any(), any()) } returns mockApiClient

        repository = JellyfinRepository(mockClientFactory, mockCredentialManager)
    }

    @Test
    fun `testServerConnection returns success for valid server`() = runTest {
        // Arrange
        val serverUrl = "https://demo.jellyfin.org"
        val mockSystemInfo = PublicSystemInfo(
            serverName = "Demo Server",
            version = "10.8.0"
        )
        val mockResponse = mockk<Response<PublicSystemInfo>>()
        every { mockResponse.content } returns mockSystemInfo
        coEvery { mockSystemApi.getPublicSystemInfo() } returns mockResponse

        // Act
        val result = repository.testServerConnection(serverUrl)

        // Assert
        assertTrue("Result should be success", result is ApiResult.Success)
        assertEquals("Should return system info", mockSystemInfo, (result as ApiResult.Success).data)
        coVerify { mockSystemApi.getPublicSystemInfo() }
    }

    @Test
    fun `testServerConnection returns error for network failure`() = runTest {
        // Arrange
        val serverUrl = "https://invalid.server.com"
        coEvery { mockSystemApi.getPublicSystemInfo() } throws java.net.UnknownHostException("Host not found")

        // Act
        val result = repository.testServerConnection(serverUrl)

        // Assert
        assertTrue("Result should be error", result is ApiResult.Error)
        assertEquals("Error type should be NETWORK", ErrorType.NETWORK, (result as ApiResult.Error).errorType)
        assertTrue("Error message should mention connection", result.message.contains("connect"))
    }

    @Test
    fun `getUserLibraries returns success with libraries`() = runTest {
        // Arrange
        setupAuthenticatedState()
        val mockLibraries = listOf(
            createMockLibrary("Movies", "movies"),
            createMockLibrary("TV Shows", "tvshows")
        )
        val mockQueryResult = BaseItemDtoQueryResult(
            items = mockLibraries,
            totalRecordCount = mockLibraries.size
        )
        val mockResponse = mockk<Response<BaseItemDtoQueryResult>>()
        every { mockResponse.content } returns mockQueryResult
        coEvery { mockItemsApi.getItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns mockResponse

        // Act
        val result = repository.getUserLibraries()

        // Assert
        assertTrue("Result should be success", result is ApiResult.Success)
        assertEquals("Should return 2 libraries", 2, (result as ApiResult.Success).data.size)
        assertEquals("First library should be Movies", "Movies", result.data[0].name)
    }

    @Test
    fun `getUserLibraries returns error when not authenticated`() = runTest {
        // Act - repository starts without authentication
        val result = repository.getUserLibraries()

        // Assert
        assertTrue("Result should be error", result is ApiResult.Error)
        assertEquals("Error type should be AUTHENTICATION", ErrorType.AUTHENTICATION, (result as ApiResult.Error).errorType)
        assertTrue("Error message should mention authentication", result.message.contains("authenticated"))
    }

    @Test
    fun `toggleFavorite marks item as favorite successfully`() = runTest {
        // Arrange
        setupAuthenticatedState()
        val itemId = UUID.randomUUID().toString()
        val userId = UUID.randomUUID()
        val itemUuid = UUID.fromString(itemId)
        val mockResponse = mockk<Response<Unit>>()
        every { mockResponse.content } returns Unit
        coEvery { mockUserApi.markFavoriteItem(userId, itemUuid) } returns mockResponse

        // Act
        val result = repository.toggleFavorite(itemId, true)

        // Assert
        assertTrue("Result should be success", result is ApiResult.Success)
        assertEquals("Should return false (new state)", false, (result as ApiResult.Success).data)
        coVerify { mockUserApi.markFavoriteItem(userId, itemUuid) }
    }

    @Test
    fun `toggleFavorite removes item from favorites successfully`() = runTest {
        // Arrange
        setupAuthenticatedState()
        val itemId = UUID.randomUUID().toString()
        val userId = UUID.randomUUID()
        val itemUuid = UUID.fromString(itemId)
        val mockResponse = mockk<Response<Unit>>()
        every { mockResponse.content } returns Unit
        coEvery { mockUserApi.unmarkFavoriteItem(userId, itemUuid) } returns mockResponse

        // Act
        val result = repository.toggleFavorite(itemId, false)

        // Assert
        assertTrue("Result should be success", result is ApiResult.Success)
        assertEquals("Should return true (new state)", true, (result as ApiResult.Success).data)
        coVerify { mockUserApi.unmarkFavoriteItem(userId, itemUuid) }
    }

    @Test
    fun `markAsWatched updates playback status successfully`() = runTest {
        // Arrange
        setupAuthenticatedState()
        val itemId = UUID.randomUUID().toString()
        val userId = UUID.randomUUID()
        val itemUuid = UUID.fromString(itemId)
        val mockResponse = mockk<Response<Unit>>()
        every { mockResponse.content } returns Unit
        coEvery { mockUserApi.markPlayedItem(userId, itemUuid) } returns mockResponse

        // Act
        val result = repository.markAsWatched(itemId)

        // Assert
        assertTrue("Result should be success", result is ApiResult.Success)
        assertTrue("Should return true", (result as ApiResult.Success).data)
        coVerify { mockUserApi.markPlayedItem(userId, itemUuid) }
    }

    @Test
    fun `markAsUnwatched updates playback status successfully`() = runTest {
        // Arrange
        setupAuthenticatedState()
        val itemId = UUID.randomUUID().toString()
        val userId = UUID.randomUUID()
        val itemUuid = UUID.fromString(itemId)
        val mockResponse = mockk<Response<Unit>>()
        every { mockResponse.content } returns Unit
        coEvery { mockUserApi.markUnplayedItem(userId, itemUuid) } returns mockResponse

        // Act
        val result = repository.markAsUnwatched(itemId)

        // Assert
        assertTrue("Result should be success", result is ApiResult.Success)
        assertTrue("Should return true", (result as ApiResult.Success).data)
        coVerify { mockUserApi.markUnplayedItem(userId, itemUuid) }
    }

    @Test
    fun `getStreamUrl returns valid URL for authenticated user`() {
        // Arrange
        setupAuthenticatedState()
        val itemId = UUID.randomUUID().toString()

        // Act
        val streamUrl = repository.getStreamUrl(itemId)

        // Assert
        assertNotNull("Stream URL should not be null", streamUrl)
        assertTrue("URL should contain item ID", streamUrl!!.contains(itemId))
        assertTrue("URL should contain stream endpoint", streamUrl.contains("/Videos/$itemId/stream"))
        assertTrue("URL should contain API key", streamUrl.contains("api_key="))
    }

    @Test
    fun `getStreamUrl returns null when not authenticated`() {
        // Act - repository starts without authentication
        val streamUrl = repository.getStreamUrl("test-item-id")

        // Assert
        assertNull("Stream URL should be null when not authenticated", streamUrl)
    }

    @Test
    fun `error handling maps HTTP errors correctly`() = runTest {
        // Arrange
        setupAuthenticatedState()
        val unauthorizedResponse = mockk<RetrofitResponse<*>>()
        every { unauthorizedResponse.code() } returns 401
        val httpException = HttpException(unauthorizedResponse)
        coEvery { mockItemsApi.getItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws httpException

        // Act
        val result = repository.getUserLibraries()

        // Assert
        assertTrue("Result should be error", result is ApiResult.Error)
        assertEquals("Error type should be UNAUTHORIZED", ErrorType.UNAUTHORIZED, (result as ApiResult.Error).errorType)
    }

    @Test
    fun `logout clears server state`() {
        // Arrange
        setupAuthenticatedState()

        // Act
        repository.logout()

        // Assert
        assertNull("Current server should be null after logout", repository.getCurrentServer())
        assertFalse("Should not be authenticated after logout", repository.isUserAuthenticated())
    }

    @Test
    fun `generated quick connect codes match allowed characters`() {
        val allowed = QuickConnectConstants.CODE_CHARACTERS.toSet()
        repeat(10) {
            val code = generateCodeForTest()
            assertEquals(QuickConnectConstants.CODE_LENGTH, code.length)
            assertTrue(code.all { it in allowed })
        }
    }

    @Test
    fun `searchItems returns filtered results`() = runTest {
        // Arrange
        setupAuthenticatedState()
        val query = "test movie"
        val mockItems = listOf(
            createMockMovie("Test Movie 1"),
            createMockMovie("Test Movie 2")
        )
        val mockQueryResult = BaseItemDtoQueryResult(
            items = mockItems,
            totalRecordCount = mockItems.size
        )
        val mockResponse = mockk<Response<BaseItemDtoQueryResult>>()
        every { mockResponse.content } returns mockQueryResult
        coEvery { mockItemsApi.getItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns mockResponse

        // Act
        val result = repository.searchItems(query)

        // Assert
        assertTrue("Result should be success", result is ApiResult.Success)
        assertEquals("Should return 2 items", 2, (result as ApiResult.Success).data.size)
        coVerify { mockItemsApi.getItems(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `searchItems returns empty list for blank query`() = runTest {
        // Arrange
        setupAuthenticatedState()

        // Act
        val result = repository.searchItems("")

        // Assert
        assertTrue("Result should be success", result is ApiResult.Success)
        assertTrue("Should return empty list for blank query", (result as ApiResult.Success).data.isEmpty())
    }

    @Test
    fun `getMovieDetails returns movie with media info`() = runTest {
        // Arrange
        setupAuthenticatedState()
        val movie = createMockMovie("Detail Movie")
        val queryResult = BaseItemDtoQueryResult(items = listOf(movie), totalRecordCount = 1)
        val mockResponse = mockk<Response<BaseItemDtoQueryResult>>()
        every { mockResponse.content } returns queryResult
coEvery { mockItemsApi.getItems(userId = any(), ids = any(), additionalFields = any(), limit = any()) } returns mockResponse

        // Act
        val result = repository.getMovieDetails(movie.id.toString())

        // Assert
        assertTrue(result is ApiResult.Success)
        assertEquals(movie.id, (result as ApiResult.Success).data.id)
    }

    private fun setupAuthenticatedState() {
        val mockServer = JellyfinServer(
            id = "test-server-id",
            name = "Test Server",
            url = "https://test.jellyfin.org",
            isConnected = true,
            version = "10.8.0",
            userId = UUID.randomUUID().toString(),
            username = "testuser",
            accessToken = "test-access-token"
        )
        
        // Use reflection to set the private _currentServer field
        val currentServerField = repository.javaClass.getDeclaredField("_currentServer")
        currentServerField.isAccessible = true
        val currentServerFlow = currentServerField.get(repository) as MutableStateFlow<JellyfinServer?>
        currentServerFlow.value = mockServer
    }

    private fun createMockLibrary(name: String, collectionType: String): BaseItemDto {
        return BaseItemDto(
            id = UUID.randomUUID(),
            name = name,
            type = BaseItemKind.COLLECTION_FOLDER,
            collectionType = collectionType
        )
    }

    private fun createMockMovie(name: String): BaseItemDto {
        return BaseItemDto(
            id = UUID.randomUUID(),
            name = name,
            type = BaseItemKind.MOVIE,
            productionYear = 2023
        )
    }

    private fun generateCodeForTest(): String {
        val chars = QuickConnectConstants.CODE_CHARACTERS
        val secureRandom = java.security.SecureRandom()
        return (1..QuickConnectConstants.CODE_LENGTH)
            .map { chars[secureRandom.nextInt(chars.length)] }
            .joinToString("")
    }
}