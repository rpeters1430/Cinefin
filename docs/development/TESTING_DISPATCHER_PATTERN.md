# Testing ViewModels with withContext(Dispatchers.IO)

## Problem

When testing ViewModels that use `withContext(Dispatchers.IO)` or other dispatcher switching, standard test dispatcher setup only controls the `Main` dispatcher. This causes tests to fail because:

1. `Dispatchers.setMain(testDispatcher)` only replaces the Main dispatcher
2. `withContext(Dispatchers.IO)` uses the real `Dispatchers.IO`, which is not controlled by the test dispatcher
3. Work on IO dispatcher doesn't complete synchronously in tests

## Solution: DispatcherProvider Pattern

The solution is to inject dispatchers through a `DispatcherProvider` interface that can be replaced in tests.

### 1. Create DispatcherProvider Interface

Location: `/app/src/main/java/com/rpeters/jellyfin/data/common/DispatcherProvider.kt`

```kotlin
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}

@Singleton
class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}

class TestDispatcherProvider(
    private val testDispatcher: CoroutineDispatcher,
) : DispatcherProvider {
    override val main: CoroutineDispatcher = testDispatcher
    override val io: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher
    override val unconfined: CoroutineDispatcher = testDispatcher
}
```

### 2. Create Hilt Module

Location: `/app/src/main/java/com/rpeters/jellyfin/di/DispatcherModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class DispatcherModule {
    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(
        impl: DefaultDispatcherProvider
    ): DispatcherProvider
}
```

### 3. Update ViewModel to Use DispatcherProvider

```kotlin
@HiltViewModel
class MainAppViewModel @Inject constructor(
    // ... other dependencies
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private suspend fun ensureValidToken(): Boolean {
        return withContext(dispatchers.io) {  // ← Use injected dispatcher
            // IO work here
        }
    }
}
```

