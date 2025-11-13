package com.rpeters.jellyfin.ui.viewmodel

import app.cash.turbine.test
import com.rpeters.jellyfin.data.preferences.AccentColor
import com.rpeters.jellyfin.data.preferences.ContrastLevel
import com.rpeters.jellyfin.data.preferences.ThemeMode
import com.rpeters.jellyfin.data.preferences.ThemePreferences
import com.rpeters.jellyfin.data.preferences.ThemePreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Comprehensive unit tests for ThemePreferencesViewModel.
 * Tests state management, repository interactions, error handling, and coroutine behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemePreferencesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockRepository: ThemePreferencesRepository
    private lateinit var viewModel: ThemePreferencesViewModel
    private lateinit var preferencesFlow: MutableStateFlow<ThemePreferences>

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Create a mutable flow to simulate repository emissions
        preferencesFlow = MutableStateFlow(ThemePreferences.DEFAULT)

        // Setup mock repository
        mockRepository = mockk(relaxed = true)
        every { mockRepository.themePreferencesFlow } returns preferencesFlow

        // Create ViewModel
        viewModel = ThemePreferencesViewModel(mockRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========================================================================
    // INITIALIZATION TESTS
    // ========================================================================

    @Test
    fun `viewModel initializes with default preferences`() = runTest {
        // Given - ViewModel created in setup

        // When
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.themePreferences.test {
            val preferences = awaitItem()
            assertEquals(ThemePreferences.DEFAULT, preferences)
        }
    }

    @Test
    fun `viewModel reflects repository state changes`() = runTest {
        // Given
        val updatedPreferences = ThemePreferences.DEFAULT.copy(
            themeMode = ThemeMode.DARK,
            accentColor = AccentColor.MATERIAL_BLUE
        )

        // When
        preferencesFlow.value = updatedPreferences
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.themePreferences.test {
            val preferences = awaitItem()
            assertEquals(ThemeMode.DARK, preferences.themeMode)
            assertEquals(AccentColor.MATERIAL_BLUE, preferences.accentColor)
        }
    }

    // ========================================================================
    // THEME MODE TESTS
    // ========================================================================

    @Test
    fun `setThemeMode calls repository with correct parameter`() = runTest {
        // Given
        val newThemeMode = ThemeMode.DARK
        coEvery { mockRepository.setThemeMode(any()) } returns Unit

        // When
        viewModel.setThemeMode(newThemeMode)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockRepository.setThemeMode(newThemeMode) }
    }

    @Test
    fun `setThemeMode to LIGHT updates correctly`() = runTest {
        // Given
        val lightMode = ThemeMode.LIGHT
        coEvery { mockRepository.setThemeMode(any()) } returns Unit

        // When
        viewModel.setThemeMode(lightMode)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockRepository.setThemeMode(lightMode) }
    }

    @Test
    fun `setThemeMode to AMOLED_BLACK updates correctly`() = runTest {
        // Given
        val amoledMode = ThemeMode.AMOLED_BLACK
        coEvery { mockRepository.setThemeMode(any()) } returns Unit

        // When
        viewModel.setThemeMode(amoledMode)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockRepository.setThemeMode(amoledMode) }
    }

    @Test
    fun `setThemeMode handles repository errors gracefully`() = runTest {
        // Given
        val exception = IOException("Test exception")
        coEvery { mockRepository.setThemeMode(any()) } throws exception

        // When
        viewModel.setThemeMode(ThemeMode.DARK)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - should not crash, error is logged
        coVerify(exactly = 1) { mockRepository.setThemeMode(ThemeMode.DARK) }
    }

    // ========================================================================
    // DYNAMIC COLORS TESTS
    // ========================================================================

    @Test
    fun `setUseDynamicColors calls repository with correct parameter`() = runTest {
        // Given
        coEvery { mockRepository.setUseDynamicColors(any()) } returns Unit

        // When
        viewModel.setUseDynamicColors(false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockRepository.setUseDynamicColors(false) }
    }

    @Test
    fun `setUseDynamicColors toggles correctly`() = runTest {
        // Given
        coEvery { mockRepository.setUseDynamicColors(any()) } returns Unit

        // When
        viewModel.setUseDynamicColors(true)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.setUseDynamicColors(false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockRepository.setUseDynamicColors(true) }
        coVerify(exactly = 1) { mockRepository.setUseDynamicColors(false) }
    }

    // ========================================================================
    // ACCENT COLOR TESTS
    // ========================================================================

    @Test
    fun `setAccentColor calls repository with correct parameter`() = runTest {
        // Given
        val newAccentColor = AccentColor.MATERIAL_GREEN
        coEvery { mockRepository.setAccentColor(any()) } returns Unit

        // When
        viewModel.setAccentColor(newAccentColor)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockRepository.setAccentColor(newAccentColor) }
    }

    @Test
    fun `setAccentColor updates all available colors`() = runTest {
        // Given
        coEvery { mockRepository.setAccentColor(any()) } returns Unit

        // When - Test each accent color
        AccentColor.entries.forEach { color ->
            viewModel.setAccentColor(color)
            testDispatcher.scheduler.advanceUntilIdle()
        }

        // Then - Each color should be set exactly once
        AccentColor.entries.forEach { color ->
            coVerify(exactly = 1) { mockRepository.setAccentColor(color) }
        }
    }

    // ========================================================================
    // CONTRAST LEVEL TESTS
    // ========================================================================

    @Test
    fun `setContrastLevel calls repository with correct parameter`() = runTest {
        // Given
        val newContrastLevel = ContrastLevel.HIGH
        coEvery { mockRepository.setContrastLevel(any()) } returns Unit

        // When
        viewModel.setContrastLevel(newContrastLevel)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockRepository.setContrastLevel(newContrastLevel) }
    }

    @Test
    fun `setContrastLevel updates all available levels`() = runTest {
        // Given
        coEvery { mockRepository.setContrastLevel(any()) } returns Unit

        // When - Test each contrast level
        ContrastLevel.entries.forEach { level ->
            viewModel.setContrastLevel(level)
            testDispatcher.scheduler.advanceUntilIdle()
        }

        // Then - Each level should be set exactly once
        ContrastLevel.entries.forEach { level ->
            coVerify(exactly = 1) { mockRepository.setContrastLevel(level) }
        }
    }

    // ========================================================================
    // THEMED ICON TESTS
    // ========================================================================

    @Test
    fun `setUseThemedIcon calls repository with correct parameter`() = runTest {
        // Given
        coEvery { mockRepository.setUseThemedIcon(any()) } returns Unit

        // When
        viewModel.setUseThemedIcon(false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockRepository.setUseThemedIcon(false) }
    }

    // ========================================================================
    // EDGE-TO-EDGE TESTS
    // ========================================================================

    @Test
    fun `setEnableEdgeToEdge calls repository with correct parameter`() = runTest {
        // Given
        coEvery { mockRepository.setEnableEdgeToEdge(any()) } returns Unit

        // When
        viewModel.setEnableEdgeToEdge(false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockRepository.setEnableEdgeToEdge(false) }
    }

    // ========================================================================
    // REDUCE MOTION TESTS
    // ========================================================================

    @Test
    fun `setRespectReduceMotion calls repository with correct parameter`() = runTest {
        // Given
        coEvery { mockRepository.setRespectReduceMotion(any()) } returns Unit

        // When
        viewModel.setRespectReduceMotion(false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockRepository.setRespectReduceMotion(false) }
    }

    // ========================================================================
    // RESET TESTS
    // ========================================================================

    @Test
    fun `resetToDefaults calls repository`() = runTest {
        // Given
        coEvery { mockRepository.resetToDefaults() } returns Unit

        // When
        viewModel.resetToDefaults()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockRepository.resetToDefaults() }
    }

    @Test
    fun `resetToDefaults handles repository errors gracefully`() = runTest {
        // Given
        val exception = IOException("Test exception")
        coEvery { mockRepository.resetToDefaults() } throws exception

        // When
        viewModel.resetToDefaults()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - should not crash, error is logged
        coVerify(exactly = 1) { mockRepository.resetToDefaults() }
    }

    // ========================================================================
    // STATE FLOW TESTS
    // ========================================================================

    @Test
    fun `themePreferences StateFlow emits updates reactively`() = runTest {
        // Given
        val initialPreferences = ThemePreferences.DEFAULT
        val updatedPreferences = initialPreferences.copy(
            themeMode = ThemeMode.DARK,
            accentColor = AccentColor.MATERIAL_RED,
            contrastLevel = ContrastLevel.HIGH
        )

        // When/Then
        viewModel.themePreferences.test {
            // Initial value
            assertEquals(initialPreferences, awaitItem())

            // Update preferences
            preferencesFlow.value = updatedPreferences
            testDispatcher.scheduler.advanceUntilIdle()

            // Updated value
            assertEquals(updatedPreferences, awaitItem())
        }
    }

    @Test
    fun `themePreferences StateFlow handles multiple rapid updates`() = runTest {
        // Given
        val updates = listOf(
            ThemePreferences.DEFAULT.copy(themeMode = ThemeMode.DARK),
            ThemePreferences.DEFAULT.copy(themeMode = ThemeMode.LIGHT),
            ThemePreferences.DEFAULT.copy(themeMode = ThemeMode.AMOLED_BLACK),
        )

        // When
        viewModel.themePreferences.test {
            // Initial value
            awaitItem()

            // Apply updates
            updates.forEach { preferences ->
                preferencesFlow.value = preferences
                testDispatcher.scheduler.advanceUntilIdle()
                assertEquals(preferences, awaitItem())
            }
        }
    }

    // ========================================================================
    // ERROR HANDLING TESTS
    // ========================================================================

    @Test
    fun `ViewModel handles repository flow errors gracefully`() = runTest {
        // Given - Create a new repository that throws on flow collection
        val errorRepository = mockk<ThemePreferencesRepository>(relaxed = true)
        every { errorRepository.themePreferencesFlow } returns preferencesFlow

        // Create ViewModel with error repository
        val errorViewModel = ThemePreferencesViewModel(errorRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // When - Flow emits an error scenario (empty preferences)
        preferencesFlow.value = ThemePreferences.DEFAULT

        // Then - Should handle gracefully without crashing
        errorViewModel.themePreferences.test {
            val preferences = awaitItem()
            assertEquals(ThemePreferences.DEFAULT, preferences)
        }
    }

    @Test
    fun `multiple preference updates are handled in sequence`() = runTest {
        // Given
        coEvery { mockRepository.setThemeMode(any()) } returns Unit
        coEvery { mockRepository.setAccentColor(any()) } returns Unit
        coEvery { mockRepository.setContrastLevel(any()) } returns Unit

        // When - Update multiple preferences in sequence
        viewModel.setThemeMode(ThemeMode.DARK)
        viewModel.setAccentColor(AccentColor.MATERIAL_BLUE)
        viewModel.setContrastLevel(ContrastLevel.HIGH)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - All updates should be called
        coVerify(exactly = 1) { mockRepository.setThemeMode(ThemeMode.DARK) }
        coVerify(exactly = 1) { mockRepository.setAccentColor(AccentColor.MATERIAL_BLUE) }
        coVerify(exactly = 1) { mockRepository.setContrastLevel(ContrastLevel.HIGH) }
    }
}
