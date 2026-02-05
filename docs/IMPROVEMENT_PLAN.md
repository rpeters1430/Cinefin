# Jellyfin Android Improvement Plan

**Last Updated**: February 4, 2026
**Scope**: Full codebase audit covering transcoding/playback, security, accessibility, UX, and code quality
**Previous Version**: January 30, 2026 (see docs/archive/ for older plans)

> **Note**: For user-facing bugs with workarounds, see [KNOWN_ISSUES.md](../KNOWN_ISSUES.md). For feature roadmap, see [ROADMAP.md](../ROADMAP.md). This document focuses on technical debt, architecture improvements, and code quality.

---

## Table of Contents

1. [Recently Completed](#recently-completed-)
2. [Phase A: Transcoding & Playback System Overhaul](#phase-a-transcoding--playback-system-overhaul-completed-) ✅ **COMPLETED**
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

**Completion Date**: February 4, 2026 (Transcoding System Overhaul Part 2 - User Preferences & Adaptive Bitrate)

### Build Errors Fixed ✅
**Status**: All compilation errors resolved

**Issues fixed:**
- ✅ Jellyfin SDK API changes in `JellyfinDeviceProfile.kt`
  - Removed `conditions` parameter from `DirectPlayProfile` (no longer accepted)
  - Added `conditions = emptyList()` to `ContainerProfile` (now required)
  - Changed bitrate parameters from `Long` to `Int`
- ✅ Missing dependencies in `NetworkModule.kt`
  - Added `ConnectivityChecker` and `PlaybackPreferencesRepository` to `EnhancedPlaybackManager`
  - Added missing import for `PlaybackPreferencesRepository`
- ✅ Function visibility conflict in `CastRemoteScreen.kt`
  - Changed `formatTime()` from public to private to avoid overload conflicts
- ✅ Missing import in `SearchScreen.kt`
  - Added import for `SearchResultsContent` from home package

**Files fixed:**
- `data/model/JellyfinDeviceProfile.kt`
- `di/NetworkModule.kt`
- `ui/player/CastRemoteScreen.kt`
- `ui/screens/SearchScreen.kt`

### A3. Configurable Bitrate Thresholds ✅
**Status**: Fully implemented and integrated into app

**What was completed:**
- ✅ Created `PlaybackPreferencesRepository` with DataStore persistence
- ✅ Built complete Settings UI with dropdown menus for all preferences
- ✅ Integrated into `EnhancedPlaybackManager` for real-time bitrate decisions
- ✅ Wired into navigation graph (accessible from Profile → Playback Settings)
- ✅ Created ViewModel with reactive StateFlow for instant UI updates

**User-configurable settings:**
- **WiFi Max Bitrate**: 120 Mbps / 80 Mbps / 40 Mbps / 20 Mbps / 10 Mbps / 5 Mbps / 3 Mbps
- **Cellular Max Bitrate**: 120 Mbps / 80 Mbps / 40 Mbps / 20 Mbps / 10 Mbps / 5 Mbps / 3 Mbps
- **Transcoding Quality**: Auto / Maximum / High / Medium / Low
- **Audio Channels**: Auto / Stereo / 5.1 Surround / 7.1 Surround

**Files created/modified:**
- `data/preferences/PlaybackPreferencesRepository.kt` (NEW)
- `ui/screens/settings/PlaybackSettingsScreen.kt` (NEW)
- `ui/viewmodel/PlaybackPreferencesViewModel.kt` (NEW)
- `data/playback/EnhancedPlaybackManager.kt` (MODIFIED - now uses user preferences)
- `ui/navigation/ProfileNavGraph.kt` (MODIFIED - added route)
- `ui/navigation/NavRoutes.kt` (MODIFIED - added route)
- `di/NetworkModule.kt` (MODIFIED - added dependency injection)

### A8. Adaptive Bitrate Monitoring During Playback ✅
**Status**: Fully implemented with intelligent quality recommendations

**What was completed:**
- ✅ Created `AdaptiveBitrateMonitor` singleton that monitors ExoPlayer in real-time
- ✅ Detects sustained buffering (>5 second threshold)
- ✅ Tracks multiple buffering events (3+ in 30-second window)
- ✅ Monitors ExoPlayer bandwidth estimates
- ✅ Generates quality recommendations with severity levels (Low/Medium/High)
- ✅ Respects user quality mode (only acts on AUTO, not manual selection)
- ✅ Built non-intrusive notification card UI
- ✅ Automatic playback restart at current position with new quality
- ✅ 1-minute cooldown between recommendations to prevent spam
- ✅ Analytics tracking for user decisions

**How it works:**
1. Monitor starts automatically when playback reaches READY state
2. Checks playback state every 1 second
3. Detects buffering patterns that indicate network issues
4. Shows recommendation card at bottom of screen
5. User can "Switch Quality" (restarts at new quality) or "Not Now" (dismisses)
6. Only suggests downgrades when on AUTO quality mode
7. Stops monitoring when player is released

**Files created/modified:**
- `data/playback/AdaptiveBitrateMonitor.kt` (NEW - 230 lines)
- `ui/player/VideoPlayerViewModel.kt` (MODIFIED - added monitoring lifecycle)
- `ui/player/VideoPlayerDialogs.kt` (MODIFIED - added notification UI)
- `ui/player/VideoPlayerScreen.kt` (MODIFIED - display notification)
- `ui/player/VideoPlayerActivity.kt` (MODIFIED - wire callbacks)

**Completion Date**: February 2026 (Transcoding System Overhaul Part 1)

1) **Dynamic DeviceProfile Handshake** - Implemented Jellyfin DeviceProfile spec using real hardware capabilities. Profile is now sent to server during playback handshake to eliminate unnecessary transcoding.
   - Files: `data/model/JellyfinDeviceProfile.kt`, `data/repository/JellyfinRepository.kt`

2) **Intelligent Network Awareness** - Enhanced `ConnectivityChecker` with bandwidth estimation and meteredness detection. `EnhancedPlaybackManager` now uses these metrics for bitrate decisions.
   - Files: `network/ConnectivityChecker.kt`, `data/playback/EnhancedPlaybackManager.kt`

