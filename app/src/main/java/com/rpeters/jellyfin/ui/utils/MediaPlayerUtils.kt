package com.rpeters.jellyfin.ui.utils

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.net.toUri
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.ui.player.VideoPlayerActivity
import org.jellyfin.sdk.model.api.BaseItemDto

@androidx.media3.common.util.UnstableApi
object MediaPlayerUtils {

    private const val WIFI_MAX_BITRATE = 8_000_000
    private const val CELLULAR_MAX_BITRATE = 4_000_000

    /**
     * Launches the internal video player with enhanced features
     */
    fun playMedia(context: Context, streamUrl: String, item: BaseItemDto) {
        try {
            if (BuildConfig.DEBUG) {
                Log.d("MediaPlayerUtils", "Launching internal video player for: ${item.name}")
            }

            val itemId = item.id?.toString() ?: ""
            val resumePosition = item.userData?.playbackPositionTicks?.div(10_000) ?: 0L

            val intent = VideoPlayerActivity.createIntent(
                context = context,
                itemId = itemId,
                itemName = item.name ?: "Unknown Title",
                startPosition = resumePosition,
            )

            context.startActivity(intent)
            if (BuildConfig.DEBUG) {
                Log.d("MediaPlayerUtils", "Successfully launched internal video player")
            }
        } catch (e: Exception) {
            Log.e("MediaPlayerUtils", "Failed to launch internal player, trying external: ${e.message}", e)

            // Fallback to external player
            playMediaExternal(context, streamUrl, item)
        }
    }

    /**
     * Launches an external media player with the given stream URL
     */
    fun playMediaExternal(context: Context, streamUrl: String, item: BaseItemDto) {
        try {
            if (BuildConfig.DEBUG) {
                Log.d("MediaPlayerUtils", "Attempting to play with external player: ${item.name}")
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(streamUrl.toUri(), "video/*")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK

                // Add extra information for media players
                putExtra("title", item.name ?: "Unknown Title")
                putExtra("artist", item.albumArtist ?: item.artists?.joinToString(", ") ?: "")
                putExtra("duration", item.runTimeTicks?.div(10_000) ?: 0L) // Convert to milliseconds
            }

            // Try to start the intent
            context.startActivity(intent)
            if (BuildConfig.DEBUG) {
                Log.d("MediaPlayerUtils", "Successfully launched external media player")
            }
        } catch (e: Exception) {
            Log.e("MediaPlayerUtils", "Failed to launch external player: ${e.message}", e)

            // Last resort fallback: try with generic intent
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, streamUrl.toUri()).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                if (BuildConfig.DEBUG) {
                    Log.d("MediaPlayerUtils", "Successfully launched fallback intent")
                }
            } catch (fallbackException: Exception) {
                Log.e("MediaPlayerUtils", "All playback methods failed: ${fallbackException.message}", fallbackException)
                throw MediaPlayerException("Unable to play media. No compatible media player found.")
            }
        }
    }

    /**
     * Launches the internal video player with a specific quality
     */
    fun playMediaWithQuality(
        context: Context,
        item: BaseItemDto,
        streamUrl: String,
        quality: String? = null,
        startPosition: Long = 0L,
    ) {
        try {
            if (BuildConfig.DEBUG) {
                Log.d("MediaPlayerUtils", "Launching video player with quality $quality for: ${item.name}")
            }

            val intent = VideoPlayerActivity.createIntent(
                context = context,
                itemId = item.id?.toString() ?: "",
                itemName = item.name ?: "Unknown Title",
                startPosition = startPosition,
            )

            context.startActivity(intent)
            if (BuildConfig.DEBUG) {
                Log.d("MediaPlayerUtils", "Successfully launched video player with quality settings")
            }
        } catch (e: Exception) {
            Log.e("MediaPlayerUtils", "Failed to launch video player with quality: ${e.message}", e)
            // Fallback to regular playback
            playMedia(context, streamUrl, item)
        }
    }

    /**
     * Checks if there's a media player available to handle the stream
     */
    fun isMediaPlayerAvailable(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType("http://example.com/test.mp4".toUri(), "video/*")
            }
            val activities = context.packageManager.queryIntentActivities(intent, 0)
            activities.isNotEmpty()
        } catch (e: Exception) {
            Log.w("MediaPlayerUtils", "Error checking for media player availability: ${e.message}")
            false
        }
    }

    /**
     * Gets the optimal stream URL based on device capabilities and network conditions
     */
    fun getOptimalStreamUrl(context: Context, baseStreamUrl: String): String {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val isCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

            val metrics = context.resources.displayMetrics
            val screenWidth = maxOf(metrics.widthPixels, metrics.heightPixels)

            val (targetWidth, targetHeight) = when {
                screenWidth >= 1920 -> 1920 to 1080
                screenWidth >= 1280 -> 1280 to 720
                else -> 854 to 480
            }

            val bitrate = if (isCellular && !isWifi) CELLULAR_MAX_BITRATE else WIFI_MAX_BITRATE

            val separator = if (baseStreamUrl.contains("?")) "&" else "?"
            buildString {
                append(baseStreamUrl)
                append(separator)
                append("MaxWidth=$targetWidth&")
                append("MaxHeight=$targetHeight&")
                append("MaxStreamingBitrate=$bitrate")
            }
        } catch (e: Exception) {
            Log.w("MediaPlayerUtils", "Failed to compute optimal stream URL: ${e.message}", e)
            baseStreamUrl
        }
    }
}

class MediaPlayerException(message: String, cause: Throwable? = null) : Exception(message, cause)
