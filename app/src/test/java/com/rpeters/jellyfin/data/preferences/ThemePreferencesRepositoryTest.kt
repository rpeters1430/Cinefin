package com.rpeters.jellyfin.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

/**
 * Comprehensive unit tests for ThemePreferencesRepository.
 * Tests DataStore operations, enum parsing, error handling, and state management.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemePreferencesRepositoryTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private lateinit var testDataStore: DataStore<Preferences>

    @Before
    fun setup() {
        // No-op, initialized per test
    }

    private fun TestScope.createRepository(): ThemePreferencesRepository {
        val testDir = tmpFolder.newFolder(UUID.randomUUID().toString())
        val testFile = java.io.File(testDir, "test_theme.preferences_pb")
        testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { testFile },
        )
        return ThemePreferencesRepository(testDataStore)
    }

    @Test
    fun `themePreferencesFlow emits default values initially`() = runTest {
        val repository = createRepository()
        val preferences = repository.themePreferencesFlow.first()

        assertEquals(ThemeMode.SYSTEM, preferences.themeMode)
        assertTrue(preferences.useDynamicColors)
        assertEquals(AccentColor.JELLYFIN_PURPLE, preferences.accentColor)
        assertEquals(ContrastLevel.STANDARD, preferences.contrastLevel)
        assertTrue(preferences.useThemedIcon)
        assertTrue(preferences.enableEdgeToEdge)
        assertTrue(preferences.respectReduceMotion)
    }

    @Test
    fun `setThemeMode updates theme mode preference`() = runTest {
        val repository = createRepository()
        val newThemeMode = ThemeMode.DARK

        repository.setThemeMode(newThemeMode)
        val preferences = repository.themePreferencesFlow.first()

        assertEquals(newThemeMode, preferences.themeMode)
    }

    @Test
    fun `setThemeMode to LIGHT updates correctly`() = runTest {
        val repository = createRepository()
        repository.setThemeMode(ThemeMode.DARK)

        repository.setThemeMode(ThemeMode.LIGHT)
        val preferences = repository.themePreferencesFlow.first()

        assertEquals(ThemeMode.LIGHT, preferences.themeMode)
    }

    @Test
    fun `setThemeMode to AMOLED_BLACK updates correctly`() = runTest {
        val repository = createRepository()
        val amoledMode = ThemeMode.AMOLED_BLACK

        repository.setThemeMode(amoledMode)
        val preferences = repository.themePreferencesFlow.first()

        assertEquals(amoledMode, preferences.themeMode)
    }

    @Test
    fun `setUseDynamicColors updates dynamic colors preference`() = runTest {
        val repository = createRepository()
        repository.setUseDynamicColors(false)
        val preferences = repository.themePreferencesFlow.first()

        assertFalse(preferences.useDynamicColors)
    }

    @Test
    fun `setUseDynamicColors toggles correctly`() = runTest {
        val repository = createRepository()
        repository.setUseDynamicColors(false)
        assertFalse(repository.themePreferencesFlow.first().useDynamicColors)

        repository.setUseDynamicColors(true)
        assertTrue(repository.themePreferencesFlow.first().useDynamicColors)
    }

    @Test
    fun `setAccentColor updates accent color preference`() = runTest {
        val repository = createRepository()
        val newAccentColor = AccentColor.MATERIAL_BLUE

        repository.setAccentColor(newAccentColor)
        val preferences = repository.themePreferencesFlow.first()

        assertEquals(newAccentColor, preferences.accentColor)
    }

    @Test
    fun `setAccentColor persists all available colors`() = runTest {
        val repository = createRepository()
        AccentColor.entries.forEach { color ->
            repository.setAccentColor(color)
            val preferences = repository.themePreferencesFlow.first()
            assertEquals(color, preferences.accentColor)
        }
    }

    @Test
    fun `setContrastLevel updates contrast level preference`() = runTest {
        val repository = createRepository()
        val newContrastLevel = ContrastLevel.HIGH

        repository.setContrastLevel(newContrastLevel)
        val preferences = repository.themePreferencesFlow.first()

        assertEquals(newContrastLevel, preferences.contrastLevel)
    }

    @Test
    fun `setContrastLevel persists all available levels`() = runTest {
        val repository = createRepository()
        ContrastLevel.entries.forEach { level ->
            repository.setContrastLevel(level)
            val preferences = repository.themePreferencesFlow.first()
            assertEquals(level, preferences.contrastLevel)
        }
    }

    @Test
    fun `setUseThemedIcon updates themed icon preference`() = runTest {
        val repository = createRepository()
        repository.setUseThemedIcon(false)
        val preferences = repository.themePreferencesFlow.first()

        assertFalse(preferences.useThemedIcon)
    }

    @Test
    fun `setEnableEdgeToEdge updates edge-to-edge preference`() = runTest {
        val repository = createRepository()
        repository.setEnableEdgeToEdge(false)
        val preferences = repository.themePreferencesFlow.first()

        assertFalse(preferences.enableEdgeToEdge)
    }

    @Test
    fun `setRespectReduceMotion updates reduce motion preference`() = runTest {
        val repository = createRepository()
        repository.setRespectReduceMotion(false)
        val preferences = repository.themePreferencesFlow.first()

        assertFalse(preferences.respectReduceMotion)
    }

    @Test
    fun `resetToDefaults clears all preferences`() = runTest {
        val repository = createRepository()
        repository.setThemeMode(ThemeMode.DARK)
        repository.setUseDynamicColors(false)
        repository.setAccentColor(AccentColor.MATERIAL_RED)
        repository.setContrastLevel(ContrastLevel.HIGH)
        repository.setUseThemedIcon(false)
        repository.setEnableEdgeToEdge(false)
        repository.setRespectReduceMotion(false)

        repository.resetToDefaults()
        val preferences = repository.themePreferencesFlow.first()

        assertEquals(ThemeMode.SYSTEM, preferences.themeMode)
        assertTrue(preferences.useDynamicColors)
        assertEquals(AccentColor.JELLYFIN_PURPLE, preferences.accentColor)
        assertEquals(ContrastLevel.STANDARD, preferences.contrastLevel)
        assertTrue(preferences.useThemedIcon)
        assertTrue(preferences.enableEdgeToEdge)
        assertTrue(preferences.respectReduceMotion)
    }

    @Test
    fun `multiple preferences can be updated independently`() = runTest {
        val repository = createRepository()
        repository.setThemeMode(ThemeMode.DARK)
        repository.setAccentColor(AccentColor.MATERIAL_GREEN)
        repository.setContrastLevel(ContrastLevel.MEDIUM)

        val preferences = repository.themePreferencesFlow.first()

        assertEquals(ThemeMode.DARK, preferences.themeMode)
        assertEquals(AccentColor.MATERIAL_GREEN, preferences.accentColor)
        assertEquals(ContrastLevel.MEDIUM, preferences.contrastLevel)
        assertTrue(preferences.useDynamicColors)
        assertTrue(preferences.useThemedIcon)
    }

    @Test
    fun `preferences persist across repository instances`() = runTest {
        val repository = createRepository()
        repository.setThemeMode(ThemeMode.AMOLED_BLACK)
        repository.setAccentColor(AccentColor.JELLYFIN_BLUE)

        val newRepository = ThemePreferencesRepository(testDataStore)
        val preferences = newRepository.themePreferencesFlow.first()

        assertEquals(ThemeMode.AMOLED_BLACK, preferences.themeMode)
        assertEquals(AccentColor.JELLYFIN_BLUE, preferences.accentColor)
    }
}
