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

private val Context.seerrDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "seerr_preferences",
)

/**
 * Repository for managing Seerr (Overseerr/Jellyseerr) integration settings.
 * The API key is stored encrypted via EncryptedPreferences (AES-256-GCM / Android Keystore).
 * Non-sensitive fields (base URL, enabled toggle) use plain DataStore.
 */
@Singleton
class SeerrPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptedPreferences: EncryptedPreferences,
) {
    private val dataStore = context.seerrDataStore

    val seerrPreferencesFlow: Flow<SeerrPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                SecureLogger.w(TAG, "IOException reading seerr preferences", exception)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                SecureLogger.e(TAG, "Error reading seerr preferences", exception)
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.BASE_URL].orEmpty() to
                (preferences[PreferencesKeys.IS_ENABLED] ?: false)
        }
        .combine(encryptedPreferences.getEncryptedString(ENCRYPTED_KEY_API_KEY)) { (baseUrl, isEnabled), apiKey ->
            SeerrPreferences(
                baseUrl = baseUrl,
                apiKey = apiKey.orEmpty(),
                isEnabled = isEnabled,
            )
        }

    suspend fun updateBaseUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BASE_URL] = com.rpeters.jellyfin.utils.normalizeServerUrl(url)
        }
    }

    suspend fun updateApiKey(apiKey: String) {
        encryptedPreferences.putEncryptedString(ENCRYPTED_KEY_API_KEY, apiKey.ifBlank { null })
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_ENABLED] = enabled
        }
    }

    private object PreferencesKeys {
        val BASE_URL = stringPreferencesKey("base_url")
        val IS_ENABLED = booleanPreferencesKey("is_enabled")
    }

    companion object {
        private const val TAG = "SeerrPreferencesRepository"
        private const val ENCRYPTED_KEY_API_KEY = "seerr_api_key"
    }
}
