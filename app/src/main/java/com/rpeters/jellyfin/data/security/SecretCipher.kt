package com.rpeters.jellyfin.data.security

/**
 * Encrypts short secrets for storage. Implemented by [EncryptedPreferences] (Android Keystore);
 * tests supply a fake.
 */
interface SecretCipher {
    /** @return the encrypted value, or null when encryption failed. */
    fun encryptValue(value: String): String?

    /** @return the plaintext, or null when [encryptedValue] is empty or could not be decrypted. */
    fun decryptValue(encryptedValue: String?): String?
}
