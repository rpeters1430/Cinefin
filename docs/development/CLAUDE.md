# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

### Building
```bash
# Build debug APK
./gradlew assembleDebug          # Linux/macOS
./gradlew.bat assembleDebug      # Windows

# Install on connected device/emulator
./gradlew installDebug

# Build release APK
./gradlew assembleRelease
```

**Windows Note**: Replace `./gradlew` with `./gradlew.bat` or `gradlew.bat` in all commands.

### Testing
```bash
# Run unit tests (JVM-based)
./gradlew testDebugUnitTest          # Linux/macOS
./gradlew.bat testDebugUnitTest      # Windows

# Run instrumentation tests (requires device/emulator)
./gradlew connectedAndroidTest       # Linux/macOS
./gradlew.bat connectedAndroidTest   # Windows

# Run both test types (used in CI)
./gradlew ciTest                     # Linux/macOS
./gradlew.bat ciTest                 # Windows

# Run a single test class
./gradlew testDebugUnitTest --tests "com.rpeters.jellyfin.ClassName"         # Linux/macOS
./gradlew.bat testDebugUnitTest --tests "com.rpeters.jellyfin.ClassName"     # Windows

# Run a specific test method
./gradlew testDebugUnitTest --tests "com.rpeters.jellyfin.ClassName.methodName"
```

### Code Quality
```bash
# Run Android Lint
./gradlew lintDebug                  # Linux/macOS
./gradlew.bat lintDebug              # Windows

# Generate JaCoCo coverage report (HTML/XML in app/build/reports)
./gradlew jacocoTestReport           # Linux/macOS
./gradlew.bat jacocoTestReport       # Windows
```

### Environment Setup (CI/Codex/Web Environments)
```bash
# Linux/macOS: Install Android SDK and setup environment
./setup.sh

# Generate local.properties from environment variables
scripts/gen-local-properties.sh   # bash (Linux/macOS)
scripts/gen-local-properties.ps1  # PowerShell (Windows)
```

**Note**: The setup scripts configure `ANDROID_SDK_ROOT` (or `ANDROID_HOME`) and generate `local.properties` with the SDK path. Required for CI/CD environments without Android Studio.

## Project Architecture

### Project Identity
- **Project Name**: Cinefin Android (formerly Jellyfin Android Client)
- **Application ID**: `com.rpeters.jellyfin`
- **Namespace**: `com.rpeters.jellyfin`
- **Version**: Defined in `app/build.gradle.kts` (versionCode: 49, versionName: "14.17")

### High-Level Architecture
This is a modern Android client for Jellyfin media servers built with:
- **UI**: Jetpack Compose (BOM 2026.02.00) with Material 3 design system
- **Architecture**: MVVM pattern with Repository pattern for data access
- **DI**: Hilt 2.59.1 for dependency injection throughout the app
- **Async**: Kotlin Coroutines 1.10.2 with StateFlow for reactive UI updates
- **Media Playback**: ExoPlayer (Media3 1.10.0-alpha01) with Jellyfin FFmpeg decoder
- **Networking**: Retrofit 3.0.0 + OkHttp 5.3.2 + Jellyfin SDK 1.8.6
- **Image Loading**: Coil 3.3.0 with custom performance optimizations
- **Security**: Android Keystore encryption, dynamic certificate pinning with TOFU model

### Multi-Platform Support
The app detects device type and displays different UIs:
- **Phone/Tablet**: `JellyfinApp` (ui/JellyfinApp.kt) - standard navigation with bottom bar
- **Android TV**: `TvJellyfinApp` (ui/tv/TvJellyfinApp.kt) - D-pad optimized TV interface
- Detection happens in `MainActivity.kt` using `DeviceTypeUtils.getDeviceType()`

