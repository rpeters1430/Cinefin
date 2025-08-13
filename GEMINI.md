# GEMINI.md - Jellyfin Android Client

## Project Overview

This is the repository for the **Jellyfin Android Client**, a modern, beautiful Android application for interacting with Jellyfin media servers. It's built using cutting-edge Android development technologies, emphasizing a Material 3 design and a robust architecture.

### Key Features

*   **Modern Material 3 Design**: Features dynamic theming, adaptive navigation, and beautiful carousels.
*   **Rich Media Experience**: Browse libraries with high-quality visuals and metadata.
*   **Secure Authentication**: Token-based authentication with multi-server support.
*   **Modern Architecture**: Built with Jetpack Compose, MVVM, Hilt, and Kotlin Coroutines.

### Core Technologies

*   **Language**: Kotlin 2.2.0
*   **UI Framework**: Jetpack Compose (2025.06.01 BOM)
*   **Architecture**: MVVM + Repository Pattern
*   **Dependency Injection**: Hilt 2.57
*   **Async Programming**: Kotlin Coroutines 1.10.2
*   **Networking & API**: Jellyfin SDK 1.6.8, Retrofit 3.0.0, Kotlinx Serialization 1.9.0
*   **Image Loading**: Coil 2.7.0
*   **Media Playback**: Media3 (ExoPlayer) 1.7.1 (Ready for implementation)

## Building and Running

### Prerequisites

*   Android Studio Iguana or later
*   JDK 17
*   Compile SDK: 36
*   Target SDK: 36

### Setup

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/rpeters1430/JellyfinAndroid.git
    cd JellyfinAndroid
    ```
2.  **Open in Android Studio**: Import the project and sync Gradle files.

### Build Commands

*   **Assemble Debug APK**:
    ```bash
    ./gradlew assembleDebug
    ```
*   **Run Unit Tests**:
    ```bash
    ./gradlew testDebugUnitTest
    ```
*   **Run Lint Checks**:
    ```bash
    ./gradlew lintDebug
    ```

### Running the App

After building, launch the app on a device or emulator. You will need to provide the URL of your Jellyfin server and your login credentials.

## Development Conventions

*   **Architecture**: Follows the MVVM pattern with a clear separation of concerns (UI, ViewModel, Repository, Data/Network).
*   **UI**: Built entirely with Jetpack Compose, adhering to Material 3 design principles.
*   **Dependency Injection**: Uses Hilt for managing dependencies across the application.
*   **State Management**: Utilizes `StateFlow` and `collectAsState` for reactive UI updates.
*   **Navigation**: Implemented with Navigation Compose.
*   **Coding Style**: Follows standard Kotlin coding conventions. The project uses Kotlin 2.2.0.

## Project Structure (Key Files & Directories)

*   `app/`: Main application module.
    *   `build.gradle.kts`: Module-level build configuration, including dependencies.
    *   `src/main/java/com/example/jellyfinandroid/`: Main source code.
        *   `JellyfinApplication.kt`: Hilt-enabled application class.
        *   `MainActivity.kt`: Entry point activity, sets up Compose content.
        *   `ui/`: Contains all Compose UI code.
            *   `JellyfinApp.kt`: Root Compose component, sets up theme, navigation, and scaffold.
            *   `navigation/`: Handles app navigation with `NavController`.
            *   `screens/`: Individual Compose screens (e.g., `ServerConnectionScreen.kt`).
            *   `components/`: Reusable UI components (e.g., `BottomNavBar.kt`).
            *   `theme/`: Material 3 theme definitions (Colors, Typography, Shapes).
            *   `viewmodel/`: ViewModels managing UI state (e.g., `ServerConnectionViewModel.kt`).
        *   `data/`: Data models and repositories.
        *   `network/`: Networking layer, likely using Retrofit with the Jellyfin SDK.
        *   `di/`: Hilt dependency injection modules.
*   `build.gradle.kts`: Top-level build configuration.
*   `settings.gradle.kts`: Project settings, including plugin versions.
*   `gradle.properties`: Project-wide Gradle properties.

This context file provides a foundational understanding for interacting with and developing the Jellyfin Android Client codebase.