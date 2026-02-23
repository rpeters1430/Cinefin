# Cast System Unified Implementation Plan

**Status:** Proposed (consolidated + updated)
**Date:** 2026-02-23
**Replaces:**
- `docs/features/CAST_REBUILD_SUMMARY.md`
- `docs/features/CAST_ISSUE_TEMPLATE.md`
- `docs/features/CAST_IMPLEMENTATION_STATUS_REVIEW_2026-02-23.md`

---

## 1) Why this single plan exists

Over time we produced:
1. a broad “rebuild from scratch” architecture plan,
2. an issue-template version of that plan,
3. and a later implementation status review focused on concrete production failures.

This document merges those into one execution plan that is **grounded in the current codebase** and prioritizes shipping reliability quickly before deeper refactors.

---

## 2) Current implementation snapshot (from code review)

### What already works
- Cast framework init and session callbacks are in place.
- Device selection and route handoff exist.
- Local -> Cast playback handoff exists, including start position and subtitle track mapping.
- Remote controls exist (play/pause/seek/stop/disconnect/volume).
- Basic Cast preference persistence exists.

### Highest-risk gaps in current code
1. **Auth model for protected servers is not receiver-safe by default**
   - Current logic explicitly notes receivers cannot use custom headers and relies on URL accessibility assumptions.
   - Impact: protected-server casting can fail in real-world auth setups.

2. **Playback continuity contract is incomplete**
   - `startCasting()` accepts `playSessionId` and `mediaSourceId`, but load request creation does not enforce end-to-end propagation.
   - Impact: potential mismatch in progress/session continuity and media-source selection.

3. **Discovery UX is race-prone**
   - Device discovery snapshots routes immediately after enabling callbacks; UI can show “no devices” too early.

4. **Reconnect strategy is inconsistent**
   - `attemptAutoReconnect()` exists, while Cast options disable reconnect/resume behavior.
   - Result: dead/ambiguous reconnect behavior.

5. **Resource hygiene issue**
   - Init path allocates `Executors.newSingleThreadExecutor()` without lifecycle shutdown.

6. **Architecture/testability debt**
   - `CastManager` remains a high-concern “god class” (discovery, lifecycle, transport, metadata, state, preferences).

---

## 3) Guiding decisions

1. **Stabilize first, then refactor** (do not block P0 bug fixes on full architecture rewrite).
2. **Prefer deterministic auth strategy over “best effort URLs.”**
3. **Make discovery asynchronous in UX and state model.**
4. **Keep one reconnect policy and remove contradictory behavior.**
5. **Ship diagnostics and QA matrix with each phase.**

---

## 4) Unified phased plan

## Phase 0 — Guardrails & observability (Day 1)

### Goals
- Reduce risk while making behavior measurable.

### Tasks
- Add feature flag for the new cast fix path (safe rollback).
- Introduce structured cast diagnostics payload:
  - session id, route id/name, receiver app id,
  - URL type (direct/transcode/proxy),
  - auth mode, load status code, idle reason,
  - discovery timing metrics.
- Add standard QA bug-report template + log checklist.

### Exit criteria
- We can classify every cast failure into actionable buckets.

---

## Phase 1 — P0 reliability fixes (Days 1–3)

### Goals
- Make casting work reliably for authenticated servers.
- Preserve playback continuity context.

### Tasks
1. **Implement receiver-compatible auth delivery strategy**
   - Supported strategies (priority order):
     1) trusted LAN proxy endpoint,
     2) server-issued cast-safe URL/tokenized endpoint,
     3) explicit hard-fail with clear user guidance.
   - Avoid silent fallback when auth guarantees are not met.

2. **Complete handoff contract**
   - Ensure `playSessionId` and `mediaSourceId` are carried from `VideoPlayerViewModel` -> cast load builder -> final URL/load customData (as supported).
   - Verify server progress and analytics correlation uses the same session context.

