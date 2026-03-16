# Cinefin Upgrade Plan (Current State)

**Date**: 2026-03-16  
**Based On**: `README.md`, `docs/plans/CURRENT_STATUS.md`, `docs/plans/ROADMAP.md`, `docs/plans/UPGRADE_PATH.md`, `docs/features/KNOWN_ISSUES.md`, and current dependency catalog in `gradle/libs.versions.toml`.

---

## 1) Current-State Snapshot

### Product and platform
- Core video streaming and browsing experience is feature-rich and broadly complete on phone/tablet.
- Android TV support exists but still needs focused polish and D-pad/focus validation.
- Several historical docs conflict on completion state for some features (notably offline downloads), so a doc-alignment pass is needed before execution tracking.

### Technical baseline
- The project is already on very recent toolchain and library versions (Kotlin 2.3.20, AGP 9.1.0, Compose BOM 2026.03.00).
- A meaningful portion of the stack intentionally uses alpha/RC dependencies for Material 3 Expressive and adaptive APIs.
- This gives access to modern features but increases upgrade and regression risk.

### Main risks right now
1. **Version-channel risk**: many alpha/RC dependencies in UI/navigation/media stack.
2. **Documentation drift risk**: status/roadmap/known-issues documents are not fully synchronized.
3. **TV experience risk**: feature exists but QA hardening is incomplete.
4. **Reliability/performance risk**: large composables and warning debt can hide regressions.

---

## 2) Upgrade Objectives

1. **Stabilize the foundation** without losing key expressive UI capabilities.
2. **Reduce release risk** by shrinking alpha/RC surface area where possible.
3. **Close QA gaps** on Android TV, playback reliability, and accessibility.
4. **Create one trustworthy status source** for planning and execution.

---

## 3) Phased Upgrade Plan

### Phase 0 (Week 1): Baseline and Documentation Alignment

### Deliverables
- Create a single authoritative status table and align conflicting docs.
- Freeze a measurable baseline for build health, test pass rate, startup performance, and playback crash/error rates.
- Tag all planned upgrades as **safe now**, **blocked**, or **monitor-only**.

### Tasks
- Reconcile `CURRENT_STATUS.md` vs `KNOWN_ISSUES.md` for offline downloads/music/TV status.
- Add a short “source of truth” section in planning docs clarifying where current truth is maintained.
- Capture a baseline report (CI pass rate, lint warning count, flaky tests, known regressions).

### Exit criteria
- No contradiction in user-facing status docs.
- Team can answer “what works today?” from one place.

---

## Phase 1 (Weeks 2–4): Dependency Stabilization (Low-to-Medium Risk)

### Goal
Move non-critical alpha/RC dependencies to stable where feature parity exists.

### Priority order
1. **Navigation** (`2.10.0-alpha01` → stable line if compatible)
2. **Lifecycle Runtime** (`2.11.0-alpha02` → stable line if compatible)
3. **Paging** (`3.5.0-alpha01` → stable if no blockers)
4. **Media3** (`1.10.0-rc02` → stable `1.10.x`)
5. **DataStore** (`1.3.0-alpha07` → stable when available/validated)
6. **Window/Adaptive libraries**: downgrade only if expressive/adaptive behavior is unchanged

### Validation gates per upgrade
- Full unit tests and lint.
- Playback smoke tests: direct play, transcode fallback, subtitle/audio switching.
- TV smoke tests for focus navigation where navigation/material dependencies change.

### Exit criteria
- Reduced alpha/RC dependency count with no functional regressions.
- Updated `UPGRADE_PATH.md` with completed moves and explicit hold reasons.

---

## Phase 2 (Weeks 5–7): Android TV and Accessibility Hardening

### Goal
Make TV support “release-confident” rather than “partially tested.”

### Tasks
- Execute D-pad navigation audit across TV home, library, detail, and player screens.
- Fix focus visibility, initial focus placement, and dead-end navigation traps.
- Run TalkBack/accessibility checks on core phone/tablet and TV flows.
- Ensure all interactive controls meet 48dp touch/focus target guidance and meaningful semantics.

### Exit criteria
- TV critical-path test checklist passes.
- Accessibility high-severity findings closed for primary flows.

---

## Phase 3 (Weeks 8–10): Performance and Code Health

### Goal
Lower ongoing maintenance risk and improve runtime consistency on lower-end devices.

### Tasks
- Complete pending immersive UI performance monitoring and profiling tasks.
- Refactor oversized composables (start with home/player screens) into testable subcomponents.
- Reduce build warnings with a “no new warnings” rule in CI.
- Add/expand regression tests around playback quality switching and offline-flow edge cases.

### Exit criteria
- Measurable reduction in frame-time jank and memory spikes on low/mid-tier devices.
- Warning count trending down with CI guardrails.

---

## Phase 4 (Weeks 11–12): Material 3 Expressive Stabilization Readiness

### Goal
Prepare for eventual stable Expressive/Adaptive releases with minimal disruption.

### Tasks
- Inventory all expressive/alpha API touchpoints and abstract where practical.
- Add screenshot/visual regression coverage for high-risk screens.
- Define a one-sprint “migration playbook” to execute once stable APIs land.

### Exit criteria
- Migration checklist is prepared before stable release day.
- Visual and interaction regressions are detectable quickly.

---

## 4) Governance and Delivery Controls

### Cadence
- Weekly upgrade review: dependency moves, blockers, regressions, doc updates.
- Biweekly release candidate builds for broader device validation.

### Required quality gates
- `assembleDebug`, unit tests, lint, and at least one TV smoke pass before merging dependency changes.
- Any upgrade touching playback requires a manual playback validation checklist run.

### Rollback strategy
- Keep upgrades isolated in small PRs (one library family at a time).
- Maintain fast rollback path via version-catalog reverts.

---

## 5) Suggested Backlog (Ordered)

1. **Doc truth alignment PR** (status + known issues + roadmap consistency).
2. **Navigation/Lifecycle stabilization PR**.
3. **Media3 stabilization PR** with playback regression matrix.
4. **TV D-pad/focus audit and fixes PR series**.
5. **Accessibility remediation PR series**.
6. **Large composable refactor PR series**.
7. **Expressive stabilization prep PR** (abstractions + visual tests).

---

## 6) Success Metrics

- Alpha/RC dependency count reduced by at least 30% where feasible without feature loss.
- Android TV critical-path pass rate ≥ 95% on defined checklist.
- Lint/warning count reduced materially from baseline with no upward drift.
- Fewer doc contradictions: status and roadmap reviewed and synced each sprint.
- No increase in playback crash/session failure rates during upgrade window.

---

## 7) Immediate Next Actions (This Week)

1. Approve this phased plan and lock owners per phase.
2. Open the **Phase 0 doc-alignment** PR as the first execution task.
3. Generate a dependency risk board from `libs.versions.toml` (stable vs alpha/RC).
4. Start Phase 1 with smallest-risk stable migrations first (navigation/lifecycle).
