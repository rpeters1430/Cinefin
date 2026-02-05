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

private val Context.playbackDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "playback_preferences",
)

/**
 * Transcoding quality levels.
 */
enum class TranscodingQuality(val label: String) {
    AUTO("Auto"),
    MAXIMUM("Maximum"),
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low"),
}

/**
 * Audio channel preferences.
 */
enum class AudioChannelPreference(val label: String, val channels: Int?) {
    AUTO("Auto", null),
    STEREO("Stereo", 2),
    SURROUND_5_1("5.1 Surround", 6),
    SURROUND_7_1("7.1 Surround", 8),
}

data class PlaybackPreferences(
    val maxBitrateWifi: Int,
    val maxBitrateCellular: Int,
    val transcodingQuality: TranscodingQuality,
    val audioChannels: AudioChannelPreference,
) {
    companion object {
        val DEFAULT = PlaybackPreferences(
            maxBitrateWifi = 80_000_000,    // 80 Mbps
            maxBitrateCellular = 25_000_000, // 25 Mbps
            transcodingQuality = TranscodingQuality.AUTO,
            audioChannels = AudioChannelPreference.AUTO
        )
    }
}

/**
 * Repository for managing playback-related user preferences using DataStore.
 */
@Singleton
class PlaybackPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.playbackDataStore)

    val preferences: Flow<PlaybackPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                SecureLogger.w(TAG, "IOException reading playback preferences, using defaults", exception)
                emit(emptyPreferences())
            } else {
                SecureLogger.e(TAG, "Unexpected error reading playback preferences", exception)
                throw exception
            }
        }
        .map { prefs ->
            PlaybackPreferences(
                maxBitrateWifi = prefs[PreferencesKeys.MAX_BITRATE_WIFI] ?: PlaybackPreferences.DEFAULT.maxBitrateWifi,
                maxBitrateCellular = prefs[PreferencesKeys.MAX_BITRATE_CELLULAR] ?: PlaybackPreferences.DEFAULT.maxBitrateCellular,
                transcodingQuality = runCatching {
                    TranscodingQuality.valueOf(prefs[PreferencesKeys.TRANSCODING_QUALITY] ?: "")
                }.getOrDefault(PlaybackPreferences.DEFAULT.transcodingQuality),
                audioChannels = runCatching {
                    AudioChannelPreference.valueOf(prefs[PreferencesKeys.AUDIO_CHANNELS] ?: "")
                }.getOrDefault(PlaybackPreferences.DEFAULT.audioChannels)
            )
        }

    suspend fun setMaxBitrateWifi(bitrate: Int) {
        dataStore.edit { it[PreferencesKeys.MAX_BITRATE_WIFI] = bitrate }
    }

    suspend fun setMaxBitrateCellular(bitrate: Int) {
        dataStore.edit { it[PreferencesKeys.MAX_BITRATE_CELLULAR] = bitrate }
    }

    suspend fun setTranscodingQuality(quality: TranscodingQuality) {
        dataStore.edit { it[PreferencesKeys.TRANSCODING_QUALITY] = quality.name }
    }

    suspend fun setAudioChannels(preference: AudioChannelPreference) {
        dataStore.edit { it[PreferencesKeys.AUDIO_CHANNELS] = preference.name }
    }

    private object PreferencesKeys {
        val MAX_BITRATE_WIFI = intPreferencesKey("max_bitrate_wifi")
        val MAX_BITRATE_CELLULAR = intPreferencesKey("max_bitrate_cellular")
        val TRANSCODING_QUALITY = stringPreferencesKey("transcoding_quality")
        val AUDIO_CHANNELS = stringPreferencesKey("audio_channels")
    }

    companion object {
        private const val TAG = "PlaybackPrefsRepo"
    }
}
