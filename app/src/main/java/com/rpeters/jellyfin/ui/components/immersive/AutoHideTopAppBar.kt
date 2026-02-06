package com.rpeters.jellyfin.ui.components.immersive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Auto-hiding translucent top app bar for immersive layouts.
 * Hides on scroll down, shows on scroll up.
 *
 * @param visible Whether the app bar should be visible
 * @param title Title text to display
 * @param navigationIcon Leading navigation icon content
 * @param actions Trailing action icons content
 * @param translucent Whether to use a translucent background (default true)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoHideTopAppBar(
    visible: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    translucent: Boolean = true,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
            initialOffsetY = { -it },
        ),
        exit = slideOutVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
            targetOffsetY = { -it },
        ),
    ) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            colors = if (translucent) {
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
                )
            } else {
                TopAppBarDefaults.topAppBarColors()
            },
            windowInsets = WindowInsets.statusBars,
            modifier = modifier,
        )
    }
}

/**
 * Auto-hiding top app bar with scroll detection.
 * Automatically hides/shows based on scroll direction.
 *
 * @param scrollOffset Current scroll offset (increases as user scrolls down)
 * @param title Title text to display
 * @param navigationIcon Leading navigation icon content
 * @param actions Trailing action icons content
 * @param hideThreshold Scroll distance required to hide app bar (default 50dp)
 * @param translucent Whether to use a translucent background (default true)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollAwareTopAppBar(
    scrollOffset: Float,
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    hideThreshold: Float = 50f,
    translucent: Boolean = true,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    var previousScrollOffset by remember { mutableFloatStateOf(0f) }

    val visible by remember(scrollOffset) {
        derivedStateOf {
            val delta = scrollOffset - previousScrollOffset
            val shouldShow = when {
                scrollOffset < hideThreshold -> true // Always show at top
                delta < -10f -> true // Scrolling up
                delta > 10f -> false // Scrolling down
                else -> scrollOffset < hideThreshold // Maintain state unless at top
            }
            previousScrollOffset = scrollOffset
            shouldShow
        }
    }

    AutoHideTopAppBar(
        visible = visible,
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        translucent = translucent,
        scrollBehavior = scrollBehavior,
        modifier = modifier,
    )
}
