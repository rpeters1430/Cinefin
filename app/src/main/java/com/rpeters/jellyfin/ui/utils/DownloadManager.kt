package com.rpeters.jellyfin.ui.utils

import android.Manifest
import android.app.DownloadManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.storage.MediaStoreSaver
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.io.IOException

/**
 * Utility class for handling media downloads from Jellyfin server.
 *
 * Manages downloading media files to local storage with proper file organization,
 * progress tracking, and storage management.
 */
object MediaDownloadManager {

    private const val TAG = "MediaDownloadManager"
    private const val DOWNLOADS_FOLDER = "JellyfinAndroid"
    private const val DEFAULT_MIME_TYPE = "application/octet-stream"

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
                val mimeType = getMimeType(item) ?: DEFAULT_MIME_TYPE
                queryDownloadEntry(context, fileName, subPath)?.uri?.let { existingUri ->
                    context.contentResolver.delete(existingUri, null, null)
                }
                val destinationUri = createDownloadUri(context, fileName, subPath, mimeType)
                if (destinationUri == null) {
                    Log.w(TAG, "Unable to create MediaStore destination for ${item.name}")
                    return null
                }
                setDestinationUri(destinationUri)

                // Allow download only over WiFi by default for large media files
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)

                // Show download notification
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                // Set MIME type based on media type
                setMimeType(mimeType)
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
    fun isDownloaded(context: Context, item: BaseItemDto, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        return try {
            if (!hasStoragePermission(context, item)) {
                Log.w(TAG, "Missing storage permission for ${item.name}")
                return false
            }
            val fileName = generateFileName(item)
            val subPath = generateSubPath(item)

            queryDownloadEntry(context, fileName, subPath) != null
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

            queryDownloadEntry(context, fileName, subPath)?.uri?.toString()
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

            val entry = queryDownloadEntry(context, fileName, subPath)
            if (entry?.uri != null) {
                val deleted = context.contentResolver.delete(entry.uri, null, null) > 0
                if (!deleted) {
                    Log.w(TAG, "Failed to delete MediaStore entry for ${item.name}")
                }
                deleted
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "MediaStore entry does not exist for ${item.name}")
                }
                true
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
            sumMediaStoreDownloads(context)
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
            deleteMediaStoreDownloads(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all downloads: ${e.message}", e)
            false
        }
    }

    // Helper methods

    private fun sumMediaStoreDownloads(context: Context): Long {
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.MediaColumns.SIZE,
            MediaStore.Downloads.RELATIVE_PATH,
        )
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("${buildRelativePath("")}%")

        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            var total = 0L
            val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) {
                if (sizeIndex != -1) {
                    total += cursor.getLong(sizeIndex)
                }
            }
            return total
        }
        return 0L
    }

    private fun deleteMediaStoreDownloads(context: Context): Boolean {
        val resolver = context.contentResolver
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("${buildRelativePath("")}%")

        return resolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            selection,
            selectionArgs,
        ) >= 0
    }

    private fun hasStoragePermission(context: Context, item: BaseItemDto): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = when (item.type) {
                BaseItemKind.AUDIO -> Manifest.permission.READ_MEDIA_AUDIO
                BaseItemKind.MOVIE, BaseItemKind.EPISODE, BaseItemKind.MUSIC_VIDEO -> Manifest.permission.READ_MEDIA_VIDEO
                else -> null
            }
            permission?.let {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            } ?: true
        } else {
            true
        }
    }

    private fun generateFileName(item: BaseItemDto): String {
        val baseName = (item.name ?: "Unknown").replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val extension = getFileExtension(item)
        return "$baseName.$extension"
    }

    private fun generateSubPath(item: BaseItemDto): String {
        return when (item.type) {
            BaseItemKind.MOVIE -> "Movies"
            BaseItemKind.EPISODE -> "TV Shows/${item.seriesName ?: "Unknown Series"}"
            BaseItemKind.AUDIO -> "Music/${item.albumArtist ?: "Unknown Artist"}"
            BaseItemKind.MUSIC_VIDEO -> "Music Videos"
            else -> "Other"
        }.replace(Regex("[^a-zA-Z0-9._/ -]"), "_")
    }

    private fun getFileExtension(item: BaseItemDto): String {
        // Default extensions based on media type
        return when (item.type) {
            BaseItemKind.MOVIE, BaseItemKind.EPISODE -> "mp4"
            BaseItemKind.AUDIO -> "mp3"
            BaseItemKind.MUSIC_VIDEO -> "mp4"
            else -> "bin"
        }
    }

    private fun getMimeType(item: BaseItemDto): String? {
        return when (item.type) {
            BaseItemKind.MOVIE, BaseItemKind.EPISODE, BaseItemKind.MUSIC_VIDEO -> "video/mp4"
            BaseItemKind.AUDIO -> "audio/mpeg"
            else -> null
        }
    }

    // Pre-API 29 legacy file-system helpers removed; MediaStore is used exclusively on supported devices.

    private fun queryDownloadEntry(context: Context, fileName: String, subPath: String): DownloadEntry? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val resolver = context.contentResolver
        val relativePath = buildRelativePath(subPath)
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.MediaColumns.SIZE,
            MediaStore.Downloads.RELATIVE_PATH,
        )
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(fileName, relativePath)

        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val idIndex = cursor.getColumnIndex(MediaStore.Downloads._ID)
                if (sizeIndex != -1 && idIndex != -1) {
                    val size = cursor.getLong(sizeIndex)
                    if (size > 0) {
                        val id = cursor.getLong(idIndex)
                        val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                        return DownloadEntry(uri = uri)
                    }
                }
            }
        }
        return null
    }

    private fun createDownloadUri(
        context: Context,
        fileName: String,
        subPath: String,
        mimeType: String,
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val relativePath = buildRelativePath(subPath)
        return try {
            MediaStoreSaver.prepareDownload(
                context = context,
                fileName = fileName,
                relativePath = relativePath,
                mimeType = mimeType,
            )
        } catch (ioe: IOException) {
            Log.w(TAG, "Unable to create MediaStore destination for $fileName", ioe)
            null
        }
    }

    private fun buildRelativePath(subPath: String): String {
        val basePath = "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOADS_FOLDER"
        val normalized = subPath.trim('/').takeIf { it.isNotEmpty() }
        return if (normalized != null) {
            "$basePath/$normalized/"
        } else {
            "$basePath/"
        }
    }

    private data class DownloadEntry(
        val uri: Uri,
    )
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
