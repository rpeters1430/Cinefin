# Cinefin Testing Improvement & Coverage Plan

This document outlines the steps to stabilize the current test suite, fix existing failures, and improve overall testing coverage for the Cinefin Android client.

## Current Status (as of March 2026)
- **Total Tests:** 538
- **Failing Tests:** 186 (approx. 35%)
- **Major Failure Types:** 
    - `MockKException`: Missing stubbing/mock definitions.
    - `ClassCastException`: Incorrect mock types or data layer casting issues.
    - `NullPointerException`: Issues in `Enums.kt` and static initialization.
    - `IOException`: DataStore file conflicts during parallel test execution.
    - `AssertionError`: Logic regressions or outdated test expectations.

---

## Phase 1: Stabilization (Fixing Existing Failures)

The primary goal is to reach a "Green" state (100% passing) by resolving systemic issues.

### 1. Resolve Mocking Failures
Many tests fail because dependencies are not fully mocked or `coEvery` is missing for suspend functions.
- **Action:** Audit `EnhancedPlaybackManagerTest` and `JellyfinMediaRepositoryTest`.
- **Fix:** Ensure all repository calls are stubbed with `any()` or specific matchers. Use `coEvery` for all `suspend` functions.

### 2. Fix DataStore `IOException`
Tests for `ThemePreferencesRepository` and `LibraryActionsPreferencesRepository` fail due to file access conflicts.
- **Action:** Use a unique DataStore file name per test or a `StandardTestDispatcher` with a temporary directory.
- **Fix:** Update `ThemePreferencesRepositoryTest.kt` to use a `TestScope` and a unique file for each test run.

### 3. Debug `Enums.kt` NullPointerExceptions
`JellyfinCacheTest` and `JellyfinAuthRepositoryTest` report NPEs in `Enums.kt`.
- **Action:** Investigate the source of `Enums.kt` NPEs (likely related to `valueOf` or `values()` calls on mocked/uninitialized enums).
- **Fix:** Ensure all SDK enums (like `BaseItemKind`) are correctly used in mocks.

### 4. Correct Data Layer Casting
`EnhancedPlaybackManagerTest` fails with `ClassCastException` in `JellyfinRepository.kt`.
- **Action:** Inspect line 1318 in `JellyfinRepository.kt`. Check if `networkCapabilities` is being cast or if a mocked return value is of the wrong type.

---

## Phase 2: Standardization & Best Practices

To prevent future regressions and ensure high-quality tests, we will adopt the following standards.

### 1. Unified Test Dispatchers
Use `StandardTestDispatcher` for all ViewModel and Repository tests to ensure predictable coroutine execution.
- **Rule:** Always use `runTest` and `advanceUntilIdle()`.

### 2. Shared Test Rules
Create a `CinefinTestRule` or similar to handle:
- `MainDispatcherRule` (setting/resetting `Dispatchers.Main`).
- MockK clearing (`unmockkAll()`).
- DataStore cleanup.

### 3. Mocking Strategy
- **Repositories:** Should be mocked using `mockk<JellyfinRepository>()`.
- **SDK Calls:** Should always be wrapped in a repository. NEVER mock the SDK's `ItemApi` directly in ViewModel tests.

---

## Phase 3: Coverage Improvement (Target: 70%+)

Once the suite is stable, we will focus on increasing coverage in critical areas.

### 1. Priority: ViewModels
Target 100% logic coverage for:
- `MainAppViewModel`
- `VideoPlayerViewModel`
- `ImmersiveSearchViewModel`
- `TvDetailViewModel`

**Testing Checklist:**
- [ ] Success state loading.
- [ ] Empty state handling.
- [ ] Error state (API failure, timeout).
- [ ] Authentication expiration (Redirect logic).

### 2. Edge Case Testing
- **Network Flakiness:** Test how repositories handle `IOException` or `SocketTimeoutException`.
- **Large Data Sets:** Test performance/UI behavior with 500+ items in a library.
- **Deep Linking:** Test navigation states when entering from a notification or deep link.

### 3. UI/Compose Testing
Add instrumentation tests for complex UI interactions that Unit tests cannot cover:
- D-pad focus movement on TV.
- Pull-to-refresh triggers.
- Multi-select library actions.

---

## How to Follow This Plan

1. **Run Tests Locally:** Use `./gradlew testDebugUnitTest --continue` to see the full list of failures.
2. **Surgical Fixes:** Pick one category (e.g., "DataStore failures") and fix all related tests across the app.
3. **Verify:** After each fix, run the specific test class to confirm.
4. **CI Integration:** Ensure `android-ci.yml` is updated to block merges if tests fail.

---
*Created by Gemini CLI - March 2026*
