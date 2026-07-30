# Detail Screen Rating & Codec Badges Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the duplicated, plain-text rating UI and flat codec chip on the movie/TV show/season/episode detail screens with shared, colored, icon-forward badge components — matching the visual polish already present in the resolution/HDR/Atmos badges.

**Architecture:** Two new/extended shared composable files (`ui/components/RatingBadges.kt` for community/critic/official rating pills, and an extension to `ui/components/immersive/MediaInfoBadges.kt` for a colored codec badge) get wired into the four existing detail-screen files, replacing hand-rolled inline blocks. Tier-color logic is pulled out into small pure functions so it can be unit tested without needing Compose UI test infrastructure.

**Tech Stack:** Jetpack Compose (Material 3), Kotlin, JUnit4 (existing pure-function test convention in `ui/theme`).

## Global Constraints

- Follow existing code style: 4-space indent, trailing commas in multi-line calls (matches surrounding code in every file touched).
- Do not introduce new color hex constants — reuse `OfficialRatingGreen` / `OfficialRatingAmber` / `OfficialRatingRed` from `ui/theme/Color.kt:72-75` for all rating tiers.
- Do not add new drawable assets — reuse the existing `R.drawable.avc_24px` for the AVC codec icon; all other codec icons must be Material icons already used elsewhere in this codebase (verified via grep, listed per-task below) to avoid referencing icon names that don't exist in this project's Material Icons Extended version.
- `criticRating: Float?` and `communityRating: Float?` are confirmed fields on `org.jellyfin.sdk.model.api.BaseItemDto` (verified against the `jellyfin-model-jvm-1.8.12.jar` class file).
- No new tests for pure-visual Compose composables (matches existing convention — `MediaInfoBadges.kt` and `DetailMetadataTag.kt` have no UI tests today). Do add JUnit tests for the new pure (non-Composable) logic functions, matching the existing `ui/theme/ThemeTest.kt` convention.

---

### Task 1: Rating tier color functions

**Files:**
- Modify: `app/src/main/java/com/rpeters/jellyfin/ui/theme/Color.kt:301` (insert after `getOfficialRatingColor`, before the `HERO IMAGE GRADIENTS` section header)
- Create: `app/src/test/java/com/rpeters/jellyfin/ui/theme/RatingColorTest.kt`

**Interfaces:**
- Produces: `fun getCommunityRatingColor(rating: Float): Color` — green ≥7.0, amber 5.0–6.9, red <5.0.
- Produces: `fun getCriticRatingColor(rating: Float): Color` — green ≥60f, red <60f.
- Both used by Task 2's `RatingBadges.kt`.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/rpeters/jellyfin/ui/theme/RatingColorTest.kt`:

```kotlin
package com.rpeters.jellyfin.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class RatingColorTest {

    @Test
    fun `getCommunityRatingColor returns green at and above 7`() {
        assertEquals(OfficialRatingGreen, getCommunityRatingColor(7.0f))
        assertEquals(OfficialRatingGreen, getCommunityRatingColor(9.5f))
    }

    @Test
    fun `getCommunityRatingColor returns amber between 5 and 6point9`() {
        assertEquals(OfficialRatingAmber, getCommunityRatingColor(5.0f))
        assertEquals(OfficialRatingAmber, getCommunityRatingColor(6.9f))
    }

    @Test
    fun `getCommunityRatingColor returns red below 5`() {
        assertEquals(OfficialRatingRed, getCommunityRatingColor(4.9f))
        assertEquals(OfficialRatingRed, getCommunityRatingColor(0.0f))
    }

    @Test
    fun `getCriticRatingColor returns green at and above 60`() {
        assertEquals(OfficialRatingGreen, getCriticRatingColor(60f))
        assertEquals(OfficialRatingGreen, getCriticRatingColor(100f))
    }

    @Test
    fun `getCriticRatingColor returns red below 60`() {
        assertEquals(OfficialRatingRed, getCriticRatingColor(59.9f))
        assertEquals(OfficialRatingRed, getCriticRatingColor(0f))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat testDebugUnitTest --tests "com.rpeters.jellyfin.ui.theme.RatingColorTest"`
Expected: FAIL — `getCommunityRatingColor`/`getCriticRatingColor` unresolved reference.

- [ ] **Step 3: Implement the functions**

