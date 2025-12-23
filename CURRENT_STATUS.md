# Jellyfin Android Client - Current Status

**Last Updated**: 2025-12-22
**Kotlin**: 2.3.0 | **JDK**: 21 | **Compose BOM**: 2025.12.01 | **Build Status**: ✅ Passing

---

## 📱 Project Overview

A modern Android client for Jellyfin media servers built with Jetpack Compose and Material 3 design principles. The app provides a native mobile experience for browsing and streaming media from Jellyfin servers.

---

## ✅ Core Features (Verified & Working)

### Authentication & Connection
- ✅ Server URL input and validation
- ✅ Username/password authentication
- ✅ Token-based session management
- ✅ Remember server credentials
- ✅ Multi-server support
- ✅ Secure credential storage (Android Keystore)

### Media Library Browsing
- ✅ Home screen with personalized content
- ✅ Library grid view with cover art
- ✅ Movie browsing and details
- ✅ TV show browsing with seasons/episodes
- ✅ Music library (basic display)
- ✅ Favorites management
- ✅ Search functionality (basic)

### Video Playback
- ✅ ExoPlayer integration (Media3 1.9.0)
- ✅ HLS/DASH streaming support
- ✅ Video player controls
- ✅ FFmpeg decoder integration
- ✅ Playback state tracking
- ✅ Resume playback from last position

### UI/UX
- ✅ Material 3 design system
- ✅ Dark/Light theme support
- ✅ Adaptive layouts for different screen sizes
- ✅ Navigation with bottom navigation bar
- ✅ Custom carousel implementation (not official Material 3)
- ✅ Loading states and error handling
- ✅ Image loading with Coil 3.3.0

### Architecture
- ✅ MVVM pattern with ViewModels
- ✅ Repository pattern for data access
- ✅ Hilt dependency injection (2.57.2)
- ✅ Kotlin Coroutines for async operations
- ✅ StateFlow for reactive UI updates
- ✅ Retrofit 3.0.0 for API integration
- ✅ OkHttp 5.3.2 for HTTP client

---

## ⚠️ Partially Implemented Features

### Video Features
- ⚠️ **Picture-in-Picture (PiP)** - Basic implementation, needs testing
- ⚠️ **Subtitle support** - Present but needs verification
- ⚠️ **Audio track selection** - UI exists, functionality needs testing

### Media Features
- ⚠️ **Music playback** - UI exists, playback controls incomplete
- ⚠️ **Offline downloads** - Screen exists (`OfflineScreen.kt`), core functionality incomplete
- ⚠️ **Continue Watching** - Backend support unclear, needs verification

### Android TV
- ⚠️ **TV UI screens** - Some TV-specific screens exist (`TvHomeScreen.kt`, `TvLibraryScreen.kt`)
- ⚠️ **D-pad navigation** - Partial implementation, needs comprehensive testing
- ⚠️ **Leanback integration** - Not fully implemented

### Advanced Features
- ⚠️ **Advanced search filters** - Basic search works, filters incomplete
- ⚠️ **Quick Connect** - Backend implementation exists, UI integration unclear
- ⚠️ **Chromecast** - Dependencies included (`play-services-cast-framework 22.2.0`), integration status unknown

---

## ❌ Not Yet Implemented

- ❌ **Live TV streaming**
- ❌ **DVR management**
- ❌ **Audio-only playback UI** (music player controls)
- ❌ **Sync play** (watch together)
- ❌ **Background audio playback**
- ❌ **Widgets** (home screen widgets)
- ❌ **Notifications** (playback, downloads)
- ❌ **Multiple user profiles** in single session

---

## 🏗️ Current Architecture

### Project Structure
```
app/src/main/java/com/rpeters/jellyfin/
├── JellyfinApplication.kt       # Application class with Hilt
├── MainActivity.kt              # Main activity (~3.0KB / 3,092 bytes)
├── core/                        # Core constants and utilities
├── data/                        # Data layer
│   ├── models/                  # Data models
│   ├── repository/              # Repository implementations
│   ├── offline/                 # Offline/download management
│   ├── playback/                # Playback managers
│   └── paging/                  # Pagination support
├── di/                          # Hilt dependency injection modules
├── network/                     # Network layer (Retrofit services)
├── ui/                          # UI layer (Compose screens)
│   ├── components/              # Reusable Compose components
│   ├── navigation/              # Navigation graphs
│   ├── screens/                 # Screen implementations
│   ├── theme/                   # Material 3 theme
│   ├── viewmodel/               # ViewModels
│   └── player/                  # Video player components
└── utils/                       # Utility classes
```

### Key Files
- **HomeScreen.kt** (~39.5KB / 40,407 bytes) - Main home screen with carousel and library grid
- **JellyfinRepository.kt** - Primary data repository
- **VideoPlayerScreen.kt** - Video playback screen
- **ServerConnectionViewModel.kt** - Authentication and connection management
- **MainAppViewModel.kt** - Main app state management

