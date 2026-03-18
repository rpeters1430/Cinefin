# Design: Material 3 Expressive — Critical & High Priority Fixes

**Date:** 2026-03-18
**Scope:** Critical + High priority issues from the M3 Expressive audit
**Approach:** 4 sequential commits, each independently reviewable

---

## Problem Statement

A full audit of the Cinefin app revealed that despite having a solid Material 3 Expressive foundation, several systemic gaps prevent M3 components from behaving with expressive motion and shape semantics:

1. `MotionScheme.expressive()` is defined in `Motion.kt` but never passed to `MaterialTheme {}`, so all native M3 components fall back to standard motion.
2. The `resolutionStrategy` in `build.gradle.kts` pins core M3 artifacts to `1.5.0-alpha13`, while the version catalog declares `1.5.0-alpha15`.
3. 152 occurrences of `RoundedCornerShape(Ndp)` across 48 UI files bypass the shape token system.
4. 12+ call sites use bare `CircularProgressIndicator` / `LinearProgressIndicator` instead of the `ExpressiveWavy*` wrappers.

---

## Commit 1: Wire motionScheme into MaterialTheme

**File:** `app/src/main/java/com/rpeters/jellyfin/ui/theme/Theme.kt`

**Change:** Add `motionScheme = MotionTokens.expressiveMotionScheme` to the `MaterialTheme {}` call (line ~105).

`Theme.kt` exposes two `JellyfinAndroidTheme` overloads. The primary at line 54 calls `MaterialTheme`; the legacy at line 122 delegates to the primary. Only the primary overload's `MaterialTheme {}` call needs the new parameter.

**Implementation note:** The current call uses the three-slot overload `(colorScheme, typography, shapes, content)`. Adding `motionScheme` as a named Kotlin parameter is safe and switches to the four-slot overload transparently. Named arguments must be used to avoid positional ambiguity.

**Verification:** After this commit, search for any other `MaterialTheme(` calls in test fixtures or `@Preview` functions — if any exist, they should also receive `motionScheme = MotionTokens.expressiveMotionScheme`.

**Why this matters:** Every M3 component (`Button`, `NavigationBar`, `FAB`, etc.) that internally reads `MaterialTheme.motionScheme` will now use expressive spring/easing curves automatically.

**Risk:** Low. The value already exists in `Motion.kt`; this is a plumbing connection only.

---

## Commit 2: Align resolutionStrategy with version catalog

**File:** `app/build.gradle.kts`

**Change:** Update the forced pin from `1.5.0-alpha13` to `1.5.0-alpha15` on line ~309.

**Scope clarification:** The `resolutionStrategy` affects the `androidx.compose.material3` artifact group (core M3, adaptive, navigation suite). It does **not** affect the `material3ExpressiveComponents` artifact, which the version catalog already declares separately at `1.5.0-alpha02` — that entry remains unchanged.

**Why this matters:** Some shape tokens (e.g., `shapes.full`) and motion APIs may differ between alpha13 and alpha15. Aligning the pin ensures the version catalog's declared versions actually resolve at build time.

**Risk:** Low-medium. Run `./gradlew assembleDebug` immediately after this commit and verify before proceeding to Commit 3.

---

## Commit 3: Replace hardcoded RoundedCornerShape with MaterialTheme.shapes tokens

**Files:** 48 UI files across `ui/components/`, `ui/screens/`, `ui/player/`

### Token mapping

| Hardcoded value | Replacement token | JellyfinShapes dp |
|---|---|---|
| `RoundedCornerShape(4.dp)` | `MaterialTheme.shapes.extraSmall` | 4dp |
| `RoundedCornerShape(8.dp)` | `MaterialTheme.shapes.small` | 8dp |
| `RoundedCornerShape(12.dp)` | `MaterialTheme.shapes.medium` | 12dp |
| `RoundedCornerShape(16.dp)` | `MaterialTheme.shapes.large` | 16dp |
| `RoundedCornerShape(28.dp)` | `MaterialTheme.shapes.extraLarge` | 28dp |

### shapes.full gap

`JellyfinShapes` currently does not wire `ShapeTokens.Full` into the `Shapes(...)` constructor. Before replacing any circular shape, add `full = ShapeTokens.Full` as a named parameter inside the `Shapes(...)` constructor call assigned to `JellyfinShapes` in `Shape.kt` (lines 49–55). After that:
- `RoundedCornerShape(50)` / `RoundedCornerShape(50.dp)` / percentage-based fully-rounded shapes → `MaterialTheme.shapes.full`
- `CircleShape` (used as a layout clip for image avatars, not as a card shape) → leave as `CircleShape`

### Exclusions (leave as-is)

- **Directional corner shapes** (`RoundedCornerShape(topStart=X, bottomEnd=Y)`) — no token equivalent; leave unchanged
- **TV-specific shapes** — intentional TV UX decisions; leave unchanged
- **`DownloadsScreen.kt` storage bar (line 297)** — `LinearProgressIndicator` with `clip(CircleShape)` for a storage-usage bar; this is a progress component edge case handled in Commit 4, not a card shape

