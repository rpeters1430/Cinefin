# TV Episode Details Loading Fix

## Problem Description

The TV Episode Details screen would not load when navigating directly to episodes that weren't already loaded in the app state. Users would see an endless loading indicator followed by a navigation back to the previous screen.

**Error Logs:**
```
NavGraph: TVEpisodeDetail: Episode 2c89a86e-e112-3db3-b75e-342198d5a229 not found in app state with 50 items, attempting delayed retry
NavGraph: TVEpisodeDetail: Episode 2c89a86e-e112-3db3-b75e-342198d5a229 still not found after retry, navigating back
```

## Root Cause Analysis

The TV Episode Detail screen (`TVEpisodeDetailScreen`) expected episodes to already be present in `appState.allItems`. However, episodes were only loaded into the app state in two scenarios:

1. **Recently Added Items**: Episodes loaded via `getRecentlyAddedByTypes()` for the home screen
2. **Season Navigation**: Episodes loaded when user clicks from a season screen (calls `addOrUpdateItem()`)

**Missing Scenario**: Direct navigation to episode details (deep links, navigation state restoration, or episodes not in recently added) would fail because the specific episode wasn't in the app state.

## Technical Solution

### 1. Added Episode Details Repository Method

**File**: `JellyfinRepository.kt`

```kotlin
suspend fun getEpisodeDetails(episodeId: String): ApiResult<BaseItemDto> {
    return getItemDetailsById(episodeId, "episode")
}
```

This reuses the existing `getItemDetailsById` pattern used for movies and series.

### 2. Enhanced MainAppViewModel Episode Loading

**File**: `MainAppViewModel.kt`

Updated `loadEpisodeDetails()` method to:
- Check if episode already exists in app state
- Fetch episode from repository if not found
- Add episode to app state via `addOrUpdateItem()`
- Optionally load series context for better user experience
- Handle loading states and errors properly

**Key Implementation:**
```kotlin
fun loadEpisodeDetails(episodeId: String) {
    viewModelScope.launch {
        // Check if already loaded
        val existingEpisode = _appState.value.allItems.find { it.id.toString() == episodeId }
        if (existingEpisode != null) return@launch
        
        // Set loading state
        _appState.value = _appState.value.copy(isLoading = true)
        
        // Fetch from repository
        when (val result = repository.getEpisodeDetails(episodeId)) {
            is ApiResult.Success -> {
                addOrUpdateItem(result.data)
                // Optionally load series context
                // Handle success...
            }
            is ApiResult.Error -> {
                // Handle error...
            }
        }
    }
}
```

### 3. Updated TVEpisodeDetail Navigation Logic

**File**: `NavGraph.kt`

**Before:**
- Episode not found → Show loading briefly → Navigate back (poor UX)

**After:**
- Episode not found → Call `loadEpisodeDetails()` → Show loading → Display episode or error

**Key Changes:**
```kotlin
else -> {
    // Episode not found - try to load it
    LaunchedEffect(episodeId) {
        viewModel.loadEpisodeDetails(episodeId)
    }
    
    // Show loading while fetching
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
```

### 4. Enhanced Error Handling

Added proper error state handling in the navigation:
- Display error message if episode loading fails
- Provide "Go Back" button for user recovery
- Clear error state when navigating away

## Testing Verification

### Test Cases
1. **Direct Episode Navigation**: Navigate directly to episode detail via deep link
2. **Recently Added Episodes**: Click episodes from home screen carousel
3. **Season Episodes**: Click episodes from season/episode list
4. **Error Scenarios**: Test with invalid episode IDs
5. **Network Issues**: Test with poor connectivity

### Expected Behavior
- ✅ Episode details load regardless of how user navigates to them
- ✅ Smooth loading experience with progress indicators
- ✅ Proper error handling with user-friendly messages
- ✅ Series context loaded for better episode information display
- ✅ No more navigation back to previous screen on missing episodes

## Code Changes Summary

| File | Change Type | Description |
|------|-------------|-------------|
| `JellyfinRepository.kt` | Addition | Added `getEpisodeDetails()` method |
| `MainAppViewModel.kt` | Enhancement | Enhanced `loadEpisodeDetails()` with repository fetch |
| `NavGraph.kt` | Fix | Updated TVEpisodeDetail navigation with proper loading |
| `NavGraph.kt` | Addition | Added error handling UI for failed episode loads |

## Benefits

1. **Robust Episode Loading**: Episodes load regardless of navigation path
2. **Better User Experience**: No more unexpected navigation back
3. **Proper Error Handling**: Clear feedback when episodes can't be loaded
4. **Series Context**: Automatically loads series information for better episode details
5. **Performance**: Only loads episodes when needed (lazy loading)

## Future Improvements

1. **Caching**: Implement episode details caching to avoid repeated network calls
2. **Preloading**: Consider preloading adjacent episodes in a season
3. **Offline Support**: Add offline episode details support
4. **Season Episodes**: Batch load all episodes in a season for faster navigation

This fix ensures that TV Episode Details screen works reliably across all user navigation scenarios while maintaining good performance and user experience.
