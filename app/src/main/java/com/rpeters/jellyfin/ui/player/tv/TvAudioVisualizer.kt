package com.rpeters.jellyfin.ui.player.tv

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

/**
 * TV-optimized audio visualizer with animated waveform and spectrum
 * Provides visual feedback during audio playback
 */
@Composable
fun TvAudioVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    visualizerStyle: VisualizerStyle = VisualizerStyle.WAVEFORM,
) {
    when (visualizerStyle) {
        VisualizerStyle.WAVEFORM -> AnimatedWaveformVisualizer(
            isPlaying = isPlaying,
            modifier = modifier,
        )
        VisualizerStyle.SPECTRUM -> AnimatedSpectrumVisualizer(
            isPlaying = isPlaying,
            modifier = modifier,
        )
        VisualizerStyle.CIRCULAR -> AnimatedCircularVisualizer(
            isPlaying = isPlaying,
            modifier = modifier,
        )
    }
}

/**
 * Animated waveform visualizer
 */
@Composable
private fun AnimatedWaveformVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "waveform_phase",
    )
    val playingColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val pausedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Transparent),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (isPlaying) {
                drawWaveform(
                    phase = phase,
                    color = playingColor,
                )
            } else {
                // Static waveform when paused
                drawStaticWaveform(
                    color = pausedColor,
                )
            }
        }
    }
}

/**
 * Animated spectrum analyzer visualizer
 */
@Composable
private fun AnimatedSpectrumVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spectrum")
    val animationPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spectrum_phase",
    )

    val barCount = 32
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val pausedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Transparent),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (isPlaying) {
                drawSpectrum(
                    barCount = barCount,
                    animationPhase = animationPhase,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                )
            } else {
                drawStaticSpectrum(
                    barCount = barCount,
                    color = pausedColor,
                )
            }
        }
    }
}

/**
 * Animated circular visualizer
 */
@Composable
private fun AnimatedCircularVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "circular")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "circular_rotation",
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "circular_pulse",
    )
    val activeColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Transparent),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (isPlaying) {
                drawCircularWaveform(
                    rotation = rotation,
                    pulse = pulse,
                    color = activeColor,
                )
            }
        }
    }
}

/**
 * Draw animated waveform
 */
private fun DrawScope.drawWaveform(
    phase: Float,
    color: Color,
) {
    val width = size.width
    val height = size.height
    val centerY = height / 2f

    val path = Path().apply {
        moveTo(0f, centerY)

        val waveLength = width / 4f
        val amplitude = height * 0.3f
        val samples = 200

        for (i in 0..samples) {
            val x = (i.toFloat() / samples) * width
            val normalizedX = x / waveLength
            val wavePhase = (normalizedX + phase / 360f) * 2 * Math.PI
            val y = centerY + (
                sin(wavePhase.toFloat()) * amplitude *
                    sin((normalizedX + phase / 720f) * Math.PI).toFloat()
                )

            if (i == 0) {
                moveTo(x, y.toFloat())
            } else {
                lineTo(x, y.toFloat())
            }
        }
    }

    drawPath(
        path = path,
        color = color,
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 4.dp.toPx(),
        ),
    )
}

/**
 * Draw static waveform when paused
 */
private fun DrawScope.drawStaticWaveform(color: Color) {
    val width = size.width
    val height = size.height
    val centerY = height / 2f

    drawLine(
        color = color,
        start = Offset(0f, centerY),
        end = Offset(width, centerY),
        strokeWidth = 4.dp.toPx(),
    )
}

/**
 * Draw animated spectrum bars
 */
private fun DrawScope.drawSpectrum(
    barCount: Int,
    animationPhase: Float,
    primaryColor: Color,
    secondaryColor: Color,
) {
    val width = size.width
    val height = size.height
    val barWidth = (width / barCount) * 0.7f
    val spacing = width / barCount

    for (i in 0 until barCount) {
        val normalizedIndex = i.toFloat() / barCount
        val phaseOffset = (normalizedIndex * 2 * Math.PI + animationPhase / 360f * 2 * Math.PI).toFloat()
        val barHeight = (abs(sin(phaseOffset)) * 0.7f + 0.3f) * height

        val x = i * spacing + (spacing - barWidth) / 2
        val y = height - barHeight

        val gradient = Brush.verticalGradient(
            colors = listOf(
                primaryColor,
                secondaryColor,
            ),
            startY = y,
            endY = height,
        )

        drawRoundRect(
            brush = gradient,
            topLeft = Offset(x, y),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
        )
    }
}

/**
 * Draw static spectrum when paused
 */
private fun DrawScope.drawStaticSpectrum(
    barCount: Int,
    color: Color,
) {
    val width = size.width
    val height = size.height
    val barWidth = (width / barCount) * 0.7f
    val spacing = width / barCount

    for (i in 0 until barCount) {
        val barHeight = height * 0.2f
        val x = i * spacing + (spacing - barWidth) / 2
        val y = height - barHeight

        drawRoundRect(
            color = color,
            topLeft = Offset(x, y),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
        )
    }
}

/**
 * Draw circular waveform
 */
private fun DrawScope.drawCircularWaveform(
    rotation: Float,
    pulse: Float,
    color: Color,
) {
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val baseRadius = size.height.coerceAtMost(size.width) * 0.3f * pulse

    val points = 60
    val path = Path()

    for (i in 0..points) {
        val angle = (i.toFloat() / points * 360f + rotation) * Math.PI.toFloat() / 180f
        val radiusVariation = sin((i.toFloat() / points * 8 + rotation / 60f) * 2 * Math.PI).toFloat()
        val radius = baseRadius + radiusVariation * 10f

        val x = centerX + radius * kotlin.math.cos(angle.toDouble()).toFloat()
        val y = centerY + radius * kotlin.math.sin(angle.toDouble()).toFloat()

        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()

    drawPath(
        path = path,
        color = color.copy(alpha = 0.6f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 3.dp.toPx(),
        ),
    )
}

/**
 * Available visualizer styles
 */
enum class VisualizerStyle {
    WAVEFORM,
    SPECTRUM,
    CIRCULAR,
}
