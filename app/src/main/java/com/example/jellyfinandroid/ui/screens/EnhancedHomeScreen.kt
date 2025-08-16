package com.example.jellyfinandroid.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.jellyfinandroid.ui.components.*
import com.example.jellyfinandroid.ui.viewmodel.EnhancedHomeViewModel

/**
 * Enhanced Home Screen demonstrating Phase 2 improvements:
 * - Smart error handling with contextual messages
 * - Beautiful loading states with skeleton screens
 * - Offline indicators and graceful degradation
 * - Retry mechanisms with user feedback
 *
 * This screen showcases the new error handling and UX improvements.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedHomeScreen(
    onNavigateToLibrary: (String, String) -> Unit = { _, _ -> },
    onNavigateToItem: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: EnhancedHomeViewModel = hiltViewModel(),
) {
    // Collect state from our enhanced ViewModel
    val homeState by viewModel.homeState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val errorState by viewModel.errorState.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState(initial = false)

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // Top App Bar with refresh action
        TopAppBar(
            title = { Text("Enhanced Home") },
            actions = {
                IconButton(
                    onClick = { viewModel.refreshHomeData() },
                    enabled = !isRefreshing,
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                        )
                    }
                }
            },
        )

        // Error Banner (slides in when there's an error)
        ErrorBanner(
            error = errorState,
            onRetry = if (viewModel.isCurrentErrorRetryable()) {
                { viewModel.retryLastFailedOperation() }
            } else {
                null
            },
            onDismiss = { viewModel.clearError() },
        )

        // Offline Indicator
        OfflineIndicator(
            isVisible = !isConnected,
            hasOfflineContent = false, // This would come from OfflineManager
            onViewOfflineContent = { /* Navigate to offline content */ },
        )

        // Custom error message (for specific errors)
        homeState.customErrorMessage?.let { customError ->
            CompactError(
                error = com.example.jellyfinandroid.ui.utils.ProcessedError(
                    userMessage = customError,
                    errorType = com.example.jellyfinandroid.data.repository.common.ErrorType.NOT_FOUND,
                    isRetryable = false,
                    suggestedAction = "Contact your administrator",
                ),
                onRetry = null,
            )
        }

        // Main Content with Loading States
        if (isLoading && homeState.libraries.isEmpty()) {
            // Show skeleton loading for initial load
            SkeletonHomeScreen()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Libraries Section
                item {
                    SectionHeader(
                        title = "Your Libraries",
                        isLoading = isLoading && homeState.libraries.isEmpty(),
                        onRefresh = { viewModel.refreshHomeData() },
                    )
                }

                if (homeState.libraries.isNotEmpty()) {
                    items(
                        items = homeState.libraries,
                        key = { it.id ?: it.name ?: "" },
                    ) { library ->
                        LibraryCard(
                            library = library,
                            onClick = {
                                onNavigateToLibrary(
                                    library.id?.toString() ?: "",
                                    library.name ?: "Unknown Library",
                                )
                            },
                        )
                    }
                } else if (!isLoading) {
                    item {
                        EmptyStateCard(
                            title = "No Libraries Found",
                            message = "Your Jellyfin server doesn't have any libraries configured.",
                            actionText = "Refresh",
                            onAction = { viewModel.refreshHomeData() },
                        )
                    }
                }

                // Recently Added Section
                item {
                    SectionHeader(
                        title = "Recently Added",
                        isLoading = isLoading && homeState.recentlyAdded.isEmpty(),
                        onRefresh = { viewModel.refreshHomeData() },
                    )
                }

                if (homeState.recentlyAdded.isNotEmpty()) {
                    items(
                        items = homeState.recentlyAdded,
                        key = { it.id ?: it.name ?: "" },
                    ) { item ->
                        RecentlyAddedCard(
                            item = item,
                            onClick = { onNavigateToItem(item.id?.toString() ?: "") },
                        )
                    }
                } else if (!isLoading && homeState.libraries.isNotEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No Recent Content",
                            message = "No recently added content found in your libraries.",
                            actionText = "Load All Content",
                            onAction = { viewModel.loadAllContentTypes() },
                        )
                    }
                }

                // Demonstration: Bulk Load Button
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Enhanced Features Demo",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )

                            Text(
                                text = "This demonstrates parallel loading, smart retries, and enhanced error handling.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )

                            Button(
                                onClick = { viewModel.loadAllContentTypes() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Load All Content Types (Parallel)")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh $title",
                )
            }
        }
    }
}

@Composable
private fun LibraryCard(
    library: org.jellyfin.sdk.model.api.BaseItemDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = library.name ?: "Unknown Library",
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = library.type?.name ?: "Library",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun RecentlyAddedCard(
    item: org.jellyfin.sdk.model.api.BaseItemDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Placeholder for thumbnail
            Card(
                modifier = Modifier.size(60.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Box(modifier = Modifier.fillMaxSize())
            }

            Column {
                Text(
                    text = item.name ?: "Unknown Item",
                    style = MaterialTheme.typography.bodyLarge,
                )

                Text(
                    text = item.type?.name ?: "Media",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    message: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )

            Button(onClick = onAction) {
                Text(actionText)
            }
        }
    }
}
