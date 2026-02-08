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

**Completion Date**: February 5, 2026 (User Preferences - Phase E)

### Phase E: User Preferences ✅
**Status**: Fully implemented with extended PlaybackPreferences system

**What was completed:**
- ✅ **E1**: Default playback quality preference (already existed as TranscodingQuality)
- ✅ **E2**: Preferred audio language preference with common language selection
- ✅ **E3**: Auto-play next episode toggle (enabled by default)
- ✅ **E5**: Resume playback mode preference (Always/Ask/Never)

**Implementation details:**
- Extended `PlaybackPreferencesRepository` with three new preferences:
  - `preferredAudioLanguage: String?` - ISO 639-2/T language codes (eng, spa, fra, etc.)
  - `autoPlayNextEpisode: Boolean` - Default: true
  - `resumePlaybackMode: ResumePlaybackMode` - Default: ALWAYS
- Added `ResumePlaybackMode` enum with three values: ALWAYS, ASK, NEVER
- Updated `PlaybackPreferencesViewModel` with setter methods for all new preferences
- Enhanced `PlaybackSettingsScreen` with new UI sections:
  - "Behavior" section with auto-play toggle and resume mode dropdown
  - Language dropdown with 13 common languages + "No preference" option
  - Modern Material 3 design with consistent styling

**Files modified:**
- `data/preferences/PlaybackPreferencesRepository.kt` - Extended with new preferences
- `ui/viewmodel/PlaybackPreferencesViewModel.kt` - Added setter methods
- `ui/screens/settings/PlaybackSettingsScreen.kt` - Added UI controls

**Preferences Wired Into Playback Logic** ✅
- ✅ Preferred audio language: Auto-selects matching audio track on playback start
- ✅ Auto-play next episode: Conditionally starts countdown when playback ends
- ✅ Resume playback mode: Controls whether to resume from saved position or start from beginning
  - ALWAYS: Auto-resume from saved position (default)
  - NEVER: Always start from beginning
  - ASK: Show dialog (TODO: UI layer implementation needed)

**Files wired:**
- `ui/player/VideoPlayerViewModel.kt` - All three preferences now control playback behavior

**Completion Date**: February 5, 2026 (User Preferences - Phase E Complete)

**Completion Date**: February 5, 2026 (Progress Sync Resilience & Infrastructure)

### Progress Sync Resilience ✅
**Status**: Fully implemented with offline queuing and background synchronization

**What was completed:**
- ✅ Created `OfflineProgressRepository` using DataStore for local persistence of pending updates.
- ✅ Implemented `OfflineProgressSyncWorker` with WorkManager for robust background syncing.
- ✅ Integrated into `JellyfinUserRepository` to automatically queue updates on network failure.
- ✅ Wired `MainAppViewModel` to trigger immediate synchronization upon network reconnection.
- ✅ Added `hilt-work` and `work-runtime-ktx` dependencies for reliable background processing.

### Build & Test Stability ✅
**Status**: All core unit tests and compilation errors resolved

**Issues fixed:**
- ✅ Fixed `CastManager` missing helper functions and incompatible `MediaStatus` access.
- ✅ Updated `JellyfinStreamRepositoryTest`, `EnhancedPlaybackManagerTest`, and `JellyfinRepositoryTest` constructors to match new architecture.
- ✅ Resolved `NoSuchMethodError` in Compose runtime by aligning BOM and Kotlin versions.
- ✅ Created `ThemeComposeTest` to verify theme stability across dependency updates.

**Files created/modified:**
- `data/repository/OfflineProgressRepository.kt` (NEW)
- `data/worker/OfflineProgressSyncWorker.kt` (NEW)
- `data/repository/JellyfinUserRepository.kt` (MODIFIED)
- `ui/viewmodel/MainAppViewModel.kt` (MODIFIED)
- `app/build.gradle.kts` & `libs.versions.toml` (MODIFIED)

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

### B1. Remove API Tokens from URL Query Parameters ✅ COMPLETED
**Status**: Fully implemented (February 2026)
**Priority**: High | **Effort**: 1-2 days

**Problem**: Access tokens were appended as `?api_key=` query parameters in URLs, exposing them in logs and network traffic (CWE-598).

**Solution Implemented**:
- [x] Subtitle URLs: Use `Authorization: MediaBrowser Token="..."` header via OkHttp interceptor
- [x] Stream URLs: Authentication handled via OkHttp interceptor headers (X-Emby-Token)
- [x] Image URLs: No authentication in query parameters
- [x] Cast URLs: Removed `addAuthTokenToUrl()` function and all calls to it
- [x] Cast Image URLs: Direct URLs without tokens (requires server to allow unauthenticated image access)

**Notes**:
- All API requests now use header-based authentication via `JellyfinAuthInterceptor`
- Cast receivers fetch images directly and cannot use custom headers
- Servers must allow unauthenticated access to `/Items/{id}/Images/*` endpoints for Cast artwork
- Future enhancement: Local proxy for Cast image authentication if needed

**Files Modified**:
- `ui/player/CastManager.kt` - Removed token injection from Cast image URLs
- All URLs now rely on header-based authentication

---

## Phase C: Reliability & Error Handling (HIGH)

### C1. Add Progress Sync Resilience for Network Drops ✅ COMPLETED
**Status**: Fully implemented with local queuing and background flush.

---

## Phase D: UX Polish & Accessibility (MEDIUM)

### D1. Add Empty State Composables ✅ COMPLETED
**Status**: Fully implemented (February 2026)
**Priority**: Medium | **Effort**: 1 day

