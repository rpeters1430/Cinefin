package com.rpeters.jellyfin.ui.viewmodel

import android.content.Context
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.repository.JellyfinAuthRepository
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.JellyfinSearchRepository
import com.rpeters.jellyfin.data.repository.JellyfinStreamRepository
import com.rpeters.jellyfin.data.repository.JellyfinUserRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import com.rpeters.jellyfin.ui.player.CastManager
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for MainAppViewModel delete and refresh operations.
 * Tests deleteItem and refreshItemMetadata methods with success and error scenarios.
 */
@OptIn(ExperimentalCoroutinesApi::class, androidx.media3.common.util.UnstableApi::class)
class MainAppViewModelDeleteItemTest {

    @MockK
    private lateinit var repository: JellyfinRepository

    @MockK
    private lateinit var authRepository: JellyfinAuthRepository

    @MockK
    private lateinit var mediaRepository: JellyfinMediaRepository

    @MockK
    private lateinit var userRepository: JellyfinUserRepository

    @MockK
    private lateinit var streamRepository: JellyfinStreamRepository

    @MockK
    private lateinit var searchRepository: JellyfinSearchRepository

    @MockK
    private lateinit var credentialManager: SecureCredentialManager

    @MockK
    private lateinit var castManager: CastManager

    @MockK
    private lateinit var context: Context

    private lateinit var viewModel: MainAppViewModel
    private val dispatcher = UnconfinedTestDispatcher()

    private val testItemId = UUID.randomUUID()
    private val testItem = mockk<BaseItemDto>(relaxed = true).apply {
        coEvery { id } returns testItemId
        coEvery { name } returns "Test Movie"
        coEvery { type } returns BaseItemKind.MOVIE
    }

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(dispatcher)

        // Setup default mock behaviors
        coEvery { repository.currentServer } returns MutableStateFlow(null)
        coEvery { repository.isConnected } returns MutableStateFlow(false)