In `app/src/main/java/com/rpeters/jellyfin/ui/theme/Color.kt`, insert immediately after the closing `}` of `getOfficialRatingColor` (currently ends at line 301, right before the `// ====... HERO IMAGE GRADIENTS` comment block that starts at line 303):

```kotlin

/**
 * Tier color for a 0-10 scale community rating (e.g. TMDb/IMDb-style).
 */
fun getCommunityRatingColor(rating: Float): Color {
    return when {
        rating >= 7.0f -> OfficialRatingGreen
        rating >= 5.0f -> OfficialRatingAmber
        else -> OfficialRatingRed
    }
}

/**
 * Tier color for a 0-100 scale critic rating (e.g. Rotten Tomatoes-style).
 */
fun getCriticRatingColor(rating: Float): Color {
    return if (rating >= 60f) OfficialRatingGreen else OfficialRatingRed
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat testDebugUnitTest --tests "com.rpeters.jellyfin.ui.theme.RatingColorTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/theme/Color.kt app/src/test/java/com/rpeters/jellyfin/ui/theme/RatingColorTest.kt
git commit -m "feat: add tier-color functions for community/critic ratings"
```

---

### Task 2: Shared rating badge composables

**Files:**
- Create: `app/src/main/java/com/rpeters/jellyfin/ui/components/RatingBadges.kt`

**Interfaces:**
- Consumes: `getCommunityRatingColor(Float): Color`, `getCriticRatingColor(Float): Color` (Task 1), `getOfficialRatingColor(String): Color` (existing, `ui/theme/Color.kt:293`).
- Produces:
  - `@Composable fun CommunityRatingBadge(rating: Float, modifier: Modifier = Modifier)`
  - `@Composable fun CriticRatingBadge(rating: Float, modifier: Modifier = Modifier)`
  - `@Composable fun OfficialRatingBadge(rating: String, modifier: Modifier = Modifier)`
  - `@Composable fun RatingRow(communityRating: Float?, criticRating: Float? = null, modifier: Modifier = Modifier)` — renders `CommunityRatingBadge`/`CriticRatingBadge` side by side for whichever is non-null; renders nothing if both are null.
  - These four are consumed by Tasks 4–7.

This task has no automated test (pure Compose UI, no branching logic beyond what Task 1 already covers and what visual inspection in Task 8 verifies) — matches the existing no-test convention for `MediaInfoBadges.kt`.

- [ ] **Step 1: Create the file**

Create `app/src/main/java/com/rpeters/jellyfin/ui/components/RatingBadges.kt`:

```kotlin
package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.ui.theme.getCommunityRatingColor
import com.rpeters.jellyfin.ui.theme.getCriticRatingColor
import com.rpeters.jellyfin.ui.theme.getOfficialRatingColor
import java.util.Locale

/**
 * Community rating (e.g. TMDb/IMDb-style, 0-10 scale) as a colored gradient pill.
 * Gradient color reflects score tier via [getCommunityRatingColor].
 */
@Composable
fun CommunityRatingBadge(
    rating: Float,
    modifier: Modifier = Modifier,
) {
    val tint = getCommunityRatingColor(rating)
    Box(
        modifier = modifier
            .background(
                brush = Brush.horizontalGradient(colors = listOf(tint.copy(alpha = 0.85f), tint)),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = String.format(Locale.US, "%.1f", rating),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
        }
    }
}

/**
 * Critic rating (e.g. Rotten Tomatoes-style, 0-100 scale) as a colored gradient pill.
 * Only meaningful when the source data is non-null — callers should guard with `?.let`.
 */
@Composable
fun CriticRatingBadge(
    rating: Float,
    modifier: Modifier = Modifier,
) {
    val tint = getCriticRatingColor(rating)
    Box(
        modifier = modifier
            .background(
                brush = Brush.horizontalGradient(colors = listOf(tint.copy(alpha = 0.85f), tint)),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = "🍅")
            Text(
                text = "${rating.toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
        }
    }
}

/**
 * Official (age/content) rating, e.g. "PG-13", as an outlined pill colored per [getOfficialRatingColor].
 */
@Composable
fun OfficialRatingBadge(
    rating: String,
    modifier: Modifier = Modifier,
) {
    val tintColor = getOfficialRatingColor(rating)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = tintColor.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, tintColor.copy(alpha = 0.6f)),
    ) {
        Text(
            text = rating,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = tintColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/**
 * Lays out [CommunityRatingBadge] and [CriticRatingBadge] side by side for whichever
 * rating values are non-null. Renders nothing if both are null.
 */
@Composable
fun RatingRow(
    communityRating: Float?,
    criticRating: Float? = null,
    modifier: Modifier = Modifier,
) {
    if (communityRating == null && criticRating == null) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        communityRating?.let { CommunityRatingBadge(rating = it) }
        criticRating?.let { CriticRatingBadge(rating = it) }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/components/RatingBadges.kt
git commit -m "feat: add shared rating badge composables"
```

