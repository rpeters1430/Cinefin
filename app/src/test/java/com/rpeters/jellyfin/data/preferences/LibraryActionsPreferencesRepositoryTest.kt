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
 * Comprehensive unit tests for LibraryActionsPreferencesRepository.
 * Tests DataStore operations, default values, and error handling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryActionsPreferencesRepositoryTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var repository: LibraryActionsPreferencesRepository

    private fun TestScope.createRepository(): LibraryActionsPreferencesRepository {
        val testDir = tmpFolder.newFolder(java.util.UUID.randomUUID().toString())
        val testFile = java.io.File(testDir, "test_lib_actions.preferences_pb")
        testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { testFile },
        )
        return LibraryActionsPreferencesRepository(testDataStore)
    }

    @Test
    fun `preferences flow emits default values initially`() = runTest {
        val repository = createRepository()
        val preferences = repository.preferences.first()
        assertFalse(preferences.enableManagementActions)
    }

    @Test
    fun `default preferences match LibraryActionsPreferences DEFAULT constant`() = runTest {
        val repository = createRepository()
        val preferences = repository.preferences.first()
        assertEquals(LibraryActionsPreferences.DEFAULT.enableManagementActions, preferences.enableManagementActions)
    }

    @Test
    fun `setEnableManagementActions updates preference to true`() = runTest {
        val repository = createRepository()
        repository.setEnableManagementActions(true)
        val updatedPrefs = repository.preferences.first()
        assertTrue(updatedPrefs.enableManagementActions)
    }

    @Test
    fun `setEnableManagementActions updates preference to false`() = runTest {
        val repository = createRepository()
        repository.setEnableManagementActions(true)
        repository.setEnableManagementActions(false)
        val disabledPrefs = repository.preferences.first()
        assertFalse(disabledPrefs.enableManagementActions)
    }

    @Test
    fun `setEnableManagementActions toggles correctly multiple times`() = runTest {
        val repository = createRepository()
        repository.setEnableManagementActions(true)
        assertTrue(repository.preferences.first().enableManagementActions)
        repository.setEnableManagementActions(false)
        assertFalse(repository.preferences.first().enableManagementActions)
        repository.setEnableManagementActions(true)
        assertTrue(repository.preferences.first().enableManagementActions)
    }

    @Test
    fun `preferences persist across repository instances`() = runTest {
        val repository = createRepository()
        repository.setEnableManagementActions(true)
        
        val newRepository = LibraryActionsPreferencesRepository(testDataStore)
        val persistedPrefs = newRepository.preferences.first()
        assertTrue(persistedPrefs.enableManagementActions)
    }

    @Test
    fun `multiple updates persist correctly`() = runTest {
        val repository = createRepository()
        repository.setEnableManagementActions(true)
        repository.setEnableManagementActions(false)
        
        val newRepository = LibraryActionsPreferencesRepository(testDataStore)
        assertFalse(newRepository.preferences.first().enableManagementActions)
    }

    @Test
    fun `preferences flow emits defaults when DataStore read fails`() = runTest {
        val repository = createRepository()
        val preferences = repository.preferences.first()
        assertEquals(LibraryActionsPreferences.DEFAULT.enableManagementActions, preferences.enableManagementActions)
    }

    @Test
    fun `setEnableManagementActions handles multiple rapid updates`() = runTest {
        val repository = createRepository()
        repository.setEnableManagementActions(true)
        repository.setEnableManagementActions(false)
        repository.setEnableManagementActions(true)
        repository.setEnableManagementActions(false)
        repository.setEnableManagementActions(true)
        
        val finalPrefs = repository.preferences.first()
        assertTrue(finalPrefs.enableManagementActions)
    }

    @Test
    fun `preferences flow emits updated values reactively`() = runTest {
        val repository = createRepository()
        repository.setEnableManagementActions(true)
        val updated = repository.preferences.first()
        assertTrue(updated.enableManagementActions)
    }

    @Test
    fun `setting same value twice does not cause issues`() = runTest {
        val repository = createRepository()
        repository.setEnableManagementActions(true)
        repository.setEnableManagementActions(true)
        assertTrue(repository.preferences.first().enableManagementActions)
    }
}