### Authentication & Session Management
- **JellyfinAuthRepository** (data/repository/JellyfinAuthRepository.kt) handles all authentication
- **JellyfinSessionManager** (data/session/JellyfinSessionManager.kt) manages SDK client lifecycle
- **SecureCredentialManager** (data/SecureCredentialManager.kt) uses Android Keystore for secure token storage
- **Certificate Pinning**: Dynamic TOFU (Trust-on-First-Use) model for enhanced security
- Authentication state flows through ViewModels to UI via StateFlow

### Repository Layer Pattern
All data access goes through repositories that wrap the Jellyfin SDK:
- **JellyfinRepository**: Main repository for media library operations
- **JellyfinAuthRepository**: Authentication and server connection
- **JellyfinStreamRepository**: Streaming URLs and playback info
- **JellyfinSearchRepository**: Search functionality
- **JellyfinUserRepository**: User preferences and settings
- **GenerativeAiRepository**: AI-powered features (summaries, recommendations, smart search)
- **RemoteConfigRepository**: Firebase Remote Config for feature flags and A/B testing

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
- **DispatcherModule**: Provides coroutine dispatchers (Main, IO, Default)
- **AiModule**: Provides AI models with smart Nano/Cloud fallback
- **RemoteConfigModule**: Provides Firebase Remote Config instance

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

### Google Cast / Chromecast Integration
- **CastManager** (ui/player/CastManager.kt): Manages all Cast functionality
  - Session management with auto-reconnect support
  - Media loading with authentication token injection
  - Subtitle track support
  - Preview loading (artwork + metadata without playback)
- **CastOptionsProvider** (ui/player/CastOptionsProvider.kt): Cast configuration
  - **Current receiver**: `CC1AD845` (Google Default Media Receiver)
  - **Why not Jellyfin's official receiver?** The Jellyfin Cast receivers (`F007D354` stable, `6F511C87` unstable) require implementing the full Jellyfin Cast protocol with custom data payloads containing server info, authentication, and media source selection. This app uses a simplified URL-based approach - sending HLS transcoded stream URLs with auth tokens as query parameters - which works reliably with Google's Default Media Receiver without requiring custom protocol implementation.
  - Source: https://github.com/jellyfin/jellyfin-chromecast
- **Cast preferences**: Stored via CastPreferencesRepository for auto-reconnect
- **Authentication**: Cast URLs no longer include tokens; casting protected media requires a local proxy or unauthenticated endpoint
- **Stream optimization**: Prefers HLS transcoding (`container=hls`) for maximum compatibility with adaptive streaming fallback

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

#### Important Testing Patterns
- **Dispatcher Testing**: Use `StandardTestDispatcher` (NOT `UnconfinedTestDispatcher`) with `advanceUntilIdle()` for ViewModel tests
  ```kotlin
  private val testDispatcher = StandardTestDispatcher()

  @Test
  fun test() = runTest(testDispatcher) {
      viewModel.someAction()
      advanceUntilIdle()  // CRITICAL: Must call to execute pending coroutines
      // Now assert
  }
  ```
- **Mocking Flows**: Use `coEvery` (NOT `every`) for Flow properties to avoid MockKException
  ```kotlin
  // CORRECT:
  coEvery { repository.currentServer } returns MutableStateFlow(null)

  // WRONG:
  every { repository.currentServer } returns flowOf(null)  // Causes MockKException
  ```
- **Default Parameters**: Use `any()` matchers when mocking repository methods with default parameters
  ```kotlin
  coEvery {
      repository.getLibraryItems(
          parentId = "123",
          itemTypes = "Movie",
          startIndex = any(),  // Use any() for default parameters
          limit = any(),
          collectionType = "movies"
      )
  } returns ApiResult.Success(items)
  ```
- **Common Pitfalls**:
  - Forgetting `advanceUntilIdle()` - coroutines won't execute
  - Using `every` instead of `coEvery` for Flows - causes inline function errors
  - Using `runBlocking { delay() }` instead of `advanceUntilIdle()` - unreliable
- See `docs/development/TESTING_GUIDE.md` for complete ViewModel testing examples and detailed patterns

