package com.rpeters.jellyfin.data

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class BiometricCapability(
    val authenticators: Int,
    val isAvailable: Boolean,
    val isStrongSupported: Boolean,
    val isWeakOnly: Boolean,
    val allowsDeviceCredentialFallback: Boolean,
    val status: Int,
)

/**
 * Manager for handling biometric authentication in the Jellyfin Android app.
 * Provides a simple interface for requesting biometric authentication.
 */
class BiometricAuthManager(private val context: Context) {

    private val biometricManager by lazy { BiometricManager.from(context) }

    /**
     * Checks if biometric authentication is available on the device.
     *
     * @return true if biometric authentication is available, false otherwise
     */
    fun isBiometricAuthAvailable(requireStrongBiometric: Boolean = false): Boolean {
        return getCapability(requireStrongBiometric).isAvailable
    }

    /**
     * Requests biometric authentication from the user.
     *
     * @param activity The activity to use for the biometric prompt
     * @param title The title to display in the biometric prompt
     * @param subtitle The subtitle to display in the biometric prompt
     * @param description The description to display in the biometric prompt
     * @return true if authentication was successful, false otherwise
     */
    suspend fun requestBiometricAuth(
        activity: FragmentActivity,
        title: String = "Biometric Authentication",
        subtitle: String = "Authenticate to access your credentials",
        description: String = "Confirm your identity using your biometric credential",
        requireStrongBiometric: Boolean = false,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        // Check if biometric auth is available
        val capability = getCapability(requireStrongBiometric)
        if (!capability.isAvailable) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Authentication error (e.g., user cancelled, timeout)
                    continuation.resume(false)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // Authentication succeeded
                    continuation.resume(true)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Authentication failed (e.g., incorrect fingerprint)
                    // Don't resume here as the user might try again
                }
            },
        )

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)
            .setConfirmationRequired(false) // Don't require explicit confirmation after biometric

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            promptInfoBuilder.setAllowedAuthenticators(capability.authenticators)
        } else if (capability.allowsDeviceCredentialFallback) {
            @Suppress("DEPRECATION")
            promptInfoBuilder.setDeviceCredentialAllowed(true)
        } else {
            promptInfoBuilder.setAllowedAuthenticators(capability.authenticators)
        }

        val promptInfo = promptInfoBuilder.build()

        biometricPrompt.authenticate(promptInfo)

        // Allow cancellation of the coroutine
        continuation.invokeOnCancellation {
            biometricPrompt.cancelAuthentication()
        }
    }

    /**
     * Gets the appropriate authenticators based on the Android version.
     *
     * SECURITY NOTE: This uses BIOMETRIC_WEAK on Android < 11 for compatibility.
     *
     * Security Trade-offs:
     * - BIOMETRIC_STRONG: More secure but may not work on all devices (requires
     *   liveness detection for face unlock, excludes some fingerprint sensors)
     * - BIOMETRIC_WEAK: Works on more devices but may allow face recognition
     *   without liveness detection or less secure iris recognition
     *
     * Current Implementation: Allows callers to choose between compatibility (BIOMETRIC_WEAK)
     * and maximum trust (BIOMETRIC_STRONG with device credential fallback). A warning can be
     * surfaced to users when only BIOMETRIC_WEAK is available.
     *
     * @return Bitmask of authenticators
     */
    fun getCapability(requireStrongBiometric: Boolean = false): BiometricCapability {
        val strongStatus = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        val weakStatus = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        val strongSupported = strongStatus == BiometricManager.BIOMETRIC_SUCCESS
        val weakSupported = weakStatus == BiometricManager.BIOMETRIC_SUCCESS

        val deviceCredentialAuth = deviceCredentialAuthenticator()
        val allowsDeviceCredentialFallback = deviceCredentialAuth != 0 ||
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !requireStrongBiometric)
        val authenticators = when {
            requireStrongBiometric -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or deviceCredentialAuth
                } else {
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                }
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && strongSupported -> {
                BiometricManager.Authenticators.BIOMETRIC_STRONG or deviceCredentialAuth
            }

            else -> {
                BiometricManager.Authenticators.BIOMETRIC_WEAK or deviceCredentialAuth
            }
        }
        val status = biometricManager.canAuthenticate(authenticators)

        return BiometricCapability(
            authenticators = authenticators,
            isAvailable = status == BiometricManager.BIOMETRIC_SUCCESS,
            isStrongSupported = strongSupported,
            isWeakOnly = !strongSupported && weakSupported,
            allowsDeviceCredentialFallback = allowsDeviceCredentialFallback,
            status = status,
        )
    }

    private fun deviceCredentialAuthenticator(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            0
        }
    }
}
