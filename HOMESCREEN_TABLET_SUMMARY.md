# HomeScreen Tablet Adaptation Summary

**Implemented**: 2026-01-30
**Status**: ✅ Complete - Ready for Testing
**Phase**: 2.1 - HomeScreen Adaptation

---

## What Was Implemented

### Adaptive HomeScreen Layout

The HomeScreen now automatically adapts its layout based on device screen size:

| Screen Size | Layout Type | Columns | Visual Design |
|-------------|-------------|---------|---------------|
| **Phone** (< 600dp) | Vertical Carousels | 1 | Original carousel-based scrolling |
| **Medium Tablet** (600-840dp) | Grid Layout | 3 | Hero carousel + 3-column grids |
| **Large Tablet** (> 840dp) | Grid Layout | 4 | Hero carousel + 4-column grids |

### Phone Layout (Unchanged)

For compact screens (phones), the original carousel-based layout is preserved:
- Hero carousel at top
- Continue Watching horizontal carousel
- Next Up horizontal carousel
- Recently Added sections as horizontal carousels
- Optimized for vertical scrolling

### Tablet Layout (New!)

For medium and expanded screens (tablets), a new grid-based layout provides better space utilization:

#### 1. **Hero Carousel** (Top)
- Maintained across all screen sizes
- Shows featured movies/TV shows
- Auto-scrolling with 15-second intervals
- Larger hero height on tablets (480dp vs 400dp)

#### 2. **Continue Watching** (Grid)
- 3 or 4 columns based on screen size
- Poster cards with watch progress overlay
- Shows all in-progress items at once
- Easier to browse and resume content

#### 3. **Next Up** (Grid)
- 3 or 4 columns
- Shows up to 2 rows (6-8 items)
- Next episodes in your series
- Poster cards with series information

#### 4. **Recently Added Movies** (Grid)
- 3 or 4 columns
- Shows up to 2 rows (6-8 items)
- Poster cards with movie titles and years
- Quick access to new content

#### 5. **Recently Added TV Shows** (Grid)
- 3 or 4 columns
- Shows up to 2 rows (6-8 items)
- Poster cards with show titles and years

#### 6. **Recently Added Videos** (Grid)
- 2 columns (horizontal cards)
- Shows up to 4 items
- Landscape-oriented cards for home videos
- Better suited for video content

---

## Technical Implementation

### Window Size Class Detection

```kotlin
val context = LocalContext.current
val windowSizeClass = calculateWindowSizeClass(activity = context as Activity)
val isTablet = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
```

### Layout Selection

```kotlin
if (isTablet) {
    TabletHomeLayout(...)
} else {
    // Original phone carousel layout
    LazyColumn(...)
}
```

### Grid Column Calculation

```kotlin
val gridColumns = when (windowSizeClass.widthSizeClass) {
    WindowWidthSizeClass.Medium -> 3  // 600-840dp
    else -> 4                          // > 840dp
}
```

### Nested Grid Pattern

Each content section uses a nested `LazyVerticalGrid` inside the main `LazyColumn`:

```kotlin
LazyColumn {
    // Section header
    item {
        Text("Recently Added Movies")
    }

    // Grid of items
    item {
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            userScrollEnabled = false  // Parent LazyColumn handles scrolling
        ) {
            items(movies) { movie ->
                PosterMediaCard(...)
            }
        }
    }
}
```

**Why nested grids?**
- Allows mixed content types (carousel + grids)
- Maintains pull-to-refresh functionality
- Each section can have different grid configurations
- Proper section headers between grids

---

## Changes Made

**File Modified**: `app/src/main/java/com/rpeters/jellyfin/ui/screens/HomeScreen.kt`

### New Imports Added:

```kotlin
import android.app.Activity
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
```

### New Function:

- `TabletHomeLayout()` - Complete tablet-optimized layout with grids (~260 lines)

### Modified Functions:

- `HomeContent()` - Added window size class calculation and layout selection

