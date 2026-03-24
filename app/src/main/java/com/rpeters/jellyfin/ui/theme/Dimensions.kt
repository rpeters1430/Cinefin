package com.rpeters.jellyfin.ui.theme

import androidx.compose.ui.unit.dp

object Dimens {
    val Spacing4 = 4.dp
    val Spacing6 = 6.dp
    val Spacing8 = 8.dp
    val Spacing10 = 10.dp
    val Spacing12 = 12.dp
    val Spacing16 = 16.dp
    val Spacing18 = 18.dp
    val Spacing24 = 24.dp
    val Spacing32 = 32.dp
    val Spacing56 = 56.dp

    val Size16 = 16.dp
    val Size18 = 18.dp
    val Size64 = 64.dp
    val Height200 = 200.dp

    val Corner6 = 6.dp
}

object ImmersiveDimens {
    // Hero section heights
    val HeroHeightPhone = 480.dp
    val HeroHeightTablet = 600.dp
    val HeroHeightTV = 720.dp

    // Card dimensions (larger than expressive cards)
    val CardWidthSmall = 200.dp // Increased from 176dp in usage or 200dp default
    val CardWidthMedium = 320.dp // Increased from 280dp
    val CardWidthLarge = 440.dp // Increased from 400dp
    val CardHeightSmall = 300.dp
    val CardHeightMedium = 480.dp // Adjusted for 320dp width (2:3 aspect would be 480)
    val CardHeightLarge = 660.dp

    // Spacing (tighter for immersive layouts)
    val SpacingRowTight = 16.dp
    val SpacingRowMedium = 20.dp
    val SpacingContentPadding = 24.dp
    val SpacingSectionVertical = 32.dp

    // Border radius
    val CornerRadiusCinematic = 12.dp
    val CornerRadiusCard = 8.dp
    val CornerRadiusSmall = 4.dp

    // Overlay gradients
    val GradientHeightHero = 200.dp
    val GradientHeightCard = 120.dp

    // FAB dimensions
    val FabSize = 56.dp
    val FabSpacing = 16.dp
    val FabBottomOffset = 80.dp

    // Cast Member dimensions
    val CastMemberWidth = 100.dp
    val CastMemberImageSize = 80.dp
}
