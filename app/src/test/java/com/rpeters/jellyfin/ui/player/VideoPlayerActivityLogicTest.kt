package com.rpeters.jellyfin.ui.player

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlayerActivityLogicTest {

    @Test
    fun `shouldPausePlayback returns false when in pip`() {
        val result = VideoPlayerActivity.shouldPausePlayback(
            isInPictureInPictureMode = true,
            isFinishing = true,
            isChangingConfigurations = false,
        )

        assertFalse(result)
    }

    @Test
    fun `shouldPausePlayback returns true when finishing without pip`() {
        val result = VideoPlayerActivity.shouldPausePlayback(
            isInPictureInPictureMode = false,
            isFinishing = true,
            isChangingConfigurations = true,
        )

        assertTrue(result)
    }

    @Test
    fun `shouldPausePlayback returns true when leaving without configuration change`() {
        val result = VideoPlayerActivity.shouldPausePlayback(
            isInPictureInPictureMode = false,
            isFinishing = false,
            isChangingConfigurations = false,
        )

        assertTrue(result)
    }

    @Test
    fun `shouldPausePlayback returns false when changing configurations`() {
        val result = VideoPlayerActivity.shouldPausePlayback(
            isInPictureInPictureMode = false,
            isFinishing = false,
            isChangingConfigurations = true,
        )

        assertFalse(result)
    }

    @Test
    fun `shouldAutoEnterPip returns true for pre-S when supported and playing`() {
        val result = VideoPlayerActivity.shouldAutoEnterPip(
            sdkInt = Build.VERSION_CODES.R,
            isPipSupported = true,
            isPlaying = true,
        )

        assertTrue(result)
    }

    @Test
    fun `shouldAutoEnterPip returns false on S or newer`() {
        val result = VideoPlayerActivity.shouldAutoEnterPip(
            sdkInt = Build.VERSION_CODES.S,
            isPipSupported = true,
            isPlaying = true,
        )

        assertFalse(result)
    }

    @Test
    fun `shouldAutoEnterPip returns false when not playing`() {
        val result = VideoPlayerActivity.shouldAutoEnterPip(
            sdkInt = Build.VERSION_CODES.R,
            isPipSupported = true,
            isPlaying = false,
        )

        assertFalse(result)
    }
}