3. **Fail fast with actionable errors before load**
   - Validate URL/auth reachability assumptions prior to `remoteMediaClient.load`.
   - Replace generic auth error copy with categorized user-facing errors.

### Tests
- Authenticated server success path.
- Expired/invalid token path.
- Media source continuity path.

### Exit criteria
- Protected-server cast success rate improves and failures are explicit/actionable.

---

## Phase 2 — Discovery + reconnect coherence (Days 3–5)

### Goals
- Eliminate false “no devices found.”
- Resolve reconnect contradictions.

### Tasks
1. Replace synchronous discovery snapshot with async discovery state machine:
   - `Idle -> Discovering -> DevicesFound | Timeout | Error`.
2. Update cast dialog UX:
   - show scanning state for a timeout window,
   - only show “no devices” after timeout.
3. Choose one reconnect policy and wire end-to-end:
   - Either remove stored reconnect behavior entirely,
   - or enable and invoke reconnect at app/player startup with explicit guardrails.

### Tests
- Discovery timing tests (slow network simulation).
- Reconnect behavior tests for enabled/disabled modes.

### Exit criteria
- Discovery behavior is deterministic and reconnect behavior matches settings.

---

## Phase 3 — Lifecycle/resource hygiene (Day 5)

### Goals
- Prevent long-lived resource leaks/regressions.

### Tasks
- Replace unmanaged per-init executor creation with lifecycle-managed dispatcher/executor.
- Ensure callbacks/listeners/routes are always registered/unregistered symmetrically.
- Add init/release stress tests for repeated player/activity recreation.

### Exit criteria
- No leaked threads/listeners in repeated init/release runs.

---

## Phase 4 — Modular refactor (Days 6–9)

### Goals
- De-risk future changes and improve testability.

### Target split
- `CastSessionController` (SDK lifecycle + session callbacks)
- `CastDiscoveryController` (device scanning + route selection)
- `CastMediaLoadBuilder` (URL/media metadata/subtitles/tracks)
- `CastStateStore` (stateflow + persistence contract)
- `CastPlaybackController` (remote controls + position sync hooks)

`VideoPlayerViewModel` remains orchestration-only.

### Tests
- Contract tests per component.
- One integration test for local -> cast -> local round trip.

### Exit criteria
- Core cast behavior survives refactor with stable/clear boundaries.

---

## Phase 5 — Validation & rollout (Days 9–12)

### Goals
- Validate across devices/network conditions and release safely.

### Tasks
- Real-device matrix:
  - Chromecast (3rd gen)
  - Chromecast with Google TV
  - Android TV receiver
- Regression scenarios:
  - start/seek/pause/resume/stop,
  - disconnect/reconnect,
  - background/foreground,
  - token refresh and network transitions.
- Staged rollout with runtime telemetry dashboards by failure category.

### Exit criteria
- Stable KPIs for connection success, playback start success, and failure-rate reduction.

---

## 5) Prioritized backlog (single ordered queue)

1. Feature-flag + diagnostics baseline.
2. Protected-auth cast-safe URL/proxy strategy.
3. Session continuity (`playSessionId`, `mediaSourceId`) enforcement.
4. Discovery async state + dialog timeout UX.
5. Reconnect policy unification.
6. Executor/listener lifecycle cleanup.
7. Modular extraction from `CastManager`.
8. Device matrix, telemetry validation, staged rollout.

---

## 6) Definition of done (“Stable Cast”)

- High cast initiation success on authenticated servers.
- No premature “no devices found” under normal LAN conditions.
- Local <-> Cast handoff resumes near-correct position consistently.
- Reconnect behavior is predictable and matches configured policy.
- No thread/listener leaks across repeated playback sessions.
- Automated coverage includes happy path + top failure modes.

---

## 7) Notes for implementation PR strategy

- Keep PRs phase-aligned and small.
- Each PR must include:
  - explicit acceptance criteria,
  - tests for newly addressed failure modes,
  - telemetry/log updates for new code paths.
- Do not mix deep refactors into P0/P1 reliability fixes unless necessary to unblock.
