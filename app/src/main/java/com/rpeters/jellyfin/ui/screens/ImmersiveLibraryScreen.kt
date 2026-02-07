package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.EmptyStateComposable
import com.rpeters.jellyfin.ui.components.EmptyStateType
import com.rpeters.jellyfin.ui.components.MiniPlayer
import com.rpeters.jellyfin.ui.components.shimmer
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.utils.getItemKey
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Immersive version of LibraryScreen with:
 * - Floating action buttons instead of top app bar
 * - Larger library cards with gradient backgrounds
 * - Tighter spacing for cinematic feel
 * - Material 3 Expressive animations
 * - Edge-to-edge layout with system bar padding
 */
@OptInAppExperimentalApis
@Composable
fun ImmersiveLibraryScreen(
    libraries: List<BaseItemDto>,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    onLibraryClick: (BaseItemDto) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onNowPlayingClick: () -> Unit = {},
    showBackButton: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Auto-hide FABs when scrolling down
    val showFabs by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 100
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Main content
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 24.dp,
                    bottom = 120.dp, // Extra space for MiniPlayer + FABs
                ),
                verticalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight),
            ) {
                // Header
                item(key = "header") {
                    Text(
                        text = stringResource(id = R.string.your_libraries),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }

                // Content states
                when {
                    isLoading && errorMessage == null && libraries.isEmpty() -> {
                        items(ImmersiveLibraryScreenDefaults.LibraryPlaceholderCount) {
                            ImmersiveLibraryLoadingCard()
                        }
                    }
                    errorMessage != null -> {
                        item(key = "error") {
                            ImmersiveErrorCard(errorMessage = errorMessage)
                        }
                    }
                    libraries.isEmpty() -> {
                        item(key = "empty") {
                            EmptyStateComposable(
                                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                                title = stringResource(id = R.string.no_libraries_found),
                                description = "Your media libraries will appear here once they are configured on your server",
                                type = EmptyStateType.Info,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                            )
                        }
                    }
                    else -> {
                        items(
                            items = libraries,
                            key = { it.getItemKey() },
                            contentType = { "immersive_library_card" },
                        ) { library ->
                            ImmersiveLibraryCard(
                                library = library,
                                getImageUrl = getImageUrl,
                                onClick = {
                                    try {
                                        onLibraryClick(library)
                                    } catch (e: CancellationException) {
                                        throw e
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        // Floating action buttons (top-right corner)
        AnimatedVisibility(
            visible = showFabs,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Back button (if needed)
                if (showBackButton) {
                    FloatingActionButton(
                        onClick = onBackClick,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                }

                // Refresh button
                FloatingActionButton(
                    onClick = onRefresh,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(id = R.string.refresh),
                    )
                }

                // Settings button
                FloatingActionButton(
                    onClick = onSettingsClick,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(id = R.string.settings),
                    )
                }
            }
        }

        // Search FAB (bottom-right)
        AnimatedVisibility(
            visible = showFabs,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 96.dp), // Above MiniPlayer
        ) {
            FloatingActionButton(
                onClick = onSearchClick,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(id = R.string.search),
                )
            }
        }

        // MiniPlayer at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            MiniPlayer(onExpandClick = onNowPlayingClick)
        }
    }
}

/**
 * Immersive library card with gradient background and larger size
 */
@Composable
private fun ImmersiveLibraryCard(
    library: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val libraryIcon = getImmersiveLibraryIcon(library.collectionType)
    val libraryColor = getLibraryColor(library.collectionType)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(ImmersiveLibraryScreenDefaults.LibraryCardHeight)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Gradient background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                libraryColor.copy(alpha = 0.15f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            // Content
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Icon with colored background
                Surface(
                    shape = CircleShape,
                    color = libraryColor.copy(alpha = 0.2f),
                    modifier = Modifier.size(64.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = libraryIcon,
                            contentDescription = null,
                            tint = libraryColor,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                // Library name and count
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = library.name ?: stringResource(R.string.unknown),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    library.childCount?.let { count ->
                        Text(
                            text = "$count items",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Loading placeholder card with shimmer effect
 */
@Composable
private fun ImmersiveLibraryLoadingCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(ImmersiveLibraryScreenDefaults.LibraryCardHeight)
            .shimmer(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        // Empty shimmer card
    }
}

/**
 * Error card with Material 3 styling
 */
@Composable
private fun ImmersiveErrorCard(
    errorMessage: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(24.dp),
        )
    }
}

/**
 * Get icon for library type
 */
@Composable
private fun getImmersiveLibraryIcon(
    collectionType: org.jellyfin.sdk.model.api.CollectionType?,
): ImageVector {
    return when (collectionType?.toString()?.lowercase()) {
        "movies" -> Icons.Default.Movie
        "tvshows" -> Icons.Default.Tv
        "music" -> Icons.Default.MusicNote
        "books" -> Icons.AutoMirrored.Filled.LibraryBooks
        "homevideos" -> Icons.Default.Photo
        "musicvideos" -> Icons.Default.Audiotrack
        "playlists" -> Icons.Default.BookmarkBorder
        "mixed" -> Icons.Default.Widgets
        else -> Icons.Default.Folder
    }
}

/**
 * Get themed color for library type
 */
@Composable
private fun getLibraryColor(
    collectionType: org.jellyfin.sdk.model.api.CollectionType?,
): Color {
    return when (collectionType?.toString()?.lowercase()) {
        "movies" -> MaterialTheme.colorScheme.primary // Movies - primary purple
        "tvshows" -> MaterialTheme.colorScheme.secondary // TV - secondary blue
        "music" -> MaterialTheme.colorScheme.tertiary // Music - tertiary teal
        "books" -> Color(0xFFFF6F00) // Books - orange
        "homevideos" -> Color(0xFFC2185B) // Home videos - pink
        "musicvideos" -> Color(0xFF7B1FA2) // Music videos - deep purple
        "playlists" -> Color(0xFF0288D1) // Playlists - light blue
        "mixed" -> Color(0xFF5E35B1) // Mixed - deep purple
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * Default values for ImmersiveLibraryScreen
 */
private object ImmersiveLibraryScreenDefaults {
    val LibraryCardHeight = 120.dp
    const val LibraryPlaceholderCount = 6
}
