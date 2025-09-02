package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.data.repository.JellyfinStreamRepository
import com.rpeters.jellyfin.ui.player.CastManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

/**
 * Dedicated ViewModel for streaming and media URL operations.
 * Extracted from MainAppViewModel to reduce complexity and prevent merge conflicts.
 */
@HiltViewModel
class StreamingViewModel @Inject constructor(
    private val streamRepository: JellyfinStreamRepository,
    @UnstableApi private val castManager: CastManager,
) : ViewModel() {

    /**
     * Get primary image URL for an item
     */
    fun getImageUrl(item: BaseItemDto): String? {
        return streamRepository.getImageUrl(item.id.toString(), "Primary", null)
    }

    /**
     * Get backdrop image URL for an item
     */
    fun getBackdropUrl(item: BaseItemDto): String? {
        return streamRepository.getBackdropUrl(item)
    }

    /**
     * Get series image URL for TV show episodes
     */
    fun getSeriesImageUrl(item: BaseItemDto): String? {
        return streamRepository.getSeriesImageUrl(item)
    }

    /**
     * Get streaming URL for an item
     */
    fun getStreamUrl(item: BaseItemDto): String? {
        return streamRepository.getStreamUrl(item.id.toString())
    }

    /**
     * Get download URL for offline storage
     */
    fun getDownloadUrl(item: BaseItemDto): String? {
        return streamRepository.getDownloadUrl(item.id.toString())
    }

    /**
     * Get direct stream URL optimized for downloads
     */
    fun getDirectStreamUrl(item: BaseItemDto, container: String? = null): String? {
        return streamRepository.getDirectStreamUrl(item.id.toString(), container)
    }

    /**
     * Send a preview (artwork + metadata) to the Cast device if connected
     */
    @UnstableApi
    fun sendCastPreview(item: BaseItemDto) {
        viewModelScope.launch {
            // Initialize cast on background thread if not yet initialized (safe to call multiple times)
            withContext(Dispatchers.IO) {
                castManager.initialize()
            }

            val image = getImageUrl(item)
            val backdrop = getBackdropUrl(item)
            castManager.loadPreview(item, imageUrl = image, backdropUrl = backdrop)
        }
    }

    /**
     * Check if cast is available and connected
     */
    @UnstableApi
    fun isCastConnected(): Boolean {
        return castManager.castState.value.isConnected
    }

    /**
     * Get cast device name if connected
     */
    @UnstableApi
    fun getCastDeviceName(): String? {
        return castManager.castState.value.deviceName
    }
}
