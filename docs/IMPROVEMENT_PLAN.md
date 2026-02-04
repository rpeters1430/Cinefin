# Jellyfin Android Improvement Plan

**Last Updated**: February 4, 2026
**Scope**: Full codebase audit covering transcoding/playback, security, accessibility, UX, and code quality
**Previous Version**: January 30, 2026 (see docs/archive/ for older plans)

> **Note**: For user-facing bugs with workarounds, see [KNOWN_ISSUES.md](../KNOWN_ISSUES.md). For feature roadmap, see [ROADMAP.md](../ROADMAP.md). This document focuses on technical debt, architecture improvements, and code quality.

---

## Table of Contents

1. [Recently Completed](#recently-completed-)
2. [Phase A: Transcoding & Playback System Overhaul](#phase-a-transcoding--playback-system-overhaul-critical)
3. [Phase B: Security Hardening](#phase-b-security-hardening-high)
4. [Phase C: Reliability & Error Handling](#phase-c-reliability--error-handling-high)
5. [Phase D: UX Polish & Accessibility](#phase-d-ux-polish--accessibility-medium)
6. [Phase E: Missing User Preferences](#phase-e-missing-user-preferences-medium)
7. [Phase F: Code Quality & Technical Debt](#phase-f-code-quality--technical-debt-carried-forward)
8. [Phase G: Subtitle System Improvements](#phase-g-subtitle-system-improvements-medium)
9. [Implementation Priority Order](#implementation-priority-order)
10. [Related Documentation](#related-documentation)

---

## Recently Completed ✅

**Completion Date**: January 2026

1) **Offline downloads hanging bug** - Replaced infinite `collect` with `first()` + timeout
   - File: `data/offline/OfflineDownloadManager.kt:207`

2) **Offline download ID mismatch** - Made `startDownload` suspend, returns real ID
   - File: `data/offline/OfflineDownloadManager.kt:84-94`

3) **Cache directory initialization race condition** - Added `ensureCacheDir()` guard
   - File: `data/cache/JellyfinCache.kt`

4) **Memory cache thread safety** - Added `synchronized` blocks around memoryCache
   - File: `data/cache/JellyfinCache.kt`

5) **GlobalScope replacement** - Injected `@ApplicationScope` for cache init
   - File: `data/cache/JellyfinCache.kt`

6) **Video player auto quality selection** - "Auto" clears track overrides for adaptive selection
   - File: `ui/player/VideoPlayerViewModel.kt:1026-1068`

7) **Screen refactoring** - Split Movies/TV Shows/Stuff screens into smaller composables

8) **Warning cleanup** - Removed safe-call on non-null, redundant casts, deprecated API calls

9) **Transcoding position reset fix** - Position preservation across codec flushes with retry limits
   - File: `ui/player/VideoPlayerViewModel.kt` (see `TRANSCODING_FIX_SUMMARY.md`)

10) **Transcoding diagnostics tool** - Identifies which videos need transcoding and why
    - File: `ui/viewmodel/TranscodingDiagnosticsViewModel.kt`

---

## Phase A: Transcoding & Playback System Overhaul (CRITICAL)

The transcoding system works for basic cases but has significant architectural gaps that prevent optimal server interaction, cause unnecessary transcoding, and degrade playback quality.

### A1. Implement Jellyfin DeviceProfile Specification
**Priority**: Critical | **Effort**: 3-5 days

**Problem**: No `DeviceProfile` is sent to the Jellyfin server. The server has zero knowledge of this device's actual capabilities, so it falls back to generic transcoding decisions. This is the single biggest gap in the playback system.

**Current state**: `DeviceCapabilities.kt` detects hardware codecs, but this information is never transmitted to the server. `JellyfinStreamRepository` builds stream URLs without any device profile parameters.

**Files**:
- `data/DeviceCapabilities.kt` - Has codec detection but doesn't generate a DeviceProfile
- `data/repository/JellyfinStreamRepository.kt` - Builds URLs without profile parameters
- `data/playback/EnhancedPlaybackManager.kt` - Makes decisions without telling the server

**Plan**:
- [ ] Create `DeviceProfile` data class matching Jellyfin's specification (CodecProfiles, ContainerProfiles, TranscodingProfiles)
- [ ] Generate device-specific profile from `DeviceCapabilities` detected codecs
- [ ] Send profile with `POST /Sessions/Playing` and include in stream URL parameters
- [ ] Add profile caching so detection doesn't repeat every playback
- [ ] Test with server-side transcoding logs to confirm profile is respected

