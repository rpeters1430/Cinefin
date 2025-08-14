package com.example.jellyfinandroid.ui.utils

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.jellyfinandroid.BuildConfig
import org.jellyfin.sdk.model.api.BaseItemDto
import java.io.File

/**
 * Utility class for handling media downloads from Jellyfin server.
 *
 * Manages downloading media files to local storage with proper file organization,
 * progress tracking, and storage management.
 */
object MediaDownloadManager {

    private const val TAG = "MediaDownloadManager"
    private const val DOWNLOADS_FOLDER = "JellyfinAndroid"

    /**
     * Downloads a media item to local storage using Android's DownloadManager.
     *
     * @param context The application context
     * @param item The Jellyfin media item to download
     * @param streamUrl The stream URL for the media item
     * @return The download ID for tracking, or null if download failed to start
     */
    fun downloadMedia(context: Context, item: BaseItemDto, streamUrl: String): Long? {
        return try {
            if (!hasStoragePermission(context, item)) {
                Log.w(TAG, "Missing storage permission for ${item.name}")
                return null
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            // Create download request
            val request = DownloadManager.Request(streamUrl.toUri()).apply {
                setTitle("${item.name ?: "Unknown"}")
                setDescription("Downloading from Jellyfin")

                // Set destination in Downloads directory with organized folder structure
                val fileName = generateFileName(item)
                val subPath = generateSubPath(item)
                val subFolder = "$DOWNLOADS_FOLDER/$subPath/$fileName"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, subFolder)
                } else {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, subFolder)
                }

                // Allow download only over WiFi by default for large media files
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)

                // Show download notification
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                // Allow media scanner to find the file
                setVisibleInDownloadsUi(true)

