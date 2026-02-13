# AI Person Biography Feature

**Status**: ✅ Implemented
**Date**: February 13, 2026
**Priority**: Quick Win #1

---

## Overview

AI-generated biographies for actors/directors on the PersonDetailScreen. When users click on a cast member, they now see an AI-generated bio that analyzes the person's filmography in their library and highlights career information, known roles, and library presence.

---

## Implementation Details

### 1. Repository Method

**File**: `app/src/main/java/com/rpeters/jellyfin/data/repository/GenerativeAiRepository.kt`

Added `generatePersonBio()` method:
```kotlin
suspend fun generatePersonBio(
    personName: String,
    filmography: List<BaseItemDto>,
): String
```

**Features**:
- Uses primary model (Gemini Nano when available, cloud fallback)
- 15-second timeout to prevent long waits
- Analyzes up to 15 movies and 15 TV shows (configurable via Remote Config)
- Generates 2-3 sentence bio (max 60 words)
- Graceful fallback: "Featured in X movies and Y shows in your library"
- Analytics tracking for success/failure

**Prompt Structure**:
- Context: Movie count, TV count, notable titles
- Focus: Career highlights, acting style/genres, library presence
- Output: Concise, engaging, informative

---

### 2. ViewModel Updates

**File**: `app/src/main/java/com/rpeters/jellyfin/ui/viewmodel/PersonDetailViewModel.kt`

**Changes**:
- Injected `GenerativeAiRepository`
- Added `aiBio: String?` and `isBioLoading: Boolean` to `PersonDetailUiState.Success`
- Async bio generation after filmography loads
- State updates when bio completes or fails

**Flow**:
1. User navigates to PersonDetailScreen
2. ViewModel loads filmography from Jellyfin API
3. UI shows filmography immediately (tabs, grid)
4. ViewModel generates AI bio asynchronously
5. Bio card appears with loading indicator → content

---

### 3. UI Component

**File**: `app/src/main/java/com/rpeters/jellyfin/ui/screens/PersonDetailScreen.kt`

**New Component**: `AiBioCard`

**Design Features**:
- Material 3 Card with `primaryContainer` background
- Gradient sparkle icon (primary → tertiary)
- "AI Biography" header with AI badge
- Loading state: Linear progress indicator
- Content state: Bio text with improved line height
- Error state: Italic fallback message
- Animated visibility (fade + expand)

**Placement**: Above the tab row (All/Movies/TV Shows)

---

## Feature Flags & Remote Config

**Feature Flags** (`core/FeatureFlags.kt`):
- `ai_person_bio` - Enable/disable feature
- `ai_person_bio_context_size` - Number of items to analyze (default: 15)

**Usage**:
```kotlin
if (remoteConfig.getBoolean("ai_person_bio")) {
    // Show AI bio
}
```

---

## Analytics

Tracked via `AnalyticsHelper`:
```kotlin
analytics.logAiEvent(
    feature = "person_bio",
    success = true/false,
    backend = "nano" / "cloud",
    latency_ms = duration
)
```

**Metrics to Monitor**:
- Success rate
- Average latency
- On-device vs cloud usage
- Error types (timeout, API failure)

---

## User Experience

**Before**:
- Person detail screen showed only filmography grid
- No context about the person's career or roles

**After**:
- Beautiful AI-generated bio appears at the top
- Loading state shows progress
- Bio highlights career, genres, library presence
- Example: "Versatile character actor known for intense dramatic roles in crime thrillers like The Departed and Shutter Island. Featured in 12 movies and 3 shows in your library."

---

## Testing Checklist

- [ ] Click on actor from Movie detail → bio generates
- [ ] Click on actor from TV Show detail → bio generates
- [ ] Bio loading state shows progress indicator
- [ ] Bio appears with correct content
- [ ] Bio handles failure gracefully (fallback message)
- [ ] AI badge and sparkle icon display correctly
- [ ] Works with on-device Gemini Nano (Samsung S25)
- [ ] Falls back to cloud API on older devices
- [ ] Feature flag disables bio when turned off
- [ ] Analytics events fire correctly

---

## Performance

**Target Metrics**:
- < 3s average generation time (on-device)
- < 5s average generation time (cloud)
- < 10% error rate
- 100% of Nano-compatible devices use on-device

**Optimization**:
- 15-second timeout prevents indefinite waits
- Cache would be beneficial (future enhancement)
- Context size configurable via Remote Config

---

## Future Enhancements

1. **Bio Caching** - Cache generated bios by personId to avoid regeneration
2. **Person Image** - Add person's photo from Jellyfin metadata API
3. **Expandable Bio** - Allow long bios to be collapsed/expanded
4. **Role Information** - Show character names for each film appearance
5. **Career Timeline** - Visual timeline of roles by year
6. **On-Device Indicator** - Show privacy badge when using Gemini Nano
7. **Bio Regeneration** - Refresh button to regenerate with different prompt

---

## Known Limitations

1. **No Caching** - Bio is regenerated on every screen visit
   - Workaround: Add simple in-memory cache by personId
2. **No Person Image** - Screen doesn't show person's photo
   - Requires: Jellyfin person metadata API integration
3. **Fixed Context Size** - Always analyzes 15 items
   - Configurable via Remote Config, but not user-facing setting
4. **No Edit/Copy** - Users can't edit or copy the bio text
   - Future: Add copy-to-clipboard button

---

## Related Files

**New Code**:
- `GenerativeAiRepository.kt`: `generatePersonBio()` method
- `PersonDetailViewModel.kt`: Bio state management
- `PersonDetailScreen.kt`: `AiBioCard` component

**Modified Files**:
- `core/FeatureFlags.kt`: Added AI person bio flags

**Documentation**:
- `AI_INTEGRATION_OPPORTUNITIES.md`: Full proposal
- `AI_PERSON_BIO_FEATURE.md`: This file

---

## Example Output

**Input**:
- Person: Tom Hanks
- Filmography: Forrest Gump, Cast Away, Saving Private Ryan, The Green Mile, Captain Phillips, Sully, etc.

**AI Output**:
> "Award-winning actor known for transformative performances in dramatic roles spanning war films (Saving Private Ryan), survival stories (Cast Away), and heartfelt dramas (Forrest Gump, The Green Mile). Featured in 15 movies in your library, showcasing versatility across genres."

---

**Status**: ✅ Complete and ready for testing
**Next Quick Win**: Thematic Analysis for detail screens