### Priority files (highest rendered frequency)

1. `ui/components/MediaCards.kt`
2. `ui/screens/home/HomeCarousel.kt`
3. `ui/components/ExpressiveCarousel.kt`
4. `ui/components/immersive/ImmersiveMediaCard.kt`
5. `ui/components/PlaybackRecommendationNotifications.kt`

**Why this matters:** Visual output is unchanged (dp values match). But these sites now respond to theme shape changes, and corner radii become manageable from one place.

**Risk:** Low. Direct dp-to-token mapping. The only risk is a directional shape being incorrectly mapped — the exclusion list prevents this.

---

## Commit 4: Replace bare progress indicators with ExpressiveWavy wrappers

**Files:** 10 files, ~14 indicator sites

### Opt-in requirement

The `ExpressiveWavy*` functions in `ExpressiveWavyProgress.kt` are annotated `@OptInAppExperimentalApis`. Every target file that does not already use an `ExpressiveWavy*` component must add the file-level opt-in at the top:

```kotlin
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)
```

Or annotate the specific composable with `@OptInAppExperimentalApis`. Files that already use `ExpressiveWavy*` components have this and need no change.

### Replacement map

| Current | Replacement | Notes |
|---|---|---|
| `CircularProgressIndicator()` | `ExpressiveWavyCircularLoading()` | Indeterminate |
| `CircularProgressIndicator(progress = { x })` | `ExpressiveWavyCircularProgress(progress = x)` | Determinate — unwrap lambda to `Float` |
| `LinearProgressIndicator()` | `ExpressiveWavyLinearLoading()` | Indeterminate |
| `LinearProgressIndicator(progress = { x })` | `ExpressiveWavyLinearProgress(progress = x)` | Determinate — unwrap lambda to `Float` |

**trackColor note:** The wavy wrappers default `trackColor` to `MaterialTheme.colorScheme.surfaceVariant`. The standard `LinearProgressIndicator` defaults to `surfaceContainerHighest`. This is a subtle visual difference for indeterminate indicators. Pass `trackColor = ProgressIndicatorDefaults.linearTrackColor` to the wrapper if preserving the original appearance is important at a given site.

**Determinate lambda unwrapping:** Some call sites pass `progress` as a lambda `{ float }`. The `ExpressiveWavy*` wrappers take a plain `Float`. Evaluate the lambda at the call site:
```kotlin
// Before
LinearProgressIndicator(progress = { currentPosition.toFloat() / duration.toFloat() })
// After
ExpressiveWavyLinearProgress(progress = currentPosition.toFloat() / duration.toFloat())
```

### Target files and sites

| File | Lines | Type |
|---|---|---|
| `ui/downloads/DownloadsScreen.kt` | 297, 641 | Determinate, Indeterminate |
| `ui/components/DownloadButton.kt` | 199, 205, 286, 293 | Mixed |
| `ui/player/MiniPlayer.kt` | 127 | Determinate — lambda unwrap required |
| `ui/screens/PersonDetailScreen.kt` | 139, 425 | Circular + Linear |
| `ui/player/VideoPlayerDialogs.kt` | 270 | Circular |
| `ui/components/AiSummaryCard.kt` | 36 | Indeterminate linear |
| `ui/screens/details/components/WhyYoullLoveThisCard.kt` | 69 | Indeterminate linear |
| `ui/components/ConnectionProgress.kt` | 142 | Linear |
| `ui/components/AccessibleLoadingStates.kt` | 127 | Linear |
| `ui/components/PlaybackProgressIndicator.kt` | 73 | Linear |

**Why this matters:** The wrappers provide M3 Expressive wavy animation and include an emulator fallback (avoiding shader ANRs). Bare indicators bypass both the visual upgrade and the safety guard.

**Risk:** Low. Same API surface. Emulator fallback means no runtime risk.

---

## Success Criteria

1. `./gradlew assembleDebug` passes after each individual commit
2. `./gradlew testDebugUnitTest` passes after the full set of commits
3. No `RoundedCornerShape` in `MediaCards.kt`, `HomeCarousel.kt`, `ExpressiveCarousel.kt`
4. No bare `CircularProgressIndicator` or `LinearProgressIndicator` in the 10 target files (all occurrences, not just listed lines)
5. `MaterialTheme {}` in `Theme.kt` includes `motionScheme = MotionTokens.expressiveMotionScheme`
6. `JellyfinShapes` includes `full = ShapeTokens.Full`
7. `resolutionStrategy` pin reads `1.5.0-alpha15` for `androidx.compose.material3` group

---

## Out of Scope (deferred to full sweep)

- Button default shape → `shapes.full`
- 24 screens bypassing `Expressive*` button wrappers
- 18 screens using raw `TopAppBar`
- 9 screens using raw `PullToRefreshBox`
- 4 duplicate `NavigationBar` implementations
- 153 `FontWeight` overrides
- 5 hardcoded `sp` font sizes
- Direct color usage (`Color.Red`, `Color.White` for semantic roles)
