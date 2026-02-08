# AI Assistant - Quick Start

## ✅ What's Already Done

Your AI backend is now fully configured with:

1. **Smart Fallback System**
   - ✅ Tries Gemini Nano (on-device) first
   - ✅ Falls back to Gemini 2.5 Flash (cloud API) automatically
   - ✅ Tracks which backend is active

2. **Repository Pattern**
   - ✅ `GenerativeAiRepository` - centralized AI logic
   - ✅ Can be used from any screen (phone, tablet, TV)
   - ✅ Pre-built methods for common AI tasks

3. **UI Integration**
   - ✅ `AiAssistantScreen` - chat interface with recommendations
   - ✅ Already added to navigation graph
   - ✅ Shows "(On-Device)" indicator when using Gemini Nano

## 🚀 Next Steps (5 minutes)

### Step 1: Get Your API Key
1. Go to https://aistudio.google.com/apikey
2. Click "Get API Key"
3. Copy the API key

### Step 2: Add to gradle.properties
Open `gradle.properties` in your project root (it's already gitignored) and add:
```properties
GOOGLE_AI_API_KEY=paste-your-api-key-here
```

**Note:** `gradle.properties` is already in .gitignore - your API key won't be committed.

**Alternative:** You can also use `local.properties` or set an environment variable `GOOGLE_AI_API_KEY`. The build script checks all three locations.

### Step 3: Build & Test
```bash
# Clean build
gradlew clean assembleDebug

# Install on device
gradlew installDebug
```

### Step 4: Try It Out
1. Launch the app
2. Navigate to **AI Assistant** (should be in your navigation)
3. Check logcat to see which backend loaded:
   ```
   AiModule: Using Gemini Nano (on-device)
   OR
   AiModule: Gemini Nano unavailable, using cloud API
   ```
4. Try asking:
   - "Find sci-fi movies"
   - "Recommend something funny"
   - "What's my viewing mood?"

## 📊 Backend Detection

The app automatically detects the best AI backend at startup:

| Scenario | Backend Used | Why |
|----------|-------------|-----|
| Supported devices (see below) with Nano installed | **Gemini Nano** | On-device AI available |
| Samsung S25 series with Nano | **Gemini Nano (Multimodal)** | Text + image support |
| Unsupported device or Nano not installed | **Gemini 2.5 Flash** | Cloud API fallback |
| No API key configured | **Error** | Need API key for cloud fallback |

### Devices with Gemini Nano Support

**✅ Supported Devices:**
- **Google Pixel**: 8/8 Pro/8a, 9/9 Pro/9 Pro XL/9 Pro Fold, 10/10 Pro/10 Pro XL, Fold (all variants)
- **Samsung Galaxy**: S25/S25+/S25 Ultra ⭐ (with multimodal support)

**Requirements**: Android 14+ (Android 15+ for S25), Google Play Services updated

## 🎯 Using AI in Other Screens

The `GenerativeAiRepository` is injected and ready to use anywhere:

### Example: Add to HomeScreen
```kotlin
// In HomeViewModel.kt - add AI repository
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val aiRepository: GenerativeAiRepository // ← Add this
) : ViewModel() {

    fun analyzeViewingMood() {
        viewModelScope.launch {
            val recent = jellyfinRepository.getContinueWatching()
            if (recent is ApiResult.Success) {
                val mood = aiRepository.analyzeViewingHabits(recent.data)
                // Show mood in UI
            }
        }
    }
}
```

## 🔧 Available AI Methods

```kotlin
// Simple chat
aiRepository.generateResponse("What movies are trending?")

// Stream responses (for typing effect)
aiRepository.generateResponseStream("Recommend something").collect { response ->
    // Update UI in real-time
}

// Analyze viewing habits
aiRepository.analyzeViewingHabits(recentItems)

// Smart search
aiRepository.smartSearchQuery("movies like inception")

// Generate recommendations
aiRepository.generateRecommendations(recentItems, "I like action")

// Summarize item (useful for TV)
aiRepository.summarizeItem(movieItem)

// Check if on-device
aiRepository.isUsingOnDeviceAI() // true if Gemini Nano
```

## 🔍 Troubleshooting

### Build Error: "GOOGLE_AI_API_KEY not found"
- Ensure `GOOGLE_AI_API_KEY` is in `gradle.properties`
- Rebuild: `gradlew clean build`
- Check file location: `C:\Users\James\Desktop\JellyfinAndroid\gradle.properties`

### Runtime Error: "API key not configured"
- The cloud fallback was triggered but no API key found
- Add API key to `gradle.properties` and rebuild
- Or set environment variable: `GOOGLE_AI_API_KEY=your-key`

### Gemini Nano Not Loading
- Requires Android 14+ and compatible device (Pixel 8a+)
- Check Play Services is updated
- Nano will auto-download on supported devices
- Fallback to cloud API is automatic if unavailable

### Slow Responses
- **First request**: Model initialization (~2-3 seconds)
- **Cloud API**: Network dependent (~1-2 seconds)
- **Gemini Nano**: Very fast after initialization (~100-500ms)

## 📱 Testing on Different Devices

| Device Type | Expected Backend | Notes |
|-------------|------------------|-------|
| Pixel 8/9/10 series | Gemini Nano | Text-only AI |
| Pixel Fold | Gemini Nano | Text-only AI |
| Samsung Galaxy S25 series | Gemini Nano (Multimodal) | ⭐ Text + image support |
| Samsung Galaxy S23/S24 | Cloud API | Nano not available |
| Other Android phones | Cloud API | Nano not available |
| Emulator (any) | Cloud API | Nano not available |

## 🎨 UI Customization

The AI Assistant screen shows which backend is active:
- **Title**: "Jellyfin AI Assistant (On-Device)" ← Gemini Nano
- **Title**: "Jellyfin AI Assistant" ← Cloud API

You can customize this in `AiAssistantScreen.kt` line 74-78.

## 📈 Next Features to Add

Here are some ideas for using AI in your app:

1. **HomeScreen Mood Banner**
   - Show "You're on a sci-fi kick!" based on watch history
   - Use: `aiRepository.analyzeViewingHabits()`

2. **Smart Search Enhancement**
   - User types "movies like the matrix"
   - AI extracts: ["Matrix", "Sci-Fi", "Action", "Keanu Reeves"]
   - Use: `aiRepository.smartSearchQuery()`

3. **TV Voice Commands**
   - User says "Play something funny"
   - AI parses intent and searches comedies
   - Use: `aiRepository.generateResponse()`

4. **Personalized Recommendations**
   - "Based on your recent watches, try these..."
   - Use: `aiRepository.generateRecommendations()`

5. **Accessibility (TV)**
   - Summarize long descriptions for TV viewing
   - Use: `aiRepository.summarizeItem()`

6. **Multimodal Features (Samsung S25)** ⭐ NEW
   - Analyze movie posters to suggest similar content
   - Describe scenes from video thumbnails
   - Visual search: "Find movies with this vibe" (upload image)
   - **Note**: Requires multimodal Gemini Nano (S25 only) or cloud API

## 📖 Full Documentation

See `AI_SETUP.md` for complete documentation including:
- Architecture details
- Advanced usage examples
- Performance & privacy considerations
- API reference

---

**Ready to test!** Add your API key to `local.properties` and build the app.
