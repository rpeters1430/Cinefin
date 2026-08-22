package com.rpeters.jellyfin.ui.viewmodel

import com.rpeters.jellyfin.data.repository.IJellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.jellyfin.sdk.model.api.NameGuidPair
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModelTest {

    private val repository: IJellyfinRepository = mockk()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PlaylistDetailViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = PlaylistDetailViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load successfully loads playlist and items`() = runTest(testDispatcher) {
        val playlistId = UUID.randomUUID().toString()
        val mockUserData = mockk<UserItemDataDto> {
            io.mockk.every { isFavorite } returns true
        }
        val mockStudio = mockk<NameGuidPair> {
            io.mockk.every { name } returns "MKBHD"
        }
        val playlist = BaseItemDto(
            id = UUID.fromString(playlistId),
            name = "YouTube Tech Reviews",
            type = BaseItemKind.PLAYLIST,
            overview = "Top tech reviews downloaded via YouTarr",
            tags = listOf("YouTube", "YouTarr"),
            userData = mockUserData,
        )
        val video1 = BaseItemDto(
            id = UUID.randomUUID(),
            name = "Phone Review 2026",
            type = BaseItemKind.VIDEO,
            studios = listOf(mockStudio),
        )
        val video2 = BaseItemDto(
            id = UUID.randomUUID(),
            name = "Laptop Teardown",
            type = BaseItemKind.VIDEO,
        )
        val items = listOf(video1, video2)

        coEvery { repository.getPlaylistDetails(playlistId) } returns ApiResult.Success(playlist)
        coEvery { repository.getPlaylistItems(playlistId) } returns ApiResult.Success(items)

        viewModel.load(playlistId)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNotNull(state.playlist)
        assertEquals("YouTube Tech Reviews", state.playlist?.name)
        assertEquals(2, state.items.size)
        assertTrue(state.isFavorite)
        assertTrue(state.isYouTubePlaylist)
    }

    @Test
    fun `load handles error gracefully`() = runTest(testDispatcher) {
        val playlistId = UUID.randomUUID().toString()
        coEvery { repository.getPlaylistDetails(playlistId) } returns ApiResult.Error("Playlist not found")

        viewModel.load(playlistId)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("Playlist not found", state.errorMessage)
    }

    @Test
    fun `toggleFavorite updates favorite state and calls repository`() = runTest(testDispatcher) {
        val playlistId = UUID.randomUUID().toString()
        val mockUserData = mockk<UserItemDataDto> {
            io.mockk.every { isFavorite } returns false
        }
        val playlist = BaseItemDto(
            id = UUID.fromString(playlistId),
            name = "My Playlist",
            type = BaseItemKind.PLAYLIST,
            userData = mockUserData,
        )

        coEvery { repository.getPlaylistDetails(playlistId) } returns ApiResult.Success(playlist)
        coEvery { repository.getPlaylistItems(playlistId) } returns ApiResult.Success(emptyList())
        coEvery { repository.toggleFavorite(playlistId, true) } returns ApiResult.Success(true)

        viewModel.load(playlistId)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isFavorite)

        viewModel.toggleFavorite()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isFavorite)
        coVerify { repository.toggleFavorite(playlistId, true) }
    }
}