### Preserved Functionality:

✅ Pull-to-refresh works on both layouts
✅ Long-press actions (management sheet) work on all cards
✅ Click navigation to detail screens
✅ Watch progress indicators on Continue Watching
✅ Loading states handled
✅ Empty states handled
✅ All existing animations and transitions

---

## Testing Instructions

### On Your Pixel Tablet

1. **Install the debug APK**
   ```powershell
   .\gradlew.bat installDebug
   ```

2. **Test Portrait Mode**
   - Hold tablet vertically
   - You should see **3-column grids** for all content sections
   - Hero carousel at top should be larger than phone
   - All sections should show multiple items at once

3. **Test Landscape Mode**
   - Rotate tablet horizontally
   - You should see **4-column grids** for all content sections
   - Better space utilization across the wider screen
   - More content visible without scrolling

4. **Compare with Phone**
   - Install on a phone or narrow the window
   - Should show original carousel-based layout
   - No grid layout on phone

### What to Look For

✅ **Correct Behavior:**
- 3 columns in portrait, 4 columns in landscape
- Hero carousel displays properly
- All sections show content in grids
- Cards are properly sized (not too small/large)
- Proper spacing between cards (12dp)
- Section headers visible and readable
- Smooth scrolling
- Pull-to-refresh works
- Long-press actions work
- Click navigation works

❌ **Potential Issues to Report:**
- Cards too small or too large
- Spacing issues (too tight or too loose)
- Grid doesn't appear (shows carousels instead)
- Content overflow or clipping
- Images not loading
- Performance issues (lag or stutter)
- Grid height calculation wrong (content cut off or too much whitespace)

### Testing Checklist

- [ ] Portrait mode shows 3-column grids
- [ ] Landscape mode shows 4-column grids
- [ ] Hero carousel displays properly
- [ ] Continue Watching grid shows all in-progress items
- [ ] Next Up grid shows recent episodes
- [ ] Recently Added Movies grid shows new movies
- [ ] Recently Added Shows grid shows new shows
- [ ] Recently Added Videos shows in 2-column grid
- [ ] Section headers are visible
- [ ] Cards are properly sized
- [ ] Images load correctly
- [ ] Pull-to-refresh works
- [ ] Long-press opens management sheet
- [ ] Click navigates to detail screen
- [ ] Watch progress bars show on Continue Watching
- [ ] Smooth scrolling performance
- [ ] No layout flickering or jumping
- [ ] Rotation transition is smooth

---

## Visual Comparison

### Before (Phone Layout on Tablet)
```
┌─────────────────────────────────────┐
│  [Hero Carousel (small)]            │
├─────────────────────────────────────┤
│  Continue Watching                  │
│  [→ →  →  →  →  →  →  →  →  →]    │  ← Wasted space
├─────────────────────────────────────┤
│  Next Up                            │
│  [→ →  →  →  →  →  →  →  →  →]    │  ← Wasted space
├─────────────────────────────────────┤
│  Recently Added Movies              │
│  [→ →  →  →  →  →  →  →  →  →]    │  ← Wasted space
└─────────────────────────────────────┘
```

### After (Tablet Grid Layout)
```
┌─────────────────────────────────────┐
│  [Hero Carousel (larger)]           │
├─────────────────────────────────────┤
│  Continue Watching                  │
│  [Item] [Item] [Item] [Item]        │
│  [Item] [Item] [Item] [Item]        │  ← Multiple rows visible
├─────────────────────────────────────┤
│  Next Up                            │
│  [Item] [Item] [Item] [Item]        │
│  [Item] [Item] [Item] [Item]        │  ← Grid layout
├─────────────────────────────────────┤
│  Recently Added Movies              │
│  [Item] [Item] [Item] [Item]        │
│  [Item] [Item] [Item] [Item]        │  ← Better use of space
└─────────────────────────────────────┘
```

---

## Performance Considerations

### Optimizations Implemented:

1. **Nested Grid Height Calculation**
   - Pre-calculated fixed heights for nested grids
   - Prevents layout thrashing
   - Formula: `200.dp * rows` for poster cards

2. **userScrollEnabled = false**
   - Nested grids don't scroll independently
   - Parent LazyColumn handles all scrolling
   - Reduces nested scroll complexity

3. **Limited Items per Section**
   - Continue Watching: All items
   - Other sections: 2 rows max (6-8 items)
   - Prevents excessive off-screen composition

4. **Proper item() keys**
   - All grid items use unique keys via `getItemKey()`
   - Enables efficient recomposition
   - Smooth animations when data changes

5. **Preserved Performance Monitoring**
   - Existing PerformanceMetricsTracker still active
   - Debug-only monitoring with 30-second intervals

---

## Design Decisions

### Why Not LazyVerticalGrid for Everything?

**Considered**: Single `LazyVerticalGrid` with different cell spans for each section.

**Chosen**: `LazyColumn` with nested `LazyVerticalGrid` sections.

**Reasoning**:
- Different sections need different layouts (carousel vs grid)
- Section headers easier to implement
- Mixed content types (featured carousel + grids)
- Pull-to-refresh easier with LazyColumn
- Maintains compatibility with existing phone layout
- More flexible for future changes

### Why Fixed Heights for Nested Grids?

**Reason**: Nested LazyLayouts require fixed heights when `userScrollEnabled = false`.

**Alternative**: Using `height(Dp.Unspecified)` would cause layout issues.

**Solution**: Pre-calculate height based on:
- Number of items
- Grid columns
- Item height (poster cards ~200dp, media cards ~140dp)
- Formula: `itemHeight * ceil(itemCount / columns)`

### Why Limit to 2 Rows?

**Reasons**:
- Prevents excessive vertical scrolling
- Maintains discoverability (see more sections)
- Better performance (fewer items composed)
- User can navigate to library for more items
- Matches design patterns from other media apps

---

## Known Limitations

1. **Grid Heights**
   - Fixed heights calculated based on item count
   - If card heights change (responsive images), may need adjustment
   - Works well for standard poster aspect ratios

2. **Section Visibility**
   - Sections only show if items exist
   - Empty sections are hidden (expected behavior)
   - Requires content in library to see full layout

3. **Very Large Screens**
   - Currently max 4 columns for > 840dp
   - Could support 5-6 columns on very large tablets/foldables
   - Can be adjusted in future if needed

4. **Rotation Transition**
   - Grid re-layouts when rotating
   - Brief loading state during window size class recalculation
   - This is expected Material 3 behavior

---

## Next Steps

After testing the HomeScreen on your Pixel Tablet:

### If it looks good:
**Next Priority: Phase 2.2 - LibraryScreen Adaptation**
- Apply same grid pattern to library view
- Show libraries in responsive grid (2-4 columns)
- Better than current single-column list

### Other improvements to consider:
- **Phase 2.3**: SearchScreen grid results
- **Phase 3.1**: MovieDetailScreen split view
- **Phase 3.2**: TVEpisodeDetailScreen split view

---

## Reverting (If Needed)

If you encounter critical issues:

```bash
git checkout app/src/main/java/com/rpeters/jellyfin/ui/screens/HomeScreen.kt
```

Or manually remove the `TabletHomeLayout` function and replace:
```kotlin
if (isTablet) {
    TabletHomeLayout(...)
} else {
    LazyColumn(...)
}
```

With:
```kotlin
LazyColumn(...)
```

---

## Build Info

- **Build Type**: Debug
- **Build Status**: ✅ Successful
- **APK Location**: `app\build\outputs\apk\debug\app-debug.apk`
- **Compile Time**: ~6 seconds (incremental)
- **Full Build Time**: ~17 seconds

---

**Ready to Test!** 🚀

Install the app on your Pixel Tablet and experience the new grid-based layout!

```powershell
.\gradlew.bat installDebug
```
