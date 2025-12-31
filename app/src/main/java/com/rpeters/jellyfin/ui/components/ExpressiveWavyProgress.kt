@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.OptInAppExperimentalApis

/**
 * Material 3 Expressive Wavy Progress Indicators
 *
 * These components use the new wavy/expressive progress indicators introduced in Material 3 1.4.0+
 * They provide more engaging, fluid animations for loading states and progress tracking.
 */

/**
 * Expressive wavy linear progress indicator for determinate progress
 *
 * Perfect for video playback progress, download progress, or any tracked operation
 *
 * @param progress Current progress value between 0.0 and 1.0
 * @param modifier Modifier for the indicator
 * @param color Color of the progress wave (defaults to primary)
 * @param trackColor Color of the track behind the wave (defaults to surfaceVariant)
 * @param amplitude Function that calculates wave amplitude based on progress
 * @param wavelength Distance between wave peaks
 * @param waveSpeed Speed of the wave animation
 */
@OptInAppExperimentalApis
@Composable
fun ExpressiveWavyLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    amplitude: (Float) -> Float = { 0.15f },
    wavelength: Dp = 48.dp,
    waveSpeed: Dp = 24.dp,
) {
    LinearWavyProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier,
        color = color,
        trackColor = trackColor,
        amplitude = amplitude,
        wavelength = wavelength,
        waveSpeed = waveSpeed,
    )
}

/**
 * Expressive wavy linear progress indicator for indeterminate loading
 *
 * Perfect for refresh operations, data loading, or any unknown duration task
 *
 * @param modifier Modifier for the indicator
 * @param color Color of the progress wave (defaults to primary)
 * @param trackColor Color of the track behind the wave (defaults to surfaceVariant)
 * @param amplitude Wave amplitude value
 * @param wavelength Distance between wave peaks
 * @param waveSpeed Speed of the wave animation
 */
@OptInAppExperimentalApis
@Composable
fun ExpressiveWavyLinearLoading(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    amplitude: Float = 0.15f,
    wavelength: Dp = 48.dp,
    waveSpeed: Dp = 24.dp,
) {
    LinearWavyProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = trackColor,
        amplitude = amplitude,
        wavelength = wavelength,
        waveSpeed = waveSpeed,
    )
}

/**
 * Expressive wavy circular progress indicator for determinate progress
 *
 * Perfect for download progress, upload progress, or circular timers
 *
 * @param progress Current progress value between 0.0 and 1.0
 * @param modifier Modifier for the indicator
 * @param color Color of the progress wave (defaults to primary)
 * @param trackColor Color of the track behind the wave (defaults to surfaceVariant)
 * @param amplitude Function that calculates wave amplitude based on progress
 * @param wavelength Distance between wave peaks along the circle
 * @param waveSpeed Speed of the wave animation
 */
@OptInAppExperimentalApis
@Composable
fun ExpressiveWavyCircularProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    amplitude: (Float) -> Float = { 0.1f },
    wavelength: Dp = 32.dp,
    waveSpeed: Dp = 16.dp,
) {
    CircularWavyProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier,
        color = color,
        trackColor = trackColor,
        amplitude = amplitude,
        wavelength = wavelength,
        waveSpeed = waveSpeed,
    )
}

/**
 * Expressive wavy circular progress indicator for indeterminate loading
 *
 * Perfect for full-screen loading, splash screens, or any unknown duration task
 *
 * @param modifier Modifier for the indicator
 * @param color Color of the progress wave (defaults to primary)
 * @param trackColor Color of the track behind the wave (defaults to surfaceVariant)
 * @param amplitude Wave amplitude value
 * @param wavelength Distance between wave peaks along the circle
 * @param waveSpeed Speed of the wave animation
 */
@OptInAppExperimentalApis
@Composable
fun ExpressiveWavyCircularLoading(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    amplitude: Float = 0.1f,
    wavelength: Dp = 32.dp,
    waveSpeed: Dp = 16.dp,
) {
    CircularWavyProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = trackColor,
        amplitude = amplitude,
        wavelength = wavelength,
        waveSpeed = waveSpeed,
    )
}

/**
 * Video player progress bar with expressive wavy style
 *
 * Designed specifically for video playback tracking with smooth wave animations
 * that make progress more engaging and visible
 *
 * @param currentPosition Current playback position in seconds
 * @param duration Total video duration in seconds
 * @param modifier Modifier for the progress bar
 * @param color Color of the played portion (defaults to primary)
 * @param trackColor Color of the remaining portion (defaults to surfaceVariant)
 * @param waveAmplitude Amplitude of the wave (higher = more pronounced)
 */
@OptInAppExperimentalApis
@Composable
fun VideoPlayerWavyProgressBar(
    currentPosition: Long,
    duration: Long,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    waveAmplitude: Float = 0.12f,
) {
    val progress = if (duration > 0) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    ExpressiveWavyLinearProgress(
        progress = progress,
        modifier = modifier.fillMaxWidth(),
        color = color,
        trackColor = trackColor,
        amplitude = { waveAmplitude },
        wavelength = 64.dp,
        waveSpeed = 32.dp,
    )
}

/**
 * Compact wavy loading indicator for inline use
 *
 * Smaller circular wavy loader for buttons, cards, or inline loading states
 *
 * @param modifier Modifier for the indicator
 * @param size Size of the circular indicator
 * @param color Color of the wave (defaults to onSurface)
 */
@OptInAppExperimentalApis
@Composable
fun CompactWavyLoading(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        ExpressiveWavyCircularLoading(
            modifier = Modifier.size(size),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
            amplitude = 0.08f,
            wavelength = 24.dp,
            waveSpeed = 12.dp,
        )
    }
}

/**
 * Full-screen wavy loading state
 *
 * Large centered wavy circular indicator for full-screen loading
 *
 * @param modifier Modifier for the container
 * @param message Optional loading message to display below indicator
 */
@OptInAppExperimentalApis
@Composable
fun FullScreenWavyLoading(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (message != null) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            ) {
                ExpressiveWavyCircularLoading(
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    amplitude = 0.15f,
                )
                androidx.compose.material3.Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            ExpressiveWavyCircularLoading(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                amplitude = 0.15f,
            )
        }
    }
}
