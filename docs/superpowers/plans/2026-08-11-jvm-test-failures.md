# JVM Test Failure Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make all JVM unit tests pass by correcting nondeterministic ViewModel test setup and making episode playback analysis fail safely.

**Architecture:** Keep `ServerConnectionViewModel` runtime behavior unchanged and make its tests supply finite, explicit discovery/connectivity Flows. In `TVEpisodeDetailViewModel`, preserve coroutine cancellation while converting ordinary optional-analysis failures into a logged `null` result.

**Tech Stack:** Kotlin 2.3.20, JUnit 4, MockK, kotlinx-coroutines-test, AndroidX ViewModel, StateFlow.

## Global Constraints

- Do not modify device/emulator handling or `connectedDebugAndroidTest`.
- Do not suppress `CancellationException`.
- Do not refactor unrelated ViewModels, repositories, or logging infrastructure.
- Preserve all existing production discovery and connectivity behavior.
- Verify the complete `testDebugUnitTest` suite.

---

## File Structure

- `app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/ServerConnectionViewModelTest.kt`: deterministic test doubles and ViewModel lifecycle cleanup.
- `app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/TVEpisodeDetailViewModelTest.kt`: observable recovery and `SecureLogger` assertions.
- `app/src/main/java/com/rpeters/jellyfin/ui/viewmodel/TVEpisodeDetailViewModel.kt`: non-cancellation analysis error handling.

### Task 1: Make server connection tests deterministic

**Files:**
- Modify: `app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/ServerConnectionViewModelTest.kt:75-125`
- Test: `app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/ServerConnectionViewModelTest.kt`

**Interfaces:**
- Consumes: `IJellyfinDiscoveryRepository.discoverServers(): Flow<List<DiscoveredServer>>`, `ConnectivityChecker.observeNetworkConnectivity(): Flow<Boolean>`, and `ConnectivityChecker.isOnline(): Boolean`.
- Produces: deterministic finite Flow stubs used by every test-created `ServerConnectionViewModel`.

- [ ] **Step 1: Reproduce the timeout failures**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.rpeters.jellyfin.ui.viewmodel.ServerConnectionViewModelTest"
```

Expected: FAIL after one-minute `UncompletedCoroutinesError` timeouts in the five reported cases.

- [ ] **Step 2: Stub ViewModel observers explicitly in `setUp()`**

Add the `flowOf` import and these stubs after constructing the relaxed dependencies:

```kotlin
import kotlinx.coroutines.flow.flowOf

every { discoveryRepository.discoverServers() } returns flowOf(emptyList())
every { connectivityChecker.observeNetworkConnectivity() } returns flowOf(true)
every { connectivityChecker.isOnline() } returns true
```

These stubs prevent relaxed Flow mocks from controlling scheduler behavior and prevent the offline retry collector from starting during tests that are not testing offline behavior.

- [ ] **Step 3: Centralize ViewModel cleanup**

Keep cancellation in `tearDown()` as the authoritative cleanup:

```kotlin
@After
fun tearDown() {
    if (::viewModel.isInitialized) {
        viewModel.viewModelScope.cancel()
    }
    unmockkAll()
}
```

Remove redundant per-test `viewModel.viewModelScope.cancel()` calls only if they interfere with assertions or test completion; otherwise leave them unchanged to minimize the diff.

- [ ] **Step 4: Run the server connection test class**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.rpeters.jellyfin.ui.viewmodel.ServerConnectionViewModelTest"
```

Expected: all 10 tests PASS without one-minute waits.

- [ ] **Step 5: Commit the deterministic test setup**

```powershell
git add app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/ServerConnectionViewModelTest.kt
git commit -m "test: stabilize server connection ViewModel tests"
```

### Task 2: Recover from episode playback-analysis exceptions

**Files:**
- Modify: `app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/TVEpisodeDetailViewModelTest.kt:86-116`
- Modify: `app/src/main/java/com/rpeters/jellyfin/ui/viewmodel/TVEpisodeDetailViewModel.kt:207-216`
- Test: `app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/TVEpisodeDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `EnhancedPlaybackUtils.analyzePlaybackCapabilities(BaseItemDto): PlaybackCapabilityAnalysis` and `SecureLogger.e(String, String, Throwable?)`.
- Produces: `TVEpisodeDetailState.playbackAnalysis == null` after ordinary analysis exceptions while cancellation continues to propagate.

- [ ] **Step 1: Replace the invalid Android static-log mock with the production logger boundary**

Import `SecureLogger`, `mockkObject`, and `unmockkObject`. In the error-path test, mock and verify the real production boundary:

```kotlin
mockkObject(SecureLogger)
every { SecureLogger.e(any(), any(), any()) } returns Unit

try {
    listOf(
        IllegalStateException("boom"),
        RuntimeException("kaboom"),
    ).forEach { throwable ->
        coEvery { enhancedPlaybackUtils.analyzePlaybackCapabilities(episode) } throws throwable

        viewModel.loadEpisodeDetails(episode)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.playbackAnalysis)
    }

    verify(exactly = 2) {
        SecureLogger.e(
            "TVEpisodeDetailVM",
            match { it.contains(episode.id.toString()) },
            any(),
        )
    }
} finally {
    unmockkObject(SecureLogger)
}
```

- [ ] **Step 2: Run the test to verify the missing behavior**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.rpeters.jellyfin.ui.viewmodel.TVEpisodeDetailViewModelTest.loadEpisodeAnalysis logs errors and recovers across exceptions"
```

Expected: FAIL because `IllegalStateException("boom")` escapes `loadEpisodeAnalysis` instead of producing a logged `null` analysis.

- [ ] **Step 3: Implement minimal non-fatal analysis handling**

Update `loadEpisodeAnalysis` to preserve cancellation and handle ordinary exceptions:

```kotlin
private fun loadEpisodeAnalysis(episode: BaseItemDto) {
    viewModelScope.launch {
        val analysis = try {
            enhancedPlaybackUtils.analyzePlaybackCapabilities(episode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SecureLogger.e(
                "TVEpisodeDetailVM",
                "Failed to analyze playback capabilities for episode ${episode.id}",
                e,
            )
            null
        }
        _state.value = _state.value.copy(playbackAnalysis = analysis)
    }
}
```

- [ ] **Step 4: Run the episode detail tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.rpeters.jellyfin.ui.viewmodel.TVEpisodeDetailViewModelTest"
```

Expected: both tests PASS.

- [ ] **Step 5: Commit the recovery behavior**

```powershell
git add app/src/main/java/com/rpeters/jellyfin/ui/viewmodel/TVEpisodeDetailViewModel.kt app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/TVEpisodeDetailViewModelTest.kt
git commit -m "fix: recover from episode playback analysis failures"
```

### Task 3: Verify JVM regression coverage

**Files:**
- Verify: all files under `app/src/test/java`

**Interfaces:**
- Consumes: the deterministic test harness and non-fatal episode analysis behavior from Tasks 1 and 2.
- Produces: a passing device-free JVM test suite.

- [ ] **Step 1: Run both affected classes together**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.rpeters.jellyfin.ui.viewmodel.ServerConnectionViewModelTest" --tests "com.rpeters.jellyfin.ui.viewmodel.TVEpisodeDetailViewModelTest"
```

Expected: all 12 tests PASS.

- [ ] **Step 2: Run the complete JVM suite**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: all JVM tests PASS; the existing two skipped tests may remain skipped.

- [ ] **Step 3: Check the final diff**

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors and no unintended files staged or modified by this work.
