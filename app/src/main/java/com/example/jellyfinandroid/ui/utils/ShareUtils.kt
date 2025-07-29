package com.example.jellyfinandroid.ui.utils

import com.example.jellyfinandroid.BuildConfig
import android.content.Context
import android.content.Intent
import android.util.Log
import org.jellyfin.sdk.model.api.BaseItemDto

object ShareUtils {
    
    /**
     * Creates a share intent for a media item
     */
    fun shareMedia(context: Context, item: BaseItemDto) {
        try {
            val shareText = buildShareText(item)
            
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_SUBJECT, "Check out: ${item.name}")
            }
            
            val chooser = Intent.createChooser(shareIntent, "Share ${item.name}")
            chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(chooser)
            
            if (BuildConfig.DEBUG) {
            
                Log.d("ShareUtils", "Successfully shared: ${item.name}")
            
            }
            
        } catch (e: Exception) {
            Log.e("ShareUtils", "Failed to share media: ${e.message}", e)
        }
    }
    
    private fun buildShareText(item: BaseItemDto): String {
        val title = item.name ?: "Unknown Title"
        val year = item.productionYear?.let { " ($it)" } ?: ""
        val overview = item.overview?.let { "\n\n$it" } ?: ""
        val genres = item.genres?.takeIf { it.isNotEmpty() }?.let { 
            "\n\nGenres: ${it.joinToString(", ")}" 
        } ?: ""
        
        val rating = item.communityRating?.let { 
            "\nRating: ${String.format(java.util.Locale.ROOT, "%.1f", it)}/10" 
        } ?: ""
        
        val runtime = item.runTimeTicks?.let { ticks ->
            val minutes = (ticks / 10_000_000 / 60).toInt()
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            val runtimeText = if (hours > 0) "${hours}h ${remainingMinutes}m" else "${minutes}m"
            "\nRuntime: $runtimeText"
        } ?: ""
        
        return "Check out this ${item.type?.toString()?.lowercase() ?: "media"}: $title$year$rating$runtime$genres$overview"
    }
}