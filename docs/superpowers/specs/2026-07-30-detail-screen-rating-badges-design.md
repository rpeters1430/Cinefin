# Detail Screen Rating & Codec Badges — Design

## Goal

The movie/TV show/season/episode detail screens have good data but present it as plain
text/inline hand-rolled UI. Give ratings and video codec info a more "professional"
treatment — colored badges with icons, consistent across all four detail screens —
matching the existing polish already present in the resolution/HDR/Atmos badges.

## Scope

- `ImmersiveMovieDetailScreen.kt` (via `MovieHeroContent.kt`)
- `ImmersiveTVShowDetailScreen.kt`
- `ImmersiveTVSeasonScreen.kt`
- `ImmersiveTVEpisodeDetailScreen.kt`

Out of scope: `ItemDetailScreen.kt` (non-immersive fallback), `tv/TvItemDetailScreen.kt`,
and `DetailVideoInfoRow.kt` (confirmed dead code — not referenced by any of the four
screens above, so left untouched).

## Current State

- **Tech-spec badges** (`ui/components/immersive/MediaInfoBadges.kt`): already solid —
  `QualityBadge` (resolution, gradient per tier), `HdrBadge`, `AtmosBadge` all have
  distinct gradients/icons. `CodecBadge` is a single flat gray pill used for codec name,
  bit-depth, frame rate, and 3D flag alike — codec itself has no visual distinction.
- **Ratings**: no shared component. Each of the 4 screens hand-rolls its own inline
  rating UI:
  - `communityRating` (0–10 scale): rendered as an orange→gold gradient box + star icon
    + `"%.1f"` text, duplicated per screen with copy-pasted styling.
  - `officialRating` (e.g. PG-13): `Surface` + `Text`, colored via
    `getOfficialRatingColor()` in `ui/theme/Color.kt`.
  - `criticRating` (Rotten Tomatoes 0–100, present on `BaseItemDto`): **not rendered
    anywhere** currently.

## Design

### 1. Shared rating badges — new file `ui/components/RatingBadges.kt`

- **`CommunityRatingBadge(rating: Float)`**
  - Star icon + `"%.1f"` text in a gradient pill, matching the existing visual weight
    of `QualityBadge`/`HdrBadge`.
  - Gradient tier by score: green (≥7.0), amber (5.0–6.9), red (<5.0) — replaces the
    current always-orange gradient with a score-reflective one.
- **`CriticRatingBadge(rating: Float)`**
  - 🍅 + `"${rating.toInt()}%"` in a gradient pill.
  - Tier: green ("fresh", ≥60), red ("rotten", <60).
  - Only rendered when `criticRating != null`.
- **`OfficialRatingBadge(rating: String)`**
  - Refactor of the existing outline+`getOfficialRatingColor()` pattern into a shared
    composable (visual style unchanged, just de-duplicated).
- **`RatingRow(communityRating, criticRating, officialRating, modifier)`**
  - Convenience composable: lays out whichever of the three badges are non-null in a
    `Row`, so call sites collapse to one line instead of a hand-rolled block.

### 2. Codec badge upgrade — extend `ui/components/immersive/MediaInfoBadges.kt`

- New enum `VideoCodecType` (mirrors `ResolutionQuality`/`HdrType` pattern): HEVC/H.265,
  AVC/H.264, AV1, VP9, other/unknown — each with a distinct icon + accent color
  (e.g. HEVC green "efficient", AVC blue "standard", AV1 purple "next-gen").
  - `VideoCodecType.fromCodecName(codec: String?)` maps the raw codec string to a type.
- `VideoInfoCard`'s codec chip switches from plain `CodecBadge` to a colored badge
  driven by `VideoCodecType`. Bit-depth, frame rate, and 3D chips remain plain
  `CodecBadge` (secondary info, not the visual focus).

### 3. Rollout — replace inline duplication with shared components

Each of the 4 screens' inline rating block is replaced with a single `RatingRow(...)`
call using that screen's `communityRating`/`criticRating`/`officialRating` fields.
No behavior change beyond visuals — same data, same nullability handling.

## Testing

- Existing Compose UI/screenshot tests (if any) covering these screens should still
  pass with updated snapshots where applicable.
- Manual verification: run the app, view a movie/show/season/episode with and without
  each rating field populated (community only, community + critic, no ratings) to
  confirm graceful fallback when fields are null.
- Manual verification of codec badge colors against a few real codec strings
  (`hevc`, `h264`, `av1`) from actual library items.

## Non-goals

- No custom vector drawable assets — 🍅 emoji is used directly for the critic rating
  icon (matches the approved mockup), avoiding new asset creation.
- No changes to `DetailVideoInfoRow.kt`, `ItemDetailScreen.kt`, or TV-input detail
  screens.
- No new data sources — uses only `communityRating`, `criticRating`, `officialRating`
  already available on `BaseItemDto`.
