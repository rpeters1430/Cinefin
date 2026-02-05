package com.rpeters.jellyfin.ui.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.util.PerformanceMetricsTracker
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.ui.components.*
import com.rpeters.jellyfin.ui.screens.home.HomeContent
import com.rpeters.jellyfin.ui.utils.MediaPlayerUtils
import com.rpeters.jellyfin.ui.viewmodel.LibraryActionsPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.MainAppState
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto

@OptIn(UnstableApi::class)
@OptInAppExperimentalApis
@Composable
fun HomeScreen(
    appState: MainAppState,
    currentServer: JellyfinServer?,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSearchClick: () -> Unit = {},
    onAiAssistantClick: () -> Unit = {},
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit = {},
    onLibraryClick: (BaseItemDto) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onNowPlayingClick: () -> Unit = {},
    onGenerateViewingMood: () -> Unit = {},
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    viewModel: MainAppViewModel = hiltViewModel(),
    libraryActionsPreferencesViewModel: LibraryActionsPreferencesViewModel = hiltViewModel(),
) {
    val libraryActionPrefs by libraryActionsPreferencesViewModel.preferences.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedItem by remember { mutableStateOf<BaseItemDto?>(null) }
    var showManageSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val managementEnabled = libraryActionPrefs.enableManagementActions
    val managementDisabledMessage = stringResource(id = R.string.library_actions_management_disabled)

    val handleItemLongPress: (BaseItemDto) -> Unit = { item ->
        if (managementEnabled) {
            selectedItem = item
            showManageSheet = true
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message = managementDisabledMessage)
            }
        }
    }

    val handlePlay: (BaseItemDto) -> Unit = { item ->
        val streamUrl = viewModel.getStreamUrl(item)
        if (streamUrl != null) {
            MediaPlayerUtils.playMedia(context, streamUrl, item)
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Unable to start playback")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            ExpressiveTopAppBar(
                title = currentServer?.name ?: stringResource(id = R.string.app_name),
                navigationIcon = {
                    if (showBackButton) {
                        ExpressiveBackNavigationIcon(onClick = onBackClick)
                    }
                },
                actions = {
                    ExpressiveTopAppBarRefreshAction(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        onClick = onRefresh,
                        isLoading = appState.isLoading,
                    )
                    ExpressiveTopAppBarAction(
                        icon = Icons.Default.Settings,
                        contentDescription = stringResource(id = R.string.settings),
                        onClick = onSettingsClick,
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                },
            )
        },
        bottomBar = {
            MiniPlayer(onExpandClick = onNowPlayingClick)
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = onAiAssistantClick,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Assistant",
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                FloatingActionButton(
                    onClick = onSearchClick,
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(id = R.string.search),
                    )
                }
            }
        },
        containerColor = Color.Transparent,
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surface,
                    MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ),
        ),
    ) { paddingValues ->
        PerformanceMetricsTracker(
            enabled = com.rpeters.jellyfin.BuildConfig.DEBUG,
            intervalMs = 30000,
        )

        LaunchedEffect(appState.continueWatching.size, appState.recentlyAdded.size) {
            if (appState.viewingMood == null &&
                !appState.isLoadingViewingMood &&
                (appState.continueWatching.isNotEmpty() || appState.recentlyAdded.isNotEmpty())
            ) {
                viewModel.generateViewingMood()
            }
        }

        Box(modifier = Modifier.padding(paddingValues)) {
            HomeContent(
                appState = appState,
                currentServer = currentServer,
                onRefresh = onRefresh,
                getImageUrl = getImageUrl,
                getBackdropUrl = getBackdropUrl,
                getSeriesImageUrl = getSeriesImageUrl,
                onItemClick = onItemClick,
                onItemLongPress = handleItemLongPress,
                onLibraryClick = onLibraryClick,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    selectedItem?.let { item ->
        if (showManageSheet) {
            val itemName = item.name ?: stringResource(id = R.string.unknown)
            val deleteSuccessMessage = stringResource(id = R.string.library_actions_delete_success, itemName)
            val deleteFailureTemplate = stringResource(id = R.string.library_actions_delete_failure, itemName, "%s")
            val refreshRequestedMessage = stringResource(id = R.string.library_actions_refresh_requested)
            val unknownErrorMessage = stringResource(id = R.string.unknown_error)

            MediaItemActionsSheet(
                item = item,
                sheetState = sheetState,
                onDismiss = {
                    showManageSheet = false
                    selectedItem = null
                },
                onPlay = {
                    handlePlay(item)
                },
                onDelete = { _, _ ->
                    viewModel.deleteItem(item) { success, message ->
                        coroutineScope.launch {
                            val text = if (success) {
                                deleteSuccessMessage
                            } else {
                                String.format(deleteFailureTemplate, message ?: "")
                            }
                            snackbarHostState.showSnackbar(text)
                        }
                    }
                },
                onRefreshMetadata = { _, _ ->
                    viewModel.refreshItemMetadata(item) { success, message ->
                        coroutineScope.launch {
                            val text = if (success) {
                                refreshRequestedMessage
                            } else {
                                "Failed to refresh metadata: ${message ?: unknownErrorMessage}"
                            }
                            snackbarHostState.showSnackbar(text)
                        }
                    }
                },
                onToggleWatched = {
                    viewModel.toggleWatchedStatus(item)
                },
                managementEnabled = managementEnabled,
            )
        }
    }
}