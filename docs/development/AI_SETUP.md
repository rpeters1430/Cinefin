# AI Assistant Setup Guide

This guide explains how to set up the Gemini AI integration in your Jellyfin Android app.

## Architecture Overview

The AI system is designed with a **repository pattern** that can be used across all screens (phone, tablet, TV):

```
GenerativeAiRepository (Central AI Logic)
    ↓
Used by any screen via ViewModels:
- AiAssistantScreen (chat interface)
- HomeScreen (recommendations)
- SearchScreen (smart search)
- TV screens (voice commands, summaries)
```

## Backend Strategy: Gemini Nano → Cloud Fallback

The app intelligently selects the best AI backend:

1. **First choice: Gemini Nano (On-Device)**
   - Runs directly on device (Android 14+, Pixel 8a+)
   - **Privacy**: No data sent to cloud
   - **Speed**: Faster responses
   - **Free**: No API costs

2. **Fallback: Gemini 2.5 Flash (Cloud API)**
   - Used when Nano unavailable
   - Requires internet connection
   - Requires API key (see setup below)

## Setup Steps

### Step 1: Get a Google AI API Key

1. Go to [Google AI Studio](https://aistudio.google.com/apikey)
2. Click "Get API Key"
3. Create a new API key (or use existing)
4. Copy the API key

### Step 2: Add API Key to Your Project

**Option A: gradle.properties (Recommended - easiest)**

Add to `gradle.properties` (already gitignored):
```properties
GOOGLE_AI_API_KEY=your-api-key-here
```

**Option B: local.properties (Also works)**

Add to `local.properties` (also gitignored):
```properties
GOOGLE_AI_API_KEY=your-api-key-here
```

**Option C: Environment Variable (For CI/CD)**
```bash
export GOOGLE_AI_API_KEY=your-api-key-here
```

**Note:** The build.gradle.kts is already configured to check all three locations automatically!

### Step 3: Configure the API Key in Code

Update `di/AiModule.kt` to use the API key:

```kotlin
private fun createCloudFlashModel(): GenerativeModel {
    return Firebase.ai(
        backend = GenerativeBackend.googleAI(
            apiKey = BuildConfig.GOOGLE_AI_API_KEY
        )
    ).generativeModel(
        modelName = "gemini-2.5-flash",
        config = GenerativeModelConfig.builder()
            .temperature(0.7f)
            .topK(40)
            .topP(0.95f)
            .maxOutputTokens(2048)
            .build()
    )
}

private fun provideProModel(): GenerativeModel {
    return Firebase.ai(
        backend = GenerativeBackend.googleAI(
            apiKey = BuildConfig.GOOGLE_AI_API_KEY
        )
    ).generativeModel(
        modelName = "gemini-2.5-flash",
        config = GenerativeModelConfig.builder()
            .temperature(0.8f)
            .topK(40)
            .topP(0.95f)
            .maxOutputTokens(4096)
            .build()
    )
}
```

### Step 4: Testing Gemini Nano (Optional)

**Requirements for Gemini Nano:**
- Android 14 (API 34) or higher
- Supported device (Pixel 8a or newer recommended)
- Download Gemini Nano model:

```bash
# Download via AICore (automatic on supported devices)
# Or manually via Google Play Services update
```

**Check if Nano is available:**
The app automatically detects Nano availability at startup. Check the logs:
```
AiModule: Using Gemini Nano (on-device)
OR
AiModule: Gemini Nano unavailable, using cloud API
```

**UI Indicator:**
When on-device AI is active, the screen title shows "(On-Device)":
```
Jellyfin AI Assistant (On-Device)  ← Gemini Nano
Jellyfin AI Assistant               ← Cloud API
```

## Using AI Features in Other Screens

The `GenerativeAiRepository` provides reusable AI functions:

### Example 1: Smart Search (Already integrated)
```kotlin
// In SearchViewModel or any ViewModel
class SearchViewModel @Inject constructor(
    private val aiRepository: GenerativeAiRepository,
    private val jellyfinRepository: JellyfinRepository
) : ViewModel() {

    fun searchWithAI(naturalQuery: String) {
        viewModelScope.launch {
            // Get AI-enhanced search terms
            val keywords = aiRepository.smartSearchQuery(naturalQuery)

            // Search Jellyfin with optimized keywords
            val results = jellyfinRepository.searchItems(keywords.joinToString(" "))
        }
    }
}
```

### Example 2: Viewing Mood Analysis (HomeScreen)
```kotlin
// In HomeViewModel
fun analyzeViewingMood() {
    viewModelScope.launch {
        val recentItems = jellyfinRepository.getContinueWatching()
        if (recentItems is ApiResult.Success) {
            val mood = aiRepository.analyzeViewingHabits(recentItems.data)
            _uiState.update { it.copy(viewingMood = mood) }
        }
    }
}
```

### Example 3: TV Voice Commands
```kotlin
// In TvSearchViewModel
fun processVoiceCommand(command: String) {
    viewModelScope.launch {
        // Let AI parse the voice command
        val response = aiRepository.generateResponse(
            "User said: '$command'. Extract their intent and format as JSON with {action, parameters}"
        )
        // Parse and execute
    }
}
```

### Example 4: Item Summaries for TV
```kotlin
// In TvDetailViewModel
fun getSummary(item: BaseItemDto) {
    viewModelScope.launch {
        // Get concise summary for TV (easier to read from distance)
        val summary = aiRepository.summarizeItem(item)
        _uiState.update { it.copy(summary = summary) }
    }
}
```

## Repository API Reference

### GenerativeAiRepository Methods

```kotlin
// Simple chat
suspend fun generateResponse(prompt: String): String

// Streaming responses (for real-time UI)
fun generateResponseStream(prompt: String): Flow<GenerateContentResponse>

// Analyze viewing habits
suspend fun analyzeViewingHabits(recentItems: List<BaseItemDto>): String

// Smart search translation
suspend fun smartSearchQuery(userQuery: String): List<String>

// Generate recommendations
suspend fun generateRecommendations(
    recentItems: List<BaseItemDto>,
    userPreferences: String? = null
): String

// Summarize media item (useful for TV)
suspend fun summarizeItem(item: BaseItemDto): String

// Check if using on-device AI
fun isUsingOnDeviceAI(): Boolean
```

## Device Compatibility

### Gemini Nano Support (On-Device AI)

**Google Pixel Devices:**
- Pixel 8, 8 Pro, 8a (Android 14+)
- Pixel 9, 9 Pro, 9 Pro XL, 9 Pro Fold (Android 14+)
- Pixel 10, 10 Pro, 10 Pro XL (Android 15+)
- Pixel Fold (all variants)

**Samsung Galaxy Devices:**
- Galaxy S25, S25+, S25 Ultra (Android 15+)
  - ⭐ **Supports multimodal Gemini Nano** (text + image input)

**Requirements:**
- Android 14+ (Android 15+ for latest features)
- Google Play Services updated
- Gemini Nano model downloaded (auto-downloads on supported devices)

### Devices Using Cloud API (Fallback)
- All other Android devices (Samsung S23, OnePlus, etc.)
- Older Pixel devices (Pixel 7 and earlier)
- Android emulators
- Devices below Android 14

## Performance & Privacy

### On-Device (Gemini Nano)
- ✅ **Privacy**: All processing on device, no data sent to cloud
- ✅ **Speed**: ~100-500ms response time
- ✅ **Cost**: Free, no API costs
- ✅ **Multimodal**: Text + images on Samsung S25 series
- ⚠️ **Limitation**: Smaller model, may be less capable for complex tasks

### Cloud (Gemini 2.5 Flash)
- ⚠️ **Privacy**: Data sent to Google AI servers
- ✅ **Speed**: ~500-2000ms response time (depends on network)
- ⚠️ **Cost**: Free tier: 15 requests/minute, 1500 requests/day
- ✅ **Capability**: More powerful model, better at complex reasoning
- ✅ **Multimodal**: Text + images + video support

## Troubleshooting

### "No API key configured" error
- Ensure `GOOGLE_AI_API_KEY` is set in `local.properties` or environment
- Rebuild project: `gradlew clean assembleDebug`
- Check `BuildConfig.GOOGLE_AI_API_KEY` is generated

### Gemini Nano not loading
- Check device compatibility (Android 14+, Pixel 8a+)
- Update Google Play Services
- Check logcat for "AiModule" logs to see which backend is selected

### Slow responses
- Cloud API: Check internet connection
- Nano: First request may be slower (model loading)
- Consider reducing `maxOutputTokens` for faster responses

### Rate limiting (Cloud API)
- Free tier: 15 requests/minute, 1500/day
- If exceeded, add delay between requests or upgrade to paid tier
- Consider using Nano for high-frequency features

## Next Steps

1. ✅ **Setup complete** - AI backend is configured with fallback
2. 🎯 **Test the AI Assistant screen** - Navigate to AI Assistant in app
3. 🚀 **Add AI to other screens**:
   - HomeScreen: Add viewing mood banner
   - SearchScreen: Integrate smart search
   - TvScreens: Add voice commands and summaries
4. 📊 **Monitor usage** - Track which backend is being used in analytics

## Example: Adding AI to HomeScreen

```kotlin
// In HomeViewModel
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val aiRepository: GenerativeAiRepository // ← Add this
) : ViewModel() {

    private val _viewingMood = MutableStateFlow<String?>(null)
    val viewingMood: StateFlow<String?> = _viewingMood.asStateFlow()

    fun loadViewingMood() {
        viewModelScope.launch {
            when (val result = jellyfinRepository.getContinueWatching()) {
                is ApiResult.Success -> {
                    val mood = aiRepository.analyzeViewingHabits(result.data)
                    _viewingMood.value = mood
                }
                else -> Unit
            }
        }
    }
}

// In HomeScreen.kt
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val viewingMood by viewModel.viewingMood.collectAsStateWithLifecycle()

    // Show mood banner
    viewingMood?.let { mood ->
        Text(
            text = mood,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}
```

---

**Questions?** Check the [Firebase AI documentation](https://firebase.google.com/docs/ai) or file an issue.
