package com.example.jellyfinandroid.ui.theme

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
    val CornerExtraLarge = 28.dp
    val CornerFull = 50.dp
    
    // Shape families
    val ExtraSmall: CornerBasedShape = RoundedCornerShape(CornerExtraSmall)
    val Small: CornerBasedShape = RoundedCornerShape(CornerSmall)
    val Medium: CornerBasedShape = RoundedCornerShape(CornerMedium)
    val Large: CornerBasedShape = RoundedCornerShape(CornerLarge)
    val ExtraLarge: CornerBasedShape = RoundedCornerShape(CornerExtraLarge)
    val Full: CornerBasedShape = RoundedCornerShape(CornerFull)
    
    // Component-specific shapes
    val ButtonShape = Small
    val CardShape = Medium
    val DialogShape = ExtraLarge
    val FabShape = Large
    val ChipShape = Small
    val BottomSheetShape = ExtraLarge
    val ModalShape = ExtraLarge
    
    // Media content shapes
    val PosterShape = Medium // For movie/TV posters
    val ThumbnailShape = Small // For episode thumbnails
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
    extraLarge = ShapeTokens.ExtraLarge
)
