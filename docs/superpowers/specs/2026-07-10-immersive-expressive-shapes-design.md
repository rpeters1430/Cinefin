# Immersive Layer: Material 3 Expressive Shape System

**Date**: 2026-07-10
**Status**: Approved for planning

## Goal

The app's immersive (Netflix/Disney+-style) UI layer — `ui/components/immersive/*` and
`ImmersiveDimens` — currently uses flat, hand-picked corner radii (8–12dp `RoundedCornerShape`)
that don't follow the Material 3 Expressive shape spec, even though the app's base theme
(`Theme.kt`) already uses `MaterialExpressiveTheme` and `MotionScheme.expressive()`. This is
phase 1 of moving the app toward a genuinely Expressive look: bring the immersive layer's shape
language in line with the Expressive corner scale, and introduce morphing polygon shapes
(a signature Expressive component) as a deliberate accent.

## Scope

**In scope**: `ui/components/immersive/*` (media cards, FAB group, hero carousel, parallax hero,
badges) and `ImmersiveDimens` shape-related tokens.

**Out of scope** (future phases): motion/interaction feedback, color/elevation, the standalone
`Expressive*` components (`ExpressiveCards.kt`, `ExpressiveFAB.kt`, `ExpressiveListItems.kt`,
`ExpressiveLoading.kt`), TV screens, and the shared `ShapeTokens`/`JellyfinShapes` used outside
the immersive layer.

## Design

### 1. New immersive shape scale

Add an `ImmersiveShapes` object to `ui/theme/Shape.kt` implementing the Expressive corner scale
(None, ExtraSmall 4dp, Small 8dp, Medium 12dp, Large 16dp, LargeIncreased 20dp, ExtraLarge 28dp,
ExtraLargeIncreased 32dp, ExtraExtraLarge 48dp, Full). Prefer sourcing these from material3
1.5.0-alpha23's own Expressive shape tokens if exposed; otherwise hand-define
`RoundedCornerShape` values matching the spec.

Replace the three flat constants in `ImmersiveDimens` (`CornerRadiusCinematic` 12dp,
`CornerRadiusCard` 8dp, `CornerRadiusSmall` 4dp) with references into `ImmersiveShapes`.

Mapping:
- Hero image/container: `ExtraLarge`/`ExtraLargeIncreased` (28–32dp)
- Media cards (`ImmersiveMediaCard`): `Large`/`LargeIncreased` (16–20dp), up from 12dp
- Badges/pills (rating, watch-status, episode-count): `Full` (pill) or `Medium` (square-ish)
- Gradient scrims: unaffected (no shape)

### 2. Morphing shape accents

Using material3's `MaterialShapes` polygon presets (Cookie4/6/9-Sided, Sunny, Clover, Pill, etc.)
and `androidx.graphics.shapes.Morph`, add shape-morph animation in exactly two places:

1. **`FloatingActionGroup` primary FAB** — morphs from circle (rest) to a soft cookie/sunny
   polygon on press, animated with `MotionTokens.expressiveMotionScheme`. Secondary FABs remain
   circular; only the primary action morphs, to preserve visual hierarchy.
2. **Unwatched-episode-count badge** in `ImmersiveMediaCard` — replace the current
   `MaterialTheme.shapes.large` rounded-rect badge with a small cookie/sunny polygon shape.

All other elements (rating badge, watched checkmark, nav bars) keep standard rounded/circular
shapes. Morphing is a sparing accent, not a blanket treatment.

**Risk**: `MaterialShapes`/shape-morph API surface in material3 1.5.0-alpha23 may not match
later stable releases. Verify exact class/function names against the actual AAR during
implementation; fall back to a hand-rolled `androidx.graphics.shapes.Morph` polygon if the
material3 preset isn't available at this alpha version.

## Files touched

- `ui/theme/Shape.kt` — new `ImmersiveShapes` object
- `ui/theme/Dimensions.kt` — replace flat `ImmersiveDimens` corner constants
- `ui/components/immersive/ImmersiveMediaCard.kt` — card shape, image clip, badge morph
- `ui/components/immersive/FloatingActionGroup.kt` — primary FAB shape-morph
- `ui/components/immersive/ImmersiveHeroCarousel.kt`, `ParallaxHeroSection.kt` — hero corner
  update if they clip corners (confirm at implementation time)
- `ui/components/immersive/MediaInfoBadges.kt` — badge shape updates if logic is duplicated here

## Testing

Visual/UI-only change, no new business logic — no new unit tests. Manual verification: build
debug APK, exercise Home → immersive card press states, a detail screen's FAB, confirm shapes
render/morph correctly in light and dark theme, on phone and tablet sizing.
