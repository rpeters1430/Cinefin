# Immersive Layer Expressive Shapes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the immersive (Netflix-style) UI layer's shape language in line with the Material 3 Expressive corner scale, and add morphing polygon shapes as a deliberate accent on the primary FAB and the unwatched-episode-count badge.

**Architecture:** Add a small `ImmersiveShapes`/corner-token addition to the existing theme files (`ui/theme/Shape.kt`, `ui/theme/Dimensions.kt`), then update the handful of call sites in `ui/components/immersive/ImmersiveMediaCard.kt` and `ui/components/immersive/FloatingActionGroup.kt` that currently hardcode flat `RoundedCornerShape` values. Morphing shapes use material3 1.5.0-alpha23's `MaterialShapes` (`androidx.graphics.shapes.RoundedPolygon` presets) and `Morph`, confirmed present in the resolved dependency (verified against the library's sources jar).

**Tech Stack:** Jetpack Compose, Material 3 1.5.0-alpha23 (`MaterialShapes`, `Morph`, `ExperimentalMaterial3ExpressiveApi`), Kotlin 2.3.20.

## Global Constraints

- Scope is limited to `ui/components/immersive/*` and the immersive-specific shape/dimension tokens — do not touch `ui/screens/Immersive*.kt`, TV screens, or the standalone `Expressive*` components (per approved spec `docs/superpowers/specs/2026-07-10-immersive-expressive-shapes-design.md`).
- Corner scale values must match Material 3's own `ShapeDefaults`/`ShapeTokens` dp values exactly: Medium=12dp, Large=16dp, LargeIncreased=20dp, ExtraLarge=28dp (verified against `androidx.compose.material3.tokens.ShapeTokens` in the resolved 1.5.0-alpha23 sources).
- Morphing shapes are used in exactly two places: the `FloatingActionGroup` primary FAB (press-driven morph) and the unwatched-episode-count badge in `ImmersiveMediaCard` (static polygon swap, no morph animation). No other element gets a polygon shape.
- New experimental-API usage (`MaterialShapes`, `Morph`, `toShape`, `toPath`) must be opted in via the existing `@file:OptInAppExperimentalApis` annotation (see `app/src/main/java/com/rpeters/jellyfin/ExperimentalOptIns.kt`), consistent with how `ui/theme/Theme.kt` already does it.
- No new unit tests — this is a visual/UI-only change. Each task's "test cycle" is a Kotlin compile check (`compileDebugKotlin`); the final task is a manual visual verification pass (build + run).

---

### Task 1: Add Expressive corner tokens to the theme

**Files:**
- Modify: `app/src/main/java/com/rpeters/jellyfin/ui/theme/Dimensions.kt`
- Modify: `app/src/main/java/com/rpeters/jellyfin/ui/theme/Shape.kt`

**Interfaces:**
- Produces: `ImmersiveDimens.CardCornerRadius: Dp` (20dp), `ImmersiveDimens.RatingBadgeCornerRadius: Dp` (12dp) — new constants, added alongside (not replacing) the existing `CornerRadiusCinematic`/`CornerRadiusCard`/`CornerRadiusSmall` constants, which stay untouched because other out-of-scope screens (`ImmersiveHomeVideoDetailScreen.kt`, `ImmersiveAlbumDetailScreen.kt`) still reference them.
- Produces: `ImmersiveShapes.Card: CornerBasedShape`, `ImmersiveShapes.RatingBadge: CornerBasedShape` in `ui/theme/Shape.kt` — consumed by Task 2.

- [ ] **Step 1: Add the new Dp constants to `ImmersiveDimens`**

In `app/src/main/java/com/rpeters/jellyfin/ui/theme/Dimensions.kt`, add two new constants inside the existing `ImmersiveDimens` object, right after the existing `// Border radius` block (after `val CornerRadiusSmall = 4.dp`):

```kotlin
    // Border radius
    val CornerRadiusCinematic = 12.dp
    val CornerRadiusCard = 8.dp
    val CornerRadiusSmall = 4.dp

    // Material 3 Expressive corner scale (matches ShapeTokens.CornerLargeIncreased / CornerMedium)
    val CardCornerRadius = 20.dp
    val RatingBadgeCornerRadius = 12.dp
```

- [ ] **Step 2: Add `ImmersiveShapes` to `Shape.kt`**

In `app/src/main/java/com/rpeters/jellyfin/ui/theme/Shape.kt`, add a new object after the existing `JellyfinShapes` val at the bottom of the file:

```kotlin

/**
 * Expressive corner scale for the immersive (Netflix-style) UI layer.
 * Values match Material 3's own ShapeDefaults.large/largeIncreased/medium dp values.
 */
object ImmersiveShapes {
    val Card: CornerBasedShape = RoundedCornerShape(ImmersiveDimens.CardCornerRadius)
    val RatingBadge: CornerBasedShape = RoundedCornerShape(ImmersiveDimens.RatingBadgeCornerRadius)
}
```

