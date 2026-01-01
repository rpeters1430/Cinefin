package com.rpeters.jellyfin.data.security

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509TrustManager

/**
 * Unit tests for PinningTrustManager - SSL certificate validation with TOFU.
 *
 * Tests cover:
 * - System certificate validation delegation
 * - Certificate pinning validation
 * - TOFU (Trust-on-First-Use) flow
 * - Hostname-aware validation via Socket/SSLEngine
 * - Error handling for null/empty chains
 */
class PinningTrustManagerTest {

    private lateinit var mockSystemTrustManager: X509TrustManager
    private lateinit var mockCertPinningManager: CertificatePinningManager
    private lateinit var pinningTrustManager: PinningTrustManager

    @Before
    fun setup() {
        mockSystemTrustManager = mockk(relaxed = true)
        mockCertPinningManager = mockk(relaxed = true)

        pinningTrustManager = PinningTrustManager(
            systemTrustManager = mockSystemTrustManager,
            certPinningManager = mockCertPinningManager,
            onFirstConnection = null, // Auto-trust TOFU
        )
    }

    @Test
    fun `checkClientTrusted delegates to system trust manager`() {
        val chain = arrayOf<X509Certificate>(mockk())
        val authType = "RSA"

        pinningTrustManager.checkClientTrusted(chain, authType)

        verify(exactly = 1) {
            mockSystemTrustManager.checkClientTrusted(chain, authType)
        }
    }

    @Test
    fun `checkClientTrusted with socket delegates to system trust manager`() {
        val chain = arrayOf<X509Certificate>(mockk())
        val authType = "RSA"
        val socket: Socket = mockk()

        pinningTrustManager.checkClientTrusted(chain, authType, socket)

        verify(exactly = 1) {
            mockSystemTrustManager.checkClientTrusted(chain, authType)
        }
    }

    @Test
    fun `checkClientTrusted with engine delegates to system trust manager`() {
        val chain = arrayOf<X509Certificate>(mockk())
        val authType = "RSA"
        val engine: SSLEngine = mockk()

        pinningTrustManager.checkClientTrusted(chain, authType, engine)

        verify(exactly = 1) {
            mockSystemTrustManager.checkClientTrusted(chain, authType)
        }
    }

    @Test(expected = CertificateException::class)
    fun `checkServerTrusted throws exception for null chain`() {
        val socket: Socket = mockk()
        every { socket.inetAddress?.hostName } returns "jellyfin.example.com"

        pinningTrustManager.checkServerTrusted(null, "RSA", socket)
    }

    @Test(expected = CertificateException::class)
    fun `checkServerTrusted throws exception for empty chain`() {
        val socket: Socket = mockk()
        every { socket.inetAddress?.hostName } returns "jellyfin.example.com"

        pinningTrustManager.checkServerTrusted(emptyArray(), "RSA", socket)
    }

    @Test
    fun `checkServerTrusted without hostname falls back to basic validation`() {
        val chain = arrayOf<X509Certificate>(mockk())
        val authType = "RSA"
        val socket: Socket = mockk()

        // Socket returns null hostname
        every { socket.inetAddress } returns null

        pinningTrustManager.checkServerTrusted(chain, authType, socket)

        // Should delegate to system trust manager's 2-parameter method
        verify(exactly = 1) {
            mockSystemTrustManager.checkServerTrusted(chain, authType)
        }
    }

    @Test
    fun `checkServerTrusted with socket extracts hostname from socket`() {
        val hostname = "jellyfin.example.com"
        val chain = arrayOf<X509Certificate>(mockk())
        val authType = "RSA"
        val socket: Socket = mockk()
        val inetAddress: InetAddress = mockk()

        every { socket.inetAddress } returns inetAddress
        every { inetAddress.hostName } returns hostname

        // Mock certificate pinning manager - first connection (no stored pin)
        coEvery { mockCertPinningManager.getStoredPin(hostname) } returns null
        coEvery { mockCertPinningManager.computeCertificatePin(any()) } returns "sha256/test_pin"

        pinningTrustManager.checkServerTrusted(chain, authType, socket)

        // Should have attempted to get stored pin for this hostname
        coVerify(exactly = 1) {
            mockCertPinningManager.getStoredPin(hostname)
        }
    }

