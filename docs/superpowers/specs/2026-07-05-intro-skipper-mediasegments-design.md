# Intro/Credits Skip via Jellyfin MediaSegments API

Date: 2026-07-05

## Problem

The video player already renders "Skip Intro" / "Skip Credits" buttons
(`SkipIntroOutroButtons` in `VideoPlayerOverlays.kt`), driven by
`VideoPlayerState.introStartMs/introEndMs/outroStartMs/outroEndMs`. Today those
fields are populated by `VideoPlayerMetadataManager.loadSkipMarkers` using a
weak heuristic: it scans the item's chapter list for chapter names containing
"intro"/"opening" or "credits"/"outro"/"ending". This only works for libraries
where chapters happen to be named that way, and it doesn't reflect real
fingerprinted segment data from the
[intro-skipper](https://github.com/intro-skipper/intro-skipper) plugin.

Jellyfin server 10.9+ has an official MediaSegments API
(`GET /MediaSegments/{itemId}`) that intro-skipper populates with precise
`INTRO`/`OUTRO` (and `RECAP`/`PREVIEW`/`COMMERCIAL`) segment boundaries per
item. The Jellyfin Android SDK (1.8.11, already a dependency) has this fully
bound: `ApiClient.mediaSegmentsApi.getItemSegments(itemId, includeSegmentTypes)`
returning `MediaSegmentDtoQueryResult` of `MediaSegmentDto(id, itemId, type,
startTicks, endTicks)`.

## Scope

- Phone/tablet player only (`VideoPlayerActivity`, `ExpressiveVideoControls`,
  `VideoPlayerOverlays`). `TvVideoPlayerControls.kt` and
  `TvPlayerControls_Backup.kt` are not currently wired into any screen
  (verified via reference search) and are out of scope.
- Segment types: `INTRO` and `OUTRO` only, matching the existing "Skip Intro"
  / "Skip Credits" buttons. `RECAP`/`PREVIEW`/`COMMERCIAL` are not requested
  or surfaced.
- Skip UX stays tap-to-skip (no auto-skip preference is being added).
- No changes to `VideoPlayerState`, `SkipIntroOutroButtons`,
  `ExpressiveVideoControls`, or `VideoPlayerViewModel` — they already consume
  `introStartMs/introEndMs/outroStartMs/outroEndMs` correctly. This is a
  data-source swap underneath existing UI, not a UI change.

## Design

### 1. Repository: fetch real segments

Add to `IJellyfinRepository`:

```kotlin
suspend fun getMediaSegments(itemId: String): ApiResult<List<MediaSegmentDto>>
```

Implement in `JellyfinRepository` using the existing `withServerClient`
wrapper (same pattern as `getMovieDetails`/`getEpisodeDetails`):

```kotlin
override suspend fun getMediaSegments(itemId: String): ApiResult<List<MediaSegmentDto>> =
    withServerClient("getMediaSegments") { _, client ->
        val uuid = UUID.fromString(itemId)
        client.mediaSegmentsApi.getItemSegments(
            itemId = uuid,
            includeSegmentTypes = listOf(MediaSegmentType.INTRO, MediaSegmentType.OUTRO),
        ).content.items ?: emptyList()
    }
```

`withServerClient` already converts thrown exceptions into `ApiResult.Error`,
so a server without the MediaSegments endpoint (pre-10.9, or the plugin not
installed/no segments computed yet for that item) naturally surfaces as an
error here — no special-casing needed at this layer.

### 2. Metadata manager: prefer real segments, fall back to chapter heuristic

In `VideoPlayerMetadataManager.loadSkipMarkers`, after resolving `item`
(episode or movie details) as today:

1. Call `repository.getMediaSegments(itemId)`.
2. If `ApiResult.Success` and the list is non-empty:
   - Take the earliest-starting segment of type `INTRO` → `introStartMs` /
     `introEndMs` (ticks / 10_000, same conversion already used for
     chapters).
   - Take the earliest-starting segment of type `OUTRO` → `outroStartMs` /
     `outroEndMs`.
   - Any type with no matching segment stays `null`.
   - Update state and return — **skip the chapter heuristic** in this case.
3. Otherwise (`ApiResult.Error`, or `Success` with an empty list): fall
   through to the existing chapter-name heuristic unchanged.

This preserves current behavior for servers/items without MediaSegments data,
while preferring accurate plugin-fingerprinted data when available.

### 3. No UI changes

`SkipIntroOutroButtons` already:
- Shows "Skip Intro" when `currentPosMs in introStartMs..introEndMs`, seeking
  to `introEndMs` on tap.
- Shows "Skip Credits" when `currentPosMs >= outroStartMs`, seeking to
  `outroEndMs` (or `currentPosMs + 10_000` if null) on tap.

Both behaviors are correct for real MediaSegments data as well, so nothing
here needs to change.

## Testing

- **Repository unit test**: mock `ApiClient`/`mediaSegmentsApi` (or the
  session manager's client) to verify `getMediaSegments` maps
  `MediaSegmentDtoQueryResult` correctly and surfaces `ApiResult.Error` when
  the call throws.
- **`VideoPlayerMetadataManager` unit test** (`loadSkipMarkers`):
  - Segments present for both `INTRO` and `OUTRO` → state fields set from
    segments, chapter heuristic not consulted.
  - `getMediaSegments` returns `ApiResult.Error` (simulating old
    server/no plugin) → falls back to existing chapter-name heuristic
    behavior (already covered by existing tests, must still pass).
  - `getMediaSegments` returns `ApiResult.Success(emptyList())` → falls back
    to chapter heuristic.
  - Neither source has data → all four fields `null`, no skip buttons
    (existing behavior).

## Out of scope / non-goals

- Android TV player wiring (dead code currently, not part of this change).
- Auto-skip preference/toggle.
- RECAP/PREVIEW/COMMERCIAL segment types.
- Any change to how intro-skipper itself computes segments (server-side
  plugin behavior is untouched; this is purely the Android client consuming
  the existing official API it already writes to).