- [ ] **Step 3: Compile check**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (no references to the new symbols yet, so this just confirms the additions themselves compile).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/theme/Dimensions.kt app/src/main/java/com/rpeters/jellyfin/ui/theme/Shape.kt
git commit -m "feat: add Expressive corner scale tokens for immersive layer"
```

---

### Task 2: Apply the new corner scale to `ImmersiveMediaCard`

**Files:**
- Modify: `app/src/main/java/com/rpeters/jellyfin/ui/components/immersive/ImmersiveMediaCard.kt`

**Interfaces:**
- Consumes: `ImmersiveShapes.Card`, `ImmersiveShapes.RatingBadge` (from Task 1), `ImmersiveDimens.CardCornerRadius: Dp` (from Task 1).
- Produces: no new public symbols — internal shape usage only.

- [ ] **Step 1: Replace the card shape**

In `ImmersiveMediaCard.kt`, replace line 105:

```kotlin
    val cardShape = RoundedCornerShape(ImmersiveDimens.CornerRadiusCinematic)
```

with:

```kotlin
    val cardShape = ImmersiveShapes.Card
```

- [ ] **Step 2: Replace the glow border radius**

Replace line 130 (inside the `.expressiveGlow(...)` call):

```kotlin
                borderRadius = ImmersiveDimens.CornerRadiusCinematic,
```

with:

```kotlin
                borderRadius = ImmersiveDimens.CardCornerRadius,
```

- [ ] **Step 3: Replace the image clip shape**

Replace line 210 (inside `ImmersiveCardContent`'s `OptimizedImage` modifier):

```kotlin
                .clip(RoundedCornerShape(ImmersiveDimens.CornerRadiusCinematic)),
```

with:

```kotlin
                .clip(ImmersiveShapes.Card),
