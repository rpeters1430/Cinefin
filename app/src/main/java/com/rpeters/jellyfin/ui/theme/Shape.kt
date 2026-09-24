package com.rpeters.jellyfin.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Shapes system
 * Following Material Design 3 shape tokens for visual hierarchy
 */
object ShapeTokens {

    // Corner radius tokens
    val CornerExtraSmall = 4.dp
    val CornerSmall = 8.dp
    val CornerMedium = 12.dp
    val CornerLarge = 16.dp
    val CornerSheetDialog = 24.dp
    val CornerExtraLarge = 28.dp
    val CornerFull = 50.dp

    // Shape families
    val ExtraSmall: CornerBasedShape = RoundedCornerShape(CornerExtraSmall)
    val Small: CornerBasedShape = RoundedCornerShape(CornerSmall)
    val Medium: CornerBasedShape = RoundedCornerShape(CornerMedium)
    val Large: CornerBasedShape = RoundedCornerShape(CornerLarge)
    val ExtraLarge: CornerBasedShape = RoundedCornerShape(CornerExtraLarge)
    val Full: CornerBasedShape = RoundedCornerShape(CornerFull)

    // Component-specific shapes (DESIGN.md Section 4)
    val ButtonShape = Full // Full pill buttons per DESIGN.md
    val CardShape = Small // 8dp for poster and thumbnail cards per DESIGN.md
    val DialogShape = RoundedCornerShape(CornerSheetDialog) // 24dp for dialogs per DESIGN.md
    val FabShape = Large
    val ChipShape = Small // 8dp for chips and badges per DESIGN.md
    val BottomSheetShape = RoundedCornerShape(topStart = CornerSheetDialog, topEnd = CornerSheetDialog) // 24dp top per DESIGN.md
    val ModalShape = RoundedCornerShape(CornerSheetDialog)

    // Media content shapes (DESIGN.md Section 4)
    val PosterShape = Small // 8dp for movie/TV posters
    val ThumbnailShape = Small // 8dp for episode thumbnails
    val AvatarShape = Full // For user avatars
    val LibraryIconShape = Large // For library type icons
}

/**
 * Material 3 Shapes following the new shape scale
 */
val JellyfinShapes = Shapes(
    extraSmall = ShapeTokens.ExtraSmall,
    small = ShapeTokens.Small,
    medium = ShapeTokens.Medium,
    large = ShapeTokens.Large,
    extraLarge = ShapeTokens.ExtraLarge,
)

/**
 * Expressive corner scale for the immersive (Netflix-style) UI layer.
 * Values match Material 3's own ShapeDefaults.largeIncreased/medium dp values.
 */
object ImmersiveShapes {
    val Card: CornerBasedShape = RoundedCornerShape(ImmersiveDimens.CardCornerRadius)
    val RatingBadge: CornerBasedShape = RoundedCornerShape(ImmersiveDimens.RatingBadgeCornerRadius)

    // Density-pass poster card (rails: Next Up, recently added, more like this, library grid)
    val PosterImage: CornerBasedShape = RoundedCornerShape(8.dp)
    val PosterRatingBadge: CornerBasedShape = RoundedCornerShape(8.dp)
    val ContinueThumb: CornerBasedShape = RoundedCornerShape(12.dp)
}
