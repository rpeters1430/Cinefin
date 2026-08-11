# JVM Test Failure Fixes Design

## Goal

Restore a passing `testDebugUnitTest` suite by fixing the five hanging
`ServerConnectionViewModelTest` cases and the broken episode-analysis error-path test. Device and
emulator setup for `connectedDebugAndroidTest` is explicitly out of scope.

## Root Causes

`ServerConnectionViewModel` starts long-lived discovery and connectivity collection jobs in
`viewModelScope`. Several tests drain the shared test scheduler with `advanceUntilIdle()` before
those jobs are deterministically stopped, causing `runTest` to reach its one-minute timeout even
though the intended assertions do not fail.

`TVEpisodeDetailViewModelTest` directly mocks the static Android logging API, while production code
uses `SecureLogger`. The production analysis path also only preserves coroutine cancellation; an
ordinary playback-analysis exception escapes the ViewModel job instead of being treated as an
optional-analysis failure.

## Design

### Server connection tests

Keep production discovery and connectivity behavior unchanged. Make the tests deterministic by
providing finite Flow stubs for ViewModel observers, advancing only until the state under test is
reached, and cancelling the ViewModel scope in reliable cleanup. Avoid using scheduler-wide
draining as a substitute for waiting on a specific observable condition when a long-lived collector
is active.

### Episode analysis

Treat playback capability analysis as optional detail metadata. Preserve structured cancellation by
rethrowing `CancellationException`. Catch other exceptions, log them through `SecureLogger` with the
episode identifier and throwable, and publish a `null` analysis so the episode detail screen remains
usable.

The test will verify observable recovery behavior and the logger abstraction used by production. It
will not mock `android.util.Log.e` directly.

## Verification

Work test-first from the existing failing cases:

1. Reproduce each failing class independently.
2. Apply the smallest lifecycle/test-harness change and verify all
   `ServerConnectionViewModelTest` cases pass.
3. Verify the episode-analysis error test fails for the missing recovery behavior, then implement
   the minimal production catch-and-log behavior.
4. Run both affected test classes together.
5. Run the complete `testDebugUnitTest` suite.

Instrumentation tests are not part of verification because no Android device is connected.

## Non-Goals

- Changing application discovery or connectivity behavior.
- Suppressing coroutine cancellation.
- Adding emulator/device automation.
- Refactoring unrelated ViewModels, repositories, or logging infrastructure.
