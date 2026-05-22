package com.rpeters.jellyfin.ui.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.coroutines.CancellationException

object TrailerUtils {

    private const val TAG = "TrailerUtils"

    /**
     * Extracts YouTube video ID from a YouTube URL.
     * Returns null if the URL is not a standard YouTube video link or is not YouTube.
     */
    fun getYouTubeVideoId(url: String): String? {
        if (url.startsWith("vnd.youtube:", ignoreCase = true)) {
            return url.substringAfter("vnd.youtube:")
        }
        
        // Pattern 1: youtu.be/VIDEO_ID
        if (url.contains("youtu.be/", ignoreCase = true)) {
            val id = url.substringAfter("youtu.be/").substringBefore("?").substringBefore("/")
            if (id.length == 11) return id
        }
        
        // Pattern 2: watch?v=VIDEO_ID
        if (url.contains("v=", ignoreCase = true)) {
            val query = url.substringAfter("v=")
            val id = query.substringBefore("&").substringBefore("/")
            if (id.length == 11) return id
        }
        
        // Pattern 3: embed/VIDEO_ID or v/VIDEO_ID
        val pathSegments = listOf("embed/", "/v/")
        for (segment in pathSegments) {
            if (url.contains(segment, ignoreCase = true)) {
                val id = url.substringAfter(segment).substringBefore("?").substringBefore("/")
                if (id.length == 11) return id
            }
        }
        
        return null
    }

    /**
     * Attempts to play the trailer.
     * 1. If it's a YouTube link, tries to open in the official YouTube app on the phone first.
     * 2. If the YouTube app is not available or if launch fails, falls back to the default browser/handler.
     */
    fun playTrailer(context: Context, url: String) {
        val videoId = getYouTubeVideoId(url)
        val isYouTube = videoId != null || url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true)

        if (isYouTube) {
            // Step 1: Try opening via vnd.youtube:$videoId if we extracted a video ID
            if (videoId != null) {
                try {
                    SecureLogger.d(TAG, "Attempting to open YouTube app via vnd.youtube scheme for video: $videoId")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    SecureLogger.w(TAG, "vnd.youtube scheme failed, trying standard URL with package fallback. Error: ${e.message}")
                }
            }

            // Step 2: Try opening the HTTP/HTTPS URL with explicit YouTube package name
            try {
                SecureLogger.d(TAG, "Attempting to open YouTube app via package name for URL: $url")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage("com.google.android.youtube")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SecureLogger.w(TAG, "YouTube app package failed or not installed. Error: ${e.message}")
            }
        }

        // Step 3: Final fallback to default browser or system handler
        try {
            SecureLogger.d(TAG, "Opening trailer in default system browser/handler: $url")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to launch default browser/handler for URL: $url. Error: ${e.message}", e)
        }
    }
}
