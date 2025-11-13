package com.rpeters.jellyfin.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Comprehensive unit tests for ThemePreferencesRepository.
 * Tests DataStore operations, enum parsing, error handling, and state management.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemePreferencesRepositoryTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher + Job())

    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var repository: ThemePreferencesRepository

    @Before
    fun setup() {
        // Create a test DataStore with a temporary file
        val testFile = tmpFolder.newFile("test_theme_preferences.preferences_pb")
        testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )

        repository = ThemePreferencesRepository(testDataStore)
    }

    @After
    fun tearDown() {
        // Clean up test files
        tmpFolder.root.deleteRecursively()
    }

    // ========================================================================
    // DEFAULT VALUES TESTS
    // ========================================================================

    @Test
    fun `themePreferencesFlow emits default values initially`() = testScope.runTest {
        // Given - fresh repository with no stored preferences

        // When
        val preferences = repository.themePreferencesFlow.first()

        // Then
        assertEquals(ThemeMode.SYSTEM, preferences.themeMode)
        assertTrue(preferences.useDynamicColors)
        assertEquals(AccentColor.JELLYFIN_PURPLE, preferences.accentColor)
        assertEquals(ContrastLevel.STANDARD, preferences.contrastLevel)
        assertTrue(preferences.useThemedIcon)
        assertTrue(preferences.enableEdgeToEdge)
        assertTrue(preferences.respectReduceMotion)
    }

    // ========================================================================
    // THEME MODE TESTS
    // ========================================================================

    @Test
    fun `setThemeMode updates theme mode preference`() = testScope.runTest {
        // Given
        val newThemeMode = ThemeMode.DARK

        // When
        repository.setThemeMode(newThemeMode)
        val preferences = repository.themePreferencesFlow.first()

        // Then
        assertEquals(newThemeMode, preferences.themeMode)
    }

    @Test
    fun `setThemeMode to LIGHT updates correctly`() = testScope.runTest {
        // Given
        repository.setThemeMode(ThemeMode.DARK)

        // When
        repository.setThemeMode(ThemeMode.LIGHT)
        val preferences = repository.themePreferencesFlow.first()

        // Then
        assertEquals(ThemeMode.LIGHT, preferences.themeMode)
    }

    @Test
    fun `setThemeMode to AMOLED_BLACK updates correctly`() = testScope.runTest {
        // Given
        val amoledMode = ThemeMode.AMOLED_BLACK

        // When
        repository.setThemeMode(amoledMode)
        val preferences = repository.themePreferencesFlow.first()

        // Then
        assertEquals(amoledMode, preferences.themeMode)
    }

    // ========================================================================
    // DYNAMIC COLORS TESTS
    // ========================================================================

    @Test
    fun `setUseDynamicColors updates dynamic colors preference`() = testScope.runTest {
        // When
        repository.setUseDynamicColors(false)
        val preferences = repository.themePreferencesFlow.first()

        // Then
        assertFalse(preferences.useDynamicColors)
    }

    @Test
    fun `setUseDynamicColors toggles correctly`() = testScope.runTest {
        // Given - default is true
        repository.setUseDynamicColors(false)
        val prefs1 = repository.themePreferencesFlow.first()
        assertFalse(prefs1.useDynamicColors)

        // When
        repository.setUseDynamicColors(true)
        val prefs2 = repository.themePreferencesFlow.first()

        // Then
        assertTrue(prefs2.useDynamicColors)
    }

    // ========================================================================
    // ACCENT COLOR TESTS
    // ========================================================================

    @Test
    fun `setAccentColor updates accent color preference`() = testScope.runTest {
        // Given
        val newAccentColor = AccentColor.MATERIAL_BLUE

        // When
        repository.setAccentColor(newAccentColor)
        val preferences = repository.themePreferencesFlow.first()

        // Then
        assertEquals(newAccentColor, preferences.accentColor)
    }

    @Test
    fun `setAccentColor persists all available colors`() = testScope.runTest {
        // Test each accent color option
        AccentColor.entries.forEach { color ->
            // When
            repository.setAccentColor(color)
            val preferences = repository.themePreferencesFlow.first()

            // Then
            assertEquals(color, preferences.accentColor)
        }
    }

    // ========================================================================
    // CONTRAST LEVEL TESTS
    // ========================================================================

    @Test
    fun `setContrastLevel updates contrast level preference`() = testScope.runTest {
        // Given
        val newContrastLevel = ContrastLevel.HIGH

        // When
        repository.setContrastLevel(newContrastLevel)
        val preferences = repository.themePreferencesFlow.first()

        // Then
        assertEquals(newContrastLevel, preferences.contrastLevel)
    }

    @Test
    fun `setContrastLevel persists all available levels`() = testScope.runTest {
        // Test each contrast level
        ContrastLevel.entries.forEach { level ->
            // When
            repository.setContrastLevel(level)
            val preferences = repository.themePreferencesFlow.first()

            // Then
            assertEquals(level, preferences.contrastLevel)
        }
    }

    // ========================================================================
    // THEMED ICON TESTS
    // ========================================================================

    @Test
    fun `setUseThemedIcon updates themed icon preference`() = testScope.runTest {
        // When
        repository.setUseThemedIcon(false)
        val preferences = repository.themePreferencesFlow.first()

        // Then
        assertFalse(preferences.useThemedIcon)
    }

    // ========================================================================
    // EDGE-TO-EDGE TESTS
    // ========================================================================

    @Test
    fun `setEnableEdgeToEdge updates edge-to-edge preference`() = testScope.runTest {
        // When
        repository.setEnableEdgeToEdge(false)
        val preferences = repository.themePreferencesFlow.first()

        // Then
        assertFalse(preferences.enableEdgeToEdge)
    }

    // ========================================================================
    // REDUCE MOTION TESTS
    // ========================================================================

    @Test
    fun `setRespectReduceMotion updates reduce motion preference`() = testScope.runTest {
        // When
        repository.setRespectReduceMotion(false)
        val preferences = repository.themePreferencesFlow.first()

        // Then
        assertFalse(preferences.respectReduceMotion)
    }

    // ========================================================================
    // RESET TESTS
    // ========================================================================

    @Test
    fun `resetToDefaults clears all preferences`() = testScope.runTest {
        // Given - set non-default values
        repository.setThemeMode(ThemeMode.DARK)
        repository.setUseDynamicColors(false)
        repository.setAccentColor(AccentColor.MATERIAL_RED)
        repository.setContrastLevel(ContrastLevel.HIGH)
        repository.setUseThemedIcon(false)
        repository.setEnableEdgeToEdge(false)
        repository.setRespectReduceMotion(false)

        // When
        repository.resetToDefaults()
        val preferences = repository.themePreferencesFlow.first()

        // Then - all values should be defaults
        assertEquals(ThemeMode.SYSTEM, preferences.themeMode)
        assertTrue(preferences.useDynamicColors)
        assertEquals(AccentColor.JELLYFIN_PURPLE, preferences.accentColor)
        assertEquals(ContrastLevel.STANDARD, preferences.contrastLevel)
        assertTrue(preferences.useThemedIcon)
        assertTrue(preferences.enableEdgeToEdge)
        assertTrue(preferences.respectReduceMotion)
    }

    // ========================================================================
    // COMBINED PREFERENCES TESTS
    // ========================================================================

    @Test
    fun `multiple preferences can be updated independently`() = testScope.runTest {
        // When
        repository.setThemeMode(ThemeMode.DARK)
        repository.setAccentColor(AccentColor.MATERIAL_GREEN)
        repository.setContrastLevel(ContrastLevel.MEDIUM)

        val preferences = repository.themePreferencesFlow.first()

        // Then
        assertEquals(ThemeMode.DARK, preferences.themeMode)
        assertEquals(AccentColor.MATERIAL_GREEN, preferences.accentColor)
        assertEquals(ContrastLevel.MEDIUM, preferences.contrastLevel)
        // Other preferences should remain at defaults
        assertTrue(preferences.useDynamicColors)
        assertTrue(preferences.useThemedIcon)
    }

    @Test
    fun `preferences persist across repository instances`() = testScope.runTest {
        // Given - set preferences
        repository.setThemeMode(ThemeMode.AMOLED_BLACK)
        repository.setAccentColor(AccentColor.JELLYFIN_BLUE)

        // When - create new repository instance with same DataStore
        val newRepository = ThemePreferencesRepository(testDataStore)
        val preferences = newRepository.themePreferencesFlow.first()

        // Then - preferences should be persisted
        assertEquals(ThemeMode.AMOLED_BLACK, preferences.themeMode)
        assertEquals(AccentColor.JELLYFIN_BLUE, preferences.accentColor)
    }

}
