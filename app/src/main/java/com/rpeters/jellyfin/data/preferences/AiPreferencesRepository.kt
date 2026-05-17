package com.rpeters.jellyfin.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.aiDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ai_preferences",
)

data class AiPreferences(
    val enableSmartContentWarnings: Boolean,
    val enableAiChapterMarkers: Boolean,
) {
    companion object {
        val DEFAULT = AiPreferences(
            enableSmartContentWarnings = true,
            enableAiChapterMarkers = true,
        )
    }
}

@Singleton
class AiPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.aiDataStore)

    private val TAG = "AiPreferencesRepo"

    val preferences: Flow<AiPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                SecureLogger.w(TAG, "IOException reading ai preferences, using defaults", exception)
                emit(emptyPreferences())
            } else {
                SecureLogger.e(TAG, "Unexpected error reading ai preferences", exception)
                throw exception
            }
        }
        .map { prefs ->
            AiPreferences(
                enableSmartContentWarnings = prefs[PreferencesKeys.ENABLE_CONTENT_WARNINGS] ?: AiPreferences.DEFAULT.enableSmartContentWarnings,
                enableAiChapterMarkers = prefs[PreferencesKeys.ENABLE_CHAPTER_MARKERS] ?: AiPreferences.DEFAULT.enableAiChapterMarkers,
            )
        }

    suspend fun updateEnableSmartContentWarnings(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.ENABLE_CONTENT_WARNINGS] = enabled
        }
    }

    suspend fun updateEnableAiChapterMarkers(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.ENABLE_CHAPTER_MARKERS] = enabled
        }
    }

    private object PreferencesKeys {
        val ENABLE_CONTENT_WARNINGS = booleanPreferencesKey("enable_smart_content_warnings")
        val ENABLE_CHAPTER_MARKERS = booleanPreferencesKey("enable_ai_chapter_markers")
    }
}
