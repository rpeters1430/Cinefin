# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Jellyfin Android client application built with Kotlin and Jetpack Compose. The app uses Material 3 design with adaptive navigation suite for different screen sizes.

## Architecture

- **Build System**: Gradle with Kotlin DSL (`.gradle.kts` files)
- **UI Framework**: Jetpack Compose with Material 3
- **Navigation**: Uses `NavigationSuiteScaffold` for adaptive navigation across different screen sizes
- **Package Structure**: `com.example.jellyfinandroid` (currently example package, will likely be changed)
- **Target SDK**: Android 36 (API level 36)
- **Min SDK**: Android 31 (API level 31)

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

## Key Files and Structure

- `app/src/main/java/com/example/jellyfinandroid/MainActivity.kt` - Main activity with Compose UI
- `app/src/main/java/com/example/jellyfinandroid/ui/theme/` - Theme definitions (Color, Theme, Type)
- `gradle/libs.versions.toml` - Centralized dependency management using version catalogs
- `app/build.gradle.kts` - App-level build configuration
- `build.gradle.kts` - Root project build configuration

## UI Components

The app currently implements:
- Adaptive navigation using `NavigationSuiteScaffold`
- Three main destinations: Home, Favorites, Profile
- Material 3 theming system
- Edge-to-edge display support

## Dependencies Management

Dependencies are managed using Gradle version catalogs in `gradle/libs.versions.toml`. Key dependencies include:
- Jetpack Compose BOM for UI components
- Material 3 with adaptive navigation suite
- AndroidX core libraries
- Testing frameworks (JUnit, Espresso)

## Development Notes

- The project uses Kotlin 2.0.21 with Compose compiler plugin
- Gradle wrapper version: 8.13
- Java compatibility: Version 11
- All Compose previews are available for UI development
- ProGuard is configured but disabled for debug builds