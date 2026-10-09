package com.rpeters.jellyfin.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rpeters.jellyfin.data.model.ServerProfile
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.serverProfilesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "server_profiles",
)

/**
 * Saved sign-ins and which one is active.
 */
data class ServerProfiles(
    val profiles: List<ServerProfile> = emptyList(),
    val activeProfileId: String? = null,
) {
    val activeProfile: ServerProfile?
        get() = profiles.firstOrNull { it.id == activeProfileId }
}

/**
 * Persists the list of saved server profiles. Jellyfin and Emby profiles share one list.
 *
 * Access tokens are stored the same way the single saved session always was (app-private
 * DataStore), so a profile can be restored at launch without a biometric prompt. Passwords stay
 * in [com.rpeters.jellyfin.data.SecureCredentialManager].
 */
@Singleton
class ServerProfileRepository(
    private val dataStore: DataStore<Preferences>,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.serverProfilesDataStore)

    private val json = Json {
        ignoreUnknownKeys = true
        // A profile written by a newer app version may carry a server type this one does not know.
        coerceInputValues = true
    }

    val serverProfiles: Flow<ServerProfiles> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                SecureLogger.w(TAG, "IOException reading server profiles, using empty list", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { it.toServerProfiles() }

    suspend fun current(): ServerProfiles = serverProfiles.first()

    /**
     * Adds the profile, or replaces the saved one with the same [ServerProfile.id], and makes it
     * the active profile.
     */
    suspend fun saveAndActivate(profile: ServerProfile) {
        dataStore.edit { preferences ->
            val existing = preferences.toServerProfiles().profiles
            val updated = if (existing.any { it.id == profile.id }) {
                existing.map { if (it.id == profile.id) profile else it }
            } else {
                existing + profile
            }
            preferences[Keys.PROFILES] = encode(updated)
            preferences[Keys.ACTIVE_PROFILE_ID] = profile.id
        }
    }

    /**
     * @return the newly active profile, or null when [profileId] is not saved.
     */
    suspend fun setActive(profileId: String): ServerProfile? {
        var activated: ServerProfile? = null
        dataStore.edit { preferences ->
            activated = preferences.toServerProfiles().profiles.firstOrNull { it.id == profileId }
            if (activated != null) {
                preferences[Keys.ACTIVE_PROFILE_ID] = profileId
            }
        }
        return activated
    }

    /**
     * Removes a profile. When it was the active one, no profile is active afterwards; the caller
     * decides whether to switch to another.
     *
     * @return the removed profile, or null when [profileId] is not saved.
     */
    suspend fun remove(profileId: String): ServerProfile? {
        var removed: ServerProfile? = null
        dataStore.edit { preferences ->
            val current = preferences.toServerProfiles()
            removed = current.profiles.firstOrNull { it.id == profileId }
            if (removed != null) {
                preferences[Keys.PROFILES] = encode(current.profiles.filterNot { it.id == profileId })
                if (current.activeProfileId == profileId) {
                    preferences.remove(Keys.ACTIVE_PROFILE_ID)
                }
            }
        }
        return removed
    }

    /**
     * Drops the stored token for a profile but keeps the profile in the list, so it can be
     * signed in to again from the switcher.
     */
    suspend fun clearToken(profileId: String) {
        dataStore.edit { preferences ->
            val profiles = preferences.toServerProfiles().profiles
            if (profiles.any { it.id == profileId }) {
                preferences[Keys.PROFILES] = encode(
                    profiles.map { if (it.id == profileId) it.copy(accessToken = null, loginTimestamp = null) else it },
                )
            }
        }
    }

    private fun Preferences.toServerProfiles(): ServerProfiles {
        val profiles = this[Keys.PROFILES]?.let(::decode).orEmpty()
        val activeId = this[Keys.ACTIVE_PROFILE_ID]?.takeIf { id -> profiles.any { it.id == id } }
        return ServerProfiles(profiles = profiles, activeProfileId = activeId)
    }

    private fun encode(profiles: List<ServerProfile>): String =
        json.encodeToString(ListSerializer(ServerProfile.serializer()), profiles)

    private fun decode(raw: String): List<ServerProfile> =
        try {
            json.decodeFromString(ListSerializer(ServerProfile.serializer()), raw)
        } catch (e: SerializationException) {
            SecureLogger.w(TAG, "Saved server profiles could not be read, starting with an empty list", e)
            emptyList()
        }

    private object Keys {
        val PROFILES = stringPreferencesKey("profiles")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
    }

    companion object {
        private const val TAG = "ServerProfileRepository"
    }
}
