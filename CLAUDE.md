# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

### Building
```bash
# Build debug APK (use ./gradlew.bat on Windows)
./gradlew assembleDebug

# Install on connected device/emulator
./gradlew installDebug

# Build release APK
./gradlew assembleRelease
```

### Testing
```bash
# Run unit tests (JVM-based)
./gradlew testDebugUnitTest

# Run instrumentation tests (requires device/emulator)
./gradlew connectedAndroidTest

# Run both test types (used in CI)
./gradlew ciTest

# Run a single test class
./gradlew testDebugUnitTest --tests "com.rpeters.jellyfin.ClassName"
```

### Code Quality
```bash
# Run Android Lint
./gradlew lintDebug

# Generate JaCoCo coverage report (HTML/XML in app/build/reports)
./gradlew jacocoTestReport
```

### Environment Setup (CI/Codex/Web Environments)
```bash
# Linux/macOS: Install Android SDK and setup environment
./setup.sh

# Generate local.properties from environment variables
scripts/gen-local-properties.sh   # bash
scripts/gen-local-properties.ps1  # PowerShell
```

## Project Architecture

### High-Level Architecture
This is a modern Android client for Jellyfin media servers built with:
- **UI**: Jetpack Compose with Material 3 design system
- **Architecture**: MVVM pattern with Repository pattern for data access
- **DI**: Hilt for dependency injection throughout the app
- **Async**: Kotlin Coroutines with StateFlow for reactive UI updates
- **Media Playback**: ExoPlayer (Media3 1.9.0) with Jellyfin FFmpeg decoder
- **Networking**: Retrofit 3.0.0 + OkHttp 5.3.2 + Jellyfin SDK 1.8.5
- **Image Loading**: Coil 3.3.0 with custom performance optimizations

### Multi-Platform Support
The app detects device type and displays different UIs:
- **Phone/Tablet**: `JellyfinApp` (ui/JellyfinApp.kt) - standard navigation with bottom bar
- **Android TV**: `TvJellyfinApp` (ui/tv/TvJellyfinApp.kt) - D-pad optimized TV interface
- Detection happens in `MainActivity.kt` using `DeviceTypeUtils.getDeviceType()`

### Authentication & Session Management
- **JellyfinAuthRepository** (data/repository/JellyfinAuthRepository.kt) handles all authentication
- **JellyfinSessionManager** (data/session/JellyfinSessionManager.kt) manages SDK client lifecycle
- **SecureCredentialManager** (data/SecureCredentialManager.kt) uses Android Keystore for secure token storage
- Authentication state flows through ViewModels to UI via StateFlow

### Repository Layer Pattern
All data access goes through repositories that wrap the Jellyfin SDK:
- **JellyfinRepository**: Main repository for media library operations
- **JellyfinAuthRepository**: Authentication and server connection
- **JellyfinStreamRepository**: Streaming URLs and playback info
- **JellyfinSearchRepository**: Search functionality
- **JellyfinUserRepository**: User preferences and settings

Repositories use the `ApiResult<T>` sealed class pattern for error handling:
```kotlin
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val type: ErrorType, val message: String, ...) : ApiResult<Nothing>()
}
```

### Dependency Injection Structure
Hilt modules are organized in `di/` directory:
- **NetworkModule**: Provides OkHttpClient, Jellyfin SDK, ImageLoader, caching
- **Phase4Module**: Provides repositories and managers
- **AudioModule**: Provides audio playback services

Key pattern: Use `Provider<T>` for circular dependencies (e.g., `Provider<JellyfinAuthRepository>` in interceptors)

### Navigation Architecture
- **Phone/Tablet**: Navigation Compose with bottom navigation bar (`ui/components/BottomNavBar.kt`)
- **TV**: Separate TV-specific navigation graph
- Navigation routes defined in `ui/navigation/NavRoutes.kt`
- Navigation graphs split by feature:
  - `NavGraph.kt`: Main phone navigation
  - `AuthNavGraph.kt`: Authentication flows
  - `HomeLibraryNavGraph.kt`: Home and library screens
  - `ProfileNavGraph.kt`: User profile/settings

### Media Playback Architecture
- **EnhancedPlaybackManager** (data/playback/EnhancedPlaybackManager.kt): Determines optimal playback method
  - Checks codec support via MediaCodecList
  - Decides between Direct Play (no transcoding) vs Transcoding
  - Configures ExoPlayer accordingly
- **VideoPlayerScreen** (ui/screens/VideoPlayerScreen.kt): Main video player UI
- **AudioService** (ui/player/audio/AudioService.kt): Background audio playback with Media3 session
- **PlaybackProgressManager**: Tracks and reports playback position to server

### Image Loading & Performance
- Custom `ImageLoadingOptimizer` (ui/image/OptimizedImageLoader.kt) configures Coil based on device performance
- **DevicePerformanceProfile** detects device tier (LOW/MEDIUM/HIGH/FLAGSHIP) to adjust:
  - Image cache sizes
  - Loading animations
  - Placeholder strategies
- Images loaded via Coil with OkHttp integration for auth headers