### Key Constants & Configuration
- Centralized constants in `core/constants/Constants.kt`
- **SDK versions**: compileSdk 36, targetSdk 35, minSdk 26 (Android 8.0+)
- **Current version**: versionCode 49, versionName "14.17"
- **Java version**: 21 with core library desugaring enabled
- **Kotlin version**: 2.3.10 with KSP 2.3.5
- **Dependency versions**: Centralized in `gradle/libs.versions.toml`
- **Release builds**: ProGuard/R8 enabled with shrinking and minification (`proguard-rules.pro`)
- **Native debug symbols**: FULL debug symbols enabled for Play Console crash reporting
- **Network security**: Custom configuration in `app/src/main/res/xml/network_security_config.xml`
- **Firebase Integration**: Crashlytics and Performance Monitoring enabled (Google Services plugin)
- **Test coverage**: Enabled for both unit and instrumentation tests in debug builds

### Release Signing Configuration
Release builds require signing credentials configured via Gradle properties or environment variables:
- `JELLYFIN_KEYSTORE_FILE` - Path to keystore file (defaults to `jellyfin-release.keystore`)
- `JELLYFIN_KEYSTORE_PASSWORD` - Keystore password
- `JELLYFIN_KEY_ALIAS` - Key alias (defaults to `jellyfin-release`)
- `JELLYFIN_KEY_PASSWORD` - Key password

Set in `gradle.properties` (local dev) or CI environment variables.

### API Keys & Configuration Files
- **Google AI API Key**: `GOOGLE_AI_API_KEY` in `gradle.properties` or environment variable
  - Required for cloud-based AI features (Gemini API fallback)
  - Optional if only using on-device Gemini Nano
- **Firebase**: Requires `google-services.json` in `app/` directory
  - Download from Firebase Console (project settings)
  - Used for Crashlytics, Analytics, Remote Config, App Check

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
- **AnalyticsHelper** (utils/AnalyticsHelper.kt): Privacy-safe Firebase Analytics wrapper

### Generative AI Features
The app includes AI-powered features using Google's Gemini models with automatic fallback:
- **Architecture**: Smart backend selection (on-device Nano → cloud API)
  - **Gemini Nano**: On-device AI for privacy and speed (when available)
  - **Gemini 2.5 Flash**: Cloud API fallback for all devices
  - Automatic download and status tracking for Nano model
- **GenerativeAiRepository** (data/repository/GenerativeAiRepository.kt): Central AI functionality
  - `generateResponse()`: Chat with AI assistant
  - `generateSummary()`: TL;DR summaries of movie/show overviews
  - `analyzeViewingHabits()`: Mood analysis from watch history
  - `generateRecommendations()`: Personalized content suggestions
  - `smartSearchQuery()`: Natural language to search keywords
- **AiModule** (di/AiModule.kt): Configures AI models and handles Nano availability
  - Provides `@Named("primary-model")`: Nano with cloud fallback
  - Provides `@Named("pro-model")`: Gemini 2.5 Flash for complex reasoning
  - Background download tracking with progress updates
  - Error handling for 606 (config not ready), 601 (quota), 605 (download failed)
- **Remote Config Integration**: Feature flags and prompt customization
  - `enable_ai_features`: Toggle AI features on/off remotely
  - `ai_force_pro_model`: Force cloud model (Turbo Mode)
  - `ai_primary_model_name`, `ai_pro_model_name`: Customize model versions
  - `ai_chat_system_prompt`, `ai_summary_prompt_template`: Customize prompts
  - `ai_history_context_size`, `ai_recommendation_count`: Tune behavior
- **API Key Configuration**: Google AI API key for cloud fallback
  - Set in `gradle.properties` as `GOOGLE_AI_API_KEY=your_key_here`
  - Or via environment variable `GOOGLE_AI_API_KEY`
  - Falls back to empty string if not provided (on-device only)

