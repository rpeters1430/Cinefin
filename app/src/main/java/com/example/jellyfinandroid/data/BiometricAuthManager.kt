package com.example.jellyfinandroid.data

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
    fun isBiometricAuthAvailable(): Boolean {
        return when (biometricManager.canAuthenticate(getAuthenticators())) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
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
        description: String = "Confirm your identity using your biometric credential"
    ): Boolean = suspendCancellableCoroutine { continuation ->
        // Check if biometric auth is available
        if (!isBiometricAuthAvailable()) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(activity, executor,
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
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)
            .setAllowedAuthenticators(getAuthenticators())
            .setConfirmationRequired(false) // Don't require explicit confirmation after biometric
            .build()

        biometricPrompt.authenticate(promptInfo)
        
        // Allow cancellation of the coroutine
        continuation.invokeOnCancellation {
            biometricPrompt.cancelAuthentication()
        }
    }

    /**
     * Gets the appropriate authenticators based on the Android version.
     *
     * @return Bitmask of authenticators
     */
    private fun getAuthenticators(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // On Android 11+ (API 30+) use both biometric and device credential
            BiometricManager.Authenticators.BIOMETRIC_STRONG or 
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            // On older versions, use biometric or device credential
            BiometricManager.Authenticators.BIOMETRIC_WEAK or 
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
    }
}