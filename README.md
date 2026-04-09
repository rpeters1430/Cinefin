# Cinefin Android

> A refreshed, feature-rich Android Jellyfin client experience (formerly **Jellyfin Android Client**).

<p align="center">
  <img src="docs/assets/newicon-readme.png" alt="Cinefin featured icon" width="220" />
</p>

<p align="center">
  <a href="https://github.com/rpeters1430/Cinefin/actions/workflows/android-ci.yml">
    <img src="https://github.com/rpeters1430/Cinefin/actions/workflows/android-ci.yml/badge.svg" alt="Android CI" />
  </a>
  <a href="https://github.com/rpeters1430/Cinefin/actions/workflows/dependency-check.yml">
    <img src="https://github.com/rpeters1430/Cinefin/actions/workflows/dependency-check.yml/badge.svg" alt="Dependency Check" />
  </a>
  <a href="https://android-arsenal.com/api?level=26">
    <img src="https://img.shields.io/badge/API-26%2B-brightgreen.svg?style=flat" alt="API Level 26+" />
  </a>
  <img src="https://img.shields.io/badge/version-14.60-blue.svg?style=flat" alt="Version 14.60" />
  <img src="https://img.shields.io/badge/Kotlin-2.3.20-7F52FF.svg?style=flat&logo=kotlin" alt="Kotlin 2.3.20" />
</p>

A modern, beautiful Android client for Jellyfin media servers built with Material 3 Expressive design and the latest Android development technologies. Stream your personal media collection with AI-powered features, offline support, and an immersive cinema-style UI.

---

## 📋 Table of Contents

