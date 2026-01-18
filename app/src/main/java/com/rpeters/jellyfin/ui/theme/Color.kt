package com.rpeters.jellyfin.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// JELLYFIN BRAND COLORS
// ============================================================================

// Primary brand colors inspired by Jellyfin logo and interface
val JellyfinPurple80 = Color(0xFFBB86FC)
val JellyfinPurple90 = Color(0xFFD0BCFF)
val JellyfinBlue80 = Color(0xFF82B1FF)
val JellyfinBlue90 = Color(0xFFADCEFF)
val JellyfinTeal80 = Color(0xFF26A69A)
val JellyfinTeal90 = Color(0xFF80CBC4)

val JellyfinPurple40 = Color(0xFF6200EE)
val JellyfinPurple30 = Color(0xFF3700B3)
val JellyfinBlue40 = Color(0xFF2962FF)
val JellyfinBlue30 = Color(0xFF0039CB)
val JellyfinTeal40 = Color(0xFF00695C)
val JellyfinTeal30 = Color(0xFF004D40)

// ============================================================================
// NEUTRAL COLOR SYSTEM
// ============================================================================

// Neutral colors for expressive theming (Material 3 compliant)
val Neutral99 = Color(0xFFFFFBFF)
val Neutral95 = Color(0xFFF5F1F5)
val Neutral90 = Color(0xFFE6E1E6)
val Neutral80 = Color(0xFFCAC5CA)
val Neutral70 = Color(0xFFAEA9AE)
val Neutral60 = Color(0xFF938F93)
val Neutral50 = Color(0xFF787578)
val Neutral40 = Color(0xFF605D60)
val Neutral30 = Color(0xFF484548)
val Neutral20 = Color(0xFF302E30)
val Neutral10 = Color(0xFF1B1B1F)
val Neutral0 = Color(0xFF000000)

// ============================================================================
// SEMANTIC COLORS FOR MEDIA CONTENT
// ============================================================================

// Content type indicators
val MovieRed = Color(0xFFE53E3E)
val SeriesBlue = Color(0xFF3182CE)
val MusicGreen = Color(0xFF38A169)
val BookPurple = Color(0xFF805AD5)
val AudioBookOrange = Color(0xFFDD6B20)
val PhotoYellow = Color(0xFFD69E2E)

// Rating and quality indicators
val RatingGold = Color(0xFFFFD700)
val RatingSilver = Color(0xFFC0C0C0)
val RatingBronze = Color(0xFFCD7F32)
val Quality4K = Color(0xFF00FF00)
val Quality1440 = Color(0xFF00E5FF)
val QualityHD = Color(0xFF00BFFF)
val QualitySD = Color(0xFFFFA500)

// Status indicators
val StatusOnline = Color(0xFF48BB78)
val StatusOffline = Color(0xFFE53E3E)
val StatusPending = Color(0xFFFFA500)
val StatusError = Color(0xFFE53E3E)
val StatusWarning = Color(0xFFFFA500)
val StatusInfo = Color(0xFF3182CE)

// ============================================================================
// EXPRESSIVE COLOR SYSTEM
// ============================================================================

// Expressive primary colors (Material 3 Expressive)
val ExpressivePrimary = Color(0xFF6442D6)
val ExpressiveOnPrimary = Color(0xFFFFFFFF)
val ExpressivePrimaryContainer = Color(0xFFEADDFF)
val ExpressiveOnPrimaryContainer = Color(0xFF21005D)

// Expressive secondary colors
val ExpressiveSecondary = Color(0xFF625B71)
val ExpressiveOnSecondary = Color(0xFFFFFFFF)
val ExpressiveSecondaryContainer = Color(0xFFE8DEF8)
val ExpressiveOnSecondaryContainer = Color(0xFF1D192B)

// Expressive tertiary colors
val ExpressiveTertiary = Color(0xFF7D5260)
val ExpressiveOnTertiary = Color(0xFFFFFFFF)
val ExpressiveTertiaryContainer = Color(0xFFFFD8E4)
val ExpressiveOnTertiaryContainer = Color(0xFF31111D)

// Expressive error colors
val ExpressiveError = Color(0xFFBA1A1A)
val ExpressiveOnError = Color(0xFFFFFFFF)
val ExpressiveErrorContainer = Color(0xFFFFDAD6)
val ExpressiveOnErrorContainer = Color(0xFF410002)

// ============================================================================
// SURFACE AND BACKGROUND COLORS
// ============================================================================

// Surface colors for different elevation levels (Material 3 Expressive)
val SurfaceDim = Color(0xFFDED8E1)
val SurfaceBright = Color(0xFFFEF7FF)
val Surface0 = Color(0xFFFFFFFF)
val Surface1 = Color(0xFFF7F2FA)
val Surface2 = Color(0xFFF1ECF4)
val Surface3 = Color(0xFFEBE6EE)
val Surface4 = Color(0xFFE8E5E8)
val Surface5 = Color(0xFFE5E0E7)
val SurfaceContainerLowest = Surface0
val SurfaceContainerLow = Surface1
val SurfaceContainer = Surface2
val SurfaceContainerHigh = Surface3
val SurfaceContainerHighest = Surface5

