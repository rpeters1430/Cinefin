package com.example.jellyfinandroid.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureCredentialManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedSharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun savePassword(serverUrl: String, username: String, password: String) {
        val key = generateKey(serverUrl, username)
        encryptedSharedPreferences.edit().putString(key, password).apply()
    }

    fun getPassword(serverUrl: String, username: String): String? {
        val key = generateKey(serverUrl, username)
        return encryptedSharedPreferences.getString(key, null)
    }

    fun clearPassword(serverUrl: String, username: String) {
        val key = generateKey(serverUrl, username)
        encryptedSharedPreferences.edit().remove(key).apply()
    }

    fun clearAllPasswords() {
        encryptedSharedPreferences.edit().clear().apply()
    }
    
    fun clearCredentials() {
        clearAllPasswords()
    }

    private fun generateKey(serverUrl: String, username: String): String {
        return "${serverUrl.trimEnd('/')}_$username"
    }
} 