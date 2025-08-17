# QWEN.md - Project Context for Jellyfin Android

This document provides an overview of the Jellyfin Android project for use as context in future interactions.

## Project Overview

This is a modern Android client for Jellyfin media servers. The application is built using Jetpack Compose with Material 3 design principles and follows contemporary Android development practices.

### Key Features
- Material 3 design with adaptive navigation
- Server connection and authentication (including Quick Connect)
- Media library browsing (Movies, TV Shows, Music)
- Detailed media screens with metadata
- Search functionality
- Favorites management
- Media playback (using Media3/ExoPlayer)
- Offline download capabilities

### Technology Stack
- **Language:** Kotlin 2.2.0
- **UI Framework:** Jetpack Compose (2025.06.01 BOM)
- **Architecture:** MVVM + Repository Pattern
- **Dependency Injection:** Hilt 2.56.2
- **Networking:** Retrofit 3.0.0, OkHttp 4.12.0, Kotlinx Serialization 1.9.0
- **Image Loading:** Coil 2.7.0
- **Media Playback:** Media3 (ExoPlayer) 1.7.1
- **Navigation:** Navigation Compose 2.9.1
- **Data Storage:** DataStore Preferences 1.1.7

### Project Structure
```
app/src/main/java/com/example/jellyfinandroid/
├── 📱 MainActivity.kt              # Main activity with navigation
├── 🚀 JellyfinApplication.kt       # Application class with Hilt
├── 📊 data/                        # Data models and repository
├── 🌐 network/                     # Network layer (Retrofit API definitions)
├── 💉 di/                          # Hilt dependency injection modules
├── 🎨 ui/                          # Compose UI layer
│   ├── components/                 # Reusable UI components
│   ├── navigation/                 # Navigation graph and routes
│   ├── screens/                    # Individual screen composables
│   ├── theme/                      # Material 3 theming
│   ├── viewmodel/                  # ViewModel classes
│   └── utils/                      # UI-related utilities
└── utils/                          # General utilities (logging, etc.)
```

## Building and Running

### Prerequisites
- Android Studio Iguana or later
- JDK 17
- Compile SDK: 36
- Target SDK: 36

### Setup
1. Clone the repository
2. Open in Android Studio
3. Sync Gradle files
4. Wait for indexing to complete

### Build Commands
```bash
# Assemble debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest

# Run lint checks
./gradlew lintDebug
```

## Development Conventions

- **Architecture:** MVVM pattern with ViewModels and StateFlow for state management
- **UI:** Jetpack Compose with Material 3 components
- **Navigation:** Single-activity architecture using Navigation Compose
- **Dependency Injection:** Hilt for managing dependencies
- **Networking:** Retrofit with Kotlinx Serialization for API calls
- **Data Management:** Repository pattern to abstract data sources
- **Error Handling:** Comprehensive error handling with user-friendly messages
- **Security:** Secure storage for credentials using Android Security library
- **Logging:** Custom `SecureLogger` utility for debugging

## Key Components

### Navigation
The app uses a single-activity architecture with `NavHost` in `ui/navigation/NavGraph.kt` defining all routes and screen compositions. Authentication state determines the initial destination.

### State Management
ViewModels (in `ui/viewmodel/`) expose state via StateFlow. UI components collect these states using `collectAsStateWithLifecycle()` to ensure proper lifecycle handling.

### Data Layer
The `data/` package contains repository implementations that interact with the Jellyfin SDK and manage data fetching/caching.

### UI Layer
- Screens are located in `ui/screens/`
- Reusable components in `ui/components/`
- Theming in `ui/theme/` with dynamic color support

## Testing
Unit tests are implemented using JUnit and MockK. Instrumentation tests use AndroidX Test libraries.

To run tests:
```bash
./gradlew testDebugUnitTest
```