**Impact**: Eliminates unnecessary transcoding for devices that support the source format.

---

### A2. Fix Network Quality Assessment (Currently Broken)
**Priority**: Critical | **Effort**: 1-2 days

**Problem**: `JellyfinStreamRepository` has a `getNetworkQuality()` method that **always returns HIGH** (hardcoded). This means adaptive quality selection is impossible.

**Current state** (`JellyfinStreamRepository.kt:473-482`):
```kotlin
// Always returns HIGH - broken implementation
private fun getNetworkQuality(): NetworkQuality {
    return NetworkQuality.HIGH  // TODO: implement actual detection
}
```

**Files**:
- `data/repository/JellyfinStreamRepository.kt:473-482` - Broken stub
- `data/playback/EnhancedPlaybackManager.kt:379-399` - Simplistic transport check

**Plan**:
- [ ] Implement actual bandwidth estimation using OkHttp interceptor timing
- [ ] Use ConnectivityManager for transport type AND signal strength
- [ ] Detect metered connections via `NetworkCapabilities.NET_CAPABILITY_NOT_METERED`
- [ ] Feed measured bandwidth into bitrate selection decisions
- [ ] Add periodic re-evaluation during long playback sessions

---

### A3. Make Bitrate Thresholds Configurable
**Priority**: High | **Effort**: 1-2 days

**Problem**: All bitrate thresholds are hardcoded with no user control. WiFi caps at 50 Mbps (may be too low for LAN 4K playback), cellular caps at 15 Mbps, and transcoding quality tiers (20/8/3 Mbps) are fixed.

**Hardcoded values** (`EnhancedPlaybackManager.kt`):
| Value | Line | Issue |
|-------|------|-------|
| WiFi Direct Play: 50 Mbps | ~44 | Too low for LAN 4K remux |
| Cellular Direct Play: 15 Mbps | ~45 | Not configurable |
| HIGH transcode: 20 Mbps | ~304 | Too low for LAN |
| MEDIUM transcode: 8 Mbps | ~314 | Fixed |
| LOW transcode: 3 Mbps | ~324 | May cause buffering |
| Max audio channels: 2 | ~310 | Ignores 5.1/7.1 |

**Plan**:
- [ ] Add "Maximum streaming bitrate" setting (WiFi / Cellular separate)
- [ ] Add "Transcoding quality" preference (Auto, Maximum, High, Medium, Low)
- [ ] Add "Audio channels" preference (Stereo, 5.1, 7.1, Auto)
- [ ] Store in DataStore and inject into `EnhancedPlaybackManager`
- [ ] Expose in Settings screen under Playback section

---

### A4. Send Audio/Subtitle Stream Indices to Server
**Priority**: High | **Effort**: 1 day

**Problem**: When transcoding, the app doesn't specify which audio or subtitle stream to transcode. The server guesses (usually first audio track), which is wrong for multilingual content.

**Files**:
- `data/repository/JellyfinStreamRepository.kt` - Missing `AudioStreamIndex` and `SubtitleStreamIndex` parameters
- `ui/player/VideoPlayerViewModel.kt` - Tracks selected audio/subtitle but doesn't pass to stream URL

**Plan**:
- [ ] Add `AudioStreamIndex` parameter to transcoding URL builder
- [ ] Add `SubtitleStreamIndex` parameter when server-side subtitles are needed
- [ ] Pass user's audio track selection from `VideoPlayerViewModel` to `JellyfinStreamRepository`
- [ ] Handle mid-playback audio track changes by rebuilding the transcoding URL

---

### A5. Implement Transcoding Session Lifecycle
**Priority**: High | **Effort**: 2-3 days

**Problem**: PlaySessionId is generated but the server session is never maintained. No heartbeat keeps the session alive, and no cleanup occurs when playback ends. The server may timeout mid-video and stop the transcode.

**Files**:
- `ui/player/PlaybackProgressManager.kt` - Reports position but no session keepalive
- `data/repository/JellyfinStreamRepository.kt` - Generates PlaySessionId but never refreshes it

**Plan**:
- [ ] Send periodic heartbeat to `/Sessions/Playing/Progress` (every 10 seconds)
- [ ] Send `/Sessions/Playing/Stopped` when playback ends or app backgrounds
- [ ] Handle server-side session timeout gracefully (re-create session on 404)
- [ ] Queue progress updates when offline and sync on reconnect
- [ ] Clean up sessions in `onCleared()` of VideoPlayerViewModel

