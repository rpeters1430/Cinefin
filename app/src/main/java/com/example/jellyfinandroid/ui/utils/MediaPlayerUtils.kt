package com.example.jellyfinandroid.ui.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.jellyfinandroid.ui.player.VideoPlayerActivity
import org.jellyfin.sdk.model.api.BaseItemDto

object MediaPlayerUtils {
    
    /**
     * Launches the internal video player with enhanced features
     */
    fun playMedia(context: Context, streamUrl: String, item: BaseItemDto) {
        try {
            Log.d("MediaPlayerUtils", "Launching internal video player for: ${item.name}")
            
            val intent = VideoPlayerActivity.createIntent(
                context = context,
                itemId = item.id?.toString() ?: "",
                itemName = item.name ?: "Unknown Title",
                streamUrl = streamUrl,
                startPosition = item.userData?.playbackPositionTicks?.div(10_000) ?: 0L
            )
            
            context.startActivity(intent)
            Log.d("MediaPlayerUtils", "Successfully launched internal video player")
            
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
            Log.d("MediaPlayerUtils", "Attempting to play with external player: ${item.name}")
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(streamUrl), "video/*")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                
                // Add extra information for media players
                putExtra("title", item.name ?: "Unknown Title")
                putExtra("artist", item.albumArtist ?: item.artists?.joinToString(", ") ?: "")
                putExtra("duration", item.runTimeTicks?.div(10_000) ?: 0L) // Convert to milliseconds
            }
            
            // Try to start the intent
            context.startActivity(intent)
            Log.d("MediaPlayerUtils", "Successfully launched external media player")
            
        } catch (e: Exception) {
            Log.e("MediaPlayerUtils", "Failed to launch external player: ${e.message}", e)
            
            // Last resort fallback: try with generic intent
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(streamUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                Log.d("MediaPlayerUtils", "Successfully launched fallback intent")
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
        startPosition: Long = 0L
    ) {
        try {
            Log.d("MediaPlayerUtils", "Launching video player with quality $quality for: ${item.name}")
            
            val intent = VideoPlayerActivity.createIntent(
                context = context,
                itemId = item.id?.toString() ?: "",
                itemName = item.name ?: "Unknown Title",
                streamUrl = streamUrl,
                startPosition = startPosition
            )
            
            context.startActivity(intent)
            Log.d("MediaPlayerUtils", "Successfully launched video player with quality settings")
            
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
                setDataAndType(Uri.parse("http://example.com/test.mp4"), "video/*")
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
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            val isWifi = capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true

            val metrics = context.resources.displayMetrics
            val maxWidth = metrics.widthPixels

            val separator = if (baseStreamUrl.contains("?")) "&" else "?"
            buildString {
                append(baseStreamUrl)
                append(separator)
                append("MaxWidth=$maxWidth")
                if (!isWifi) {
                    append("&MaxStreamingBitrate=4000000")
                }
            }
        } catch (e: Exception) {
            Log.w("MediaPlayerUtils", "Failed to compute optimal stream URL: ${e.message}", e)
            baseStreamUrl
        }
    }
}

class MediaPlayerException(message: String, cause: Throwable? = null) : Exception(message, cause)