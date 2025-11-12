package com.rpeters.jellyfin.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.rpeters.jellyfin.data.preferences.AccentColor

/**
 * Generates a light color scheme based on the selected accent color.
 */
fun getLightColorScheme(accentColor: AccentColor): androidx.compose.material3.ColorScheme {
    return when (accentColor) {
        AccentColor.JELLYFIN_PURPLE -> JellyfinPurpleLightColorScheme
        AccentColor.JELLYFIN_BLUE -> JellyfinBlueLightColorScheme
        AccentColor.JELLYFIN_TEAL -> JellyfinTealLightColorScheme
        AccentColor.MATERIAL_PURPLE -> MaterialPurpleLightColorScheme
        AccentColor.MATERIAL_BLUE -> MaterialBlueLightColorScheme
        AccentColor.MATERIAL_GREEN -> MaterialGreenLightColorScheme
        AccentColor.MATERIAL_RED -> MaterialRedLightColorScheme
        AccentColor.MATERIAL_ORANGE -> MaterialOrangeLightColorScheme
    }
}

/**
 * Generates a dark color scheme based on the selected accent color.
 */
fun getDarkColorScheme(accentColor: AccentColor): androidx.compose.material3.ColorScheme {
    return when (accentColor) {
        AccentColor.JELLYFIN_PURPLE -> JellyfinPurpleDarkColorScheme
        AccentColor.JELLYFIN_BLUE -> JellyfinBlueDarkColorScheme
        AccentColor.JELLYFIN_TEAL -> JellyfinTealDarkColorScheme
        AccentColor.MATERIAL_PURPLE -> MaterialPurpleDarkColorScheme
        AccentColor.MATERIAL_BLUE -> MaterialBlueDarkColorScheme
        AccentColor.MATERIAL_GREEN -> MaterialGreenDarkColorScheme
        AccentColor.MATERIAL_RED -> MaterialRedDarkColorScheme
        AccentColor.MATERIAL_ORANGE -> MaterialOrangeDarkColorScheme
    }
}

/**
 * Generates an AMOLED Black color scheme based on the selected accent color.
 * Uses pure black (#000000) for background to save battery on OLED screens.
 */
fun getAmoledBlackColorScheme(accentColor: AccentColor): androidx.compose.material3.ColorScheme {
    return when (accentColor) {
        AccentColor.JELLYFIN_PURPLE -> JellyfinPurpleAmoledColorScheme
        AccentColor.JELLYFIN_BLUE -> JellyfinBlueAmoledColorScheme
        AccentColor.JELLYFIN_TEAL -> JellyfinTealAmoledColorScheme
        AccentColor.MATERIAL_PURPLE -> MaterialPurpleAmoledColorScheme
        AccentColor.MATERIAL_BLUE -> MaterialBlueAmoledColorScheme
        AccentColor.MATERIAL_GREEN -> MaterialGreenAmoledColorScheme
        AccentColor.MATERIAL_RED -> MaterialRedAmoledColorScheme
        AccentColor.MATERIAL_ORANGE -> MaterialOrangeAmoledColorScheme
    }
}

// ============================================================================
// JELLYFIN PURPLE COLOR SCHEMES
// ============================================================================

private val JellyfinPurpleLightColorScheme = lightColorScheme(
    primary = ExpressivePrimary,
    onPrimary = ExpressiveOnPrimary,
    primaryContainer = ExpressivePrimaryContainer,
    onPrimaryContainer = ExpressiveOnPrimaryContainer,
    secondary = ExpressiveSecondary,
    onSecondary = ExpressiveOnSecondary,
    secondaryContainer = ExpressiveSecondaryContainer,
    onSecondaryContainer = ExpressiveOnSecondaryContainer,
    tertiary = ExpressiveTertiary,
    onTertiary = ExpressiveOnTertiary,
    tertiaryContainer = ExpressiveTertiaryContainer,
    onTertiaryContainer = ExpressiveOnTertiaryContainer,
    error = ExpressiveError,
    onError = ExpressiveOnError,
    errorContainer = ExpressiveErrorContainer,
    onErrorContainer = ExpressiveOnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = SurfaceContainerLowest,
    onSurface = OnBackground,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = OnBackgroundVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
)

private val JellyfinPurpleDarkColorScheme = darkColorScheme(
    primary = JellyfinPurple80,
    onPrimary = JellyfinPurple30,
    primaryContainer = JellyfinPurple40,
    onPrimaryContainer = JellyfinPurple90,
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E6),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E6),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
)

