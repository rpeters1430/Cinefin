# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Jellyfin Android client application built with Kotlin and Jetpack Compose. It's a modern, feature-rich media streaming client that connects to Jellyfin servers, featuring Material 3 design with adaptive navigation, secure authentication, and comprehensive media browsing capabilities.

## Architecture

### Core Architecture
- **Pattern**: MVVM + Repository Pattern with Clean Architecture principles
- **UI Framework**: Jetpack Compose with Material 3
- **Dependency Injection**: Hilt for singleton and scoped dependencies
- **State Management**: StateFlow and Compose State for reactive UI updates
- **Navigation**: Adaptive navigation with `NavigationSuiteScaffold` for different screen sizes
- **Networking**: Jellyfin SDK + Retrofit with OkHttp for API communication
- **Authentication**: Token-based with secure credential storage using AndroidX Security

### Key Architectural Components
- **Repository Layer**: Modular repository pattern with specialized repositories:
  - `JellyfinRepository` - Main coordinator for API interactions
  - `JellyfinAuthRepository` - Authentication and server management
  - `JellyfinStreamRepository` - Media streaming and image URL generation
  - `JellyfinMediaRepository` - Media content and metadata
  - `JellyfinSearchRepository` - Search functionality
  - `JellyfinUserRepository` - User management and preferences
- **Enhanced Playback System**:
  - `EnhancedPlaybackManager` - Intelligent Direct Play vs transcoding decision engine
  - `DeviceCapabilities` - Real-time codec detection and performance profiling
  - `EnhancedPlaybackUtils` - UI-friendly wrapper for playback functionality
  - `PlaybackRecommendationViewModel` - Manages playback recommendations and notifications
- **ViewModels**: Manage UI state and business logic (`MainAppViewModel`, `ServerConnectionViewModel`, etc.)
- **Secure Storage**: `SecureCredentialManager` for encrypted credential persistence with biometric support
- **Client Factory**: `OptimizedClientFactory` manages API client instances with token-based authentication
- **Data Models**: `JellyfinServer`, `ApiResult<T>`, `PlaybackResult<T>` for structured data handling
- **Error Handling**: Centralized `ErrorHandler` with comprehensive error types and retry mechanisms

## Common Development Commands

### Build Commands
```bash
./gradlew build                    # Build the entire project
./gradlew assemble                 # Assemble main outputs for all variants
./gradlew assembleDebug           # Build debug APK
./gradlew assembleRelease         # Build release APK
./gradlew clean                   # Clean build directory
```

### Testing Commands
```bash
./gradlew test                    # Run all unit tests
./gradlew testDebugUnitTest       # Run debug unit tests
./gradlew testReleaseUnitTest     # Run release unit tests
./gradlew connectedAndroidTest    # Run instrumentation tests on connected devices
./gradlew connectedDebugAndroidTest # Run debug instrumentation tests
./gradlew ciTest                  # Run CI test suite (unit + instrumentation tests)
```

### Code Quality Commands
```bash
./gradlew lint                    # Run lint checks
./gradlew lintDebug              # Run lint on debug variant
./gradlew lintRelease            # Run lint on release variant
./gradlew lintFix                # Apply safe lint suggestions automatically
./gradlew check                  # Run all verification tasks
```

## Key Architecture Files

### Application Layer
- `app/src/main/java/com/rpeters/jellyfin/JellyfinApplication.kt` - Application class with Hilt setup and performance optimizations
- `app/src/main/java/com/rpeters/jellyfin/MainActivity.kt` - Main activity with adaptive navigation

### Data Layer
- `app/src/main/java/com/rpeters/jellyfin/data/repository/JellyfinRepository.kt` - Central repository for API calls
- `app/src/main/java/com/rpeters/jellyfin/data/JellyfinServer.kt` - Server data models
- `app/src/main/java/com/rpeters/jellyfin/data/SecureCredentialManager.kt` - Encrypted credential storage with biometric support
- `app/src/main/java/com/rpeters/jellyfin/data/repository/JellyfinRepositoryCoordinator.kt` - Repository coordination and delegation
- `app/src/main/java/com/rpeters/jellyfin/data/cache/OptimizedCacheManager.kt` - Performance-optimized caching
- `app/src/main/java/com/rpeters/jellyfin/data/playback/EnhancedPlaybackManager.kt` - Advanced playback decision engine
- `app/src/main/java/com/rpeters/jellyfin/data/DeviceCapabilities.kt` - Device codec and performance analysis

