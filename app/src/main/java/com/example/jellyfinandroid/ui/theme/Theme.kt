package com.example.jellyfinandroid.ui.theme

import android.app.Activity
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

private val JellyfinDarkColorScheme = darkColorScheme(
    primary = JellyfinPurple80,
    onPrimary = Neutral10,
    primaryContainer = JellyfinPurple30,
    onPrimaryContainer = JellyfinPurple90,
    
    secondary = JellyfinBlue80,
    onSecondary = Neutral10,
    secondaryContainer = JellyfinBlue30,
    onSecondaryContainer = JellyfinBlue90,
    
    tertiary = JellyfinTeal80,
    onTertiary = Neutral10,
    tertiaryContainer = JellyfinTeal30,
    onTertiaryContainer = JellyfinTeal90,
    
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = Neutral30,
    onSurfaceVariant = Neutral80,
    
    outline = Neutral60,
    outlineVariant = Neutral30,
    scrim = Neutral0,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral20,
    inversePrimary = JellyfinPurple40,
)

private val JellyfinLightColorScheme = lightColorScheme(
    primary = JellyfinPurple40,
    onPrimary = Neutral99,
    primaryContainer = JellyfinPurple90,
    onPrimaryContainer = JellyfinPurple30,
    
    secondary = JellyfinBlue40,
    onSecondary = Neutral99,
    secondaryContainer = JellyfinBlue90,
    onSecondaryContainer = JellyfinBlue30,
    
    tertiary = JellyfinTeal40,
    onTertiary = Neutral99,
    tertiaryContainer = JellyfinTeal90,
    onTertiaryContainer = JellyfinTeal30,
    
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral95,
    onSurfaceVariant = Neutral30,
    
    outline = Neutral50,
    outlineVariant = Neutral80,
    scrim = Neutral0,
    inverseSurface = Neutral20,
    inverseOnSurface = Neutral95,
    inversePrimary = JellyfinPurple80,
)

@Composable
fun JellyfinAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> JellyfinDarkColorScheme
        else -> JellyfinLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}