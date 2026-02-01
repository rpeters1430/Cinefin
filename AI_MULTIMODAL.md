# Multimodal AI Features (Future Implementation)

This document outlines how to implement image + text AI features using Gemini Nano's multimodal capabilities, available on Samsung S25 devices and cloud API.

## Device Support

### On-Device Multimodal AI (Gemini Nano)
**Supported Devices:**
- Samsung Galaxy S25 (Android 15+)
- Samsung Galaxy S25+ (Android 15+)
- Samsung Galaxy S25 Ultra (Android 15+)

**Capabilities:**
- ✅ Text + Image input
- ✅ Private (all processing on-device)
- ✅ Fast (~500ms-1s response time)
- ✅ Free (no API costs)

### Cloud Multimodal AI (Gemini 2.5 Flash)
**All Devices:**
- Any Android device with internet connection
- Requires Google AI API key

**Capabilities:**
- ✅ Text + Image + Video input
- ✅ More powerful model
- ⚠️ Requires internet
- ⚠️ Uses API quota (free tier: 15 req/min, 1500/day)

## Implementation Guide

### Step 1: Add Image Loading to Repository

Uncomment the `analyzeImage()` method in `GenerativeAiRepository.kt` and implement bitmap loading:

```kotlin
suspend fun analyzeImage(imageUri: Uri, prompt: String): String = withContext(Dispatchers.IO) {
    try {
        // Load the image as a Bitmap using Coil (already in dependencies)
        val imageLoader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(imageUri)
            .build()

        val result = imageLoader.execute(request)
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            ?: throw IllegalStateException("Failed to load bitmap")

        // Create content with both text and image
        val content = content {
            text(prompt)
            image(bitmap)
        }

        // Use primary model (Nano on S25, cloud otherwise)
        val response = primaryModel.generateContent(content)
        response.text ?: "Unable to analyze image."
    } catch (e: Exception) {
        android.util.Log.e("GenerativeAiRepository", "Error analyzing image", e)
        "Error analyzing image: ${e.message}"
    }
}
```

### Step 2: Add Context to Repository

Update the repository to accept Android Context (needed for image loading):

```kotlin
@Singleton
class GenerativeAiRepository @Inject constructor(
    @ApplicationContext private val context: Context, // ← Add this
    @Named("primary-model") private val primaryModel: GenerativeModel,
    @Named("pro-model") private val proModel: GenerativeModel,
    @Named("is-using-nano") private val isUsingNano: Boolean
) {
    // ... existing code
}
```

### Step 3: Add Image Analysis Methods

Add these specialized methods to `GenerativeAiRepository`:

```kotlin
/**
 * Analyzes a movie poster and suggests similar content
 */
suspend fun analyzePosterForRecommendations(posterUri: Uri): String {
    return analyzeImage(
        imageUri = posterUri,
        prompt = """Analyze this movie poster. Based on the visual style, colors, and imagery,
                   suggest 3-5 movies or shows with similar visual aesthetics or themes.
                   Be specific with titles.""".trimIndent()
    )
}

/**
 * Generates a description of a video thumbnail for accessibility
 */
suspend fun describeVideoThumbnail(thumbnailUri: Uri): String {
    return analyzeImage(
        imageUri = thumbnailUri,
        prompt = "Describe what's happening in this scene in one concise sentence."
    )
}

/**
 * Performs visual search based on a reference image
 */
suspend fun visualSearchSimilarContent(referenceImageUri: Uri): String {
    return analyzeImage(
        imageUri = referenceImageUri,
        prompt = """Based on this image, suggest media content (movies, shows)
                   that match this vibe, aesthetic, or theme. Focus on visual similarities.""".trimIndent()
    )
}

/**
 * Extracts genre/mood from poster artwork
 */
suspend fun extractGenreFromPoster(posterUri: Uri): String {
    return analyzeImage(
        imageUri = posterUri,
        prompt = "What genre and mood does this poster convey? Answer in 1-2 words (e.g., 'Dark Thriller', 'Light Comedy')."
    )
}
```

## Use Cases

### 1. Visual Search from Screenshots
User shares a screenshot of a movie scene they liked:

```kotlin
// In SearchViewModel
fun searchByImage(imageUri: Uri) {
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }

        val aiDescription = aiRepository.visualSearchSimilarContent(imageUri)
        val searchTerms = aiRepository.smartSearchQuery(aiDescription)

        val results = jellyfinRepository.searchItems(searchTerms.joinToString(" "))
        _uiState.update { it.copy(results = results, isLoading = false) }
    }
}
```

### 2. Enhanced Poster Analysis on HomeScreen
Show AI-generated insights when user long-presses a poster:

```kotlin
// In HomeViewModel
fun analyzePoster(item: BaseItemDto) {
    viewModelScope.launch {
        val posterUrl = jellyfinRepository.getImageUrl(item.id.toString())
        posterUrl?.let { url ->
            val insights = aiRepository.analyzePosterForRecommendations(Uri.parse(url))
            _uiState.update { it.copy(posterInsights = insights) }
        }
    }
}
```

### 3. Accessibility for TV (Describe Thumbnails)
On TV screens, describe video content for visually impaired users:

```kotlin
// In TvDetailViewModel
fun getAccessibleDescription(item: BaseItemDto) {
    viewModelScope.launch {
        val thumbnailUrl = jellyfinRepository.getBackdropUrl(item)
        thumbnailUrl?.let { url ->
            val description = aiRepository.describeVideoThumbnail(Uri.parse(url))
            // Announce via TalkBack
            _uiState.update { it.copy(accessibilityDescription = description) }
        }
    }
}
```

