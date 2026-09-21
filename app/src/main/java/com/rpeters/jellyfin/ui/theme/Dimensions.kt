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
    // Hero section heights (density pass: 480 -> 300, plus a collapsed state)
    val HeroHeightPhone = 300.dp
    val HeroHeightCollapsed = 104.dp
    val HeroHeightTablet = 600.dp
    val HeroHeightTV = 720.dp

    // Detail backdrop heights (density pass: 580 -> 200, plus a collapsed state)
    val DetailBackdropHeight = 200.dp
    val DetailBackdropCollapsed = 56.dp

    // Poster card dimensions (density pass: 200x300 -> 88x132)
    val PosterCardWidth = 88.dp
    val PosterCardHeight = 132.dp

    // Continue watching card dimensions (density pass: 320x180 -> 200x112)
    val ContinueCardWidth = 200.dp
    val ContinueThumbHeight = 112.dp

    // Library grid cell (new: 115 wide x 173 tall, 2:3 poster)
    val LibraryGridCellWidth = 115.dp
    val LibraryGridCellHeight = 173.dp

    // Episode thumb dimensions (density pass: 100x56 -> 80x45)
    val EpisodeThumbWidth = 80.dp
    val EpisodeThumbHeight = 45.dp

    // Card dimensions (larger than expressive cards) - retained for non-density screens
    val CardWidthXSmall = 130.dp
    val CardWidthSmall = 200.dp // Increased from 176dp in usage or 200dp default
    val CardWidthMedium = 320.dp // Increased from 280dp
    val CardWidthLarge = 440.dp // Increased from 400dp
    val CardHeightXSmall = 195.dp
    val CardHeightSmall = 300.dp
    val CardHeightMedium = 480.dp // Adjusted for 320dp width (2:3 aspect would be 480)
    val CardHeightLarge = 660.dp

    // Spacing (tighter for immersive layouts)
    val SpacingRowTight = 16.dp
    val SpacingRowMedium = 20.dp
    val SpacingContentPadding = 24.dp
    val SpacingSectionVertical = 32.dp

    // Density-pass row gaps: 8dp between posters, 10dp between grid/continue-watching cards
    val CardGapPoster = 8.dp
    val CardGapGrid = 10.dp
    val ScreenPadding = 16.dp

    // Border radius
    val CornerRadiusCinematic = 12.dp
    val CornerRadiusCard = 8.dp
    val CornerRadiusSmall = 4.dp

    // Material 3 Expressive corner scale (matches ShapeTokens.CornerLargeIncreased / CornerMedium)
    val CardCornerRadius = 20.dp
    val RatingBadgeCornerRadius = 12.dp

    // Overlay gradients
    val GradientHeightHero = 200.dp
    val GradientHeightCard = 120.dp

    // FAB dimensions
    val FabSize = 56.dp
    val FabSpacing = 16.dp
    val FabBottomOffset = 80.dp

    // Cast Member dimensions
    val CastMemberWidth = 128.dp
    val CastMemberImageSize = 108.dp

    // Density-pass chip rail (e.g. Home library rail, TV season rail)
    val ChipRailHeight = 36.dp
    val ChipRailHorizontalPadding = 14.dp
}
