package com.rpeters.jellyfin.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.ui.viewmodel.PreferencesKeys
import com.rpeters.jellyfin.ui.viewmodel.dataStore
import com.rpeters.jellyfin.utils.normalizeServerUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single persisted session that the connection screen restores at launch.
 *
 * Saved server profiles live in
 * [com.rpeters.jellyfin.data.preferences.ServerProfileRepository]; this store mirrors whichever
 * one is active into the `login_preferences` keys the launch path has always read, so switching
 * profiles survives an app restart without changing that path.
 */
@Singleton
class ActiveSessionStore(
    private val dataStore: DataStore<Preferences>,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.dataStore)

    /** Does nothing when the server has no signed-in user or token to restore. */
    suspend fun save(server: JellyfinServer) {
        val username = server.username
        val token = server.accessToken
        if (server.url.isBlank() || username.isNullOrBlank() || token.isNullOrBlank()) return

        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SERVER_URL] = normalizeServerUrl(server.url)
            preferences[PreferencesKeys.USERNAME] = username
            preferences[PreferencesKeys.SESSION_TOKEN] = token
            val userId = server.userId
            if (userId != null) {
                preferences[PreferencesKeys.SESSION_USER_ID] = userId
            } else {
                preferences.remove(PreferencesKeys.SESSION_USER_ID)
            }
            preferences[PreferencesKeys.SESSION_SERVER_ID] = server.id
            preferences[PreferencesKeys.SESSION_SERVER_NAME] = server.name
            preferences[PreferencesKeys.SESSION_LOGIN_TIMESTAMP] = server.loginTimestamp ?: System.currentTimeMillis()
            preferences[PreferencesKeys.SESSION_IS_ADMIN] = server.isAdministrator
            preferences[PreferencesKeys.SESSION_SERVER_TYPE] = server.serverType.name
            server.embyConnectUserId?.let { preferences[PreferencesKeys.EMBY_CONNECT_USER_ID] = it }
                ?: preferences.remove(PreferencesKeys.EMBY_CONNECT_USER_ID)
            server.embyConnectAccessKey?.let { preferences[PreferencesKeys.EMBY_CONNECT_ACCESS_KEY] = it }
                ?: preferences.remove(PreferencesKeys.EMBY_CONNECT_ACCESS_KEY)
        }
    }

    /** Removes the saved server, user, and token. Login options such as biometrics are kept. */
    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.SERVER_URL)
            preferences.remove(PreferencesKeys.USERNAME)
            preferences.remove(PreferencesKeys.SESSION_TOKEN)
            preferences.remove(PreferencesKeys.SESSION_USER_ID)
            preferences.remove(PreferencesKeys.SESSION_SERVER_ID)
            preferences.remove(PreferencesKeys.SESSION_SERVER_NAME)
            preferences.remove(PreferencesKeys.SESSION_LOGIN_TIMESTAMP)
            preferences.remove(PreferencesKeys.SESSION_IS_ADMIN)
            preferences.remove(PreferencesKeys.SESSION_SERVER_TYPE)
            preferences.remove(PreferencesKeys.EMBY_CONNECT_USER_ID)
            preferences.remove(PreferencesKeys.EMBY_CONNECT_ACCESS_KEY)
        }
    }
}