### 4. Genre Auto-Tagging
Automatically tag untagged media by analyzing posters:

```kotlin
// In LibraryViewModel
fun autoTagByPoster(item: BaseItemDto) {
    viewModelScope.launch {
        val posterUrl = jellyfinRepository.getImageUrl(item.id.toString())
        posterUrl?.let { url ->
            val detectedGenre = aiRepository.extractGenreFromPoster(Uri.parse(url))
            // Update item metadata
        }
    }
}
```

## UI Examples

### Visual Search Screen

```kotlin
@Composable
fun VisualSearchScreen(
    viewModel: SearchViewModel = hiltViewModel()
) {
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            viewModel.searchByImage(it)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Button(onClick = { launcher.launch("image/*") }) {
            Icon(Icons.Default.Image, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Search by Image")
        }

        selectedImageUri?.let { uri ->
            AsyncImage(
                model = uri,
                contentDescription = "Selected image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }

        // Show search results
        val results by viewModel.searchResults.collectAsStateWithLifecycle()
        LazyColumn {
            items(results) { item ->
                MediaCard(item = item)
            }
        }
    }
}
```

### Poster Insights Dialog

```kotlin
@Composable
fun PosterInsightsDialog(
    item: BaseItemDto,
    insights: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Visual Analysis") },
        text = {
            Column {
                AsyncImage(
                    model = getImageUrl(item),
                    contentDescription = item.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(insights)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
```

## Performance Considerations

### Samsung S25 (On-Device Multimodal)
- **First request**: ~1-2 seconds (model loading)
- **Subsequent requests**: ~500ms-1s
- **Image size**: Recommend max 1024x1024px (resize larger images)
- **No internet required**
- **Private**: Images never leave device

### Cloud API (All Devices)
- **Response time**: ~1-3 seconds (network dependent)
- **Image size**: Max 4MB per image
- **Rate limits**: 15 requests/min, 1500/day (free tier)
- **Privacy**: Images sent to Google AI servers

### Optimization Tips

1. **Resize images before sending:**
```kotlin
suspend fun resizeImageForAI(uri: Uri, maxSize: Int = 1024): Bitmap {
    val imageLoader = ImageLoader(context)
    val request = ImageRequest.Builder(context)
        .data(uri)
        .size(maxSize) // Coil will resize
        .build()

    val result = imageLoader.execute(request)
    return (result.drawable as BitmapDrawable).bitmap
}
```

2. **Cache AI responses:**
```kotlin
private val posterAnalysisCache = mutableMapOf<String, String>()

suspend fun analyzePosterForRecommendations(posterUri: Uri): String {
    val cacheKey = posterUri.toString()
    return posterAnalysisCache.getOrPut(cacheKey) {
        analyzeImage(posterUri, "...prompt...")
    }
}
```

3. **Use lower quality for thumbnails:**
```kotlin
val thumbnailRequest = ImageRequest.Builder(context)
    .data(uri)
    .size(512) // Smaller for faster processing
    .build()
```

## Testing Multimodal Features

### On Samsung S25 Devices
1. Install app on S25/S25+/S25 Ultra
2. Ensure Android 15 and Google Play Services updated
3. Check logcat: `AiModule: Using Gemini Nano (on-device)`
4. Test image analysis - should be fast (~500ms) with no internet

### On Other Devices (Cloud Fallback)
1. Ensure API key configured in `local.properties`
2. Test with internet connection
3. Monitor API usage at https://aistudio.google.com
4. Check response times (~1-3 seconds)

### Test Cases
```kotlin
@Test
fun testPosterAnalysis() = runTest {
    val testImageUri = Uri.parse("content://test/poster.jpg")

    val result = aiRepository.analyzePosterForRecommendations(testImageUri)

    assertNotNull(result)
    assertTrue(result.contains("movie", ignoreCase = true))
}
```

## Privacy & Compliance

### On-Device (Samsung S25)
- ✅ **GDPR Compliant**: No data leaves device
- ✅ **No internet required**: Fully offline capable
- ✅ **No logging**: Google doesn't see images or prompts

### Cloud API
- ⚠️ **Data sent to Google**: Images uploaded to Google AI servers
- ⚠️ **Privacy policy**: Must inform users in privacy policy
- ⚠️ **Retention**: Google may retain data for service improvement
- **Recommendation**: Add user consent dialog before first use

### User Consent Example
```kotlin
@Composable
fun CloudAIConsentDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("AI Image Analysis") },
        text = {
            Text(
                """This feature uses cloud AI to analyze images.
                   Your images will be sent to Google AI servers.

                   On Samsung S25 devices, images are processed
                   entirely on-device for privacy.

                   Do you want to continue?""".trimIndent()
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text("Accept") }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text("Decline") }
        }
    )
}
```

## Roadmap

### Phase 1: Basic Image Analysis
- [ ] Implement `analyzeImage()` in repository
- [ ] Add poster analysis to detail screens
- [ ] Add visual search screen

### Phase 2: Advanced Features
- [ ] Genre auto-tagging from posters
- [ ] Mood-based visual recommendations
- [ ] Video thumbnail descriptions for accessibility

### Phase 3: Multimodal Chat
- [ ] Allow users to upload images in AI chat
- [ ] "Show me movies that look like this" feature
- [ ] Screenshot-based search

### Phase 4: Samsung S25 Optimizations
- [ ] Detect S25 devices and highlight multimodal badge
- [ ] Optimize on-device performance
- [ ] Add offline-only mode toggle

---

**Next Steps:**
1. Test on Samsung S25 device (if available)
2. Implement `analyzeImage()` method
3. Add visual search screen
4. Collect user feedback on multimodal features
