package com.example.jellyfinandroid.ui.downloads

import android.content.Context
import com.example.jellyfinandroid.data.offline.DownloadStatus
import com.example.jellyfinandroid.data.offline.OfflineDownload
import com.example.jellyfinandroid.data.offline.OfflineDownloadManager
import com.example.jellyfinandroid.data.offline.OfflinePlaybackManager
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    @MockK(relaxUnitFun = true)
    lateinit var downloadManager: OfflineDownloadManager

    @MockK(relaxUnitFun = true)
    lateinit var playbackManager: OfflinePlaybackManager

    @MockK
    lateinit var context: Context

    private lateinit var viewModel: DownloadsViewModel
    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var downloadsFlow: MutableStateFlow<List<OfflineDownload>>

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(dispatcher)
        downloadsFlow = MutableStateFlow(emptyList())
        every { downloadManager.downloads } returns downloadsFlow
        every { downloadManager.downloadProgress } returns MutableStateFlow(emptyMap())
        viewModel = DownloadsViewModel(context, downloadManager, playbackManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pauseAllDownloads pauses active downloads`() = runTest {
        val downloading = OfflineDownload(
            id = "1",
            jellyfinItemId = "j1",
            itemName = "Item1",
            itemType = "episode",
            downloadUrl = "",
            localFilePath = "",
            fileSize = 0L,
            status = DownloadStatus.DOWNLOADING,
        )
        val completed = downloading.copy(id = "2", status = DownloadStatus.COMPLETED)
        downloadsFlow.value = listOf(downloading, completed)

        viewModel.pauseAllDownloads()

        coVerify { downloadManager.pauseDownload("1") }
        coVerify(exactly = 0) { downloadManager.pauseDownload("2") }
    }

    @Test
    fun `clearCompletedDownloads deletes completed downloads`() = runTest {
        val downloading = OfflineDownload(
            id = "1",
            jellyfinItemId = "j1",
            itemName = "Item1",
            itemType = "episode",
            downloadUrl = "",
            localFilePath = "",
            fileSize = 0L,
            status = DownloadStatus.DOWNLOADING,
        )
        val completed = downloading.copy(id = "2", status = DownloadStatus.COMPLETED)
        downloadsFlow.value = listOf(downloading, completed)

        viewModel.clearCompletedDownloads()

        coVerify { downloadManager.deleteDownload("2") }
        coVerify(exactly = 0) { downloadManager.deleteDownload("1") }
    }
}
