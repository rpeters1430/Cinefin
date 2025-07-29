package com.example.jellyfinandroid.ui.player

import com.example.jellyfinandroid.BuildConfig
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.MappingTrackSelector
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioTrack(
    val id: String,
    val language: String?,
    val label: String,
    val isSelected: Boolean = false,
    val channelCount: Int = 0,
    val bitrate: Int = 0
)

data class SubtitleTrack(
    val id: String,
    val language: String?,
    val label: String,
    val isSelected: Boolean = false,
    val isForced: Boolean = false
)

data class TrackSelectionState(
    val audioTracks: List<AudioTrack> = emptyList(),
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val selectedAudioTrackId: String? = null,
    val selectedSubtitleTrackId: String? = null,
    val subtitlesEnabled: Boolean = false
)

@UnstableApi
class TrackSelectionManager(
    private val exoPlayer: ExoPlayer,
    private val trackSelector: DefaultTrackSelector
) {
    
    private val _trackSelectionState = MutableStateFlow(TrackSelectionState())
    val trackSelectionState: StateFlow<TrackSelectionState> = _trackSelectionState.asStateFlow()
    
    fun updateAvailableTracks() {
        val currentTracks = exoPlayer.currentTracks
        val audioTracks = mutableListOf<AudioTrack>()
        val subtitleTracks = mutableListOf<SubtitleTrack>()
        
        // Process audio tracks
        for (trackGroup in currentTracks.groups) {
            if (trackGroup.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until trackGroup.length) {
                    val format = trackGroup.getTrackFormat(i)
                    val audioTrack = AudioTrack(
                        id = "${trackGroup.type}-${trackGroup.mediaTrackGroup.getFormat(i).id}",
                        language = format.language,
                        label = buildAudioTrackLabel(format),
                        isSelected = trackGroup.isTrackSelected(i),
                        channelCount = format.channelCount,
                        bitrate = format.bitrate
                    )
                    audioTracks.add(audioTrack)
                }
            }
        }
        
        // Process subtitle tracks
        for (trackGroup in currentTracks.groups) {
            if (trackGroup.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until trackGroup.length) {
                    val format = trackGroup.getTrackFormat(i)
                    val subtitleTrack = SubtitleTrack(
                        id = "${trackGroup.type}-${trackGroup.mediaTrackGroup.getFormat(i).id}",
                        language = format.language,
                        label = buildSubtitleTrackLabel(format),
                        isSelected = trackGroup.isTrackSelected(i),
                        isForced = (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0
                    )
                    subtitleTracks.add(subtitleTrack)
                }
            }
        }
        
        // Add "Off" option for subtitles
        val subtitleTracksWithOff = listOf(
            SubtitleTrack(
                id = "off",
                language = null,
                label = "Off",
                isSelected = !subtitleTracks.any { it.isSelected }
            )
        ) + subtitleTracks
        
        val selectedAudioId = audioTracks.find { it.isSelected }?.id
        val selectedSubtitleId = subtitleTracks.find { it.isSelected }?.id
        
        _trackSelectionState.value = _trackSelectionState.value.copy(
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracksWithOff,
            selectedAudioTrackId = selectedAudioId,
            selectedSubtitleTrackId = selectedSubtitleId ?: "off",
            subtitlesEnabled = selectedSubtitleId != null
        )
        
        if (BuildConfig.DEBUG) {
        
            Log.d("TrackSelectionManager", "Updated tracks: ${audioTracks.size} audio, ${subtitleTracks.size} subtitle")
        
        }
    }
    
    fun selectAudioTrack(trackId: String) {
        try {
            val currentTracks = exoPlayer.currentTracks
            val parametersBuilder = trackSelector.buildUponParameters()
            
            // Find and select the audio track
            for (trackGroup in currentTracks.groups) {
                if (trackGroup.type == C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until trackGroup.length) {
                        val format = trackGroup.getTrackFormat(i)
                        val currentTrackId = "${trackGroup.type}-${trackGroup.mediaTrackGroup.getFormat(i).id}"
                        
                        if (currentTrackId == trackId) {
                            parametersBuilder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                            // Note: Track selection API changed in Media3
                            // This would need to be updated for the new API
                            break
                        }
                    }
                }
            }
            
            trackSelector.setParameters(parametersBuilder.build())
            
            _trackSelectionState.value = _trackSelectionState.value.copy(
                selectedAudioTrackId = trackId
            )
            
            if (BuildConfig.DEBUG) {
            
                Log.d("TrackSelectionManager", "Selected audio track: $trackId")
            
            }
            
        } catch (e: Exception) {
            Log.e("TrackSelectionManager", "Failed to select audio track: $trackId", e)
        }
    }
    
    fun selectSubtitleTrack(trackId: String) {
        try {
            val parametersBuilder = trackSelector.buildUponParameters()
            
            if (trackId == "off") {
                // Disable subtitles
                parametersBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                
                _trackSelectionState.value = _trackSelectionState.value.copy(
                    selectedSubtitleTrackId = "off",
                    subtitlesEnabled = false
                )
                
                if (BuildConfig.DEBUG) {
                
                    Log.d("TrackSelectionManager", "Disabled subtitles")
                
                }
                
            } else {
                // Enable and select specific subtitle track
                val currentTracks = exoPlayer.currentTracks
                
                for (trackGroup in currentTracks.groups) {
                    if (trackGroup.type == C.TRACK_TYPE_TEXT) {
                        for (i in 0 until trackGroup.length) {
                            val format = trackGroup.getTrackFormat(i)
                            val currentTrackId = "${trackGroup.type}-${trackGroup.mediaTrackGroup.getFormat(i).id}"
                            
                            if (currentTrackId == trackId) {
                                parametersBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                // Note: Track selection API changed in Media3
                                // This would need to be updated for the new API
                                
                                _trackSelectionState.value = _trackSelectionState.value.copy(
                                    selectedSubtitleTrackId = trackId,
                                    subtitlesEnabled = true
                                )
                                
                                if (BuildConfig.DEBUG) {
                                
                                    Log.d("TrackSelectionManager", "Selected subtitle track: $trackId")
                                
                                }
                                break
                            }
                        }
                    }
                }
            }
            
            trackSelector.setParameters(parametersBuilder.build())
            
        } catch (e: Exception) {
            Log.e("TrackSelectionManager", "Failed to select subtitle track: $trackId", e)
        }
    }
    
    fun toggleSubtitles() {
        val currentState = _trackSelectionState.value
        if (currentState.subtitlesEnabled) {
            selectSubtitleTrack("off")
        } else {
            // Select first available subtitle track
            val firstSubtitleTrack = currentState.subtitleTracks.find { it.id != "off" }
            firstSubtitleTrack?.let { selectSubtitleTrack(it.id) }
        }
    }
    
    private fun buildAudioTrackLabel(format: Format): String {
        val language = format.language?.let { getLanguageDisplayName(it) } ?: "Unknown"
        val channels = when (format.channelCount) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> "${format.channelCount}ch"
        }
        val bitrate = if (format.bitrate > 0) {
            "${format.bitrate / 1000}kbps"
        } else {
            ""
        }
        
        return buildString {
            append(language)
            if (channels.isNotEmpty()) {
                append(" • $channels")
            }
            if (bitrate.isNotEmpty()) {
                append(" • $bitrate")
            }
        }
    }
    
    private fun buildSubtitleTrackLabel(format: Format): String {
        val language = format.language?.let { getLanguageDisplayName(it) } ?: "Unknown"
        val forced = if ((format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0) " (Forced)" else ""
        return "$language$forced"
    }
    
    private fun getLanguageDisplayName(languageCode: String): String {
        return when (languageCode.lowercase()) {
            "en", "eng" -> "English"
            "es", "spa" -> "Spanish"
            "fr", "fra" -> "French"
            "de", "ger" -> "German"
            "it", "ita" -> "Italian"
            "pt", "por" -> "Portuguese"
            "ru", "rus" -> "Russian"
            "ja", "jpn" -> "Japanese"
            "ko", "kor" -> "Korean"
            "zh", "chi" -> "Chinese"
            "ar", "ara" -> "Arabic"
            "hi", "hin" -> "Hindi"
            "nl", "dut" -> "Dutch"
            "sv", "swe" -> "Swedish"
            "no", "nor" -> "Norwegian"
            "da", "dan" -> "Danish"
            "fi", "fin" -> "Finnish"
            "pl", "pol" -> "Polish"
            "cs", "cze" -> "Czech"
            "hu", "hun" -> "Hungarian"
            "tr", "tur" -> "Turkish"
            "he", "heb" -> "Hebrew"
            "th", "tha" -> "Thai"
            "vi", "vie" -> "Vietnamese"
            else -> languageCode.uppercase()
        }
    }
}