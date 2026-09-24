package com.rpeters.jellyfin.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Retained for backwards compatibility.
 * Endless looping background glow pulses are eliminated per antislop R-19 / DESIGN.md Section 9.
 */
@Composable
fun Modifier.aiAura(
    enabled: Boolean = true,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.tertiary,
): Modifier {
    // Purged infinite loop transition (antislop R-19: no endless pulses or loops)
    return this
}
