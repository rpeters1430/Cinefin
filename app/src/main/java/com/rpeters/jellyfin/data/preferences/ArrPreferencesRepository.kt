package com.rpeters.jellyfin.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rpeters.jellyfin.data.security.EncryptedPreferences
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sonarrDataStore: DataStore<Preferences> by preferencesDataStore(name = "sonarr_preferences")
private val Context.radarrDataStore: DataStore<Preferences> by preferencesDataStore(name = "radarr_preferences")

@Singleton
class ArrPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptedPreferences: EncryptedPreferences,
) {
    private val sonarrStore = context.sonarrDataStore
    private val radarrStore = context.radarrDataStore

    // ── Sonarr ──────────────────────────────────────────────────────────────

    val sonarrPreferencesFlow: Flow<SonarrPreferences> = sonarrStore.data
        .catch { e ->
            if (e is IOException) {
                SecureLogger.w(TAG, "IOException reading Sonarr prefs", e)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else throw e
        }
        .map { it[SonarrKeys.BASE_URL].orEmpty() to (it[SonarrKeys.IS_ENABLED] ?: false) }
        .combine(encryptedPreferences.getEncryptedString(KEY_SONARR_API_KEY)) { (url, enabled), key ->
            SonarrPreferences(baseUrl = url, apiKey = key.orEmpty(), isEnabled = enabled)
        }

    suspend fun updateSonarrUrl(url: String) = sonarrStore.edit { 
        it[SonarrKeys.BASE_URL] = com.rpeters.jellyfin.utils.normalizeServerUrl(url) 
    }
    suspend fun updateSonarrApiKey(key: String) = encryptedPreferences.putEncryptedString(KEY_SONARR_API_KEY, key.ifBlank { null })
    suspend fun setSonarrEnabled(enabled: Boolean) = sonarrStore.edit { it[SonarrKeys.IS_ENABLED] = enabled }

    // ── Radarr ──────────────────────────────────────────────────────────────

    val radarrPreferencesFlow: Flow<RadarrPreferences> = radarrStore.data
        .catch { e ->
            if (e is IOException) {
                SecureLogger.w(TAG, "IOException reading Radarr prefs", e)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else throw e
        }
        .map { it[RadarrKeys.BASE_URL].orEmpty() to (it[RadarrKeys.IS_ENABLED] ?: false) }
        .combine(encryptedPreferences.getEncryptedString(KEY_RADARR_API_KEY)) { (url, enabled), key ->
            RadarrPreferences(baseUrl = url, apiKey = key.orEmpty(), isEnabled = enabled)
        }

    suspend fun updateRadarrUrl(url: String) = radarrStore.edit { 
        it[RadarrKeys.BASE_URL] = com.rpeters.jellyfin.utils.normalizeServerUrl(url) 
    }
    suspend fun updateRadarrApiKey(key: String) = encryptedPreferences.putEncryptedString(KEY_RADARR_API_KEY, key.ifBlank { null })
    suspend fun setRadarrEnabled(enabled: Boolean) = radarrStore.edit { it[RadarrKeys.IS_ENABLED] = enabled }

    private object SonarrKeys {
        val BASE_URL = stringPreferencesKey("base_url")
        val IS_ENABLED = booleanPreferencesKey("is_enabled")
    }

    private object RadarrKeys {
        val BASE_URL = stringPreferencesKey("base_url")
        val IS_ENABLED = booleanPreferencesKey("is_enabled")
    }

    companion object {
        private const val TAG = "ArrPreferencesRepository"
        private const val KEY_SONARR_API_KEY = "sonarr_api_key"
        private const val KEY_RADARR_API_KEY = "radarr_api_key"
    }
}
