package com.rpeters.jellyfin.ui.player

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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

    // §1.3 Android 16 — VideoPlayerActivity must handle all listed config changes so
    // the player survives dark-mode switches, font scale, RTL, and system-forced
    // orientation changes on ≥600dp screens without recreating the Activity.
    @Test
    fun `VideoPlayerActivity configChanges handles all required entries for Android 16`() {
        val manifest = File("src/main/AndroidManifest.xml").takeIf { it.exists() }
            ?: File("../app/src/main/AndroidManifest.xml").takeIf { it.exists() }

        requireNotNull(manifest) { "Could not locate AndroidManifest.xml from test working directory" }

        val content = manifest.readText()
        val required = listOf(
            "orientation",
            "screenSize",
            "keyboardHidden",
            "screenLayout",
            "density",
            "smallestScreenSize",
            "uiMode",        // dark/light mode switch must not recreate the player
            "fontScale",     // accessibility font changes must not disrupt playback
            "layoutDirection", // RTL switch must not disrupt playback
        )

        val videoPlayerEntry = content
            .substringAfter("VideoPlayerActivity")
            .substringBefore("</activity>")

        required.forEach { entry ->
            assertTrue(
                "VideoPlayerActivity configChanges must include '$entry' to survive Android 16 system changes",
                videoPlayerEntry.contains(entry),
            )
        }
    }

    @Test
    fun `MainActivity manifest configures windowSoftInputMode adjustResize for Android 15 edge-to-edge`() {
        val manifest = File("src/main/AndroidManifest.xml").takeIf { it.exists() }
            ?: File("../app/src/main/AndroidManifest.xml").takeIf { it.exists() }

        requireNotNull(manifest) { "Could not locate AndroidManifest.xml from test working directory" }

        val content = manifest.readText()
        val mainActivityEntry = content
            .substringAfter("MainActivity")
            .substringBefore("</activity>")

        assertTrue(
            "MainActivity must configure windowSoftInputMode='adjustResize' for Android 15 edge-to-edge IME handling",
            mainActivityEntry.contains("android:windowSoftInputMode=\"adjustResize\""),
        )
    }

    @Test
    fun `Activities do not use deprecated setDecorFitsSystemWindows for Android 15 edge-to-edge`() {
        val mainActivityFile = File("src/main/java/com/rpeters/jellyfin/MainActivity.kt").takeIf { it.exists() }
            ?: File("../app/src/main/java/com/rpeters/jellyfin/MainActivity.kt").takeIf { it.exists() }
        val videoPlayerActivityFile = File("src/main/java/com/rpeters/jellyfin/ui/player/VideoPlayerActivity.kt").takeIf { it.exists() }
            ?: File("../app/src/main/java/com/rpeters/jellyfin/ui/player/VideoPlayerActivity.kt").takeIf { it.exists() }

        requireNotNull(mainActivityFile) { "Could not locate MainActivity.kt" }
        requireNotNull(videoPlayerActivityFile) { "Could not locate VideoPlayerActivity.kt" }

        assertFalse(
            "MainActivity must not use deprecated setDecorFitsSystemWindows",
            mainActivityFile.readText().contains("setDecorFitsSystemWindows"),
        )
        assertFalse(
            "VideoPlayerActivity must not use deprecated setDecorFitsSystemWindows",
            videoPlayerActivityFile.readText().contains("setDecorFitsSystemWindows"),
        )
        assertTrue(
            "MainActivity must call enableEdgeToEdge",
            mainActivityFile.readText().contains("enableEdgeToEdge"),
        )
        assertTrue(
            "VideoPlayerActivity must call enableEdgeToEdge",
            videoPlayerActivityFile.readText().contains("enableEdgeToEdge"),
        )
    }
}
