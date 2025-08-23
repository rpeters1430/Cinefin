package com.rpeters.jellyfin.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Material 3 Expressive Light Color Scheme
private val JellyfinExpressiveLightColorScheme = lightColorScheme(
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
    scrim = Color(0x52000000),
    inverseSurface = Color(0xFF1C1B1F),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFFD0BCFF),
)

// Material 3 Expressive Dark Color Scheme
private val JellyfinExpressiveDarkColorScheme = darkColorScheme(
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
    scrim = Color(0x52000000),
    inverseSurface = Color(0xFFE6E1E6),
    inverseOnSurface = Color(0xFF1C1B1F),
    inversePrimary = Color(0xFF6750A4),
)

@Composable
fun JellyfinAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+ and works great with Expressive theming
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> JellyfinExpressiveDarkColorScheme
        else -> JellyfinExpressiveLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = JellyfinShapes,
        content = content,
    )
}
