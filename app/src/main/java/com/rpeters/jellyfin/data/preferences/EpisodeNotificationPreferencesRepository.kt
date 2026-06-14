package com.rpeters.jellyfin.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.episodeNotificationDataStore by preferencesDataStore(name = "episode_notifications")

@Serializable
data class FollowedSeriesNotification(
    val seriesId: String,
    val seriesName: String,
    val seasonCount: Int = 0,
    val episodeCount: Int = 0,
    val lastCheckedAt: Long = 0L,
)

data class EpisodeNotificationPreferences(
    val enabled: Boolean = true,
    val followedSeries: List<FollowedSeriesNotification> = emptyList(),
) {
    companion object {
        val DEFAULT = EpisodeNotificationPreferences()
    }
}

@Singleton
class EpisodeNotificationPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.episodeNotificationDataStore
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val preferencesFlow: Flow<EpisodeNotificationPreferences> = dataStore.data.map { prefs ->
        EpisodeNotificationPreferences(
            enabled = prefs[KEY_ENABLED] ?: true,
            followedSeries = decodeFollowedSeries(prefs[KEY_FOLLOWED_SERIES]),
        )
    }

    suspend fun getPreferences(): EpisodeNotificationPreferences = preferencesFlow.first()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_ENABLED] = enabled }
    }

    suspend fun followSeries(series: FollowedSeriesNotification) {
        dataStore.edit { prefs ->
            val current = decodeFollowedSeries(prefs[KEY_FOLLOWED_SERIES])
                .filterNot { it.seriesId == series.seriesId }
            prefs[KEY_FOLLOWED_SERIES] = encodeFollowedSeries((current + series).sortedBy { it.seriesName.lowercase() })
        }
    }

    suspend fun unfollowSeries(seriesId: String) {
        dataStore.edit { prefs ->
            val current = decodeFollowedSeries(prefs[KEY_FOLLOWED_SERIES])
                .filterNot { it.seriesId == seriesId }
            prefs[KEY_FOLLOWED_SERIES] = encodeFollowedSeries(current)
        }
    }

    suspend fun updateSeriesBaseline(seriesId: String, seasonCount: Int, episodeCount: Int) {
        dataStore.edit { prefs ->
            val current = decodeFollowedSeries(prefs[KEY_FOLLOWED_SERIES])
            prefs[KEY_FOLLOWED_SERIES] = encodeFollowedSeries(
                current.map { series ->
                    if (series.seriesId == seriesId) {
                        series.copy(
                            seasonCount = seasonCount,
                            episodeCount = episodeCount,
                            lastCheckedAt = System.currentTimeMillis(),
                        )
                    } else {
                        series
                    }
                },
            )
        }
    }

    private fun decodeFollowedSeries(raw: String?): List<FollowedSeriesNotification> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(FollowedSeriesNotification.serializer()), raw)
        } catch (e: Exception) {
            SecureLogger.w(TAG, "Failed to decode episode notification subscriptions: ${e.message}")
            emptyList()
        }
    }

    private fun encodeFollowedSeries(series: List<FollowedSeriesNotification>): String =
        json.encodeToString(ListSerializer(FollowedSeriesNotification.serializer()), series)

    companion object {
        private const val TAG = "EpisodeNotificationPrefs"
        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_FOLLOWED_SERIES = stringPreferencesKey("followed_series")
    }
}
