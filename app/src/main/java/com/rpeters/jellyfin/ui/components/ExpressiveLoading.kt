package com.rpeters.jellyfin.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Material 3 Expressive Loading Indicators
 * Enhanced loading states with expressive animations
 */

/**
 * Expressive circular loading indicator with gradient and pulsing animation
 */
@Composable
fun ExpressiveCircularLoading(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp,
    showPulse: Boolean = true,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "circular_loading")
    val primary = MaterialTheme.colorScheme.primary
    val primaryAccent = primary.copy(alpha = 0.7f)

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotation),
        ) {
            val canvasWidth = size.value
            val canvasHeight = size.value
            val strokeWidthPx = strokeWidth.toPx()
            val radius = (minOf(canvasWidth, canvasHeight) / 2) - strokeWidthPx / 2

            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Transparent,
                        Color.Transparent,
                        Color.Transparent,
                        Color.Transparent,
                        Color.Transparent,
                        primary,
                        primaryAccent,
                    ),
                ),
                radius = radius,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidthPx,
                    cap = StrokeCap.Round,
                ),
            )
        }

        if (showPulse) {
            Surface(
                modifier = Modifier.size(size * 0.3f * pulse),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            ) {}
        }
    }
}

/**
 * Expressive linear loading with wave animation
 */
@Composable
fun ExpressiveLinearLoading(
    progress: Float? = null,
    modifier: Modifier = Modifier,
    showWave: Boolean = true,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "linear_loading")
    val waveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave_offset",
    )

    if (progress != null) {
        // Determinate progress
        LinearProgressIndicator(
            progress = { progress },
            modifier = modifier,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
    } else {
        // Indeterminate with wave effect
        Box(modifier = modifier) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )

            if (showWave) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                ) {
                    val width = size.width
                    val height = size.height
                    val waveLength = width / 3f
                    val amplitude = height / 4f

                    for (x in 0..width.toInt() step 2) {
                        val y = amplitude * sin((x / waveLength + waveOffset * 2) * Math.PI).toFloat()
                        drawCircle(
                            color = waveColor,
                            radius = 1.dp.toPx(),
                            center = Offset(x.toFloat(), height / 2 + y),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dots loading animation
 */
@Composable
fun ExpressiveDotsLoading(
    modifier: Modifier = Modifier,
    dotCount: Int = 3,
    dotSize: Dp = 8.dp,
    animationDelay: Int = 150,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots_loading")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(dotCount) { index ->
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = index * animationDelay,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot_scale_$index",
            )

            Surface(
                modifier = Modifier.size(dotSize * scale),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(
                    alpha = 0.5f + (scale - 0.5f) * 0.5f,
                ),
            ) {}
        }
    }
}

/**
 * Loading card for skeleton screens
 */
@Composable
fun ExpressiveLoadingCard(
    modifier: Modifier = Modifier,
    showImage: Boolean = true,
    showTitle: Boolean = true,
    showSubtitle: Boolean = true,
    imageHeight: Dp = 200.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer_loading")

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_offset",
    )

    Card(
        modifier = modifier.width(200.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column {
            if (showImage) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(imageHeight)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.surfaceVariant,
                                ),
                                start = Offset(shimmerOffset * 100, 0f),
                                end = Offset(shimmerOffset * 100 + 200f, 0f),
                            ),
                        ),
                )
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showTitle) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.surface,
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                    start = Offset(shimmerOffset * 50, 0f),
                                    end = Offset(shimmerOffset * 50 + 100f, 0f),
                                ),
                            ),
                    )
                }

                if (showSubtitle) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    ),
                                    start = Offset(shimmerOffset * 40, 0f),
                                    end = Offset(shimmerOffset * 40 + 80f, 0f),
                                ),
                            ),
                    )
                }
            }
        }
    }
}

/**
 * Full screen loading with message
 */
@Composable
fun ExpressiveFullScreenLoading(
    message: String = "Loading...",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ExpressiveCircularLoading(size = 64.dp)

            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            ExpressiveDotsLoading()
        }
    }
}

/**
 * Media loading indicator with progress
 */
@Composable
fun ExpressiveMediaLoading(
    title: String,
    progress: Float,
    subtitle: String = "",
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ExpressiveLinearLoading(
                progress = progress,
                showWave = false,
            )

            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
