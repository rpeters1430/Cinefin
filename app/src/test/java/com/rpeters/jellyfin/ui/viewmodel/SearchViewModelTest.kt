package com.rpeters.jellyfin.ui.viewmodel

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
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

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
    fun `updateSearchQuery_withNonBlankQuery_updatesState`() = runTest {
        viewModel.updateSearchQuery("matrix")

        val state = viewModel.searchState.value
        assertEquals("matrix", state.searchQuery)
        assertTrue(state.searchResults.isEmpty())
        assertFalse(state.isSearching)
        assertFalse(state.hasSearched)
        assertNull(state.errorMessage)
    }

    @Test
    fun `updateSearchQuery_withBlankQueryAfterSearch_clearsState`() = runTest {
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
    fun `performSearch_onSuccess_updatesResultsAndFlags`() = runTest {
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
    fun `performSearch_onError_setsErrorMessage`() = runTest {
        coEvery { searchRepository.searchItems("fail", any(), any()) } returns ApiResult.Error("network")

        viewModel.performSearch("fail")

        val state = viewModel.searchState.value
        assertTrue(state.searchResults.isEmpty())
        assertFalse(state.isSearching)
        assertEquals("network", state.errorMessage)
    }

    @Test
    fun `toggleContentType_withSelectedMovie_removesTypeAndTriggersSearch`() = runTest {
        coEvery { searchRepository.searchItems(any(), any(), any()) } returns ApiResult.Success(emptyList())

        viewModel.updateSearchQuery("comedy")
        viewModel.toggleContentType(BaseItemKind.MOVIE)

        val selectedTypes = viewModel.searchState.value.selectedContentTypes
        assertFalse(selectedTypes.contains(BaseItemKind.MOVIE))

        coVerify {
            searchRepository.searchItems(
                "comedy",
                match { it.toSet() == setOf(BaseItemKind.SERIES, BaseItemKind.AUDIO, BaseItemKind.BOOK) },
                50,
            )
        }
    }

    @Test
    fun `toggleContentType_withUnselectedType_addsTypeAndTriggersSearch`() = runTest {
        coEvery { searchRepository.searchItems(any(), any(), any()) } returns ApiResult.Success(emptyList())

        viewModel.updateSearchQuery("test")
        viewModel.toggleContentType(BaseItemKind.MOVIE)
        viewModel.toggleContentType(BaseItemKind.MOVIE)

        val selectedTypes = viewModel.searchState.value.selectedContentTypes
        assertTrue(selectedTypes.contains(BaseItemKind.MOVIE))

        coVerify { searchRepository.searchItems("test", any(), 50) }
    }

    @Test
    fun `clearSearch_always_resetsAllStateFields`() {
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
    fun `searchMovies_onSuccess_updatesResultsFromRepository`() = runTest {
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
    fun `searchMovies_onError_setsErrorMessage`() = runTest {
        coEvery { searchRepository.searchMovies("terminator") } returns ApiResult.Error("network error")

        viewModel.searchMovies("terminator")

        val state = viewModel.searchState.value
        assertFalse(state.isSearching)
        assertEquals("network error", state.errorMessage)
    }

    @Test
    fun `searchTVShows_onError_preservesResultsAndSetsError`() = runTest {
        val shows = listOf(mockk<BaseItemDto>())
        coEvery { searchRepository.searchTVShows("success") } returns ApiResult.Success(shows)
        coEvery { searchRepository.searchTVShows("lost") } returns ApiResult.Error("timeout")

        viewModel.searchTVShows("success")
        viewModel.searchTVShows("lost")

        val state = viewModel.searchState.value
        assertFalse(state.isSearching)
        assertEquals(shows, state.searchResults)
        assertEquals("timeout", state.errorMessage)
    }
}
