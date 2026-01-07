package com.rpeters.jellyfin.data.security

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.cert.X509Certificate

/**
 * Unit tests for CertificatePinningManager - Certificate pin storage and validation.
 *
 * Tests cover:
 * - Certificate pin computation (SHA-256 public key hashing)
 * - Pin storage and retrieval
 * - Pin validation (TOFU flow)
 * - Pin mismatch detection
 * - Hostname extraction from URLs
 */
class CertificatePinningManagerTest {

    private lateinit var mockEncryptedPreferences: EncryptedPreferences
    private lateinit var certPinningManager: CertificatePinningManager

    @Before
    fun setup() {
        mockEncryptedPreferences = mockk(relaxed = true)
        certPinningManager = CertificatePinningManager(mockEncryptedPreferences)
    }

    @Test
    fun `computeCertificatePin produces SHA-256 hash`() {
        val mockCert: X509Certificate = mockk()
        val mockPublicKey = mockk<java.security.PublicKey>()

        // Mock certificate to return a public key with encoded bytes
        every { mockCert.publicKey } returns mockPublicKey
        every { mockPublicKey.encoded } returns ByteArray(256) { it.toByte() }

        val pin = certPinningManager.computeCertificatePin(mockCert)

        assertNotNull("Pin should not be null", pin)
        assertTrue("Pin should be a non-empty Base64 string", pin.isNotEmpty())
    }

    @Test
    fun `computeCertificatePin produces consistent hash for same certificate`() {
        val mockCert: X509Certificate = mockk()
        val mockPublicKey = mockk<java.security.PublicKey>()

        // Mock with deterministic public key bytes
        val publicKeyBytes = ByteArray(256) { 42 }
        every { mockCert.publicKey } returns mockPublicKey
        every { mockPublicKey.encoded } returns publicKeyBytes

        val pin1 = certPinningManager.computeCertificatePin(mockCert)
        val pin2 = certPinningManager.computeCertificatePin(mockCert)

        assertEquals("Same certificate should produce same pin", pin1, pin2)
    }

    @Test
    fun `computeCertificatePin produces different hash for different certificates`() {
        val mockCert1: X509Certificate = mockk()
        val mockCert2: X509Certificate = mockk()
        val mockPublicKey1 = mockk<java.security.PublicKey>()
        val mockPublicKey2 = mockk<java.security.PublicKey>()

        // Different public key bytes
        every { mockCert1.publicKey } returns mockPublicKey1
        every { mockPublicKey1.encoded } returns ByteArray(256) { 1 }

        every { mockCert2.publicKey } returns mockPublicKey2
        every { mockPublicKey2.encoded } returns ByteArray(256) { 2 }

        val pin1 = certPinningManager.computeCertificatePin(mockCert1)
        val pin2 = certPinningManager.computeCertificatePin(mockCert2)

        assertNotEquals("Different certificates should produce different pins", pin1, pin2)
    }

    @Test
    fun `storePin saves pin to encrypted preferences`() = runTest {
        val hostname = "jellyfin.example.com"
        val pin = "sha256/abc123def456"
        val savedValue = slot<String>()
        coEvery {
            mockEncryptedPreferences.putEncryptedString(
                any(),
                capture(savedValue),
            )
        } returns Unit

        certPinningManager.storePin(hostname, pin)

        coVerify(exactly = 1) {
            mockEncryptedPreferences.putEncryptedString(
                "cert_pin_$hostname",
                any(),
            )
        }
        val json = JSONObject(savedValue.captured)
        assertEquals(pin, json.getString("pin"))
    }

    @Test
    fun `getStoredPin retrieves pin from encrypted preferences`() = runTest {
        val hostname = "jellyfin.example.com"
        val expectedPin = "sha256/abc123def456"
        val json = JSONObject()
        json.put("pin", expectedPin)
        json.put("backups", JSONArray())
        json.put("firstSeen", 1L)
        json.put("lastValidated", 1L)
        json.put("expiresAt", Long.MAX_VALUE)

        coEvery {
            mockEncryptedPreferences.getEncryptedString("cert_pin_$hostname")
        } returns flowOf(json.toString())

        val actualPin = certPinningManager.getStoredPinRecord(hostname)

        assertEquals("Should retrieve stored pin", expectedPin, actualPin?.primaryPin)
    }

    @Test
    fun `getStoredPin returns null when no pin stored`() = runTest {
        val hostname = "new-server.example.com"

        coEvery {
            mockEncryptedPreferences.getEncryptedString("cert_pin_$hostname")
        } returns flowOf(null)

        val pin = certPinningManager.getStoredPinRecord(hostname)

        assertNull("Should return null for new server (no pin stored)", pin)
    }

    @Test
    fun `removePin deletes pin from encrypted preferences`() = runTest {
        val hostname = "jellyfin.example.com"

        certPinningManager.removePin(hostname)

        coVerify(exactly = 1) {
            mockEncryptedPreferences.removeKey("cert_pin_$hostname")
        }
    }

    @Test
    fun `validatePins succeeds when no pin stored - TOFU first connection`() = runTest {
        val hostname = "new-server.example.com"
        val mockCert: X509Certificate = mockk()

        coEvery {
            mockEncryptedPreferences.getEncryptedString("cert_pin_$hostname")
        } returns flowOf(null)

        // Should not throw exception for first connection
        certPinningManager.validatePins(hostname, listOf(mockCert))
    }

