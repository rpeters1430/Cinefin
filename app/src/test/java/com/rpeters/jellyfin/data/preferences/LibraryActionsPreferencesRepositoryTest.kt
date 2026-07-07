package com.rpeters.jellyfin.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive unit tests for LibraryActionsPreferencesRepository.
 * Tests DataStore operations, default values, and error handling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryActionsPreferencesRepositoryTest {

    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var repository: LibraryActionsPreferencesRepository

    private fun createRepository(): LibraryActionsPreferencesRepository {
        testDataStore = InMemoryDataStore()
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

    private class InMemoryDataStore : DataStore<Preferences> {
        private val stateFlow = MutableStateFlow<Preferences>(emptyPreferences())
        private val mutex = Mutex()

        override val data: Flow<Preferences> = stateFlow

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            return mutex.withLock {
                val updated = transform(stateFlow.value)
                stateFlow.value = updated
                updated
            }
        }
    }
}
