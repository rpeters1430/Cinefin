@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.jellyfinandroid.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.example.jellyfinandroid.ui.ShimmerBox
import com.example.jellyfinandroid.ui.theme.SeriesBlue
import com.example.jellyfinandroid.ui.viewmodel.TVSeasonState
import com.example.jellyfinandroid.ui.viewmodel.TVSeasonViewModel
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TVSeasonScreen(
    seriesId: String,
    onBackClick: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier
) {
    val viewModel: TVSeasonViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    
    LaunchedEffect(seriesId) {
        viewModel.loadSeriesData(seriesId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.seriesDetails?.name ?: "TV Show",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SeriesBlue.copy(alpha = 0.1f)
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SeriesBlue)
                }
            }
            state.errorMessage != null -> {
                ErrorContent(
                    message = state.errorMessage!!,
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            else -> {
                TVSeasonContent(
                    state = state,
                    getImageUrl = getImageUrl,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun TVSeasonContent(
    state: TVSeasonState,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Series Details Header
        state.seriesDetails?.let { series ->
            item {
                SeriesDetailsHeader(
                    series = series,
                    getImageUrl = getImageUrl
                )
            }
        }
        
        // Seasons Section
        if (state.seasons.isNotEmpty()) {
            item {
                Text(
                    text = "Seasons",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            items(state.seasons) { season ->
                SeasonCard(
                    season = season,
                    getImageUrl = getImageUrl
                )
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No seasons available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesDetailsHeader(
    series: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box {
            // Background Image
            SubcomposeAsyncImage(
                model = getImageUrl(series),
                contentDescription = series.name,
                loading = {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        shape = RoundedCornerShape(16.dp)
                    )
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(SeriesBlue.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = series.name ?: "Unknown Series",
                            style = MaterialTheme.typography.headlineMedium,
                            color = SeriesBlue
                        )
                    }
                },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            
            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )
            
            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = series.name ?: "Unknown Series",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    series.productionYear?.let { year ->
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                    
                    series.communityRating?.let { rating ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color.Yellow,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = String.format("%.1f", rating),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                    
                    series.childCount?.let { count ->
                        Text(
                            text = "$count episodes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
                
                series.overview?.let { overview ->
                    if (overview.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonCard(
    season: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { /* TODO: Navigate to episodes */ },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Season Poster
            SubcomposeAsyncImage(
                model = getImageUrl(season),
                contentDescription = season.name,
                loading = {
                    ShimmerBox(
                        modifier = Modifier
                            .width(80.dp)
                            .height(120.dp),
                        shape = RoundedCornerShape(8.dp)
                    )
                },
                error = {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(120.dp)
                            .background(SeriesBlue.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = season.name ?: "Season",
                            style = MaterialTheme.typography.bodySmall,
                            color = SeriesBlue
                        )
                    }
                },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(80.dp)
                    .height(120.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            
            // Season Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = season.name ?: "Unknown Season",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                season.productionYear?.let { year ->
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                season.childCount?.let { count ->
                    Text(
                        text = "$count episodes",
                        style = MaterialTheme.typography.bodySmall,
                        color = SeriesBlue
                    )
                }
                
                season.overview?.let { overview ->
                    if (overview.isNotBlank()) {
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Error",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        androidx.compose.material3.Button(
            onClick = onRetry,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = SeriesBlue
            )
        ) {
            Text("Retry")
        }
    }
} 