# Material 3 Expressive Critical & High Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 4 systemic gaps that prevent Material 3 Expressive components from using expressive motion, correct shape tokens, and wavy progress indicators throughout the app.

**Architecture:** 4 sequential commits — theme wiring, version pin update, shape token sweep across 48 files, progress indicator replacement in 10 files. Each commit must build cleanly before the next begins.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 1.5.0-alpha15, `androidx.compose.material3.MotionScheme`, `MaterialTheme.shapes.*`, `ExpressiveWavy*` wrappers (in-project at `ui/components/ExpressiveWavyProgress.kt`)

**Spec:** `docs/superpowers/specs/2026-03-18-material3-expressive-critical-high-fixes-design.md`

**Task ordering note:** Task 1 (motionScheme) requires M3 alpha15 which is currently pinned at alpha13. If Task 1 produces "unresolved reference: motionScheme" during the build, execute Task 2 first (version pin update), then return to Task 1.

---

## Opt-in convention for this project

Throughout this plan, whenever a file needs the `ExperimentalMaterial3ExpressiveApi` opt-in, use the project's canonical bundled annotation instead of the raw form:

```kotlin
// Project convention — covers ExperimentalMaterial3ExpressiveApi and all other common APIs
@file:OptInAppExperimentalApis   // file-level
// OR on a specific composable:
@OptInAppExperimentalApis
```

The `@OptInAppExperimentalApis` annotation is defined at `com.rpeters.jellyfin.OptInAppExperimentalApis` and already bundles `ExperimentalMaterial3ExpressiveApi::class`. Import: `import com.rpeters.jellyfin.OptInAppExperimentalApis`.

If a file already has `@file:OptInAppExperimentalApis` or `@OptInAppExperimentalApis` on any of its composables, **no additional annotation is needed** — the opt-in is already in scope.

---

## Task 1: Wire motionScheme into MaterialTheme

**Files:**
- Modify: `app/src/main/java/com/rpeters/jellyfin/ui/theme/Theme.kt:105-110`

**Note:** This is a theme plumbing change with no unit tests. Verification is a clean build.

- [ ] **Step 1: Add file-level opt-in to Theme.kt**

`Theme.kt` currently has no `@file:OptIn` or `@OptInAppExperimentalApis`. Add at the very top of the file (before the `package` line):

```kotlin
@file:OptInAppExperimentalApis
```

And add the import alongside the existing imports:

```kotlin
import com.rpeters.jellyfin.OptInAppExperimentalApis
```

- [ ] **Step 2: Add motionScheme parameter to MaterialTheme call**

At `Theme.kt:105-110`, change:

```kotlin
MaterialTheme(
    colorScheme = tunedColorScheme,
    typography = Typography,
    shapes = JellyfinShapes,
    content = content,
)
```

To:

```kotlin
MaterialTheme(
    colorScheme = tunedColorScheme,
    motionScheme = MotionTokens.expressiveMotionScheme,
    typography = Typography,
    shapes = JellyfinShapes,
    content = content,
)
```

`MotionTokens` is in the same package (`com.rpeters.jellyfin.ui.theme`) — no import needed. Kotlin type inference handles `MotionScheme` type resolution; no explicit `import androidx.compose.material3.MotionScheme` is required in `Theme.kt`.

- [ ] **Step 3: Check for other MaterialTheme call sites**

```bash
grep -rn "MaterialTheme(" app/src/main/java/ --include="*.kt"
```

If any `@Preview` functions call `MaterialTheme(` directly (not via `JellyfinAndroidTheme`), add the same `motionScheme` parameter to them too.

