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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

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

        offlineManager = OfflineManager(context)
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
        mockkObject(MediaDownloadManager)

        val itemOne = mockk<BaseItemDto>(relaxed = true) {
            every { type } returns BaseItemKind.MOVIE
        }
        val itemTwo = mockk<BaseItemDto>(relaxed = true) {
            every { type } returns BaseItemKind.EPISODE
        }

        offlineManager.setOfflineContent(listOf(itemOne, itemTwo))

        every { MediaDownloadManager.getTotalDownloadSize(context) } returns 2_097_152L

        val storageInfo = offlineManager.getOfflineStorageUsage()

        assertEquals(2_097_152L, storageInfo.totalSizeBytes)
        assertEquals(2, storageInfo.itemCount)
        assertEquals("2.0 MB", storageInfo.formattedSize)
    }

    @Test
    fun suggestPlaybackSource_prefersLocalThenFallsBackToStreamOrUnavailable() {
        mockkObject(MediaDownloadManager)
        val item = mockk<BaseItemDto>(relaxed = true)
        val onlineUrl = "https://example.com/stream"

        every { MediaDownloadManager.isDownloaded(context, item) } returns true
        every { MediaDownloadManager.getLocalFilePath(context, item) } returns "file://offline"

        assertEquals(PlaybackSource.LOCAL, offlineManager.suggestPlaybackSource(item))
        assertEquals("file://offline", item.getBestPlaybackUrl(offlineManager, onlineUrl))

        every { MediaDownloadManager.isDownloaded(context, item) } returns false

        assertEquals(PlaybackSource.STREAM, offlineManager.suggestPlaybackSource(item))
        assertEquals(onlineUrl, item.getBestPlaybackUrl(offlineManager, onlineUrl))

        currentNetwork = null

        assertFalse(offlineManager.isNetworkSuitableForStreaming())
        assertEquals(PlaybackSource.UNAVAILABLE, offlineManager.suggestPlaybackSource(item))
        assertNull(item.getBestPlaybackUrl(offlineManager, onlineUrl))
    }

    private fun OfflineManager.setOfflineContent(items: List<BaseItemDto>) {
        val field = OfflineManager::class.java.getDeclaredField("_offlineContent")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val state = field.get(this) as MutableStateFlow<List<BaseItemDto>>
        state.value = items
    }
}
