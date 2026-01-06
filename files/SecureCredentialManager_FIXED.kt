package com.rpeters.jellyfin.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.fragment.app.FragmentActivity
import com.rpeters.jellyfin.core.constants.Constants
import com.rpeters.jellyfin.utils.normalizeServerUrl
import com.rpeters.jellyfin.utils.normalizeServerUrlLegacy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureCredentialManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "SecureCredentialManager"
        private const val KEY_VERSION = "v1"
        private const val KEY_ROTATION_INTERVAL_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
        private const val DATASTORE_NAME = "secure_credentials"
    }

    // ✅ FIX: Create DataStore directly as a property instead of using Context extension
    // This avoids potential issues with Context extension properties in Singleton classes
    private val secureCredentialsDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = {
                context.preferencesDataStoreFile(DATASTORE_NAME)
            },
        )

    // ✅ ENHANCEMENT: Use modern Android Keystore with DataStore for secure storage
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
    }

    // Biometric authentication manager
    private val biometricAuthManager by lazy { BiometricAuthManager(context) }

    /**
     * Checks if biometric authentication is available on the device.
     *
     * @return true if biometric authentication is available, false otherwise
     */
    fun isBiometricAuthAvailable(): Boolean {
        return biometricAuthManager.isBiometricAuthAvailable()
    }

    /**
     * Generates a unique key alias that includes a version and timestamp to support key rotation
     */
    private fun getKeyAlias(timestamp: Long = System.currentTimeMillis()): String {
        return "${Constants.Security.KEY_ALIAS}_${KEY_VERSION}_${timestamp / KEY_ROTATION_INTERVAL_MS}"
    }

    /**
     * Gets the buggy key alias format that was used before the typo fix
     * This is needed to decrypt passwords saved with the old buggy format
     */
    private fun getBuggyKeyAlias(timestamp: Long = System.currentTimeMillis()): String {
        return "${Constants.Security.KEY_ALIAS}_$KEY_VERSION}_${timestamp / KEY_ROTATION_INTERVAL_MS}"
    }

    /**
     * Gets or creates a secret key with an expiration timestamp for key rotation.
     * Performs keystore operations on background thread.
     */
    private suspend fun getOrCreateSecretKey(forceNew: Boolean = false): SecretKey = withContext(Dispatchers.IO) {
        val currentAlias = getKeyAlias()
        val buggyAlias = getBuggyKeyAlias()

        // Check if key exists (try both current and buggy formats for backward compatibility)
        val keyExists = keyStore.containsAlias(currentAlias) || keyStore.containsAlias(buggyAlias)

        // Check if we should rotate the key (force new key or key doesn't exist)
        if (forceNew || !keyExists) {
            // Remove old keys (keep only the most recent ones)
            val aliases = keyStore.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                if (alias.startsWith(Constants.Security.KEY_ALIAS) && alias != currentAlias) {
                    try {
                        keyStore.deleteEntry(alias)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete old key: $alias", e)
                    }
                }
            }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                currentAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // CRITICAL: Allow caller-provided IV for decryption on Android API 36+
                // Without this, decryption with stored IV fails with:
                // "InvalidAlgorithmParameterException: Caller-provided IV not permitted"
                .setRandomizedEncryptionRequired(false)
                // SECURITY NOTE: User authentication not required for credential access
                //
                // Security Trade-offs:
                // - false: Credentials can be decrypted without biometric/PIN prompt
                //   • Pro: Better UX - no authentication prompt on every app launch
                //   • Con: If device is unlocked, credentials accessible without additional auth
                //
                // - true: Requires biometric/PIN for every credential access
                //   • Pro: Maximum security - credentials need authentication to decrypt
                //   • Con: User must authenticate every time credentials are needed
                //   • Requires: .setUserAuthenticationValidityDurationSeconds(300) for timeout
                //
                // Current Implementation: Prioritizes user experience over maximum security.
                // The AndroidKeyStore still provides hardware-backed encryption, and device
                // lock screen provides the primary security boundary.
                //
                // Alternative Implementation (Maximum Security):
                // To require authentication for credential access:
                // ```
                // .setUserAuthenticationRequired(true)
                // .setUserAuthenticationValidityDurationSeconds(300) // 5-minute validity
                // ```
                //
                // TODO: Consider adding a user setting to enable/disable authentication requirement
                // TODO: Add support for biometric-only keys (API 30+) using BiometricPrompt
                .setUserAuthenticationRequired(false)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            return@withContext keyGenerator.generateKey()
        }

        // Try to get the key using the current alias first, then fall back to buggy alias
        // This provides backward compatibility for passwords encrypted with the old buggy key format
        return@withContext when {
            keyStore.containsAlias(currentAlias) -> keyStore.getKey(currentAlias, null) as SecretKey
            keyStore.containsAlias(buggyAlias) -> keyStore.getKey(buggyAlias, null) as SecretKey
            else -> throw IllegalStateException("No encryption key found")
        }
    }

    /**
     * Encrypts data using AES/GCM/NoPadding with a new IV for each encryption.
     * Performs keystore operations on background thread.
     */
    private suspend fun encrypt(data: String): String = withContext(Dispatchers.IO) {
        try {
            val cipher = Cipher.getInstance(Constants.Security.ENCRYPTION_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())

            val iv = cipher.iv
            val encryptedData = cipher.doFinal(data.toByteArray())

            // Combine IV + encrypted data and encode to Base64
            val combined = iv + encryptedData
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            throw SecurityException("Failed to encrypt data", e)
        }
    }

    /**
     * Decrypts data using AES/GCM/NoPadding.
     * Performs keystore operations on background thread.
     */
    private suspend fun decrypt(encryptedData: String): String? = withContext(Dispatchers.IO) {
        try {
            val combined = Base64.decode(encryptedData, Base64.NO_WRAP)
            val iv = combined.sliceArray(0 until Constants.Security.IV_LENGTH)
            val cipherData = combined.sliceArray(Constants.Security.IV_LENGTH until combined.size)

            val cipher = Cipher.getInstance(Constants.Security.ENCRYPTION_TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)

            String(cipher.doFinal(cipherData))
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            null // Return null if decryption fails
        }
    }

    /**
     * Forces key rotation by generating a new key
     */
    suspend fun rotateKey() {
        try {
            getOrCreateSecretKey(forceNew = true)
            Log.d(TAG, "Key rotation completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Key rotation failed", e)
            throw SecurityException("Failed to rotate encryption key", e)
        }
    }

    // ✅ ENHANCEMENT: Modern secure storage with Android Keystore + DataStore + Key Rotation
    suspend fun savePassword(serverUrl: String, username: String, password: String) {
        val keys = generateKeys(serverUrl, username)
        val encryptedPassword = encrypt(password)

        android.util.Log.d(TAG, "savePassword: Saving password for user='$username', serverUrl='$serverUrl'")
        android.util.Log.d(TAG, "savePassword: Generated key='${keys.newKey}'")
        android.util.Log.d(TAG, "savePassword: Encrypted password length: ${encryptedPassword.length}")

        try {
            // CRITICAL FIX: Use NonCancellable to ensure password save completes even if parent scope is cancelled
            // This prevents the JobCancellationException that occurs when the app navigates away after successful login
            // before the DataStore edit operation completes
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                // Check DataStore state before edit
                val beforeEdit = secureCredentialsDataStore.data.first()
                android.util.Log.d(TAG, "savePassword: DataStore before edit contains ${beforeEdit.asMap().size} entries")

                secureCredentialsDataStore.edit { prefs ->
                    android.util.Log.d(TAG, "savePassword: Inside edit block, setting key='${keys.newKey}'")
                    prefs[stringPreferencesKey(keys.newKey)] = encryptedPassword
                    prefs[longPreferencesKey("${keys.newKey}_timestamp")] = System.currentTimeMillis()
                    prefs.remove(stringPreferencesKey(keys.legacyRawKey))
                    prefs.remove(longPreferencesKey("${keys.legacyRawKey}_timestamp"))
                    prefs.remove(stringPreferencesKey(keys.legacyNormalizedKey))
                    prefs.remove(longPreferencesKey("${keys.legacyNormalizedKey}_timestamp"))
                    android.util.Log.d(TAG, "savePassword: Edit block completed, prefs now contains ${prefs.asMap().size} entries")
                }

                android.util.Log.d(TAG, "savePassword: Edit operation returned successfully")

                // Verify the password was saved by immediately reading it back
                val verification = secureCredentialsDataStore.data.first()
                android.util.Log.d(TAG, "savePassword: Verification read returned ${verification.asMap().size} entries")

                val savedPassword = verification[stringPreferencesKey(keys.newKey)]
                if (savedPassword != null) {
                    android.util.Log.d(TAG, "savePassword: ✅ Password saved successfully and verified in DataStore")
                    android.util.Log.d(TAG, "savePassword: DataStore now contains ${verification.asMap().size} entries")
                } else {
                    android.util.Log.e(TAG, "savePassword: ❌ ERROR - Password was not found in DataStore after saving!")
                    android.util.Log.e(TAG, "savePassword: DataStore keys present: ${verification.asMap().keys.joinToString { it.name }}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "savePassword: EXCEPTION during save operation", e)
            throw e
        }
    }

    /**
     * Gets a password with optional biometric authentication
     *
     * @param serverUrl The server URL
     * @param username The username
     * @param activity The activity to use for biometric prompt (if null, no biometric auth)
     * @return The decrypted password or null if not found or auth failed
     */
    suspend fun getPassword(
        serverUrl: String,
        username: String,
        activity: FragmentActivity? = null,
    ): String? {
        android.util.Log.d(TAG, "getPassword: Retrieving password for user='$username', serverUrl='$serverUrl'")

        // If activity is provided and biometric auth is available, request auth
        if (activity != null && biometricAuthManager.isBiometricAuthAvailable()) {
            android.util.Log.d(TAG, "getPassword: Requesting biometric authentication")
            val authSuccess = biometricAuthManager.requestBiometricAuth(
                activity = activity,
                title = "Access Credentials",
                subtitle = "Authenticate to access your saved credentials",
                description = "Confirm your identity to retrieve saved login information",
            )

            // If biometric auth failed, return null
            if (!authSuccess) {
                android.util.Log.w(TAG, "getPassword: Biometric authentication failed")
                return null
            }
        }

        val keys = generateKeys(serverUrl, username)
        android.util.Log.d(TAG, "getPassword: Generated key='${keys.newKey}'")

        val preferences = secureCredentialsDataStore.data.first()
        android.util.Log.d(TAG, "getPassword: DataStore contains ${preferences.asMap().size} entries")
        // Log all DataStore keys for debugging (excluding actual password values)
        preferences.asMap().keys.forEach { key ->
            android.util.Log.d(TAG, "getPassword: DataStore key found: ${key.name}")
        }
        var encryptedPassword = preferences[stringPreferencesKey(keys.newKey)]

        if (encryptedPassword == null) {
            android.util.Log.d(TAG, "getPassword: Password not found with new key='${keys.newKey}', checking legacy keys")
            val legacySearchOrder = listOf(keys.legacyNormalizedKey, keys.legacyRawKey)
            var matchedKey: String? = null
            for (legacyKey in legacySearchOrder) {
                android.util.Log.d(TAG, "getPassword: Checking legacy key='$legacyKey'")
                encryptedPassword = preferences[stringPreferencesKey(legacyKey)]
                if (encryptedPassword != null) {
                    matchedKey = legacyKey
                    android.util.Log.d(TAG, "getPassword: Found password with legacy key='$legacyKey'")
                    break
                }
            }

            // FIX: If still not found, try with the raw (non-normalized) server URL
            // This handles the case where credentials were saved before URL normalization was fixed
            if (encryptedPassword == null) {
                android.util.Log.d(TAG, "getPassword: Checking with raw server URL (pre-normalization fix)")
                val rawKey = generateKey(serverUrl, username)
                if (rawKey != keys.newKey) { // Don't check the same key twice
                    android.util.Log.d(TAG, "getPassword: Trying raw URL key='$rawKey'")
                    encryptedPassword = preferences[stringPreferencesKey(rawKey)]
                    if (encryptedPassword != null) {
                        matchedKey = rawKey
                        android.util.Log.d(TAG, "getPassword: Found password with raw URL key='$rawKey'")
                    }
                }
            }

            if (encryptedPassword != null && matchedKey != null) {
                android.util.Log.d(TAG, "getPassword: Migrating legacy credential from key='$matchedKey' to key='${keys.newKey}'")
                migrateLegacyCredential(matchedKey, keys.newKey, encryptedPassword)
            }
        } else {
            android.util.Log.d(TAG, "getPassword: Found password with new key")
        }

        val result = encryptedPassword?.let { decrypt(it) }
        android.util.Log.d(TAG, "getPassword: Returning password ${if (result != null) "SUCCESS" else "NULL"}")
        return result
    }

    /**
     * Gets a password without biometric authentication (for backward compatibility)
     */
    suspend fun getPassword(serverUrl: String, username: String): String? {
        return getPassword(serverUrl, username, null)
    }

    suspend fun clearPassword(serverUrl: String, username: String) {
        val keys = generateKeys(serverUrl, username)
        secureCredentialsDataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey(keys.newKey))
            prefs.remove(longPreferencesKey("${keys.newKey}_timestamp"))
            prefs.remove(stringPreferencesKey(keys.legacyRawKey))
            prefs.remove(longPreferencesKey("${keys.legacyRawKey}_timestamp"))
            prefs.remove(stringPreferencesKey(keys.legacyNormalizedKey))
            prefs.remove(longPreferencesKey("${keys.legacyNormalizedKey}_timestamp"))
        }
    }

    suspend fun clearAllPasswords() {
        secureCredentialsDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun clearCredentials() {
        clearAllPasswords()
    }

    private data class CredentialKeys(
        val newKey: String,
        val legacyRawKey: String,
        val legacyNormalizedKey: String,
    )

    private fun generateKeys(serverUrl: String, username: String): CredentialKeys {
        val normalizedUrl = normalizeServerUrl(serverUrl)
        val legacyNormalizedUrl = normalizeServerUrlLegacy(serverUrl)
        val rawTrimmedUrl = serverUrl.trim()

        val newKey = generateKey(normalizedUrl, username)
        val legacyNormalizedKey = generateKey(legacyNormalizedUrl, username)
        val legacyRawKey = generateKey(rawTrimmedUrl, username)

        return CredentialKeys(newKey, legacyRawKey, legacyNormalizedKey)
    }

    private suspend fun migrateLegacyCredential(
        oldKey: String,
        newKey: String,
        encryptedPassword: String,
    ) {
        secureCredentialsDataStore.edit { prefs ->
            prefs[stringPreferencesKey(newKey)] = encryptedPassword
            prefs[longPreferencesKey("${newKey}_timestamp")] =
                prefs[longPreferencesKey("${oldKey}_timestamp")] ?: System.currentTimeMillis()
            prefs.remove(stringPreferencesKey(oldKey))
            prefs.remove(longPreferencesKey("${oldKey}_timestamp"))
        }
    }

    // ✅ ENHANCEMENT: Improved key generation with cryptographically secure hashing
    @VisibleForTesting
    internal fun generateKey(serverUrl: String, username: String): String {
        require(serverUrl.isNotBlank()) { "Server URL cannot be blank" }
        require(username.isNotBlank()) { "Username cannot be blank" }

        // Use SHA-256 for more secure key generation
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$serverUrl::$username::${Constants.Security.KEY_ALIAS}".toByteArray()
        val hash = digest.digest(input)

        // Take first 16 bytes and encode as hex for the key
        val keyBytes = hash.sliceArray(0 until 16)
        return "pwd_${keyBytes.joinToString("") { "%02x".format(it) }}"
    }
}
