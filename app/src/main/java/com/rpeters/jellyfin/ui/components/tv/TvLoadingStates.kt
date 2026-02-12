package com.rpeters.jellyfin.ui.components.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.ui.appShimmer
import com.rpeters.jellyfin.ui.components.ExpressiveCircularLoading
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

/**
 * Shimmer effect modifier for skeleton loading animations
 */
fun Modifier.shimmer(): Modifier = composed {
    appShimmer(
        baseColor = TvMaterialTheme.colorScheme.surfaceVariant,
        highlightColor = TvMaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    )
}

/**
 * TV-optimized skeleton card for loading states
 */
@Composable
fun TvSkeletonCard(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(240.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Skeleton poster
        Box(
            modifier = Modifier
                .size(240.dp, 360.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TvMaterialTheme.colorScheme.surfaceVariant)
                .shimmer(),
        )

        // Skeleton title
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(TvMaterialTheme.colorScheme.surfaceVariant)
                .shimmer(),
        )

        // Skeleton subtitle
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(TvMaterialTheme.colorScheme.surfaceVariant)
                .shimmer(),
        )
    }
}

/**
 * TV-optimized skeleton carousel for loading states
 */
@Composable
fun TvSkeletonCarousel(
    title: String = "Loading...",
    itemCount: Int = 6,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Skeleton title
        Row(
            modifier = Modifier.padding(start = 56.dp, top = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (title == "Loading...") {
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(TvMaterialTheme.colorScheme.surfaceVariant)
                        .shimmer(),
                )
            } else {
                TvText(
                    text = title,
                    style = TvMaterialTheme.typography.headlineLarge,
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(
                count = itemCount,
                key = { it },
                contentType = { "tv_skeleton_card" },
            ) {
                TvSkeletonCard()
            }
        }
    }
}

/**
 * TV-friendly error banner with large, readable text and proper focus handling
 */
@Composable
fun TvErrorBanner(
    title: String,
    message: String,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    icon: ImageVector = Icons.Default.Error,
    modifier: Modifier = Modifier,
) {
    TvCard(
        onClick = { /* Non-interactive card */ },
        colors = TvCardDefaults.colors(
            containerColor = TvMaterialTheme.colorScheme.errorContainer,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 56.dp, vertical = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TvIcon(
                imageVector = icon,
                contentDescription = null,
                tint = TvMaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(48.dp),
            )

            TvText(
                text = title,
                style = TvMaterialTheme.typography.headlineSmall,
                color = TvMaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
            )

            TvText(
                text = message,
                style = TvMaterialTheme.typography.bodyLarge,
                color = TvMaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
            )

            if (onRetry != null || onDismiss != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    onRetry?.let {
                        TvButton(
                            onClick = it,
                        ) {
                            TvText("Retry")
                        }
                    }

                    onDismiss?.let {
                        TvButton(
                            onClick = it,
                            colors = androidx.tv.material3.ButtonDefaults.colors(
                                containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
                                contentColor = TvMaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            TvText("Dismiss")
                        }
                    }
                }
            }
        }
    }
}

/**
 * TV-friendly warning banner for non-critical issues
 */
@Composable
fun TvWarningBanner(
    title: String,
    message: String,
    onAction: (() -> Unit)? = null,
    actionText: String = "OK",
    modifier: Modifier = Modifier,
) {
    TvCard(
        onClick = { /* Non-interactive card */ },
        colors = TvCardDefaults.colors(
            containerColor = TvMaterialTheme.colorScheme.tertiaryContainer,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 56.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TvIcon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = TvMaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(32.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TvText(
                    text = title,
                    style = TvMaterialTheme.typography.titleMedium,
                    color = TvMaterialTheme.colorScheme.onTertiaryContainer,
                )

                TvText(
                    text = message,
                    style = TvMaterialTheme.typography.bodyMedium,
                    color = TvMaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            onAction?.let {
                TvButton(
                    onClick = it,
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = TvMaterialTheme.colorScheme.tertiary,
                        contentColor = TvMaterialTheme.colorScheme.onTertiary,
                    ),
                ) {
                    TvText(actionText)
                }
            }
        }
    }
}

/**
 * Full-screen TV loading state with centered spinner and message
 */
@Composable
fun TvFullScreenLoading(
    message: String = "Loading...",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvMaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ExpressiveCircularLoading(
                size = 64.dp,
                color = TvMaterialTheme.colorScheme.primary,
            )

            TvText(
                text = message,
                style = TvMaterialTheme.typography.headlineSmall,
                color = TvMaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Empty state for TV screens when no content is available
 */
@Composable
fun TvEmptyState(
    title: String,
    message: String,
    onAction: (() -> Unit)? = null,
    actionText: String = "Refresh",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvMaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(56.dp),
        ) {
            TvText(
                text = title,
                style = TvMaterialTheme.typography.headlineMedium,
                color = TvMaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            TvText(
                text = message,
                style = TvMaterialTheme.typography.bodyLarge,
                color = TvMaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            onAction?.let {
                Spacer(modifier = Modifier.height(16.dp))
                TvButton(onClick = it) {
                    TvText(actionText)
                }
            }
        }
    }
}
