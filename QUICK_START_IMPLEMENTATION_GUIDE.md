# 🚀 **QUICK START IMPLEMENTATION GUIDE - Phase 5 Priority Items**

## 🎯 **IMMEDIATE ACTION ITEMS (Week 1)**

### **1. Audio Playback Implementation (High Priority)**

#### **Step 1: Create Audio Models**
```kotlin
// File: app/src/main/java/com/rpeters/jellyfin/data/model/AudioModels.kt
data class AudioItem(
    val id: String,
    val name: String,
    val artist: String?,
    val album: String?,
    val duration: Long,
    val imageUrl: String?,
    val streamUrl: String?
)

data class AudioStreamInfo(
    val streamUrl: String,
    val format: String,
    val bitrate: Int,
    val sampleRate: Int
)

data class AudioMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val year: Int?,
    val genre: String?,
    val duration: Long
)
```

#### **Step 2: Create Audio Repository**
```kotlin
// File: app/src/main/java/com/rpeters/jellyfin/data/repository/AudioPlaybackRepository.kt
@Singleton
class AudioPlaybackRepository @Inject constructor(
    private val jellyfinRepository: JellyfinRepository
) {
    suspend fun getAudioItem(audioId: String): AudioItem {
        return jellyfinRepository.getItem(audioId).let { item ->
            AudioItem(
                id = item.id,
                name = item.name,
                artist = item.artistIds?.firstOrNull()?.let { jellyfinRepository.getArtist(it) }?.name,
                album = item.albumId?.let { jellyfinRepository.getAlbum(it) }?.name,
                duration = item.runTimeTicks?.div(10_000_000) ?: 0L,
                imageUrl = jellyfinRepository.getImageUrl(item.id, ImageType.Primary),
                streamUrl = jellyfinRepository.getStreamUrl(item.id)
            )
        }
    }

    suspend fun getAudioStreamInfo(audioId: String): AudioStreamInfo {
        val streamUrl = jellyfinRepository.getStreamUrl(audioId)
        return AudioStreamInfo(
            streamUrl = streamUrl,
            format = "mp3", // Default format
            bitrate = 320000, // Default bitrate
            sampleRate = 44100 // Default sample rate
        )
    }

    suspend fun updatePlaybackPosition(audioId: String, position: Long) {
        jellyfinRepository.updatePlaybackProgress(audioId, position)
    }
}
```

#### **Step 3: Create Audio Player UI**
```kotlin
// File: app/src/main/java/com/rpeters/jellyfin/ui/components/AudioPlayer.kt
@Composable
fun AudioPlayer(
    audioItem: AudioItem,
    isPlaying: Boolean,
    currentPosition: Long,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Album Art
            AsyncImage(
                model = audioItem.imageUrl,
                contentDescription = "Album art for ${audioItem.name}",
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Track Info
            Text(
                text = audioItem.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            audioItem.artist?.let { artist ->
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress Bar
            val duration = audioItem.duration
            val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
            
            Slider(
                value = progress,
                onValueChange = { newProgress ->
                    val newPosition = (newProgress * duration).toLong()
                    onSeek(newPosition)
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Time Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(currentPosition),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Play/Pause Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
```

### **2. Continue Watching Feature (High Priority)**

#### **Step 1: Create Continue Watching Models**
```kotlin
// File: app/src/main/java/com/rpeters/jellyfin/data/model/ContinueWatchingModels.kt
data class ContinueWatchingItem(
    val itemId: String,
    val name: String,
    val imageUrl: String?,
    val progress: Float, // 0.0 to 1.0
    val remainingTime: Long,
    val lastWatched: Long,
    val mediaType: MediaType
)

enum class MediaType {
    MOVIE, TV_SHOW, AUDIO, PHOTO
}
```

#### **Step 2: Add Continue Watching to Repository**
```kotlin
// Add to JellyfinRepository.kt
suspend fun getContinueWatchingItems(limit: Int = 10): List<ContinueWatchingItem> {
    return try {
        val response = jellyfinApi.getResumeItems(
            userId = validateServer().userId,
            limit = limit
        )
        
        response.items.map { item ->
            val progress = item.userData?.playbackPositionTicks?.let { position ->
                if (item.runTimeTicks != null && item.runTimeTicks > 0) {
                    position.toFloat() / item.runTimeTicks
                } else 0f
            } ?: 0f
            
            ContinueWatchingItem(
                itemId = item.id,
                name = item.name,
                imageUrl = getImageUrl(item.id, ImageType.Primary),
                progress = progress,
                remainingTime = item.runTimeTicks?.let { runtime ->
                    val position = item.userData?.playbackPositionTicks ?: 0L
                    (runtime - position) / 10_000_000 // Convert to milliseconds
                } ?: 0L,
                lastWatched = item.userData?.lastPlayedDate?.toEpochMilli() ?: 0L,
                mediaType = when (item.type) {
                    "Movie" -> MediaType.MOVIE
                    "Episode" -> MediaType.TV_SHOW
                    "Audio" -> MediaType.AUDIO
                    else -> MediaType.PHOTO
                }
            )
        }
    } catch (e: Exception) {
        handleExceptionSafely(e, "Failed to get continue watching items")
        emptyList()
    }
}
```