### Testing
- 📊 **41 test files** in codebase
- Unit tests for ViewModels and Repository
- Test frameworks: JUnit4, MockK, Turbine, AndroidX Test
- Test coverage tracking with JaCoCo

---

## 🔧 Build Configuration

### Versions
```toml
kotlin = "2.3.0"
ksp = "2.3.4"
agp = "8.13.2"
hilt = "2.57.2"
composeBom = "2025.12.01"
material3 = "1.5.0-alpha11"
retrofit = "3.0.0"
okhttp = "5.3.2"
media3 = "1.9.0"
jellyfinSdk = "1.8.5"
coil = "3.3.0"
```

### Build Commands
```bash
# Build debug APK
./gradlew assembleDebug

# Install on device/emulator
./gradlew installDebug

# Run unit tests
./gradlew testDebugUnitTest

# Run instrumentation tests
./gradlew connectedAndroidTest

# Lint check
./gradlew lintDebug

# Generate coverage report
./gradlew jacocoTestReport
```

### Requirements
- **Minimum SDK**: 26 (Android 8.0)
- **Target SDK**: 35
- **Compile SDK**: 36
- **JDK**: 21
- **Android Studio**: Iguana or later

---

## 📦 Key Dependencies

### UI & Compose
- androidx.compose.bom: 2025.12.01
- androidx.compose.material3: 1.5.0-alpha11
- androidx.compose.material3.adaptive: 1.3.0-alpha05
- androidx.tv:tv-material: 1.1.0-alpha01

### Networking
- org.jellyfin.sdk:jellyfin-core: 1.8.5
- com.squareup.retrofit2:retrofit: 3.0.0
- com.squareup.okhttp3:okhttp: 5.3.2
- org.jetbrains.kotlinx:kotlinx-serialization-json: 1.9.0

### Media Playback
- androidx.media3:media3-exoplayer: 1.9.0
- androidx.media3:media3-ui: 1.9.0
- org.jellyfin.media3:media3-ffmpeg-decoder: 1.8.0+1

### Image Loading
- io.coil-kt.coil3:coil-compose: 3.3.0
- io.coil-kt.coil3:coil-network-okhttp: 3.3.0

### Dependency Injection
- com.google.dagger:hilt-android: 2.57.2
- androidx.hilt:hilt-navigation-compose: 1.3.0

---

## 🚨 Known Limitations

### Material 3 Components
- 📦 **Official Material 3 Carousel** - Dependency is **disabled** in `gradle/libs.versions.toml`
  - Using custom carousel implementation instead
  - Comment in config: `# androidx-material3-carousel = { ... }`

- 📦 **Official Pull-to-Refresh** - Dependency is **disabled**
  - Using experimental APIs from androidx.compose.material3.pulltorefresh
  - Comment in config: `# androidx-material3-pulltorefresh = { ... }`

### Compiler Warnings
- ⚠️ Flag not supported: `-Xannotation-default-target=param-property` (update note if warnings persist with Kotlin 2.3.0)
- ⚠️ Some experimental Coroutines APIs used (needs opt-in annotations)

### Platform Support
- ✅ **Android Mobile**: Full support (phones/tablets)
- ⚠️ **Android TV**: Partial support (screens exist, needs comprehensive testing)
- ❌ **Android Auto**: Not supported
- ❌ **Wear OS**: Not supported

---

## 📈 Project Health

### Build Status
- ✅ **Gradle Build**: Passing
- ✅ **Kotlin Compilation**: Success
- ✅ **Dependency Resolution**: All dependencies resolved
- ✅ **Code Style**: Ktlint configured

### Code Quality
- **Total Files**: 100+ Kotlin files
- **Test Files**: 41 test files
- **Architecture**: Clean MVVM with repository pattern
- **DI Coverage**: Comprehensive Hilt usage

### CI/CD
- ✅ GitHub Actions configured
- ✅ Build verification on push
- ✅ Automated testing
- ✅ Release workflow (tag-based)

---

## 🎯 Immediate Next Steps

Based on the current state, recommended priorities:

1. **Verify and document** partial features (music playback, PiP, TV support)
2. **Complete offline download** functionality
3. **Implement audio-only playback** UI for music
4. **Test and fix** Android TV D-pad navigation
5. **Add Live TV support** (if required)
6. **Comprehensive testing** of Chromecast integration

---

## 📚 Related Documentation

- **IMPROVEMENTS_ARCHIVE.md** - Historical improvement plans and analysis (archived)
- **KNOWN_ISSUES.md** - Active bugs and workarounds
- **README.md** - Project overview and setup instructions
- **CONTRIBUTING.md** - Contribution guidelines
- **AGENTS.md** - Repository guidelines for AI agents

---

## 🔄 Document Maintenance

This document should be updated:
- When new features are completed
- When build configuration changes
- When major dependencies are updated
- At minimum: monthly review

**Verification Process**: Each ✅ status should be verified against actual code implementation, not just documentation claims.
