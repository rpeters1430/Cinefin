package com.rpeters.jellyfin.ui.utils

import android.content.Context
import android.util.Log
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.playback.EnhancedPlaybackManager
import com.rpeters.jellyfin.data.playback.PlaybackResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced playback utilities that integrate with the UI layer
 */
@Singleton
class EnhancedPlaybackUtils @Inject constructor(
    @ApplicationContext private val context: Context,
    private val enhancedPlaybackManager: EnhancedPlaybackManager,
) {

    companion object {
        private const val TAG = "EnhancedPlaybackUtils"
    }

    /**
     * Play media item with intelligent Direct Play/transcoding detection
     */
    suspend fun playMedia(
        item: BaseItemDto,
        onPlaybackStarted: (String, PlaybackInfo) -> Unit = { _, _ -> },
        onPlaybackError: (String) -> Unit = { },
    ) {
        withContext(Dispatchers.IO) {
            try {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Starting enhanced playback for: ${item.name} (${item.type})")
                }

                val playbackResult = enhancedPlaybackManager.getOptimalPlaybackUrl(item)

                withContext(Dispatchers.Main) {
                    when (playbackResult) {
                        is PlaybackResult.DirectPlay -> {
                            val playbackInfo = PlaybackInfo(
                                url = playbackResult.url,
                                isDirectPlay = true,
                                container = playbackResult.container,
                                videoCodec = playbackResult.videoCodec,
                                audioCodec = playbackResult.audioCodec,
                                bitrate = playbackResult.bitrate,
                                reason = playbackResult.reason
                            )
                            
                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "Direct Play: ${playbackResult.reason}")
                            }
                            
                            // Start playback using existing media player
                            startMediaPlayback(item, playbackInfo.url)
                            onPlaybackStarted(playbackResult.url, playbackInfo)
                        }
                        
                        is PlaybackResult.Transcoding -> {
                            val playbackInfo = PlaybackInfo(
                                url = playbackResult.url,
                                isDirectPlay = false,
                                container = playbackResult.targetContainer,
                                videoCodec = playbackResult.targetVideoCodec,
                                audioCodec = playbackResult.targetAudioCodec,
                                bitrate = playbackResult.targetBitrate,
                                reason = playbackResult.reason
                            )
                            
                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "Transcoding: ${playbackResult.reason}")
                                Log.d(TAG, "Target: ${playbackResult.targetVideoCodec}/${playbackResult.targetAudioCodec} " +
                                    "in ${playbackResult.targetContainer} @ ${playbackResult.targetBitrate / 1_000_000}Mbps")
                            }
                            
                            // Start transcoded playback
                            startMediaPlayback(item, playbackInfo.url)
                            onPlaybackStarted(playbackResult.url, playbackInfo)
                        }
                        
                        is PlaybackResult.Error -> {
                            Log.e(TAG, "Playback failed: ${playbackResult.message}")
                            onPlaybackError(playbackResult.message)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start enhanced playback", e)
                withContext(Dispatchers.Main) {
                    onPlaybackError("Failed to start playback: ${e.message}")
                }
            }
        }
    }

    /**
     * Start media playback using the appropriate player based on media type
     */
    private fun startMediaPlayback(item: BaseItemDto, streamUrl: String) {
        when (item.type) {
            BaseItemKind.VIDEO, BaseItemKind.MOVIE, BaseItemKind.EPISODE -> {
                // Use video player
                MediaPlayerUtils.playMedia(context, streamUrl, item)
            }
            
            BaseItemKind.AUDIO -> {
                // For audio, we could start an audio-specific player
                // For now, use the video player which can handle audio-only content
                MediaPlayerUtils.playMedia(context, streamUrl, item)
            }
            
            BaseItemKind.MUSIC_ALBUM -> {
                // For albums, we might want to play the first track or show a playlist
                Log.d(TAG, "Album playback - would typically get first track or create playlist")
                // Could be enhanced to get album tracks and play the first one
                MediaPlayerUtils.playMedia(context, streamUrl, item)
            }
            
            else -> {
                // Generic media playback
                MediaPlayerUtils.playMedia(context, streamUrl, item)
            }
        }
    }

    /**
     * Analyze playback capabilities for a media item without starting playback
     */
    suspend fun analyzePlaybackCapabilities(item: BaseItemDto): PlaybackCapabilityAnalysis {
        return withContext(Dispatchers.IO) {
            try {
                val playbackResult = enhancedPlaybackManager.getOptimalPlaybackUrl(item)
                
                when (playbackResult) {
                    is PlaybackResult.DirectPlay -> {
                        PlaybackCapabilityAnalysis(
                            canPlay = true,
                            preferredMethod = PlaybackMethod.DIRECT_PLAY,
                            expectedQuality = determineQualityFromBitrate(playbackResult.bitrate),
                            details = "Direct Play: ${playbackResult.reason}",
                            codecs = "${playbackResult.videoCodec ?: "N/A"}/${playbackResult.audioCodec ?: "N/A"}",
                            container = playbackResult.container,
                            estimatedBandwidth = playbackResult.bitrate
                        )
                    }
                    
                    is PlaybackResult.Transcoding -> {
                        PlaybackCapabilityAnalysis(
                            canPlay = true,
                            preferredMethod = PlaybackMethod.TRANSCODING,
                            expectedQuality = determineQualityFromResolution(playbackResult.targetResolution),
                            details = "Transcoding: ${playbackResult.reason}",
                            codecs = "${playbackResult.targetVideoCodec}/${playbackResult.targetAudioCodec}",
                            container = playbackResult.targetContainer,
                            estimatedBandwidth = playbackResult.targetBitrate
                        )
                    }
                    
                    is PlaybackResult.Error -> {
                        PlaybackCapabilityAnalysis(
                            canPlay = false,
                            preferredMethod = PlaybackMethod.UNAVAILABLE,
                            expectedQuality = "Unknown",
                            details = "Error: ${playbackResult.message}",
                            codecs = "N/A",
                            container = "N/A",
                            estimatedBandwidth = 0
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to analyze playback capabilities", e)
                PlaybackCapabilityAnalysis(
                    canPlay = false,
                    preferredMethod = PlaybackMethod.UNAVAILABLE,
                    expectedQuality = "Unknown",
                    details = "Analysis failed: ${e.message}",
                    codecs = "N/A",
                    container = "N/A",
                    estimatedBandwidth = 0
                )
            }
        }
    }

    /**
     * Get playback recommendations for better user experience
     */
    suspend fun getPlaybackRecommendations(item: BaseItemDto): List<PlaybackRecommendation> {
        return withContext(Dispatchers.IO) {
            val recommendations = mutableListOf<PlaybackRecommendation>()
            
            try {
                val analysis = analyzePlaybackCapabilities(item)
                
                when (analysis.preferredMethod) {
                    PlaybackMethod.DIRECT_PLAY -> {
                        recommendations.add(
                            PlaybackRecommendation(
                                type = RecommendationType.OPTIMAL,
                                message = "Optimal playback quality - Direct Play enabled",
                                details = "Your device can play this media without transcoding for best quality and performance."
                            )
                        )
                    }
                    
                    PlaybackMethod.TRANSCODING -> {
                        recommendations.add(
                            PlaybackRecommendation(
                                type = RecommendationType.INFO,
                                message = "Media will be transcoded for compatibility",
                                details = "The server will convert this media to a compatible format. Quality may be adjusted based on your network conditions."
                            )
                        )
                        
                        if (analysis.expectedQuality.contains("720p") || analysis.expectedQuality.contains("Low")) {
                            recommendations.add(
                                PlaybackRecommendation(
                                    type = RecommendationType.WARNING,
                                    message = "Quality reduced due to network or device limitations",
                                    details = "Consider using WiFi for better quality or check device storage space."
                                )
                            )
                        }
                    }
                    
                    PlaybackMethod.UNAVAILABLE -> {
                        recommendations.add(
                            PlaybackRecommendation(
                                type = RecommendationType.ERROR,
                                message = "Cannot play this media",
                                details = analysis.details
                            )
                        )
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate playback recommendations", e)
                recommendations.add(
                    PlaybackRecommendation(
                        type = RecommendationType.ERROR,
                        message = "Unable to analyze playback compatibility",
                        details = "Please try again or contact support if the problem persists."
                    )
                )
            }
            
            recommendations
        }
    }

    private fun determineQualityFromBitrate(bitrate: Int): String {
        return when {
            bitrate >= 20_000_000 -> "4K/Ultra High"
            bitrate >= 10_000_000 -> "1080p/High" 
            bitrate >= 5_000_000 -> "720p/Medium"
            bitrate >= 2_000_000 -> "480p/Low"
            else -> "Audio/Very Low"
        }
    }

    private fun determineQualityFromResolution(resolution: String): String {
        return when {
            resolution.contains("3840x2160") -> "4K/Ultra High"
            resolution.contains("1920x1080") -> "1080p/High"
            resolution.contains("1280x720") -> "720p/Medium"
            else -> "Standard Definition"
        }
    }
}

/**
 * Playback information for UI display
 */
data class PlaybackInfo(
    val url: String,
    val isDirectPlay: Boolean,
    val container: String,
    val videoCodec: String?,
    val audioCodec: String?,
    val bitrate: Int,
    val reason: String
)

/**
 * Playback capability analysis result
 */
data class PlaybackCapabilityAnalysis(
    val canPlay: Boolean,
    val preferredMethod: PlaybackMethod,
    val expectedQuality: String,
    val details: String,
    val codecs: String,
    val container: String,
    val estimatedBandwidth: Int
)

/**
 * Playback recommendation for user guidance
 */
data class PlaybackRecommendation(
    val type: RecommendationType,
    val message: String,
    val details: String
)

/**
 * Playback method enumeration
 */
enum class PlaybackMethod {
    DIRECT_PLAY,
    TRANSCODING,
    UNAVAILABLE
}

/**
 * Recommendation type for UI styling
 */
enum class RecommendationType {
    OPTIMAL,
    INFO, 
    WARNING,
    ERROR
}