---

### Task 3: Colored codec badge

**Files:**
- Modify: `app/src/main/java/com/rpeters/jellyfin/ui/components/immersive/MediaInfoBadges.kt`
- Create: `app/src/test/java/com/rpeters/jellyfin/ui/components/immersive/MediaInfoBadgesTest.kt`

**Interfaces:**
- Consumes: `R.drawable.avc_24px` (existing, `app/src/main/res/drawable/avc_24px.xml`).
- Produces:
  - `enum class VideoCodecType { HEVC, AVC, AV1, VP9, OTHER }` with `companion object { fun fromCodecName(codec: String): VideoCodecType }`.
  - `@Composable fun CodecTypeBadge(codecText: String, modifier: Modifier = Modifier)`.
- `VideoInfoCard` (existing, same file) is modified to call `CodecTypeBadge(codecText = codec)` instead of `CodecBadge(text = codec, icon = codecIcon)`, and its `codecIcon: ImageVector? = null` parameter is removed — Tasks 5 and 7 must drop the `codecIcon = codecIcon` argument (and its now-dead local `val codecIcon = ...` computation) from their `VideoInfoCard(...)` calls.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/rpeters/jellyfin/ui/components/immersive/MediaInfoBadgesTest.kt`:

```kotlin
package com.rpeters.jellyfin.ui.components.immersive

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaInfoBadgesTest {

    @Test
    fun `fromCodecName maps known codec names case-insensitively`() {
        assertEquals(VideoCodecType.HEVC, VideoCodecType.fromCodecName("HEVC"))
        assertEquals(VideoCodecType.HEVC, VideoCodecType.fromCodecName("hevc"))
        assertEquals(VideoCodecType.AVC, VideoCodecType.fromCodecName("AVC"))
        assertEquals(VideoCodecType.AV1, VideoCodecType.fromCodecName("AV1"))
        assertEquals(VideoCodecType.VP9, VideoCodecType.fromCodecName("VP9"))
    }

    @Test
    fun `fromCodecName falls back to OTHER for unknown codecs`() {
        assertEquals(VideoCodecType.OTHER, VideoCodecType.fromCodecName("MPEG2"))
        assertEquals(VideoCodecType.OTHER, VideoCodecType.fromCodecName(""))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.rpeters.jellyfin.ui.components.immersive.MediaInfoBadgesTest"`
Expected: FAIL — `VideoCodecType` unresolved reference.

- [ ] **Step 3: Add imports**

In `app/src/main/java/com/rpeters/jellyfin/ui/components/immersive/MediaInfoBadges.kt`, add these imports alongside the existing icon imports (after line 24, `import androidx.compose.material.icons.outlined.VideoFile`):

```kotlin
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoLibrary
```

And add, alongside the existing `import androidx.compose.ui.res.vectorResource` — this import does not yet exist in this file, so add it after line 45 (`import androidx.compose.ui.graphics.vector.ImageVector`):

```kotlin
import androidx.compose.ui.res.vectorResource
```

- [ ] **Step 4: Add `VideoCodecType` enum and `CodecTypeBadge` composable**

In the same file, insert after the closing `}` of `CodecBadge` (currently ends at line 245, right before the `MediaInfoCard` doc comment that starts at line 247):

```kotlin

/**
 * Video codec categories with a distinct accent color + icon each.
 */
enum class VideoCodecType(
    val accentColor: Color,
    val materialIcon: ImageVector?,
    val drawableRes: Int?,
) {
    HEVC(Color(0xFF43E97B), Icons.Default.Speed, null),
    AVC(Color(0xFF4FACFE), null, R.drawable.avc_24px),
    AV1(Color(0xFF9B59B6), Icons.Default.Memory, null),
    VP9(Color(0xFF00BFA5), Icons.Default.VideoLibrary, null),
    OTHER(Color(0xFF9E9E9E), Icons.Outlined.VideoFile, null),
    ;

    companion object {
        fun fromCodecName(codec: String): VideoCodecType = when (codec.uppercase(java.util.Locale.ROOT)) {
            "HEVC" -> HEVC
            "AVC" -> AVC
            "AV1" -> AV1
            "VP9" -> VP9
            else -> OTHER
        }
    }
}

/**
 * Codec badge colored + iconed per [VideoCodecType]. [codecText] is shown verbatim
 * (already normalized to e.g. "HEVC"/"AVC"/"AV1"/"VP9" by callers).
 */
@Composable
fun CodecTypeBadge(
    codecText: String,
    modifier: Modifier = Modifier,
) {
    val type = VideoCodecType.fromCodecName(codecText)
    val icon = type.drawableRes?.let { ImageVector.vectorResource(id = it) } ?: type.materialIcon
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = type.accentColor.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, type.accentColor.copy(alpha = 0.5f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = type.accentColor,
                )
            }
            Text(
                text = codecText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = type.accentColor,
                fontSize = 12.sp,
                letterSpacing = 0.3.sp,
            )
        }
    }
}
```

- [ ] **Step 5: Wire `CodecTypeBadge` into `VideoInfoCard` and drop `codecIcon`**

In the same file, in `VideoInfoCard` (currently lines 343–393):

Change the signature (currently lines 345–355):

```kotlin
fun VideoInfoCard(
    resolution: ResolutionQuality,
    codec: String,
    bitDepth: Int? = null,
    frameRate: Double? = null,
    isHdr: Boolean = false,
    hdrType: HdrType = HdrType.HDR,
    is3D: Boolean = false,
    codecIcon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
```

to:

```kotlin
fun VideoInfoCard(
    resolution: ResolutionQuality,
    codec: String,
    bitDepth: Int? = null,
    frameRate: Double? = null,
    isHdr: Boolean = false,
    hdrType: HdrType = HdrType.HDR,
    is3D: Boolean = false,
    modifier: Modifier = Modifier,
) {
```

And change the codec chip line (currently line 373):

```kotlin
            CodecBadge(text = codec, icon = codecIcon)
```

to:

```kotlin
            CodecTypeBadge(codecText = codec)
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.rpeters.jellyfin.ui.components.immersive.MediaInfoBadgesTest"`
Expected: PASS (2 tests)

- [ ] **Step 7: Verify it compiles**

Run: `./gradlew.bat compileDebugKotlin`
Expected: FAILS at this point — `ImmersiveTVShowDetailScreen.kt` and `ImmersiveTVEpisodeDetailScreen.kt` still pass the now-removed `codecIcon` argument to `VideoInfoCard`. This is expected; Tasks 5 and 7 fix these call sites. Confirm the only compile errors are `no value passed for parameter` / `codecIcon` in those two files, then proceed.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/components/immersive/MediaInfoBadges.kt app/src/test/java/com/rpeters/jellyfin/ui/components/immersive/MediaInfoBadgesTest.kt
git commit -m "feat: add colored per-codec video badge"
```

---

### Task 4: Wire rating badges into the movie detail screen

**Files:**
- Modify: `app/src/main/java/com/rpeters/jellyfin/ui/screens/details/components/MovieHeroContent.kt`

**Interfaces:**
- Consumes: `CommunityRatingBadge`, `CriticRatingBadge`, `OfficialRatingBadge` (Task 2, package `com.rpeters.jellyfin.ui.components`).

- [ ] **Step 1: Replace the official rating block**

In `MovieHeroContent.kt`, replace lines 113–128:

```kotlin
            // Official Rating
            movie.officialRating?.let { rating ->
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = getOfficialRatingColor(rating).copy(alpha = 0.2f),
                    modifier = Modifier,
                ) {
                    Text(
                        text = rating,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = getOfficialRatingColor(rating),
                    )
                }
            }
```

with:

```kotlin
            // Official Rating
            movie.officialRating?.let { rating ->
                OfficialRatingBadge(rating = rating)
            }
```

- [ ] **Step 2: Replace the community rating block**

Replace lines 131–171 (the `// 3. Critics Rating and Community (if available)` block):

```kotlin
        // 3. Critics Rating and Community (if available)
        movie.communityRating?.let { rating ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFFFF8C00), Color(0xFFFFD700)),
                            ),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f", rating),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                        )
                        Text(
                            text = "/ 10",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
```

with:

```kotlin
        // 3. Critics Rating and Community (if available)
        RatingRow(
            communityRating = movie.communityRating,
            criticRating = movie.criticRating,
        )
```

- [ ] **Step 3: Update imports**

At the top of `MovieHeroContent.kt`:

Remove (now unused — both were only used in the two blocks just replaced):
```kotlin
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Brush
import com.rpeters.jellyfin.ui.theme.getOfficialRatingColor
```

Add:
```kotlin
import com.rpeters.jellyfin.ui.components.CommunityRatingBadge
import com.rpeters.jellyfin.ui.components.CriticRatingBadge
import com.rpeters.jellyfin.ui.components.OfficialRatingBadge
import com.rpeters.jellyfin.ui.components.RatingRow
```

(`CommunityRatingBadge`/`CriticRatingBadge` aren't referenced directly in this file — `RatingRow` uses them internally — but importing all four keeps this file's rating-badge imports self-documenting and matches how `VideoInfoCard`/`AudioInfoCard` are imported as a pair in the other three screens. If your linter flags unused imports, keep only `OfficialRatingBadge` and `RatingRow`.)

Also remove `Box` from `androidx.compose.foundation.layout.Box` if no longer used elsewhere in the file — check with:

Run: `grep -n "Box(" app/src/main/java/com/rpeters/jellyfin/ui/screens/details/components/MovieHeroContent.kt`
Expected: no matches (the only `Box(` was in the block just deleted) — if so, remove the `import androidx.compose.foundation.layout.Box` line too.

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/screens/details/components/MovieHeroContent.kt
git commit -m "feat: use shared rating badges on movie detail screen"
```

---

### Task 5: Wire rating badges + codec badge into the TV show detail screen

**Files:**
- Modify: `app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveTVShowDetailScreen.kt`

**Interfaces:**
- Consumes: `CommunityRatingBadge`, `CriticRatingBadge`, `OfficialRatingBadge`, `RatingRow` (Task 2); `VideoInfoCard` without `codecIcon` (Task 3).

- [ ] **Step 1: Replace the private `RatingBadge` composable's call site**

Replace line 538:

```kotlin
                series.communityRating?.let { rating ->
                    RatingBadge(rating)
                }
```

with:

```kotlin
                RatingRow(
                    communityRating = series.communityRating,
                    criticRating = series.criticRating,
                )
```

- [ ] **Step 2: Delete the now-unused private `RatingBadge` composable**

Delete lines 1079–1117 (the entire `private fun RatingBadge(rating: Float, source: String? = null) { ... }` function, immediately preceded by its `@Composable` annotation).

- [ ] **Step 3: Replace the official rating block**

Replace lines 665–681:

```kotlin
            series.officialRating?.let { rating ->
                val normalized = normalizeOfficialRating(rating) ?: return@let
                val tintColor = getOfficialRatingColor(normalized)
                Surface(
                    shape = RoundedCornerShape(Dimens.Corner6),
                    color = tintColor.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, tintColor.copy(alpha = 0.6f)),
                ) {
                    Text(
                        text = normalized,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = Dimens.Spacing10, vertical = Dimens.Spacing6),
                    )
                }
            }