### Testing Infrastructure
- **Test runner**: `HiltTestRunner` (testing/HiltTestRunner.kt) for instrumentation tests
- **Frameworks**: JUnit4, MockK, Turbine (for testing StateFlow), AndroidX Test
- **Coverage**: JaCoCo configured to exclude generated code, models, and DI modules
- **Unit tests location**: `app/src/test/java`
- **Instrumentation tests location**: `app/src/androidTest/java`
- Test naming convention: `functionName_scenario_expectedResult`

### Key Constants & Configuration
- Centralized constants in `core/constants/Constants.kt`
- SDK versions: compileSdk 36, targetSdk 35, minSdk 26
- Java version: 21 with core library desugaring enabled
- Dependency versions: Centralized in `gradle/libs.versions.toml`

### State Management Pattern
ViewModels expose UI state via StateFlow:
```kotlin
private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
val uiState: StateFlow<UiState> = _uiState.asStateFlow()
```

Screens collect state with lifecycle awareness:
```kotlin
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

### Error Handling & Logging
- **SecureLogger** (utils/SecureLogger.kt): Filters PII and sensitive data from logs
- Verbose logging disabled by default (even in debug builds) to reduce spam
- Enable verbose logging: `SecureLogger.enableVerboseLogging = true`
- **ErrorHandler** for user-facing error messages
- **Logger** (core/Logger.kt) with file logging support

## Material 3 Design System

### Current Implementation
- Using Material 3 alpha versions (1.5.0-alpha11)
- **Custom carousel implementation** instead of official Material 3 Carousel (dependency disabled)
- **Adaptive layouts** using Material 3 adaptive components for different screen sizes
- Theme defined in `ui/theme/` with Jellyfin brand colors:
  - Primary: Jellyfin Purple (#6200EE)
  - Secondary: Jellyfin Blue (#2962FF)
  - Tertiary: Jellyfin Teal (#00695C)

### Material 3 Opt-ins
Compiler flags already configured for:
- `@OptIn(ExperimentalMaterial3Api::class)`
- `@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)`

## Android TV Considerations

When working on TV features:
- TV screens are in `ui/tv/` and `ui/screens/tv/`
- Use `androidx.tv:tv-material` components for TV-optimized layouts
- Test D-pad navigation with `Modifier.focusable()` and `focusRequester`
- TV player controls in `ui/player/tv/TvVideoPlayerControls.kt`

## Offline & Download Features

**Status**: Partially implemented
- Screens exist: `ui/downloads/DownloadsScreen.kt`
- Manager: `data/offline/OfflineDownloadManager.kt`
- Core functionality incomplete - this is a known gap per CURRENT_STATUS.md

## Commit Convention

Follow Conventional Commits:
- `feat:` new feature
- `fix:` bug fix
- `docs:` documentation only
- `refactor:` code change that neither fixes a bug nor adds a feature
- `test:` adding or updating tests
- `chore:` maintenance tasks

Example: `feat: add movie detail screen`, `fix: prevent crash on empty library`

## Common File Locations

- **Application class**: `JellyfinApplication.kt` (Hilt entry point, initializes app-wide services)
- **Main activity**: `MainActivity.kt` (device type detection, setContent)
- **Root Compose phone app**: `ui/JellyfinApp.kt`
- **Root Compose TV app**: `ui/tv/TvJellyfinApp.kt`
- **Main home screen**: `ui/screens/HomeScreen.kt` (large file ~40KB with carousel)
- **Hilt modules**: `di/` directory
- **Repositories**: `data/repository/`
- **ViewModels**: `ui/viewmodel/`
- **Reusable components**: `ui/components/`
- **Navigation**: `ui/navigation/`
- **Theme**: `ui/theme/`

## Known Limitations

1. **Material 3 Carousel**: Official dependency disabled, using custom implementation
2. **Android TV**: Partial implementation exists, needs comprehensive D-pad testing
3. **Music Playback**: UI exists but playback controls incomplete
4. **Offline Downloads**: Screen exists but core functionality incomplete

Refer to CURRENT_STATUS.md for detailed feature status and IMPROVEMENTS.md for roadmap.

## Performance Optimization

- **NetworkOptimizer** (utils/NetworkOptimizer.kt) configures StrictMode and network settings
- **MainThreadMonitor** (utils/MainThreadMonitor.kt) tracks main thread impact in debug builds
- **ModernSurfaceCoordinator** (ui/surface/ModernSurfaceCoordinator.kt) manages surfaces for video playback
- Memory optimization: LeakCanary in debug builds, cache cleanup on low memory

## Code Quality Expectations

- **Test coverage target**: 70%+ for ViewModels and Repositories
- **Naming**: PascalCase for classes, camelCase for functions/vars, UPPER_SNAKE_CASE for constants
- **Indentation**: 4 spaces, ~120 char lines
- **Architecture**: Follow existing MVVM + Repository pattern
- **Null safety**: Use nullable types appropriately, avoid !! operator
- **Coroutines**: Launch in ViewModelScope, use structured concurrency
