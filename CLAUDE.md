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
- **Repository Layer**: `JellyfinRepository` handles all API interactions and caching
- **ViewModels**: Manage UI state and business logic (`MainAppViewModel`, `ServerConnectionViewModel`, etc.)
- **Secure Storage**: `SecureCredentialManager` for encrypted credential persistence
- **Client Factory**: `JellyfinClientFactory` manages API client instances and authentication
- **Data Models**: `JellyfinServer`, `ApiResult<T>` for structured data handling

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
- `app/src/main/java/com/example/jellyfinandroid/JellyfinApplication.kt` - Application class with Hilt setup
- `app/src/main/java/com/example/jellyfinandroid/MainActivity.kt` - Main activity with adaptive navigation

### Data Layer
- `app/src/main/java/com/example/jellyfinandroid/data/repository/JellyfinRepository.kt` - Central repository for API calls
- `app/src/main/java/com/example/jellyfinandroid/data/JellyfinServer.kt` - Server data models
- `app/src/main/java/com/example/jellyfinandroid/data/SecureCredentialManager.kt` - Encrypted credential storage

### Dependency Injection
- `app/src/main/java/com/example/jellyfinandroid/di/NetworkModule.kt` - Network-related dependencies (Jellyfin SDK, OkHttp)

### UI Layer
- `app/src/main/java/com/example/jellyfinandroid/ui/viewmodel/` - ViewModels for state management
- `app/src/main/java/com/example/jellyfinandroid/ui/screens/` - Compose screens for different features
- `app/src/main/java/com/example/jellyfinandroid/ui/theme/` - Material 3 theme definitions

### Navigation
- `app/src/main/java/com/example/jellyfinandroid/ui/navigation/AppDestinations.kt` - App navigation destinations

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
- **Image URLs**: Dynamic image URL generation with size constraints
- **Content Types**: Supports Movies, TV Shows, Music, Books, etc.
- **Recently Added**: Specialized endpoints for recent content by type

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

### Testing Strategy
- Unit tests for repository and business logic
- Mock external dependencies (network, storage)
- Focus on ViewModels and data transformation logic

## Dependencies Management

Dependencies are managed using Gradle version catalogs in `gradle/libs.versions.toml`. Key dependencies include:

### Core Android
- Jetpack Compose BOM (2025.06.01)
- Material 3 with adaptive navigation suite
- AndroidX core libraries and lifecycle components

### Jellyfin Integration
- Jellyfin SDK (1.6.8) for API communication
- Retrofit (3.0.0) with Kotlinx Serialization
- OkHttp (5.1.0) with logging interceptor

### Architecture
- Hilt (2.56.2) for dependency injection
- Kotlin Coroutines (1.10.2) for async operations
- DataStore Preferences for settings storage

## Development Notes

### Build Configuration
- **Kotlin**: 2.2.0 with Compose compiler plugin
- **Gradle**: 8.13 with Kotlin DSL
- **Java**: Target/Source compatibility Version 11
- **Android SDK**: Target 36, Min 31 (Android 12+)

### Code Style
- Follow Kotlin coding conventions from CONTRIBUTING.md
- Use 4 spaces for indentation, 120 character line length
- PascalCase for classes, camelCase for functions/variables
- Implement proper error handling and logging

### Security Considerations
- Never log sensitive information (tokens, passwords)
- Use SecureCredentialManager for credential storage
- Validate all user inputs and API responses
- Implement proper SSL/TLS certificate validation