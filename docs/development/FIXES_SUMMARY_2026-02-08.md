# Bug Fixes Summary - February 8, 2026

## Issues Fixed

### 1. ✅ TV Episode Detail Hero Scrolling Bug
**Problem**: Hero image at the top of immersive TV episode detail screen scrolled with content instead of staying static (like screenshot 1 showed).

**Root Cause**: The hero was using `ParallaxHeroSection` inside the `LazyColumn`, causing it to scroll with the content.

**Solution**: Restructured to use Box-layering pattern (same as `ImmersiveHomeVideoDetailScreen`):
- Moved hero to `StaticHeroSection` OUTSIDE the `LazyColumn`
- Hero content moved into `LazyColumn` as first item (overlays hero initially)
- Added 1dp background spacer item that extends as user scrolls to cover hero cleanly
- Wrapped all content items in background boxes to prevent hero bleed-through

**Files Changed**:
- `app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveTVEpisodeDetailScreen.kt`

---

### 2. ✅ Transcoding Diagnostics Logic Contradiction
**Problem**: Screenshot 2 showed items displaying "DIRECT PLAY" badge but also showing "Why transcoding needed:" section with reasons (contradictory).

**Root Cause**: The UI was checking `video.transcodingReasons.isNotEmpty()` to show the reasons section, but `DirectPlayAnalysis` can have issues/warnings even when `canDirectPlay = true` (e.g., software decoding warnings that don't prevent direct play).

**Solution**: Changed condition from:
```kotlin
if (video.transcodingReasons.isNotEmpty())
```
to:
```kotlin
if (video.needsTranscoding && video.transcodingReasons.isNotEmpty())
```

Now the "Why transcoding needed:" section ONLY appears when transcoding is actually required.

**Files Changed**:
- `app/src/main/java/com/rpeters/jellyfin/ui/screens/TranscodingDiagnosticsScreen.kt` (line 229)

---

### 3. ✅ Excessive RAM Detection Logging Spam
**Problem**: Logcat showed hundreds of "Detected total RAM: 6211MB" logs (performance issue and log pollution).

**Root Cause**: `DeviceCapabilities.getTotalRAM()` was being called repeatedly without caching. It's called by `getDevicePerformanceProfile()` which is called by multiple methods during initialization.

**Solution**: Added caching mechanism similar to other device capabilities:
- Added `private var totalRAM: Long? = null` field
- Updated `getTotalRAM()` to check cache first: `totalRAM?.let { return it }`
- Cache result after detection: `totalRAM = result`

**Performance Impact**: Eliminates hundreds of redundant system calls and log entries.

**Files Changed**:
- `app/src/main/java/com/rpeters/jellyfin/data/DeviceCapabilities.kt` (lines 44, 648-670)

---

### 4. ✅ Item Navigation in Transcoding Diagnostics
**Problem**: User requested ability to click on items in diagnostics screen to navigate to the actual media item (Movie/Episode detail screen).

**Solution**: Implemented full navigation support:
1. **ViewModel Changes**:
   - Added `item: BaseItemDto` and `itemType: String` to `VideoAnalysis` data class
   - Updated `analyzeVideo()` to capture full item and type (e.g., "MOVIE", "EPISODE")
   - Added sorting by itemType to group Movies and Episodes

2. **UI Changes**:
   - Made `VideoAnalysisCard` clickable using `Card(onClick = ...)`
   - Added item type badge below title (shows "Movie" or "Episode")
   - Added chevron icon (→) to indicate clickability
   - Added `onItemClick` parameter to screen

3. **Navigation Changes**:
   - Updated `ProfileNavGraph.kt` to handle clicks with full routing logic:
     - Movies → `Screen.MovieDetail`
     - Episodes → `Screen.TVEpisodeDetail`
     - Videos → `Screen.HomeVideoDetail`
     - Series → `Screen.TVSeasons`
     - Others → `Screen.ItemDetail`

**User Experience**: Users can now tap any video in the diagnostics list to jump directly to its detail page for playback or more information.

**Files Changed**:
- `app/src/main/java/com/rpeters/jellyfin/ui/viewmodel/TranscodingDiagnosticsViewModel.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/screens/TranscodingDiagnosticsScreen.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/navigation/ProfileNavGraph.kt`

---

## Build Status

✅ **BUILD SUCCESSFUL in 1m 37s**

All changes compile successfully with no warnings or errors.

---

## Testing Recommendations

### Manual Testing Checklist

1. **TV Episode Detail Hero**:
   - [ ] Navigate to any TV episode detail screen
   - [ ] Verify hero image stays static (doesn't scroll)
   - [ ] Verify content scrolls over hero smoothly
   - [ ] Verify no hero bleed-through on scrollable content

2. **Transcoding Diagnostics Logic**:
   - [ ] Navigate to Settings → Transcoding Diagnostics
   - [ ] Verify items showing "DIRECT PLAY" do NOT show "Why transcoding needed:" section
   - [ ] Verify items showing "TRANSCODE" DO show reasons (if available)

3. **RAM Detection**:
   - [ ] Clear logcat: `adb logcat -c`
   - [ ] Launch app fresh
   - [ ] Navigate to any screen
   - [ ] Check logcat: `adb logcat -d | grep "Detected total RAM"`
   - [ ] Verify only 1-2 log entries (not hundreds)

4. **Diagnostics Navigation**:
   - [ ] Navigate to Settings → Transcoding Diagnostics
   - [ ] Tap on a Movie item → should navigate to Movie Detail screen
   - [ ] Go back, tap on an Episode item → should navigate to Episode Detail screen
   - [ ] Verify chevron icon (→) is visible on all items
   - [ ] Verify item type badge shows "MOVIE" or "EPISODE"

---

## Related Files

### Modified Files (7 total)
1. `app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveTVEpisodeDetailScreen.kt`
2. `app/src/main/java/com/rpeters/jellyfin/ui/screens/TranscodingDiagnosticsScreen.kt`
3. `app/src/main/java/com/rpeters/jellyfin/ui/viewmodel/TranscodingDiagnosticsViewModel.kt`
4. `app/src/main/java/com/rpeters/jellyfin/data/DeviceCapabilities.kt`
5. `app/src/main/java/com/rpeters/jellyfin/ui/navigation/ProfileNavGraph.kt`

### Screenshots Analyzed
- `Screenshot_20260208_202929.png` - TV Episode Detail hero scrolling issue
- `Screenshot_20260208_203202.png` - Transcoding Diagnostics contradiction
- `latest_logcat` - RAM detection spam logs

---

## Key Patterns Applied

### Box-Layering Pattern (Issue #1)
For immersive screens with static heroes:
```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    // Static hero (outside LazyColumn)
    StaticHeroSection(imageUrl = ..., height = ...)

    // Scrollable content
    LazyColumn {
        item { /* Hero content overlay */ }
        item { /* 1dp background spacer */ }
        item { Box(modifier = Modifier.background(...)) { /* Content */ } }
    }
}
```

### Caching Pattern (Issue #3)
For expensive operations that shouldn't be repeated:
```kotlin
private var cachedValue: Type? = null

private fun getValue(): Type {
    cachedValue?.let { return it }

    val result = expensiveComputation()
    cachedValue = result
    return result
}
```

### Navigation Handler Pattern (Issue #4)
Standard pattern for item navigation in the app:
```kotlin
onItemClick = { item ->
    when (item.type) {
        BaseItemKind.MOVIE -> navController.navigate(Screen.MovieDetail.createRoute(id))
        BaseItemKind.EPISODE -> navController.navigate(Screen.TVEpisodeDetail.createRoute(id))
        // ... other types
    }
}
```

---

## Next Steps

**Recommended**:
1. Test all fixes on real device (especially hero scrolling behavior)
2. Verify RAM detection logs are reduced in production builds
3. Add analytics event for diagnostics navigation feature usage
4. Consider adding filter/sort options to Transcoding Diagnostics screen (by type, by status)

**Optional Enhancements**:
- Add "Home Videos" support to diagnostics screen (currently only Movies/Episodes)
- Add search/filter functionality to diagnostics list
- Show percentage of library that needs transcoding in summary card
- Add export diagnostics report feature (CSV/JSON)