### Firebase Integration
- **Firebase Crashlytics**: Crash reporting for production debugging
- **Firebase Performance Monitoring**: Track app performance metrics
- **Firebase App Check**: Protect backend from abuse (Play Integrity + debug provider)
- **Firebase Remote Config**: Feature flags, A/B testing, prompt customization
  - **RemoteConfigRepository**: Interface for feature flags
  - Default fetch interval: 12 hours (configurable)
  - Use for gradual rollouts and emergency kill switches
- **Firebase Analytics**: Privacy-safe usage analytics via AnalyticsHelper
  - No PII collection (media titles scrubbed)
  - Tracks AI usage, playback methods, cast sessions, UI interactions
- **Configuration**: Requires `google-services.json` in `app/` directory

## Documentation Resources

This codebase has extensive documentation in the `docs/` directory:
- **Central Index**: `docs/README.md` - Navigate all technical docs
- **Features**: `docs/features/` - UI improvements, immersive UI progress, feature flags
- **Development**: `docs/development/` - Testing guide, AI setup, contributing guidelines
- **Plans**: `docs/plans/` - Roadmap, current status, phase progress
- **Security**: `docs/security/` - TLS fixes and troubleshooting

Key documents for Claude Code users:
- `docs/development/TESTING_GUIDE.md` - ViewModel testing patterns (especially important)
- `docs/development/AI_SETUP.md` - AI feature configuration
- `docs/features/IMMERSIVE_UI_PROGRESS.md` - Immersive UI implementation status
- `docs/plans/CURRENT_STATUS.md` - Verified project status and features
- `docs/plans/ROADMAP.md` - Development roadmap and planned features

## CI/CD & Automation

### Gemini AI-Powered Issue Management
The repository uses automated workflows powered by Google's Gemini AI:
- **Auto-triaging**: Issues are automatically labeled when opened
- **Fix planning**: Maintainers can comment `/fix` to generate AI fix plans
- **Automated PRs**: Comment `/approve` on fix plans to create PRs with fixes
- **Auto-merge**: Comment `/approve` on PRs to merge approved changes
- **Workflows**: See `.github/workflows/gemini-*.yml` and `.github/GEMINI_README.md`
- **Commands**: `/fix`, `/approve`, `/deny` (maintainers only)
- **Security**: Only repository OWNER, MEMBER, and COLLABORATOR can use commands

### Standard CI Workflows
- ✅ Build verification on every push
- 🧪 Unit testing with detailed reports
- 🔍 Code quality checks (lint, security scans)
- 📦 Dependency monitoring (weekly updates via Renovate)
- 🚀 Automated releases on git tags

## Material 3 Design System

### Current Implementation
- Using Material 3 forced to 1.5.0-alpha13 (via `resolutionStrategy` in build.gradle.kts)
- Version catalog declares: material3: 1.5.0-alpha14, expressive: 1.5.0-alpha02, adaptive: 1.3.0-alpha08
- **Material 3 Expressive Components** enabled with official carousel implementation
- **Official Material 3 Carousel** (androidx.compose.material3:material3-carousel) for hero content
  - `HorizontalUncontainedCarousel` for hero carousel with auto-scrolling (15 second intervals)
  - Maintains consistent item sizes ideal for large media content
  - Uses `CarouselState` and `CarouselDefaults` for state management
