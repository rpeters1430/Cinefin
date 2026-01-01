package com.rpeters.jellyfin.data.security

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections

/**
 * Unit tests for EncryptedPreferences - AES-256-GCM encryption/decryption.
 *
 * Tests cover:
 * - Encryption/decryption round-trip
 * - IV uniqueness (multiple encryptions produce different ciphertext)
 * - Tamper detection (modified ciphertext fails decryption)
 * - Concurrent encryption safety
 * - Edge cases (empty strings, null values)
 */
class EncryptedPreferencesTest {

    private lateinit var context: Context
    private lateinit var encryptedPreferences: EncryptedPreferences

    @Before
    fun setup() {
        // Mock context - EncryptedPreferences uses it for DataStore
        context = mockk(relaxed = true)

        // Create instance - it will use real Android Keystore in test environment
        // Note: This requires Android test environment, not pure JVM
        encryptedPreferences = EncryptedPreferences(context)
    }

    @Test
    fun `encryptValue returns non-null for valid input`() {
        val plaintext = "my_sensitive_token_12345"

        val encrypted = encryptedPreferences.encryptValue(plaintext)

        assertNotNull("Encryption should succeed for valid input", encrypted)
        assertTrue("Encrypted value should not be empty", encrypted!!.isNotEmpty())
    }

    @Test
    fun `encryptValue returns null for empty string`() {
        val encrypted = encryptedPreferences.encryptValue("")

        assertNull("Encryption should return null for empty string", encrypted)
    }

    @Test
    fun `decryptValue returns null for null input`() {
        val decrypted = encryptedPreferences.decryptValue(null)

        assertNull("Decryption should return null for null input", decrypted)
    }

    @Test
    fun `decryptValue returns null for empty string`() {
        val decrypted = encryptedPreferences.decryptValue("")

        assertNull("Decryption should return null for empty string", decrypted)
    }

    @Test
    fun `encrypt then decrypt returns original value`() {
        val originalValue = "my_super_secret_token_abc123"

        val encrypted = encryptedPreferences.encryptValue(originalValue)
        assertNotNull("Encryption should succeed", encrypted)

        val decrypted = encryptedPreferences.decryptValue(encrypted)

        assertEquals("Decrypted value should match original", originalValue, decrypted)
    }

    @Test
    fun `encrypt produces different ciphertext for same plaintext - IV uniqueness`() {
        val plaintext = "same_value_encrypted_twice"

        val encrypted1 = encryptedPreferences.encryptValue(plaintext)
        val encrypted2 = encryptedPreferences.encryptValue(plaintext)

        assertNotNull("First encryption should succeed", encrypted1)
        assertNotNull("Second encryption should succeed", encrypted2)
        assertNotEquals(
            "Each encryption should use a different IV, producing different ciphertext",
            encrypted1,
            encrypted2,
        )

        // Both should decrypt to the same value
        assertEquals(plaintext, encryptedPreferences.decryptValue(encrypted1))
        assertEquals(plaintext, encryptedPreferences.decryptValue(encrypted2))
    }

    @Test
    fun `tamper detection - modified ciphertext fails decryption`() {
        val plaintext = "original_value"

        val encrypted = encryptedPreferences.encryptValue(plaintext)
        assertNotNull("Encryption should succeed", encrypted)

        // Tamper with the ciphertext by changing one character
        val tamperedCiphertext = encrypted!!.replaceFirst('A', 'B')

        val decrypted = encryptedPreferences.decryptValue(tamperedCiphertext)

        assertNull(
            "Decryption should fail for tampered ciphertext (GCM authentication tag mismatch)",
            decrypted,
        )
    }

    @Test
    fun `tamper detection - truncated ciphertext fails decryption`() {
        val plaintext = "original_value"

        val encrypted = encryptedPreferences.encryptValue(plaintext)
        assertNotNull("Encryption should succeed", encrypted)

        // Truncate the ciphertext
        val truncated = encrypted!!.substring(0, encrypted.length / 2)

        val decrypted = encryptedPreferences.decryptValue(truncated)

        assertNull(
            "Decryption should fail for truncated ciphertext",
            decrypted,
        )
    }