- [ ] **Step 4: Build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL. If you see "unresolved reference: motionScheme", the current M3 version pin (alpha13) doesn't expose this overload — execute Task 2 first, then return here.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/theme/Theme.kt
git commit -m "fix(theme): wire expressiveMotionScheme into MaterialTheme"
```

---

## Task 2: Align resolutionStrategy with version catalog

**Files:**
- Modify: `app/build.gradle.kts:309`

- [ ] **Step 1: Update the version pin**

At `build.gradle.kts:309`, change:

```kotlin
useVersion("1.5.0-alpha13")
```

To:

```kotlin
useVersion("1.5.0-alpha15")
```

Leave all surrounding logic unchanged. Do **not** touch the `material3ExpressiveComponents` artifact version declared separately in `libs.versions.toml` as `1.5.0-alpha02` — that is a different artifact group and is correct as-is.

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL. If you see "cannot access class" or "unresolved reference" errors, an API changed between alpha13 and alpha15. Fix the affected call sites before proceeding to Task 3.

- [ ] **Step 3: Verify resolved version**

```bash
./gradlew dependencies --configuration debugRuntimeClasspath | grep "material3 "
```

Expected output contains: `androidx.compose.material3:material3:1.5.0-alpha15`

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore(deps): align M3 resolutionStrategy to 1.5.0-alpha15"
```

---

## Task 3: Add shapes.full to JellyfinShapes

**Files:**
- Modify: `app/src/main/java/com/rpeters/jellyfin/ui/theme/Shape.kt:49-55`

This must be done before the shape sweep (Task 4) so `MaterialTheme.shapes.full` resolves.

- [ ] **Step 1: Add full parameter to the Shapes constructor**

At `Shape.kt:49-55`, change:

```kotlin
val JellyfinShapes = Shapes(
    extraSmall = ShapeTokens.ExtraSmall,
    small = ShapeTokens.Small,
    medium = ShapeTokens.Medium,
    large = ShapeTokens.Large,
    extraLarge = ShapeTokens.ExtraLarge,
)
```

To:

```kotlin
val JellyfinShapes = Shapes(
    extraSmall = ShapeTokens.ExtraSmall,
    small = ShapeTokens.Small,
    medium = ShapeTokens.Medium,
    large = ShapeTokens.Large,
    extraLarge = ShapeTokens.ExtraLarge,
    full = ShapeTokens.Full,
)
```

