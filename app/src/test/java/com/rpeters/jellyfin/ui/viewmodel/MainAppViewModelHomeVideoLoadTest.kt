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
import com.rpeters.jellyfin.ui.player.CastManager
import com.rpeters.jellyfin.ui.screens.LibraryType
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.jellyfin.sdk.model.api.CollectionType
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Verifies that loadLibraryTypeData called with a HOMEVIDEOS library
 * issues the API call with itemTypes=null (no item type filter), which
 * is what Jellyfin needs to return all home video items.
 *
 * This is the ViewModel-level contract that TvLibraryScreen's fix relies on.
 */
@OptIn(ExperimentalCoroutinesApi::class, androidx.media3.common.util.UnstableApi::class)
class MainAppViewModelHomeVideoLoadTest {

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
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(testDispatcher)

        repository = mockk(relaxed = true)
        every { repository.currentServer } returns MutableStateFlow(null)
        every { repository.isConnected } returns MutableStateFlow(false)

        every { authRepository.isTokenExpired() } returns false
        coEvery { authRepository.reAuthenticate() } returns true

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
            dispatchers = TestDispatcherProvider(testDispatcher),
            generativeAiRepository = generativeAiRepository,
            analytics = analyticsHelper,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadLibraryTypeData with HOMEVIDEOS library calls API with null itemTypes`() = runTest(testDispatcher) {
        // Given - a real home video library with HOMEVIDEOS collectionType
        val libraryId = UUID.randomUUID()
        val homeVideoLibrary = BaseItemDto(
            id = libraryId,
            name = "Home Videos",
            type = BaseItemKind.COLLECTION_FOLDER,
            collectionType = CollectionType.HOMEVIDEOS,
        )
        coEvery {
            mediaRepository.getLibraryItems(
                parentId = libraryId.toString(),
                itemTypes = null,         // HOMEVIDEOS must NOT filter by item type
                collectionType = "homevideos",
            )
        } returns ApiResult.Success(emptyList())

        // When
        viewModel.loadLibraryTypeData(homeVideoLibrary, LibraryType.STUFF)
        advanceUntilIdle()

        // Then - API was called with itemTypes=null (the HOMEVIDEOS-specific path)
        coVerify(exactly = 1) {
            mediaRepository.getLibraryItems(
                parentId = libraryId.toString(),
                itemTypes = null,
                collectionType = "homevideos",
            )
        }
    }

    @Test
    fun `loadLibraryTypeData with null-collectionType library uses item type filter`() = runTest(testDispatcher) {
        // Given - a library with null collectionType (some Jellyfin servers return this)
        val libraryId = UUID.randomUUID()
        val unknownLibrary = BaseItemDto(
            id = libraryId,
            name = "Unknown Library",
            type = BaseItemKind.COLLECTION_FOLDER,
            collectionType = null,
        )
        coEvery {
            mediaRepository.getLibraryItems(
                parentId = libraryId.toString(),
                itemTypes = any(),
                collectionType = null,
            )
        } returns ApiResult.Success(emptyList())

        // When
        viewModel.loadLibraryTypeData(unknownLibrary, LibraryType.STUFF)
        advanceUntilIdle()

        // Then - API was called with itemTypes filter (not null)
        coVerify(exactly = 1) {
            mediaRepository.getLibraryItems(
                parentId = libraryId.toString(),
                itemTypes = "Book,AudioBook,Video",
                collectionType = null,
            )
        }
    }
}
