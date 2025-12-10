package com.rpeters.jellyfin.ui.viewmodel

import com.rpeters.jellyfin.data.preferences.LibraryActionsPreferences
import com.rpeters.jellyfin.data.preferences.LibraryActionsPreferencesRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for LibraryActionsPreferencesViewModel.
 * Tests state management, repository interactions, and preference updates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryActionsPreferencesViewModelTest {

    @MockK
    private lateinit var repository: LibraryActionsPreferencesRepository

    private lateinit var viewModel: LibraryActionsPreferencesViewModel
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(dispatcher)

        // Setup default mock behavior - repository emits default preferences
        coEvery { repository.preferences } returns flowOf(LibraryActionsPreferences.DEFAULT)

        viewModel = LibraryActionsPreferencesViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========================================================================
    // INITIALIZATION TESTS
    // ========================================================================

    @Test
    fun `viewModel initializes with default preferences from repository`() = runTest {
        // When - viewModel is initialized in setup()
        val preferences = viewModel.preferences.value

        // Then
        assertEquals(LibraryActionsPreferences.DEFAULT, preferences)
        assertFalse(preferences.enableManagementActions)
    }

    @Test
    fun `viewModel observes preferences flow from repository on init`() = runTest {
        // Given - repository emits enabled preferences
        val enabledPrefs = LibraryActionsPreferences(enableManagementActions = true)
        coEvery { repository.preferences } returns flowOf(enabledPrefs)

        // When - create new viewModel instance
        val newViewModel = LibraryActionsPreferencesViewModel(repository)

        // Then - viewModel should reflect repository state
        assertEquals(enabledPrefs, newViewModel.preferences.value)
        assertTrue(newViewModel.preferences.value.enableManagementActions)
    }

    // ========================================================================
    // SET MANAGEMENT ACTIONS ENABLED TESTS
    // ========================================================================

    @Test
    fun `setManagementActionsEnabled calls repository with true`() = runTest {
        // When
        viewModel.setManagementActionsEnabled(true)

        // Then
        coVerify(exactly = 1) { repository.setEnableManagementActions(true) }
    }

    @Test
    fun `setManagementActionsEnabled calls repository with false`() = runTest {
        // When
        viewModel.setManagementActionsEnabled(false)

        // Then
        coVerify(exactly = 1) { repository.setEnableManagementActions(false) }
    }

    @Test
    fun `setManagementActionsEnabled multiple times calls repository each time`() = runTest {
        // When
        viewModel.setManagementActionsEnabled(true)
        viewModel.setManagementActionsEnabled(false)
        viewModel.setManagementActionsEnabled(true)

        // Then
        coVerify(exactly = 3) { repository.setEnableManagementActions(any()) }
        coVerify(exactly = 2) { repository.setEnableManagementActions(true) }
        coVerify(exactly = 1) { repository.setEnableManagementActions(false) }
    }

    // ========================================================================
    // STATE FLOW TESTS
    // ========================================================================

    @Test
    fun `preferences state updates when repository emits new value`() = runTest {
        // Given - repository emits disabled preferences
        val disabledPrefs = LibraryActionsPreferences(enableManagementActions = false)
        coEvery { repository.preferences } returns flowOf(disabledPrefs)

        // When - create new viewModel
        val newViewModel = LibraryActionsPreferencesViewModel(repository)

        // Then
        assertEquals(disabledPrefs, newViewModel.preferences.value)
        assertFalse(newViewModel.preferences.value.enableManagementActions)
    }

    @Test
    fun `preferences state reflects enabled state from repository`() = runTest {
        // Given
        val enabledPrefs = LibraryActionsPreferences(enableManagementActions = true)
        coEvery { repository.preferences } returns flowOf(enabledPrefs)

        // When
        val newViewModel = LibraryActionsPreferencesViewModel(repository)

        // Then
        assertTrue(newViewModel.preferences.value.enableManagementActions)
    }

    // ========================================================================
    // INTEGRATION TESTS
    // ========================================================================

    @Test
    fun `setManagementActionsEnabled does not throw exceptions`() = runTest {
        // Given - repository setup with coJustRun
        coEvery { repository.setEnableManagementActions(any()) } returns Unit

        // When/Then - should not throw
        viewModel.setManagementActionsEnabled(true)
        viewModel.setManagementActionsEnabled(false)
    }

    @Test
    fun `viewModel correctly handles rapid toggle requests`() = runTest {
        // Given
        coEvery { repository.setEnableManagementActions(any()) } returns Unit

        // When - rapidly toggle
        repeat(10) { index ->
            viewModel.setManagementActionsEnabled(index % 2 == 0)
        }

        // Then - should have called repository 10 times
        coVerify(exactly = 10) { repository.setEnableManagementActions(any()) }
    }

    // ========================================================================
    // LIFECYCLE TESTS
    // ========================================================================

    @Test
    fun `preferences flow collection starts on viewModel initialization`() = runTest {
        // Given - fresh repository
        coEvery { repository.preferences } returns flowOf(LibraryActionsPreferences.DEFAULT)

        // When - viewModel is created
        val newViewModel = LibraryActionsPreferencesViewModel(repository)

        // Then - preferences should immediately have value
        assertEquals(LibraryActionsPreferences.DEFAULT, newViewModel.preferences.value)
    }

    @Test
    fun `viewModel maintains state consistency`() = runTest {
        // Given - repository with specific state
        val testPrefs = LibraryActionsPreferences(enableManagementActions = true)
        coEvery { repository.preferences } returns flowOf(testPrefs)

        // When
        val newViewModel = LibraryActionsPreferencesViewModel(repository)

        // Then - state should match repository exactly
        assertEquals(testPrefs, newViewModel.preferences.value)
        assertEquals(testPrefs.enableManagementActions, newViewModel.preferences.value.enableManagementActions)
    }
}
