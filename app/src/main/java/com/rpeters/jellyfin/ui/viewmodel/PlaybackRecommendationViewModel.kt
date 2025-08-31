package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.ui.utils.EnhancedPlaybackUtils
import com.rpeters.jellyfin.ui.utils.PlaybackRecommendation
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

/**
 * ViewModel for managing playback recommendations and notifications
 */
@HiltViewModel
class PlaybackRecommendationViewModel @Inject constructor(
    private val enhancedPlaybackUtils: EnhancedPlaybackUtils,
) : ViewModel() {

    companion object {
        private const val TAG = "PlaybackRecommendationVM"
    }

    private val _recommendations = MutableStateFlow<List<PlaybackRecommendation>>(emptyList())
    val recommendations: StateFlow<List<PlaybackRecommendation>> = _recommendations.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentItem = MutableStateFlow<BaseItemDto?>(null)
    val currentItem: StateFlow<BaseItemDto?> = _currentItem.asStateFlow()

    /**
     * Analyze and get recommendations for a media item
     */
    fun analyzeItem(item: BaseItemDto) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _currentItem.value = item

                SecureLogger.d(TAG, "Analyzing playback recommendations for: ${item.name}")

                val recommendations = enhancedPlaybackUtils.getPlaybackRecommendations(item)
                _recommendations.value = recommendations

                SecureLogger.d(TAG, "Found ${recommendations.size} playback recommendations")
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to analyze playback recommendations", e)
                _recommendations.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Dismiss a specific recommendation
     */
    fun dismissRecommendation(recommendation: PlaybackRecommendation) {
        _recommendations.value = _recommendations.value.filterNot { it == recommendation }
    }

    /**
     * Clear all recommendations
     */
    fun clearRecommendations() {
        _recommendations.value = emptyList()
        _currentItem.value = null
    }

    /**
     * Refresh recommendations for the current item
     */
    fun refreshRecommendations() {
        _currentItem.value?.let { item ->
            analyzeItem(item)
        }
    }

    /**
     * Get recommendations count by type
     */
    fun getRecommendationCounts(): Map<com.rpeters.jellyfin.ui.utils.RecommendationType, Int> {
        return _recommendations.value.groupingBy { it.type }.eachCount()
    }

    /**
     * Check if there are any critical recommendations (warnings or errors)
     */
    fun hasCriticalRecommendations(): Boolean {
        return _recommendations.value.any {
            it.type == com.rpeters.jellyfin.ui.utils.RecommendationType.WARNING ||
                it.type == com.rpeters.jellyfin.ui.utils.RecommendationType.ERROR
        }
    }

    /**
     * Get recommendations filtered by type
     */
    fun getRecommendationsByType(type: com.rpeters.jellyfin.ui.utils.RecommendationType): List<PlaybackRecommendation> {
        return _recommendations.value.filter { it.type == type }
    }
}
