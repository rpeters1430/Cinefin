package com.rpeters.jellyfin.ui.player

import android.content.Context
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.TextOutput

/** Bounds and step for the in-player subtitle sync control. */
object SubtitleDelay {
    const val STEP_MS = 100L
    const val MAX_MS = 10_000L

    fun clamp(delayMs: Long): Long = delayMs.coerceIn(-MAX_MS, MAX_MS)

    /** Formats a delay as a signed seconds label, e.g. "+0.3 s" or "-1.5 s". */
    fun format(delayMs: Long): String {
        val sign = if (delayMs > 0) "+" else if (delayMs < 0) "-" else ""
        val absMs = kotlin.math.abs(delayMs)
        return "$sign${absMs / 1000}.${(absMs % 1000) / 100} s"
    }
}

/**
 * Thread-safe holder for the current subtitle delay. Written from the UI thread and read from the
 * playback thread on every render pass.
 */
class SubtitleDelayController {
    @Volatile
    var delayMs: Long = 0L
        private set

    fun setDelay(delayMs: Long) {
        this.delayMs = SubtitleDelay.clamp(delayMs)
    }

    internal val delayUs: Long
        get() = delayMs * 1000L
}

/**
 * Wraps a text renderer and shifts the position it renders at, so subtitles appear later
 * (positive delay) or earlier (negative delay) than the video. Media3 has no built-in subtitle
 * offset, and this keeps side-loaded, embedded, and HLS subtitle tracks working the same way.
 */
@UnstableApi
internal class DelayedTextRenderer(
    renderer: Renderer,
    private val controller: SubtitleDelayController,
) : ForwardingRenderer(renderer) {

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        super.render(positionUs - controller.delayUs, elapsedRealtimeUs)
    }

    override fun resetPosition(positionUs: Long, sampleStreamIsResetToKeyFrame: Boolean) {
        super.resetPosition(positionUs - controller.delayUs, sampleStreamIsResetToKeyFrame)
    }
}

/** Renderers factory that routes every text renderer through [DelayedTextRenderer]. */
@UnstableApi
internal class SubtitleDelayRenderersFactory(
    context: Context,
    private val controller: SubtitleDelayController,
) : DefaultRenderersFactory(context) {

    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        val textRenderers = ArrayList<Renderer>()
        super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, textRenderers)
        textRenderers.mapTo(out) { DelayedTextRenderer(it, controller) }
    }
}