- [Features](#-features)
- [Technical Stack](#️-technical-stack)
- [Requirements](#-requirements)
- [Getting Started](#-getting-started)
- [Project Structure](#️-project-structure)
- [Key Components](#-key-components)
- [Development](#-development)
- [Design System](#-design-system)
- [Project Status](#-project-status)
- [Documentation](#-documentation)
- [Contributing](#-contributing)
- [Testing](#-testing-guidelines)
- [Security](#-security--configuration)
- [Privacy](#-privacy-policy)
- [License](#-license)
- [Support](#-support)

---

## ✨ Features

### 🎨 **Modern Material 3 Expressive Design**
- **Expressive theming** with dynamic colors, Jellyfin brand palette, and AMOLED dark mode
- **Immersive UI mode** — Netflix/Disney+ inspired cinema screens with parallax hero images
- **Adaptive navigation** that responds intelligently to phone, tablet, TV, and foldable form factors
- **Hero Carousel** with auto-scrolling (15-second intervals) showcasing newest content
- **Dark / Light / AMOLED** theme variants with seamless system integration

### 🎬 **Rich Media Experience**
- **Browse all media libraries** — Movies, TV Shows, Music, Home Videos, and more
- **Recently-added carousel** with cinematic 16:9 aspect ratio cards
- **High-quality poster / backdrop images** with smart device-tier-aware loading
- **Rich metadata** — ratings, years, overviews, genres, cast, and director
- **ExoPlayer video playback** with Direct Play detection, HLS/DASH adaptive bitrate, and subtitle support
- **Auto-play next episode** with countdown UI
- **Resume Playback** — automatic position tracking and seamless resumption
- **Adaptive Bitrate Monitoring** with real-time quality adjustment and configurable thresholds
- **Transcoding Diagnostics** — identify exactly why and how a stream is being transcoded

### 🤖 **AI-Powered Features**
- **AI Assistant** — chat with an in-app assistant about your media library (on-device Gemini Nano + cloud fallback)
- **AI Summaries** — instant TL;DR summaries of movie and show overviews
- **Viewing Mood Analysis** — AI analyses your watch history to detect trends and moods
- **Smart Search** — natural language queries converted to precise search keywords
- **Personalized Recommendations** — AI-generated content suggestions based on your habits

### 🔐 **Secure Authentication**
- **Multi-server support** — connect to and switch between multiple Jellyfin instances
- **Token-based auth** with automatic session refresh and reconnection
- **Quick Connect** — code-based login without entering a password
- **Biometric lock** — optional fingerprint / face authentication over stored credentials
- **Certificate pinning** — TOFU (Trust-on-First-Use) model for enhanced TLS security
- **Android Keystore** encryption (AES-256-GCM) for all stored credentials

### 📺 **Casting & Multitasking**
- **Chromecast** with full transport controls: seek bar, volume slider, and real-time position tracking
- **DLNA / UPnP** casting to compatible renderers on your local network
- **Picture-in-Picture** with play/pause and ±30 s skip actions
- **Background Audio** service with notification and lock-screen media controls (Music)

### 📥 **Offline Downloads**
- **Background downloads** via WorkManager with live progress notifications
- **Wi-Fi-only mode** to protect your mobile data
- **Storage management** — view, manage, and delete downloaded content
- **Offline playback** routing — seamlessly plays from local storage when a download is available
- **Download queue** — pause, resume, or cancel individual items

### 📱 **Modern Android Architecture**
- **Jetpack Compose** declarative UI throughout
- **MVVM + Repository pattern** with clean separation of concerns
- **Hilt dependency injection** for maintainable, testable code
- **Kotlin Coroutines + StateFlow** for structured async and reactive UI
- **Firebase** — Crashlytics, Performance Monitoring, Analytics, App Check, and Remote Config

---

## 🛠️ Technical Stack

### **Core Technologies**

| Component | Version | Notes |
|-----------|---------|-------|
| **Language** | Kotlin 2.3.20 | JDK 21 required |
| **UI Framework** | Jetpack Compose BOM 2026.03.01 | Material 3 Expressive |
| **Architecture** | MVVM + Repository | Hilt DI, StateFlow |
| **Dependency Injection** | Hilt 2.59.2 | |
| **Async** | Kotlin Coroutines 1.10.2 | |

### **Networking & API**

| Component | Version |
|-----------|---------|
| **Jellyfin SDK** | 1.8.8 |
| **HTTP Client** | Retrofit 3.0.0 + OkHttp 5.3.2 |
| **Serialization** | Kotlinx Serialization 1.11.0 |
| **Image Loading** | Coil 3.4.0 |

### **Media & UI**

| Component | Version |
|-----------|---------|
| **Media Playback** | ExoPlayer / Media3 1.10.0 + FFmpeg decoder |
| **Material Design** | Material 3 Expressive 1.5.0-alpha17 |
| **Navigation** | Navigation Compose 2.10.0-alpha02 |
| **Data Storage** | DataStore Preferences 1.3.0-alpha07 |
| **Paging** | Paging 3 3.5.0-beta01 |

---

## 📱 Requirements

- **Android 8.0** (API level 26) or higher
- **Active Jellyfin server** (version 10.8.0 or later recommended)
- **Internet connection** for streaming
- **Java 21** with core library desugaring enabled

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2) or later
- JDK 21
- **Compile SDK:** 36
- **Target SDK:** 35

### Building the Project

1. **Clone the repository:**
   ```bash
   git clone https://github.com/rpeters1430/Cinefin.git
   cd Cinefin
   ```

2. **Open in Android Studio:**
   - Import the project
   - Sync Gradle files
   - Wait for indexing to complete

3. **Build and run:**
   ```bash
   ./gradlew assembleDebug
   # Or use Android Studio's Run button
   ```

   ```bash
   ./gradlew installDebug    # Install on a connected device/emulator
   ```

### First Launch Setup

1. **Launch the app** on your device or emulator
2. **Enter your Jellyfin server URL** (e.g., `https://jellyfin.example.com`)
3. **Provide your credentials** (username and password, or use Quick Connect)
4. **Start browsing** your media collection!

### Optional: AI Features
To enable cloud AI fallback (Gemini 2.5 Flash), add your Google AI API key:
```properties
# gradle.properties or local.properties
GOOGLE_AI_API_KEY=your_api_key_here
```
On-device Gemini Nano works without an API key on supported devices.

---

## 🏗️ Project Structure

```
app/src/main/java/com/rpeters/jellyfin/
├── JellyfinApplication.kt       # Application class with Hilt
├── MainActivity.kt              # Main activity — device type detection
├── core/                        # Constants, feature flags, helpers
├── data/                        # Models, repositories, offline, playback, AI
│   ├── repository/              # Jellyfin SDK wrappers (media, auth, search…)
│   ├── offline/                 # Download manager, WorkManager workers
│   ├── playback/                # EnhancedPlaybackManager, bitrate monitor
│   └── ai/                      # AI backend state, model interfaces
├── di/                          # Hilt modules (Network, Phase4, AI, DataStore…)
├── network/                     # OkHttp interceptors, connectivity
├── ui/
│   ├── screens/                 # Feature screens (Home, Library, Player…)
│   ├── screens/Immersive*.kt    # Immersive cinema-style screen variants
│   ├── components/              # Reusable Compose components
│   ├── components/immersive/    # Hero carousel, parallax, gradient scrims
│   ├── player/                  # VideoPlayerActivity, cast, audio, PiP
│   ├── tv/                      # Android TV screens and navigation
│   ├── viewmodel/               # ViewModels for each screen
│   ├── navigation/              # Nav graphs and route definitions
│   └── theme/                   # Material 3 theme, colors, typography
└── utils/                       # SecureLogger, Analytics, DeviceUtils…
```

Additional paths:
- Module: `:app`
- Resources: `app/src/main/res`
- Manifest: `app/src/main/AndroidManifest.xml`
- Unit tests: `app/src/test/java`
- Instrumentation tests: `app/src/androidTest/java`

---

## 🎯 Key Components

### **🎠 Immersive Hero Carousel**
Full-bleed cinema-style hero carousel on the home screen:
- **Auto-scrolling** every 15 seconds with smooth transitions
- **Parallax backdrop** images with gradient scrims for text readability
- **Rich metadata** — title, year, rating, and genre pills
- **Tap to navigate** directly into the detail screen

### **🏠 Home Screen**
Personalized media dashboard:
- Hero Carousel → Continue Watching → Next Up → Recently Added (Movies, Shows, Home Videos) → Libraries
- **Pull-to-refresh** for instant content updates
- Performance-adaptive item limits (LOW → HIGH device tiers)

### **📚 Library Browser**
Browse all media collections with:
- **Visual library cards** with cover art and collection type badges
- **Lazy-loaded grids** with Paging 3 for large libraries
- **Filter & sort** controls per library

### **🤖 AI Assistant**
Conversational AI sidebar powered by Gemini:
- **On-device Nano** (private, fast) with automatic cloud fallback
- **Smart search** — type naturally, get precise results
- **Content summaries** and mood-based recommendations

### **⭐ Favorites & Search**
- **Dedicated Favorites screen** with quick-access filtering
- **Multi-library search** with type and genre filters
- **Instant results** as you type

### **🎬 Video Player**
Feature-complete ExoPlayer-backed player:
- **Material 3 Expressive controls** with gesture support (swipe to seek, drag for brightness/volume)
- **Track selection** — audio language and subtitle track picker
- **PiP mode**, Chromecast, and DLNA casting from within the player
- **Resume** from last position and **auto-play** next episode

---

## 🔧 Development

### **Build, Test, Lint, Coverage**

| Command | Description |
|---------|-------------|
| `./gradlew assembleDebug` | Build debug APK |
| `./gradlew installDebug` | Install on connected device/emulator |
| `./gradlew testDebugUnitTest` | Run JVM unit tests |
| `./gradlew connectedAndroidTest` | Run instrumentation tests (device required) |
| `./gradlew lintDebug` | Run Android Lint |
| `./gradlew jacocoTestReport` | Generate JaCoCo coverage report |
| `./gradlew ciTest` | Run all tests (unit + instrumentation) |

> **Windows**: Replace `./gradlew` with `./gradlew.bat` in all commands above.

### **CI / Environment Setup**
- Set `ANDROID_SDK_ROOT` (or `ANDROID_HOME`) to your SDK path.
- If no SDK is installed, run `./setup.sh` (Linux/macOS) to install cmdline-tools, accept licenses, and provision the SDK.
- Generate `local.properties`: `scripts/gen-local-properties.sh` (bash) or `scripts/gen-local-properties.ps1` (PowerShell).

### **Code Style & Naming**
- **Kotlin conventions**, 4-space indent, ~120 char line limit
- **Material 3** UI, unidirectional data flow, **MVVM** with ViewModels
- **DI via Hilt**; all data access via Repositories returning `ApiResult<T>`
- Names: Classes `PascalCase`, functions/vars `camelCase`, constants `UPPER_SNAKE_CASE`

### **CI/CD**
Automated workflows for:
- ✅ **Build verification** on every push
- 🧪 **Unit testing** with detailed reports
- 🔍 **Code quality** checks (lint, security scans)
- 📦 **Dependency monitoring** (weekly Renovate updates)
- 🚀 **Automated releases** on git tags
- 🤖 **AI-powered issue triage** via Gemini workflows (`/fix`, `/approve`)

---

## 🎨 Design System

### **Colors**
- **Primary:** Jellyfin Purple `#6200EE`
- **Secondary:** Jellyfin Blue `#2962FF`
- **Tertiary:** Jellyfin Teal `#00695C`
- **Dynamic theming** support for Android 12+ (Material You)
- **AMOLED** pure-black dark theme variant

### **Typography**
- **Material 3 type scale** throughout
- **Accessible contrast ratios** (WCAG AA)
- **Responsive text sizing** across all screen sizes

### **Components**
- **Cards:** Elevated with rounded corners, backdrop images
- **Buttons:** Material 3 filled, outlined, and tonal variants
- **Navigation:** Adaptive navigation suite (bottom bar → nav rail → nav drawer)
- **Loading:** Shimmer skeleton screens and progress indicators
- **Carousel:** Material 3 `HorizontalUncontainedCarousel` for hero content

---

## 📊 Project Status

### **What's Working Now (April 2026)** ✅

| Feature | Status |
|---------|--------|
| Authentication (multi-server, Quick Connect, biometric) | ✅ Complete |
| Video Playback (ExoPlayer, HLS/DASH, FFmpeg, Direct Play) | ✅ Complete |
| Adaptive Bitrate Monitoring | ✅ Complete |
| Transcoding Diagnostics | ✅ Complete |
| AI Assistant & AI Summaries | ✅ Complete |
| Library Browsing (Movies, TV, Music) | ✅ Complete |
| Search & Favorites | ✅ Complete |
| Resume Playback | ✅ Complete |
| Auto-Play Next Episode | ✅ Complete |
| Chromecast (seek, volume, position tracking) | ✅ Complete |
| DLNA / UPnP Casting | ✅ Complete |
| Picture-in-Picture | ✅ Complete |
| Offline Downloads | ✅ Complete |
| Material 3 Expressive UI (dark/light/AMOLED) | ✅ Complete |
| Immersive Cinema UI (parallax, hero carousel) | ✅ Complete |
| Firebase (Analytics, Crashlytics, Remote Config) | ✅ Complete |
| Secure Credential Storage (Keystore AES-256-GCM) | ✅ Complete |
| Certificate Pinning (TOFU) | ✅ Complete |

### **In Progress** 🔄

| Feature | Status |
|---------|--------|
| Music Background Playback (notification, lock-screen controls) | 🔄 In Progress |
| Android TV D-pad navigation & focus polish | 🔄 In Progress |

### **Planned** 📋

| Feature | Priority |
|---------|----------|
| Live TV & DVR | Low |
| Sync Play | Low |
| Multi-Profile Support | Low |
| Home Screen Widgets | Low |

**[📖 See detailed roadmap in docs/plans/ROADMAP.md](docs/plans/ROADMAP.md)**

---

## 📚 Documentation

For detailed information on features, development, and project planning, visit our **[Central Documentation Index](docs/README.md)**.

| Document | Description |
|----------|-------------|
| **[Current Status](docs/plans/CURRENT_STATUS.md)** | Verified feature status matrix |
| **[Roadmap](docs/plans/ROADMAP.md)** | Development roadmap and planned phases |
| **[Known Issues](docs/features/KNOWN_ISSUES.md)** | Active bugs with workarounds |
| **[Upgrade Path](docs/plans/UPGRADE_PATH.md)** | Dependency upgrade strategy |
| **[Immersive UI Progress](docs/features/IMMERSIVE_UI_PROGRESS.md)** | Immersive UI implementation tracking |
| **[AI Setup Guide](docs/development/AI_SETUP.md)** | Configuring AI features |
| **[Testing Guide](docs/development/TESTING_GUIDE.md)** | ViewModel testing patterns |
| **[Contributing](docs/development/CONTRIBUTING.md)** | Contribution guidelines |

---

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](docs/development/CONTRIBUTING.md) for details.

### **Development Setup**
1. Fork the repository
2. Create a feature branch (`feature/my-feature`)
3. Make your changes with tests where applicable
4. Submit a pull request with a clear description

### **Issue Reporting**
- Use the GitHub issue tracker
- Provide detailed reproduction steps
- Include device model, Android version, and app version
- Attach logcat output when possible

### **Conventional Commits**
Follow Conventional Commits for all commit messages:

| Prefix | Use For |
|--------|---------|
| `feat:` | New feature |
| `fix:` | Bug fix |
| `docs:` | Documentation only |
| `refactor:` | Code refactor (no feature/fix) |
| `test:` | Adding or updating tests |
| `chore:` | Maintenance tasks |

Examples: `feat: add movie detail screen`, `fix: prevent crash on empty library`

### **Branching**
- `feature/...` for new features
- `bugfix/...` for bug fixes
- `hotfix/...` for urgent fixes
- `docs/...` for documentation updates

### **PR Checklist**
- [ ] Clear description with linked issue(s)
- [ ] Screenshots/GIFs for UI changes
- [ ] Tests added/updated where applicable
- [ ] `./gradlew testDebugUnitTest` passes
- [ ] `./gradlew lintDebug` passes
- [ ] Docs updated if needed

---

## 🧪 Testing Guidelines
- Focus tests on ViewModel and Repository logic
- Use JUnit4, MockK, Turbine, and AndroidX Test
- Mock all network and I/O; avoid real server calls in unit tests
- Name tests descriptively: `loadMovieDetails_updatesState_onSuccess`
- Use `StandardTestDispatcher` with `advanceUntilIdle()` in coroutine tests
- Use `coEvery` (not `every`) when mocking Flows and suspend functions
- Coverage via `jacocoTestReport`; generated/DI classes are already excluded
- Hilt testing configured; use `HiltAndroidRule` and `HiltTestRunner` for instrumented tests

See **[Testing Guide](docs/development/TESTING_GUIDE.md)** for comprehensive patterns and examples.

---

## 🔒 Security & Configuration
- Never commit secrets or keystores — use Android Keystore / encrypted storage
- Network config: `app/src/main/res/xml/network_security_config.xml`
- Min SDK 26, Target SDK 35, Compile SDK 36
- Keep all dependency versions centralized in `gradle/libs.versions.toml`
- API keys in `gradle.properties` (local dev) or CI environment variables — never in source

---

## 🔐 Privacy Policy

This app respects your privacy:
- **No personal data collection** — no names, emails, or payment info collected
- **Local-only credentials** — server URLs and tokens stored only on your device (Android Keystore)
- **Direct server connection** — all media streaming flows directly between your device and your Jellyfin server
- **Limited analytics** — only crash reports and performance metrics (opt-out available) via Firebase

For full details, see our [Privacy Policy](privacy-policy.md).

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **Jellyfin Team** for the amazing open-source media server platform
- **Android & Material Teams** for Jetpack Compose and Material 3 Expressive
- **Community contributors** for feedback, bug reports, and pull requests

---

## 📞 Support

- **GitHub Issues:** [Report bugs and request features](https://github.com/rpeters1430/Cinefin/issues)
- **Jellyfin Community:** [Official Jellyfin forums](https://forum.jellyfin.org/)
- **API Documentation:** [Jellyfin API docs](https://api.jellyfin.org/)

---

**Last Updated:** April 9, 2026

**Made with ❤️ for the Jellyfin community**
