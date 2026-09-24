package com.rpeters.jellyfin.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Cinefin spacing tokens following the 4dp base grid (DESIGN.md Section 4).
 * Allowed values: 4, 8, 12, 16, 24, 32, 48 dp.
 */
data class Spacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val mediumSmall: Dp = 12.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val huge: Dp = 48.dp,

    // Semantic spacing defined in DESIGN.md Section 4
    val screenHorizontalCompact: Dp = 16.dp,
    val screenHorizontalMedium: Dp = 24.dp,
    val cardGapInRow: Dp = 12.dp,
    val homeRowGap: Dp = 24.dp,
    val detailSectionGap: Dp = 32.dp,
)

/**
 * CompositionLocal providing Cinefin [Spacing] values.
 */
val LocalSpacing = staticCompositionLocalOf { Spacing() }

/**
 * Convenient accessor for [Spacing] through [androidx.compose.material3.MaterialTheme].
 */
val androidx.compose.material3.MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current
