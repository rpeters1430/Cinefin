package com.rpeters.jellyfin.ui.player

import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class VideoPlayerPlaybackManagerLogicTest {

    @Test
    fun `uses offline source when download exists and device is online`() {
        assertTrue(
            VideoPlayerPlaybackManager.shouldUseOfflineSource(
                isDownloaded = true,
                forceOffline = false,
                isOnline = true,
            ),
        )
    }

    @Test
    fun `uses offline source when download exists and device is offline`() {
        assertTrue(
            VideoPlayerPlaybackManager.shouldUseOfflineSource(
                isDownloaded = true,
                forceOffline = false,
                isOnline = false,
            ),
        )
    }

    @Test
    fun `does not use offline source when no download exists`() {
        assertFalse(
            VideoPlayerPlaybackManager.shouldUseOfflineSource(
                isDownloaded = false,
                forceOffline = true,
                isOnline = false,
            ),
        )
    }

    @Test
    fun `vertical drag handler ignores mostly horizontal drags`() {
        assertFalse(
            VideoPlayerGestureConstants.shouldHandleVerticalDrag(
                dragAmountX = 60f,
                dragAmountY = 8f,
            ),
        )
    }

    @Test
    fun `vertical drag handler accepts clear vertical swipes`() {
        assertTrue(
            VideoPlayerGestureConstants.shouldHandleVerticalDrag(
                dragAmountX = 8f,
                dragAmountY = 60f,
            ),
        )
    }
}