3) **Multilingual Transcoding Support** - Added `AudioStreamIndex` and `SubtitleStreamIndex` to transcoding URLs. Changing tracks mid-transcode now restarts playback with the correct server-side stream.
   - Files: `data/repository/JellyfinStreamRepository.kt`, `ui/player/VideoPlayerViewModel.kt`

4) **Session Lifecycle & Recovery** - Added session recovery for 404/401 errors in `PlaybackProgressManager`. Periodic progress reports now double as session heartbeats.
   - File: `ui/player/PlaybackProgressManager.kt`

5) **Unified Codec Intelligence** - Centralized all codec support logic in `DeviceCapabilities.kt`. Updated `TranscodingDiagnosticsViewModel` to use this single source of truth.
   - Files: `data/DeviceCapabilities.kt`, `ui/viewmodel/TranscodingDiagnosticsViewModel.kt`

6) **Structured Fallback Detection** - Replaced string-matching error detection with ExoPlayer error codes (`DECODER_INIT_FAILED`, etc.) for more reliable transcoding fallbacks.
   - File: `ui/player/VideoPlayerViewModel.kt`

**Completion Date**: January 2026

7) **Offline downloads hanging bug** - Replaced infinite `collect` with `first()` + timeout
8) **Offline download ID mismatch** - Made `startDownload` suspend, returns real ID
9) **Cache directory initialization race condition** - Added `ensureCacheDir()` guard
10) **Memory cache thread safety** - Added `synchronized` blocks around memoryCache
11) **Video player auto quality selection** - "Auto" clears track overrides for adaptive selection
12) **Transcoding position reset fix** - Position preservation across codec flushes with retry limits

---

## Phase A: Transcoding & Playback System Overhaul (COMPLETED ✅)

All major transcoding improvements have been implemented!

### ~~A3. Make Bitrate Thresholds Configurable~~ ✅ COMPLETED
**Status**: Fully implemented and integrated

**Implemented**:
- ✅ Add "Maximum streaming bitrate" setting (WiFi / Cellular separate)
- ✅ Add "Transcoding quality" preference (Auto, Maximum, High, Medium, Low)
- ✅ Add "Audio channels" preference (Auto, Stereo, 5.1, 7.1)
- ✅ Store in DataStore with reactive Flow updates
- ✅ Inject into `EnhancedPlaybackManager` for bitrate decisions
- ✅ Expose in Settings screen under "Playback Settings"
- ✅ Wire into navigation graph

### ~~A8. Add Adaptive Bitrate During Playback~~ ✅ COMPLETED
**Status**: Fully implemented with user notification system

