# Additional Bug Report - Jellyfin Android App

## Summary
During my analysis of the Jellyfin Android app codebase, I discovered several bugs and issues that need attention. While the existing `Bug_Report.md` indicates that 3 major bugs were fixed, my investigation reveals that one of these fixes was not properly implemented, and additional issues exist.

---

## Bug #1: Carousel State Synchronization Fix Not Implemented ❌

**Location:** `MainActivity.kt` lines 1169-1308 in `RecentlyAddedCarousel` composable

**Severity:** Medium

**Status:** **NOT FIXED** (Despite being marked as fixed in Bug_Report.md)

**Description:**
The carousel synchronization issue that was supposedly fixed is still present. The `currentItem` variable is only updated when users click on indicators, but not when they swipe the carousel. This means the indicators don't reflect the actual carousel position during swipe gestures.

**Code Evidence:**
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
    var currentItem by rememberSaveable { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    // ❌ MISSING: LaunchedEffect to sync currentItem with carousel state
    // The bug report claims this was fixed with:
    // LaunchedEffect(carouselState) {
    //     snapshotFlow { carouselState.firstVisibleItemIndex }
    //         .collect { index ->
    //             currentItem = index
    //         }
    // }

    Column(modifier = modifier) {
        HorizontalUncontainedCarousel(
            state = carouselState,
            itemWidth = 320.dp,
            itemSpacing = 20.dp,
            contentPadding = PaddingValues(horizontal = 32.dp)
        ) { index ->
            // ... carousel content
        }

        // Indicators only update when clicked, not when swiped
        Row(/* ... */) {
            repeat(items.size) { index ->
                val isSelected = index == currentItem // ❌ Still wrong during swipes
                // ... indicator content
                Box(
                    modifier = Modifier
                        .clickable {
                            currentItem = index // ✅ Only works on click
                            coroutineScope.launch {
                                carouselState.animateScrollToItem(index)
                            }
                        }
                )
            }
        }
    }
}
```

**Impact:**
- Users see incorrect indicator state when swiping carousel
- Poor user experience as indicators don't reflect actual position
- Inconsistent behavior between swipe and click interactions

**Fix Required:**
Implement the missing `LaunchedEffect` with `snapshotFlow` to monitor carousel state changes.

---

## Bug #2: Potential Null Pointer Exception in NetworkModule

**Location:** `NetworkModule.kt` line 82

**Severity:** High

**Description:**
The `JellyfinClientFactory.getClient()` method uses the `!!` operator which can cause a crash if `currentClient` is null due to unexpected conditions.

**Code Issue:**
```kotlin
@Singleton
class JellyfinClientFactory @Inject constructor(
    private val jellyfin: Jellyfin
) {
    private var currentClient: org.jellyfin.sdk.api.client.ApiClient? = null
    private var currentBaseUrl: String? = null
    
    fun getClient(baseUrl: String, accessToken: String? = null): org.jellyfin.sdk.api.client.ApiClient {
        val normalizedUrl = baseUrl.trimEnd('/') + "/"
        val clientKey = "$normalizedUrl|$accessToken"
        
        if (currentClient == null || currentBaseUrl != clientKey) {
            currentClient = jellyfin.createApi(
                baseUrl = normalizedUrl,
                accessToken = accessToken
            )
            currentBaseUrl = clientKey
        }
        
        return currentClient!! // ❌ POTENTIAL CRASH: What if jellyfin.createApi() returns null?
    }
}
```

**Impact:**
- App crash if `jellyfin.createApi()` unexpectedly returns null
- Poor error handling for API client creation failures
- Potential loss of user session

**Fix Required:**
Replace `!!` with proper null handling and error management.

---

## Bug #3: Missing Image Loading in Media Cards

**Location:** `MainActivity.kt` - Various card composables (MediaCard, LibraryCard, etc.)

**Severity:** Medium

**Description:**
All media cards use `ShimmerBox` but don't actually load images from the `getImageUrl` function. This means users only see placeholder shimmer effects instead of actual media artwork.

**Code Evidence:**
```kotlin
@Composable
fun MediaCard(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier
) {
    // ... card setup
    Box {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            shape = RoundedCornerShape(12.dp)
        ) // ❌ Only shows shimmer, never loads actual image
        
        // ❌ MISSING: SubcomposeAsyncImage or AsyncImage to load actual images
        // Should be something like:
        // SubcomposeAsyncImage(
        //     model = getImageUrl(item),
        //     contentDescription = item.name,
        //     loading = { ShimmerBox(...) },
        //     modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
        // )
    }
}
```

**Impact:**
- Users never see actual media artwork
- Poor visual experience with only placeholder content
- Reduced app functionality and usability

**Fix Required:**
Replace `ShimmerBox` with proper image loading using `SubcomposeAsyncImage` with shimmer as loading state.

---

## Bug #4: MainActivity.kt Size Concerns

**Location:** `MainActivity.kt` 

**Severity:** Low (Code Quality Issue)

**Description:**
The `MainActivity.kt` file is extremely large (61KB, 1579 lines), which indicates potential architectural issues and makes the code hard to maintain.

**Code Structure Issues:**
- Single file contains multiple unrelated composables
- Missing separation of concerns
- Makes debugging and maintenance difficult
- Increases chance of merge conflicts

**Impact:**
- Difficult code maintenance
- Potential for introducing bugs during modifications
- Poor code organization and readability

**Fix Required:**
Refactor into separate files organized by functionality (e.g., `CarouselComponents.kt`, `MediaCards.kt`, etc.).

---

## Bug #5: Incomplete Quick Connect Implementation

**Location:** `JellyfinRepository.kt` lines 129-188

**Severity:** Medium

**Description:**
The Quick Connect implementation is marked as "simple" and "for demonstration" but contains hardcoded mock behavior that doesn't actually connect to real Jellyfin servers.

**Code Evidence:**
```kotlin
suspend fun getQuickConnectState(serverUrl: String, secret: String): ApiResult<QuickConnectState> {
    return try {
        // Simulate checking state - in real implementation this would call the server
        kotlinx.coroutines.delay(2000) // Simulate network delay
        
        // Simulate approval (in real implementation, this would check server state)
        val state = if (secret.isNotEmpty()) "Approved" else "Pending" // ❌ MOCK BEHAVIOR
        
        ApiResult.Success(QuickConnectState(state = state))
    } catch (e: Exception) {
        ApiResult.Error("Failed to get Quick Connect state: ${e.message}", e, ErrorType.NETWORK)
    }
}
```

**Impact:**
- Quick Connect doesn't actually work with real servers
- Misleading user experience
- Feature appears to work but is non-functional

**Fix Required:**
Implement proper Quick Connect API calls to Jellyfin server endpoints.

---

## Summary

**Critical Issues Status:**
1. ✅ **Carousel synchronization** - **FIXED** (LaunchedEffect with snapshotFlow implemented)
2. ✅ **Potential null pointer crash** - **FIXED** (Safe null handling in NetworkModule)
3. ✅ **Image loading functionality** - **FIXED** (SubcomposeAsyncImage implemented)

**Remaining Issues:**
4. **MainActivity.kt size concerns** (Low Priority - Code Quality)
5. **Incomplete Quick Connect implementation** (Medium Priority)

**Recommendations:**
1. ✅ **DONE:** Fix the carousel synchronization by implementing the missing `LaunchedEffect`
2. ✅ **DONE:** Replace `!!` operator with proper null handling in `NetworkModule`
3. ✅ **DONE:** Implement actual image loading in media cards
4. **TODO:** Refactor `MainActivity.kt` into smaller, focused components
5. **TODO:** Complete the Quick Connect implementation with real API calls

**All Bug Status:**
- Bug #2 (Data Type Mismatch): ✅ Fixed (custom API service removed)
- Bug #3 (Memory Leak): ✅ Fixed (proper lifecycle management implemented)
- Bug #1 (Carousel Sync): ✅ **FIXED** (LaunchedEffect properly implemented)
- Bug #4 (Null Pointer Risk): ✅ **FIXED** (Safe null handling implemented)
- Bug #5 (Image Loading): ✅ **FIXED** (SubcomposeAsyncImage implemented)