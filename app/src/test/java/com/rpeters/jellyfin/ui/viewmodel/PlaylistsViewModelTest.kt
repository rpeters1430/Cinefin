package com.rpeters.jellyfin.ui.viewmodel

import com.rpeters.jellyfin.data.repository.IJellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsViewModelTest {

    private val repository: IJellyfinRepository = mockk()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PlaylistsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadPlaylists updates state with loaded playlists and supports filtering`() = runTest(testDispatcher) {
        val playlist1 = BaseItemDto(
            id = UUID.randomUUID(),
            name = "YouTube Music Mix",
            type = BaseItemKind.PLAYLIST,
        )
        val playlist2 = BaseItemDto(
            id = UUID.randomUUID(),
            name = "Documentaries",
            type = BaseItemKind.PLAYLIST,
        )

        coEvery { repository.getPlaylists(any()) } returns ApiResult.Success(listOf(playlist1, playlist2))

        viewModel = PlaylistsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(2, state.playlists.size)
        assertEquals(2, state.filteredPlaylists.size)

        // Filter
        viewModel.setSearchQuery("Music")
        assertEquals(1, viewModel.state.value.filteredPlaylists.size)
        assertEquals("YouTube Music Mix", viewModel.state.value.filteredPlaylists.first().name)
    }

    @Test
    fun `loadPlaylists handles error`() = runTest(testDispatcher) {
        coEvery { repository.getPlaylists(any()) } returns ApiResult.Error("Network error")

        viewModel = PlaylistsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("Network error", state.errorMessage)
        assertTrue(state.playlists.isEmpty())
    }
}
