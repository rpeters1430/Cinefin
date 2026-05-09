package com.rpeters.jellyfin.ui.screens.home

import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.*
import com.rpeters.jellyfin.ui.components.immersive.ImmersiveHeroCarousel
import com.rpeters.jellyfin.ui.screens.LibraryType
import com.rpeters.jellyfin.ui.theme.Dimens
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.ui.viewmodel.MainAppState
import com.rpeters.jellyfin.utils.getItemKey
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType

@VisibleForTesting
@OptIn(ExperimentalMaterial3Api::class)
@OptInAppExperimentalApis
@Composable
internal fun MobileExpressiveHomeContent(
    appState: MainAppState,
    contentLists: HomeContentLists,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit,
    onLibraryClick: (BaseItemDto) -> Unit,
    viewingMood: String?,
    listState: LazyListState,
    contentPadding: PaddingValues,
    bottomSpacing: Dp,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val unknownText = androidx.compose.ui.res.stringResource(id = R.string.unknown)
    val libraryRows = remember(appState.libraries, appState.itemsByLibrary) {
        appState.libraries.mapNotNull { library ->
            if (library.toLibraryTypeOrNull() == LibraryType.STUFF) return@mapNotNull null
            val libraryId = library.id.toString()
            val items = appState.itemsByLibrary[libraryId]
                .orEmpty()
                .sortedByDescending { it.dateCreated }
                .take(10)
            if (items.isEmpty()) null else library to items
        }
    }
    val hasHeroSlot = contentLists.featuredItems.isNotEmpty() || appState.isLoading
    val topPadding = if (hasHeroSlot) {
        contentPadding.calculateTopPadding()
    } else {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 0.dp,
            top = topPadding,
            end = 0.dp,
            bottom = contentPadding.calculateBottomPadding() + bottomSpacing,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val heroItems = contentLists.featuredItems
        if (heroItems.isNotEmpty() || appState.isLoading) {
            item(key = "recently_added_hero", contentType = "hero_row") {
                if (heroItems.isNotEmpty()) {
                    val featuredCarouselItems = remember(heroItems, unknownText) {
                        heroItems.map { item ->
                            CarouselItem(
                                id = item.id.toString(),
                                title = item.name ?: unknownText,
                                subtitle = item.productionYear?.toString() ?: "",
                                imageUrl = getBackdropUrl(item) ?: getSeriesImageUrl(item) ?: getImageUrl(item) ?: "",
                                type = if (item.type == BaseItemKind.SERIES) MediaType.TV_SHOW else MediaType.MOVIE,
                            )
                        }
                    }

                    ImmersiveHeroCarousel(
                        items = featuredCarouselItems,
                        onItemClick = { selected ->
                            heroItems.firstOrNull { it.id.toString() == selected.id }?.let(onItemClick)
                        },
                        onPlayClick = { selected ->
                            heroItems.firstOrNull { it.id.toString() == selected.id }?.let(onItemClick)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    // Placeholder while loading
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ImmersiveDimens.HeroHeightPhone),
                    )
                }
            }
        }

        item(key = "libraries", contentType = "libraries") {
            LibraryNavigationCarousel(
                libraries = appState.libraries,
                getImageUrl = getImageUrl,
                onLibraryClick = onLibraryClick,
            )
        }

        if (!viewingMood.isNullOrBlank()) {
            item(key = "viewing_mood", contentType = "viewing_mood") {
                ViewingMoodWidget(
                    viewingMood = viewingMood,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        if (contentLists.continueWatching.isNotEmpty()) {
            item(key = "continue_watching", contentType = "continue_watching") {
                ContinueWatchingSection(
                    items = contentLists.continueWatching,
                    getImageUrl = { item -> getBackdropUrl(item) ?: getSeriesImageUrl(item) ?: getImageUrl(item) },
                    onItemClick = onItemClick,
                    onItemLongPress = onItemLongPress,
                    cardWidth = 320.dp,
                )
            }
        }

        if (contentLists.nextUp.isNotEmpty()) {
            item(key = "next_up", contentType = "next_up") {
                PosterRowSection(
                    title = androidx.compose.ui.res.stringResource(id = R.string.home_next_up),
                    items = contentLists.nextUp,
                    getImageUrl = { item -> getSeriesImageUrl(item) ?: getImageUrl(item) },
                    onItemClick = onItemClick,
                    onItemLongPress = onItemLongPress,
                    cardWidth = 200.dp,
                )
            }
        }

        if (contentLists.recentEpisodes.isNotEmpty()) {
            item(key = "recently_added_tv_episodes", contentType = "recent_episodes") {
                PosterRowSection(
                    title = androidx.compose.ui.res.stringResource(id = R.string.home_recently_added_tv_episodes),
                    items = contentLists.recentEpisodes,
                    getImageUrl = { item -> getSeriesImageUrl(item) ?: getImageUrl(item) },
                    onItemClick = onItemClick,
                    onItemLongPress = onItemLongPress,
                    cardWidth = 200.dp,
                )
            }
        }

        if (contentLists.recentVideos.isNotEmpty()) {
            item(key = "recent_stuff", contentType = "recent_stuff") {
                MediaRowSection(
                    title = androidx.compose.ui.res.stringResource(id = R.string.home_recently_added_stuff),
                    items = contentLists.recentVideos,
                    getImageUrl = { item -> getBackdropUrl(item) ?: getImageUrl(item) },
                    onItemClick = onItemClick,
                    onItemLongPress = onItemLongPress,
                )
            }
        }

        items(
            items = libraryRows,
            key = { (library, _) -> library.id.toString() },
            contentType = { "library_recent_row" },
        ) { (library, items) ->
            LibraryRecentSection(
                library = library,
                items = items,
                getImageUrl = { item ->
                    when (item.type) {
                        BaseItemKind.EPISODE -> getSeriesImageUrl(item) ?: getImageUrl(item)
                        BaseItemKind.SERIES -> getSeriesImageUrl(item) ?: getBackdropUrl(item) ?: getImageUrl(item)
                        BaseItemKind.VIDEO -> getBackdropUrl(item) ?: getImageUrl(item)
                        else -> getImageUrl(item)
                    }
                },
                onLibraryClick = onLibraryClick,
                onItemClick = onItemClick,
                onItemLongPress = onItemLongPress,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryNavigationCarousel(
    libraries: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onLibraryClick: (BaseItemDto) -> Unit,
) {
    if (libraries.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionTitle(
            title = androidx.compose.ui.res.stringResource(R.string.home_libraries),
            modifier = Modifier.padding(top = 8.dp),
        )

        val carouselState = rememberCarouselState { libraries.size }
        androidx.compose.material3.carousel.HorizontalUncontainedCarousel(
            state = carouselState,
            itemWidth = 220.dp,
            itemSpacing = 16.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        ) { index ->
            val library = libraries[index]
            LibraryExpressiveCard(
                library = library,
                imageUrl = getImageUrl(library),
                onClick = { onLibraryClick(library) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LibraryRecentSection(
    library: BaseItemDto,
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onLibraryClick: (BaseItemDto) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit,
) {
    val haptics = com.rpeters.jellyfin.ui.utils.rememberExpressiveHaptics()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = library.name ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.library_recently_added_section),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = {
                haptics.lightClick()
                onLibraryClick(library)
            }) {
                Text(text = androidx.compose.ui.res.stringResource(R.string.open))
            }
        }

        when (library.toLibraryTypeOrNull()) {
            LibraryType.STUFF, LibraryType.MUSIC -> HomeLibraryMediaRow(
                items = items,
                getImageUrl = getImageUrl,
                onItemClick = onItemClick,
                onItemLongPress = onItemLongPress,
                cardWidth = 320.dp,
            )
            else -> HomeLibraryPosterRow(
                items = items,
                getImageUrl = getImageUrl,
                onItemClick = onItemClick,
                onItemLongPress = onItemLongPress,
                cardWidth = 200.dp,
            )
        }
    }
}

@Composable
private fun HomeLibraryPosterRow(
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit,
    cardWidth: Dp,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(items = items, key = { it.getItemKey() }) { item ->
            PosterMediaCard(
                item = item,
                getImageUrl = getImageUrl,
                onClick = onItemClick,
                onLongPress = onItemLongPress,
                cardWidth = cardWidth,
                showTitle = true,
                showMetadata = true,
                titleMinLines = 2,
            )
        }
    }
}

@Composable
private fun HomeLibraryMediaRow(
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit,
    cardWidth: Dp,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(items = items, key = { it.getItemKey() }) { item ->
            MediaCard(
                item = item,
                getImageUrl = { getImageUrl(item) },
                onClick = onItemClick,
                onLongPress = onItemLongPress,
                cardWidth = cardWidth,
            )
        }
    }
}

@Composable
private fun LibraryExpressiveCard(
    library: BaseItemDto,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = com.rpeters.jellyfin.ui.utils.rememberExpressiveHaptics()
    val sharedTransitionScope = com.rpeters.jellyfin.ui.navigation.LocalSharedTransitionScope.current
    val animatedVisibilityScope = com.rpeters.jellyfin.ui.navigation.LocalAnimatedVisibilityScope.current
    val libraryId = library.id.toString()

    val sharedElementModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                rememberSharedContentState(key = "library_$libraryId"),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else {
        Modifier
    }

    ElevatedCard(
        onClick = {
            haptics.lightClick()
            onClick()
        },
        modifier = modifier.then(sharedElementModifier),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            HeroImageWithGradient(
                imageUrl = imageUrl,
                contentDescription = library.name ?: "Library",
                modifier = Modifier.fillMaxSize(),
            )
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)) {
                    Icon(
                        imageVector = library.toLibraryTypeOrNull()?.icon ?: Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(20.dp),
                    )
                }
                Column(modifier = Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.86f),
                        tonalElevation = 2.dp,
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = library.name ?: "Library",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = library.collectionType?.toString()?.replace("_", " ") ?: "Collection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun BaseItemDto.toLibraryTypeOrNull(): LibraryType? = when (collectionType) {
    CollectionType.MOVIES -> LibraryType.MOVIES
    CollectionType.TVSHOWS -> LibraryType.TV_SHOWS
    CollectionType.MUSIC -> LibraryType.MUSIC
    CollectionType.HOMEVIDEOS, CollectionType.BOOKS -> LibraryType.STUFF
    else -> when (collectionType?.toString()?.lowercase()?.replace(" ", "")) {
        "movies" -> LibraryType.MOVIES
        "tvshows" -> LibraryType.TV_SHOWS
        "music" -> LibraryType.MUSIC
        else -> LibraryType.STUFF
    }
}

@Composable
internal fun ViewingMoodWidget(
    viewingMood: String,
    onMoodClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    ExpressiveContentCard(
        onClick = onMoodClick,
        modifier = modifier.fillMaxWidth().padding(vertical = Dimens.Spacing8),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(ImmersiveDimens.SpacingContentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(32.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Your Viewing Mood",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = viewingMood,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}
