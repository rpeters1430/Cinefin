# Immersive UI Bug Fixes

## Issues Fixed (Round 2 - 2026-02-06 Evening)

### 4. Hero Content Cut Off By Top Bar (When at Top of Screen)
**Problem**: When scrolled to the very top, the hero carousel's text content (title/subtitle) was being cut off by the translucent top bar.

**Root Cause**: The hero image goes full-bleed (correct), but the text overlay had no awareness of the top bar, so text overlapped with it.

**Solution**: Added safe area padding to hero content overlay
- Calculate `safeTopPadding = statusBarHeight + topBarHeight + 16dp`
- Apply to the Column containing title/subtitle as top padding
- Content now stays visible below the top bar

**Files Changed**:
- `ui/components/immersive/ImmersiveHeroCarousel.kt` (line 194-211)
  - Added WindowInsets.statusBars import
  - Added safe top padding calculation
  - Applied padding to content Column

---

### 5. Crash: Duplicate LazyColumn Keys in TV Shows Screen
**Problem**: App crashed when scrolling library screen with error:
```
Key "Discover More" was already used. If you are using LazyColumn/Row please make sure you provide a unique key for each item.
```

**Root Cause**: In `organizeTVShowsIntoSections()`, when there were multiple chunks of remaining TV shows (more than 15), they were all given the title "Discover More". Since LazyColumn uses `key = { it.title }`, all sections had duplicate keys.

**Solution**: Made section titles unique by appending chunk index
- First chunk: "More TV Shows" (unchanged)
- Subsequent chunks: "Discover More 2", "Discover More 3", etc.
- Each section now has a guaranteed unique key

**Files Changed**:
- `ui/screens/ImmersiveTVShowsScreen.kt` (line 243-251)

---

## Issues Fixed (Round 1 - 2026-02-06 Morning)

### 1. Hero Carousel Goes Under Top Bar
**Problem**: The hero carousel was being pushed down by top padding, starting below the translucent top bar instead of behind it.

**Root Cause**: LazyColumn had `contentPadding.top = statusBarHeight + 64dp` which pushed ALL content including the hero down.

**Solution**:
- Removed top padding from LazyColumn (`top = 0.dp`)
- Hero now starts at screen top and goes full-bleed behind the translucent top bar
- This creates the immersive Netflix-style design where the hero fills the entire screen

**Files Changed**:
- `ui/screens/ImmersiveHomeScreen.kt` (line 360)
- `ui/screens/ImmersiveMoviesScreen.kt` (line 113)
- `ui/screens/ImmersiveTVShowsScreen.kt` (line 116)

---

### 2. Top Bar Flickering/Oscillating
**Problem**: When scrolling, the top bar would hide, then pop back, then try to hide again, creating a flickering effect.

**Root Cause**:
- `rememberAutoHideTopBarVisible` used `nearTopOffsetPx = 140px` by default
- Hero is 480dp tall (~1440px on most phones)
- The "always show when near top" logic (< 140px) was fighting with "hide when scrolling down" logic
- Direction changes reset the accumulator, causing unstable behavior

**Solution**:
1. **Increased nearTopOffsetPx to hero height**: Now uses `ImmersiveDimens.HeroHeightPhone.toPx().toInt()` (~1440px)
   - Top bar only shows when truly at the very top (within hero height)
   - Prevents flicker while scrolling through the hero

2. **Simplified scroll detection logic**:
   - Removed complex direction accumulator that reset on direction changes
   - Now uses simple delta tracking with hysteresis (50px threshold)
   - More stable and predictable behavior

**Files Changed**:
- `ui/components/immersive/TopBarVisibility.kt` (refactored entire function)
- `ui/screens/ImmersiveHomeScreen.kt` (line 116-121)
- `ui/screens/ImmersiveMoviesScreen.kt` (line 49-53)
- `ui/screens/ImmersiveTVShowsScreen.kt` (line 52-56)

---

### 3. Hero Image Behavior (Not Actually a Bug)
**Problem Reported**: Hero image scrolls down the screen instead of sticking at the top.

**Clarification**: This is **intentional Netflix-style behavior**:
- The hero carousel is meant to scroll away as you browse content
- It's not pinned/collapsed like a CollapsingToolbar
- The issue was that it was starting BELOW the top bar due to bug #1
- Now that it starts behind the top bar (full-bleed), the behavior is correct