```

with:

```kotlin
            series.officialRating?.let { rating ->
                val normalized = normalizeOfficialRating(rating) ?: return@let
                OfficialRatingBadge(rating = normalized)
            }
```

- [ ] **Step 4: Remove the `codecIcon` computation and argument**

Replace lines 786–800:

```kotlin
                            val codecIcon = if (codecText == "AVC") {
                                ImageVector.vectorResource(id = R.drawable.avc_24px)
                            } else {
                                null
                            }

                            VideoInfoCard(
                                resolution = resolution,
                                codec = codecText,
                                bitDepth = stream.bitDepth,
                                frameRate = stream.averageFrameRate?.toDouble(),
                                isHdr = hdrType != null,
                                hdrType = hdrType ?: HdrType.HDR,
                                codecIcon = codecIcon,
                            )
```

with:

```kotlin
                            VideoInfoCard(
                                resolution = resolution,
                                codec = codecText,
                                bitDepth = stream.bitDepth,
                                frameRate = stream.averageFrameRate?.toDouble(),
                                isHdr = hdrType != null,
                                hdrType = hdrType ?: HdrType.HDR,
                            )
```

- [ ] **Step 5: Update imports**

Add:
```kotlin
import com.rpeters.jellyfin.ui.components.OfficialRatingBadge
import com.rpeters.jellyfin.ui.components.RatingRow
```

Remove (now unused after Steps 1–4 — verify each with grep before deleting, since this file is large):
```kotlin
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import com.rpeters.jellyfin.ui.theme.RatingGold
import com.rpeters.jellyfin.ui.theme.getOfficialRatingColor
```

Run, for each import above, e.g.:
`grep -n "vectorResource\|ImageVector\|RatingGold\|getOfficialRatingColor\|Icons.Default.Star\|Icons.filled.Star" app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveTVShowDetailScreen.kt`
Expected: only the `import` lines themselves remain (no other usages) — confirms safe to delete. If any symbol still has a non-import usage, keep that import.

- [ ] **Step 6: Verify it compiles**

Run: `./gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveTVShowDetailScreen.kt
git commit -m "feat: use shared rating/codec badges on TV show detail screen"
```

---

### Task 6: Wire rating badges into the TV season detail screen

**Files:**
- Modify: `app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveTVSeasonScreen.kt`

**Interfaces:**
- Consumes: `RatingRow`, `OfficialRatingBadge` (Task 2).

Note: this screen has no `VideoInfoCard` usage (seasons aggregate episodes and don't carry their own media stream), so Task 3's codec badge doesn't apply here.

- [ ] **Step 1: Replace the metadata row's rating + official rating blocks**

Replace lines 518–555:

```kotlin
                // Rating with star
                series.communityRating?.let { rating ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = RatingGold,
                            modifier = Modifier.size(Dimens.Size18),
                        )
                        Text(
                            text = String.format(Locale.ROOT, "%.1f", rating),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

                // Official Rating
                series.officialRating?.let { rating ->
                    val normalizedRating = normalizeOfficialRating(rating) ?: return@let
                    Surface(
                        shape = RoundedCornerShape(Dimens.Corner6),
                        color = Color.White.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                    ) {
                        Text(
                            text = normalizedRating,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = Dimens.Spacing8, vertical = Dimens.Spacing4),
                        )
                    }
                }