private val JellyfinPurpleAmoledColorScheme = darkColorScheme(
    primary = JellyfinPurple80,
    onPrimary = JellyfinPurple30,
    primaryContainer = JellyfinPurple40,
    onPrimaryContainer = JellyfinPurple90,
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF2A2731),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF3A1F2A),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5A0007),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE6E1E6),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE6E1E6),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF2A2A2A),
)

// ============================================================================
// JELLYFIN BLUE COLOR SCHEMES
// ============================================================================

private val JellyfinBlueLightColorScheme = lightColorScheme(
    primary = JellyfinBlue40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E6FF),
    onPrimaryContainer = JellyfinBlue30,
    secondary = Color(0xFF535F71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E3F8),
    onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFF6B5778),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF251431),
    error = ExpressiveError,
    onError = ExpressiveOnError,
    errorContainer = ExpressiveErrorContainer,
    onErrorContainer = ExpressiveOnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = SurfaceContainerLowest,
    onSurface = OnBackground,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = OnBackgroundVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
)

private val JellyfinBlueDarkColorScheme = darkColorScheme(
    primary = JellyfinBlue80,
    onPrimary = JellyfinBlue30,
    primaryContainer = JellyfinBlue40,
    onPrimaryContainer = JellyfinBlue90,
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253141),
    secondaryContainer = Color(0xFF3C4758),
    onSecondaryContainer = Color(0xFFD7E3F8),
    tertiary = Color(0xFFD6BEE5),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF523F5F),
    onTertiaryContainer = Color(0xFFF2DAFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C7CF),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
)

private val JellyfinBlueAmoledColorScheme = darkColorScheme(
    primary = JellyfinBlue80,
    onPrimary = JellyfinBlue30,
    primaryContainer = JellyfinBlue40,
    onPrimaryContainer = JellyfinBlue90,
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253141),
    secondaryContainer = Color(0xFF1F2933),
    onSecondaryContainer = Color(0xFFD7E3F8),
    tertiary = Color(0xFFD6BEE5),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF2A1F35),
    onTertiaryContainer = Color(0xFFF2DAFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5A0007),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFC3C7CF),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF2A2A2A),
)

// ============================================================================
// JELLYFIN TEAL COLOR SCHEMES
// ============================================================================

private val JellyfinTealLightColorScheme = lightColorScheme(
    primary = JellyfinTeal40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F3E8),
    onPrimaryContainer = JellyfinTeal30,
    secondary = Color(0xFF4A635F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E2),
    onSecondaryContainer = Color(0xFF05201C),
    tertiary = Color(0xFF4A6278),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD2E6FF),
    onTertiaryContainer = Color(0xFF031E31),
    error = ExpressiveError,
    onError = ExpressiveOnError,
    errorContainer = ExpressiveErrorContainer,
    onErrorContainer = ExpressiveOnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = SurfaceContainerLowest,
    onSurface = OnBackground,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = OnBackgroundVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
)

private val JellyfinTealDarkColorScheme = darkColorScheme(
    primary = JellyfinTeal80,
    onPrimary = JellyfinTeal30,
    primaryContainer = JellyfinTeal40,
    onPrimaryContainer = JellyfinTeal90,
    secondary = Color(0xFFB0CCC6),
    onSecondary = Color(0xFF1B3531),
    secondaryContainer = Color(0xFF324B47),
    onSecondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFFB5CAE2),
    onTertiary = Color(0xFF1E3347),
    tertiaryContainer = Color(0xFF354A5F),
    onTertiaryContainer = Color(0xFFD2E6FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF191C1B),
    onBackground = Color(0xFFE1E3E1),
    surface = Color(0xFF191C1B),
    onSurface = Color(0xFFE1E3E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBFC9C5),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4946),
)

private val JellyfinTealAmoledColorScheme = darkColorScheme(
    primary = JellyfinTeal80,
    onPrimary = JellyfinTeal30,
    primaryContainer = JellyfinTeal40,
    onPrimaryContainer = JellyfinTeal90,
    secondary = Color(0xFFB0CCC6),
    onSecondary = Color(0xFF1B3531),
    secondaryContainer = Color(0xFF152B28),
    onSecondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFFB5CAE2),
    onTertiary = Color(0xFF1E3347),
    tertiaryContainer = Color(0xFF172837),
    onTertiaryContainer = Color(0xFFD2E6FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5A0007),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE1E3E1),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE1E3E1),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFBFC9C5),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF2A2A2A),
)