// Background colors
val Background = Color(0xFFFEF7FF)
val OnBackground = Color(0xFF1C1B1F)
val BackgroundVariant = Color(0xFFE7E0EC)
val OnBackgroundVariant = Color(0xFF49454F)

// ============================================================================
// OUTLINE AND BORDER COLORS
// ============================================================================

val Outline = Color(0xFF79747E)
val OutlineVariant = Color(0xFFCAC4D0)
val OutlineLight = Color(0xFFE7E0EC)

// ============================================================================
// INTERACTIVE STATE COLORS
// ============================================================================

// Pressed states
val PressedPrimary = Color(0xFF6750A4)
val PressedSecondary = Color(0xFF625B71)
val PressedSurface = Color(0xFFE7E0EC)

// Focused states
val FocusedPrimary = Color(0xFF6750A4)
val FocusedSecondary = Color(0xFF625B71)
val FocusedSurface = Color(0xFFE7E0EC)

// Hovered states
val HoveredPrimary = Color(0xFF7F67BE)
val HoveredSecondary = Color(0xFF7A7284)
val HoveredSurface = Color(0xFFF1ECF4)

// ============================================================================
// MEDIA-SPECIFIC COLORS
// ============================================================================

// Video player controls
val VideoControlBackground = Color(0xCC000000)
val VideoControlForeground = Color(0xFFFFFFFF)
val VideoProgressBar = Color(0xFF6750A4)
val VideoProgressBarBackground = Color(0x66FFFFFF)

// Audio player colors
val AudioWaveform = Color(0xFF6750A4)
val AudioWaveformBackground = Color(0xFFE7E0EC)
val AudioControl = Color(0xFF6750A4)

// Image viewer colors
val ImageOverlay = Color(0x66000000)
val ImageControl = Color(0xFFFFFFFF)

// ============================================================================
// ACCESSIBILITY ENHANCED COLORS
// ============================================================================

// High contrast colors for accessibility
val HighContrastPrimary = Color(0xFF4F378B)
val HighContrastSecondary = Color(0xFF4A4458)
val HighContrastSurface = Color(0xFFE7E0EC)
val HighContrastOnSurface = Color(0xFF1C1B1F)

// Color blind friendly alternatives
val ColorBlindFriendlyRed = Color(0xFFD73027)
val ColorBlindFriendlyBlue = Color(0xFF4575B4)
val ColorBlindFriendlyGreen = Color(0xFF74ADD1)

// ============================================================================
// DYNAMIC COLOR SUPPORT
// ============================================================================

// These colors will be used when dynamic color is disabled
// They provide a fallback that still looks great
val FallbackPrimary = Color(0xFF6750A4)
val FallbackSecondary = Color(0xFF625B71)
val FallbackTertiary = Color(0xFF7D5260)
val FallbackError = Color(0xFFBA1A1A)

// ============================================================================
// UTILITY COLOR FUNCTIONS
// ============================================================================

/**
 * Get content type color based on media type
 */
fun getContentTypeColor(contentType: String?): Color {
    return when (contentType?.uppercase()) {
        "MOVIE" -> MovieRed
        "SERIES", "TVSHOW" -> SeriesBlue
        "MUSIC", "AUDIO" -> MusicGreen
        "BOOK" -> BookPurple
        "AUDIOBOOK" -> AudioBookOrange
        "PHOTO", "IMAGE" -> PhotoYellow
        else -> Neutral50
    }
}

/**
 * Get quality indicator color
 */
fun getQualityColor(quality: String?): Color {
    return when (quality?.uppercase()) {
        "4K", "UHD" -> Quality4K
        "1440P", "QHD" -> Quality1440
        "HD", "1080P", "720P" -> QualityHD
        "SD", "480P" -> QualitySD
        else -> Neutral50
    }
}

/**
 * Get status color
 */
fun getStatusColor(status: String?): Color {
    return when (status?.uppercase()) {
        "ONLINE", "AVAILABLE" -> StatusOnline
        "OFFLINE", "UNAVAILABLE" -> StatusOffline
        "PENDING", "LOADING" -> StatusPending
        "ERROR", "FAILED" -> StatusError
        "WARNING" -> StatusWarning
        "INFO" -> StatusInfo
        else -> Neutral50
    }
}

// ============================================================================
// HERO IMAGE GRADIENTS
// ============================================================================

/**
 * Gradient stops for hero image fade-to-background effect
 * Used in detail screens (Movie, TV Show) for seamless blending
 */
object HeroGradient {
    /** Number of gradient color stops for smooth blending */
    const val GRADIENT_STOPS = 7

    /** Alpha values for each gradient stop (0.0 = transparent, 1.0 = opaque) */
    val alphaStops = listOf(
        0.0f, // Fully transparent at top
        0.0f, // Still transparent
        0.0f, // Still transparent (60% down)
        0.3f, // Start fade
        0.7f, // Medium fade
        0.9f, // Almost solid
        1.0f, // Fully opaque at bottom
    )
}
