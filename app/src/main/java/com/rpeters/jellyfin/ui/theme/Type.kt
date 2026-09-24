package com.rpeters.jellyfin.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rpeters.jellyfin.data.preferences.AppFont

import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.rpeters.jellyfin.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

// Cinefin Design System Fonts (DESIGN.md Section 3)
// 1. Barlow Semi Condensed for titles & headers (marquee feel, fits long titles)
val BarlowSemiCondensedFont = GoogleFont("Barlow Semi Condensed")
val BarlowSemiCondensedFontFamily = FontFamily(
    Font(googleFont = BarlowSemiCondensedFont, fontProvider = provider),
    Font(googleFont = BarlowSemiCondensedFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = BarlowSemiCondensedFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = BarlowSemiCondensedFont, fontProvider = provider, weight = FontWeight.Bold),
)

// 2. Atkinson Hyperlegible for body, overviews, and metadata (optimized for legibility in low light)
val AtkinsonHyperlegibleFont = GoogleFont("Atkinson Hyperlegible")
val AtkinsonHyperlegibleFontFamily = FontFamily(
    Font(googleFont = AtkinsonHyperlegibleFont, fontProvider = provider),
    Font(googleFont = AtkinsonHyperlegibleFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = AtkinsonHyperlegibleFont, fontProvider = provider, weight = FontWeight.Bold),
)

val RobotoFlexFont = GoogleFont("Roboto Flex")
val RobotoFlexFontFamily = FontFamily(
    Font(googleFont = RobotoFlexFont, fontProvider = provider)
)

val InterFont = GoogleFont("Inter")
val InterFontFamily = FontFamily(
    Font(googleFont = InterFont, fontProvider = provider)
)

val OutfitFont = GoogleFont("Outfit")
val OutfitFontFamily = FontFamily(
    Font(googleFont = OutfitFont, fontProvider = provider)
)

// Material 3 Expressive typography system for Cinefin
fun AppFont.toFontFamily(): FontFamily {
    return when (this) {
        AppFont.DEFAULT -> FontFamily.Default
        AppFont.SANS_SERIF -> FontFamily.SansSerif
        AppFont.SERIF -> FontFamily.Serif
        AppFont.MONOSPACE -> FontFamily.Monospace
        AppFont.ROBOTO_FLEX -> RobotoFlexFontFamily
        AppFont.INTER -> InterFontFamily
        AppFont.OUTFIT -> OutfitFontFamily
    }
}

/**
 * Returns the Cinefin typography system configured with [titleFamily] for titles/headers
 * and [bodyFamily] for body/metadata/labels (DESIGN.md Section 3).
 */
fun getTypography(
    titleFamily: FontFamily = BarlowSemiCondensedFontFamily,
    bodyFamily: FontFamily = AtkinsonHyperlegibleFontFamily,
): Typography {
    return Typography(
        // Display styles - for large, prominent text (Barlow SC)
        displayLarge = TextStyle(
            fontFamily = titleFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 57.sp,
            lineHeight = 64.sp,
            letterSpacing = (-0.25).sp,
        ),
        displayMedium = TextStyle(
            fontFamily = titleFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 45.sp,
            lineHeight = 52.sp,
            letterSpacing = 0.sp,
        ),
        // Detail screen title (when no logo image) - Barlow SC 36 / 40, weight 600
        displaySmall = TextStyle(
            fontFamily = titleFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 36.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.sp,
        ),

        // Headline styles - for section headings (Barlow SC)
        headlineLarge = TextStyle(
            fontFamily = titleFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            letterSpacing = 0.sp,
        ),
        // Screen titles (Library, Search) - Barlow SC 28 / 34, weight 600
        headlineMedium = TextStyle(
            fontFamily = titleFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            letterSpacing = 0.sp,
        ),
        // Section heads on detail screens - Barlow SC 24 / 30, weight 600
        headlineSmall = TextStyle(
            fontFamily = titleFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            letterSpacing = 0.sp,
        ),

        // Title styles
        // Home row titles - Barlow SC 22 / 28, weight 500
        titleLarge = TextStyle(
            fontFamily = titleFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
        // Card titles, list item titles - Atkinson 16 / 22, weight 700
        titleMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.15.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
        // Episode titles in lists - Atkinson 14 / 20, weight 700
        titleSmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),

        // Body styles - Atkinson Hyperlegible
        // Overviews, long text - Atkinson 16 / 24, weight 400
        bodyLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp,
        ),
        // Default body - Atkinson 14 / 20, weight 400
        bodyMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp,
        ),
        // Metadata (year, runtime, rating) - Atkinson 12 / 16, weight 400
        bodySmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp,
        ),

        // Label styles - Atkinson Hyperlegible
        // Buttons - Atkinson 14 / 20, weight 700
        labelLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
        // Chips, badges - Atkinson 12 / 16, weight 700
        labelMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
        labelSmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    )
}

/**
 * Convenience overload that maintains compatibility with user-selected fonts.
 * When [fontFamily] is default, uses the Cinefin dual Barlow SC / Atkinson pair.
 */
fun getTypography(fontFamily: FontFamily = FontFamily.Default): Typography {
    return if (fontFamily == FontFamily.Default) {
        getTypography(
            titleFamily = BarlowSemiCondensedFontFamily,
            bodyFamily = AtkinsonHyperlegibleFontFamily,
        )
    } else {
        getTypography(
            titleFamily = fontFamily,
            bodyFamily = fontFamily,
        )
    }
}

fun getTvTypography(fontFamily: FontFamily = FontFamily.Default): androidx.tv.material3.Typography {
    val typography = getTypography(fontFamily)
    return androidx.tv.material3.Typography(
        displayLarge = typography.displayLarge,
        displayMedium = typography.displayMedium,
        displaySmall = typography.displaySmall,
        headlineLarge = typography.headlineLarge,
        headlineMedium = typography.headlineMedium,
        headlineSmall = typography.headlineSmall,
        titleLarge = typography.titleLarge,
        titleMedium = typography.titleMedium,
        titleSmall = typography.titleSmall,
        bodyLarge = typography.bodyLarge,
        bodyMedium = typography.bodyMedium,
        bodySmall = typography.bodySmall,
        labelLarge = typography.labelLarge,
        labelMedium = typography.labelMedium,
        labelSmall = typography.labelSmall,
    )
}

val Typography = getTypography()