**Implemented**:
- [x] Created `EmptyStateComposable` reusable component in `ui/components/`
- [x] Supports multiple types (Info, Error, NoResults) with appropriate styling
- [x] Configurable icon, title, description, and optional action button
- [x] Follows Material 3 design principles
- [x] Implemented in SearchResultsContent (enhanced with icon and description)
- [x] Implemented in FavoritesScreen (replaced inline empty state)
- [x] Implemented in LibraryScreen (replaced simple text)
- [x] Implemented in AudioQueueScreen (replaced inline empty state)

**Files Created**:
- `ui/components/EmptyStateComposable.kt` - Reusable empty state component

**Files Modified**:
- `ui/screens/home/SearchResultsContent.kt` - Enhanced empty state with icon
- `ui/screens/FavoritesScreen.kt` - Uses EmptyStateComposable
- `ui/screens/LibraryScreen.kt` - Uses EmptyStateComposable
- `ui/screens/AudioQueueScreen.kt` - Uses EmptyStateComposable

**Note**: Many screens (MoviesScreen, TVEpisodesScreen, etc.) already have custom empty states using `ExpressiveEmptyState` or similar components.

### D3. Add Content Descriptions for Accessibility
**Priority**: Medium | **Effort**: 1 day
- [ ] Add `contentDescription` to all media cards and player controls for TalkBack support

---

## Phase E: Missing User Preferences ✅ COMPLETED

All standard user preference features have been implemented in PlaybackSettingsScreen:
- ✅ **E1**: Default playback quality (TranscodingQuality enum - Auto/Maximum/High/Medium/Low)
- ✅ **E2**: Preferred audio language (13 common languages with ISO 639-2/T codes)
- ✅ **E3**: Auto-play next episode toggle (boolean, default: enabled)
- ✅ **E5**: Resume playback mode (Always/Ask/Never enum)

**Note**: These preferences are now stored in DataStore and exposed in the UI. The next step is to wire them into `VideoPlayerViewModel` to control actual playback behavior (auto-play, resume, audio track selection).

---

## Phase F: Code Quality & Technical Debt

### F1. Refactor Large Composables
- Remaining: `VideoPlayerScreen.kt` (~1,700 lines), `HomeScreen.kt` (~1,100 lines)

---

## Implementation Priority Order (Updated)

### Tier 1: High Priority ✅ ALL COMPLETED
1. ~~**Configurable bitrate thresholds** (A3)~~ ✅ COMPLETED
2. ~~**Remove tokens from URLs** (B1)~~ ✅ COMPLETED - Security fix
3. ~~**Progress sync resilience** (C1)~~ ✅ COMPLETED - Data integrity
4. ~~**User preferences** (Phase E)~~ ✅ COMPLETED - Standard client features

### Tier 2: Medium Priority
5. ~~**Adaptive bitrate during playback** (A8)~~ ✅ COMPLETED
6. ~~**Empty states** (D1)~~ ✅ COMPLETED - UX polish
7. **Accessibility** (D3) - Content descriptions for TalkBack
8. **Refactor large composables** (F1) - Maintainability (IN PROGRESS: HomeScreen & VideoPlayerScreen partially split)

---

## Summary of Progress

### 🎉 Completed Phases
- **Phase A: Transcoding & Playback System Overhaul** ✅ **100% COMPLETE**
  - All transcoding improvements implemented
  - User preferences for bitrate control
  - Adaptive quality recommendations during playback
  - Intelligent network-aware decisions

- **Phase B: Security Hardening** ✅ **COMPLETE**
  - Removed API tokens from URL query parameters (CWE-598 fixed)
  - All authentication now uses header-based tokens

- **Phase C: Reliability & Error Handling** ✅ **COMPLETE**
  - Progress sync resilience with offline queuing
  - Background synchronization via WorkManager

- **Phase D: UX Polish** ✅ **PARTIAL COMPLETE**
  - D1: Empty state composables ✅
  - D3: Accessibility (content descriptions) - Remaining

- **Phase E: User Preferences** ✅ **COMPLETE**
  - Preferred audio language selection
  - Auto-play next episode toggle
  - Resume playback mode (Always/Ask/Never)
  - All preferences stored in DataStore with reactive UI

### 🚧 In Progress
- **Phase F: Code Quality & Technical Debt** 🔄 **PARTIAL**
  - HomeScreen.kt partially refactored into smaller components
  - VideoPlayerScreen.kt partially refactored into smaller components

### 📊 Overall Status
- **Build Status**: ✅ All files compile successfully
- **Test Coverage**: Existing tests passing
- **New Files Created**: 10+ (repositories, ViewModels, UI components, workers)
- **Files Modified**: 20+ (integration across codebase)

### 🎯 Next Recommended Priorities
Based on user impact and technical debt:

1. **Accessibility (D3)**: Add content descriptions for TalkBack support (~1 day)
2. **Wire Preferences**: Connect Phase E preferences to VideoPlayerViewModel for actual playback control (~1 day)
3. **Code Quality (F1)**: Complete large composable refactoring (~3-5 days)

---

## Related Documentation

- [KNOWN_ISSUES.md](../KNOWN_ISSUES.md)
- [ROADMAP.md](../ROADMAP.md)
- [TRANSCODING_FIX_SUMMARY.md](../TRANSCODING_FIX_SUMMARY.md)
- [TESTING_GUIDE.md](TESTING_GUIDE.md) - ViewModel testing patterns and best practices
