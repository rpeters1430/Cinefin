package com.example.jellyfinandroid.ui.utils

import android.content.Context
import android.os.Environment
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MediaDownloadManagerTest {

    @Test
    fun getDownloadBaseDir_returnsExternalFilesDir_onModernDevices() {
        val context = mockk<Context>()
        val expected = File("/tmp/external")
        every { context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) } returns expected

        val result = MediaDownloadManager.getDownloadBaseDir(context, sdkInt = android.os.Build.VERSION_CODES.Q)

        assertEquals(expected, result)
    }

    @Test
    fun isDownloaded_checksExternalFilesDir() {
        val baseDir = createTempDir()
        val context = mockk<Context>()
        every { context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) } returns baseDir

        val item = BaseItemDto(name = "Video", type = BaseItemKind.MOVIE)
        val file = File(baseDir, "JellyfinAndroid/Movies/Video.mp4")
        file.parentFile?.mkdirs()
        file.writeText("data")

        assertTrue(MediaDownloadManager.isDownloaded(context, item))
    }
}