#### **Step 3: Create Continue Watching UI Component**
```kotlin
// File: app/src/main/java/com/rpeters/jellyfin/ui/components/ContinueWatchingSection.kt
@Composable
fun ContinueWatchingSection(
    items: List<ContinueWatchingItem>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isNotEmpty()) {
        Column(modifier = modifier) {
            Text(
                text = "Continue Watching",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items) { item ->
                    ContinueWatchingCard(
                        item = item,
                        onClick = { onItemClick(item.itemId) }
                    )
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(200.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            // Background Image
            AsyncImage(
                model = item.imageUrl,
                contentDescription = "Cover for ${item.name}",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentScale = ContentScale.Crop
            )
            
            // Progress Overlay
            LinearProgressIndicator(
                progress = item.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            )
            
            // Play Button Overlay
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play ${item.name}",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        // Title
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(12.dp)
        )
    }
}
```

### **3. Enhanced Loading States (Medium Priority)**

#### **Step 1: Create Skeleton Loading Components**
```kotlin
// File: app/src/main/java/com/rpeters/jellyfin/ui/components/SkeletonLoading.kt
@Composable
fun SkeletonMovieCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(120.dp)
            .height(180.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shimmerEffect()
        )
    }
}

@Composable
fun SkeletonText(
    width: Dp,
    height: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .shimmerEffect()
            .clip(RoundedCornerShape(4.dp))
    )
}

@Composable
fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer"
    )
    
    this.background(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
    )
}
```

#### **Step 2: Create Progressive Image Loading**
```kotlin
// File: app/src/main/java/com/rpeters/jellyfin/ui/components/ProgressiveImage.kt
@Composable
fun ProgressiveImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit = { SkeletonBox(modifier = modifier) },
    error: @Composable () -> Unit = { 
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.BrokenImage,
                contentDescription = "Failed to load image",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
) {
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            onLoading = { isLoading = true },
            onSuccess = { isLoading = false },
            onError = { 
                isLoading = false
                isError = true
            }
        )
        
        if (isLoading) {
            placeholder()
        }
        
        if (isError) {
            error()
        }
    }
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.shimmerEffect()
    )
}
```

### **4. Performance Optimization (Medium Priority)**

#### **Step 1: Optimize Image Loading Configuration**
```kotlin
// File: app/src/main/java/com/rpeters/jellyfin/di/ImageModule.kt
@Module
@InstallIn(SingletonComponent::class)
object ImageModule {
    
    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25) // 25% of app memory
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02) // 2% of available disk space
                    .build()
            }
            .components {
                add(ImageDecoder.Factory())
                add(VideoFrameDecoder.Factory())
            }
            .respectCacheHeaders(false)
            .build()
    }
}
```

#### **Step 2: Optimize List Performance**
```kotlin
// Update existing LazyColumn implementations
LazyColumn {
    items(
        items = movieList,
        key = { movie -> movie.id }, // Stable keys for better performance
        contentType = { movie -> "movie_card" } // Content type for recycling
    ) { movie ->
        MovieCard(
            movie = movie,
            modifier = Modifier.animateItemPlacement() // Smooth animations
        )
    }
}
```

## 🚀 **IMPLEMENTATION CHECKLIST**

### **Week 1 Goals:**
- [ ] ✅ Create audio playback models and repository
- [ ] ✅ Implement basic audio player UI
- [ ] ✅ Add continue watching feature to repository
- [ ] ✅ Create continue watching UI components
- [ ] ✅ Implement skeleton loading components
- [ ] ✅ Add progressive image loading
- [ ] ✅ Optimize image loading configuration
- [ ] ✅ Update list implementations with performance optimizations

### **Testing Checklist:**
- [ ] ✅ Test audio playback with different audio formats
- [ ] ✅ Verify continue watching data loading
- [ ] ✅ Test skeleton loading states
- [ ] ✅ Verify image loading performance improvements
- [ ] ✅ Test list scrolling performance

### **Integration Points:**
- [ ] ✅ Add audio player to navigation
- [ ] ✅ Integrate continue watching into home screen
- [ ] ✅ Update existing screens to use skeleton loading
- [ ] ✅ Replace AsyncImage with ProgressiveImage where appropriate

---

**This quick start guide provides immediate implementation steps for the highest priority improvements. Each component is designed to be modular and can be implemented independently.**

**Estimated Time:** 3-5 days for complete implementation  
**Difficulty:** Medium  
**Impact:** High (core functionality and user experience)