package com.rpeters.jellyfin.ui.components.immersive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.HdrOn
import androidx.compose.material.icons.outlined.SurroundSound
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3 Expressive media quality badge with gradient background
 * Displays resolution badges (4K, 1440P, FHD, HD, SD) with beautiful styling
 */
@Composable
fun QualityBadge(
    resolution: ResolutionQuality,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    animate: Boolean = true,
) {
    val scale by animateFloatAsState(
        targetValue = if (animate) 1f else 0.95f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "quality_badge_scale"
    )

    Surface(
        modifier = modifier.scale(scale),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(
                        colors = resolution.gradientColors
                    )
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (showIcon) {
                    Icon(
                        imageVector = resolution.icon,
                        contentDescription = resolution.label,
                        modifier = Modifier.size(20.dp),
                        tint = resolution.iconTint
                    )
                }
                Text(
                    text = resolution.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = resolution.textColor,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/**
 * Premium HDR badge with vibrant gradient
 */
@Composable
fun HdrBadge(
    hdrType: HdrType = HdrType.HDR,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFF6B9D),
                            Color(0xFFC239B3),
                            Color(0xFF7928CA)
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.HdrOn,
                    contentDescription = hdrType.label,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White
                )
                Text(
                    text = hdrType.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/**
 * Dolby Atmos badge with signature gradient
 */
@Composable
fun AtmosBadge(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF1E3A8A), // Deep blue
                            Color(0xFF3B82F6), // Bright blue
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = "Dolby Atmos",
                    modifier = Modifier.size(18.dp),
                    tint = Color.White
                )
                Text(
                    text = "ATMOS",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 12.sp,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}

/**
 * Elegant codec badge with soft styling
 */
@Composable
fun CodecBadge(
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        tonalElevation = 1.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                letterSpacing = 0.3.sp
            )
        }
    }
}

/**
 * Premium media info card with Material 3 Expressive styling
 * Displays video or audio information with beautiful cards
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaInfoCard(
    title: String,
    icon: ImageVector,
    iconBackground: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon container with elevation
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconBackground,
                tonalElevation = 4.dp,
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Content column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    letterSpacing = 0.8.sp
                )
                content()
            }
        }
    }
}

/**
 * Complete video information display with all details
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoInfoCard(
    resolution: ResolutionQuality,
    codec: String,
    bitDepth: Int? = null,
    frameRate: Double? = null,
    isHdr: Boolean = false,
    hdrType: HdrType = HdrType.HDR,
    is3D: Boolean = false,
    modifier: Modifier = Modifier,
) {
    MediaInfoCard(
        title = "VIDEO",
        icon = Icons.Outlined.VideoFile,
        iconBackground = MaterialTheme.colorScheme.primaryContainer,
        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QualityBadge(resolution = resolution)

            if (isHdr) {
                HdrBadge(hdrType = hdrType)
            }

            CodecBadge(text = codec)

            bitDepth?.let {
                CodecBadge(text = "${it}-bit")
            }

            frameRate?.let {
                CodecBadge(
                    text = "${it.toInt()} FPS"
                )
            }

            if (is3D) {
                CodecBadge(text = "3D")
            }
        }
    }
}

/**
 * Complete audio information display with channel layout and codecs
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioInfoCard(
    channels: String,
    codec: String,
    isAtmos: Boolean = false,
    language: String? = null,
    modifier: Modifier = Modifier,
) {
    MediaInfoCard(
        title = "AUDIO",
        icon = Icons.Outlined.SurroundSound,
        iconBackground = MaterialTheme.colorScheme.tertiaryContainer,
        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (channels.isNotEmpty()) {
                CodecBadge(
                    text = channels,
                    icon = Icons.Outlined.SurroundSound
                )
            }

            CodecBadge(text = codec)

            if (isAtmos) {
                AtmosBadge()
            }

            language?.let {
                CodecBadge(text = it.uppercase())
            }
        }
    }
}

// ============================================================================
// Data Classes & Enums
// ============================================================================

/**
 * Resolution quality levels with Material 3 color palettes
 */
enum class ResolutionQuality(
    val label: String,
    val icon: ImageVector,
    val gradientColors: List<Color>,
    val textColor: Color,
    val iconTint: Color,
) {
    UHD_8K(
        label = "8K",
        icon = Icons.Rounded.HighQuality,
        gradientColors = listOf(
            Color(0xFFFFD700), // Gold
            Color(0xFFFFA500)  // Orange
        ),
        textColor = Color(0xFF000000),
        iconTint = Color(0xFF000000).copy(alpha = 0.8f)
    ),
    UHD_4K(
        label = "4K",
        icon = Icons.Rounded.HighQuality,
        gradientColors = listOf(
            Color(0xFFFF6B6B), // Coral red
            Color(0xFFEE5A6F)  // Rose
        ),
        textColor = Color.White,
        iconTint = Color.White
    ),
    QHD_1440P(
        label = "1440P",
        icon = Icons.Rounded.HighQuality,
        gradientColors = listOf(
            Color(0xFF667EEA), // Purple blue
            Color(0xFF764BA2)  // Purple
        ),
        textColor = Color.White,
        iconTint = Color.White
    ),
    FHD_1080P(
        label = "FHD",
        icon = Icons.Rounded.Hd,
        gradientColors = listOf(
            Color(0xFF4FACFE), // Light blue
            Color(0xFF00F2FE)  // Cyan
        ),
        textColor = Color.White,
        iconTint = Color.White
    ),
    HD_720P(
        label = "HD",
        icon = Icons.Rounded.Hd,
        gradientColors = listOf(
            Color(0xFF43E97B), // Green
            Color(0xFF38F9D7)  // Teal
        ),
        textColor = Color(0xFF000000),
        iconTint = Color(0xFF000000).copy(alpha = 0.7f)
    ),
    SD(
        label = "SD",
        icon = Icons.Rounded.SdCard,
        gradientColors = listOf(
            Color(0xFFBDBDBD), // Gray
            Color(0xFF9E9E9E)  // Darker gray
        ),
        textColor = Color.White,
        iconTint = Color.White
    );

    companion object {
        fun fromResolution(width: Int?, height: Int?): ResolutionQuality {
            val w = width ?: 0
            val h = height ?: 0
            return when {
                h >= 4320 || w >= 7680 -> UHD_8K
                h >= 2160 || w >= 3840 -> UHD_4K
                h >= 1440 || w >= 2560 -> QHD_1440P
                h >= 1080 || w >= 1920 -> FHD_1080P
                h >= 720 || w >= 1280 -> HD_720P
                else -> SD
            }
        }
    }
}

/**
 * HDR types with proper labeling
 */
enum class HdrType(val label: String) {
    HDR("HDR"),
    HDR10("HDR10"),
    HDR10_PLUS("HDR10+"),
    DOLBY_VISION("Dolby Vision"),
    HLG("HLG");

    companion object {
        fun detect(videoRange: String?, videoRangeType: String?): HdrType? {
            val range = videoRange?.lowercase() ?: ""
            val rangeType = videoRangeType?.lowercase() ?: ""

            return when {
                rangeType.contains("dovi") || range.contains("dolby") -> DOLBY_VISION
                rangeType.contains("hdr10+") || range.contains("hdr10+") -> HDR10_PLUS
                rangeType.contains("hdr10") || range.contains("hdr10") -> HDR10
                rangeType.contains("hlg") || range.contains("hlg") -> HLG
                rangeType.contains("hdr") || range.contains("hdr") -> HDR
                else -> null
            }
        }
    }
}