    @Test
    fun `validatePins succeeds when certificate matches stored pin`() = runTest {
        val hostname = "jellyfin.example.com"
        val mockCert: X509Certificate = mockk()
        val mockPublicKey = mockk<java.security.PublicKey>()

        // Set up certificate to return deterministic public key
        val publicKeyBytes = ByteArray(256) { 42 }
        every { mockCert.publicKey } returns mockPublicKey
        every { mockPublicKey.encoded } returns publicKeyBytes

        // Compute expected pin
        val expectedPin = certPinningManager.computeCertificatePin(mockCert)
        val json = JSONObject()
        json.put("pin", expectedPin)
        json.put("backups", JSONArray())
        json.put("firstSeen", 1L)
        json.put("lastValidated", 1L)
        json.put("expiresAt", Long.MAX_VALUE)

        // Mock encrypted preferences to return stored pin
        coEvery {
            mockEncryptedPreferences.getEncryptedString("cert_pin_$hostname")
        } returns flowOf(json.toString())

        // Should not throw exception when pin matches
        certPinningManager.validatePins(hostname, listOf(mockCert))
    }

    @Test(expected = PinningValidationException::class)
    fun `validatePins throws exception when certificate does not match stored pin`() = runTest {
        val hostname = "jellyfin.example.com"
        val mockCert: X509Certificate = mockk()
        val mockPublicKey = mockk<java.security.PublicKey>()

        // Set up certificate
        every { mockCert.publicKey } returns mockPublicKey
        every { mockPublicKey.encoded } returns ByteArray(256) { 1 }

        // Mock stored pin that won't match
        val differentPin = "sha256/completely_different_pin_value"
        val json = JSONObject()
        json.put("pin", differentPin)
        json.put("backups", JSONArray())
        json.put("firstSeen", 1L)
        json.put("lastValidated", 1L)
        json.put("expiresAt", Long.MAX_VALUE)
        coEvery {
            mockEncryptedPreferences.getEncryptedString("cert_pin_$hostname")
        } returns flowOf(json.toString())

        // Should throw PinningValidationException for pin mismatch
        certPinningManager.validatePins(hostname, listOf(mockCert))
    }

    @Test
    fun `validatePins checks entire certificate chain`() = runTest {
        val hostname = "jellyfin.example.com"

        // Create certificate chain (leaf, intermediate, root)
        val leafCert: X509Certificate = mockk()
        val intermediateCert: X509Certificate = mockk()
        val rootCert: X509Certificate = mockk()

        val leafKey = mockk<java.security.PublicKey>()
        val intermediateKey = mockk<java.security.PublicKey>()
        val rootKey = mockk<java.security.PublicKey>()

        every { leafCert.publicKey } returns leafKey
        every { leafKey.encoded } returns ByteArray(256) { 1 }

        every { intermediateCert.publicKey } returns intermediateKey
        every { intermediateKey.encoded } returns ByteArray(256) { 2 }

        every { rootCert.publicKey } returns rootKey
        every { rootKey.encoded } returns ByteArray(256) { 3 }

        // Pin the intermediate certificate
        val intermediatePin = certPinningManager.computeCertificatePin(intermediateCert)
        val json = JSONObject()
        json.put("pin", intermediatePin)
        json.put("backups", JSONArray())
        json.put("firstSeen", 1L)
        json.put("lastValidated", 1L)
        json.put("expiresAt", Long.MAX_VALUE)

        coEvery {
            mockEncryptedPreferences.getEncryptedString("cert_pin_$hostname")
        } returns flowOf(json.toString())

        // Should succeed because intermediate cert in chain matches pin
        certPinningManager.validatePins(hostname, listOf(leafCert, intermediateCert, rootCert))
    }

    @Test
    fun `extractHostname extracts hostname from URL`() {
        val url = "https://jellyfin.example.com:8096/web/index.html"

        val hostname = certPinningManager.extractHostname(url)

        assertEquals("Should extract hostname from URL", "jellyfin.example.com", hostname)
    }

    @Test
    fun `extractHostname handles URL without port`() {
        val url = "https://jellyfin.example.com/web/index.html"

        val hostname = certPinningManager.extractHostname(url)

        assertEquals("Should extract hostname from URL without port", "jellyfin.example.com", hostname)
    }

    @Test
    fun `extractHostname handles URL without path`() {
        val url = "https://jellyfin.example.com"

        val hostname = certPinningManager.extractHostname(url)

        assertEquals("Should extract hostname from URL without path", "jellyfin.example.com", hostname)
    }

    @Test
    fun `extractHostname handles localhost`() {
        val url = "http://localhost:8096"

        val hostname = certPinningManager.extractHostname(url)

        assertEquals("Should handle localhost", "localhost", hostname)
    }

    @Test
    fun `extractHostname handles IP address`() {
        val url = "https://192.168.1.100:8096"

        val hostname = certPinningManager.extractHostname(url)

        assertEquals("Should handle IP address", "192.168.1.100", hostname)
    }

    @Test
    fun `extractHostname returns original string for invalid URL`() {
        val invalidUrl = "not-a-valid-url"

        val hostname = certPinningManager.extractHostname(invalidUrl)

        assertEquals("Should return original string for invalid URL", invalidUrl, hostname)
    }

    @Test
    fun `createPinner creates CertificatePinner with pins`() {
        val hostname = "jellyfin.example.com"
        val pins = listOf("pin1", "pin2", "pin3")

        val pinner = certPinningManager.createPinner(hostname, pins)

        assertNotNull("Should create CertificatePinner", pinner)
    }
}
