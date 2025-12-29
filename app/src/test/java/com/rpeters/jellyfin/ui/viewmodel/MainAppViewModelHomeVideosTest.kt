package com.rpeters.jellyfin.ui.viewmodel

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.common.TestDispatcherProvider
import com.rpeters.jellyfin.data.repository.JellyfinAuthRepository
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.JellyfinSearchRepository
import com.rpeters.jellyfin.data.repository.JellyfinStreamRepository
import com.rpeters.jellyfin.data.repository.JellyfinUserRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MainAppViewModelHomeVideosTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

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
    private lateinit var castManager: com.rpeters.jellyfin.ui.player.CastManager

    @MockK
    private lateinit var context: Context

    private lateinit var viewModel: MainAppViewModel
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(dispatcher)

        coEvery { mediaRepository.getUserLibraries() } returns ApiResult.Success(emptyList())

        every { repository.currentServer } returns MutableStateFlow(null)
        every { repository.isConnected } returns MutableStateFlow(true)

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
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `home video library only exposes its own items`() = runTest {
        val libraryA = "libA"
        val libraryB = "libB"
        val itemA = BaseItemDto(id = UUID.randomUUID(), name = "A", type = BaseItemKind.VIDEO)
        val itemB = BaseItemDto(id = UUID.randomUUID(), name = "B", type = BaseItemKind.VIDEO)

        coEvery {
            mediaRepository.getLibraryItems(
                parentId = libraryA,
                itemTypes = "Book,AudioBook,Video",
                startIndex = 0,
                limit = 100,
                collectionType = null,
            )
        } returns ApiResult.Success(listOf(itemA))
        coEvery {
            mediaRepository.getLibraryItems(
                parentId = libraryB,
                itemTypes = "Book,AudioBook,Video",
                startIndex = 0,
                limit = 100,
                collectionType = null,
            )
        } returns ApiResult.Success(listOf(itemB))

        viewModel.loadHomeVideos(libraryA)
        viewModel.loadHomeVideos(libraryB)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.appState.value
        assertEquals(listOf(itemA), state.itemsByLibrary[libraryA])
        assertEquals(listOf(itemB), state.itemsByLibrary[libraryB])
        assertTrue(state.itemsByLibrary[libraryA]?.none { it.name == "B" } == true)
        assertTrue(state.itemsByLibrary[libraryB]?.none { it.name == "A" } == true)
    }
}
