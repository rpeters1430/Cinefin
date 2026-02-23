package com.rpeters.jellyfin.data.offline

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.rpeters.jellyfin.data.common.TestDispatcherProvider
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class OfflineDownloadManagerTest {

    private lateinit var manager: OfflineDownloadManager
    private lateinit var context: Context
    private lateinit var repository: JellyfinRepository
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var deviceCapabilities: com.rpeters.jellyfin.data.DeviceCapabilities
    private lateinit var tempDir: File
    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = TestDispatcherProvider(testDispatcher)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = mockk(relaxed = true)
        okHttpClient = mockk(relaxed = true)

        // Create a temporary directory for test downloads and datastore
        tempDir = createTempDirectory("jellyfin_test").toFile()

        // Create a real in-memory DataStore using test scope
        dataStore = PreferenceDataStoreFactory.create(
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher + kotlinx.coroutines.SupervisorJob()),
            produceFile = { File(tempDir, "test.preferences_pb") }
        )

        val mockEncryptedPreferences = mockk<com.rpeters.jellyfin.data.security.EncryptedPreferences>(relaxed = true)
        every { mockEncryptedPreferences.getEncryptedString(any()) } returns MutableStateFlow("http://test.com/decrypted")

        deviceCapabilities = mockk(relaxed = true)
        every { deviceCapabilities.getDeviceId() } returns "test-device-id"

        manager = OfflineDownloadManager(
            context = context,
            repository = repository,
            okHttpClient = okHttpClient,
            encryptedPreferences = mockEncryptedPreferences,
            dispatchers = testDispatchers,
            dataStore = dataStore,
            deviceCapabilities = deviceCapabilities,
        )
    }

    @After
    fun tearDown() {
        // Clean up temp directory
        tempDir.deleteRecursively()
        clearMocks(repository, okHttpClient)
    }

    @Test
    fun `startDownload creates download with correct properties`() = runTest(testDispatcher) {
        val item = buildBaseItem(
            id = UUID.randomUUID(),
            name = "Test Movie",
        )
        val downloadUrl = "https://server/stream/video.mp4"

        every { repository.getStreamUrl(item.id.toString()) } returns downloadUrl
        mockSuccessfulDownload()

        val downloadId = manager.startDownload(item, downloadUrl = downloadUrl)
        advanceUntilIdle()

        assertNotNull("Download ID should not be null", downloadId)
        assertTrue("Download ID should not be empty", downloadId.isNotEmpty())
        
        val downloads = manager.downloads.value
        assertTrue(downloads.any { it.id == downloadId })
    }

    @Test
    fun `pauseDownload updates status to PAUSED`() = runTest(testDispatcher) {
        val item = buildBaseItem(id = UUID.randomUUID(), name = "Test Movie")
        val downloadUrl = "https://server/stream/video.mp4"

        every { repository.getStreamUrl(item.id.toString()) } returns downloadUrl
        mockSuccessfulDownload()

        val downloadId = manager.startDownload(item, downloadUrl = downloadUrl)
        advanceUntilIdle()

        manager.pauseDownload(downloadId)
        advanceUntilIdle()

        val download = manager.downloads.value.find { it.id == downloadId }
        assertNotNull("Download should exist", download)
        assertEquals(DownloadStatus.PAUSED, download?.status)
    }

    @Test
    fun `deleteDownload removes download from list`() = runTest(testDispatcher) {
        val item = buildBaseItem(id = UUID.randomUUID(), name = "Test Movie")
        val downloadUrl = "https://server/stream/video.mp4"

        every { repository.getStreamUrl(item.id.toString()) } returns downloadUrl
        mockSuccessfulDownload()

        val downloadId = manager.startDownload(item, downloadUrl = downloadUrl)
        advanceUntilIdle()

        manager.deleteDownload(downloadId)
        advanceUntilIdle()

        val download = manager.downloads.value.find { it.id == downloadId }
        assertNull("Download should be removed", download)
    }

    @Test
    fun `isItemDownloaded returns true when item completed`() = runTest(testDispatcher) {
        val itemId = UUID.randomUUID()
        val item = buildBaseItem(id = itemId, name = "Test Movie")
        val downloadUrl = "https://server/stream/video.mp4"

        every { repository.getStreamUrl(itemId.toString()) } returns downloadUrl
        mockSuccessfulDownload()

        manager.startDownload(item, downloadUrl = downloadUrl)
        advanceUntilIdle()

        // Verify status is completed
        val isDownloaded = manager.isItemDownloaded(itemId.toString())
        assertTrue("Item should be downloaded", isDownloaded)
    }

    @Test
    fun `getAvailableStorage returns positive value`() = runTest(testDispatcher) {
        val availableStorage = manager.getAvailableStorage()
        assertTrue("Available storage should be positive", availableStorage >= 0)
    }

    @Test
    fun `downloadProgress updates during download`() = runTest(testDispatcher) {
        val item = buildBaseItem(id = UUID.randomUUID(), name = "Test Movie")
        val downloadUrl = "https://server/stream/video.mp4"

        every { repository.getStreamUrl(item.id.toString()) } returns downloadUrl
        mockSuccessfulDownload()

        val downloadId = manager.startDownload(item, downloadUrl = downloadUrl)
        advanceUntilIdle()

        val progress = manager.downloadProgress.value
        assertNotNull("Download progress should not be null", progress)
        assertTrue(progress.containsKey(downloadId))
        assertEquals(100f, progress[downloadId]?.progressPercent)
    }

    @Test
    fun `getAvailableStorage does not throw when called`() = runTest(testDispatcher) {
        val storage = manager.getAvailableStorage()
        assertTrue("Available storage should be non-negative", storage >= 0)
    }

    @Test
    fun `getUsedStorage does not throw when called`() = runTest(testDispatcher) {
        val used = manager.getUsedStorage()
        assertTrue("Used storage should be non-negative", used >= 0)
    }

    @Test
    fun `getTotalStorage returns a positive value`() = runTest(testDispatcher) {
        val total = manager.getTotalStorage()
        assertTrue("Total storage should be greater than zero", total > 0)
    }

    @Test
    fun `download completes correctly with throttled DataStore writes`() = runTest(testDispatcher) {
        val item = buildBaseItem(id = UUID.randomUUID(), name = "Throttle Test")
        val downloadUrl = "https://server/stream/video.mp4"

        // 3 MB body — would cause ~384 DataStore writes at 8 KB/chunk before the fix
        val threeMB = ByteArray(3 * 1024 * 1024) { it.toByte() }
        val call = mockk<Call>(relaxed = true)
        val response = Response.Builder()
            .request(Request.Builder().url("https://server/video.mp4").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(threeMB.toResponseBody("video/mp4".toMediaType()))
            .build()
        every { okHttpClient.newCall(any()) } returns call
        every { call.execute() } returns response

        val downloadId = manager.startDownload(item, downloadUrl = downloadUrl)
        advanceUntilIdle()

        val download = manager.downloads.value.find { it.id == downloadId }
        assertNotNull("Download should exist", download)
        assertEquals("Download should be COMPLETED", DownloadStatus.COMPLETED, download?.status)
        assertTrue("Progress should be tracked", manager.downloadProgress.value.containsKey(downloadId))
        assertEquals("Final progress should be 100%", 100f, manager.downloadProgress.value[downloadId]?.progressPercent)
    }

    @Test
    fun `corrupt DataStore JSON results in empty downloads not a crash`() = runTest(testDispatcher) {
        // Pre-seed a separate DataStore with invalid JSON
        val corruptDataStore = PreferenceDataStoreFactory.create(
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher + kotlinx.coroutines.SupervisorJob()),
            produceFile = { File(tempDir, "corrupt_test.preferences_pb") }
        )
        corruptDataStore.edit { prefs ->
            prefs[androidx.datastore.preferences.core.stringPreferencesKey("offline_downloads")] =
                "{{not valid json at all}}"
        }
        advanceUntilIdle()

        val mockEncryptedPreferences = mockk<com.rpeters.jellyfin.data.security.EncryptedPreferences>(relaxed = true)
        every { mockEncryptedPreferences.getEncryptedString(any()) } returns MutableStateFlow("http://test.com/decrypted")

        val corruptManager = OfflineDownloadManager(
            context = context,
            repository = repository,
            okHttpClient = okHttpClient,
            encryptedPreferences = mockEncryptedPreferences,
            dispatchers = testDispatchers,
            dataStore = corruptDataStore,
            deviceCapabilities = deviceCapabilities,
        )
        advanceUntilIdle()

        val downloads = corruptManager.downloads.value
        assertTrue("Downloads should be empty after corrupt data, not crash", downloads.isEmpty())
        corruptManager.cleanup()
    }

    @Test
    fun `startDownload fails fast when no download URL can be resolved`() = runTest(testDispatcher) {
        val item = buildBaseItem(id = UUID.randomUUID(), name = "No URL")

        every { repository.getDownloadUrl(item.id.toString()) } returns null
        every { repository.getStreamUrl(item.id.toString()) } returns null

        try {
            manager.startDownload(item)
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message?.contains("Unable to resolve a download URL") == true)
        }

        assertTrue(manager.downloads.value.isEmpty())
    }

    // Helper functions

    private fun buildBaseItem(
        id: UUID,
        name: String,
        type: BaseItemKind = BaseItemKind.MOVIE,
    ): BaseItemDto = BaseItemDto(
        id = id,
        name = name,
        type = type,
    )

    private fun mockSuccessfulDownload() {
        val call = mockk<Call>(relaxed = true)
        val response = Response.Builder()
            .request(Request.Builder().url("https://server/video.mp4").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("test video content".toResponseBody())
            .build()

        every { okHttpClient.newCall(any()) } returns call
        every { call.execute() } returns response
    }
}
