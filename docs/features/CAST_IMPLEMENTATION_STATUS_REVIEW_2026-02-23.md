# Chromecast (Cast) Implementation Review - 2026-02-23

## Scope
Static code review of current Chromecast sender implementation in:
- `app/src/main/java/com/rpeters/jellyfin/ui/player/CastManager.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/player/VideoPlayerViewModel.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/player/VideoPlayerScreen.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/player/CastOptionsProvider.kt`
- `app/src/main/java/com/rpeters/jellyfin/data/repository/JellyfinRepository.kt`
- `app/src/main/java/com/rpeters/jellyfin/data/model/JellyfinDeviceProfile.kt`
- `app/src/main/java/com/rpeters/jellyfin/data/preferences/CastPreferencesRepository.kt`

Build/tests were intentionally not run per request.

## Current Status

### What is implemented
- Cast framework initialization and session listener wiring are present (`CastManager.initialize`, `CastManager.awaitInitialization`).
- Device discovery + manual route selection UI is implemented (`discoverDevices`, `connectToDevice`, `VideoPlayerViewModel.showCastDialog`).
- Local-to-cast handoff exists (release local ExoPlayer, start cast load) in `VideoPlayerViewModel.handleCastState`.
- Remote controls exist: play/pause/seek/stop/disconnect/volume (`CastManager` + `CastRemoteScreen`).
- Basic cast state persistence exists in DataStore (`CastPreferencesRepository`).
- Cast-specific PlaybackInfo path exists (`JellyfinRepository.getCastPlaybackInfo`) with Cast device profiles.

### What is partially implemented / inconsistent
- Auto-reconnect is implemented in manager/preferences but not actually invoked anywhere (`CastManager.attemptAutoReconnect` is unused).
- Reconnect policy conflicts with options (`setEnableReconnectionService(false)` and `setResumeSavedSession(false)` in `CastOptionsProvider`).
- `startCasting` API accepts `playSessionId` and `mediaSourceId` but current implementation ignores them.

### Overall assessment
- **Feature completeness:** Medium (core path exists).
- **Reliability:** Low-Medium (several state/auth/discovery failure modes).
- **Maintainability:** Low (too much lifecycle/network/session logic concentrated in one class).

## Ranked Bugs / Issues

## P0 (Critical)

1. **Protected-server casting fails by design in common auth setups**
- Evidence:
  - `CastManager.kt:664` explicitly notes receivers cannot use custom headers.
  - `CastManager.kt:531` / `CastManager.kt:536` builds stream URLs without guaranteed receiver-auth strategy.
  - `CastManager.kt:588` uses a generic auth-failure message after receiver error.
- Impact:
  - Users on authenticated Jellyfin servers will see cast load failures or immediate idle/error.
- Root cause:
  - Sender generates URLs but does not provide a deterministic auth delivery mechanism compatible with receiver fetch behavior.

2. **Playback request context is dropped (session/media source continuity broken)**
- Evidence:
  - `CastManager.startCasting` accepts `playSessionId` + `mediaSourceId` (`CastManager.kt:625`, `CastManager.kt:626`) but does not use them in the load URL/request.
  - `resolveCastPlaybackData` only returns URL/mime/playSessionId (`CastManager.kt:473`-`CastManager.kt:477`).
- Impact:
  - Wrong media source selection, broken analytics/progress correlation, unstable resume/continuity behavior.
- Root cause:
  - Handoff contract is incomplete between player state and cast load builder.

## P1 (High)

3. **Discovery is race-prone and can falsely report “No devices found”**
- Evidence:
  - `discoverDevices` adds callback and immediately reads `router.routes` (`CastManager.kt:373`-`CastManager.kt:379`).
  - `VideoPlayerViewModel.showCastDialog` treats empty list as hard failure right away (`VideoPlayerViewModel.kt:1693`-`VideoPlayerViewModel.kt:1699`).
- Impact:
  - Intermittent inability to cast, especially on slower networks/devices.
- Root cause:
  - No async discovery window/state; synchronous snapshot used as final result.

4. **Auto-reconnect feature is effectively dead code**
- Evidence:
  - `CastManager.attemptAutoReconnect` exists (`CastManager.kt:422`) but is never called in app code.
  - Reconnection is disabled in options (`CastOptionsProvider.kt:45`, `CastOptionsProvider.kt:46`).
- Impact:
  - Unexpected behavior vs persisted preferences; user expectation mismatch.