// ============================================================================
// MATERIAL PURPLE, BLUE, GREEN, RED, ORANGE COLOR SCHEMES
// ============================================================================

private val MaterialPurpleLightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    error = ExpressiveError,
    onError = ExpressiveOnError,
    errorContainer = ExpressiveErrorContainer,
    onErrorContainer = ExpressiveOnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = SurfaceContainerLowest,
    onSurface = OnBackground,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = OnBackgroundVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
)

private val MaterialPurpleDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E6),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E6),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
)

private val MaterialPurpleAmoledColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF2A2731),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF3A1F2A),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5A0007),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE6E1E6),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE6E1E6),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF2A2A2A),
)

// Material Blue
private val MaterialBlueLightColorScheme = lightColorScheme(
    primary = Color(0xFF0061A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E3F7),
    onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFF6B5778),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF251431),
    error = ExpressiveError,
    onError = ExpressiveOnError,
    errorContainer = ExpressiveErrorContainer,
    onErrorContainer = ExpressiveOnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = SurfaceContainerLowest,
    onSurface = OnBackground,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = OnBackgroundVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
)

private val MaterialBlueDarkColorScheme = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3C4858),
    onSecondaryContainer = Color(0xFFD7E3F7),
    tertiary = Color(0xFFD6BEE4),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF523F5F),
    onTertiaryContainer = Color(0xFFF2DAFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE2E2E5),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E2E5),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C7CF),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
)

private val MaterialBlueAmoledColorScheme = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF1F2933),
    onSecondaryContainer = Color(0xFFD7E3F7),
    tertiary = Color(0xFFD6BEE4),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF2A1F35),
    onTertiaryContainer = Color(0xFFF2DAFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5A0007),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE2E2E5),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE2E2E5),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFC3C7CF),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF2A2A2A),
)

// Material Green
private val MaterialGreenLightColorScheme = lightColorScheme(
    primary = Color(0xFF006E1C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF93F990),
    onPrimaryContainer = Color(0xFF002203),
    secondary = Color(0xFF526350),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E8D0),
    onSecondaryContainer = Color(0xFF101F10),
    tertiary = Color(0xFF39656B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBCEBF2),
    onTertiaryContainer = Color(0xFF001F24),
    error = ExpressiveError,
    onError = ExpressiveOnError,
    errorContainer = ExpressiveErrorContainer,
    onErrorContainer = ExpressiveOnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = SurfaceContainerLowest,
    onSurface = OnBackground,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = OnBackgroundVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
)

private val MaterialGreenDarkColorScheme = darkColorScheme(
    primary = Color(0xFF77DC77),
    onPrimary = Color(0xFF00390A),
    primaryContainer = Color(0xFF005313),
    onPrimaryContainer = Color(0xFF93F990),
    secondary = Color(0xFFB9CCB4),
    onSecondary = Color(0xFF243424),
    secondaryContainer = Color(0xFF3A4B39),
    onSecondaryContainer = Color(0xFFD5E8D0),
    tertiary = Color(0xFFA1CED6),
    onTertiary = Color(0xFF00363D),
    tertiaryContainer = Color(0xFF1F4D53),
    onTertiaryContainer = Color(0xFFBCEBF2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1C19),
    onBackground = Color(0xFFE2E3DE),
    surface = Color(0xFF1A1C19),
    onSurface = Color(0xFFE2E3DE),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9C0),
    outline = Color(0xFF8B938A),
    outlineVariant = Color(0xFF414941),
)

private val MaterialGreenAmoledColorScheme = darkColorScheme(
    primary = Color(0xFF77DC77),
    onPrimary = Color(0xFF00390A),
    primaryContainer = Color(0xFF005313),
    onPrimaryContainer = Color(0xFF93F990),
    secondary = Color(0xFFB9CCB4),
    onSecondary = Color(0xFF243424),
    secondaryContainer = Color(0xFF1E2A1E),
    onSecondaryContainer = Color(0xFFD5E8D0),
    tertiary = Color(0xFFA1CED6),
    onTertiary = Color(0xFF00363D),
    tertiaryContainer = Color(0xFF142B30),
    onTertiaryContainer = Color(0xFFBCEBF2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5A0007),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE2E3DE),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE2E3DE),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFC1C9C0),
    outline = Color(0xFF8B938A),
    outlineVariant = Color(0xFF2A2A2A),
)

