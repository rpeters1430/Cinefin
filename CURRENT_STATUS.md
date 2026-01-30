# Jellyfin Android - Current Status

**Last Updated**: January 30, 2026

This document provides a comprehensive snapshot of what works RIGHT NOW in the Jellyfin Android client. For planned features and improvements, see [ROADMAP.md](ROADMAP.md). For known bugs and workarounds, see [KNOWN_ISSUES.md](KNOWN_ISSUES.md). For dependency upgrade strategy, see [UPGRADE_PATH.md](UPGRADE_PATH.md).

---

## Feature Status Matrix

### ✅ Fully Working Features

| Feature | Status | Platforms | Notes |
|---------|--------|-----------|-------|
| **Authentication** | ✅ Complete | All | Multi-server, token-based, Quick Connect, auto-login |
| **Secure Storage** | ✅ Complete | All | Android Keystore, AES-256-GCM encryption |
| **Certificate Pinning** | ✅ Complete | All | TOFU (Trust-on-First-Use) model |
| **Video Playback** | ✅ Complete | Phone, Tablet, TV | ExoPlayer/Media3, HLS/DASH, FFmpeg decoder, Direct Play detection |
| **Library Browsing** | ✅ Complete | All | Movies, TV Shows, Music libraries |
| **Search** | ✅ Complete | Phone, Tablet | Multi-library search with filters |
| **Favorites** | ✅ Complete | All | Add/remove favorites, dedicated favorites screen |
| **Resume Playback** | ✅ Complete | All | Automatic position tracking and resumption |
| **Picture-in-Picture** | ✅ Complete | Phone, Tablet, TV | Manual/auto-enter, remote actions (play/pause, skip ±30s) |
| **Chromecast** | ✅ Complete | Phone, Tablet | Full casting with seek, volume, position tracking |
| **Auto-Play Next Episode** | ✅ Complete | Phone, Tablet | Countdown UI, automatic continuation |
| **Material 3 UI** | ✅ Complete | All | Expressive components, dark/light/AMOLED themes |
| **Adaptive Navigation** | ✅ Complete | All | Screen-size responsive navigation |
| **Recently Added Carousel** | ✅ Complete | Phone, Tablet | Cinematic 16:9 cards with animations |

### ⚠️ Partially Working Features