`ShapeTokens.Full` is already defined at `Shape.kt:28` as `RoundedCornerShape(50.dp)`.

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/theme/Shape.kt
git commit -m "fix(theme): add shapes.full token to JellyfinShapes"
```

---

## Task 4: Replace hardcoded RoundedCornerShape with MaterialTheme.shapes tokens

**Files:** 48 UI files across `ui/components/`, `ui/screens/`, `ui/player/`

### Token mapping reference

| Find | Replace with |
|---|---|
| `RoundedCornerShape(4.dp)` | `MaterialTheme.shapes.extraSmall` |
| `RoundedCornerShape(8.dp)` | `MaterialTheme.shapes.small` |
| `RoundedCornerShape(12.dp)` | `MaterialTheme.shapes.medium` |
| `RoundedCornerShape(16.dp)` | `MaterialTheme.shapes.large` |
| `RoundedCornerShape(28.dp)` | `MaterialTheme.shapes.extraLarge` |
| `RoundedCornerShape(50.dp)` or `RoundedCornerShape(50)` (card context) | `MaterialTheme.shapes.full` |

### Exclusion rules (do NOT replace)

- `RoundedCornerShape(topStart=X, bottomEnd=Y, ...)` — directional shapes, leave as-is
- Any shape in `ui/tv/` or TV-specific components — leave as-is
- `CircleShape` — layout clip for circular images/avatars, not a card shape; leave as-is
- `RoundedCornerShape(50)` used as `clip(CircleShape)` substitute in `DownloadsScreen.kt:297` storage bar — leave as-is (this site is handled as a progress indicator context in Task 5)

- [ ] **Step 1: Find all occurrences to establish baseline**

```bash
grep -rn "RoundedCornerShape([0-9]" app/src/main/java/com/rpeters/jellyfin/ui/ --include="*.kt" | grep -v "/tv/"
```

Note the total count. Expected: ~152. This is your baseline for the final verification.

- [ ] **Step 2: Fix priority file — MediaCards.kt**

File: `app/src/main/java/com/rpeters/jellyfin/ui/components/MediaCards.kt`

Find all `RoundedCornerShape(Ndp)` in this file and replace per the mapping table. After all replacements in the file, check if `RoundedCornerShape` import is still used — if not, remove the import.

Build check:
```bash
./gradlew assembleDebug 2>&1 | tail -5
```

- [ ] **Step 3: Fix priority file — HomeCarousel.kt**

File: `app/src/main/java/com/rpeters/jellyfin/ui/screens/home/HomeCarousel.kt`

Same approach. Build check after.

- [ ] **Step 4: Fix priority file — ExpressiveCarousel.kt**

File: `app/src/main/java/com/rpeters/jellyfin/ui/components/ExpressiveCarousel.kt`

Same approach. Build check after.

- [ ] **Step 5: Fix priority file — ImmersiveMediaCard.kt**

File: `app/src/main/java/com/rpeters/jellyfin/ui/components/immersive/ImmersiveMediaCard.kt`

Same approach.

- [ ] **Step 6: Fix priority file — PlaybackRecommendationNotifications.kt**

File: `app/src/main/java/com/rpeters/jellyfin/ui/components/PlaybackRecommendationNotifications.kt`

Same approach.

- [ ] **Step 7: Fix all remaining files**

Get the full list of remaining files:

```bash
grep -rln "RoundedCornerShape([0-9]" app/src/main/java/com/rpeters/jellyfin/ui/ --include="*.kt" | grep -v "/tv/"
```

Process **every** file in this output — do not skip any. For each file:
1. Replace each `RoundedCornerShape(Ndp)` per the mapping table
2. Skip any directional shapes and `CircleShape`
3. Remove the `RoundedCornerShape` import if no longer used in the file

- [ ] **Step 8: Verify no plain occurrences remain**

```bash
grep -rn "RoundedCornerShape([0-9]" app/src/main/java/com/rpeters/jellyfin/ui/ --include="*.kt" | grep -v "/tv/"
```

Expected: Zero results. (Directional shapes like `topStart=` will not match this pattern and are correctly excluded.)

- [ ] **Step 9: Full build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/
git commit -m "refactor(ui): replace hardcoded RoundedCornerShape with MaterialTheme.shapes tokens"
```

---

## Task 5: Replace bare progress indicators with ExpressiveWavy wrappers

**Files:** 10 files, 14 indicator sites

### Opt-in check for each target file

Before editing each file, run:

```bash
grep -n "OptInAppExperimentalApis\|ExperimentalMaterial3ExpressiveApi" <file>
```

- If any result is found → the opt-in is already present, no annotation addition needed
- If no result → add at the very top of the file (before `package`):
  ```kotlin
  @file:OptInAppExperimentalApis
  ```
  And add the import:
  ```kotlin
  import com.rpeters.jellyfin.OptInAppExperimentalApis
  ```

### Import to add in each file

Only import the variants actually used in that file:

```kotlin
import com.rpeters.jellyfin.ui.components.ExpressiveWavyCircularLoading
import com.rpeters.jellyfin.ui.components.ExpressiveWavyCircularProgress
import com.rpeters.jellyfin.ui.components.ExpressiveWavyLinearLoading
import com.rpeters.jellyfin.ui.components.ExpressiveWavyLinearProgress
```

Remove the corresponding `CircularProgressIndicator` / `LinearProgressIndicator` imports if they become unused.

### trackColor note

The wavy wrappers default `trackColor = MaterialTheme.colorScheme.surfaceVariant`. The standard `LinearProgressIndicator` defaults to `surfaceContainerHighest`. If the original call explicitly passes `trackColor`, preserve it. If not, accept the new default — the visual difference is minimal.

### Determinate lambda unwrapping

