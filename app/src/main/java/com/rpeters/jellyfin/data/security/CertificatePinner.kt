package com.rpeters.jellyfin.data.security

import android.util.Log
import com.rpeters.jellyfin.BuildConfig
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.CertificatePinner
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Manages SSL certificate pinning for Jellyfin servers.
 *
 * This provides protection against Man-in-the-Middle (MITM) attacks by
 * verifying that the server's certificate matches a known pin.
 *
 * SECURITY FEATURES:
 * - Dynamic certificate pinning (user can trust servers on first connect)
 * - SHA-256 hash-based pinning
 * - Trust-on-first-use (TOFU) model
 * - Pin revocation for compromised certificates
 *
 * USAGE:
 * 1. On first connection to a server, user is prompted to trust the certificate
 * 2. Certificate pin is stored securely
 * 3. Future connections validate against the stored pin
 * 4. If pin doesn't match, connection is rejected
 */
@Singleton
class CertificatePinningManager @Inject constructor(
    private val encryptedPreferences: EncryptedPreferences,
) {

    companion object {
        private const val TAG = "CertificatePinningManager"
        private const val PIN_PREFIX = "cert_pin_"
        private const val HASH_ALGORITHM = "SHA-256"
    }

    /**
     * Gets the stored certificate pin for a hostname.
     *
     * @param hostname The server hostname
     * @return The stored pin (SHA-256 hash), or null if not pinned
     */
    suspend fun getStoredPin(hostname: String): String? {
        val key = getPinKey(hostname)
        // Use firstOrNull() to get a single value instead of collect() which blocks forever
        return encryptedPreferences.getEncryptedString(key).firstOrNull()
    }

    /**
     * Stores a certificate pin for a hostname.
     *
     * @param hostname The server hostname
     * @param pin The SHA-256 hash of the certificate
     */
    suspend fun storePin(hostname: String, pin: String) {
        val key = getPinKey(hostname)
        encryptedPreferences.putEncryptedString(key, pin)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Stored certificate pin for $hostname")
        }
    }

    /**
     * Removes a certificate pin (e.g., if certificate is compromised or changed).
     *
     * @param hostname The server hostname
     */
    suspend fun removePin(hostname: String) {
        val key = getPinKey(hostname)
        encryptedPreferences.removeKey(key)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Removed certificate pin for $hostname")
        }
    }

    /**
     * Computes the SHA-256 hash of a certificate's public key.
     *
     * This is used for certificate pinning - we pin the public key hash,
     * not the entire certificate, as per RFC 7469.
     *
     * @param certificate The X.509 certificate
     * @return Base64-encoded SHA-256 hash of the public key
     */
    fun computeCertificatePin(certificate: Certificate): String {
        require(certificate is X509Certificate) {
            "Certificate must be X509Certificate"
        }

        // Get the SubjectPublicKeyInfo (SPKI) bytes
        val publicKeyBytes = certificate.publicKey.encoded

        // Compute SHA-256 hash
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        val hash = digest.digest(publicKeyBytes)

        // Return as Base64
        return android.util.Base64.encodeToString(
            hash,
            android.util.Base64.NO_WRAP,
        )
    }

    /**
     * Validates a certificate chain against stored pins.
     *
     * @param hostname The server hostname
     * @param certificates The certificate chain from the server
     * @throws SSLPeerUnverifiedException if pins don't match
     */
    suspend fun validatePins(hostname: String, certificates: List<Certificate>) {
        val storedPin = getStoredPin(hostname)

        // If no pin is stored, this is a new server (TOFU model)
        if (storedPin == null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "No pin stored for $hostname - first connection")
            }
            return
        }

        // Check if any certificate in the chain matches the stored pin
        val chainPins = certificates.mapNotNull { cert ->
            try {
                computeCertificatePin(cert)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to compute pin for certificate", e)
                null
            }
        }

        if (!chainPins.contains(storedPin)) {
            val message = "Certificate pin mismatch for $hostname. " +
                "Expected: $storedPin, Got: ${chainPins.joinToString()}"
            Log.e(TAG, message)
            throw SSLPeerUnverifiedException(message)
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Certificate pin validated for $hostname")
        }
    }

    /**
     * Creates an OkHttp CertificatePinner for a specific hostname.
     *
     * Note: This is for static pinning. For dynamic pinning, use the
     * SSLSocketFactory approach with validatePins().
     *
     * @param hostname The server hostname
     * @param pins List of SHA-256 pins (Base64 encoded)
     * @return CertificatePinner instance
     */
    fun createPinner(hostname: String, pins: List<String>): CertificatePinner {
        val builder = CertificatePinner.Builder()

        pins.forEach { pin ->
            builder.add(hostname, "sha256/$pin")
        }

        return builder.build()
    }

    /**
     * Extracts hostname from a Jellyfin server URL.
     *
     * @param serverUrl The full server URL
     * @return The hostname (e.g., "jellyfin.example.com")
     */
    fun extractHostname(serverUrl: String): String {
        return try {
            val url = java.net.URL(serverUrl)
            url.host
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract hostname from: $serverUrl", e)
            serverUrl
        }
    }

    private fun getPinKey(hostname: String): String {
        return "$PIN_PREFIX$hostname"
    }

    /**
     * Clears all stored certificate pins.
     * SECURITY WARNING: Only call this during app reset or if user explicitly requests it.
     */
    suspend fun clearAllPins() {
        // Note: This would require iterating through all keys
        // For now, individual pins should be removed as needed
        Log.w(TAG, "clearAllPins() called - implement if needed")
    }
}

/**
 * Certificate trust decision for Trust-on-First-Use (TOFU) model.
 */
data class CertificateTrustDecision(
    val hostname: String,
    val certificate: X509Certificate,
    val pin: String,
    val isFirstConnection: Boolean,
    val shouldTrust: Boolean = false,
)