// Material Red
private val MaterialRedLightColorScheme = lightColorScheme(
    primary = Color(0xFFB3261E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF9DEDC),
    onPrimaryContainer = Color(0xFF410E0B),
    secondary = Color(0xFF775652),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD6),
    onSecondaryContainer = Color(0xFF2C1512),
    tertiary = Color(0xFF785930),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF2B1700),
    error = ExpressiveError,
    onError = ExpressiveOnError,
    errorContainer = ExpressiveErrorContainer,
    onErrorContainer = ExpressiveOnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = SurfaceContainerLowest,
    onSurface = OnBackground,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = OnBackgroundVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
)

private val MaterialRedDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE7BDB7),
    onSecondary = Color(0xFF442925),
    secondaryContainer = Color(0xFF5D3F3B),
    onSecondaryContainer = Color(0xFFFFDAD6),
    tertiary = Color(0xFFEFC388),
    onTertiary = Color(0xFF462A05),
    tertiaryContainer = Color(0xFF5E401A),
    onTertiaryContainer = Color(0xFFFFDDB8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF201A19),
    onBackground = Color(0xFFEDE0DD),
    surface = Color(0xFF201A19),
    onSurface = Color(0xFFEDE0DD),
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BF),
    outline = Color(0xFFA08C89),
    outlineVariant = Color(0xFF534341),
)

private val MaterialRedAmoledColorScheme = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE7BDB7),
    onSecondary = Color(0xFF442925),
    secondaryContainer = Color(0xFF36211E),
    onSecondaryContainer = Color(0xFFFFDAD6),
    tertiary = Color(0xFFEFC388),
    onTertiary = Color(0xFF462A05),
    tertiaryContainer = Color(0xFF3A2110),
    onTertiaryContainer = Color(0xFFFFDDB8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5A0007),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEDE0DD),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFEDE0DD),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFD8C2BF),
    outline = Color(0xFFA08C89),
    outlineVariant = Color(0xFF2A2A2A),
)

// Material Orange
private val MaterialOrangeLightColorScheme = lightColorScheme(
    primary = Color(0xFF825500),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB3),
    onPrimaryContainer = Color(0xFF291800),
    secondary = Color(0xFF6F5B40),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFADEBC),
    onSecondaryContainer = Color(0xFF271904),
    tertiary = Color(0xFF51643F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD4EABB),
    onTertiaryContainer = Color(0xFF102004),
    error = ExpressiveError,
    onError = ExpressiveOnError,
    errorContainer = ExpressiveErrorContainer,
    onErrorContainer = ExpressiveOnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = SurfaceContainerLowest,
    onSurface = OnBackground,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = OnBackgroundVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
)

private val MaterialOrangeDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB951),
    onPrimary = Color(0xFF452B00),
    primaryContainer = Color(0xFF633F00),
    onPrimaryContainer = Color(0xFFFFDDB3),
    secondary = Color(0xFFDDC2A1),
    onSecondary = Color(0xFF3E2D16),
    secondaryContainer = Color(0xFF56442A),
    onSecondaryContainer = Color(0xFFFADEBC),
    tertiary = Color(0xFFB8CEA1),
    onTertiary = Color(0xFF243515),
    tertiaryContainer = Color(0xFF3A4C2A),
    onTertiaryContainer = Color(0xFFD4EABB),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1F1B16),
    onBackground = Color(0xFFEAE1D9),
    surface = Color(0xFF1F1B16),
    onSurface = Color(0xFFEAE1D9),
    surfaceVariant = Color(0xFF4F4539),
    onSurfaceVariant = Color(0xFFD3C4B4),
    outline = Color(0xFF9C8F80),
    outlineVariant = Color(0xFF4F4539),
)

private val MaterialOrangeAmoledColorScheme = darkColorScheme(
    primary = Color(0xFFFFB951),
    onPrimary = Color(0xFF452B00),
    primaryContainer = Color(0xFF633F00),
    onPrimaryContainer = Color(0xFFFFDDB3),
    secondary = Color(0xFFDDC2A1),
    onSecondary = Color(0xFF3E2D16),
    secondaryContainer = Color(0xFF2F2519),
    onSecondaryContainer = Color(0xFFFADEBC),
    tertiary = Color(0xFFB8CEA1),
    onTertiary = Color(0xFF243515),
    tertiaryContainer = Color(0xFF1F2B17),
    onTertiaryContainer = Color(0xFFD4EABB),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5A0007),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEAE1D9),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFEAE1D9),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFD3C4B4),
    outline = Color(0xFF9C8F80),
    outlineVariant = Color(0xFF2A2A2A),
)
