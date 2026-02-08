# Session 5.5: Episode Count Fix

## Date: 2026-02-07

## Problem Analysis

The episode count in season dropdowns is showing incorrectly. Current logic:

```kotlin
val episodeCount = when {
    episodes.isNotEmpty() -> episodes.size  // After loading
    season.childCount != null && season.childCount!! > 0 -> season.childCount!!  // From API metadata
    else -> 0  // Fallback shows "0 Episodes"
}
```

### Root Cause

The `season.childCount` property is **often `null`** in Jellyfin API responses for season objects. This means:

1. **Before first expansion**: `episodes` is empty AND `childCount` is null → Shows "0 Episodes" ❌
2. **After expansion**: `episodes` is populated → Shows correct count ✅
3. **After collapsing**: `episodes` remains in state → Shows correct count ✅

### Solution

Instead of showing "0 Episodes" when count is unknown, we should:
- Remove the episode count text entirely when unknown (cleaner UI)
- OR show generic text like "Tap to view episodes"
- Episodes will display the correct count after being loaded

## Proposed Fix

Change the display logic to only show episode count when we actually have data:

```kotlin
// Only show episode count if we have reliable data
val episodeCount = if (episodes.isNotEmpty()) {
    episodes.size
} else {
    season.childCount?.takeIf { it > 0 }
}

// Display
episodeCount?.let { count ->
    Text(
        text = "$count Episode${if (count != 1) "s" else ""}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```

This approach:
- Shows correct count when episodes are loaded
- Shows count from metadata if available (rare but possible)
- Shows nothing if count is unknown (better than showing "0")
- After first expansion, count persists because episodes remain in state

## Implementation ✅ COMPLETE

**File**: `ImmersiveTVShowDetailScreen.kt`
**Function**: `SeasonItem` (lines 519-541)

### Changes Made

**Before**:
```kotlin
val episodeCount = when {
    episodes.isNotEmpty() -> episodes.size
    season.childCount != null && season.childCount!! > 0 -> season.childCount!!
    else -> 0  // ❌ Shows "0 Episodes" when unknown
}
Text(
    text = "$episodeCount Episode${if (episodeCount != 1) "s" else ""}",
    ...
)
```

**After**:
```kotlin
val episodeCount = if (episodes.isNotEmpty()) {
    episodes.size
} else {
    season.childCount?.takeIf { it > 0 }  // Returns null if not available
}

episodeCount?.let { count ->  // Only show if we have data
    Text(
        text = "$count Episode${if (count != 1) "s" else ""}",
        ...
    )
}
```

### Behavior

| State | Before Fix | After Fix |
|-------|-----------|-----------|
| Before first expansion | "0 Episodes" ❌ | (no count shown) ✅ |
| After expanding once | "X Episodes" ✅ | "X Episodes" ✅ |
| After collapsing | "X Episodes" ✅ | "X Episodes" ✅ |

### Why This Works

1. **Unknown state**: When we don't know the count, we simply don't display it (cleaner than showing "0")
2. **After loading**: Once episodes are loaded, count persists in state even after collapsing
3. **Metadata fallback**: If API provides `childCount`, we use it (rare but possible)
4. **Better UX**: No misleading "0 Episodes" message

---

## Status: ✅ FIXED

The episode count now only displays when we have reliable data, avoiding the confusing "0 Episodes" message before the season is first expanded.