---

### A6. Improve Transcoding Fallback Detection
**Priority**: Medium | **Effort**: 1-2 days

**Problem**: Direct Play to Transcoding fallback relies on string-matching error messages (e.g., checking for "AudioRenderer" or "audio/eac3" in error text). This is brittle and varies by device/Android version.

**Current state** (`VideoPlayerViewModel.kt:343-351`):
```kotlin
// Fragile pattern matching
val shouldFallback = errorMessage.contains("AudioRenderer") ||
    errorMessage.contains("audio/eac3") || ...
```

**Plan**:
- [ ] Detect by ExoPlayer error code (`PlaybackException.errorCode`) instead of message strings
- [ ] Distinguish "codec unsupported" vs "bitrate too high" vs "resolution exceeded"
- [ ] For bitrate-related failures, retry with lower quality before switching to transcoding
- [ ] Allow multiple fallback attempts (current limit: 1 attempt via `hasAttemptedTranscodingFallback`)

---

### A7. Unify Codec Detection Logic
**Priority**: Medium | **Effort**: 1-2 days

**Problem**: Codec support is detected in three separate places with inconsistent logic:
- `DeviceCapabilities.kt` - Queries MediaCodecList (most thorough)
- `JellyfinStreamRepository.kt` - Has own codec selection
- `TranscodingDiagnosticsViewModel.kt` - Hardcoded codec lists

**Plan**:
- [ ] Make `DeviceCapabilities` the single source of truth for all codec queries
- [ ] Inject `DeviceCapabilities` into `JellyfinStreamRepository` and `TranscodingDiagnosticsViewModel`
- [ ] Remove hardcoded SUPPORTED_CONTAINERS/CODECS lists from diagnostics
- [ ] Add unit tests for `DeviceCapabilities` codec detection

---

### A8. Add Adaptive Bitrate During Playback
**Priority**: Medium | **Effort**: 2-3 days

**Problem**: Quality changes are manual only. If network degrades during playback (e.g., WiFi signal drops), the app doesn't ask the server for a lower-quality transcode. HLS variant selection is left entirely to ExoPlayer without guidance.

**Plan**:
- [ ] Monitor ExoPlayer bandwidth estimates during playback
- [ ] Detect sustained buffering (>5 seconds) as a quality degradation signal
- [ ] Automatically request lower transcoding bitrate if buffering exceeds threshold
- [ ] Show user notification: "Quality reduced due to network conditions"
- [ ] Provide "Auto" quality mode that adjusts dynamically (vs. fixed quality)

---

## Phase B: Security Hardening (HIGH)

### B1. Remove API Tokens from URL Query Parameters
**Priority**: High | **Effort**: 1-2 days

**Problem**: Access tokens are appended as `?api_key=` query parameters in subtitle and Cast URLs. Query parameters are logged by proxies, load balancers, CDNs, and device logs (CWE-598).

**Files**:
- `ui/player/VideoPlayerViewModel.kt:1079` - Subtitle URLs with `api_key=`
- `ui/player/CastManager.kt` - Cast URLs with `api_key=`

