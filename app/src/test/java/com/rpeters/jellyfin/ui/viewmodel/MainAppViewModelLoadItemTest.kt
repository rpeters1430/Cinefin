package com.rpeters.jellyfin.ui.viewmodel

import android.content.Context
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.common.TestDispatcherProvider
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
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for MainAppViewModel.loadItemById().
 * Verifies that selectedItem is set on success and remains null on error.
 */
@OptIn(ExperimentalCoroutinesApi::class, androidx.media3.common.util.UnstableApi::class)
class MainAppViewModelLoadItemTest {

    // Use relaxed mock so ViewModel init block (connectivityChecker, etc.) doesn't throw
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
    private lateinit var generativeAiRepository: com.rpeters.jellyfin.data.repository.GenerativeAiRepository

    @MockK
    private lateinit var analyticsHelper: com.rpeters.jellyfin.utils.AnalyticsHelper

    @MockK
    private lateinit var context: Context

    private lateinit var viewModel: MainAppViewModel
    private val dispatcher = StandardTestDispatcher()

    private val fakeItemId = UUID.randomUUID()
    private val fakeItem = BaseItemDto(
        id = fakeItemId,
        name = "Stranger Things",
        type = BaseItemKind.SERIES,
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(dispatcher)

        // Relaxed mock: init block accesses connectivityChecker without explicit stubs
        repository = mockk(relaxed = true)

        // Use every{} (not coEvery{}) for non-suspend Flow properties
        every { repository.currentServer } returns MutableStateFlow(null)
        every { repository.isConnected } returns MutableStateFlow(false)

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
            dispatchers = TestDispatcherProvider(dispatcher),
            generativeAiRepository = generativeAiRepository,
            analytics = analyticsHelper,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========================================================================
    // loadItemById - SUCCESS
    // ========================================================================

    @Test
    fun `loadItemById_success_setsSelectedItemInState`() = runTest(dispatcher) {
        // Given
        coEvery { repository.getItemDetails(fakeItemId.toString()) } returns ApiResult.Success(fakeItem)

        // When
        viewModel.loadItemById(fakeItemId.toString())
        advanceUntilIdle()

        // Then
        val state = viewModel.appState.value
        assertEquals(fakeItem, state.selectedItem)
    }

    @Test
    fun `loadItemById_success_doesNotClearOtherStateFields`() = runTest(dispatcher) {
        // Given
        val existingMovie = BaseItemDto(id = UUID.randomUUID(), name = "Existing Movie", type = BaseItemKind.MOVIE)
        viewModel.setAppStateForTest(viewModel.appState.value.copy(allMovies = listOf(existingMovie)))

        coEvery { repository.getItemDetails(fakeItemId.toString()) } returns ApiResult.Success(fakeItem)

        // When
        viewModel.loadItemById(fakeItemId.toString())
        advanceUntilIdle()

        // Then – selectedItem is set AND existing state is preserved
        val state = viewModel.appState.value
        assertEquals(fakeItem, state.selectedItem)
        assertEquals(listOf(existingMovie), state.allMovies)
    }

    // ========================================================================
    // loadItemById - ERROR
    // ========================================================================

    @Test
    fun `loadItemById_networkError_selectedItemRemainsNull`() = runTest(dispatcher) {
        // Given
        coEvery {
            repository.getItemDetails(fakeItemId.toString())
        } returns ApiResult.Error("Network failure", errorType = ErrorType.NETWORK)

        // When
        viewModel.loadItemById(fakeItemId.toString())
        advanceUntilIdle()

        // Then
        assertNull(viewModel.appState.value.selectedItem)
    }

    @Test
    fun `loadItemById_notFoundError_selectedItemRemainsNull`() = runTest(dispatcher) {
        // Given
        coEvery {
            repository.getItemDetails(fakeItemId.toString())
        } returns ApiResult.Error("Item not found", errorType = ErrorType.NOT_FOUND)

        // When
        viewModel.loadItemById(fakeItemId.toString())
        advanceUntilIdle()

        // Then
        assertNull(viewModel.appState.value.selectedItem)
    }

    @Test
    fun `loadItemById_error_doesNotOverwritePreviousSelectedItem`() = runTest(dispatcher) {
        // Given – a previously fetched item is already in state
        viewModel.setAppStateForTest(viewModel.appState.value.copy(selectedItem = fakeItem))

        val differentId = UUID.randomUUID().toString()
        coEvery {
            repository.getItemDetails(differentId)
        } returns ApiResult.Error("Server error", errorType = ErrorType.SERVER_ERROR)

        // When
        viewModel.loadItemById(differentId)
        advanceUntilIdle()

        // Then – error path leaves state untouched; previous selectedItem is preserved
        assertEquals(fakeItem, viewModel.appState.value.selectedItem)
    }
}
