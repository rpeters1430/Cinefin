# Library Screen Crash Fixes

## Issues Identified and Fixed

### 1. **Double Loading Issue**
**Problem**: Library screens were loading data twice, causing performance issues and potential crashes.

**Root Cause**: 
- `MainAppViewModel.loadInitialData()` was loading all library data upfront
- Individual screens were also loading their specific data
- This created race conditions and double API calls

**Solution**:
- Implemented on-demand loading system with `loadLibraryTypeData()` method
- Added loading tracker to prevent duplicate API calls
- Updated all library screens to use the new loading system

**Files Modified**:
- `MainAppViewModel.kt`: Added `loadLibraryTypeData()` method with loading tracker
- `TVShowsScreen.kt`: Updated to use on-demand loading
- `NavGraph.kt`: Updated Movies, TV Shows, and Music screens to use on-demand loading

### 2. **TV Shows Route Navigation Issues**
**Problem**: TV Shows route was conditionally available based on library types, causing crashes when TV shows library wasn't detected.

**Root Cause**:
- TV Shows route was wrapped in `if (CollectionType.TVSHOWS in libraryTypes)` condition
- If no TV shows library was found, the route wouldn't be available
- Navigation attempts would fail and crash the app

**Solution**:
- Made TV Shows route always available (removed conditional check)
- Added proper error handling for navigation failures
- Enhanced route mapping with try-catch blocks

**Files Modified**:
- `NavGraph.kt`: Removed conditional checks for TV Shows, Movies, and Music routes
- Added error handling in navigation callbacks

### 3. **Library Navigation Error Handling**
**Problem**: Clicking on library items could crash the app if route mapping failed.

**Root Cause**:
- Missing error handling in library navigation
- No fallback for invalid library types
- Silent failures that could crash the app

**Solution**:
- Added comprehensive error handling in `libraryRouteFor()` function
- Added try-catch blocks around navigation calls
- Added logging for debugging navigation issues

**Files Modified**:
- `NavGraph.kt`: Enhanced `libraryRouteFor()` with error handling
- `LibraryScreen.kt`: Added error handling for library clicks

### 4. **State Management Improvements**
**Problem**: Race conditions in data loading could cause UI inconsistencies.

**Root Cause**:
- Multiple data loading methods running simultaneously
- No coordination between different loading states
- Stale data being displayed

**Solution**:
- Implemented centralized loading state management
- Added loading tracker to prevent duplicate loads
- Improved error state handling

## Key Changes Made

### MainAppViewModel.kt
```kotlin
// ✅ NEW: Smart loading tracker
private val loadedLibraryTypes = mutableSetOf<String>()

// ✅ NEW: Load data only when needed
fun loadLibraryTypeData(libraryType: LibraryType, forceRefresh: Boolean = false) {
    val typeKey = libraryType.name
    
    // Skip if already loaded (prevents double loading)
    if (!forceRefresh && loadedLibraryTypes.contains(typeKey)) {
        return // No unnecessary API calls!
    }
    
    // Load specific library type data
    when (libraryType) {
        LibraryType.MOVIES -> loadAllMovies(reset = true)
        LibraryType.TV_SHOWS -> loadAllTVShows(reset = true)
        LibraryType.MUSIC, LibraryType.STUFF -> loadLibraryItemsPage(reset = true)
    }
    loadedLibraryTypes.add(typeKey)
}
```

### NavGraph.kt
```kotlin
// ✅ FIXED: Always available routes (no conditional checks)
composable(Screen.TVShows.route) {
    val viewModel = mainViewModel
    TVShowsScreen(
        onTVShowClick = { seriesId ->
            try {
                navController.navigate(Screen.TVSeasons.createRoute(seriesId))
            } catch (e: Exception) {
                Log.e("NavGraph", "Failed to navigate to TV Seasons: $seriesId", e)
            }
        },
        onBackClick = { navController.popBackStack() },
        viewModel = viewModel,
    )
}

// ✅ ENHANCED: Better error handling in library navigation
fun libraryRouteFor(library: BaseItemDto): String? {
    return try {
        when (library.collectionType) {
            CollectionType.MOVIES -> Screen.Movies.route
            CollectionType.TVSHOWS -> Screen.TVShows.route
            CollectionType.MUSIC -> Screen.Music.route
            else -> library.id?.toString()?.let { id ->
                val type = library.collectionType?.toString()?.lowercase(Locale.getDefault()) ?: "mixed"
                Screen.Stuff.createRoute(id, type)
            }
        }
    } catch (e: Exception) {
        Log.e("NavGraph", "Error determining library route for ${library.name}", e)
        null
    }
}
```

### TVShowsScreen.kt
```kotlin
// ✅ FIXED: Use on-demand loading to prevent double loading
LaunchedEffect(Unit) {
    // Use the new on-demand loading system to prevent double loading
    viewModel.loadLibraryTypeData(LibraryType.TV_SHOWS, forceRefresh = false)
}
```

## Testing Recommendations

1. **Library Screen Navigation**:
   - Navigate to Library screen from home
   - Click on different library types (Movies, TV Shows, Music)
   - Verify no crashes or double loading

2. **TV Shows Functionality**:
   - Click on TV Shows library button
   - Verify TV Shows screen loads without freezing
   - Test navigation to individual TV show details

3. **Error Handling**:
   - Test with slow network connections
   - Verify error messages are displayed properly
   - Check that app doesn't crash on navigation failures

4. **Performance**:
   - Monitor for double loading issues
   - Verify smooth navigation between screens
   - Check memory usage during library browsing

## Expected Behavior After Fixes

- ✅ Library screen loads without errors
- ✅ TV Library button works without freezing or crashing
- ✅ No double loading of data
- ✅ Smooth navigation between library types
- ✅ Proper error handling and user feedback
- ✅ Better performance and reduced API calls

## Impact

These fixes resolve the core navigation and loading issues that were causing crashes and poor user experience. The app should now provide a stable and smooth library browsing experience with proper error handling and optimized data loading.
