package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.core.util.PerformanceMetricsTracker
import com.rpeters.jellyfin.ui.components.ExpressiveSimpleEmptyState
import com.rpeters.jellyfin.ui.components.MiniPlayer
import com.rpeters.jellyfin.ui.components.immersive.rememberImmersivePerformanceConfig
import com.rpeters.jellyfin.ui.components.shimmer
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.utils.getItemKey
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.api.BaseItemDto

// ─────────────────────────────────────────────────────────────────────────────
// ImmersiveLibraryScreen – Library tab: lists all Jellyfin media libraries
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Immersive version of LibraryScreen that displays all available Jellyfin media
 * libraries (Movies, TV Shows, Music, etc.) as large gradient cards.
 *
 * Used on the Library tab in [HomeLibraryNavGraph].
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
    onAiAssistantClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onNowPlayingClick: () -> Unit = {},
    showBackButton: Boolean = false,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_VARIABLE")
    val perfConfig = rememberImmersivePerformanceConfig()
    val listState = rememberLazyListState()

    PerformanceMetricsTracker(
        enabled = com.rpeters.jellyfin.BuildConfig.DEBUG,
        intervalMs = 30000,
    )

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
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 32.dp,
                    bottom = 120.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight),
            ) {
                item(key = "header") {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(id = R.string.your_libraries),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                    )
                }

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
                            ExpressiveSimpleEmptyState(
                                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                                title = stringResource(id = R.string.no_libraries_found),
                                subtitle = "Your media libraries will appear here once they are configured on your server",
                                iconTint = MaterialTheme.colorScheme.primary,
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

        AnimatedVisibility(
            visible = showFabs,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 28.dp, end = 16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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

        AnimatedVisibility(
            visible = showFabs,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 64.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FloatingActionButton(
                    onClick = onAiAssistantClick,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = stringResource(id = R.string.ai_assistant),
                    )
                }

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
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            MiniPlayer(onExpandClick = onNowPlayingClick)
        }
    }
}

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

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
    }
}

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

@Composable
private fun getLibraryColor(
    collectionType: org.jellyfin.sdk.model.api.CollectionType?,
): Color {
    return when (collectionType?.toString()?.lowercase()) {
        "movies" -> MaterialTheme.colorScheme.primary
        "tvshows" -> MaterialTheme.colorScheme.secondary
        "music" -> MaterialTheme.colorScheme.tertiary
        "books" -> Color(0xFFFF6F00)
        "homevideos" -> Color(0xFFC2185B)
        "musicvideos" -> Color(0xFF7B1FA2)
        "playlists" -> Color(0xFF0288D1)
        "mixed" -> Color(0xFF5E35B1)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private object ImmersiveLibraryScreenDefaults {
    val LibraryCardHeight = 120.dp
    const val LibraryPlaceholderCount = 6
}
