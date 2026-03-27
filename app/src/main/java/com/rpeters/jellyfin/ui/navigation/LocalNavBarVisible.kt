package com.rpeters.jellyfin.ui.navigation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

/**
 * CompositionLocal that carries a [MutableState<Boolean>] controlling the global nav bar's
 * visibility. [JellyfinApp] provides it; immersive screens write [false] on scroll-down and
 * [true] on scroll-up so the floating nav bar auto-hides during media browsing.
 *
 * [JellyfinApp] resets the value to [true] whenever the current destination changes, so stale
 * hidden state from a previous screen never bleeds through.
 */
val LocalNavBarVisible = compositionLocalOf<MutableState<Boolean>> {
    mutableStateOf(true)
}