        viewModel = MainAppViewModel(
            context = context,
            repository = repository,
            authRepository = authRepository,
            mediaRepository = mediaRepository,
            userRepository = userRepository,
            streamRepository = streamRepository,
            searchRepository = searchRepository,
            credentialManager = credentialManager,
            castManager = castManager,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========================================================================
    // DELETE ITEM - SUCCESS TESTS
    // ========================================================================

    @Test
    fun `deleteItem with valid item calls repository and returns success`() = runTest {
        // Given
        coEvery { userRepository.deleteItemAsAdmin(testItemId.toString()) } returns ApiResult.Success(true)
        var callbackSuccess: Boolean? = null
        var callbackMessage: String? = null

        // When
        viewModel.deleteItem(testItem) { success, message ->
            callbackSuccess = success
            callbackMessage = message
        }

        // Then
        assertTrue(callbackSuccess == true)
        assertNull(callbackMessage)
    }

    @Test
    fun `deleteItem removes item from all state lists`() = runTest {
        // Given
        coEvery { userRepository.deleteItemAsAdmin(testItemId.toString()) } returns ApiResult.Success(true)

        // Add item to state
        val initialState = viewModel.appState.value.copy(
            allMovies = listOf(testItem),
            allTVShows = listOf(),
            allItems = listOf(testItem),
            recentlyAdded = listOf(testItem),
            favorites = listOf(testItem),
            searchResults = listOf(testItem),
        )
        viewModel.setAppStateForTest(initialState)

        // When
        viewModel.deleteItem(testItem) { _, _ -> }

        // Then
        val finalState = viewModel.appState.value
        assertFalse(finalState.allMovies.contains(testItem))
        assertFalse(finalState.allItems.contains(testItem))
        assertFalse(finalState.recentlyAdded.contains(testItem))
        assertFalse(finalState.favorites.contains(testItem))
        assertFalse(finalState.searchResults.contains(testItem))
    }

    @Test
    fun `deleteItem removes item from itemsByLibrary map`() = runTest {
        // Given
        coEvery { userRepository.deleteItemAsAdmin(testItemId.toString()) } returns ApiResult.Success(true)

        val libraryId = "test-library"
        val anotherItem = mockk<BaseItemDto>(relaxed = true).apply {
            coEvery { id } returns UUID.randomUUID()
        }

        val initialState = viewModel.appState.value.copy(
            itemsByLibrary = mapOf(libraryId to listOf(testItem, anotherItem)),
        )
        viewModel.setAppStateForTest(initialState)

        // When
        viewModel.deleteItem(testItem) { _, _ -> }

        // Then
        val finalLibraryItems = viewModel.appState.value.itemsByLibrary[libraryId]
        assertFalse(finalLibraryItems?.contains(testItem) == true)
        assertTrue(finalLibraryItems?.contains(anotherItem) == true)
    }

    // ========================================================================
    // DELETE ITEM - ERROR TESTS
    // ========================================================================

    @Test
    fun `deleteItem handles repository error`() = runTest {
        // Given
        val errorMessage = "Administrator permissions required"
        coEvery {
            userRepository.deleteItemAsAdmin(testItemId.toString())
        } returns ApiResult.Error(errorMessage, errorType = ErrorType.FORBIDDEN)

        var callbackSuccess: Boolean? = null
        var callbackMessage: String? = null

        // When
        viewModel.deleteItem(testItem) { success, message ->
            callbackSuccess = success
            callbackMessage = message
        }

        // Then
        assertFalse(callbackSuccess == true)
        assertEquals(errorMessage, callbackMessage)
    }

    @Test
    fun `deleteItem sets error message in state on failure`() = runTest {
        // Given
        val errorMessage = "Network error"
        coEvery {
            userRepository.deleteItemAsAdmin(testItemId.toString())
        } returns ApiResult.Error(errorMessage, errorType = ErrorType.NETWORK)

        // When
        viewModel.deleteItem(testItem) { _, _ -> }

        // Then
        val finalState = viewModel.appState.value
        assertEquals("Failed to delete item: $errorMessage", finalState.errorMessage)
    }

    // ========================================================================
    // REFRESH ITEM METADATA - SUCCESS TESTS
    // ========================================================================

    @Test
    fun `refreshItemMetadata with valid item calls repository and returns success`() = runTest {
        // Given
        coEvery { userRepository.refreshItemMetadata(testItemId.toString()) } returns ApiResult.Success(true)
        var callbackSuccess: Boolean? = null
        var callbackMessage: String? = null

        // When
        viewModel.refreshItemMetadata(testItem) { success, message ->
            callbackSuccess = success
            callbackMessage = message
        }

        // Then
        assertTrue(callbackSuccess == true)
        assertNull(callbackMessage)
    }

    @Test
    fun `refreshItemMetadata does not modify state on success`() = runTest {
        // Given
        coEvery { userRepository.refreshItemMetadata(testItemId.toString()) } returns ApiResult.Success(true)

        val initialState = viewModel.appState.value.copy(
            allMovies = listOf(testItem),
        )
        viewModel.setAppStateForTest(initialState)

        // When
        viewModel.refreshItemMetadata(testItem) { _, _ -> }

        // Then - state should be unchanged
        val finalState = viewModel.appState.value
        assertTrue(finalState.allMovies.contains(testItem))
    }

    // ========================================================================
    // REFRESH ITEM METADATA - ERROR TESTS
    // ========================================================================

    @Test
    fun `refreshItemMetadata handles repository error`() = runTest {
        // Given
        val errorMessage = "Administrator permissions required for metadata refresh"
        coEvery {
            userRepository.refreshItemMetadata(testItemId.toString())
        } returns ApiResult.Error(errorMessage, errorType = ErrorType.FORBIDDEN)

        var callbackSuccess: Boolean? = null
        var callbackMessage: String? = null

        // When
        viewModel.refreshItemMetadata(testItem) { success, message ->
            callbackSuccess = success
            callbackMessage = message
        }

        // Then
        assertFalse(callbackSuccess == true)
        assertEquals(errorMessage, callbackMessage)
    }

    @Test
    fun `refreshItemMetadata sets error message in state on failure`() = runTest {
        // Given
        val errorMessage = "Network error"
        coEvery {
            userRepository.refreshItemMetadata(testItemId.toString())
        } returns ApiResult.Error(errorMessage, errorType = ErrorType.NETWORK)

        // When
        viewModel.refreshItemMetadata(testItem) { _, _ -> }

        // Then
        val finalState = viewModel.appState.value
        assertEquals("Failed to refresh metadata: $errorMessage", finalState.errorMessage)
    }

    // ========================================================================
    // MULTIPLE OPERATIONS TESTS
    // ========================================================================

    @Test
    fun `multiple deleteItem calls handle different items correctly`() = runTest {
        // Given
        val item1Id = UUID.randomUUID()
        val item2Id = UUID.randomUUID()
        val item1 = mockk<BaseItemDto>(relaxed = true).apply { coEvery { id } returns item1Id }
        val item2 = mockk<BaseItemDto>(relaxed = true).apply { coEvery { id } returns item2Id }

        coEvery { userRepository.deleteItemAsAdmin(item1Id.toString()) } returns ApiResult.Success(true)
        coEvery { userRepository.deleteItemAsAdmin(item2Id.toString()) } returns ApiResult.Success(true)

        var successCount = 0

        // When
        viewModel.deleteItem(item1) { success, _ -> if (success) successCount++ }
        viewModel.deleteItem(item2) { success, _ -> if (success) successCount++ }

        // Then
        assertEquals(2, successCount)
    }

    @Test
    fun `deleteItem followed by refreshItemMetadata on different items work correctly`() = runTest {
        // Given
        val item1Id = UUID.randomUUID()
        val item2Id = UUID.randomUUID()
        val item1 = mockk<BaseItemDto>(relaxed = true).apply { coEvery { id } returns item1Id }
        val item2 = mockk<BaseItemDto>(relaxed = true).apply { coEvery { id } returns item2Id }

        coEvery { userRepository.deleteItemAsAdmin(item1Id.toString()) } returns ApiResult.Success(true)
        coEvery { userRepository.refreshItemMetadata(item2Id.toString()) } returns ApiResult.Success(true)

        var deleteSuccess = false
        var refreshSuccess = false

        // When
        viewModel.deleteItem(item1) { success, _ -> deleteSuccess = success }
        viewModel.refreshItemMetadata(item2) { success, _ -> refreshSuccess = success }

        // Then
        assertTrue(deleteSuccess)
        assertTrue(refreshSuccess)
    }
}
