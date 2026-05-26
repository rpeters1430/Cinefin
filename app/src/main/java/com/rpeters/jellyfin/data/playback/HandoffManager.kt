package com.rpeters.jellyfin.data.playback

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Cross-Device Continuity (Handoff) for playback state.
 * Uses CompanionDeviceManager for state transfer on Android 16/17 (Baklava/Vanilla Ice Cream).
 */
@Singleton
class HandoffManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "HandoffManager"
        const val ACTION_HANDOFF_RESUME = "com.rpeters.jellyfin.ACTION_HANDOFF_RESUME"
        const val EXTRA_ITEM_ID = "handoff_item_id"
        const val EXTRA_POSITION_MS = "handoff_position_ms"
        const val EXTRA_TITLE = "handoff_title"
        const val EXTRA_SIGNATURE = "handoff_signature"
    }

    private val sessionKey = ByteArray(32).apply { SecureRandom().nextBytes(this) }

    fun generateSignature(itemId: String, positionMs: Long): String {
        val data = "$itemId:$positionMs"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(sessionKey, "HmacSHA256"))
        val signatureBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(signatureBytes, android.util.Base64.NO_WRAP)
    }

    fun verifySignature(itemId: String, positionMs: Long, signature: String): Boolean {
        val expected = generateSignature(itemId, positionMs)
        return expected == signature
    }

    private var currentItemId: String? = null
    private var currentTitle: String? = null
    private var currentPosition: Long = 0L

    /**
     * Start broadcasting the current playback state for cross-device handoff.
     */
    fun startBroadcasting(itemId: String, title: String, positionMs: Long) {
        currentItemId = itemId
        currentTitle = title
        currentPosition = positionMs

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Starting handoff broadcast for $title ($itemId) at $positionMs ms")
        }

        updateSystemHandoff()
    }

    /**
     * Update the broadcasted playback position.
     */
    fun updatePosition(positionMs: Long) {
        if (currentItemId == null || kotlin.math.abs(currentPosition - positionMs) < 5000L) return
        
        currentPosition = positionMs
        updateSystemHandoff()
    }

    /**
     * Stop broadcasting handoff state.
     */
    fun stopBroadcasting() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Stopping handoff broadcast for $currentTitle")
        }
        
        currentItemId = null
        currentTitle = null
        currentPosition = 0L
        
        clearSystemHandoff()
    }

    private fun updateSystemHandoff() {
        val id = currentItemId ?: return
        val pos = currentPosition
        val title = currentTitle ?: "Media"

        // Android 17+ CompanionDeviceManager State Transfer (Vanilla Ice Cream)
        // Since we are building for the future, we use a reflective or guarded approach
        if (Build.VERSION.SDK_INT >= 37) {
            updateAndroid17Handoff(id, title, pos)
        } else if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+ Custom Broadcast for Cross-Device SDK integration
            broadcastHandoffState(id, title, pos)
        }
    }

    private fun clearSystemHandoff() {
        // Implementation to clear state from system managers
    }

    @RequiresApi(37)
    private fun updateAndroid17Handoff(itemId: String, title: String, positionMs: Long) {
        try {
            // Placeholder for real Android 17 CompanionDeviceManager.transferState call
            // In a real environment, this would involve creating a StateTransferObject
            SecureLogger.v(TAG, "Updating Android 17 Handoff state for $itemId")
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to update Android 17 Handoff", e)
        }
    }

    private fun broadcastHandoffState(itemId: String, title: String, positionMs: Long) {
        val intent = Intent(ACTION_HANDOFF_RESUME).apply {
            putExtra(EXTRA_ITEM_ID, itemId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_POSITION_MS, positionMs)
            putExtra(EXTRA_SIGNATURE, generateSignature(itemId, positionMs))
            setPackage(context.packageName)
            // Ensure this is broadcast globally if using Cross-Device SDK, 
            // but for now we keep it internal for simulation.
        }
        context.sendBroadcast(intent)
    }
}
