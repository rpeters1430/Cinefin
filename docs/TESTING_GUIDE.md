# MainAppViewModel Testing Guide

## Problem Summary

Testing `MainAppViewModel.loadLibraryTypeData()` is complex due to:

1. **Dispatcher Complexity**: The ViewModel uses both `viewModelScope` (Main dispatcher) and `Dispatchers.IO` in `ensureValidToken()`
2. **Flow Properties**: Repository properties like `currentServer` and `isConnected` return Flows that must be mocked carefully
3. **Method Chaining**: `loadLibraryTypeData(LibraryType, Boolean)` delegates to `ensureLibrariesLoaded()` which may load libraries first
4. **Default Parameters**: Repository methods like `getLibraryItems()` have default parameters that affect how mocks match

## Key Solutions

### 1. Test Dispatcher Setup

Use `StandardTestDispatcher` (not `UnconfinedTestDispatcher`) with `advanceUntilIdle()`:

```kotlin
private val testDispatcher = StandardTestDispatcher()

@Before
fun setup() {
    Dispatchers.setMain(testDispatcher)
    // ... setup code
}

@Test
fun test() = runTest(testDispatcher) {
    // Perform action
    viewModel.loadLibraryTypeData(LibraryType.MOVIES)

    // CRITICAL: Must call this to execute all pending coroutines
    advanceUntilIdle()

    // Now assert
    val state = viewModel.appState.value
    // assertions...
}
```

### 2. Mocking Flow Properties

Flow properties returning inline functions (like `flowOf()`, `asStateFlow()`) require `coEvery`:

```kotlin
// WRONG - causes MockKException
every { repository.currentServer } returns flowOf(null)

// CORRECT - use coEvery and MutableStateFlow
coEvery { repository.currentServer } returns MutableStateFlow(null)
coEvery { repository.isConnected } returns MutableStateFlow(false)
```

### 3. Mocking Repository Methods with Default Parameters

Use `any()` matchers for default parameters to ensure mocks match:

```kotlin
// The actual call uses defaults: getLibraryItems(parentId = id, itemTypes = "Movie", collectionType = "movies")
// But the method signature has: fun getLibraryItems(parentId: String?, itemTypes: String?, startIndex: Int = 0, limit: Int = 100, ...)

coEvery {
    mediaRepository.getLibraryItems(
        parentId = libraryId.toString(),
        itemTypes = "Movie",
        startIndex = any(),  // Use any() for default parameters!
        limit = any(),       // Use any() for default parameters!
        collectionType = "movies",
    )
} returns ApiResult.Success(listOf(movie))
```

### 4. Pre-populating ViewModel State

Use the test helper methods to set up initial state:

```kotlin
// When testing with libraries already loaded
viewModel.setAppStateForTest(
    MainAppState(libraries = listOf(library))
)
```

### 5. Understanding the Load Flow

```
loadLibraryTypeData(LibraryType.MOVIES)
    ↓
findLibraryForType() - searches current state.libraries
    ↓ (if null)
ensureLibrariesLoaded() - calls mediaRepository.getUserLibraries()
    ↓
loadLibraryTypeData(library, libraryType, forceRefresh)
    ↓
ensureValidToken() - uses Dispatchers.IO
    ↓
mediaRepository.getLibraryItems()
```

### 6. Complete Working Example

```kotlin
@Test
fun `loadLibraryTypeData_whenLibrariesNotLoaded_loadsLibrariesThenItems`() = runTest(testDispatcher) {
    // Arrange
    val libraryId = UUID.randomUUID()
    val library = BaseItemDto(
        id = libraryId,
        name = "Movies",
        type = BaseItemKind.COLLECTION_FOLDER,
        collectionType = CollectionType.MOVIES,
    )
    val movie = BaseItemDto(
        id = UUID.randomUUID(),
        name = "Test Movie",
        type = BaseItemKind.MOVIE,
    )

    // Mock getUserLibraries - will be called because state.libraries is empty
    coEvery { mediaRepository.getUserLibraries(forceRefresh = false) } returns
        ApiResult.Success(listOf(library))

    // Mock getLibraryItems - use any() for default parameters
    coEvery {
        mediaRepository.getLibraryItems(
            parentId = libraryId.toString(),
            itemTypes = "Movie",
            startIndex = any(),
            limit = any(),
            collectionType = "movies",
        )
    } returns ApiResult.Success(listOf(movie))

    // Act
    viewModel.loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = false)
    advanceUntilIdle()  // CRITICAL!

    // Assert
    val state = viewModel.appState.value
    assertEquals(1, state.libraries.size)
    assertNotNull(state.itemsByLibrary[libraryId.toString()])
}
```

## Common Pitfalls

1. **Forgetting `advanceUntilIdle()`** - Coroutines won't execute
2. **Using `every` instead of `coEvery` for Flows** - Causes MockKException about inline functions
3. **Not using `any()` for default parameters** - Mocks won't match actual calls
4. **Using `runBlocking { delay() }`** - Unreliable and indicates missing `advanceUntilIdle()`
5. **Not mocking `getUserLibraries()`** - Required when state.libraries is empty

## Files Created

-  `/home/rpeters1428/JellyfinAndroid/app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/MainAppViewModelLibraryLoadTest.kt` - Complete test suite
