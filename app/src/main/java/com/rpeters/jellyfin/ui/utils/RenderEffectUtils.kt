package com.rpeters.jellyfin.ui.utils

import android.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * Utility to convert Android framework RenderEffect to Compose-friendly RenderEffect.
 * Supports API 31+ (Android 12).
 */
fun asComposeRenderEffect(renderEffect: RenderEffect): androidx.compose.ui.graphics.RenderEffect {
    return renderEffect.asComposeRenderEffect()
}
