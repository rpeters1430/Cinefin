# TV Episode Navigation Fix

## Problem Description
When users clicked on an episode in a TV show season screen, the app would navigate back to the same screen instead of going to the TV Episode detail screen. This was happening because of a race condition in the navigation and state management.

## Root Cause Analysis
The issue was in the navigation flow:

1. User clicks on an episode in `TVEpisodesScreen`
2. `onEpisodeClick` callback calls `mainViewModel.addOrUpdateItem(episode)` to add the episode to the app state
3. Navigation immediately happens to `Screen.TVEpisodeDetail.createRoute(episodeId.toString())`
4. In the `TVEpisodeDetail` composable, it tries to find the episode in `appState.allItems`
5. **Race condition**: The navigation happens before the state flow can propagate the new episode
6. Since the episode is not found, the else clause triggers `navController.popBackStack()`, navigating back

## Solution Implemented

### 1. Enhanced Navigation Logic in `NavGraph.kt`
- **Reordered the episode click logic**: Moved the `episode.id` null check before `addOrUpdateItem` to ensure we have a valid ID
- **Improved error handling**: Added detailed logging to track the navigation flow
- **Added retry mechanism**: Implemented a brief retry with delay to handle state propagation race conditions

### 2. Better State Management in TVEpisodeDetail Navigation
```kotlin
when {
    episode != null -> {
        // Episode found, show detail screen
        Log.d("NavGraph", "TVEpisodeDetail: Found episode ${episode.name} in app state")
        // Show TVEpisodeDetailScreen...
    }
    appState.isLoading -> {
        // Still loading, show loading indicator
        Log.d("NavGraph", "TVEpisodeDetail: App state is loading, showing loading indicator")
        // Show CircularProgressIndicator...
    }
    else -> {
        // Episode not found, attempt retry with delay
        Log.w("NavGraph", "TVEpisodeDetail: Episode $episodeId not found, attempting delayed retry")
        
        var hasRetried by remember { mutableStateOf(false) }
        
        LaunchedEffect(episodeId, hasRetried) {
            if (!hasRetried) {
                hasRetried = true
                // Wait for state to update
                kotlinx.coroutines.delay(100)
                
                // Check again
                val retryEpisode = viewModel.appState.value.allItems.find { it.id?.toString() == episodeId }
                if (retryEpisode == null) {
                    Log.e("NavGraph", "Episode $episodeId still not found after retry, navigating back")
                    navController.popBackStack()
                }
            }
        }
        
        // Show loading while we retry
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
```

### 3. Fixed Duplicate Code Issues
- **Removed duplicate `logout()` function**: There were two identical logout functions in `JellyfinRepository.kt` causing compilation errors
- **Added necessary imports**: Added `mutableStateOf` and `setValue` imports to `NavGraph.kt`

## Technical Details

### Files Modified:
1. **`/ui/navigation/NavGraph.kt`**:
   - Enhanced TVEpisodes onEpisodeClick handler
   - Improved TVEpisodeDetail composable with retry logic
   - Added detailed logging for debugging

2. **`/data/repository/JellyfinRepository.kt`**:
   - Removed duplicate logout function
   - Maintained the comprehensive logout function with credential clearing

### Key Improvements:
- **Race condition handling**: Added a 100ms delay retry mechanism to handle state propagation
- **Better error reporting**: Enhanced logging to track navigation flow
- **User experience**: Shows loading indicator instead of immediately navigating back
- **Robustness**: Graceful handling of edge cases where episodes might not be immediately available

## Testing Recommendations
1. **Navigate to a TV show**: Go to TV Shows → Select a series → Select a season
2. **Click on multiple episodes**: Test clicking on different episodes in the season
3. **Verify navigation**: Ensure each episode click navigates to the episode detail screen
4. **Check back navigation**: Verify that back button returns to the season/episode list
5. **Test edge cases**: Try rapid clicking on episodes to test race condition handling

## Expected Behavior After Fix
- ✅ Clicking on an episode should navigate to the `TVEpisodeDetailScreen`
- ✅ Episode details should display correctly with series information
- ✅ Back navigation should work properly
- ✅ No more unexpected returns to the season screen
- ✅ Loading states should be handled gracefully

## Impact
This fix resolves the core navigation issue in the TV show browsing experience, making the app much more usable for viewing TV series content. Users can now successfully navigate through TV shows → seasons → episodes → episode details as expected.
