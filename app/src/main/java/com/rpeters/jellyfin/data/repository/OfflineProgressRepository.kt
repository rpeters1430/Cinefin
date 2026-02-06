package com.rpeters.jellyfin.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.PlayMethod
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class QueuedProgressUpdate(
    val itemId: String,
    val sessionId: String,
    val positionTicks: Long?,
    val mediaSourceId: String? = null,
    val playMethod: PlayMethod = PlayMethod.DIRECT_PLAY,
    val isPaused: Boolean = false,
    val isMuted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

private val Context.offlineProgressDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "offline_progress_updates",
)

@Singleton
class OfflineProgressRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.offlineProgressDataStore
    private val QUEUED_UPDATES_KEY = stringPreferencesKey("queued_updates")
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Adds a progress update to the queue.
     * If an update for the same itemId already exists, it is replaced with the newer one
     * to ensure we only sync the latest position.
     */
    suspend fun addUpdate(update: QueuedProgressUpdate) {
        dataStore.edit { preferences ->
            val currentJson = preferences[QUEUED_UPDATES_KEY] ?: "[]"
            val currentList = try {
                json.decodeFromString<List<QueuedProgressUpdate>>(currentJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }

            // Remove existing update for this item if it exists
            currentList.removeAll { it.itemId == update.itemId }
            currentList.add(update)

            // Keep only last 50 updates to prevent unbounded growth
            val limitedList = currentList.takeLast(50)
            preferences[QUEUED_UPDATES_KEY] = json.encodeToString(limitedList)

            SecureLogger.d("OfflineProgress", "Queued progress for item ${update.itemId} at ${update.positionTicks} ticks")
        }
    }

    /**
     * Gets all queued updates and clears the queue.
     */
    suspend fun getAndClearUpdates(): List<QueuedProgressUpdate> {
        val preferences = dataStore.data.first()
        val currentJson = preferences[QUEUED_UPDATES_KEY] ?: "[]"

        val updates = try {
            json.decodeFromString<List<QueuedProgressUpdate>>(currentJson)
        } catch (e: Exception) {
            emptyList()
        }

        if (updates.isNotEmpty()) {
            dataStore.edit { it.remove(QUEUED_UPDATES_KEY) }
            SecureLogger.d("OfflineProgress", "Cleared ${updates.size} queued updates")
        }

        return updates
    }

    /**
     * Returns true if there are pending updates.
     */
    fun hasPendingUpdates(): Flow<Boolean> = dataStore.data.map { preferences ->
        val currentJson = preferences[QUEUED_UPDATES_KEY] ?: "[]"
        currentJson != "[]"
    }
}
