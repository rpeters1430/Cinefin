@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.rpeters.jellyfin.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.OptInAppExperimentalApis

/**
 * Material 3 Expressive Pull-to-Refresh container with customizable wavy indicator
 *
 * Wraps content in a PullToRefreshBox with an expressive wavy loading indicator that follows
 * Material 3 Expressive design guidelines including smooth motion and dynamic color support.
 *
 * @param isRefreshing Whether content is currently refreshing
 * @param onRefresh Callback invoked when user triggers refresh gesture
 * @param modifier Modifier for the container
 * @param state Pull-to-refresh state for advanced customization
 * @param enabled Whether pull-to-refresh is enabled
 * @param indicatorColor Color of the refresh indicator (defaults to primary)
 * @param indicatorSize Size of the refresh indicator
 * @param useWavyIndicator Whether to use the wavy expressive indicator (defaults to true)
 * @param content Scrollable content to wrap
 */
@OptInAppExperimentalApis
@Composable
fun ExpressivePullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    enabled: Boolean = true,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    indicatorSize: Dp = 48.dp,
    useWavyIndicator: Boolean = true,
    content: @Composable () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            if (useWavyIndicator) {
                ExpressiveWavyPullToRefreshIndicator(
                    state = state,
                    isRefreshing = isRefreshing,
                    color = indicatorColor,
                    size = indicatorSize,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            } else {
                ExpressivePullToRefreshIndicator(
                    state = state,
                    isRefreshing = isRefreshing,
                    color = indicatorColor,
                    size = indicatorSize,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        },
    ) {
        content()
    }
}

/**
 * Expressive refresh indicator with smooth animations
 *
 * @param state Pull-to-refresh state
 * @param isRefreshing Whether currently refreshing
 * @param color Color of the indicator
 * @param size Size of the indicator
 * @param modifier Modifier for the indicator
 */
@OptInAppExperimentalApis
@Composable
fun ExpressivePullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            rotation.animateTo(
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 1000,
                        easing = LinearEasing,
                    ),
                    repeatMode = RepeatMode.Restart,
                ),
            )
        } else {
            rotation.snapTo(0f)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (isRefreshing || state.distanceFraction > 0f) {
            CircularWavyProgressIndicator(
                progress = { if (isRefreshing) 0.8f else state.distanceFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .size(size)
                    .rotate(rotation.value),
                color = color,
                trackColor = color.copy(alpha = 0.2f),
                amplitude = { 0.12f },
                wavelength = 32.dp,
                waveSpeed = 16.dp,
            )
        }
    }
}

/**
 * Simplified Expressive Pull-to-Refresh with minimal parameters
 *
 * Uses Material 3 default indicator with expressive motion
 *
 * @param isRefreshing Whether content is currently refreshing
 * @param onRefresh Callback invoked when user triggers refresh gesture
 * @param modifier Modifier for the container
 * @param enabled Whether pull-to-refresh is enabled
 * @param content Scrollable content to wrap
 */
@OptInAppExperimentalApis
@Composable
fun ExpressivePullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        content()
    }
}

/**
 * Expressive wavy pull-to-refresh indicator
 *
 * Uses Material 3 wavy circular progress indicator for more engaging refresh animation
 *
 * @param state Pull-to-refresh state
 * @param isRefreshing Whether currently refreshing
 * @param color Color of the indicator
 * @param size Size of the indicator
 * @param modifier Modifier for the indicator
 */
@OptInAppExperimentalApis
@Composable
fun ExpressiveWavyPullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (isRefreshing) {
            // Indeterminate wavy loading when refreshing
            CircularWavyProgressIndicator(
                modifier = Modifier.size(size),
                color = color,
                trackColor = color.copy(alpha = 0.2f),
                amplitude = 0.12f,
                wavelength = 32.dp,
                waveSpeed = 16.dp,
            )
        } else if (state.distanceFraction > 0f) {
            // Determinate wavy progress while pulling
            CircularWavyProgressIndicator(
                progress = { state.distanceFraction.coerceIn(0f, 1f) },
                modifier = Modifier.size(size),
                color = color,
                trackColor = color.copy(alpha = 0.2f),
                amplitude = { 0.12f },
                wavelength = 32.dp,
                waveSpeed = 16.dp,
            )
        }
    }
}

/**
 * Compact pull-to-refresh for smaller content areas
 *
 * Uses smaller indicator size for compact layouts
 *
 * @param isRefreshing Whether content is currently refreshing
 * @param onRefresh Callback invoked when user triggers refresh gesture
 * @param modifier Modifier for the container
 * @param enabled Whether pull-to-refresh is enabled
 * @param content Scrollable content to wrap
 */
@OptInAppExperimentalApis
@Composable
fun ExpressiveCompactPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        enabled = enabled,
        indicatorSize = 36.dp,
        useWavyIndicator = true,
        content = content,
    )
}
