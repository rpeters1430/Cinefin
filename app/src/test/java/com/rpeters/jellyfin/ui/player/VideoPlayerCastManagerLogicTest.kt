package com.rpeters.jellyfin.ui.player

import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.ui.player.cast.CastSessionEndReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class VideoPlayerCastManagerLogicTest {

    @Test
    fun `unexpected cast disconnect resumes local playback`() {
        assertTrue(
            VideoPlayerCastManager.shouldResumeLocallyAfterCastEnds(
                wasCastConnected = true,
                localPlaybackReleasedForCast = true,
                isConnected = false,
                sessionEndReason = CastSessionEndReason.CONNECTION_LOST,
            ),
        )
    }

    @Test
    fun `user requested cast disconnect does not resume local playback`() {
        assertFalse(
            VideoPlayerCastManager.shouldResumeLocallyAfterCastEnds(
                wasCastConnected = true,
                localPlaybackReleasedForCast = true,
                isConnected = false,
                sessionEndReason = CastSessionEndReason.USER_DISCONNECTED,
            ),
        )
    }

    @Test
    fun `cast load failure resumes local playback`() {
        assertTrue(
            VideoPlayerCastManager.shouldResumeLocallyAfterCastEnds(
                wasCastConnected = false,
                localPlaybackReleasedForCast = true,
                isConnected = false,
                sessionEndReason = CastSessionEndReason.LOAD_FAILED,
            ),
        )
    }

    @Test
    fun `connection loss delays local resume`() {
        assertTrue(
            VideoPlayerCastManager.shouldDelayLocalResume(
                CastSessionEndReason.CONNECTION_LOST,
            ),
        )
        assertFalse(
            VideoPlayerCastManager.shouldDelayLocalResume(
                CastSessionEndReason.LOAD_FAILED,
            ),
        )
    }

    @Test
    fun `playerMessageForCastEnd explains resumed local playback after cast loss`() {
        assertEquals(
            "Cast connection lost. Playback resumed on this device.",
            VideoPlayerCastManager.playerMessageForCastEnd(
                sessionEndReason = CastSessionEndReason.CONNECTION_LOST,
                didResumeLocally = true,
            ),
        )
    }

    @Test
    fun `playerMessageForCastEnd suppresses message for user disconnect`() {
        assertNull(
            VideoPlayerCastManager.playerMessageForCastEnd(
                sessionEndReason = CastSessionEndReason.USER_DISCONNECTED,
                didResumeLocally = false,
            ),
        )
    }
}