- Root cause:
  - Incomplete integration between preference layer, manager, and app startup lifecycle.

5. **Potential thread leak from CastContext executor creation**
- Evidence:
  - `Executors.newSingleThreadExecutor()` allocated in `startInitialization` (`CastManager.kt:293`) with no shutdown.
- Impact:
  - Long-session/process lifetime can accumulate leaked threads/resources.
- Root cause:
  - Per-initialization unmanaged executor allocation.

## P2 (Medium)

6. **`CastManager` is a god class (state, lifecycle, discovery, transport, metadata, prefs, controls)**
- Evidence:
  - `CastManager.kt` handles session lifecycle, networking decisions, URL composition, metadata, track mapping, and UI-facing state.
- Impact:
  - Hard to test/extend; bug fixes likely to regress adjacent behavior.

7. **Test suite likely stale vs current implementation direction**
- Evidence:
  - Existing tests still assert legacy streamRepository-driven behavior (e.g. `CastManagerTest.kt:380`-`CastManagerTest.kt:463`) while production path now relies on PlaybackInfo resolution in manager.
- Impact:
  - Reduced confidence; tests may not validate the real failure paths now seen in production.

8. **Error reporting is too generic for actionable support/debugging**
- Evidence:
  - Generic auth-style error message emitted for all receiver idle/error paths (`CastManager.kt:588`-`CastManager.kt:592`).
- Impact:
  - Slower triage and repeated user confusion when root cause is not auth.

## Step-by-Step Fix Plan

## Phase 0 - Stabilization Guardrails (Day 1)
1. Freeze cast behavior changes behind a dedicated feature flag.
2. Add structured cast diagnostics model (session id, route id, media status state, status code, url type).
3. Introduce a cast QA checklist and log template for reproducible reports.

## Phase 1 - P0 Fixes (Days 1-3)
1. Implement a receiver-compatible auth strategy for media URLs.
2. Carry through `playSessionId` and `mediaSourceId` from `VideoPlayerViewModel` into final cast load URL/request.
3. Explicitly validate cast URL reachability/auth before `remoteMediaClient.load` and fail fast with precise error.
4. Add tests for:
   - authenticated server cast success path,
   - invalid/expired token path,
   - media source continuity path.

## Phase 2 - Discovery and Session Reliability (Days 3-5)
1. Replace synchronous `discoverDevices()` snapshot behavior with stateful async discovery (loading / found / none / timeout).
2. Update cast dialog UX to wait for discovery timeout before showing “no devices”.
3. Wire and validate auto-reconnect policy end-to-end:
   - either remove dead code/preferences,
   - or enable and explicitly invoke reconnect flow at app/player startup.

## Phase 3 - Lifecycle/Resource Hygiene (Day 5)
1. Replace per-init unmanaged executor with shared lifecycle-managed dispatcher/executor.
2. Add explicit cleanup tests for repeated init/release cycles.
3. Verify no leaked callbacks/routes/listeners across activity/viewmodel recreation.

## Phase 4 - Architecture Refactor (Days 6-8)
1. Split `CastManager` responsibilities into:
   - `CastSessionController` (SDK lifecycle),
   - `CastMediaLoadBuilder` (URL/media info/tracks),
   - `CastStateStore` (state + persistence),
   - `CastDeviceDiscovery` (routing/discovery).
2. Keep `VideoPlayerViewModel` as orchestration only.
3. Add contract tests per component and one integration test for local->cast->local round trip.

## Phase 5 - Validation & Release Hardening (Days 8-10)
1. Real-device matrix pass:
   - Chromecast (3rd gen),
   - Chromecast with Google TV,
   - Android TV receiver.
2. Regression scenarios:
   - start cast, seek, pause/resume, stop,
   - disconnect/reconnect,
   - background/foreground,
   - network handoff and token refresh.
3. Ship with runtime telemetry on cast failures by category to confirm P0/P1 reductions.

## Suggested Execution Order
1. P0 auth + session-context continuity.
2. P1 discovery reliability.
3. P1 reconnect policy cleanup.
4. P1 resource/thread cleanup.
5. P2 architecture/test modernization.

## Exit Criteria for “Stable Cast”
- Cast initiation success rate consistently high on authenticated servers.
- No false “no devices found” reports during normal LAN conditions.
- Local-to-cast and cast-to-local handoff resumes at correct position.
- No leaked listeners/routes/threads across repeated playback sessions.
- Test suite covers core cast happy path + top failure modes.