**Alternative Implementation** (if sticky hero is desired):
- Would need to move hero outside LazyColumn
- Use Box with overlapping content
- Implement CollapsingToolbar-style pinning
- This is a different design pattern (not currently implemented)

---

## Technical Details

### Auto-Hide Top Bar Logic (Simplified)

**Before** (Buggy):
```kotlin
fun rememberAutoHideTopBarVisible(
    nearTopOffsetPx: Int = 140,  // Too small for 480dp hero!
    toggleThresholdPx: Int = 24,
) {
    // Complex accumulator that resets on direction change
    // Caused flickering
}
```

**After** (Fixed):
```kotlin
fun rememberAutoHideTopBarVisible(
    nearTopOffsetPx: Int = 140,      // Now customizable per screen
    toggleThresholdPx: Int = 50,     // Increased for stability
) {
    // Simple delta tracking
    val totalOffset = if (index == 0) offset else Int.MAX_VALUE

    if (totalOffset <= nearTopOffsetPx) {
        isVisible = true  // Always show at top
    } else if (abs(delta) >= toggleThresholdPx) {
        isVisible = delta < 0  // Show when scrolling up, hide when down
    }
}
```

### Content Padding Strategy

**Before** (Buggy):
```kotlin
LazyColumn(
    contentPadding = PaddingValues(
        top = statusBars + 64.dp,  // Pushes hero down
        bottom = 120.dp,
    )
)
```

**After** (Fixed):
```kotlin
LazyColumn(
    contentPadding = PaddingValues(
        top = 0.dp,       // Hero goes full-bleed
        bottom = 120.dp,  // Space for FAB/mini player
    )
)
```

The top bar is overlaid using `Box` in `ImmersiveScaffold`, so content naturally scrolls behind it.

---

## Testing Checklist

- [x] Build compiles successfully
- [ ] Test on phone (360x800dp): Hero is full-screen, top bar auto-hides smoothly
- [ ] Test on tablet (600x960dp): Hero scales appropriately
- [ ] Scroll performance: No jank, smooth 60fps
- [ ] Top bar behavior: No flickering when scrolling through hero
- [ ] Content readability: Text doesn't overlap with top bar when scrolling
- [ ] All three immersive screens work: Home, Movies, TV Shows

---

## Files Modified

1. **ui/components/immersive/TopBarVisibility.kt**
   - Refactored `rememberAutoHideTopBarVisible` for stability
   - Simplified logic, increased default threshold

2. **ui/screens/ImmersiveHomeScreen.kt**
   - Added LocalDensity import
   - Customized nearTopOffsetPx to hero height
   - Removed top contentPadding

3. **ui/screens/ImmersiveMoviesScreen.kt**
   - Added LocalDensity import
   - Customized nearTopOffsetPx to hero height
   - Removed top contentPadding
   - Removed unused topBarPadding calculation

4. **ui/screens/ImmersiveTVShowsScreen.kt**
   - Added LocalDensity import
   - Customized nearTopOffsetPx to hero height
   - Removed top contentPadding
   - Removed unused topBarPadding calculation

---

## Design Rationale

### Why Full-Bleed Hero?
- **Immersive experience**: Maximizes visual impact
- **Netflix/Disney+ pattern**: Industry-standard design
- **Reduces chrome**: Less UI chrome = more focus on content
- **Better use of space**: Especially important on phones

### Why Auto-Hide Top Bar?
- **More screen real estate**: Content gets full height when scrolling
- **Focus on content**: UI fades away when browsing
- **Easy access**: Scrolling up reveals navigation instantly
- **Modern UX**: Expected behavior in media apps

### Why Translucent Top Bar?
- **Smooth transitions**: No harsh edges
- **Content preview**: Still see hero image behind
- **Visual hierarchy**: Clearly indicates overlay vs content
- **Material Design 3**: Follows M3 guidelines

---

## Performance Notes

All animations use `graphicsLayer` for GPU acceleration:
- Top bar slide: `translationY` via `graphicsLayer`
- Hero parallax: `translationY` via `graphicsLayer`
- No layout recomposition during animations

Expected performance: 60fps on mid-range devices (2020+)

---

## Future Improvements

1. **Adaptive hero height**: Use different heights for phone/tablet/TV
2. **Smart scroll behavior**: Pause auto-scroll while user is scrolling
3. **Accessibility**: Add option to disable auto-hide for screen readers
4. **Configuration**: Make auto-hide behavior configurable via Remote Config
5. **CollapsingToolbar variant**: Alternative layout with sticky hero (if desired)
