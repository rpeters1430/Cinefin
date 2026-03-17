# Cinefin Upgrade Path (Execution Roadmap)

**Date**: 2026-03-16  
**Status**: Draft / Execution Initialized  
**Reference Plan**: [2026-03-16-upgrade-plan.md](2026-03-16-upgrade-plan.md)

---

## 1. Current Dependency Risk Board

| Dependency Group | Current Version | Channel | Risk | Target (Phase 1) |
|:---|:---|:---|:---|:---|
| **Kotlin / KSP** | 2.3.20 / 2.3.6 | Stable | Low | Monitor for 2.4.x |
| **Compose BOM** | 2026.03.00 | Stable | Low | Keep current |
| **Material 3 Suite** | 1.5.0-alpha15 | **Alpha** | High | **Hold Alpha** (Expressive) |
| **Media3** | 1.10.0-rc02 | **RC** | Medium | 1.10.x Stable |
| **Navigation** | 2.10.0-alpha01 | **Alpha** | Medium | **Hold Alpha** (UI Stack) |
| **Lifecycle** | 2.11.0-alpha02 | **Alpha** | Medium | **Hold Alpha** (UI Stack) |
| **Paging** | 3.5.0-alpha01 | **Alpha** | Low | **Hold Alpha** (UI Stack) |
| **Activity Compose** | 1.13.0-alpha01| **Alpha** | Low | **Hold Alpha** (UI Stack) |
| **DataStore** | 1.3.0-alpha07 | **Alpha** | Medium | 1.3.0 Stable |
| **Biometric** | 1.4.0-alpha05 | **Alpha** | Low | 1.4.x Stable |
| **Window/Adaptive** | 1.6.0-alpha01 | **Alpha** | Medium | **Hold Alpha** (UI Stack) |

---

## 2. Detailed Upgrade Paths

### A. Core Stabilization (Phase 1: Week 2)
*   **Goal**: Ensure non-UI infrastructure is stable while keeping UI stack on cutting edge.
*   **Path**:
    1.  **Media3**: `1.10.0-rc02` → `1.10.0` (Stable).
    2.  **DataStore**: `1.3.0-alpha07` → Monitor for `1.3.0` Stable.
    3.  **WorkManager**: `2.11.1` (Stable) - already current.
*   **Validation**: Test app backgrounding, configuration changes (rotation), and ViewModel survival.

### B. Media & Playback (Phase 1: Week 3)
*   **Goal**: Ensure Media3 stability, especially for Cast and Session management.
*   **Path**:
    1.  **Media3**: `1.10.0-rc02` → `1.10.0` (Stable) or latest `1.10.x` patch.
*   **Validation**: 
    - Full playback matrix (HLS, DASH, Direct).
    - Chromecast connection and media notification controls.
    - Android Auto/MediaSession metadata consistency.

### C. UI Stack Maintenance (Phase 4: Blocked / Hold Alpha)
*   **Goal**: Maintain modern "Expressive" UI by tracking alpha/beta tracks for all UI-related components.
*   **Path**:
    1.  **M3 Suite**: `1.5.0-alpha15`.
    2.  **Navigation**: `2.10.0-alpha01`.
    3.  **Lifecycle**: `2.11.0-alpha02`.
    4.  **Activity Compose**: `1.13.0-alpha01`.
    5.  **Paging**: `3.5.0-alpha01`.
*   **Strategy**: Stay on alpha/beta tracks until these features move to stable collectively. Maintain the `ExpressiveHeroCarousel` wrapper and other UI abstractions.

---

## 3. Breaking Change Watchlist

| Library | Potential Change | Mitigation Strategy |
|:---|:---|:---|
| **Media3** | Cast/Session API tweaks | Centralize MediaController logic in `PlaybackManager`. |
| **Navigation** | Type-safe nav changes | Use generated routes; avoid manual string manipulation. |
| **DataStore** | Migration API changes | Abstract DataStore access behind `SecureCredentialManager`. |
| **M3 Expressive** | Carousel/Hero API shifts | Maintain the `ExpressiveHeroCarousel` wrapper. |

---

## 4. Immediate Execution Checklist (Next 48 Hours)

- [ ] **Task 0.1**: Align `docs/plans/CURRENT_STATUS.md` with the version list in `libs.versions.toml`.
- [ ] **Task 1.1**: Create a branch `upgrade/foundation-stabilization`.
- [ ] **Task 1.2**: Downgrade `Lifecycle` and `Navigation` to stable tracks in `libs.versions.toml`.
- [ ] **Task 1.3**: Run `gradlew ciTest` and verify local smoke tests for playback.

---

## 5. Success Metric Tracking

| Metric | Baseline (Mar 16) | Target | Current |
|:---|:---|:---|:---|
| **Alpha/RC Count** | 12 | < 5 | 12 |
| **CI Test Pass Rate** | 100% | 100% | 100% |
| **Startup Time (avg)** | TBD | < 800ms | TBD |
| **TV D-pad Issues** | TBD | 0 | TBD |