```

with:

```kotlin
                // Rating
                RatingRow(
                    communityRating = series.communityRating,
                    criticRating = series.criticRating,
                )

                // Official Rating
                series.officialRating?.let { rating ->
                    val normalizedRating = normalizeOfficialRating(rating) ?: return@let
                    OfficialRatingBadge(rating = normalizedRating)
                }
```

- [ ] **Step 2: Update imports**

Add:
```kotlin
import com.rpeters.jellyfin.ui.components.OfficialRatingBadge
import com.rpeters.jellyfin.ui.components.RatingRow
```

Remove (now unused — verify first, this file is large):
```kotlin
import androidx.compose.material.icons.filled.Star
import com.rpeters.jellyfin.ui.theme.RatingGold
```

Run: `grep -n "Icons.Default.Star\|RatingGold" app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveTVSeasonScreen.kt`
Expected: only the `import` lines remain — confirms safe to delete.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveTVSeasonScreen.kt
git commit -m "feat: use shared rating badges on TV season detail screen"
```

---

### Task 7: Wire rating badges + codec badge into the TV episode detail screen

**Files:**
- Modify: `app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveTVEpisodeDetailScreen.kt`

**Interfaces:**
- Consumes: `RatingRow` (Task 2); `VideoInfoCard` without `codecIcon` (Task 3).

