# Bug Report - Jellyfin Android App

## Bug #1: Carousel State Synchronization Issue

**Location:** `MainActivity.kt` lines 940-995 in `RecentlyAddedCarousel` composable

**Severity:** Medium

**Description:**
The carousel indicators are not synchronized with the actual carousel state. The `currentItem` variable is manually tracked but never updated to reflect the actual position of the carousel, causing the indicators to always show the first item as selected.

**Code Issue:**
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyAddedCarousel(
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val carouselState = rememberCarouselState { items.size }
    // Track current item manually since CarouselState doesn't expose currentItem yet
    var currentItem by rememberSaveable { mutableStateOf(0) } // ❌ BUG: Never updated

    Column(modifier = modifier) {
        HorizontalUncontainedCarousel(
            state = carouselState,
            itemWidth = 320.dp,
            itemSpacing = 20.dp,
            contentPadding = PaddingValues(horizontal = 32.dp)
        ) { index ->
            CarouselItemCard(
                item = items[index],
                getImageUrl = getImageUrl,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Page indicators with clickable functionality
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(items.size) { index ->
                val isSelected = index == currentItem // ❌ Always false after first item
                // ... rest of indicator code
            }
        }
    }
}
```

**Impact:**
- Users see incorrect visual feedback from carousel indicators
- Poor user experience as indicators don't reflect actual carousel position

**Fix:**
Monitor the carousel state changes and update `currentItem` accordingly, or use the carousel state directly if available.

---

## Bug #2: Data Type Mismatch Between Network and UI Layers

**Location:** 
- `network/JellyfinApiService.kt` (uses `BaseItem`)
- Throughout UI layer (uses `BaseItemDto` from Jellyfin SDK)

**Severity:** High

**Description:**
There's a critical data type mismatch in the application. The custom network API service defines and uses a `BaseItem` data class, while the rest of the application (UI, ViewModels, Repository) uses `BaseItemDto` from the official Jellyfin SDK. This mismatch could cause runtime crashes or data inconsistencies.

**Code Issue:**

In `JellyfinApiService.kt`:
```kotlin
@Serializable
data class BaseItem(
    val Id: String,
    val Name: String,
    val Type: String,
    // ... custom implementation
)

interface JellyfinApiService {
    @GET("Users/{userId}/Items")
    suspend fun getUserItems(
        // ...
    ): Response<ItemsResult>  // ❌ Returns custom BaseItem
}

@Serializable
data class ItemsResult(
    val Items: List<BaseItem>,  // ❌ Custom BaseItem
    // ...
)
```

But throughout the app (Repository, ViewModels, UI):
```kotlin
// Repository
suspend fun getUserLibraries(): ApiResult<List<BaseItemDto>> // ❌ Expected BaseItemDto

// ViewModels  
val libraries: List<BaseItemDto> = emptyList() // ❌ Expected BaseItemDto

// UI Components
fun MediaCard(
    item: BaseItemDto,  // ❌ Expected BaseItemDto
    // ...
)
```

**Impact:**
- Potential runtime crashes when attempting to convert between incompatible types
- Data inconsistencies between network responses and UI expectations
- Type safety violations

**Fix:**
Either use the official Jellyfin SDK consistently throughout the app, or properly map between the custom `BaseItem` and `BaseItemDto` types.

---

## Bug #3: Memory Leak in Quick Connect Polling

**Location:** `ServerConnectionViewModel.kt` lines 282-351 in `pollQuickConnectState` method

**Severity:** High

**Description:**
The Quick Connect polling mechanism can cause memory leaks because the polling coroutine continues running even if the ViewModel is destroyed or the user navigates away from the Quick Connect screen. The polling loop only checks `_connectionState.value.isQuickConnectPolling` but doesn't properly handle ViewModel lifecycle.

**Code Issue:**
```kotlin
private suspend fun pollQuickConnectState(serverUrl: String, secret: String) {
    var attempts = 0
    val maxAttempts = 60 // 5 minutes with 5-second intervals
    
    while (attempts < maxAttempts && _connectionState.value.isQuickConnectPolling) {
        delay(5000) // Wait 5 seconds between polls
        
        when (val stateResult = repository.getQuickConnectState(serverUrl, secret)) {
            // ❌ BUG: This loop continues even if ViewModel is destroyed
            // ❌ No proper coroutine cancellation handling
            // ❌ No lifecycle awareness
        }
        attempts++
    }
}
```

The method is called in `initiateQuickConnect()`:
```kotlin
// Start polling for approval
pollQuickConnectState(serverUrl, result.secret ?: "") // ❌ Fire-and-forget coroutine
```

**Impact:**
- Memory leaks when users navigate away during Quick Connect process
- Background network calls continue unnecessarily
- Potential app crashes due to updating destroyed ViewModel state
- Battery drain from unnecessary background polling

**Fix:**
- Use `viewModelScope` properly with cancellation support
- Implement proper lifecycle-aware polling
- Add coroutine cancellation when Quick Connect is cancelled or ViewModel is destroyed

---

## Summary

These bugs range from user experience issues to potential crashes and memory leaks. The data type mismatch (#2) and memory leak (#3) should be prioritized for immediate fixes as they can cause app instability, while the carousel synchronization issue (#1) affects user experience but doesn't compromise app functionality.

---

## ✅ FIXES IMPLEMENTED

All 3 bugs have been successfully fixed:

### Bug #1 Fix: Carousel State Synchronization ✅
- **Location:** `MainActivity.kt` - `RecentlyAddedCarousel` function
- **Solution:** Added `LaunchedEffect` with `snapshotFlow` to monitor carousel state changes
- **Code Added:**
  ```kotlin
  // Monitor carousel state changes and update current item
  LaunchedEffect(carouselState) {
      snapshotFlow { carouselState.firstVisibleItemIndex }
          .collect { index ->
              currentItem = index
          }
  }
  ```

### Bug #2 Fix: Data Type Mismatch ✅
- **Location:** Removed `app/src/main/java/com/example/jellyfinandroid/network/JellyfinApiService.kt`
- **Solution:** Deleted the unused custom API service that was creating type conflicts
- **Impact:** Eliminated all data type mismatches by using only the official Jellyfin SDK

### Bug #3 Fix: Memory Leak in Quick Connect Polling ✅
- **Location:** `ServerConnectionViewModel.kt`
- **Solution:** Implemented proper coroutine lifecycle management
- **Changes Made:**
  - Added `Job` tracking for polling coroutine
  - Added `isActive` check in polling loop
  - Implemented proper cancellation in `cancelQuickConnect()`
  - Added `onCleared()` override for ViewModel cleanup
  - Used `viewModelScope.launch` for proper scoping

## Recommendations

1. ✅ **Fixed:** All critical bugs (High severity)
2. ✅ **Fixed:** Consistent data models using official Jellyfin SDK exclusively
3. ✅ **Fixed:** Proper coroutine lifecycle management
4. **Consider adding unit tests** to catch similar issues in the future
5. **Consider code reviews** to prevent similar bugs from being introduced