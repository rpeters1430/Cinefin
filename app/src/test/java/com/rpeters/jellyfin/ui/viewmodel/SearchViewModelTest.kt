package com.rpeters.jellyfin.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.rpeters.jellyfin.data.repository.JellyfinSearchRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var searchRepository: JellyfinSearchRepository

    private lateinit var viewModel: SearchViewModel
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(dispatcher)
        viewModel = SearchViewModel(searchRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateSearchQuery updates state and clears when blank`() = runTest {
        coEvery { searchRepository.searchItems(any(), any(), any()) } returns ApiResult.Success(emptyList())

        viewModel.updateSearchQuery("matrix")
        viewModel.performSearch()

        assertEquals("matrix", viewModel.searchState.value.searchQuery)
        assertTrue(viewModel.searchState.value.hasSearched)

        viewModel.updateSearchQuery("")

        val state = viewModel.searchState.value
        assertEquals("", state.searchQuery)
        assertTrue(state.searchResults.isEmpty())
        assertFalse(state.isSearching)
        assertFalse(state.hasSearched)
        assertNull(state.errorMessage)
    }

    @Test
    fun `performSearch updates results on success`() = runTest {
        val items = listOf(mockk<BaseItemDto>())
        coEvery { searchRepository.searchItems("avatar", any(), any()) } returns ApiResult.Success(items)

        viewModel.updateSearchQuery("avatar")
        viewModel.performSearch()

        val state = viewModel.searchState.value
        assertEquals(items, state.searchResults)
        assertFalse(state.isSearching)
        assertTrue(state.hasSearched)
        assertNull(state.errorMessage)
    }

    @Test
    fun `performSearch handles error response`() = runTest {
        coEvery { searchRepository.searchItems("fail", any(), any()) } returns ApiResult.Error("network")

        viewModel.performSearch("fail")

        val state = viewModel.searchState.value
        assertTrue(state.searchResults.isEmpty())
        assertFalse(state.isSearching)
        assertEquals("network", state.errorMessage)
    }

    @Test
    fun `toggleContentType updates filters and triggers search`() = runTest {
        coEvery { searchRepository.searchItems(any(), any(), any()) } returns ApiResult.Success(emptyList())

        viewModel.updateSearchQuery("comedy")
        viewModel.toggleContentType(BaseItemKind.MOVIE)

        val selectedTypes = viewModel.searchState.value.selectedContentTypes
        assertFalse(selectedTypes.contains(BaseItemKind.MOVIE))

        coVerify { searchRepository.searchItems("comedy", match { BaseItemKind.MOVIE !in it }, 50) }
    }

    @Test
    fun `clearSearch resets state`() {
        viewModel.updateSearchQuery("something")
        viewModel.clearSearch()

        val state = viewModel.searchState.value
        assertEquals("", state.searchQuery)
        assertTrue(state.searchResults.isEmpty())
        assertFalse(state.isSearching)
        assertFalse(state.hasSearched)
        assertNull(state.errorMessage)
    }

    @Test
    fun `searchMovies delegates to repository`() = runTest {
        val movies = listOf(mockk<BaseItemDto>())
        coEvery { searchRepository.searchMovies("terminator") } returns ApiResult.Success(movies)

        viewModel.searchMovies("terminator")

        val state = viewModel.searchState.value
        assertEquals(movies, state.searchResults)
        assertTrue(state.hasSearched)
        assertFalse(state.isSearching)
        assertNull(state.errorMessage)
    }

    @Test
    fun `searchTVShows handles repository error`() = runTest {
        coEvery { searchRepository.searchTVShows("lost") } returns ApiResult.Error("timeout")

        viewModel.searchTVShows("lost")

        val state = viewModel.searchState.value
        assertTrue(state.searchResults.isEmpty())
        assertFalse(state.isSearching)
        assertEquals("timeout", state.errorMessage)
    }
}