The wavy wrappers take a plain `Float`, not a lambda. Unwrap at call site:

```kotlin
// Before
LinearProgressIndicator(progress = { displayProgress })
// After
ExpressiveWavyLinearProgress(progress = displayProgress)
```

- [ ] **Step 1: Fix DownloadsScreen.kt**

File: `app/src/main/java/com/rpeters/jellyfin/ui/downloads/DownloadsScreen.kt`

- Line 297: Determinate `LinearProgressIndicator(progress = { ... })` → `ExpressiveWavyLinearProgress(progress = ...)` — unwrap the lambda
- Line 641: Indeterminate `LinearProgressIndicator()` → `ExpressiveWavyLinearLoading()`

- [ ] **Step 2: Fix DownloadButton.kt**

File: `app/src/main/java/com/rpeters/jellyfin/ui/components/DownloadButton.kt`

- Line 199: `CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)` — indeterminate → `ExpressiveWavyCircularLoading(modifier = Modifier.size(16.dp))` (drop `strokeWidth`, not a wrapper param)
- Line 205: `CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 3.dp)` — indeterminate → `ExpressiveWavyCircularLoading(modifier = Modifier.size(40.dp))`
- Line 286: `LinearProgressIndicator(progress = { displayProgress }, modifier = ...)` — determinate → `ExpressiveWavyLinearProgress(progress = displayProgress, modifier = ...)`
- Line 293: `CircularProgressIndicator(progress = { displayProgress }, modifier = ..., strokeWidth = ...)` — determinate → `ExpressiveWavyCircularProgress(progress = displayProgress, modifier = ...)` (drop `strokeWidth`)

- [ ] **Step 3: Fix MiniPlayer.kt**

File: `app/src/main/java/com/rpeters/jellyfin/ui/player/MiniPlayer.kt`

- Line 127: Determinate `LinearProgressIndicator` with progress lambda — replace with `ExpressiveWavyLinearProgress`, unwrap the lambda:

```kotlin
// Before (approximate)
LinearProgressIndicator(progress = { currentPosition.toFloat() / duration.toFloat() })

// After
ExpressiveWavyLinearProgress(
    progress = currentPosition.toFloat() / duration.toFloat()
)
```

- [ ] **Step 4: Fix PersonDetailScreen.kt**

File: `app/src/main/java/com/rpeters/jellyfin/ui/screens/PersonDetailScreen.kt`

- Line 139: Indeterminate `CircularProgressIndicator()` → `ExpressiveWavyCircularLoading()`
- Line 425: Indeterminate `LinearProgressIndicator` → `ExpressiveWavyLinearLoading()`

- [ ] **Step 5: Fix VideoPlayerDialogs.kt**

File: `app/src/main/java/com/rpeters/jellyfin/ui/player/VideoPlayerDialogs.kt`

- Line 270: Indeterminate `CircularProgressIndicator()` → `ExpressiveWavyCircularLoading()`

- [ ] **Step 6: Fix AiSummaryCard.kt**

File: `app/src/main/java/com/rpeters/jellyfin/ui/components/AiSummaryCard.kt`

- Line 36: Indeterminate `LinearProgressIndicator()` → `ExpressiveWavyLinearLoading()`

- [ ] **Step 7: Fix WhyYoullLoveThisCard.kt**

File: `app/src/main/java/com/rpeters/jellyfin/ui/screens/details/components/WhyYoullLoveThisCard.kt`

- Line 69: Indeterminate `LinearProgressIndicator()` → `ExpressiveWavyLinearLoading()`

- [ ] **Step 8: Fix ConnectionProgress.kt**

File: `app/src/main/java/com/rpeters/jellyfin/ui/components/ConnectionProgress.kt`

- Line 142: Determine if determinate or indeterminate by reading the line. Replace with `ExpressiveWavyLinearProgress(progress = ...)` if determinate, or `ExpressiveWavyLinearLoading()` if indeterminate.

- [ ] **Step 9: Fix AccessibleLoadingStates.kt**