- [ ] **Step 1: Replace the rating block**

Replace lines 601–612:

```kotlin
                // Rating
                episode.communityRating?.let { rating ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing4)) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = RatingGold, modifier = Modifier.size(Dimens.Size18))
                        Text(
                            text = String.format(Locale.ROOT, "%.1f", rating),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
```

with:

```kotlin
                // Rating
                RatingRow(
                    communityRating = episode.communityRating,
                    criticRating = episode.criticRating,
                )
```

- [ ] **Step 2: Remove the `codecIcon` computation and argument**

Replace lines 820–834:

```kotlin
                            val codecIcon = if (codecText == "AVC") {
                                ImageVector.vectorResource(id = R.drawable.avc_24px)
                            } else {
                                null
                            }

                            VideoInfoCard(
                                resolution = resolution,
                                codec = codecText,
                                bitDepth = stream.bitDepth,
                                frameRate = stream.averageFrameRate?.toDouble(),
                                isHdr = hdrType != null,
                                hdrType = hdrType ?: HdrType.HDR,
                                codecIcon = codecIcon,
                            )
```

with:

```kotlin
                            VideoInfoCard(
                                resolution = resolution,
                                codec = codecText,
                                bitDepth = stream.bitDepth,
                                frameRate = stream.averageFrameRate?.toDouble(),
                                isHdr = hdrType != null,
                                hdrType = hdrType ?: HdrType.HDR,
                            )
```