                // Set MIME type based on media type
                val mimeType = getMimeType(item)
                if (mimeType != null) {
                    setMimeType(mimeType)
                }
            }

            // Enqueue the download
            val downloadId = downloadManager.enqueue(request)

            if (BuildConfig.DEBUG) {
                Log.i(TAG, "Started download for ${item.name} with ID: $downloadId")
            }
            downloadId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start download for ${item.name}: ${e.message}", e)
            null
        }
    }

    /**
     * Checks if a media item is already downloaded.
     *
     * @param context The application context
     * @param item The media item to check
     * @return True if the item is already downloaded
     */
    fun isDownloaded(context: Context, item: BaseItemDto): Boolean {
        return try {
            if (!hasStoragePermission(context, item)) {
                Log.w(TAG, "Missing storage permission for ${item.name}")
                return false
            }
            val fileName = generateFileName(item)
            val subPath = generateSubPath(item)
            val file = File(
                getDownloadBaseDir(context),
                "$DOWNLOADS_FOLDER/$subPath/$fileName",
            )

            file.exists() && file.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking download status for ${item.name}: ${e.message}", e)
            false
        }
    }

    /**
     * Gets the local file path for a downloaded media item.
     *
     * @param context The application context
     * @param item The media item
     * @return The local file path, or null if not downloaded
     */
    fun getLocalFilePath(context: Context, item: BaseItemDto): String? {
        return try {
            if (!hasStoragePermission(context, item)) {
                Log.w(TAG, "Missing storage permission for ${item.name}")
                return null
            }
            val fileName = generateFileName(item)
            val subPath = generateSubPath(item)
            val file = File(
                getDownloadBaseDir(context),
                "$DOWNLOADS_FOLDER/$subPath/$fileName",
            )

            if (file.exists() && file.length() > 0) {
                file.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local file path for ${item.name}: ${e.message}", e)
            null
        }
    }

    /**
     * Deletes a downloaded media file.
     *
     * @param context The application context
     * @param item The media item to delete
     * @return True if the file was successfully deleted
     */
    fun deleteDownload(context: Context, item: BaseItemDto): Boolean {
        return try {
            if (!hasStoragePermission(context, item)) {
                Log.w(TAG, "Missing storage permission for ${item.name}")
                return false
            }
            val fileName = generateFileName(item)
            val subPath = generateSubPath(item)
            val file = File(
                getDownloadBaseDir(context),
                "$DOWNLOADS_FOLDER/$subPath/$fileName",
            )

            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    if (BuildConfig.DEBUG) {
                        Log.i(TAG, "Deleted download for ${item.name}")
                    }
                } else {
                    Log.w(TAG, "Failed to delete download for ${item.name}")
                }
                deleted
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Download file does not exist for ${item.name}")
                }
                true // Consider it deleted if it doesn't exist
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting download for ${item.name}: ${e.message}", e)
            false
        }
    }

    /**
     * Gets the download status for a given download ID.
     *
     * @param context The application context
     * @param downloadId The download ID
     * @return A DownloadStatus object with progress and status information
     */
    fun getDownloadStatus(context: Context, downloadId: Long): DownloadStatus? {
        return try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))

            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                val totalSizeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val downloadedSizeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)

                val status = cursor.getInt(statusIndex)
                val reason = cursor.getInt(reasonIndex)
                val totalSize = cursor.getLong(totalSizeIndex)
                val downloadedSize = cursor.getLong(downloadedSizeIndex)

                cursor.close()

                DownloadStatus(
                    status = status,
                    reason = reason,
                    totalSize = totalSize,
                    downloadedSize = downloadedSize,
                    progress = if (totalSize > 0) (downloadedSize.toFloat() / totalSize.toFloat()) else 0f,
                )
            } else {
                cursor.close()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting download status for ID $downloadId: ${e.message}", e)
            null
        }
    }

    /**
     * Calculates total storage used by downloads.
     *
     * @param context The application context
     * @return The total size in bytes used by downloads
     */
    fun getTotalDownloadSize(context: Context): Long {
        return try {
            if (!hasLegacyStoragePermission(context)) {
                Log.w(TAG, "Missing storage permission for downloads directory")
                return 0L
            }
            val downloadsDir = File(
                getDownloadBaseDir(context),
                DOWNLOADS_FOLDER,
            )

            if (downloadsDir.exists() && downloadsDir.isDirectory) {
                calculateDirectorySize(downloadsDir)
            } else {
                0L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating total download size: ${e.message}", e)
            0L
        }
    }

    /**
     * Clears all downloads to free up storage space.
     *
     * @param context The application context
     * @return True if all downloads were successfully cleared
     */
    fun clearAllDownloads(context: Context): Boolean {
        return try {
            if (!hasLegacyStoragePermission(context)) {
                Log.w(TAG, "Missing storage permission for downloads directory")
                return false
            }
            val downloadsDir = File(
                getDownloadBaseDir(context),
                DOWNLOADS_FOLDER,
            )

            if (downloadsDir.exists() && downloadsDir.isDirectory) {
                downloadsDir.deleteRecursively()
            } else {
                true // Consider it cleared if directory doesn't exist
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all downloads: ${e.message}", e)
            false
        }
    }

    // Helper methods

    internal fun getDownloadBaseDir(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): File {
        return if (sdkInt >= Build.VERSION_CODES.Q) {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }
    }

    private fun hasStoragePermission(context: Context, item: BaseItemDto): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val permission = when (item.type?.name) {
                    "AUDIO" -> Manifest.permission.READ_MEDIA_AUDIO
                val permission = when (item.type) {
                    BaseItemKind.AUDIO -> Manifest.permission.READ_MEDIA_AUDIO
                    BaseItemKind.MOVIE, BaseItemKind.EPISODE, BaseItemKind.MUSIC_VIDEO -> Manifest.permission.READ_MEDIA_VIDEO
                    else -> Manifest.permission.READ_MEDIA_IMAGES
                }
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    private fun hasLegacyStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun generateFileName(item: BaseItemDto): String {
        val baseName = (item.name ?: "Unknown").replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val extension = getFileExtension(item)
        return "$baseName.$extension"
    }

    private fun generateSubPath(item: BaseItemDto): String {
        return when (item.type?.name) {
            "MOVIE" -> "Movies"
            "EPISODE" -> "TV Shows/${item.seriesName ?: "Unknown Series"}"
            "AUDIO" -> "Music/${item.albumArtist ?: "Unknown Artist"}"
            "MUSIC_VIDEO" -> "Music Videos"
            else -> "Other"
        }.replace(Regex("[^a-zA-Z0-9._/ -]"), "_")
    }

    private fun getFileExtension(item: BaseItemDto): String {
        // Default extensions based on media type
        return when (item.type?.name) {
            "MOVIE", "EPISODE" -> "mp4"
            "AUDIO" -> "mp3"
            "MUSIC_VIDEO" -> "mp4"
            else -> "bin"
        }
    }

    private fun getMimeType(item: BaseItemDto): String? {
        return when (item.type?.name) {
            "MOVIE", "EPISODE", "MUSIC_VIDEO" -> "video/mp4"
            "AUDIO" -> "audio/mpeg"
            else -> null
        }
    }

    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        if (directory.isDirectory) {
            directory.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    calculateDirectorySize(file)
                } else {
                    file.length()
                }
            }
        } else {
            size = directory.length()
        }
        return size
    }
}

/**
 * Data class representing the status of a download.
 */
data class DownloadStatus(
    val status: Int,
    val reason: Int,
    val totalSize: Long,
    val downloadedSize: Long,
    val progress: Float,
) {
    val isCompleted: Boolean
        get() = status == DownloadManager.STATUS_SUCCESSFUL

    val isFailed: Boolean
        get() = status == DownloadManager.STATUS_FAILED

    val isRunning: Boolean
        get() = status == DownloadManager.STATUS_RUNNING

    val isPaused: Boolean
        get() = status == DownloadManager.STATUS_PAUSED

    val isPending: Boolean
        get() = status == DownloadManager.STATUS_PENDING
}
