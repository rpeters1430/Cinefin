package com.rpeters.jellyfin.ui.components.immersive

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Auto-hiding translucent top app bar for immersive layouts.
 * Uses graphicsLayer translation for smooth performance without layout jumps.
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
    val density = LocalDensity.current
    // Standard top app bar height is 64dp + status bars
    val appBarHeight = 64.dp
    val appBarHeightPx = with(density) { appBarHeight.toPx() }

    val translationY by animateFloatAsState(
        targetValue = if (visible) 0f else -appBarHeightPx * 1.5f, // Slide further up to hide shadow/glow
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "top_bar_translation",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.translationY = translationY
                // Reduce alpha as it slides away for extra smoothness
                this.alpha = if (translationY < -10f) {
                    (1f + translationY / appBarHeightPx).coerceIn(0f, 1f)
                } else {
                    1f
                }
            },
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
