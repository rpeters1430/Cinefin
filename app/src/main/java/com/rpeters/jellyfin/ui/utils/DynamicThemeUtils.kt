package com.rpeters.jellyfin.ui.utils

import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracted color palette for rich dynamic background and accent styling.
 */
@Immutable
data class MediaColorPalette(
    val vibrant: Color,
    val darkVibrant: Color,
    val lightVibrant: Color,
    val muted: Color,
    val darkMuted: Color,
    val lightMuted: Color,
    val dominant: Color,
) {
    companion object {
        fun default(primary: Color, background: Color) = MediaColorPalette(
            vibrant = primary,
            darkVibrant = primary,
            lightVibrant = primary,
            muted = primary,
            darkMuted = background,
            lightMuted = primary,
            dominant = background
        )
    }
}

/**
 * Remembers a [MediaColorPalette] extracted from the given [imageUrl].
 * The extraction runs asynchronously on background threads (IO/Default dispatchers)
 * to avoid causing frame drops during list scrolling or page transition.
 */
@Composable
fun rememberMediaColorPalette(
    imageUrl: String?,
    defaultPrimary: Color,
    defaultBackground: Color
): MediaColorPalette {
    val context = LocalContext.current
    var paletteState by remember(imageUrl) {
        mutableStateOf(MediaColorPalette.default(defaultPrimary, defaultBackground))
    }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) return@LaunchedEffect
        
        withContext(Dispatchers.IO) {
            val loader = SingletonImageLoader.get(context)
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false) // Required for Palette to extract pixels
                .build()
                
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val drawable = result.image.asDrawable(context.resources)
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val palette = withContext(Dispatchers.Default) {
                        Palette.from(bitmap).generate()
                    }
                    val vibrant = palette.getVibrantColor(defaultPrimary.toArgb())
                    val darkVibrant = palette.getDarkVibrantColor(defaultPrimary.toArgb())
                    val lightVibrant = palette.getLightVibrantColor(defaultPrimary.toArgb())
                    val muted = palette.getMutedColor(defaultPrimary.toArgb())
                    val darkMuted = palette.getDarkMutedColor(defaultBackground.toArgb())
                    val lightMuted = palette.getLightMutedColor(defaultPrimary.toArgb())
                    val dominant = palette.getDominantColor(defaultBackground.toArgb())
                    
                    withContext(Dispatchers.Main) {
                        paletteState = MediaColorPalette(
                            vibrant = Color(vibrant),
                            darkVibrant = Color(darkVibrant),
                            lightVibrant = Color(lightVibrant),
                            muted = Color(muted),
                            darkMuted = Color(darkMuted),
                            lightMuted = Color(lightMuted),
                            dominant = Color(dominant)
                        )
                    }
                }
            }
        }
    }
    
    return paletteState
}