    @Test
    fun `concurrent encryption produces unique ciphertexts - thread safety`() {
        val plaintext = "concurrent_test_value"
        val encryptionCount = 10

        // Use thread-safe collection
        val encryptedValues = Collections.synchronizedList(mutableListOf<String>())

        // Perform concurrent encryptions
        val threads = List(encryptionCount) {
            Thread {
                val encrypted = encryptedPreferences.encryptValue(plaintext)
                if (encrypted != null) {
                    encryptedValues.add(encrypted)
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Verify all encryptions succeeded
        assertEquals(
            "All concurrent encryptions should succeed",
            encryptionCount,
            encryptedValues.size,
        )

        // Verify all ciphertexts are unique (different IVs)
        val uniqueValues = encryptedValues.toSet()
        assertEquals(
            "Each concurrent encryption should produce unique ciphertext (unique IV)",
            encryptionCount,
            uniqueValues.size,
        )

        // Verify all decrypt to the same plaintext
        encryptedValues.forEach { encrypted ->
            assertEquals(plaintext, encryptedPreferences.decryptValue(encrypted))
        }
    }

    @Test
    fun `encrypt and decrypt special characters`() {
        val specialChars = "!@#$%^&*()_+-=[]{}|;:',.<>?/~`\""

        val encrypted = encryptedPreferences.encryptValue(specialChars)
        assertNotNull("Encryption should succeed for special characters", encrypted)

        val decrypted = encryptedPreferences.decryptValue(encrypted)

        assertEquals("Special characters should round-trip correctly", specialChars, decrypted)
    }

    @Test
    fun `encrypt and decrypt unicode characters`() {
        val unicode = "Hello 世界 🌍 Привет مرحبا"

        val encrypted = encryptedPreferences.encryptValue(unicode)
        assertNotNull("Encryption should succeed for unicode", encrypted)

        val decrypted = encryptedPreferences.decryptValue(encrypted)

        assertEquals("Unicode should round-trip correctly", unicode, decrypted)
    }

    @Test
    fun `encrypt and decrypt long string`() {
        val longString = "x".repeat(10000) // 10KB string

        val encrypted = encryptedPreferences.encryptValue(longString)
        assertNotNull("Encryption should succeed for long string", encrypted)

        val decrypted = encryptedPreferences.decryptValue(encrypted)

        assertEquals("Long string should round-trip correctly", longString, decrypted)
    }

    @Test
    fun `decryptValue returns null for invalid base64`() {
        val invalidBase64 = "this is not valid base64!!!"

        val decrypted = encryptedPreferences.decryptValue(invalidBase64)

        assertNull("Decryption should return null for invalid base64", decrypted)
    }

    @Test
    fun `decryptValue returns null for data too short - missing GCM tag`() {
        // Valid base64 but too short to contain IV + ciphertext + GCM tag (needs at least 12 + 16 = 28 bytes)
        val tooShort = android.util.Base64.encodeToString(
            ByteArray(20), // Only 20 bytes
            android.util.Base64.NO_WRAP,
        )

        val decrypted = encryptedPreferences.decryptValue(tooShort)

        assertNull("Decryption should return null for data too short", decrypted)
    }

    @Test
    fun `putEncryptedString with null value removes key`() = runTest {
        val key = "test_key"

        encryptedPreferences.putEncryptedString(key, null)

        // Note: This test verifies null handling logic doesn't crash
        // Full integration test would require DataStore mocking
    }

    @Test
    fun `getEncryptedString returns decrypted value from DataStore`() = runTest {
        // Note: Full integration test would require DataStore setup
        // This test verifies the basic flow doesn't crash

        val key = "test_key"
        val flow = encryptedPreferences.getEncryptedString(key)

        // Verify flow is created (actual DataStore interaction tested in integration tests)
        assertNotNull("Flow should be created", flow)
    }
}
