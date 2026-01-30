# Jellyfin Android - Roadmap

**Last Updated**: January 30, 2026

> **Quick Links**: [Feature Status](CURRENT_STATUS.md) | [Known Issues](KNOWN_ISSUES.md) | [Upgrade Path](UPGRADE_PATH.md)

A clear, actionable improvement plan for the Jellyfin Android client.

---

## Current Status

### Working Features
- Server connection & authentication (multi-server, auto-login)
- Secure credential storage (Android Keystore, AES-256-GCM encryption)
- Certificate pinning (TOFU model)
- Video playback (ExoPlayer/Media3, HLS/DASH, FFmpeg decoder, adaptive track selection)
- Library browsing (Movies, TV Shows, Music)
- Material 3 Expressive UI with dark/light/AMOLED themes
- Resume playback, favorites, search
- Android TV UI (partial)

### Partially Working
- **Music playback** - UI exists, background playback incomplete
- **Offline downloads** - Screen exists, core functionality incomplete
- **Android TV** - Screens exist, D-pad navigation needs testing

### Verified Complete
- **Picture-in-Picture** - Full implementation with remote actions (play/pause, skip ±30s)
- **Chromecast** - Full casting with seek bar, volume control, position tracking
- **Auto-Play Next Episode** - Automatic continuation with countdown UI (added Jan 23, 2026)

### Not Implemented
- Live TV & DVR
- Sync Play (watch together)
- Home screen widgets
- Multi-profile support

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

### 1.2 Offline Downloads
**Priority**: High | **Effort**: 5-7 days

Tasks:
- [ ] Complete `OfflineDownloadManager.kt` download logic
- [ ] Add WorkManager for background downloads
- [ ] Implement download progress tracking
- [ ] Add offline playback detection in `VideoPlayerViewModel`
- [ ] Add storage management UI
- [ ] Support WiFi-only downloads

Files: `data/offline/OfflineDownloadManager.kt`, `ui/downloads/DownloadsScreen.kt`

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

## Phase 3: Code Quality

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

> **Note**: For detailed bug tracking with workarounds, see [KNOWN_ISSUES.md](KNOWN_ISSUES.md)

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

- [CLAUDE.md](CLAUDE.md) - Development guidelines
- [CONTRIBUTING.md](CONTRIBUTING.md) - Contribution process
- [MATERIAL3_EXPRESSIVE.md](MATERIAL3_EXPRESSIVE.md) - M3 Expressive components
- [docs/TESTING_GUIDE.md](docs/TESTING_GUIDE.md) - Testing patterns