- [ ] **Step 3: Update imports**

Add:
```kotlin
import com.rpeters.jellyfin.ui.components.RatingRow
```

Remove (now unused — verify first):
```kotlin
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import com.rpeters.jellyfin.ui.theme.RatingGold
```

Run: `grep -n "Icons.Default.Star\|vectorResource\|ImageVector\|RatingGold" app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveTVEpisodeDetailScreen.kt`
Expected: only the remaining fully-qualified `androidx.compose.ui.graphics.vector.ImageVector` usages at (originally) lines 908 and 1023 (unrelated function signatures using the fully-qualified name, not the short import) — confirms the short `ImageVector` import is safe to delete, and those two lines need no changes since they don't rely on the import.

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveTVEpisodeDetailScreen.kt
git commit -m "feat: use shared rating/codec badges on TV episode detail screen"
```

---

### Task 8: Full verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full unit test suite**

Run: `./gradlew.bat testDebugUnitTest`
Expected: BUILD SUCCESSFUL, including the `RatingColorTest` and `MediaInfoBadgesTest` suites from Tasks 1 and 3.

- [ ] **Step 2: Build the debug APK**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Lint check**

Run: `./gradlew.bat lintDebug`
Expected: BUILD SUCCESSFUL (no new errors introduced; pre-existing warnings elsewhere in the codebase are out of scope)

- [ ] **Step 4: Manual smoke test**

Install on a connected device/emulator (`./gradlew.bat installDebug`) and, against a real Jellyfin server:
1. Open a movie with `communityRating` set and `officialRating` set — confirm the star badge shows a tier-appropriate color and the age-rating pill is colored/outlined.
2. Open a movie or show item with `criticRating` populated (if your library has one) — confirm the 🍅 badge appears next to the star badge.
3. Open an item with no ratings at all — confirm no empty/broken badge space is rendered (graceful `null` handling).
4. Open a movie/show/episode with an HEVC-encoded file — confirm the codec chip renders in its distinct color/icon (not the old flat gray).
5. Open a movie/show/episode with an AVC-encoded file — confirm the codec chip still shows the existing `avc_24px` icon, now colored.
6. Repeat on the TV Show detail, TV Season detail, and TV Episode detail screens to confirm visual consistency across all four screens.

- [ ] **Step 5: Report results to the user**

No commit for this task — it's verification only. Summarize pass/fail for each smoke-test item above.

---

## Self-Review Notes

- **Spec coverage:** Task 1 → tier colors; Task 2 → `RatingBadges.kt` (Community/Critic/Official/RatingRow); Task 3 → `VideoCodecType`/`CodecTypeBadge`; Tasks 4–7 → rollout to all four screens named in the spec's Scope section; Task 8 → testing section of the spec. `DetailVideoInfoRow.kt` is untouched per the spec's explicit non-goal.
- **Deviation from spec's `RatingRow` shape:** the spec described `RatingRow(communityRating, criticRating, officialRating, modifier)`. During file investigation, official rating badges were found to sit in different visual positions relative to community rating in 3 of the 4 screens (mixed into an unrelated Row with year/duration, or in a separate section entirely) — only the season screen has them adjacent. `RatingRow` was narrowed to `(communityRating, criticRating)` and `OfficialRatingBadge` kept as a standalone composable placed inline at each screen's existing official-rating position. This preserves every screen's existing layout while still de-duplicating all three badge types through shared components — the spec's actual goal.
- **Placeholder scan:** no TBD/TODO; every step has literal code.
- **Type consistency:** `CommunityRatingBadge(rating: Float)`, `CriticRatingBadge(rating: Float)`, `OfficialRatingBadge(rating: String)`, `RatingRow(communityRating: Float?, criticRating: Float?)` are used with matching signatures across Tasks 4–7. `VideoCodecType.fromCodecName(codec: String): VideoCodecType` and `CodecTypeBadge(codecText: String)` are used consistently in Task 3 (definition) — Tasks 5 and 7 don't call `CodecTypeBadge` directly, they only stop passing the removed `codecIcon` argument to `VideoInfoCard`, which internally calls `CodecTypeBadge`.
