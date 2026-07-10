@file:OptInAppExperimentalApis

package com.rpeters.jellyfin.ui.components.immersive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.theme.Dimens
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens

/**
 * Multi-button floating action button group for immersive screens.
 * Supports both vertical and horizontal layouts.
 */
@Composable
fun FloatingActionGroup(
    modifier: Modifier = Modifier,
    orientation: FabOrientation = FabOrientation.Vertical,
    visible: Boolean = true,
    primaryAction: FabAction? = null,
    secondaryActions: List<FabAction> = emptyList(),
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        when (orientation) {
            FabOrientation.Vertical -> {
                Column(
                    modifier = modifier,
                    verticalArrangement = Arrangement.spacedBy(ImmersiveDimens.FabSpacing),
                    horizontalAlignment = Alignment.End,
                ) {
                    secondaryActions.forEach { action ->
                        SmallFloatingActionButton(
                            onClick = action.onClick,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.contentDescription,
                            )
                        }
                    }

                    primaryAction?.let { action ->
                        MorphingPrimaryFab(action = action)
                    }
                }
            }

            FabOrientation.Horizontal -> {
                Row(
                    modifier = modifier,
                    horizontalArrangement = Arrangement.spacedBy(ImmersiveDimens.FabSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    primaryAction?.let { action ->
                        MorphingPrimaryFab(action = action)
                    }

                    secondaryActions.forEach { action ->
                        SmallFloatingActionButton(
                            onClick = action.onClick,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.contentDescription,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Primary FAB that morphs from a circle to a soft polygon while pressed.
 * [progress] is read as a State inside createOutline (not destructured with `by`) so the
 * shape animates on every draw frame without triggering full recomposition.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MorphingPrimaryFab(action: FabAction) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress = animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "fab_morph_progress",
    )
    val morphShape = remember(morph) {
        object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density,
            ): Outline {
                val path: Path = morph.toPath(progress = progress.value)
                val scaleMatrix = Matrix().apply { scale(x = size.width, y = size.height) }
                path.transform(scaleMatrix)
                path.translate(size.center - path.getBounds().center)
                return Outline.Generic(path)
            }
        }
    }

    FloatingActionButton(
        onClick = action.onClick,
        shape = morphShape,
        interactionSource = interactionSource,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = Dimens.Spacing8,
        ),
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.contentDescription,
        )
    }
}

data class FabAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
)

enum class FabOrientation {
    Vertical,
    Horizontal,
}
