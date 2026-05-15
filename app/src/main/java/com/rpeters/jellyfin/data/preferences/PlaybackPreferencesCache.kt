package com.rpeters.jellyfin.data.preferences

import com.rpeters.jellyfin.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory mirror of [PlaybackPreferencesRepository] values that can be read synchronously
 * from non-coroutine contexts (e.g. utility objects, interceptors).
 *
 * The cache subscribes to the DataStore flow immediately on construction via [ApplicationScope],
 * so values are kept up-to-date whenever the user changes settings. Reads return the default
 * value until the first DataStore emission arrives (typically within milliseconds of app start).
 */
@Singleton
class PlaybackPreferencesCache @Inject constructor(
    private val repository: PlaybackPreferencesRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    @Volatile
    var useExternalPlayer: Boolean = PlaybackPreferences.DEFAULT.useExternalPlayer
        private set

    init {
        scope.launch {
            repository.preferences.collect { prefs ->
                useExternalPlayer = prefs.useExternalPlayer
            }
        }
    }
}
