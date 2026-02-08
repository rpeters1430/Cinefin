# Device Compatibility - Gemini AI Features

This document provides a comprehensive list of devices supporting Gemini Nano (on-device AI) for the Jellyfin Android app.

## ✅ Devices with Gemini Nano Support

### Google Pixel Devices

#### Pixel 8 Series (Android 14+)
- Pixel 8
- Pixel 8 Pro
- Pixel 8a

**Gemini Nano Version:** Text-only AI
**Privacy:** ✅ Fully on-device, no cloud communication
**Speed:** ✅ Fast (~100-500ms)
**Availability:** Auto-downloads via Google Play Services

---

#### Pixel 9 Series (Android 14+)
- Pixel 9
- Pixel 9 Pro
- Pixel 9 Pro XL
- Pixel 9 Pro Fold

**Gemini Nano Version:** Text-only AI
**Privacy:** ✅ Fully on-device, no cloud communication
**Speed:** ✅ Fast (~100-500ms)
**Availability:** Auto-downloads via Google Play Services

---

#### Pixel 10 Series (Android 15+)
- Pixel 10
- Pixel 10 Pro
- Pixel 10 Pro XL

**Gemini Nano Version:** Text-only AI
**Privacy:** ✅ Fully on-device, no cloud communication
**Speed:** ✅ Very fast (~100-300ms)
**Availability:** Auto-downloads via Google Play Services

---

#### Pixel Fold Devices (Android 14+)
- Pixel Fold (all variants)
- Pixel 9 Pro Fold

**Gemini Nano Version:** Text-only AI
**Privacy:** ✅ Fully on-device, no cloud communication
**Speed:** ✅ Fast (~100-500ms)
**Availability:** Auto-downloads via Google Play Services

---

### Samsung Galaxy Devices

#### Galaxy S25 Series (Android 15+) ⭐ **NEW**
- Galaxy S25
- Galaxy S25+
- Galaxy S25 Ultra

**Gemini Nano Version:** **Multimodal AI (Text + Images)** ⭐
**Privacy:** ✅ Fully on-device, no cloud communication
**Speed:** ✅ Fast (~500ms-1s for multimodal)
**Special Features:**
- ✅ Analyze images (movie posters, screenshots)
- ✅ Visual search
- ✅ Image-based recommendations
- ✅ Genre detection from posters

**Availability:** Pre-installed with One UI 7 (Android 15)

---

## ❌ Devices Using Cloud API Fallback

All other Android devices will automatically use **Gemini 2.5 Flash (Cloud API)** as fallback:

### Samsung Devices (Non-S25)
- Galaxy S24 series (S24, S24+, S24 Ultra)
- Galaxy S23 series (S23, S23+, S23 Ultra)
- Galaxy S22 and older
- Galaxy A series
- Galaxy Z Fold series (non-Pixel Fold)
- Galaxy Z Flip series

### Google Pixel (Older Models)
- Pixel 7 series and older
- Pixel 6 series
- Pixel 5 and older

### Other Manufacturers
- OnePlus (all models)
- Xiaomi (all models)
- Motorola (all models)
- Nothing (all models)
- Sony (all models)
- All other Android devices

### Emulators
- Android Emulator (all versions)
- Genymotion
- BlueStacks

---

## Cloud API Requirements

Devices without Gemini Nano will use the cloud API, which requires:

1. **Google AI API Key**
   - Get free key: https://aistudio.google.com/apikey
   - Add to `gradle.properties`: `GOOGLE_AI_API_KEY=your-key-here`
   - (Also works in `local.properties` or as environment variable)

2. **Internet Connection**
   - Active WiFi or mobile data
   - ~1-3 seconds response time (network dependent)

3. **API Quota (Free Tier)**
   - 15 requests per minute
   - 1,500 requests per day
   - Upgrade available for higher limits

---

## Feature Comparison

| Feature | Gemini Nano (Pixel) | Gemini Nano (S25) ⭐ | Cloud API |
|---------|---------------------|---------------------|-----------|
| **Text Chat** | ✅ | ✅ | ✅ |
| **Streaming** | ✅ | ✅ | ✅ |
| **Image Analysis** | ❌ | ✅ | ✅ |
| **Video Analysis** | ❌ | ❌ | ✅ |
| **Privacy** | 🔒 On-Device | 🔒 On-Device | ⚠️ Cloud |
| **Speed** | ⚡ Fast | ⚡ Fast | 🌐 Network-dependent |
| **Internet Required** | ❌ | ❌ | ✅ |
| **Cost** | Free | Free | Free (with limits) |

---

## How Detection Works

The app automatically detects device capabilities at startup:

```
App Launches
    ↓
AiModule Initializes
    ↓
Try to create Gemini Nano model
    ↓
Test with simple prompt (3s timeout)
    ↓
┌─────────────────────────┬──────────────────────────┬─────────────────────────┐
│ Pixel 8/9/10/Fold       │ Samsung S25 Series       │ All Other Devices       │
├─────────────────────────┼──────────────────────────┼─────────────────────────┤
│ ✅ Gemini Nano loaded   │ ✅ Gemini Nano loaded    │ ❌ Nano unavailable     │
│ Text-only AI            │ Multimodal AI ⭐         │ Fallback to Cloud API   │
│ On-device processing    │ On-device processing     │ Cloud processing        │
└─────────────────────────┴──────────────────────────┴─────────────────────────┘
```

