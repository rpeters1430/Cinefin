@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.rpeters.jellyfin.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal to provide the [SharedTransitionScope] to the entire app.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * CompositionLocal to provide the [AnimatedVisibilityScope] for the current navigation destination.
 */
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }
