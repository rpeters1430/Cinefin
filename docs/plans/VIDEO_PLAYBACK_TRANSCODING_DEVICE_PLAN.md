# Video Playback & Transcoding Improvement Plan (All-Device Strategy)

## Goal
Build a **predictable, data-driven playback pipeline** that gives correct Direct Play / Direct Stream / Full Transcode decisions across flagship devices (e.g., Samsung S25 Ultra, Pixel 10 Pro XL) and long-tail Android devices.

## Current State (Project Review)

### What is working well
- Playback selection already uses server PlaybackInfo + client capability validation in `EnhancedPlaybackManager`.  
- Audio channel-aware support checks exist in `DeviceCapabilities`, including stereo fallback for surround codecs.
- The app can do audio-only transcoding (Direct Stream for video + transcode audio), which is a good efficiency baseline.

### Key gaps preventing universal device reliability
1. **Capability detection is mostly static + best-effort probing**
   - Supported codec/container sets are global defaults, then probed, but not fully profile-aware (HDR level, bit depth, profile/level constraints).
2. **No persistent per-device “known-good/known-bad” override table**
   - Playback behavior can still vary unpredictably by OEM firmware version.
3. **Transcoding policy lacks a unified rules engine**
   - Decisions are spread across manager/repository methods and can drift over time.
4. **Insufficient feedback loop from real-world failures**
   - There is logging, but not a structured telemetry pipeline that automatically improves rules.
5. **Server parameters are not always intent-explicit**
   - In some downmix scenarios, policy can still allow stream copy when forced transcode would be safer.

## Target Architecture

### 1) Device Capability Layer (Authoritative)
Create a `DevicePlaybackProfile` model generated at runtime and cached:
- Device identity: manufacturer, model, API level, SoC hints.
- Video support matrix: codec + profile + bit depth + max resolution/fps.
- Audio support matrix: codec + max channels + passthrough/downmix behavior.
- Container + subtitle behavior.
- Network tier preferences.

Then apply:
- **Static baseline** (common Android support)
- **Runtime probe** (MediaCodec + ExoPlayer test clips)
- **Cloud/remote override** (known OEM quirks, firmware regressions)

### 2) Unified Playback Decision Engine
Add a single decision entry-point (pure function + trace output):
- Inputs: media metadata, device profile, network state, user preference.
- Output: one of
  - `DIRECT_PLAY`
  - `DIRECT_STREAM_VIDEO_COPY_AUDIO_TRANSCODE`
  - `FULL_TRANSCODE`
- Always include a structured reason list (for logs + diagnostics UI).

### 3) Server Transcoding Policy Contract
Standardize one policy builder that maps decision output to Jellyfin params:
- Explicitly set `AllowVideoStreamCopy` / `AllowAudioStreamCopy` from decision.
- Enforce downmix/transcode rules for surround-on-stereo devices.
- Choose safer defaults for universal compatibility:
  - video target: `h264` fallback path, `hevc`/`av1` only when truly compatible
  - audio target: `aac` universal fallback
  - container: `ts`/`mp4` based on capability and subtitle mode

### 4) Continuous Validation + Telemetry
Implement low-noise analytics events:
- decision outcome
- playback startup time
- first-failure category (decoder init, unsupported format, buffering)
- fallback path used

Use this to auto-promote rule updates for device cohorts.

## Phased Implementation Plan

## Phase 0 (1 week): Observability Foundation
- Add structured decision trace object and emit it once per playback session.
- Add failure taxonomy in player error handling (codec init, container, network, DRM/subtitle).
- Add diagnostics export in debug screen.

**Exit criteria:** 95%+ sessions include machine-readable decision + result.

## Phase 1 (1–2 weeks): Capability Profile Hardening
- Introduce `DevicePlaybackProfile` + repository cache.
- Expand codec normalization (e.g., Dolby variants, alias mapping, profile-level metadata placeholders).
- Add safe default profile families:
  - flagship (Pixel/Samsung high tier)
  - mid-tier
  - legacy/low-RAM

**Exit criteria:** all playback decisions use profile object, not scattered checks.

## Phase 2 (2 weeks): Decision Engine Refactor
- Refactor current logic in `EnhancedPlaybackManager` to a deterministic decision engine.
- Centralize Direct Play vs Direct Stream vs Full Transcode criteria.
- Add explicit rule IDs (e.g., `AUDIO_SURROUND_DOWNMIX_RULE`) for easier debugging.

**Exit criteria:** same media + same profile always yields same decision and reason trace.

## Phase 3 (1–2 weeks): Server Policy Alignment
- Create one transcoding URL policy builder used by all call sites.
- Make audio stream copy conditional on compatibility/downmix intent.
- Add subtitle strategy policy (burn-in only when required).

**Exit criteria:** no conflicting transcoding parameter generation paths.

## Phase 4 (2 weeks): Device Matrix Certification
Create a repeatable certification matrix:
- Axes:
  - codec (H264/H265/AV1/VP9)
  - audio (AAC/AC3/EAC3/Opus/DTS where available)
  - channels (2.0/5.1/7.1)
  - container (mp4/mkv/webm/ts)
  - subtitle type (none/text/image/burned)
- Include your priority hardware first:
  - Samsung S25 Ultra
  - Pixel 10 Pro XL
- Then expand to representative long-tail devices.

**Exit criteria:** pass-rate dashboard by device cohort and format family.

## Phase 5 (ongoing): Remote Rule Updates
- Serve device overrides from remote config with strict versioning.
- Add kill switches for risky codec paths.
- Roll out in staged cohorts (1%, 10%, 50%, 100%).

**Exit criteria:** urgent regressions can be mitigated without app release.

## Practical Rules to Adopt Immediately
1. Prefer **Direct Stream (video copy + audio transcode)** over full transcode when video is compatible.
2. If surround codec is only stereo-capable on the device profile, force audio transcode/downmix policy explicitly.
3. Never up-transcode to higher resolution/bitrate than source.
4. Keep a conservative universal fallback ladder:
   - H264 + AAC + TS/MP4
5. Log every fallback transition with a machine-readable reason code.

## Test Strategy

### Automated
- Unit tests for decision engine permutations (device profile x media features x network tier).
- Property tests for invariants:
  - no impossible decision output
  - no “direct play” when unsupported codec/profile is known.
- Integration tests for URL parameter policy generation.

### On-device / lab
- Golden clip suite (small files for each codec/container/audio channel combination).
- Startup latency + rebuffer measurements for each decision type.
- Regression suite for known problematic titles.

## Success Metrics
- Playback start success rate (PSSR) ≥ 99%.
- “Unexpected transcoding” complaint rate reduced by 70%.
- First-frame startup p95 improved for compatible media.
- Fallback-to-full-transcode rate reduced on modern flagship devices.

## Risks & Mitigations
- **Risk:** Overfitting rules to a few devices.  
  **Mitigation:** cohort-based policy + telemetry-driven updates.
- **Risk:** Policy complexity grows too fast.  
  **Mitigation:** single decision engine + rule IDs + strict tests.
- **Risk:** Jellyfin server behavior differences by version.  
  **Mitigation:** include server-version awareness in policy builder.

## Suggested Backlog Tickets (ready to create)
1. Add `DevicePlaybackProfile` data model + cache.
2. Build `PlaybackDecisionEngine` with reason codes.
3. Consolidate transcoding query construction into one policy service.
4. Add decision/failure telemetry schema + dashboards.
5. Build golden media clip certification suite.
6. Add remote-config override mechanism for device quirks.

