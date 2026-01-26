package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.preferences.AccentColor
import com.rpeters.jellyfin.data.preferences.ContrastLevel
import com.rpeters.jellyfin.data.preferences.ThemeMode
import com.rpeters.jellyfin.data.preferences.ThemePreferences
import com.rpeters.jellyfin.data.preferences.ThemePreferencesRepository
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing theme preferences.
 * Provides reactive access to theme settings and methods to update them.
 */
@HiltViewModel
class ThemePreferencesViewModel @Inject constructor(
    private val themePreferencesRepository: ThemePreferencesRepository,
) : ViewModel() {

    /**
     * Current theme preferences as a StateFlow.
     * Starts with default values and updates reactively when preferences change.
     */
    val themePreferences: StateFlow<ThemePreferences> =
        themePreferencesRepository.themePreferencesFlow
            .catch { exception ->
                SecureLogger.e(TAG, "Error loading theme preferences", exception)
                emit(ThemePreferences.DEFAULT)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ThemePreferences.DEFAULT,
            )

    /**
     * Update the theme mode.
     */
    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            try {
                themePreferencesRepository.setThemeMode(themeMode)
                SecureLogger.d(TAG, "Theme mode updated to: $themeMode")
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /**
     * Toggle dynamic colors on/off.
     */
    fun setUseDynamicColors(useDynamicColors: Boolean) {
        viewModelScope.launch {
            try {
                themePreferencesRepository.setUseDynamicColors(useDynamicColors)
                SecureLogger.d(TAG, "Dynamic colors updated to: $useDynamicColors")
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /**
     * Update the custom accent color.
     */
    fun setAccentColor(accentColor: AccentColor) {
        viewModelScope.launch {
            try {
                themePreferencesRepository.setAccentColor(accentColor)
                SecureLogger.d(TAG, "Accent color updated to: $accentColor")
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /**
     * Update the contrast level.
     */
    fun setContrastLevel(contrastLevel: ContrastLevel) {
        viewModelScope.launch {
            try {
                themePreferencesRepository.setContrastLevel(contrastLevel)
                SecureLogger.d(TAG, "Contrast level updated to: $contrastLevel")
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /**
     * Toggle themed icon on/off.
     */
    fun setUseThemedIcon(useThemedIcon: Boolean) {
        viewModelScope.launch {
            try {
                themePreferencesRepository.setUseThemedIcon(useThemedIcon)
                SecureLogger.d(TAG, "Themed icon updated to: $useThemedIcon")
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /**
     * Toggle edge-to-edge layout on/off.
     */
    fun setEnableEdgeToEdge(enableEdgeToEdge: Boolean) {
        viewModelScope.launch {
            try {
                themePreferencesRepository.setEnableEdgeToEdge(enableEdgeToEdge)
                SecureLogger.d(TAG, "Edge-to-edge updated to: $enableEdgeToEdge")
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /**
     * Toggle respect reduce motion on/off.
     */
    fun setRespectReduceMotion(respectReduceMotion: Boolean) {
        viewModelScope.launch {
            try {
                themePreferencesRepository.setRespectReduceMotion(respectReduceMotion)
                SecureLogger.d(TAG, "Respect reduce motion updated to: $respectReduceMotion")
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /**
     * Reset all theme preferences to defaults.
     */
    fun resetToDefaults() {
        viewModelScope.launch {
            try {
                themePreferencesRepository.resetToDefaults()
                SecureLogger.d(TAG, "Theme preferences reset to defaults")
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    companion object {
        private const val TAG = "ThemePreferencesViewModel"
    }
}