    @Test
    fun `checkServerTrusted with engine extracts hostname from engine`() {
        val hostname = "jellyfin.example.com"
        val chain = arrayOf<X509Certificate>(mockk())
        val authType = "RSA"
        val engine: SSLEngine = mockk()

        every { engine.peerHost } returns hostname

        // Mock certificate pinning manager - first connection
        coEvery { mockCertPinningManager.getStoredPin(hostname) } returns null
        coEvery { mockCertPinningManager.computeCertificatePin(any()) } returns "sha256/test_pin"

        pinningTrustManager.checkServerTrusted(chain, authType, engine)

        // Should have attempted to get stored pin for this hostname
        coVerify(exactly = 1) {
            mockCertPinningManager.getStoredPin(hostname)
        }
    }

    @Test
    fun `first connection stores certificate pin - TOFU`() {
        val hostname = "new-server.example.com"
        val mockCert: X509Certificate = mockk()
        val mockPublicKey = mockk<java.security.PublicKey>()
        val chain = arrayOf(mockCert)
        val authType = "RSA"
        val socket: Socket = mockk()
        val inetAddress: InetAddress = mockk()

        every { socket.inetAddress } returns inetAddress
        every { inetAddress.hostName } returns hostname
        every { mockCert.publicKey } returns mockPublicKey
        every { mockPublicKey.encoded } returns ByteArray(256) { 42 }

        // First connection - no stored pin
        coEvery { mockCertPinningManager.getStoredPin(hostname) } returns null
        coEvery { mockCertPinningManager.computeCertificatePin(mockCert) } returns "sha256/new_pin"

        pinningTrustManager.checkServerTrusted(chain, authType, socket)

        // Should store the pin for first connection
        coVerify(exactly = 1) {
            mockCertPinningManager.storePin(hostname, "sha256/new_pin")
        }
    }

    @Test
    fun `subsequent connection validates against stored pin`() {
        val hostname = "known-server.example.com"
        val mockCert: X509Certificate = mockk()
        val mockPublicKey = mockk<java.security.PublicKey>()
        val chain = arrayOf(mockCert)
        val authType = "RSA"
        val socket: Socket = mockk()
        val inetAddress: InetAddress = mockk()

        every { socket.inetAddress } returns inetAddress
        every { inetAddress.hostName } returns hostname
        every { mockCert.publicKey } returns mockPublicKey
        every { mockPublicKey.encoded } returns ByteArray(256) { 42 }

        val storedPin = "sha256/stored_pin"

        // Subsequent connection - pin stored
        coEvery { mockCertPinningManager.getStoredPin(hostname) } returns storedPin
        coEvery { mockCertPinningManager.computeCertificatePin(mockCert) } returns storedPin

        pinningTrustManager.checkServerTrusted(chain, authType, socket)

        // Should NOT store pin again (already stored)
        coVerify(exactly = 0) {
            mockCertPinningManager.storePin(any(), any())
        }
    }

    @Test(expected = CertificateException::class)
    fun `pin mismatch throws exception - potential MITM attack`() {
        val hostname = "known-server.example.com"
        val mockCert: X509Certificate = mockk()
        val mockPublicKey = mockk<java.security.PublicKey>()
        val chain = arrayOf(mockCert)
        val authType = "RSA"
        val socket: Socket = mockk()
        val inetAddress: InetAddress = mockk()

        every { socket.inetAddress } returns inetAddress
        every { inetAddress.hostName } returns hostname
        every { mockCert.publicKey } returns mockPublicKey
        every { mockPublicKey.encoded } returns ByteArray(256) { 1 }

        val storedPin = "sha256/original_pin"
        val actualPin = "sha256/different_pin"

        coEvery { mockCertPinningManager.getStoredPin(hostname) } returns storedPin
        coEvery { mockCertPinningManager.computeCertificatePin(mockCert) } returns actualPin

        // Should throw CertificateException for pin mismatch
        pinningTrustManager.checkServerTrusted(chain, authType, socket)
    }

