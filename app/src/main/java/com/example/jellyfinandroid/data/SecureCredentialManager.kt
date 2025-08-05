package com.example.jellyfinandroid.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.jellyfinandroid.utils.AppConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

// Modern secure DataStore extension
private val Context.secureCredentialsDataStore: DataStore<Preferences> by preferencesDataStore(name = "secure_credentials")

@Singleton
class SecureCredentialManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // ✅ FIX: Use modern Android Keystore with DataStore for secure storage
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        return if (keyStore.containsAlias(AppConstants.Security.KEY_ALIAS)) {
            keyStore.getKey(AppConstants.Security.KEY_ALIAS, null) as SecretKey
        } else {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                AppConstants.Security.KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }

    private fun encrypt(data: String): String {
        val cipher = Cipher.getInstance(AppConstants.Security.ENCRYPTION_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())

        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data.toByteArray())

        // Combine IV + encrypted data and encode to Base64
        val combined = iv + encryptedData
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encryptedData: String): String? {
        return try {
            val combined = Base64.decode(encryptedData, Base64.NO_WRAP)
            val iv = combined.sliceArray(0 until AppConstants.Security.IV_LENGTH)
            val cipherData = combined.sliceArray(AppConstants.Security.IV_LENGTH until combined.size)

            val cipher = Cipher.getInstance(AppConstants.Security.ENCRYPTION_TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)

            String(cipher.doFinal(cipherData))
        } catch (e: Exception) {
            null // Return null if decryption fails
        }
    }

    // ✅ FIX: Modern secure storage with Android Keystore + DataStore
    suspend fun savePassword(serverUrl: String, username: String, password: String) {
        val key = generateKey(serverUrl, username)
        val encryptedPassword = encrypt(password)

        context.secureCredentialsDataStore.edit { preferences ->
            preferences[stringPreferencesKey(key)] = encryptedPassword
        }
    }

    suspend fun getPassword(serverUrl: String, username: String): String? {
        val key = generateKey(serverUrl, username)
        val encryptedPassword = context.secureCredentialsDataStore.data.map { preferences ->
            preferences[stringPreferencesKey(key)]
        }.first()

        return encryptedPassword?.let { decrypt(it) }
    }

    suspend fun clearPassword(serverUrl: String, username: String) {
        val key = generateKey(serverUrl, username)
        context.secureCredentialsDataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey(key))
        }
    }

    suspend fun clearAllPasswords() {
        context.secureCredentialsDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun clearCredentials() {
        clearAllPasswords()
    }

    // ✅ FIX: Improved key generation with better collision prevention
    private fun generateKey(serverUrl: String, username: String): String {
        require(serverUrl.isNotBlank()) { "Server URL cannot be blank" }
        require(username.isNotBlank()) { "Username cannot be blank" }

        // More robust sanitization and collision prevention
        val sanitizedUrl = serverUrl.trimEnd('/')
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(50) // Limit length
        val sanitizedUsername = username
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(30) // Limit length

        // Add hash suffix to prevent collisions
        val combined = "$sanitizedUrl::$sanitizedUsername"
        val hashSuffix = combined.hashCode().toString().takeLast(4)

        return "pwd_${sanitizedUrl}_${sanitizedUsername}_$hashSuffix"
    }
}