### Logcat Output

Check which backend is being used:

```bash
# Successful Nano detection (Pixel)
AiModule: Using Gemini Nano (on-device)

# Successful Nano detection (S25)
AiModule: Using Gemini Nano (on-device)

# Fallback to Cloud API
AiModule: Gemini Nano unavailable, using cloud API
```

---

## UI Indicators

The app shows which AI backend is active:

### AI Assistant Screen Title
- **"Jellyfin AI Assistant (On-Device)"** → Using Gemini Nano (Pixel or S25)
- **"Jellyfin AI Assistant"** → Using Cloud API

### Future Enhancements
You can add more visible indicators:

```kotlin
// In AiAssistantScreen or any screen using AI
if (uiState.isOnDeviceAI) {
    Row {
        Icon(Icons.Default.Lock, contentDescription = "Privacy")
        Text("🔒 Private (On-Device AI)")
    }
} else {
    Row {
        Icon(Icons.Default.Cloud, contentDescription = "Cloud")
        Text("☁️ Cloud AI")
    }
}
```

---

## Requirements by Device Type

### Google Pixel 8/9/10/Fold
✅ **Android 14+** (Android 15+ for Pixel 10)
✅ **Google Play Services** updated
✅ **Gemini Nano** auto-downloads on first use
❌ No API key needed (falls back to cloud if Nano fails)

### Samsung Galaxy S25 Series
✅ **Android 15** (One UI 7)
✅ **Google Play Services** updated
✅ **Gemini Nano with multimodal** pre-installed
❌ No API key needed (falls back to cloud if Nano fails)

### All Other Devices
✅ **Android 8+** (minSdk 26)
✅ **Internet connection**
✅ **Google AI API Key** (required)
⚠️ Add key to `gradle.properties`: `GOOGLE_AI_API_KEY=your-key-here`

---

## Testing Your Device

### Check if Gemini Nano is Available

1. Install the app on your device
2. Launch the app
3. Open Logcat:
   ```bash
   adb logcat -v time | findstr AiModule
   ```
4. Look for one of these messages:
   - `Using Gemini Nano (on-device)` ✅
   - `Gemini Nano unavailable, using cloud API` ❌

### Manual Test

1. Navigate to **AI Assistant** screen
2. Check the screen title:
   - Shows "(On-Device)" → Gemini Nano active ✅
   - No "(On-Device)" → Cloud API active ☁️
3. Try disconnecting WiFi/data:
   - Gemini Nano: Still works offline ✅
   - Cloud API: Shows error ❌

---

## Troubleshooting

### Gemini Nano Not Loading on Supported Device

**Issue:** You have a Pixel 8/9/10 or S25, but app uses cloud API

**Solutions:**
1. Update Google Play Services:
   - Settings → Apps → Google Play Services → Update
2. Check Android version:
   - Settings → About Phone → Android version
   - Must be Android 14+ (15+ for S25)
3. Clear app data and restart
4. Wait for Nano to download (can take 5-10 minutes on first launch)
5. Check logcat for detailed error messages

### Cloud API Not Working

**Issue:** App shows errors on non-Nano devices

**Solutions:**
1. Verify API key in `gradle.properties`:
   ```properties
   GOOGLE_AI_API_KEY=your-actual-key-here
   ```
2. Rebuild app: `gradlew clean assembleDebug`
3. Check API key is valid: https://aistudio.google.com/apikey
4. Ensure internet connection is active
5. Check API quota hasn't been exceeded

---

## Future Device Support

Google and Samsung are expanding Gemini Nano support. This document will be updated as new devices are announced.

**Expected future support:**
- More Samsung Galaxy devices (S26 series, etc.)
- Additional foldable devices
- Select flagship devices from other manufacturers

**Track announcements:**
- Google AI Blog: https://ai.google.dev/blog
- Samsung Newsroom: https://news.samsung.com

---

## Summary Table

| Device Family | Models | Gemini Nano | Multimodal | Android Ver |
|---------------|--------|-------------|------------|-------------|
| Pixel 8 | 8, 8 Pro, 8a | ✅ | ❌ | 14+ |
| Pixel 9 | 9, 9 Pro, 9 Pro XL, 9 Pro Fold | ✅ | ❌ | 14+ |
| Pixel 10 | 10, 10 Pro, 10 Pro XL | ✅ | ❌ | 15+ |
| Pixel Fold | All variants, 9 Pro Fold | ✅ | ❌ | 14+ |
| **Galaxy S25** ⭐ | **S25, S25+, S25 Ultra** | ✅ | ✅ | 15+ |
| Other Devices | All others | ☁️ Cloud API | ✅ | 8+ |

---

**Last Updated:** January 2026
**Questions?** See `AI_SETUP.md` and `AI_QUICKSTART.md` for implementation details.