### 4. Update Tests

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MainAppViewModelTest {

    // Use StandardTestDispatcher for deterministic execution
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testDispatchers: TestDispatcherProvider

    @Before
    fun setup() {
        // Set Main dispatcher
        Dispatchers.setMain(testDispatcher)

        // Create test dispatcher provider that controls ALL dispatchers
        testDispatchers = TestDispatcherProvider(testDispatcher)

        // Inject into ViewModel
        viewModel = MainAppViewModel(
            // ... other dependencies
            dispatchers = testDispatchers,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test async operation`() = runTest {
        // Arrange
        coEvery { repository.getData() } returns ApiResult.Success(data)

        // Act
        viewModel.loadData()

        // CRITICAL: Must call advanceUntilIdle() to execute all pending coroutines
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success)
    }
}
```

## Key Testing Principles

### 1. Dispatcher Choice

- **StandardTestDispatcher**: Deterministic, requires `advanceUntilIdle()` (RECOMMENDED)
  - Gives full control over coroutine execution
  - Prevents race conditions and flaky tests
  - More verbose but safer

- **UnconfinedTestDispatcher**: Executes immediately
  - Can be useful for simple tests
  - May hide timing issues
  - Less deterministic

### 2. Mocking Strategy

```kotlin
// ✅ Correct: Use every() for properties
every { repository.currentServer } returns MutableStateFlow(null)

// ❌ Wrong: Don't use coEvery for properties
coEvery { repository.currentServer } returns MutableStateFlow(null)

// ✅ Correct: Use coEvery for suspend functions
coEvery { repository.getData() } returns ApiResult.Success(data)

// ✅ Correct: Use any() for default parameters
coEvery {
    repository.getItems(
        parentId = any(),
        itemTypes = any(),
        startIndex = any(),  // ← default parameter
        limit = any(),       // ← default parameter
        collectionType = any(),
    )
} returns ApiResult.Success(items)
```

### 3. Always Use advanceUntilIdle()

```kotlin
@Test
fun `test with async work`() = runTest {
    // Act
    viewModel.loadData()

    // ✅ CRITICAL: Execute all pending coroutines
    advanceUntilIdle()

    // Assert
    val state = viewModel.uiState.value
    // assertions...
}
```

### 4. Test-Specific State Setup

```kotlin
@Test
fun `test with pre-populated state`() = runTest {
    // Arrange - Use test helper to set initial state
    viewModel.setAppStateForTest(
        MainAppState(libraries = testLibraries)
    )

    // Act
    viewModel.loadLibraryData(LibraryType.MOVIES)
    advanceUntilIdle()

    // Assert
    // ...
}
```

## Benefits

1. **Testable**: All dispatchers are controlled in tests
2. **Production-safe**: Uses real dispatchers in production
3. **Thread-safe**: Properly handles background work
4. **Flexible**: Easy to swap implementations
5. **Clean**: No test-specific code in production ViewModels

## Migration Checklist

When adding DispatcherProvider to a ViewModel:

- [ ] Add `DispatcherProvider` parameter to constructor
- [ ] Replace `Dispatchers.IO` with `dispatchers.io`
- [ ] Replace `Dispatchers.Main` with `dispatchers.main`
- [ ] Replace `Dispatchers.Default` with `dispatchers.default`
- [ ] Update all tests to inject `TestDispatcherProvider`
- [ ] Use `StandardTestDispatcher` in tests
- [ ] Call `advanceUntilIdle()` after async operations

## Example: Complete Test

```kotlin
@OptIn(ExperimentalCoroutinesApi::class, UnstableApi::class)
class MainAppViewModelLibraryLoadTest {

    @MockK private lateinit var repository: JellyfinRepository
    @MockK private lateinit var authRepository: JellyfinAuthRepository
    @MockK private lateinit var mediaRepository: JellyfinMediaRepository
    // ... other mocks

    private lateinit var viewModel: MainAppViewModel
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testDispatchers: TestDispatcherProvider

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)

        // Set Main dispatcher
        Dispatchers.setMain(testDispatcher)

        // Create test dispatcher provider
        testDispatchers = TestDispatcherProvider(testDispatcher)

        // Setup mocks
        repository = mockk(relaxed = true)
        every { repository.currentServer } returns MutableStateFlow(null)
        every { repository.isConnected } returns MutableStateFlow(false)
        every { authRepository.isTokenExpired() } returns false
        coEvery { authRepository.reAuthenticate() } returns true

        // Create ViewModel with test dispatchers
        viewModel = MainAppViewModel(
            context = context,
            repository = repository,
            authRepository = authRepository,
            mediaRepository = mediaRepository,
            userRepository = userRepository,
            streamRepository = streamRepository,
            searchRepository = searchRepository,
            credentialManager = credentialManager,
            castManager = castManager,
            dispatchers = testDispatchers,  // ← Inject test dispatchers
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadLibraryTypeData_whenLibrariesAlreadyLoaded_loadsItemsDirectly`() = runTest {
        // Arrange
        val library = BaseItemDto(
            id = UUID.randomUUID(),
            name = "Movies",
            type = BaseItemKind.COLLECTION_FOLDER,
            collectionType = CollectionType.MOVIES,
        )

        viewModel.setAppStateForTest(
            MainAppState(libraries = listOf(library))
        )

        coEvery {
            mediaRepository.getLibraryItems(
                parentId = library.id.toString(),
                itemTypes = "Movie",
                startIndex = any(),
                limit = any(),
                collectionType = "movies",
            )
        } returns ApiResult.Success(listOf(movie1, movie2))

        // Act
        viewModel.loadLibraryTypeData(LibraryType.MOVIES)
        advanceUntilIdle()  // ← Execute all coroutines

        // Assert
        val state = viewModel.appState.value
        val items = state.itemsByLibrary[library.id.toString()]
        assertNotNull(items)
        assertEquals(2, items!!.size)
        assertFalse(state.isLoading)

        // Verify repository was called exactly once
        coVerify(exactly = 1) {
            mediaRepository.getLibraryItems(
                parentId = library.id.toString(),
                itemTypes = "Movie",
                startIndex = any(),
                limit = any(),
                collectionType = "movies",
            )
        }
    }
}
```

## Common Pitfalls

### ❌ Don't: Use real dispatchers in tests
```kotlin
withContext(Dispatchers.IO) {  // ← Not testable
    // work
}
```

### ✅ Do: Use injected dispatchers
```kotlin
withContext(dispatchers.io) {  // ← Testable
    // work
}
```

### ❌ Don't: Forget advanceUntilIdle()
```kotlin
viewModel.loadData()
// Assert immediately - will fail! Work hasn't completed
```

### ✅ Do: Always advance dispatcher
```kotlin
viewModel.loadData()
advanceUntilIdle()  // ← Execute all pending work
// Now assert
```

### ❌ Don't: Use coEvery for properties
```kotlin
coEvery { repository.currentServer } returns flow  // ← Wrong!
```

### ✅ Do: Use every() for properties
```kotlin
every { repository.currentServer } returns flow  // ← Correct
```

## References

- [Kotlin Coroutines Testing Guide](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/)
- [Testing Android ViewModels](https://developer.android.com/codelabs/advanced-android-kotlin-training-testing-viewmodel)
- [MockK Documentation](https://mockk.io/)

## Files Changed

- `/app/src/main/java/com/rpeters/jellyfin/data/common/DispatcherProvider.kt` (new)
- `/app/src/main/java/com/rpeters/jellyfin/di/DispatcherModule.kt` (new)
- `/app/src/main/java/com/rpeters/jellyfin/ui/viewmodel/MainAppViewModel.kt` (updated)
- `/app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/MainAppViewModelLibraryLoadTest.kt` (updated)
- All other MainAppViewModel test files (updated)
