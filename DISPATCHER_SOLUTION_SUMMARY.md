# Solution: Testing ViewModels with withContext(Dispatchers.IO)

## Problem Summary

The `MainAppViewModel` tests were failing because:

1. The ViewModel uses `withContext(Dispatchers.IO)` in methods like `ensureValidToken()` and `loadInitialData()`
2. Test dispatcher setup with `Dispatchers.setMain(testDispatcher)` only controls the Main dispatcher
3. `withContext(Dispatchers.IO)` uses the real IO dispatcher, which is not controlled by tests
4. Work on the IO dispatcher doesn't complete synchronously, causing tests to fail

## Solution Implemented

### The DispatcherProvider Pattern

We implemented a **Dependency Injection** pattern for dispatchers that allows tests to control ALL coroutine dispatchers (Main, IO, Default, Unconfined).

## Files Created

### 1. DispatcherProvider Interface
**File**: `/app/src/main/java/com/rpeters/jellyfin/data/common/DispatcherProvider.kt`

```kotlin
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}

// Production implementation
@Singleton
class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}

// Test implementation
class TestDispatcherProvider(
    private val testDispatcher: CoroutineDispatcher,
) : DispatcherProvider {
    override val main = testDispatcher
    override val io = testDispatcher      // ← Key: IO uses test dispatcher
    override val default = testDispatcher
    override val unconfined = testDispatcher
}
```

### 2. Hilt Module
**File**: `/app/src/main/java/com/rpeters/jellyfin/di/DispatcherModule.kt`

Provides the DispatcherProvider through Hilt dependency injection.

### 3. Documentation
**File**: `/docs/TESTING_DISPATCHER_PATTERN.md`

Comprehensive guide on the pattern, including examples and best practices.

## Changes Made

### MainAppViewModel
**File**: `/app/src/main/java/com/rpeters/jellyfin/ui/viewmodel/MainAppViewModel.kt`

**Before:**
```kotlin
class MainAppViewModel @Inject constructor(
    // ... other dependencies
) : ViewModel() {

    private suspend fun ensureValidToken(): Boolean {
        return withContext(Dispatchers.IO) {  // ← Not testable
            // ...
        }
    }
}
```

**After:**
```kotlin
class MainAppViewModel @Inject constructor(
    // ... other dependencies
    private val dispatchers: DispatcherProvider,  // ← Injected
) : ViewModel() {

    private suspend fun ensureValidToken(): Boolean {
        return withContext(dispatchers.io) {  // ← Testable
            // ...
        }
    }
}
```

Changes in 3 locations:
- Line 146: `ensureValidToken()` method
- Line 220: `loadInitialData()` method
- Line 1015: `sendCastPreview()` method

### Test Files Updated

All MainAppViewModel test files updated to inject `TestDispatcherProvider`:

1. `/app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/MainAppViewModelLibraryLoadTest.kt`
2. `/app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/MainAppViewModelDeleteItemTest.kt`
3. `/app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/MainAppViewModelHomeVideosTest.kt`
4. `/app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/MainAppViewModelLibraryItemTest.kt`

**Test Setup Pattern:**
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MainAppViewModelLibraryLoadTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testDispatchers: TestDispatcherProvider

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Create test dispatcher provider
        testDispatchers = TestDispatcherProvider(testDispatcher)

        // Inject into ViewModel
        viewModel = MainAppViewModel(
            // ... other dependencies
            dispatchers = testDispatchers,  // ← Inject test dispatchers
        )
    }

    @Test
    fun `test async operation`() = runTest {
        viewModel.loadData()
        advanceUntilIdle()  // ← Execute all coroutines
        // assertions...
    }
}
```

## Test Results

All MainAppViewModelLibraryLoadTest tests now pass:

```
✓ loadLibraryTypeData_whenLibrariesAlreadyLoaded_loadsItemsDirectly
✓ loadLibraryTypeData_withTVShows_loadsSeriesCorrectly
✓ loadLibraryTypeData_onError_updatesErrorMessage
✓ loadLibraryTypeData_withPagination_detectsHasMore
✓ loadLibraryTypeData_withoutMatchingLibrary_handlesGracefully
✓ loadLibraryTypeData_clearError_removesErrorMessage
```

## Key Benefits

1. **Testability**: All coroutine dispatchers are now controlled in tests
2. **Production Safety**: Real dispatchers used in production, no test code pollution
3. **Thread Safety**: Proper background thread handling maintained
4. **Maintainability**: Clear pattern for future ViewModels
5. **Flexibility**: Easy to swap dispatcher implementations

## Usage Guidelines

### For Future ViewModels

When creating a new ViewModel that needs background work:

1. **Inject DispatcherProvider**:
   ```kotlin
   @HiltViewModel
   class MyViewModel @Inject constructor(
       private val dispatchers: DispatcherProvider,
   ) : ViewModel()
   ```

2. **Use injected dispatchers**:
   ```kotlin
   viewModelScope.launch {
       withContext(dispatchers.io) {
           // IO work
       }
   }
   ```

3. **In tests, inject TestDispatcherProvider**:
   ```kotlin
   val testDispatchers = TestDispatcherProvider(testDispatcher)
   val viewModel = MyViewModel(dispatchers = testDispatchers)
   ```

## Common Testing Patterns

### 1. Use StandardTestDispatcher
```kotlin
private val testDispatcher = StandardTestDispatcher()
```

### 2. Always call advanceUntilIdle()
```kotlin
viewModel.loadData()
advanceUntilIdle()  // Execute all pending coroutines
```

### 3. Mock properties with every(), suspend functions with coEvery()
```kotlin
// Properties
every { repository.currentServer } returns MutableStateFlow(null)

// Suspend functions
coEvery { repository.getData() } returns ApiResult.Success(data)
```

## Migration Impact

- **Production code**: Minimal changes (added 1 constructor parameter)
- **Test code**: Updated test setup in 4 test files
- **New files**: 3 files added (DispatcherProvider, Module, Documentation)
- **Breaking changes**: None (backward compatible through Hilt DI)

## Alternative Solutions Considered

### ❌ Option 1: Dispatchers.setIO() (if it existed)
- **Problem**: Not available in kotlinx.coroutines.test
- **Verdict**: Not possible

### ❌ Option 2: Mock Dispatchers.IO globally
- **Problem**: Cannot mock static objects
- **Verdict**: Not feasible

### ✅ Option 3: DispatcherProvider Pattern (Implemented)
- **Pros**: Industry standard, testable, clean
- **Cons**: Requires DI setup (already using Hilt)
- **Verdict**: Best practice solution

## References

- [Kotlin Coroutines Testing](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/)
- [Android Testing Guide](https://developer.android.com/codelabs/advanced-android-kotlin-training-testing-viewmodel)
- [Best Practices for Testing Coroutines](https://developer.android.com/kotlin/coroutines/test)

## Next Steps

This pattern should be applied to other ViewModels that use `withContext(Dispatchers.IO)` or similar dispatcher switching:

1. `VideoPlayerViewModel`
2. `AuthenticationViewModel`
3. `ServerConnectionViewModel`
4. `StreamingViewModel`
5. `DownloadsViewModel`

## Conclusion

The DispatcherProvider pattern solves the fundamental problem of testing ViewModels that use dispatcher switching. It provides:

- **Full control** over coroutine execution in tests
- **Zero production impact** (uses real dispatchers)
- **Clear, maintainable** testing approach
- **Industry-standard** solution

All MainAppViewModelLibraryLoadTest tests now pass successfully.