    @Test
    fun `onFirstConnection callback called for first connection when provided`() {
        var callbackInvoked = false
        var callbackHostname: String? = null

        val pinningTrustManagerWithCallback = PinningTrustManager(
            systemTrustManager = mockSystemTrustManager,
            certPinningManager = mockCertPinningManager,
            onFirstConnection = { hostname, cert ->
                callbackInvoked = true
                callbackHostname = hostname
                true // Accept certificate
            },
        )

        val hostname = "callback-test.example.com"
        val mockCert: X509Certificate = mockk()
        val mockPublicKey = mockk<java.security.PublicKey>()
        val chain = arrayOf(mockCert)
        val authType = "RSA"
        val socket: Socket = mockk()
        val inetAddress: InetAddress = mockk()

        every { socket.inetAddress } returns inetAddress
        every { inetAddress.hostName } returns hostname
        every { mockCert.publicKey } returns mockPublicKey
        every { mockPublicKey.encoded } returns ByteArray(256) { 42 }

        coEvery { mockCertPinningManager.getStoredPin(hostname) } returns null
        coEvery { mockCertPinningManager.computeCertificatePin(mockCert) } returns "sha256/pin"

        pinningTrustManagerWithCallback.checkServerTrusted(chain, authType, socket)

        // Verify callback was invoked
        if (!callbackInvoked) {
            fail("onFirstConnection callback should have been invoked")
        }
        assertNotNull("Callback should receive hostname", callbackHostname)
    }

    @Test(expected = CertificateException::class)
    fun `onFirstConnection callback rejection throws exception`() {
        val pinningTrustManagerWithCallback = PinningTrustManager(
            systemTrustManager = mockSystemTrustManager,
            certPinningManager = mockCertPinningManager,
            onFirstConnection = { _, _ ->
                false // Reject certificate
            },
        )

        val hostname = "rejected.example.com"
        val mockCert: X509Certificate = mockk()
        val mockPublicKey = mockk<java.security.PublicKey>()
        val chain = arrayOf(mockCert)
        val authType = "RSA"
        val socket: Socket = mockk()
        val inetAddress: InetAddress = mockk()

        every { socket.inetAddress } returns inetAddress
        every { inetAddress.hostName } returns hostname
        every { mockCert.publicKey } returns mockPublicKey
        every { mockPublicKey.encoded } returns ByteArray(256) { 42 }

        coEvery { mockCertPinningManager.getStoredPin(hostname) } returns null

        // Should throw CertificateException when callback rejects
        pinningTrustManagerWithCallback.checkServerTrusted(chain, authType, socket)
    }

    @Test
    fun `getAcceptedIssuers delegates to system trust manager`() {
        val expectedIssuers = arrayOf<X509Certificate>(mockk(), mockk())
        every { mockSystemTrustManager.acceptedIssuers } returns expectedIssuers

        val actualIssuers = pinningTrustManager.acceptedIssuers

        assertArrayEquals(
            "Should return system trust manager's accepted issuers",
            expectedIssuers,
            actualIssuers,
        )
    }

    @Test(expected = CertificateException::class)
    fun `system certificate validation failure throws exception before pinning check`() {
        val hostname = "invalid-cert.example.com"
        val chain = arrayOf<X509Certificate>(mockk())
        val authType = "RSA"
        val socket: Socket = mockk()
        val inetAddress: InetAddress = mockk()

        every { socket.inetAddress } returns inetAddress
        every { inetAddress.hostName } returns hostname

        // Mock system trust manager to throw exception (invalid certificate)
        every {
            mockSystemTrustManager.checkServerTrusted(chain, authType)
        } throws CertificateException("Certificate not trusted")

        // Should throw exception from system validation, never reach pinning check
        pinningTrustManager.checkServerTrusted(chain, authType, socket)

        // Verify pinning manager was never called (system validation failed first)
        coVerify(exactly = 0) {
            mockCertPinningManager.getStoredPin(any())
        }
    }
}
