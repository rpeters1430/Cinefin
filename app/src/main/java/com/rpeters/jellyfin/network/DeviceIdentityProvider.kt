package com.rpeters.jellyfin.network

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIdentityProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val deviceId: String by lazy { resolveDeviceId() }

    fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL?.takeIf { it.isNotBlank() }
        return listOfNotNull(manufacturer, model).joinToString(separator = " ").ifBlank { "Android" }
    }

    fun deviceId(): String = deviceId

    fun clientVersion(): String = BuildConfigWrapper.versionName

    fun clientName(): String = "Cinefin Android"

    /**
     * The `X-Plex-Client-Identifier` this install sends to plex.tv and Plex servers. Plex ties
     * sign-ins and devices to it, so it is made once and kept for the life of the install.
     */
    fun plexClientIdentifier(): String = plexClientIdentifier

    private val plexClientIdentifier: String by lazy { resolvePlexClientIdentifier() }

    private fun resolvePlexClientIdentifier(): String {
        val preferences = context.getSharedPreferences(PLEX_PREFERENCES, Context.MODE_PRIVATE)
        preferences.getString(KEY_PLEX_CLIENT_IDENTIFIER, null)?.let { return it }
        return UUID.randomUUID().toString().also {
            preferences.edit { putString(KEY_PLEX_CLIENT_IDENTIFIER, it) }
        }
    }

    private fun resolveDeviceId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (!androidId.isNullOrBlank()) {
            return androidId
        }
        return UUID.randomUUID().toString()
    }

    private companion object {
        const val PLEX_PREFERENCES = "plex_identity"
        const val KEY_PLEX_CLIENT_IDENTIFIER = "client_identifier"
    }
}

/**
 * Wrapper around BuildConfig so the provider can be unit-tested.
 */
object BuildConfigWrapper {
    val versionName: String
        get() = com.rpeters.jellyfin.BuildConfig.VERSION_NAME
}