- **Adaptive layouts** using Material 3 adaptive components (1.3.0-alpha08) for different screen sizes
- Theme defined in `ui/theme/` with Cinefin brand colors:
  - Primary: Jellyfin Purple (#6200EE)
  - Secondary: Jellyfin Blue (#2962FF)
  - Tertiary: Jellyfin Teal (#00695C)

### Material 3 Opt-ins
Compiler flags already configured for:
- `@OptIn(ExperimentalMaterial3Api::class)`
- `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`
- `@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)`

### Home Screen Layout
The home screen uses Material 3 Expressive components in the following order:
1. **Hero Carousel** - Auto-scrolling carousel (15s intervals) showcasing newest movies/shows (4-5 items)
2. **Continue Watching** - Horizontal list with vertical poster cards for partially watched items
3. **Next Up** - Horizontal list for next unwatched episodes (currently shows recent episodes)
4. **Recently Added in Movies** - Horizontal list with vertical poster cards
5. **Recently Added in Shows** - Horizontal list with vertical poster cards
6. **Recently Added in Stuff** - Horizontal list with horizontal cards for home videos
7. **Libraries** - Grid of available media libraries (at bottom)

## Immersive UI System

The app has a complete immersive UI layer (Netflix/Disney+ style) alongside the standard Material 3 screens, controlled by feature flags for gradual rollout.

### Architecture
- **13 immersive screens** in `ui/screens/Immersive*.kt` (Home, Movies, TV Shows, Library, Search, Favorites, detail screens for Movie/TV Show/TV Season/TV Episode/Home Video/Home Videos/Album)
- **11 reusable components** in `ui/components/immersive/` (hero carousel, parallax hero, media cards, auto-hide nav bars, scaffold, gradient scrims, floating action groups, top bar visibility, performance config)
- **Feature flag routing**: `core/FeatureFlags.kt` defines per-screen flags (e.g., `immersive_home_screen`, `immersive_movie_detail`) toggled via Firebase Remote Config

### Key Patterns

**Background Spacer Pattern** (prevents hero image bleed-through):
```kotlin
LazyColumn(modifier = Modifier.fillMaxSize()) {  // No .background() modifier!
    item(key = "background_spacer") {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.background))
    }
    // content items...
}
```

**Auto-Hide Top Bar** (`TopBarVisibility.kt`):
- Uses scroll delta tracking with 50px hysteresis
- `nearTopOffsetPx` must be >= hero height to avoid flicker
- Used via `rememberAutoHideTopBarVisible(lazyListState, nearTopOffsetPx, toggleThresholdPx)`

**Performance Optimization** (`ImmersivePerformanceConfig.kt`):
- Device-tier detection: LOW (<2GB RAM), MID (<4GB), HIGH (8GB+)
- Adaptive limits: hero items (3-10), row items (20-50), grid items (50-200)
- `rememberImmersivePerformanceConfig()` for one-line integration in any screen

### Common Pitfalls
- LazyColumn keys MUST be unique across all items (use indices for dynamic sections)
- Full-bleed hero content needs safe area padding (statusBars + topBar height) to avoid being cut off
- Use `graphicsLayer` for animations, not `scale()` modifier (better performance)
- `season.childCount` from Jellyfin API is often null; only show episode count when data is available

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

**Package Structure**: All code under `com.rpeters.jellyfin` in `app/src/main/java/com/rpeters/jellyfin/`

- **Application class**: `JellyfinApplication.kt` (Hilt entry point, initializes app-wide services)
- **Main activity**: `MainActivity.kt` (device type detection, setContent)
- **Root Compose phone app**: `ui/JellyfinApp.kt`
- **Root Compose TV app**: `ui/tv/TvJellyfinApp.kt`
- **Main home screen**: `ui/screens/HomeScreen.kt` (large file ~40KB with carousel)
- **Hilt modules**: `di/` directory (NetworkModule, Phase4Module, AiModule, RemoteConfigModule, etc.)
- **Repositories**: `data/repository/` (includes GenerativeAiRepository, RemoteConfigRepository)
- **AI infrastructure**: `data/ai/` (AiBackendStateHolder, AiTextModel interface)
- **ViewModels**: `ui/viewmodel/`
- **Reusable components**: `ui/components/`
- **Immersive UI components**: `ui/components/immersive/`
- **Immersive screens**: `ui/screens/Immersive*.kt`
- **Feature flags**: `core/FeatureFlags.kt`
- **Navigation**: `ui/navigation/`
- **Theme**: `ui/theme/`
- **Network layer**: `network/` directory
- **Utilities**: `utils/` directory (includes AnalyticsHelper, SecureLogger)
- **Firebase config**: `app/google-services.json` (not in version control)

## Known Limitations

1. **Next Up Feature**: Currently shows recent episodes; true "Next Up" (next unwatched episode per series) requires backend implementation
2. **Android TV**: Partial implementation exists, needs comprehensive D-pad testing
3. **Music Playback**: UI exists but playback controls incomplete
4. **Offline Downloads**: Screen exists but core functionality incomplete

Refer to `docs/plans/CURRENT_STATUS.md` for detailed feature status and `docs/plans/ROADMAP.md` for roadmap.

## Performance Optimization

- **NetworkOptimizer** (utils/NetworkOptimizer.kt) configures StrictMode and network settings
- **MainThreadMonitor** (utils/MainThreadMonitor.kt) tracks main thread impact in debug builds
- **ModernSurfaceCoordinator** (ui/surface/ModernSurfaceCoordinator.kt) manages surfaces for video playback
- **DevicePerformanceProfile** (ui/image/OptimizedImageLoader.kt) auto-detects device tier for optimal settings
- Memory optimization: LeakCanary 2.14 in debug builds, cache cleanup on low memory
- Image cache tuning based on device capabilities (LOW/MEDIUM/HIGH/FLAGSHIP tiers)

## Debugging & Troubleshooting

### Common Build Issues
- **"SDK location not found"**: Run `scripts/gen-local-properties.sh` (Linux/macOS) or `scripts/gen-local-properties.ps1` (Windows) to generate `local.properties`
- **Gradle sync failures**: Ensure JDK 21 is configured, check `JAVA_HOME` environment variable
- **Windows builds**: Always use `./gradlew.bat` or `gradlew.bat` instead of `./gradlew` in all commands
- **Missing SDK**: Run `./setup.sh` (Linux/macOS) to install Android SDK and accept licenses
- **Release signing errors**: Ensure signing credentials are configured in `gradle.properties` or environment variables
- **Firebase/Google Services plugin errors**: Ensure `google-services.json` exists in `app/` directory
- **AI features not working**: Set `GOOGLE_AI_API_KEY` in `gradle.properties` for cloud fallback

### Logging & Debugging
- Enable verbose logging: `SecureLogger.enableVerboseLogging = true` (default is false to reduce spam)
- Logcat filtering: Use tag `Jellyfin` or specific component tags
  ```bash
  # Capture logcat from connected device
  adb logcat -v time > latest_logcat

  # Filter for Jellyfin logs only
  adb logcat -v time | grep Jellyfin

  # Filter for AI-related logs
  adb logcat -v time | grep -E "GenerativeAi|AiModule"

  # Clear logcat before testing
  adb logcat -c
  ```
- Debug builds include LeakCanary 2.14 for memory leak detection
- Network traffic: Check `NetworkOptimizer` for StrictMode configuration
- Main thread monitoring: `MainThreadMonitor` tracks main thread impact in debug builds
- **Firebase Debugging**:
  - Crashlytics: View crash reports in Firebase Console
  - Remote Config: Use debug mode to fetch configs immediately (not cached)
  - Analytics: Enable debug logging with `adb shell setprop log.tag.FA VERBOSE`

### Testing Issues
- **Flow mocking failures**: Use `coEvery` instead of `every` for Flow properties
- **Coroutine tests timing out**: Use `StandardTestDispatcher` with `advanceUntilIdle()`
- **Mock not matching**: Add `any()` matchers for default parameters in repository methods
- See `docs/development/TESTING_GUIDE.md` for comprehensive testing patterns

## Code Quality Expectations

- **Test coverage target**: 70%+ for ViewModels and Repositories
- **Naming**: PascalCase for classes, camelCase for functions/vars, UPPER_SNAKE_CASE for constants
- **Indentation**: 4 spaces, ~120 char lines
- **Architecture**: Follow existing MVVM + Repository pattern
- **Null safety**: Use nullable types appropriately, avoid !! operator
- **Coroutines**: Launch in ViewModelScope, use structured concurrency
