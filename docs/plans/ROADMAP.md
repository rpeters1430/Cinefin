# Jellyfin Android - Roadmap

**Last verified on**: 2026-04-23

> **Quick Links**: [Feature Status](CURRENT_STATUS.md) | [Known Issues](../features/KNOWN_ISSUES.md) | [Upgrade Path](UPGRADE_PATH.md)

A clear, actionable improvement plan for the Jellyfin Android client.

---

## Current Status

For a comprehensive snapshot of currently working, partially working, and non-implemented features, please refer to the **[CURRENT_STATUS.md](CURRENT_STATUS.md)** document.

---

## Phase 1: Complete Core Features

### 1.1 Music Background Playback
**Priority**: High | **Effort**: 5-7 days

Tasks:
- [ ] Complete `AudioService.kt` MediaSession integration
- [ ] Add notification controls (play/pause, skip, album art)
- [ ] Add lock screen controls
- [ ] Connect `MiniPlayer.kt` to audio service
- [ ] Implement queue management (shuffle, repeat)

Files: `ui/player/audio/AudioService.kt`, `ui/components/MiniPlayer.kt`, `ui/screens/NowPlayingScreen.kt`

### 1.2 Offline Downloads — COMPLETE ✅
**Status**: Verified complete (February 2026) per [CURRENT_STATUS truth table](CURRENT_STATUS.md#feature-truth-table-canonical)

Completed:
- [x] `OfflineDownloadManager.kt` core download logic
- [x] WorkManager background downloads
- [x] Download progress tracking + notifications
- [x] Offline playback routing in `VideoPlayerViewModel`
- [x] Storage management UI + delete/cleanup
- [x] Wi-Fi-only download option

Follow-up reliability fixes are tracked in [IMPROVEMENT_PLAN §Phase C](IMPROVEMENT_PLAN.md#phase-c-reliability--error-handling-high).

### 1.3 Chromecast - COMPLETE
**Status**: Verified and enhanced (January 2026)

Completed:
- [x] Device discovery and connection
- [x] Video casting with subtitles
- [x] Play/pause/stop controls
- [x] **Seek bar** during cast playback (new)
- [x] **Volume control** slider (new)
- [x] **Position/duration tracking** with real-time updates (new)
- [x] Session persistence and auto-reconnect

Files: `ui/player/CastManager.kt`, `ui/player/VideoPlayerScreen.kt`

### 1.4 Picture-in-Picture - COMPLETE
**Status**: Verified complete (January 2026)

Completed:
- [x] Manual PiP entry (button in player)
- [x] Auto-enter on home button (Android 8-11)
- [x] Custom remote actions (play/pause, skip ±30s)
- [x] TV PiP support
- [x] Version-specific handling (SDK 26-36)
- [x] Unit tests for PipActionReceiver

Files: `ui/player/VideoPlayerActivity.kt`, `ui/player/PipActionReceiver.kt`

---

## Phase 1.5: Transcoding & Playback System Overhaul — COMPLETED ✅

> **Full Details**: [IMPROVEMENT_PLAN.md - Phase A](IMPROVEMENT_PLAN.md#phase-a-transcoding--playback-system-overhaul-completed-)

The transcoding system works for basic playback but has architectural gaps that cause unnecessary transcoding and degrade quality. This phase addresses the critical server communication and decision-making issues.

### 1.5.1 DeviceProfile & Server Communication
**Priority**: Critical | **Effort**: 5-7 days

Tasks:
- [x] Implement Jellyfin DeviceProfile specification (send device capabilities to server)
- [x] Fix broken network quality assessment (currently hardcoded to HIGH)
- [x] Send AudioStreamIndex/SubtitleStreamIndex to server for multilingual content
- [x] Implement transcoding session lifecycle (heartbeat, cleanup)

Files: `data/DeviceCapabilities.kt`, `data/repository/JellyfinStreamRepository.kt`, `data/playback/EnhancedPlaybackManager.kt`

### 1.5.2 Quality Control & Adaptivity
**Priority**: High | **Effort**: 3-5 days

Tasks:
- [x] Make bitrate thresholds configurable (WiFi/Cellular/LAN separate)
- [x] Add default playback quality user preference
- [x] Add adaptive bitrate during playback (detect buffering, auto-reduce quality)
- [x] Unify codec detection logic (3 separate implementations → 1 source of truth)

Files: `data/playback/EnhancedPlaybackManager.kt`, `ui/player/VideoPlayerViewModel.kt`, `data/DeviceCapabilities.kt`

### 1.5.3 Error Recovery Improvements
**Priority**: Medium | **Effort**: 2-3 days

Tasks:
- [x] Replace string-matching fallback detection with ExoPlayer error codes
- [x] Distinguish codec errors vs bitrate errors vs resolution errors
- [x] Allow multiple fallback attempts (currently limited to 1)
- [x] Try lower quality before switching to full transcoding

Files: `ui/player/VideoPlayerViewModel.kt`

---

## Phase 1.6: Subtitle & Preference Improvements

### 1.6.1 Subtitle System
**Priority**: Medium | **Effort**: 3-5 days

Tasks:
- [ ] Enable external subtitle support (currently filtered out)
- [ ] Preserve ASS/SSA styling instead of converting everything to VTT
- [ ] Add subtitle sync delay setting (±5 seconds)
- [ ] Add subtitle encoding preference

Files: `ui/player/VideoPlayerViewModel.kt`

### 1.6.2 Playback Preferences
**Priority**: Medium | **Effort**: 3-5 days

Tasks:
- [x] Add preferred audio language setting
- [x] Add auto-play next episode toggle with configurable countdown
- [ ] Add auto-skip intro/outro preference
- [x] Add resume playback mode (Always/Ask/Never)

Files: `ui/player/VideoPlayerViewModel.kt`, new DataStore preferences

Status note:
- Playback preferences are now mostly complete.
- Remaining gap: auto-skip intro/outro preference and UI behavior wiring.

---

## New Priority List (Q2 2026 Refresh)

Based on current shipped capabilities and open reliability gaps, this is the recommended near-term execution order:

1. **Music background playback completion** (MediaSession + notification/lockscreen controls)
2. **Auth interceptor non-blocking refresh** (remove `runBlocking` + `Thread.sleep` path)
3. **Android TV D-pad hardening** (focus dead-ends and remote-only journeys)
4. **Offline downloads reliability validation** (OEM background limits + long-run retries)
5. **Subtitle system upgrades** (external subtitles, ASS/SSA styling, sync delay)
6. **Accessibility and empty states sweep** (TalkBack content descriptions + retry UX)

---

## Phase 2: Android TV Polish

### 2.1 D-pad Navigation Audit
**Priority**: High for TV | **Effort**: 3-5 days

Tasks:
- [ ] Test all TV screens with D-pad only (no touch)
- [ ] Fix focus indicators visibility
- [ ] Fix navigation dead-ends
- [ ] Verify initial focus placement on each screen
- [ ] Test with multiple remote types

Screens to audit:
- [ ] `TvHomeScreen.kt`
- [ ] `TvLibraryScreen.kt`
- [ ] `TvItemDetailScreen.kt`
- [ ] `TvVideoPlayerScreen.kt`

### 2.2 TV Player Controls
**Priority**: Medium | **Effort**: 2-3 days

Tasks:
- [ ] Test seek with D-pad
- [ ] Test play/pause with select button
- [ ] Add back button handling
- [ ] Test subtitle/audio selection with remote

---

## Phase 3: Code Quality, Security & Reliability

### 3.0 Security & Reliability
**Priority**: High | **Effort**: 5-7 days

> **Full Details**: [IMPROVEMENT_PLAN.md - Phase B & C](IMPROVEMENT_PLAN.md#phase-b-security-hardening-high)

Tasks:
- [x] Remove API tokens from URL query parameters (CWE-598) - use Authorization header
- [ ] Restrict cleartext HTTP to RFC 1918 private networks only
- [ ] Add progress sync resilience for network drops (queue and retry)
- [ ] Fix Cast session initialization race condition
- [ ] Make auth interceptor non-blocking (replace Thread.sleep/runBlocking)
- [ ] Add empty states and retry buttons across all screens
- [ ] Add content descriptions for accessibility (TalkBack support)

### 3.1 Refactor Large Files
**Priority**: Medium | **Effort**: 3-5 days

Files to refactor:
| File | Size | Action |
|------|------|--------|
| `VideoPlayerScreen.kt` | 58KB | Extract controls, overlays, gestures |
| `MainAppViewModel.kt` | 50KB | Extract domain-specific methods |
| `TVEpisodeDetailScreen.kt` | 45KB | Extract sections |
| `HomeScreen.kt` | 41KB | Continue extraction to `home/` subfolder |

### 3.2 Fix Build Warnings
**Priority**: Low | **Effort**: 2-3 hours

Current: ~150 non-critical warnings
- Update deprecated `hiltViewModel` imports
- Remove unnecessary safe calls (`?.` on non-null)
- Update deprecated `CastPlayer` constructor

### 3.3 Test Coverage
**Priority**: Medium | **Effort**: Ongoing

Target: 70%+ for ViewModels and Repositories
- [ ] Add tests for `VideoPlayerViewModel`
- [ ] Add tests for `OfflineDownloadManager`
- [ ] Add UI tests for critical flows

---

## Phase 4: Future Features (Backlog)

### 4.1 Live TV & DVR
- Browse live TV channels
- EPG (Electronic Program Guide)
- DVR recordings management

### 4.2 Sync Play
- Create/join watch sessions
- Synchronized playback
- Chat during playback

### 4.3 Multi-Profile Support
- Switch profiles in-app
- Profile-specific settings
- Kids mode

### 4.4 Home Screen Widget
- Continue watching widget
- Quick play widget

---

## Known Limitations

> **Note**: For detailed bug tracking with workarounds, see [KNOWN_ISSUES.md](../features/KNOWN_ISSUES.md)

### Material 3 Components
- **Carousel**: Using official Material 3 carousel API via `HorizontalUncontainedCarousel`
- **Pull-to-Refresh**: Using experimental APIs

### Platform Support
- **Phone/Tablet**: Full support
- **Android TV**: Partial (needs testing)
- **Android Auto**: Not supported
- **Wear OS**: Not supported

### Dependencies
- Min SDK: 26 (Android 8.0)
- Target SDK: 35
- Compile SDK: 36
- Kotlin: 2.3.0

---

## Quick Reference

### Build Commands
```bash
./gradlew.bat assembleDebug     # Build debug APK
./gradlew.bat installDebug      # Install on device
./gradlew.bat testDebugUnitTest # Run unit tests
./gradlew.bat lintDebug         # Run lint
./gradlew.bat jacocoTestReport  # Coverage report
```

### Key Directories
- **UI**: `app/src/main/java/com/rpeters/jellyfin/ui/`
- **Data**: `app/src/main/java/com/rpeters/jellyfin/data/`
- **DI**: `app/src/main/java/com/rpeters/jellyfin/di/`
- **Tests**: `app/src/test/java/`

---

## Progress Tracking

Mark tasks complete as you work:
- [ ] = Not started
- [x] = Complete

When completing a task:
1. Check the box
2. Test thoroughly
3. Commit with conventional commit message (e.g., `feat: add music background playback`)

---

## Related Documentation

- [IMPROVEMENT_PLAN.md](IMPROVEMENT_PLAN.md) - Detailed technical improvement plan (transcoding, security, accessibility)
- [GEMINI.md](GEMINI.md) - Development guidelines
- [CONTRIBUTING.md](CONTRIBUTING.md) - Contribution process
- [MATERIAL3_EXPRESSIVE.md](MATERIAL3_EXPRESSIVE.md) - M3 Expressive components
- [docs/TESTING_GUIDE.md](docs/TESTING_GUIDE.md) - Testing patterns
- [TRANSCODING_FIX_SUMMARY.md](TRANSCODING_FIX_SUMMARY.md) - Transcoding position reset fix
- [TRANSCODING_DIAGNOSTICS_GUIDE.md](TRANSCODING_DIAGNOSTICS_GUIDE.md) - Diagnostics tool usage
