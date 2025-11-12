package com.rpeters.jellyfin.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// DataStore delegate for theme preferences
private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "theme_preferences",
)

/**
 * Repository for managing theme preferences using DataStore.
 * Provides reactive access to theme settings across the application.
 */
@Singleton
class ThemePreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.themeDataStore

    /**
     * Flow of current theme preferences.
     */
    val themePreferencesFlow: Flow<ThemePreferences> = dataStore.data
        .catch { exception ->
            // If there's an error reading preferences, emit default values
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            ThemePreferences(
                themeMode = ThemeMode.valueOf(
                    preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name,
                ),
                useDynamicColors = preferences[PreferencesKeys.USE_DYNAMIC_COLORS] ?: true,
                accentColor = AccentColor.valueOf(
                    preferences[PreferencesKeys.ACCENT_COLOR] ?: AccentColor.JELLYFIN_PURPLE.name,
                ),
                contrastLevel = ContrastLevel.valueOf(
                    preferences[PreferencesKeys.CONTRAST_LEVEL] ?: ContrastLevel.STANDARD.name,
                ),
                useThemedIcon = preferences[PreferencesKeys.USE_THEMED_ICON] ?: true,
                enableEdgeToEdge = preferences[PreferencesKeys.ENABLE_EDGE_TO_EDGE] ?: true,
                respectReduceMotion = preferences[PreferencesKeys.RESPECT_REDUCE_MOTION] ?: true,
            )
        }

    /**
     * Update theme mode (System, Light, Dark, AMOLED Black).
     */
    suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = themeMode.name
        }
    }

    /**
     * Update dynamic colors setting.
     */
    suspend fun setUseDynamicColors(useDynamicColors: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_DYNAMIC_COLORS] = useDynamicColors
        }
    }

    /**
     * Update custom accent color.
     */
    suspend fun setAccentColor(accentColor: AccentColor) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCENT_COLOR] = accentColor.name
        }
    }

    /**
     * Update contrast level.
     */
    suspend fun setContrastLevel(contrastLevel: ContrastLevel) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CONTRAST_LEVEL] = contrastLevel.name
        }
    }

    /**
     * Update themed icon setting.
     */
    suspend fun setUseThemedIcon(useThemedIcon: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_THEMED_ICON] = useThemedIcon
        }
    }

    /**
     * Update edge-to-edge layout setting.
     */
    suspend fun setEnableEdgeToEdge(enableEdgeToEdge: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ENABLE_EDGE_TO_EDGE] = enableEdgeToEdge
        }
    }

    /**
     * Update reduce motion setting.
     */
    suspend fun setRespectReduceMotion(respectReduceMotion: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.RESPECT_REDUCE_MOTION] = respectReduceMotion
        }
    }

    /**
     * Reset all theme preferences to defaults.
     */
    suspend fun resetToDefaults() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLORS = booleanPreferencesKey("use_dynamic_colors")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val CONTRAST_LEVEL = stringPreferencesKey("contrast_level")
        val USE_THEMED_ICON = booleanPreferencesKey("use_themed_icon")
        val ENABLE_EDGE_TO_EDGE = booleanPreferencesKey("enable_edge_to_edge")
        val RESPECT_REDUCE_MOTION = booleanPreferencesKey("respect_reduce_motion")
    }

    companion object {
        private const val TAG = "ThemePreferencesRepository"
    }
}
