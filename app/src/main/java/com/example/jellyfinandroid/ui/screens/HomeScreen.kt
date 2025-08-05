package com.example.jellyfinandroid.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.jellyfinandroid.BuildConfig
import com.example.jellyfinandroid.R
import com.example.jellyfinandroid.data.JellyfinServer
import com.example.jellyfinandroid.ui.components.MediaCard
import com.example.jellyfinandroid.ui.components.RecentlyAddedCard
import com.example.jellyfinandroid.ui.components.ShimmerBox
import com.example.jellyfinandroid.ui.theme.getContentTypeColor
import com.example.jellyfinandroid.ui.viewmodel.MainAppState
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    appState: MainAppState,
    currentServer: JellyfinServer?,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentServer?.name ?: stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.navigate_up),
                            )
                        }
                    } else {
                        null
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(id = R.string.settings),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Show home content
            HomeContent(
                appState = appState,
                currentServer = currentServer,
                onRefresh = onRefresh,
                getImageUrl = getImageUrl,
                getBackdropUrl = getBackdropUrl,
                getSeriesImageUrl = getSeriesImageUrl,
                onItemClick = onItemClick,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun HomeLibraryCard(
    library: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier,
) {
    val contentTypeColor = getContentTypeColor(library.type?.toString())

    Card(
        modifier = modifier
            .width(200.dp) // Increased width for horizontal aspect
            .clickable { },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column {
            Box {
                SubcomposeAsyncImage(
                    model = getImageUrl(library),
                    contentDescription = library.name ?: "Library",
                    loading = {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp), // Reduced height for horizontal aspect
                            cornerRadius = 12,
                        )
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp) // Reduced height for horizontal aspect
                                .background(contentTypeColor.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Library",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp) // Reduced height for horizontal aspect (16:10 ratio)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                )
            }

            // Content Information
            Column(
                modifier = Modifier.padding(12.dp),
            ) {
                Text(
                    text = library.name ?: "Unknown Library",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Show library type if available
                library.type?.let { type ->
                    Text(
                        text = type.toString().replace("_", " ").lowercase()
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    appState: MainAppState,
    currentServer: JellyfinServer?,
    onRefresh: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Header Section
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Welcome back!",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        currentServer?.let { server ->
                            Text(
                                text = "Connected to ${server.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Material 3 Carousel with Recently Added Movies
        val recentMovies = appState.recentlyAddedByTypes["Movies"]?.take(8) ?: emptyList()

        if (BuildConfig.DEBUG) {
            Log.d("HomeScreen", "HomeContent: Displaying ${recentMovies.size} recent movies in carousel")
        }

        if (recentMovies.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "Recently Added Movies",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    val carouselState = rememberCarouselState { recentMovies.size }

                    HorizontalUncontainedCarousel(
                        state = carouselState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        itemWidth = 280.dp,
                        itemSpacing = 12.dp,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) { index ->
                        val movie = recentMovies[index]
                        CarouselMovieCard(
                            movie = movie,
                            getBackdropUrl = getBackdropUrl, // Use backdrop for horizontal cards
                            onClick = onItemClick,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp)), // Ensure full rounding
                        )
                    }
                }
            }
        }

        // Top Libraries Section
        if (appState.libraries.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "Libraries",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        items(appState.libraries) { library ->
                            HomeLibraryCard(
                                library = library,
                                getImageUrl = getImageUrl,
                            )
                        }
                    }
                }
            }
        }

        // Library-Specific Recently Added Sections
        val libraryTypes = listOf(
            "Movies" to "Movies",
            "TV Shows" to "TV Shows",
            "TV Episodes" to "Episodes",
            "Music" to "Music",
            "Home Videos" to "Videos",
        )

        libraryTypes.forEach { (displayName, typeKey) ->
            val recentItems = appState.recentlyAddedByTypes[typeKey]?.take(15) ?: emptyList()

            if (BuildConfig.DEBUG) {
                Log.d("HomeScreen", "HomeContent: Processing $displayName - found ${recentItems.size} items from recentlyAddedByTypes")
            }

            if (recentItems.isNotEmpty()) {
                item {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Recently Added $displayName",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            IconButton(onClick = onRefresh) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh $displayName",
                                )
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) {
                            items(recentItems) { item ->
                                if (BuildConfig.DEBUG) {
                                    Log.d("HomeScreen", "HomeContent: Displaying $displayName item: '${item.name}' (${item.type})")
                                }
                                RecentlyAddedCard(
                                    item = item,
                                    getImageUrl = getImageUrl,
                                    getSeriesImageUrl = getSeriesImageUrl,
                                    onClick = onItemClick,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Loading state
        if (appState.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        // Error state
        appState.errorMessage?.let { error ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun SearchResultsContent(
    searchResults: List<BaseItemDto>,
    isSearching: Boolean,
    errorMessage: String?,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (isSearching) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(
                            text = "Searching...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        errorMessage?.let { error ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        if (searchResults.isEmpty() && !isSearching && errorMessage == null) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "No results found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Group results by type
        val groupedResults = searchResults.groupBy { it.type }
        groupedResults.forEach { (type, items) ->
            item {
                Text(
                    text = when (type) {
                        org.jellyfin.sdk.model.api.BaseItemKind.MOVIE -> "Movies"
                        org.jellyfin.sdk.model.api.BaseItemKind.SERIES -> "TV Shows"
                        org.jellyfin.sdk.model.api.BaseItemKind.EPISODE -> "Episodes"
                        org.jellyfin.sdk.model.api.BaseItemKind.AUDIO -> "Music"
                        org.jellyfin.sdk.model.api.BaseItemKind.MUSIC_ALBUM -> "Albums"
                        org.jellyfin.sdk.model.api.BaseItemKind.MUSIC_ARTIST -> "Artists"
                        org.jellyfin.sdk.model.api.BaseItemKind.BOOK -> "Books"
                        org.jellyfin.sdk.model.api.BaseItemKind.AUDIO_BOOK -> "Audiobooks"
                        else -> type?.toString() ?: "Other"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            items(items.chunked(2)) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowItems.forEach { item ->
                        MediaCard(
                            item = item,
                            getImageUrl = getImageUrl,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Fill remaining space if odd number of items
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CarouselMovieCard(
    movie: BaseItemDto,
    getBackdropUrl: (BaseItemDto) -> String?,
    onClick: (BaseItemDto) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable { onClick(movie) },
        shape = RoundedCornerShape(16.dp), // Fully rounded
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(getBackdropUrl(movie) ?: "")
                    .crossfade(true)
                    .build(),
                contentDescription = movie.name ?: "Movie backdrop",
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp)), // Clip image to card shape
                contentScale = ContentScale.Crop,
            )

            // Enhanced gradient overlay with multiple stops
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.8f),
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY,
                        ),
                    ),
            )

            // ✅ FIX: Enhanced rating badge with better positioning and styling
            movie.communityRating?.let { rating ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        text = "★ ${String.format(java.util.Locale.ROOT, "%.1f", rating)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // Movie info at the bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            ) {
                Text(
                    text = movie.name ?: "Unknown Movie",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    movie.productionYear?.let { year ->
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                    movie.runTimeTicks?.let { ticks ->
                        val minutes = (ticks / 10_000_000 / 60).toInt()
                        val hours = minutes / 60
                        val remainingMinutes = minutes % 60
                        val runtime = if (hours > 0) "${hours}h ${remainingMinutes}m" else "${minutes}m"
                        Text(
                            text = "• $runtime",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}
