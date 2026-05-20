package com.rpeters.jellyfin.ui.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.mockk.CapturingSlot
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import com.rpeters.jellyfin.data.offline.DownloadStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflineManagerTest {

    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var network: Network
    private lateinit var wifiCapabilities: NetworkCapabilities
    private lateinit var cellularCapabilities: NetworkCapabilities
    private lateinit var networkCallbackSlot: CapturingSlot<ConnectivityManager.NetworkCallback>

    private var currentNetwork: Network? = null
    private var currentCapabilities: NetworkCapabilities? = null

    private lateinit var offlineManager: OfflineManager
    private val offlineDownloadManager: com.rpeters.jellyfin.data.offline.OfflineDownloadManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        io.mockk.every { offlineDownloadManager.downloads } returns kotlinx.coroutines.flow.MutableStateFlow(emptyList())

        context = mockk(relaxed = true)
        connectivityManager = mockk(relaxed = true)
        network = mockk(relaxed = true)
        wifiCapabilities = mockk(relaxed = true) {
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
            every { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
            every { hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
            every { hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
            every { hasTransport(NetworkCapabilities.TRANSPORT_VPN) } returns false
        }
        cellularCapabilities = mockk(relaxed = true) {
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
            every { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
            every { hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
            every { hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
            every { hasTransport(NetworkCapabilities.TRANSPORT_VPN) } returns false
        }

        currentNetwork = network
        currentCapabilities = wifiCapabilities

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { connectivityManager.activeNetwork } answers { currentNetwork }
        every { connectivityManager.getNetworkCapabilities(any()) } answers { currentCapabilities }

        networkCallbackSlot = slot()
        every {
            connectivityManager.registerNetworkCallback(any<NetworkRequest>(), capture(networkCallbackSlot))
        } answers { }
        justRun { connectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>()) }

        offlineManager = OfflineManager(context, offlineDownloadManager)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun networkCallback_updatesOnlineStateAndType() = runTest {
        assertTrue(networkCallbackSlot.isCaptured)

        networkCallbackSlot.captured.onAvailable(network)

        assertTrue(offlineManager.isOnline.value)
        assertEquals(NetworkType.WIFI, offlineManager.networkType.value)

        currentNetwork = null
        networkCallbackSlot.captured.onLost(network)

        assertFalse(offlineManager.isOnline.value)
        assertEquals(NetworkType.NONE, offlineManager.networkType.value)

        currentNetwork = network
        currentCapabilities = cellularCapabilities
        networkCallbackSlot.captured.onCapabilitiesChanged(network, cellularCapabilities)

        assertEquals(NetworkType.CELLULAR, offlineManager.networkType.value)
    }

    @Test
    fun offlineStorageUsage_reportsTotalSizeAndCount() {
        val downloadOne = com.rpeters.jellyfin.data.offline.OfflineDownload(
            id = "1",
            jellyfinItemId = "item1",
            itemName = "Item 1",
            itemType = "Movie",
            downloadUrl = "http://...",
            localFilePath = "/path/to/1",
            fileSize = 1_048_576L,
            status = DownloadStatus.COMPLETED
        )
        val downloadTwo = com.rpeters.jellyfin.data.offline.OfflineDownload(
            id = "2",
            jellyfinItemId = "item2",
            itemName = "Item 2",
            itemType = "Episode",
            downloadUrl = "http://...",
            localFilePath = "/path/to/2",
            fileSize = 1_048_576L,
            status = DownloadStatus.COMPLETED
        )
        every { offlineDownloadManager.getCompletedDownloads() } returns listOf(downloadOne, downloadTwo)

        val itemOne = mockk<BaseItemDto>(relaxed = true) {
            every { id } returns java.util.UUID.nameUUIDFromBytes("item1".toByteArray())
            every { type } returns BaseItemKind.MOVIE
        }
        val itemTwo = mockk<BaseItemDto>(relaxed = true) {
            every { id } returns java.util.UUID.nameUUIDFromBytes("item2".toByteArray())
            every { type } returns BaseItemKind.EPISODE
        }

        offlineManager.setOfflineContent(listOf(
            OfflineLibraryItem(itemOne, "/path/to/1", null, null, 1_048_576L, null),
            OfflineLibraryItem(itemTwo, "/path/to/2", null, null, 1_048_576L, null)
        ))

        val storageInfo = offlineManager.getOfflineStorageUsage()

        assertEquals(2_097_152L, storageInfo.totalSizeBytes)
        assertEquals(2, storageInfo.itemCount)
        assertEquals("2.0 MB", storageInfo.formattedSize)
    }

    @Test
    fun suggestPlaybackSource_prefersLocalThenFallsBackToStreamOrUnavailable() {
        val itemId = java.util.UUID.randomUUID()
        val item = mockk<BaseItemDto>(relaxed = true) {
            every { id } returns itemId
        }
        val onlineUrl = "https://example.com/stream"

        every { offlineDownloadManager.isItemDownloaded(itemId.toString()) } returns true
        offlineManager.setOfflineContent(listOf(
            OfflineLibraryItem(item, "file://offline", null, null, 100L, null)
        ))

        assertEquals(PlaybackSource.LOCAL, offlineManager.suggestPlaybackSource(item))
        assertEquals("file://offline", item.getBestPlaybackUrl(offlineManager, onlineUrl))

        every { offlineDownloadManager.isItemDownloaded(itemId.toString()) } returns false

        assertEquals(PlaybackSource.STREAM, offlineManager.suggestPlaybackSource(item))
        assertEquals(onlineUrl, item.getBestPlaybackUrl(offlineManager, onlineUrl))

        currentNetwork = null

        assertFalse(offlineManager.isNetworkSuitableForStreaming())
        assertEquals(PlaybackSource.UNAVAILABLE, offlineManager.suggestPlaybackSource(item))
        assertNull(item.getBestPlaybackUrl(offlineManager, onlineUrl))
    }

    private fun OfflineManager.setOfflineContent(items: List<OfflineLibraryItem>) {
        val field = OfflineManager::class.java.getDeclaredField("_offlineContent")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val state = field.get(this) as MutableStateFlow<List<OfflineLibraryItem>>
        state.value = items
    }
}
