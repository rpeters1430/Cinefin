package com.rpeters.jellyfin.ui.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.Renderer
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

@UnstableApi
class SubtitleDelayTest {

    @Test
    fun format_positiveNegativeAndZero_rendersSignedSeconds() {
        assertEquals("0.0 s", SubtitleDelay.format(0L))
        assertEquals("+0.3 s", SubtitleDelay.format(300L))
        assertEquals("-1.5 s", SubtitleDelay.format(-1_500L))
        assertEquals("+10.0 s", SubtitleDelay.format(10_000L))
    }

    @Test
    fun setDelay_outOfRange_clampsToMax() {
        val controller = SubtitleDelayController()

        controller.setDelay(60_000L)
        assertEquals(SubtitleDelay.MAX_MS, controller.delayMs)

        controller.setDelay(-60_000L)
        assertEquals(-SubtitleDelay.MAX_MS, controller.delayMs)
    }

    @Test
    fun render_positiveDelay_rendersEarlierPositionSoSubtitlesShowLater() {
        val inner = mockk<Renderer>(relaxed = true)
        val controller = SubtitleDelayController().apply { setDelay(500L) }
        val renderer = DelayedTextRenderer(inner, controller)

        renderer.render(10_000_000L, 42L)

        verify { inner.render(9_500_000L, 42L) }
    }

    @Test
    fun render_negativeDelay_rendersLaterPositionSoSubtitlesShowEarlier() {
        val inner = mockk<Renderer>(relaxed = true)
        val controller = SubtitleDelayController().apply { setDelay(-1_000L) }
        val renderer = DelayedTextRenderer(inner, controller)

        renderer.render(10_000_000L, 42L)
        renderer.resetPosition(2_000_000L, true)

        verify { inner.render(11_000_000L, 42L) }
        verify { inner.resetPosition(3_000_000L, true) }
    }

    @Test
    fun render_delayChangedAfterCreation_usesLatestValue() {
        val inner = mockk<Renderer>(relaxed = true)
        val controller = SubtitleDelayController()
        val renderer = DelayedTextRenderer(inner, controller)

        renderer.render(5_000_000L, 0L)
        controller.setDelay(200L)
        renderer.render(5_000_000L, 0L)

        verify { inner.render(5_000_000L, 0L) }
        verify { inner.render(4_800_000L, 0L) }
    }
}