File: `app/src/main/java/com/rpeters/jellyfin/ui/components/AccessibleLoadingStates.kt`

- Line 127: Same — read line, apply correct variant.

- [ ] **Step 10: Fix PlaybackProgressIndicator.kt**

File: `app/src/main/java/com/rpeters/jellyfin/ui/components/PlaybackProgressIndicator.kt`

- Line 73: Same — read line, apply correct variant.

- [ ] **Step 11: Verify no bare indicators remain in target files**

```bash
grep -n "CircularProgressIndicator\|LinearProgressIndicator" \
  app/src/main/java/com/rpeters/jellyfin/ui/downloads/DownloadsScreen.kt \
  app/src/main/java/com/rpeters/jellyfin/ui/components/DownloadButton.kt \
  app/src/main/java/com/rpeters/jellyfin/ui/player/MiniPlayer.kt \
  app/src/main/java/com/rpeters/jellyfin/ui/screens/PersonDetailScreen.kt \
  app/src/main/java/com/rpeters/jellyfin/ui/player/VideoPlayerDialogs.kt \
  app/src/main/java/com/rpeters/jellyfin/ui/components/AiSummaryCard.kt \
  app/src/main/java/com/rpeters/jellyfin/ui/screens/details/components/WhyYoullLoveThisCard.kt \
  app/src/main/java/com/rpeters/jellyfin/ui/components/ConnectionProgress.kt \
  app/src/main/java/com/rpeters/jellyfin/ui/components/AccessibleLoadingStates.kt \
  app/src/main/java/com/rpeters/jellyfin/ui/components/PlaybackProgressIndicator.kt
```

Expected: Zero results (import lines should be removed too).

- [ ] **Step 12: Full build + unit tests**

```bash
./gradlew assembleDebug && ./gradlew testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 13: Commit**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/
git commit -m "refactor(ui): replace bare progress indicators with ExpressiveWavy wrappers"
```

---

## Final Verification Checklist

Run after all tasks complete:

- [ ] `./gradlew assembleDebug` — clean build
- [ ] `./gradlew testDebugUnitTest` — all tests pass
- [ ] `grep -n "motionScheme" app/src/main/java/com/rpeters/jellyfin/ui/theme/Theme.kt` — shows `motionScheme = MotionTokens.expressiveMotionScheme`
- [ ] `grep "full" app/src/main/java/com/rpeters/jellyfin/ui/theme/Shape.kt` — shows `full = ShapeTokens.Full` in the `Shapes(...)` constructor
- [ ] `grep -rn "RoundedCornerShape([0-9]" app/src/main/java/com/rpeters/jellyfin/ui/ --include="*.kt" | grep -v "/tv/"` — zero results
- [ ] `grep "useVersion" app/build.gradle.kts` — shows `useVersion("1.5.0-alpha15")`
- [ ] `grep -n "CircularProgressIndicator\|LinearProgressIndicator" app/src/main/java/com/rpeters/jellyfin/ui/downloads/DownloadsScreen.kt app/src/main/java/com/rpeters/jellyfin/ui/components/DownloadButton.kt app/src/main/java/com/rpeters/jellyfin/ui/player/MiniPlayer.kt app/src/main/java/com/rpeters/jellyfin/ui/screens/PersonDetailScreen.kt app/src/main/java/com/rpeters/jellyfin/ui/player/VideoPlayerDialogs.kt app/src/main/java/com/rpeters/jellyfin/ui/components/AiSummaryCard.kt app/src/main/java/com/rpeters/jellyfin/ui/screens/details/components/WhyYoullLoveThisCard.kt app/src/main/java/com/rpeters/jellyfin/ui/components/ConnectionProgress.kt app/src/main/java/com/rpeters/jellyfin/ui/components/AccessibleLoadingStates.kt app/src/main/java/com/rpeters/jellyfin/ui/components/PlaybackProgressIndicator.kt` — zero results
