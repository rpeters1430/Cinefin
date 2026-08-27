package com.rpeters.jellyfin.data.credentials

import android.app.Activity
import android.content.Context
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges Jellyfin sign-in to Android's system Credential Manager (Google Password Manager)
 * so a saved login can carry over when a user sets up a new device, addressing Google Play's
 * Zero-Tap Sign-In quality requirement for apps without a company-owned identity backend.
 *
 * This is intentionally separate from [com.rpeters.jellyfin.data.SecureCredentialManager],
 * which encrypts credentials with an Android Keystore key that is hardware-bound and never
 * leaves the device. Credential Manager's password storage is the piece Google's own sync
 * backend can transport across devices; the Keystore-backed store never can, regardless of
 * Android Auto Backup settings.
 *
 * Cinefin authenticates against arbitrary self-hosted Jellyfin servers rather than one
 * fixed backend, so the saved credential's "id" encodes both the username and the server
 * URL, letting a restored credential drive a real reconnect on the new device.
 */
@Singleton
class PasswordCredentialSyncManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    data class SavedSignIn(val serverUrl: String, val username: String, val password: String)

    companion object {
        private const val TAG = "PasswordCredentialSync"

        // Unlikely to appear in either a username or a URL, so the pair round-trips losslessly.
        private const val ID_DELIMITER = " :: "

        internal fun encodeId(serverUrl: String, username: String): String =
            "$username$ID_DELIMITER$serverUrl"

        internal fun decodeId(id: String): Pair<String, String>? {
            val separatorIndex = id.indexOf(ID_DELIMITER)
            if (separatorIndex <= 0) return null
            val username = id.substring(0, separatorIndex)
            val serverUrl = id.substring(separatorIndex + ID_DELIMITER.length)
            if (username.isBlank() || serverUrl.isBlank()) return null
            return username to serverUrl
        }
    }

    private val credentialManager by lazy { CredentialManager.create(appContext) }

    /**
     * Offers to save [password] to the system password manager. Must be called with an
     * [Activity] context: Credential Manager's UI-bearing calls require one and throw at
     * runtime given only an application context.
     */
    suspend fun save(activity: Activity, serverUrl: String, username: String, password: String) {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) return
        val request = CreatePasswordRequest(
            id = encodeId(serverUrl, username),
            password = password,
        )
        try {
            credentialManager.createCredential(activity, request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CreateCredentialException) {
            // Includes user cancellation of the save prompt; never fatal to sign-in.
            SecureLogger.d(TAG, "Did not save credential to Credential Manager: ${e::class.simpleName}")
        }
    }

    /**
     * Looks up a sign-in previously saved with [save] on any device, e.g. one restored via
     * the user's Google account after a device migration. Returns null if none is available
     * or the user dismisses the system picker; never throws for that case.
     */
    suspend fun getSavedSignIn(activity: Activity): SavedSignIn? {
        val request = GetCredentialRequest(listOf(GetPasswordOption()))
        return try {
            val response = credentialManager.getCredential(activity, request)
            val credential = response.credential as? PasswordCredential ?: return null
            val (username, serverUrl) = decodeId(credential.id) ?: return null
            SavedSignIn(serverUrl = serverUrl, username = username, password = credential.password)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialException) {
            // Includes "no saved credential" and user cancellation of the picker.
            SecureLogger.d(TAG, "No credential retrieved from Credential Manager: ${e::class.simpleName}")
            null
        }
    }
}