### Dependency Injection
- `app/src/main/java/com/rpeters/jellyfin/di/NetworkModule.kt` - Network-related dependencies (Jellyfin SDK, OkHttp)
- `app/src/main/java/com/rpeters/jellyfin/di/OptimizedClientFactory.kt` - Optimized API client factory
- `app/src/main/java/com/rpeters/jellyfin/di/Phase4Module.kt` - Phase 4 dependency injection modules

### UI Layer
- `app/src/main/java/com/rpeters/jellyfin/ui/viewmodel/` - ViewModels for state management
- `app/src/main/java/com/rpeters/jellyfin/ui/screens/` - Compose screens for different features
- `app/src/main/java/com/rpeters/jellyfin/ui/theme/` - Material 3 theme definitions
- `app/src/main/java/com/rpeters/jellyfin/ui/components/` - Reusable UI components including expressive and accessible variants
- `app/src/main/java/com/rpeters/jellyfin/ui/components/PlaybackRecommendationNotifications.kt` - Playback recommendation UI system
- `app/src/main/java/com/rpeters/jellyfin/ui/utils/EnhancedPlaybackUtils.kt` - Enhanced playback utilities for UI integration

### Navigation
- `app/src/main/java/com/rpeters/jellyfin/ui/navigation/AppDestinations.kt` - App navigation destinations
- `app/src/main/java/com/rpeters/jellyfin/ui/navigation/NavGraph.kt` - Navigation graph configuration
- `app/src/main/java/com/rpeters/jellyfin/ui/navigation/NavRoutes.kt` - Route definitions and parameters

## API Integration Patterns

### Error Handling
The app uses a comprehensive `ApiResult<T>` sealed class with specific error types:
- `ApiResult.Success<T>` - Successful API response
- `ApiResult.Error<T>` - Error with detailed error type and message
- `ApiResult.Loading<T>` - Loading state indication

### Authentication Flow
1. **Connection Testing**: Server URL validation before authentication
2. **Token-based Auth**: Uses Jellyfin's authentication system
3. **Credential Persistence**: Secure storage with AndroidX Security
4. **Auto-Reconnection**: Automatic token refresh on 401 errors

### Media Loading Patterns
- **Lazy Loading**: Paginated content with startIndex/limit parameters
- **Image URLs**: Dynamic image URL generation with size constraints and backdrop support
- **Content Types**: Supports Movies, TV Shows, Episodes, Music, Books, Audiobooks, Videos
- **Recently Added**: Specialized endpoints for recent content by type with configurable limits
- **Streaming**: Multiple format support (direct, transcoded, HLS, DASH) with quality adaptation
- **Offline Support**: Download management with `OfflineDownloadManager` and playback capabilities
- **Cast Integration**: Google Cast framework support with Media3 for Chromecast

## Enhanced Playback System

### Intelligent Direct Play Detection
The app uses an advanced playback decision engine that automatically determines the optimal playback method:

- **Real-time Codec Analysis**: `DeviceCapabilities` analyzes device support for video/audio codecs using `MediaCodecList`
- **Network Quality Assessment**: Dynamic network conditions evaluation for streaming decisions
- **Performance Profiling**: Device tier detection (HIGH_END, MID_RANGE, LOW_END) for optimal quality selection
- **Playback Result Types**: Sealed class hierarchy (`PlaybackResult.DirectPlay`, `PlaybackResult.Transcoding`, `PlaybackResult.Error`)

### Playback Recommendation System
- **User Notifications**: Proactive recommendations displayed as non-intrusive notifications
- **Capability Analysis**: `PlaybackCapabilityAnalysis` provides detailed technical insights
- **Recommendation Types**: Categorized as Optimal, Info, Warning, or Error with appropriate UI styling
- **Smart Recommendations**: Context-aware suggestions based on network conditions and device capabilities

### UI Integration
- **Status Indicators**: Visual badges showing Direct Play/Transcoding status on media cards
- **Detail Screen Enhancement**: Comprehensive playback capability display with technical details
- **Recommendation Notifications**: Multiple display modes (floating overlay, in-context, compact status)
- **Quality Indicators**: Animated network quality indicators with bandwidth estimation

## UI Components and Patterns

### Compose Architecture
- **State Hoisting**: UI state managed at appropriate levels
- **Reusable Components**: `MediaCards.kt`, `LibraryItemCard.kt` for consistent UI
- **Loading States**: Skeleton screens and progress indicators
- **Error Handling**: Consistent error display with retry mechanisms

### Material 3 Implementation
- **Dynamic Colors**: System-aware theming with Jellyfin brand colors
- **Adaptive Navigation**: Responsive navigation suite for different screen sizes
- **Carousel Components**: Material 3 carousel for media browsing
- **Typography**: Consistent text styling with Material 3 type scale

