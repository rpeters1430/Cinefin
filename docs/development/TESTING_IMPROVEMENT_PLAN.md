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

## Diagnostic Update (July 2026)

Progress has been made since March (538→626 total tests as coverage grew, 186→~64-67 failing,
roughly 35%→~10-11%), but `android-ci.yml`'s `build-test-lint` job has still failed on **every
recorded run on `main`** going back through at least May 27, 2026 (1297 total workflow runs
checked; all `failure`). This section documents a focused investigation session's findings so the
next person picking this up doesn't have to re-derive them. **None of the hypotheses below are
verified by a passing local run or a full stack trace** — this environment had no Android
SDK/network access (`dl.google.com` blocked by egress policy) and could not download the CI
`unit-test-reports` artifact (Azure blob storage host also blocked), so all diagnosis was done by
reading Gradle's default console test-failure summaries (exception type + `File.kt:line` only, no
message/full stack trace) plus static source reading. Treat everything here as a lead, not a fix.

### Method used to gather this data
```
# Get check runs for a PR/commit, then:
# GitHub Actions job logs (console output) via job ID — only gives "ExceptionType at File:Line"
# per failing test by default, since no custom testLogging{} block is configured in build.gradle.kts.
# For a real stack trace + message, download the "unit-test-reports" artifact
# (app/build/reports/tests/testDebugUnitTest/**, app/build/test-results/testDebugUnitTest/**)
# from a workflow run and read the XML/HTML — this requires either local network access or being
# able to reach Azure Blob Storage from CI-adjacent tooling, neither of which this session had.
```

### What's been tried and measured
- **Added `-XX:+EnableDynamicAgentLoading -Djdk.attach.allowAttachSelf=true` to `tasks.withType<Test>` JVM args** (this branch, `claude/fix-broken-tests`). Hypothesis: MockK/ByteBuddy self-attaches its inline-mocking agent at runtime to mock final Kotlin classes, and JDK 21+ restricts dynamic agent loading (JEP 451). **Measured effect: 67 → 64 failing tests** (out of 626) — a small improvement, not a fix. Kept in the codebase since it's low-risk and plausibly still relevant, but it is **not** the primary cause of the remaining failures.

### Failure clusters as of the last measured run (64 failing / 626 total)
Grouped by test class, with exception type and (unreliable — see caveat above) reported line:

1. **`JellyfinStreamRepositoryTest` — 19 tests, all `NullPointerException`.** Every test in the
   file fails, including the simplest possible one (`getStreamUrl returns null when no server
   available`, which just stubs `authRepository.getCurrentServer() returns null` and asserts
   null). The NPE happens at each test's first `every {}`/mock-touching line, which points to the
   `@MockK(relaxed = true)` mocks of `JellyfinAuthRepository` and `DeviceCapabilities` (both plain
   `final` Kotlin classes, no interface) being non-functional rather than a logic bug. No
   `RunWith(RobolectricTestRunner)` or `mockkStatic` in this file, so it's a purer MockK-only
   signal than the clusters below. **Not fixed by the JDK21 agent-loading change above.**
   Next step: get a real stack trace for `getStreamUrl returns null when no server available` (see
   "How to get better data" below) — that single simple case should make the actual failure point
   obvious.

2. **`JellyfinAuthRepositoryTest` — 9 tests, all `ClassNotFoundException`** reported at
   `JellyfinAuthRepositoryTest.kt:503` — **a line number that does not exist in the file** (it's
   254 lines long), meaning Gradle's location attribution is unreliable here and the real throw
   site is elsewhere (likely inside MockK/generated code). This file combines
   `@RunWith(RobolectricTestRunner::class)` with three `mockkStatic(...)` calls, two of which are
   **string-literal class names** guessed from the Jellyfin SDK's internals:
   `mockkStatic("org.jellyfin.sdk.api.client.extensions.QuickConnectApiKt")` and
   `...UserApiKt`. The Jellyfin SDK (`jellyfinSdk` in `libs.versions.toml`, currently 1.8.11) is
   code-generated (OpenAPI + KotlinPoet per the SDK's own repo), so the exact generated
   file/class name backing an extension property like `ApiClient.quickConnectApi` is an
   implementation detail, not a stable contract — a SDK version bump could easily change it and
   silently break these string-based lookups with exactly a `ClassNotFoundException`.
   **Caveat:** `EnhancedPlaybackManagerTest.kt` uses the same
   `RobolectricTestRunner` + `mockkStatic(android.util.Log::class)` pattern and is *not* in the
   current failing list, which argues against "Robolectric + Log mocking is fundamentally broken"
   as the cause and points more specifically at the two SDK string-literal `mockkStatic` calls.
   MockK does not allow reference-based (`ClassName::property`) mocking for **extension
   properties** (only extension functions), so the fix isn't a simple one-line swap — it likely
   needs either the correct current generated class name (unknown without SDK jar access) or a
   different mocking strategy for these two properties. `EncryptedPreferencesTest.kt` also uses
   `RobolectricTestRunner` but has no `mockkStatic` at all — its ~10 failures are a different,
   simpler story: the test's own comments say it "requires Android test environment, not pure
   JVM" for real Android Keystore access, and Robolectric's default shadows don't provide a
   functional `AndroidKeyStore` `Cipher`/`KeyStore` provider, so `encryptValue()` likely fails
   internally and returns null. This one is probably a legitimate test-environment limitation,
   not a regression — worth confirming with a real stack trace before doing anything invasive
   (e.g. don't disable/delete these tests based on speculation).

3. **`HomeViewModelTest` — 6 tests, `AssertionError`/`ComparisonFailure`.** Unlike the two
   clusters above, these look like genuine expected-vs-actual mismatches (`ComparisonFailure` is
   JUnit's type for a failed `assertEquals`, not an infra crash), i.e. real test/logic drift that
   probably needs case-by-case comparison against current `MainAppViewModel`/`HomeViewModel`
   behavior rather than an infra fix.

4. **Scattered 1-2 test failures across many other files** — `CertificatePinningManagerTest`,
   `PinningTrustManagerTest`, `PlaybackProgressManagerTest`, `RepositoryUtilsTest`,
   `ThemeTest` (3 — pre-existing, unrelated to the PR #1165 theming work, confirmed present on
   `main` before those changes), `JellyfinAuthRefreshManagerTest`, `AudioPlaybackViewModelTest`,
   `MainAppViewModelHomeVideosTest`, `MainAppViewModelLibraryLoadTest`,
   `ServerConnectionViewModelTest`/`ServerConnectionViewModelOfflineTest`,
   `TVEpisodeDetailViewModelTest`. The exact set fluctuates slightly run-to-run (64-72 failures
   observed across different runs on the same commit), suggesting some genuine flakiness on top
   of the deterministic clusters above. Each of these needs individual investigation; no shared
   pattern was found among them in this session.

### How to get better data before attempting more fixes
The single highest-leverage next step is getting one real stack trace instead of guessing:
```bash
./gradlew testDebugUnitTest --tests "*.JellyfinStreamRepositoryTest" --tests "*.JellyfinAuthRepositoryTest" --stacktrace
# or open app/build/reports/tests/testDebugUnitTest/index.html after any run
```
Either locally, or by downloading the `unit-test-reports` artifact from a recent
`android-ci.yml` run (Actions tab → run → Artifacts). That will immediately confirm or rule out
the `ClassNotFoundException`/NPE hypotheses above and turn this from "likely leads" into "known
fixes."

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
