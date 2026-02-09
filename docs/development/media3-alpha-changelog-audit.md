# Media3 latest alpha changelog audit for Jellyfin Android

This document maps the latest Media3 alpha changelog items to the current Jellyfin Android codebase.

## Current baseline

- The app is pinned to `androidx.media3` `1.10.0-alpha01`.
- Core modules in use: ExoPlayer (`exoplayer`, `hls`, `dash`), `common`, `ui`, `session`, `cast`, and `datasource-okhttp`.

## What we should improve now (high value / low risk)

### 1) Enable dynamic video scheduling experiment in `DefaultRenderersFactory`

**Changelog item:** `experimentalSetEnableMediaCodecVideoRendererDurationToProgressUs()`.

**Why it matters here:**
- We build `DefaultRenderersFactory` in two playback setup paths and rely on software/hardware fallback behavior.
- This is exactly where the new scheduling optimization is configured.

**Recommendation:**
- Ensure the opt-in is applied identically in both ExoPlayer builder paths in `VideoPlayerViewModel` (around the two existing init blocks).
- Keep this behind a local flag and shared helper to avoid one-path-only behavior.

### 2) Cast: add track selector to `RemoteCastPlayer`

**Changelog item:** `RemoteCastPlayer.Builder#setTrackSelector(...)`.

**Why it matters here:**
- We already have custom cast orchestration (`CastManager`) and local track state handling (`TrackSelectionManager`).
- This API gives us a path to align Cast audio/subtitle behavior with local preferences rather than receiver defaults.

**Recommendation:**
- Add to near-term backlog (do now/low risk if cast stack changes are isolated; do next/medium if staged).
- Implement a Cast track selector bridge so preferred audio/subtitle tracks are applied consistently for Cast playback.

### 3) Adopt new compose speed/progress controls selectively

**Changelog items:** `PlaybackSpeedControl`, `PlaybackSpeedToggleButton`, `ProgressSlider`.

**Why it matters here:**
- We have custom controls for phone/TV. The new composables could reduce maintenance cost where our controls are basic.

**Recommendation:**
- No urgent migration required.
- Evaluate only for screens where parity with Material3 behavior and accessibility is desired.

### 4) Validate AC-4/IAMF behavior on Automotive & Spatializer-capable devices

**Changelog items:** AC-4 profile handling fix and IAMF decoder/spatialization updates.

**Why it matters here:**
- We support mixed codecs and transcoding fallback; device-specific codec capability changes can alter direct play vs transcode decisions.

**Recommendation:**
- Add/extend playback capability QA matrix for Automotive-like environments and spatial audio devices.

## Benefits we already get from alpha01

### ProgressiveMediaSource timeline/queue fix (relevant to offline playback)

**Changelog item:** ProgressiveMediaSource stale timeline propagation fix (#3016).

**Why it matters here:**
- `OfflinePlaybackManager` uses `ProgressiveMediaSource` for local file playback.
- This fix reduces risk of queued periods being removed unexpectedly in progressive/offline flows.

## Changes we likely need if we upgrade beyond current alpha

### 1) Session custom notification provider API drift (potential breakage)

**Changelog item:** stale foreground-service `Intent` detection added; **breaking change on unstable API** for apps implementing custom `MediaNotification.Provider`.

**Current implementation:**
- `AudioService` currently uses `DefaultMediaNotificationProvider`, not a custom provider implementation.

**Impact:**
- Low immediate risk.
- If we later switch to a custom provider, we must implement the new required method at the same time.

### 2) Inspector module split (`FrameExtractor` removal)

**Changelog item:** `FrameExtractor` removed from old package, moved to `:media3-inspector-frame`.

**Current implementation:**
- No `FrameExtractor` usage found in app source.

### 3) Effect Lottie module split

**Changelog item:** `LottieOverlay` moved to `:media3-effect-lottie` with package rename.

**Current implementation:**
- No `LottieOverlay` usage found in app source.

### 4) Removed deprecated symbols (`ExperimentalFrameExtractor`, `ChannelMixingMatrix.create()`)

**Current implementation:**
- No usages found for these removed APIs.

## Changelog items that are not currently relevant to our implementation

- `AdsMediaSource` clipping: not using Media3 ad media source pipelines in app playback path.
- `DefaultPreloadManager.Builder` custom `DataSource.Factory`: not using preload manager.
- `CompositionPlayer` / `Transformer` edited media features: not used for local editing/export workflows.
- IMA extension additions: no IMA extension dependency in current build.

## Concrete code-level next steps

1. Ensure the dynamic renderer scheduling opt-in is applied identically in both ExoPlayer builder paths in `VideoPlayerViewModel`, via shared helper(s).
2. Unify ExoPlayer creation into a single factory/helper method so renderer flags + track selector + source-factory differences are explicit and testable.
3. Add Cast track selector integration plan using `RemoteCastPlayer.Builder#setTrackSelector(...)` to mirror local audio/subtitle preferences on Cast sessions.
4. Keep `MediaSessionService` implementation as-is for now; revisit only if we introduce custom media notification provider logic.
5. Keep watcher checks for removed/moved APIs in dependency bump PR template.
6. Add one regression test pass for playback startup latency + dropped frames after enabling dynamic scheduling experiment.

## Priority summary

- **Do now:** dynamic scheduling helper parity across both ExoPlayer paths + single ExoPlayer factory helper.
- **Do next (medium):** Cast track selector integration for receiver audio/subtitle consistency.
- **Do on next media3 bump:** validate session-notification unstable API changes and rerun codec capability QA.
- **No-op currently:** inspector/effect/IMA-specific migrations unless those modules are introduced.