**Plan**:
- [ ] For subtitle URLs: Use `Authorization: MediaBrowser Token="..."` header via OkHttp interceptor
- [ ] For Cast URLs: Document the trade-off (Cast receiver can't inject headers) and consider a local proxy approach
- [ ] Audit all other URL builders for token leakage
- [ ] Add `SecureLogger` filtering for URLs containing `api_key`

---

### B2. Restrict Cleartext HTTP to Private Networks Only
**Priority**: Medium | **Effort**: 0.5 days

**Problem**: `ConnectionOptimizer` generates HTTP fallback URLs for local servers. If DNS is hijacked, this could allow MITM on non-private networks.

**File**: `data/repository/ConnectionOptimizer.kt:77-130`

**Plan**:
- [ ] Detect network type before allowing HTTP fallback
- [ ] Only permit HTTP on RFC 1918 private address ranges (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)
- [ ] Warn users when connecting via HTTP to non-local addresses

---

## Phase C: Reliability & Error Handling (HIGH)

### C1. Add Progress Sync Resilience for Network Drops
**Priority**: High | **Effort**: 1-2 days

**Problem**: If network drops during playback, `PlaybackProgressManager` fails silently. Users lose their watch position.

**File**: `ui/player/PlaybackProgressManager.kt`

**Plan**:
- [ ] Queue pending progress updates in local storage (DataStore or Room)
- [ ] Retry sync with exponential backoff when network returns
- [ ] Show subtle indicator when progress sync is pending
- [ ] Flush queued updates on app foreground or network reconnect

---

### C2. Fix Cast Session Initialization Race Condition
**Priority**: High | **Effort**: 0.5 days

**Problem**: `initializationDeferred` in `CastManager` is checked/set without synchronization, allowing multiple concurrent initializations.

**File**: `ui/player/CastManager.kt:75-80`

**Plan**:
- [ ] Replace nullable `CompletableDeferred` with `Mutex`-guarded initialization
- [ ] Use `AtomicReference` or `@Volatile` for thread-safe state

---

### C3. Make Auth Interceptor Non-Blocking
**Priority**: Medium | **Effort**: 2-3 days

**Problem**: `Thread.sleep()` in backoff and `runBlocking` token refresh in `JellyfinAuthInterceptor` stall OkHttp dispatcher threads. During token refresh (every ~24 hours), all network requests queue up.

**File**: `network/JellyfinAuthInterceptor.kt:159`

**Plan**:
- [ ] Replace `Thread.sleep()` with OkHttp's built-in retry mechanism
- [ ] Pre-refresh tokens in a background coroutine before expiry
- [ ] Use a token-gating pattern: queue requests while refresh is in-flight, resume when done
- [ ] Add a mock WebServer test to verify non-blocking behavior

---

### C4. Improve Error Differentiation in GenerativeAiRepository
**Priority**: Medium | **Effort**: 0.5 days

**Problem**: Generic `catch (e: Exception)` blocks don't distinguish between quota limits (601), config not ready (606), download failures (605), and genuine errors.

**File**: `data/repository/GenerativeAiRepository.kt:88-101`

**Plan**:
- [ ] Map specific error codes (601, 605, 606) to distinct UI states
- [ ] Show "AI quota exceeded - try again later" vs "AI unavailable" vs "Downloading AI model..."
- [ ] Add retry with backoff for transient errors only

---

### C5. Fix PlaybackProgressManager Scope Lifecycle
**Priority**: Low | **Effort**: 0.5 days

**Problem**: `managerScope` in `PlaybackProgressManager` creates a `Job()` that is never cancelled. The scope outlives its usefulness when playback ends.

**File**: `ui/player/PlaybackProgressManager.kt:40-42`

**Plan**:
- [ ] Add `stop()` method that cancels the scope's job
- [ ] Call `stop()` from `VideoPlayerViewModel.onCleared()`
- [ ] Same pattern for `CastManager.managerScope`

---

## Phase D: UX Polish & Accessibility (MEDIUM)

### D1. Add Empty State Composables
**Priority**: Medium | **Effort**: 1 day

**Problem**: No visual distinction between "loading", "no content available", and "network error". Users see blank screens with no guidance.

**Files**: `ui/screens/HomeScreen.kt`, search screens, library screens

**Plan**:
- [ ] Create `EmptyStateComposable(icon, title, subtitle, action)` reusable component
- [ ] Add "No recently added items" state for home screen sections
- [ ] Add "No results found" state for search with query echo
- [ ] Add "Failed to load - Retry" state with action button
- [ ] Add "Library is empty" state for empty libraries

---

### D2. Add Retry Actions on Error Screens
**Priority**: Medium | **Effort**: 0.5 days

**Problem**: When API calls fail, there's no retry button. Users must navigate away and come back.

**Plan**:
- [ ] Add `Snackbar` with "Retry" action for recoverable errors
- [ ] Add full-screen retry state for screen-level load failures
- [ ] Implement in all ViewModels that load data on init

---

### D3. Add Content Descriptions for Accessibility
**Priority**: Medium | **Effort**: 1 day

**Problem**: Media card images and player controls lack `contentDescription` for TalkBack/screen readers.

**Files**: `ui/components/` (all media card files), `ui/player/VideoPlayerScreen.kt`

**Plan**:
- [ ] Add `contentDescription` to all poster/backdrop images: `"${item.name}, ${item.type}"`
- [ ] Add semantics to player controls (play, pause, seek, cast, quality, subtitles)
- [ ] Test with TalkBack enabled on a real device
- [ ] Respect system font scale in subtitle text sizing

---

### D4. Add Loading Skeletons
**Priority**: Low | **Effort**: 1-2 days

**Problem**: Home screen shows nothing until all sections load. Progressive loading with skeletons would feel faster.

**Plan**:
- [ ] Create `ShimmerEffect` modifier for skeleton placeholders
- [ ] Apply to hero carousel, media rows, and library grid while loading
- [ ] Show sections independently as they become available (not all-or-nothing)

---

## Phase E: Missing User Preferences (MEDIUM)

These are common Jellyfin client features that users expect but are currently missing or hardcoded.

### E1. Default Playback Quality Preference
- **Problem**: Each session starts at "Auto" quality. Users on limited bandwidth can't default to 720p.
- **Plan**: Add `defaultPlaybackQuality` setting (Auto, 480p, 720p, 1080p, 4K, Original)
- **File**: New setting in DataStore, consumed by `EnhancedPlaybackManager`

### E2. Preferred Audio Language Preference
- **Problem**: Audio track selection defaults to English (`VideoPlayerViewModel.kt:424-437`). Non-English users have to manually switch every time.
- **Plan**: Add `preferredAudioLanguages: List<String>` setting (ordered priority list)

### E3. Auto-Play Next Episode Toggle
- **Problem**: Auto-play is always on with 10-second countdown. No way to disable or change the timer.
- **Plan**: Add `autoPlayNextEpisode: Boolean` and `autoPlayCountdownSeconds: Int` (5/10/15/30) settings

### E4. Auto-Skip Intro/Outro Preference
- **Problem**: Intro/outro skip buttons appear but there's no auto-skip option.
- **File**: `ui/player/VideoPlayerViewModel.kt:1005-1032`
- **Plan**: Add `autoSkipIntro: Boolean` and `autoSkipOutro: Boolean` settings

### E5. Resume Playback Mode Preference
- **Problem**: Always resumes from saved position. Users may want "Ask me" or "Start from beginning".
- **Plan**: Add `resumePlaybackMode: ResumeMode` setting (Always, Ask, Never)

---

## Phase F: Code Quality & Technical Debt (Carried Forward)

These items are carried forward from the January 2026 plan with updated status.

### F1. Refactor Large Composables
**Priority**: Medium | **Effort**: 3-5 days

**Status**: Movies/TV Shows/Stuff screens already split. Remaining:

| File | Size | Action |
|------|------|--------|
| `VideoPlayerScreen.kt` | ~1,726 lines | Extract controls, overlays, gesture handlers, cast overlay |
| `HomeScreen.kt` | ~1,119 lines | Extract hero carousel, continue watching, recently added sections |
| `MainAppViewModel.kt` | ~50KB | Extract domain-specific methods into focused ViewModels |
| `TVEpisodeDetailScreen.kt` | ~45KB | Extract info sections, episode list, action buttons |

### F2. Synchronize JellyfinCache.isCached Memory Access
**Priority**: Low | **Effort**: 0.5 days

**File**: `data/cache/JellyfinCache.kt:207-214`
- Wrap `isCached` reads/removes in `synchronized(memoryCache)` blocks
- Add concurrent access test

### F3. Close Testing Gaps
**Priority**: Medium | **Effort**: 3-5 days

Missing test coverage:
- [ ] `JellyfinCache` - race safety, TTL, disk read/write
- [ ] `OfflineDownloadManager` - ID handling, encrypted URL retrieval
- [ ] `JellyfinAuthInterceptor` - mock WebServer for refresh behavior
- [ ] `EnhancedPlaybackManager` - Direct Play vs Transcoding decision logic
- [ ] `DeviceCapabilities` - codec detection accuracy
- [ ] `CastManager` - session lifecycle, reconnection

### F4. Fix Build Warnings
**Priority**: Low | **Effort**: 2-3 hours

Current: ~150 non-critical warnings remaining. Focus areas:
- Deprecated `hiltViewModel` imports
- Remaining unnecessary safe calls
- Deprecated `CastPlayer` constructor

---

## Phase G: Subtitle System Improvements (MEDIUM)

### G1. Support External Subtitles
**Priority**: Medium | **Effort**: 1-2 days

**Problem**: External subtitles are explicitly filtered out (`VideoPlayerViewModel.kt:1062`):
```kotlin
?.filter { stream -> !stream.isExternal }
```

**Plan**:
- [ ] Remove the external subtitle filter
- [ ] Build proper URLs for external subtitle files
- [ ] Test with .srt, .ass, .ssa files placed alongside media on server

### G2. Preserve ASS/SSA Subtitle Styling
**Priority**: Low | **Effort**: 2-3 days

**Problem**: All subtitle formats are converted to VTT (`VideoPlayerViewModel.kt:1070-1074`), losing ASS/SSA styling (colors, positioning, fonts).

**Plan**:
- [ ] Use ExoPlayer's native SSA/ASS renderer (`SubtitleDecoderFactory`) for embedded subtitles
- [ ] Fall back to VTT conversion only when native rendering isn't supported
- [ ] Add subtitle appearance customization (font, size, background, color)

### G3. Add Subtitle Sync Delay Setting
**Priority**: Low | **Effort**: 0.5 days

**Problem**: No way to adjust subtitle timing if they're out of sync.

**Plan**:
- [ ] Add ±5 second delay slider in player subtitle settings
- [ ] Apply offset via ExoPlayer's `SubtitleView.setSubtitleDelay()`

### G4. Add Subtitle Encoding Preference
**Priority**: Low | **Effort**: 0.5 days

**Problem**: Some subtitle files use non-UTF8 encodings. No UI for encoding selection.

**Plan**:
- [ ] Add "Subtitle Encoding" option in settings (UTF-8, ISO-8859-1, GBK, Shift_JIS)
- [ ] Apply encoding when loading external subtitle files

---

## Implementation Priority Order

### Tier 1: Critical (Do First)
| # | Item | Phase | Effort | Why Critical |
|---|------|-------|--------|--------------|
| 1 | Implement DeviceProfile | A1 | 3-5 days | Eliminates unnecessary transcoding |
| 2 | Fix network quality assessment | A2 | 1-2 days | Currently broken (hardcoded HIGH) |
| 3 | Send audio/subtitle stream indices | A4 | 1 day | Wrong audio track transcoded for multilingual content |
| 4 | Transcoding session lifecycle | A5 | 2-3 days | Server timeouts mid-video |

### Tier 2: High Priority
| # | Item | Phase | Effort | Impact |
|---|------|-------|--------|--------|
| 5 | Configurable bitrate thresholds | A3 | 1-2 days | Users can't control quality/bandwidth |
| 6 | Remove tokens from URLs | B1 | 1-2 days | Security vulnerability (CWE-598) |
| 7 | Progress sync resilience | C1 | 1-2 days | Users lose watch position on network drop |
| 8 | Cast initialization race fix | C2 | 0.5 days | Potential crash on Cast connect |
| 9 | Non-blocking auth interceptor | C3 | 2-3 days | Network stalls during token refresh |

### Tier 3: Medium Priority
| # | Item | Phase | Effort | Impact |
|---|------|-------|--------|--------|
| 10 | Transcoding fallback detection | A6 | 1-2 days | Unreliable error detection |
| 11 | Unify codec detection | A7 | 1-2 days | Inconsistent codec decisions |
| 12 | Adaptive bitrate during playback | A8 | 2-3 days | No quality adjustment on poor network |
| 13 | Empty states & retry actions | D1+D2 | 1.5 days | Blank screens confuse users |
| 14 | Content descriptions | D3 | 1 day | Accessibility compliance |
| 15 | User preferences (E1-E5) | E | 3-5 days | Missing standard client features |
| 16 | External subtitles | G1 | 1-2 days | Common user request |
| 17 | Testing gaps | F3 | 3-5 days | Code confidence |

### Tier 4: Low Priority
| # | Item | Phase | Effort | Impact |
|---|------|-------|--------|--------|
| 18 | Restrict HTTP to private networks | B2 | 0.5 days | Edge case security |
| 19 | AI error differentiation | C4 | 0.5 days | Better AI error messages |
| 20 | Scope lifecycle fixes | C5 | 0.5 days | Minor memory leak |
| 21 | Refactor large composables | F1 | 3-5 days | Developer experience |
| 22 | Loading skeletons | D4 | 1-2 days | Perceived performance |
| 23 | Subtitle styling/sync/encoding | G2-G4 | 3-4 days | Advanced subtitle features |
| 24 | Cache isCached sync | F2 | 0.5 days | Rare race condition |
| 25 | Build warnings | F4 | 2-3 hours | Code hygiene |

**Total estimated effort**: ~40-55 days across all tiers

---

## Progress Update (2026-02-04)

### Completed (January 2026)
- ✅ **Offline downloads**: `startDownload` suspend/returns real IDs, encrypted URL uses `first()` with timeout
- ✅ **Cache initialization/thread-safety**: `ensureCacheDir()`, synchronized blocks, ApplicationScope
- ✅ **Auto quality selection**: Clears track overrides for adaptive selection
- ✅ **Screen refactoring**: Movies/TV Shows/Stuff split into smaller composables
- ✅ **Warning cleanup**: Safe-call removal, redundant casts, deprecated Cast seek
- ✅ **Transcoding position reset**: Position preserved across codec flushes with retry limits
- ✅ **Transcoding diagnostics**: Tool to identify which videos need transcoding

### Active (February 2026)
- 🔄 Phase A: Transcoding system overhaul (this document)
- 🔄 Phase E: User preferences for playback control

### Pending
- 🔜 All items in Tiers 1-4 above

---

## Architecture Notes

### Transcoding Decision Flow (Current)
```
User hits Play
  → EnhancedPlaybackManager.determinePlaybackMethod()
    → Check PlaybackInfo from server (no DeviceProfile sent)
    → Check DeviceCapabilities locally
    → Decide Direct Play vs Transcode
  → JellyfinStreamRepository.buildStreamUrl()
    → Hardcoded codec/bitrate params
    → No AudioStreamIndex
    → No session heartbeat
  → ExoPlayer loads stream
    → If error → string-match fallback to transcode (1 attempt)
```

### Transcoding Decision Flow (Target)
```
User hits Play
  → EnhancedPlaybackManager.determinePlaybackMethod()
    → Send DeviceProfile to server
    → Server returns optimal PlaybackInfo
    → Check measured network quality (not hardcoded)
    → Decide method with user's quality preferences
  → JellyfinStreamRepository.buildStreamUrl()
    → Include AudioStreamIndex + SubtitleStreamIndex
    → Use user's bitrate preference
    → Start session heartbeat
  → ExoPlayer loads stream
    → Monitor bandwidth continuously
    → If buffering → auto-reduce quality
    → If error → detect by error code, try lower quality first, then transcode
    → Report playback quality feedback to server
  → On stop → send session cleanup
```

### Key Files for Transcoding Work
| File | Role |
|------|------|
| `data/playback/EnhancedPlaybackManager.kt` | Playback method decision engine |
| `data/repository/JellyfinStreamRepository.kt` | Stream URL builder |
| `data/DeviceCapabilities.kt` | Hardware codec detection |
| `ui/player/VideoPlayerViewModel.kt` | Player lifecycle, fallback, quality |
| `ui/player/PlaybackProgressManager.kt` | Progress reporting to server |
| `ui/viewmodel/TranscodingDiagnosticsViewModel.kt` | Diagnostic tool |

---

## Notes

- The previous archived plan (`docs/archive/IMPROVEMENT_PLAN_DEC30_2025.md`) contains older issues that are resolved.
- Transcoding-specific documentation: `TRANSCODING_FIX_SUMMARY.md`, `TRANSCODING_DIAGNOSTICS_GUIDE.md`, `TRANSCODING_FIX_DIAGRAM.md`
- This document tracks technical debt and code quality. For user-facing bugs, see [KNOWN_ISSUES.md](../KNOWN_ISSUES.md).

---

## Related Documentation

- **[KNOWN_ISSUES.md](../KNOWN_ISSUES.md)** - User-facing bugs with workarounds and fix status
- **[ROADMAP.md](../ROADMAP.md)** - Future features and development roadmap
- **[UPGRADE_PATH.md](../UPGRADE_PATH.md)** - Dependency upgrade strategy
- **[CURRENT_STATUS.md](../CURRENT_STATUS.md)** - Current feature status and platform support
- **[CLAUDE.md](../CLAUDE.md)** - Development guidelines and architecture details
- **[TESTING_GUIDE.md](TESTING_GUIDE.md)** - Testing patterns and best practices
- **[TRANSCODING_FIX_SUMMARY.md](../TRANSCODING_FIX_SUMMARY.md)** - Position reset fix details
- **[TRANSCODING_DIAGNOSTICS_GUIDE.md](../TRANSCODING_DIAGNOSTICS_GUIDE.md)** - Diagnostics tool usage
