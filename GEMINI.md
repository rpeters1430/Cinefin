# Gemini Context: Cinefin Android Client

## 1. Project Overview
This is a modern Android client for Jellyfin media servers (branded as Cinefin), built with **Jetpack Compose** and **Material 3 Expressive Design**. It follows a clean **MVVM architecture** with **Hilt** dependency injection.

**Key Technologies:**
- **Language:** Kotlin 2.3.10 (JDK 21 required)
- **UI:** Jetpack Compose (BOM 2026.01.01), Material 3 Expressive (Alpha)
- **Architecture:** MVVM + Repository Pattern
- **Dependency Injection:** Hilt 2.59.1
- **Async:** Kotlin Coroutines 1.10.2 + StateFlow
- **Media:** ExoPlayer (Media3 1.9.1) + FFmpeg decoder
- **Networking:** Retrofit 3.0.0 + OkHttp 5.3.2 + Jellyfin SDK 1.8.6
- **Image Loading:** Coil 3.3.0

## 2. Operational Environment (Windows/Gemini)
- **System:** Windows (`win32`).
- **Shell Commands:** Always use `gradlew.bat` (or just `gradlew`) instead of `./gradlew`.
- **Java:** Requires JDK 21.

## 3. Build & Development Commands

| Task | Command (Windows) | Notes |
|------|-------------------|-------|
| **Build Debug APK** | `gradlew assembleDebug` | Outputs to `app/build/outputs/apk/debug` |
| **Install on Device** | `gradlew installDebug` | Requires connected device/emulator |
| **Unit Tests (JVM)** | `gradlew testDebugUnitTest` | **Preferred** for logic verification |
| **Instrumentation** | `gradlew connectedAndroidTest` | Uses `HiltTestRunner` |
| **Lint** | `gradlew lintDebug` | Report: `app/build/reports/lint` |
| **Coverage** | `gradlew jacocoTestReport` | Report: `app/build/reports` |
| **CI Test** | `gradlew ciTest` | Runs both Unit & Instrumentation tests |

## 4. Architecture & Conventions

### Directory Structure (`app/src/main/java/com/rpeters/jellyfin/`)
- **`ui/`**: Compose screens, navigation, and ViewModels.
  - `screens/`: Feature screens (e.g., `HomeScreen.kt`).
  - `components/`: Reusable UI elements.
  - `theme/`: Material 3 theme definitions.
- **`data/`**: Data layer.
  - `repository/`: Repositories wrapping the Jellyfin SDK (e.g., `JellyfinRepository`).
  - `model/`: Data classes.
- **`di/`**: Hilt modules (`NetworkModule`, `Phase4Module`, etc.).
- **`utils/`**: Utility classes (`SecureLogger`, `DeviceTypeUtils`).

### Key Architectural Patterns
- **State Management:** ViewModels expose `StateFlow<UiState>`. UI collects using `collectAsStateWithLifecycle()`.
- **Navigation:**
  - **Phone/Tablet:** `ui/navigation/NavGraph.kt` (Bottom Nav).
  - **TV:** `ui/navigation/TvNavGraph.kt`.
  - **Device Detection:** `DeviceTypeUtils.getDeviceType()` in `MainActivity.kt`.
- **Data Access:** All SDK calls MUST go through Repositories (`JellyfinRepository`, `JellyfinAuthRepository`). Returns `ApiResult<T>`.
- **Security:**
  - **Storage:** `SecureCredentialManager` (Android Keystore).
  - **Network:** `network_security_config.xml` + TOFU Certificate Pinning.
  - **Logging:** `SecureLogger` (strips PII).
  - **URL Safety:** API tokens removed from URL query parameters (CWE-598).

### Testing Guidelines
- **Mocking:**
  - Use `coEvery` for Flows/Suspend functions (NOT `every`).
  - Use `any()` for default parameters in repositories.
- **Coroutines:** Use `StandardTestDispatcher` with `advanceUntilIdle()` in `runTest`.
- **Coverage:** Target 70%+ for ViewModels.

## 5. Development Status (Updated Feb 5, 2026)
- **Active Development:**
  - Music Background Playback.
  - Offline Downloads (Core logic).
  - Android TV D-pad Navigation.
- **Stable Features:**
  - Authentication (Multi-server, Quick Connect).
  - Video Playback (ExoPlayer, PiP, HLS/DASH).
  - Chromecast (Enhanced with Seek/Volume/Tracking).
  - Material 3 Adaptive UI.
  - Adaptive Bitrate Monitoring & Quality Control.
  - Transcoding Diagnostics Tool.
  - Firebase Integration (Analytics, Config, Crashlytics).
  - AI Assistant & AI Summaries.
  - DNS Resolution Error Handling.
- **Security Enhancements:**
  - Authorization headers used instead of URL tokens.
  - TOFU Certificate Pinning.

## 6. Commit & Contribution
- **Format:** Conventional Commits (`feat:`, `fix:`, `docs:`, `refactor:`).
- **Safety:** Never commit secrets.
- **Refactoring:** Prioritize refactoring large composables (`HomeScreen.kt`, `VideoPlayerScreen.kt`).