**Implemented**:
- ✅ Monitor ExoPlayer playback state every second
- ✅ Detect sustained buffering (>5 seconds threshold)
- ✅ Detect multiple buffering events (3+ in 30 second window)
- ✅ Track bandwidth estimates from ExoPlayer
- ✅ Recommend quality downgrades with severity levels (Low/Medium/High)
- ✅ Only acts when user is on AUTO quality mode (respects manual selection)
- ✅ UI notification card with accept/dismiss actions
- ✅ Automatic playback restart at current position with new quality
- ✅ Analytics tracking for user acceptance/dismissal
- ✅ Cooldown period between recommendations (1 minute minimum)

---

## Phase B: Security Hardening (HIGH)

### B1. Remove API Tokens from URL Query Parameters
**Priority**: High | **Effort**: 1-2 days

**Problem**: Access tokens are appended as `?api_key=` query parameters in subtitle and Cast URLs. 

**Plan**:
- [ ] For subtitle URLs: Use `Authorization: MediaBrowser Token="..."` header via OkHttp interceptor
- [ ] For Cast URLs: Document the trade-off (Cast receiver compatibility) or use local proxy

---

## Phase C: Reliability & Error Handling (HIGH)

### C1. Add Progress Sync Resilience for Network Drops
**Priority**: High | **Effort**: 1-2 days

**Problem**: If network drops during playback, progress is lost.

**Plan**:
- [ ] Queue pending progress updates in local storage (Room or DataStore)
- [ ] Flush queued updates on network reconnect

---

## Phase D: UX Polish & Accessibility (MEDIUM)

### D1. Add Empty State Composables
**Priority**: Medium | **Effort**: 1 day
- [ ] Create `EmptyStateComposable` reusable component
- [ ] Implement across Search and Library screens

### D3. Add Content Descriptions for Accessibility
**Priority**: Medium | **Effort**: 1 day
- [ ] Add `contentDescription` to all media cards and player controls for TalkBack support

---

## Phase E: Missing User Preferences (MEDIUM)

### E1. Default Playback Quality Preference
### E2. Preferred Audio Language Preference
### E3. Auto-Play Next Episode Toggle
### E5. Resume Playback Mode Preference (Always/Ask/Never)

---

## Phase F: Code Quality & Technical Debt

### F1. Refactor Large Composables
- Remaining: `VideoPlayerScreen.kt` (~1,700 lines), `HomeScreen.kt` (~1,100 lines)

---

## Implementation Priority Order (Updated)

### Tier 1: High Priority
1. ~~**Configurable bitrate thresholds** (A3)~~ ✅ COMPLETED
2. **Remove tokens from URLs** (B1) - Security fix
3. **Progress sync resilience** (C1) - Data integrity
4. **User preferences** (Phase E) - Standard client features

### Tier 2: Medium Priority
5. ~~**Adaptive bitrate during playback** (A8)~~ ✅ COMPLETED
6. **Empty states & retry actions** (D1+D2) - UX polish
7. **Refactor large composables** (F1) - Maintainability (IN PROGRESS: HomeScreen & VideoPlayerScreen partially split)

---

## Summary of Progress

### 🎉 Completed Phases
- **Phase A: Transcoding & Playback System Overhaul** ✅ **100% COMPLETE**
  - All transcoding improvements implemented
  - User preferences for bitrate control
  - Adaptive quality recommendations during playback
  - Intelligent network-aware decisions

### 🚧 In Progress
- **Phase F: Code Quality & Technical Debt** 🔄 **PARTIAL**
  - HomeScreen.kt partially refactored into smaller components
  - VideoPlayerScreen.kt partially refactored into smaller components

### 📊 Overall Status
- **Build Status**: ✅ All files compile successfully
- **Test Coverage**: Existing tests passing
- **New Files Created**: 6 (repositories, ViewModels, UI components)
- **Files Modified**: 10+ (integration across codebase)

### 🎯 Next Recommended Priorities
Based on user impact and technical debt:

1. **Security (B1)**: Remove API tokens from URL query parameters
2. **Reliability (C1)**: Add progress sync resilience for network drops
3. **UX (D1-D3)**: Empty states, retry actions, accessibility improvements
4. **User Preferences (E1-E5)**: Additional playback preferences
5. **Code Quality (F1)**: Complete large composable refactoring

---

## Related Documentation

- [KNOWN_ISSUES.md](../KNOWN_ISSUES.md)
- [ROADMAP.md](../ROADMAP.md)
- [TRANSCODING_FIX_SUMMARY.md](../TRANSCODING_FIX_SUMMARY.md)
- [TESTING_GUIDE.md](TESTING_GUIDE.md) - ViewModel testing patterns and best practices