## Development Patterns

### State Management
- Use `StateFlow` for ViewModels and data streams
- Leverage `collectAsState()` in Compose for reactive UI updates
- Implement loading, success, and error states consistently

### Error Handling
- Always wrap API calls in try-catch blocks
- Use `handleException()` in repository for consistent error mapping
- Implement retry mechanisms for network failures
- Use `PlaybackResult.Error` for playback-specific error handling
- Gracefully handle codec analysis failures with fallback values

### Enhanced Playback Integration
- Always use `EnhancedPlaybackUtils` for media playback initiation
- Pass `enhancedPlaybackUtils` parameter to media card components for status indicators
- Handle playback recommendations through `PlaybackRecommendationViewModel`
- Use `PlaybackCapabilityAnalysis` for detailed technical information display
- Implement playback status indicators on all media browsing screens

### Testing Strategy
- Unit tests for repository and business logic using JUnit 4 and MockK
- Mock external dependencies (network, storage) with MockK framework
- Focus on ViewModels and data transformation logic
- Instrumentation tests for UI components using Espresso
- Architecture Core Testing for LiveData and coroutines
- Test files organized by feature in corresponding test directories

## Dependencies Management

Dependencies are managed using Gradle version catalogs in `gradle/libs.versions.toml`. Key dependencies include:

### Core Android
- Jetpack Compose BOM (2025.08.01)
- Material 3 (1.5.0-alpha03) with adaptive navigation suite and expressive components
- AndroidX core libraries and lifecycle components
- Media3 (1.8.0) for video playback with ExoPlayer and Jellyfin FFmpeg decoder
- Coil (2.7.0) for image loading
- Paging 3 (3.4.0-alpha03) for paginated content loading

### Jellyfin Integration
- Jellyfin SDK (1.6.8) for API communication
- Retrofit (3.0.0) with Kotlinx Serialization
- OkHttp (5.1.0) with logging interceptor
- SLF4J Android (1.7.36) for SDK logging

### Architecture
- Hilt (2.57.1) for dependency injection
- Kotlin Coroutines (1.10.2) for async operations
- DataStore Preferences (1.2.0-alpha02) for settings storage
- AndroidX Security (1.1.0) for encrypted credential storage
- AndroidX Biometric (1.4.0-alpha04) for biometric authentication
- AndroidX TV Material (1.1.0-alpha01) for Android TV support

## Development Notes

### Build Configuration
- **Kotlin**: 2.2.10 with Compose compiler plugin
- **Gradle**: 8.12.2 (AGP) with Kotlin DSL
- **Java**: Target/Source compatibility Version 17
- **Android SDK**: Compile 36, Target 35, Min 26 (Android 8.0+) for broader device compatibility
- **Package**: `com.rpeters.jellyfin` (actual package structure)

### Code Style
- Follow Kotlin coding conventions from CONTRIBUTING.md
- Use 4 spaces for indentation, 120 character line length
- PascalCase for classes, camelCase for functions/variables
- Implement proper error handling and logging
- Use SecureLogger for all logging to prevent sensitive information leakage
- Prefer data classes for model objects and sealed classes for state management

### Commit Conventions
Follow Conventional Commits specification:
- `feat:` new feature (e.g., `feat: add movie detail screen with cast information`)
- `fix:` bug fix (e.g., `fix: resolve crash when loading empty library`)
- `docs:` documentation only changes
- `refactor:` code change that neither fixes a bug nor adds a feature
- `test:` adding missing tests or correcting existing tests
- `perf:` performance improvements

### Branch Naming
- `feature/description` - for new features
- `bugfix/description` - for bug fixes  
- `hotfix/description` - for urgent fixes
- `docs/description` - for documentation updates

### Security Considerations
- Never log sensitive information (tokens, passwords)
- Use SecureCredentialManager for credential storage with AndroidKeyStore
- Validate all user inputs and API responses
- Implement proper SSL/TLS certificate validation
- Use biometric authentication where available
- Follow secure coding practices for network communication

### Constants and Configuration
- Application constants centralized in `Constants.kt`
- API retry limits, timeout configurations, and pagination constants
- Image size constraints and streaming quality defaults
- Token expiration handling with proactive refresh (50-minute validity)
- Performance monitoring and optimization configurations
- Accessibility support configurations in `AccessibilityExtensions.kt`

### Media Player Integration
- ExoPlayer integration through Media3 framework
- Expressive video controls with playback progress management
- Cast support with `CastManager` and `CastOptionsProvider`
- Track selection management for audio/subtitle streams
- Dedicated `VideoPlayerActivity` for full-screen playback