```

- [ ] **Step 4: Replace the rating badge shape**

Replace line 242 (the rating `Surface`):

```kotlin
                Surface(
                    shape = RoundedCornerShape(ImmersiveDimens.CornerRadiusCard),
                    color = Color.Black.copy(alpha = 0.7f),
                ) {
```

with:

```kotlin
                Surface(
                    shape = ImmersiveShapes.RatingBadge,
                    color = Color.Black.copy(alpha = 0.7f),
                ) {
```

- [ ] **Step 5: Compile check**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/components/immersive/ImmersiveMediaCard.kt
git commit -m "feat: use Expressive corner scale for immersive media card shapes"
```

---

### Task 3: Morph-shape unwatched-episode-count badge

**Files:**
- Modify: `app/src/main/java/com/rpeters/jellyfin/ui/components/immersive/ImmersiveMediaCard.kt`

**Interfaces:**
- Consumes: `androidx.compose.material3.MaterialShapes.Cookie4Sided` (RoundedPolygon), `androidx.compose.material3.toShape()` extension (both from material3 1.5.0-alpha23).
- Produces: no new public symbols.

- [ ] **Step 1: Add the file-level experimental opt-in**

At the top of `ImmersiveMediaCard.kt`, above the `package` line, add:

```kotlin
@file:OptInAppExperimentalApis

```

(This mirrors the pattern already used in `ui/theme/Theme.kt`.)

- [ ] **Step 2: Add the new imports**

Add these imports alongside the existing ones in `ImmersiveMediaCard.kt`:

```kotlin
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import com.rpeters.jellyfin.OptInAppExperimentalApis
```

- [ ] **Step 3: Replace the unwatched-count badge shape**

Replace the unwatched-count badge block (originally lines 269–286):

```kotlin
            if (unwatchedEpisodeCount != null && unwatchedEpisodeCount > 0) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    val countText = if (unwatchedEpisodeCount > 99) "99+" else unwatchedEpisodeCount.toString()
                    Box(
                        modifier = Modifier.defaultMinSize(minWidth = 32.dp, minHeight = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp),
                        )
                    }
                }
            } else if (isWatched) {
```

with:

```kotlin
            if (unwatchedEpisodeCount != null && unwatchedEpisodeCount > 0) {
                Surface(
                    shape = MaterialShapes.Cookie4Sided.toShape(),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    val countText = if (unwatchedEpisodeCount > 99) "99+" else unwatchedEpisodeCount.toString()
                    Box(
                        modifier = Modifier.defaultMinSize(minWidth = 32.dp, minHeight = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp),
                        )
                    }
                }
            } else if (isWatched) {
```

- [ ] **Step 4: Compile check**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/components/immersive/ImmersiveMediaCard.kt
git commit -m "feat: use Cookie4Sided polygon shape for unwatched-episode badge"
```

---

### Task 4: Shape-morph the primary FAB in `FloatingActionGroup`

**Files:**
- Modify: `app/src/main/java/com/rpeters/jellyfin/ui/components/immersive/FloatingActionGroup.kt`

**Interfaces:**
- Consumes: `androidx.compose.material3.MaterialShapes.Circle`, `MaterialShapes.Cookie9Sided` (RoundedPolygon), `androidx.compose.material3.toPath(progress: Float): Path` extension on `Morph`, `androidx.graphics.shapes.Morph`, `MaterialTheme.motionScheme.defaultSpatialSpec<Float>()` (ambient expressive motion scheme already wired in `ui/theme/Theme.kt`).
- Produces: private `MorphingPrimaryFab(action: FabAction)` composable, used by both the `Vertical` and `Horizontal` branches of `FloatingActionGroup`.

- [ ] **Step 1: Add the file-level experimental opt-in and new imports**

At the top of `FloatingActionGroup.kt`, above the `package` line, add:

```kotlin
@file:OptInAppExperimentalApis

```

Add these imports alongside the existing ones:

```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.center
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import com.rpeters.jellyfin.OptInAppExperimentalApis
```

- [ ] **Step 2: Add the `MorphingPrimaryFab` composable**

Add this private composable at the bottom of `FloatingActionGroup.kt`, above the `data class FabAction` declaration:

```kotlin
/**
 * Primary FAB that morphs from a circle to a soft polygon while pressed.
 * [progress] is read as a State inside createOutline (not destructured with `by`) so the
 * shape animates on every draw frame without triggering full recomposition.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MorphingPrimaryFab(action: FabAction) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress = animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "fab_morph_progress",
    )
    val morphShape = remember(morph) {
        object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density,
            ): Outline {
                val path: Path = morph.toPath(progress = progress.value)
                val scaleMatrix = Matrix().apply { scale(x = size.width, y = size.height) }
                path.transform(scaleMatrix)
                path.translate(size.center - path.getBounds().center)
                return Outline.Generic(path)
            }
        }
    }

    FloatingActionButton(
        onClick = action.onClick,
        shape = morphShape,
        interactionSource = interactionSource,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = Dimens.Spacing8,
        ),
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.contentDescription,
        )
    }
}
```

- [ ] **Step 3: Use `MorphingPrimaryFab` in the `Vertical` branch**

Replace the primary-action block inside `FabOrientation.Vertical` (originally lines 60–74):

```kotlin
                    primaryAction?.let { action ->
                        FloatingActionButton(
                            onClick = action.onClick,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            elevation = FloatingActionButtonDefaults.elevation(
                                defaultElevation = Dimens.Spacing8,
                            ),
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.contentDescription,
                            )
                        }
                    }
```

with:

```kotlin
                    primaryAction?.let { action ->
                        MorphingPrimaryFab(action = action)
                    }
```

- [ ] **Step 4: Use `MorphingPrimaryFab` in the `Horizontal` branch**

Replace the primary-action block inside `FabOrientation.Horizontal` (originally lines 84–95):

```kotlin
                    primaryAction?.let { action ->
                        FloatingActionButton(
                            onClick = action.onClick,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.contentDescription,
                            )
                        }
                    }
```

with:

```kotlin
                    primaryAction?.let { action ->
                        MorphingPrimaryFab(action = action)
                    }
```

- [ ] **Step 5: Compile check**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/components/immersive/FloatingActionGroup.kt
git commit -m "feat: morph primary FAB shape on press using Material 3 Expressive polygons"
```

---

### Task 5: Manual visual verification

**Files:** None (verification only).

- [ ] **Step 1: Build the debug APK**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Install and launch on a connected device/emulator**

Run: `./gradlew.bat installDebug`
Then launch the app manually (or `adb shell am start -n com.rpeters.jellyfin/.MainActivity`).

- [ ] **Step 3: Verify card shapes**

Navigate to the Home screen (immersive layer). Confirm:
- Media cards in Continue Watching / Recently Added rows show visibly softer, larger corner rounding (20dp) than before (previously 12dp).
- The rating badge (gold star + number, top-left of a card with a rating) has slightly more rounded corners (12dp) than before (previously 8dp).
- Cards with an unwatched-episode count badge (top-right) show a scalloped "cookie" polygon shape instead of a plain rounded rectangle.

- [ ] **Step 4: Verify FAB morph**

Navigate to a screen using `FloatingActionGroup` with a primary action (e.g. a movie/show detail screen's play button, if wired to this component — confirm via `Grep` for `FloatingActionGroup(` call sites if unsure which screen to check). Press and hold the primary FAB. Confirm:
- The FAB smoothly morphs from a circle to a soft 9-sided cookie/sunny polygon while pressed.
- Releasing the press smoothly morphs it back to a circle.
- Secondary FABs (if any) remain plain circles throughout — unaffected.

- [ ] **Step 5: Verify light and dark theme**

Toggle the device/app theme between light and dark (Settings → Appearance, or system theme toggle). Confirm all of the above renders correctly in both themes — shapes are theme-independent so this mainly confirms no crashes or color regressions from the shape changes.

- [ ] **Step 6: Verify on a second form factor**

If a tablet emulator/device is available, repeat steps 3–4 there. If not, resize a phone emulator window (or use a larger AVD profile) to confirm the shapes scale proportionally with `ImmersiveDimens`' existing phone/tablet card-size branching — no hardcoded shape assumptions broke on a different card size.

- [ ] **Step 7: Report findings**

If everything in steps 3–6 matches expectations, the feature is complete — no further commit needed (this task is verification-only). If something looks wrong, note exactly what and which step, and fix it as a follow-up before considering the plan done.