| Feature | Status | Platforms | What Works | What's Missing | Issue Link |
|---------|--------|-----------|------------|----------------|------------|
| **Music Playback** | ⚠️ Partial | Phone, Tablet | UI, basic playback | Background playback, notification controls, lock screen controls, queue management | [ROADMAP §1.1](ROADMAP.md#11-music-background-playback) |
| **Offline Downloads** | ⚠️ Partial | Phone, Tablet | UI screens exist | Core download logic, WorkManager integration, progress tracking, offline playback | [ROADMAP §1.2](ROADMAP.md#12-offline-downloads), [KNOWN_ISSUES #7](KNOWN_ISSUES.md) |
| **Android TV** | ⚠️ Partial | Android TV | UI screens, basic navigation | D-pad testing, focus indicators, player controls | [ROADMAP §2.1](ROADMAP.md#21-d-pad-navigation-audit) |

### ❌ Not Implemented

| Feature | Status | Priority | Planned |
|---------|--------|----------|---------|
| **Live TV & DVR** | ❌ Not Started | Low | Phase 4 - [ROADMAP §4.1](ROADMAP.md#41-live-tv--dvr) |
| **Sync Play** | ❌ Not Started | Low | Phase 4 - [ROADMAP §4.2](ROADMAP.md#42-sync-play) |
| **Multi-Profile Support** | ❌ Not Started | Low | Phase 4 - [ROADMAP §4.3](ROADMAP.md#43-multi-profile-support) |
| **Home Screen Widgets** | ❌ Not Started | Low | Phase 4 - [ROADMAP §4.4](ROADMAP.md#44-home-screen-widget) |
| **Android Auto** | ❌ Not Planned | N/A | Not on roadmap |
| **Wear OS** | ❌ Not Planned | N/A | Not on roadmap |

---

## Platform Support

### Phone & Tablet
**Status**: ✅ Full Support

- **Min SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 15 (API 35)
- **Compile SDK**: Android 16 Preview (API 36)
- **Navigation**: Bottom navigation bar, adaptive navigation suite
- **UI**: Full Material 3 Expressive design
- **Features**: All core features fully functional

### Android TV
**Status**: ⚠️ Partial Support

- **UI**: Dedicated TV screens (`ui/tv/` directory)
- **Navigation**: Separate TV navigation graph
- **Working**: Basic browsing, video playback, library access
- **Needs Testing**: D-pad navigation, focus indicators, remote controls
- **See**: [ROADMAP Phase 2](ROADMAP.md#phase-2-android-tv-polish)

### Foldables & Large Screens
**Status**: ✅ Adaptive Layout Support

- **Adaptive Navigation**: Automatically adjusts to screen size
- **Window Size Classes**: Material 3 adaptive components
- **Layout**: Responsive grids and list layouts

---

## Technical Status

### Architecture Stack

| Component | Version | Status |
|-----------|---------|--------|
| **Kotlin** | 2.3.0 | ✅ Stable |
| **Compose BOM** | 2026.01.01 | ✅ Latest |
| **Material 3** | 1.5.0-alpha13 | ⚠️ Alpha (intentional for Expressive) |
| **Hilt** | 2.59 | ✅ Stable |
| **Coroutines** | 1.10.2 | ✅ Stable |
| **Retrofit** | 3.0.0 | ✅ Stable |
| **OkHttp** | 5.3.2 | ✅ Stable |
| **Coil** | 3.3.0 | ✅ Stable |
| **Media3** | 1.9.1 | ✅ Stable |
| **Jellyfin SDK** | 1.8.6 | ✅ Stable |
| **Navigation** | 2.9.7 | ✅ Stable |
| **Paging** | 3.4.0 | ✅ Stable |

**See**: [UPGRADE_PATH.md](UPGRADE_PATH.md) for full dependency upgrade strategy.

### Build Status

- **CI/CD**: ✅ Passing on all branches
- **Unit Tests**: ✅ Passing (target 70%+ coverage)
- **Lint**: ⚠️ ~150 non-critical warnings (see [ROADMAP §3.2](ROADMAP.md#32-fix-build-warnings))
- **Coverage**: ✅ JaCoCo configured and reporting
- **Release Builds**: ✅ ProGuard/R8 enabled with minification

### Security & Compliance

- ✅ Android Keystore encryption (AES-256-GCM)
- ✅ Certificate pinning (TOFU model)
- ✅ Secure credential storage (`SecureCredentialManager`)
- ✅ PII filtering in logs (`SecureLogger`)
- ✅ Network security config (`network_security_config.xml`)
- ✅ No hardcoded secrets or credentials
- ✅ ProGuard rules for release builds

---

## Known Limitations

### Material 3 Components
- **Carousel**: Using official Material 3 carousel API (HorizontalUncontainedCarousel) wrapped by `ExpressiveHeroCarousel`
- **Alpha Dependencies**: Intentionally using Material 3 alpha versions for Expressive components
- **Migration Path**: Will update when Material 3 Expressive reaches stable (see [UPGRADE_PATH.md](UPGRADE_PATH.md))

### Performance Considerations
- **Large Composables**: Some screens are large (HomeScreen: 1,119 lines, VideoPlayerScreen: 1,726 lines) - planned refactor in [ROADMAP §3.1](ROADMAP.md#31-refactor-large-files)
- **Image Loading**: Optimized with device performance profiles (LOW/MEDIUM/HIGH/FLAGSHIP tiers)
- **Memory**: LeakCanary enabled in debug builds for leak detection

### Platform Limitations
- **Min SDK 26**: No support for Android 7.x and below
- **Java 21 Required**: Uses core library desugaring for compatibility
- **No Wear OS**: Not supported (no plans)
- **No Android Auto**: Not supported (no plans)

---

## Development Status

### Recent Completions (January 2026)
- ✅ Auto-play next episode with countdown UI (Jan 23, 2026)
- ✅ Offline download hanging bug fixed (Jan 2026)
- ✅ Download ID mismatch resolved (Jan 2026)
- ✅ Chromecast enhancements: seek bar, volume control, position tracking (Jan 2026)
- ✅ Auto quality selection now uses adaptive track selection in the player (Jan 2026)
- ✅ Cache initialization/thread-safety improvements in JellyfinCache (Jan 2026)

### Active Development
- 🔄 Music background playback (in progress - [ROADMAP §1.1](ROADMAP.md))
- 🔄 Offline downloads core functionality (in progress - [ROADMAP §1.2](ROADMAP.md))
- 🔄 Android TV D-pad navigation testing (in progress - [ROADMAP §2.1](ROADMAP.md))

### Code Quality Focus
- 🎯 Test coverage target: 70%+ for ViewModels and Repositories
- 🎯 Refactoring large composables (ongoing - [ROADMAP §3.1](ROADMAP.md))
- 🎯 Fixing build warnings (planned - [ROADMAP §3.2](ROADMAP.md))
- 🎯 Auth refresh retry improvements (planned - [KNOWN_ISSUES #5](KNOWN_ISSUES.md))

---

## Quick Reference

### Build Commands
```bash
# Windows (use .bat extension)
./gradlew.bat assembleDebug          # Build debug APK
./gradlew.bat installDebug           # Install on device
./gradlew.bat testDebugUnitTest      # Run unit tests
./gradlew.bat connectedAndroidTest   # Run instrumentation tests (requires device)
./gradlew.bat lintDebug              # Run Android Lint
./gradlew.bat jacocoTestReport       # Generate coverage report
```

### Key File Locations
- **Application**: `app/src/main/java/com/rpeters/jellyfin/JellyfinApplication.kt`
- **Main Activity**: `app/src/main/java/com/rpeters/jellyfin/MainActivity.kt`
- **Phone UI**: `app/src/main/java/com/rpeters/jellyfin/ui/JellyfinApp.kt`
- **TV UI**: `app/src/main/java/com/rpeters/jellyfin/ui/tv/TvJellyfinApp.kt`
- **Repositories**: `app/src/main/java/com/rpeters/jellyfin/data/repository/`
- **ViewModels**: `app/src/main/java/com/rpeters/jellyfin/ui/viewmodel/`
- **Hilt Modules**: `app/src/main/java/com/rpeters/jellyfin/di/`

---

## Related Documentation

- **[ROADMAP.md](ROADMAP.md)** - Future features and development roadmap
- **[KNOWN_ISSUES.md](KNOWN_ISSUES.md)** - Active bugs with workarounds and fix status
- **[UPGRADE_PATH.md](UPGRADE_PATH.md)** - Dependency upgrade strategy and version roadmap
- **[CLAUDE.md](CLAUDE.md)** - Development guidelines and architecture details
- **[CONTRIBUTING.md](CONTRIBUTING.md)** - Contribution process and guidelines
- **[TESTING_GUIDE.md](docs/TESTING_GUIDE.md)** - Testing patterns and best practices
- **[IMPROVEMENT_PLAN.md](docs/IMPROVEMENT_PLAN.md)** - Technical debt and code quality improvements

---

## Summary

**What Works Now**: The Jellyfin Android client is a fully functional media player with secure authentication, video playback, library browsing, search, favorites, Picture-in-Picture, Chromecast, and auto-play next episode. The UI is modern with Material 3 Expressive components, and the architecture is solid with MVVM, Hilt DI, and Kotlin Coroutines.

**What's In Progress**: Music background playback, offline downloads functionality, and Android TV D-pad navigation are under active development.

**What's Planned**: Live TV, Sync Play, multi-profile support, and home screen widgets are on the roadmap for future phases.

**Known Issues**: See [KNOWN_ISSUES.md](KNOWN_ISSUES.md) for detailed bug tracking with workarounds and fix status.
