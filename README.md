# Jellyfin Android Client

[![Android CI](https://github.com/rpeters1430/JellyfinAndroid/actions/workflows/android-ci.yml/badge.svg)](https://github.com/rpeters1430/JellyfinAndroid/actions/workflows/android-ci.yml)
[![Dependency Check](https://github.com/rpeters1430/JellyfinAndroid/actions/workflows/dependency-check.yml/badge.svg)](https://github.com/rpeters1430/JellyfinAndroid/actions/workflows/dependency-check.yml)
[![API Level](https://img.shields.io/badge/API-26%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=26)

A modern, beautiful Android client for Jellyfin media servers built with Material 3 design principles and the latest Android development technologies.

## ✨ Features

### 🎨 **Modern Material 3 Design**
- **Expressive theming** with dynamic colors and Jellyfin brand colors
- **Adaptive navigation** that responds to different screen sizes
- **Beautiful carousel** showcasing recently added content
- **Dark/Light theme** support with system integration

### 🎬 **Rich Media Experience**
- **Browse media libraries** with stunning visual cards
- **Recently added carousel** with cinematic 16:9 aspect ratio cards
- **High-quality poster/backdrop** images with smart loading
- **Metadata display** including ratings, years, and descriptions

### 🔐 **Secure Authentication**
- **Server connection testing** before authentication
- **Token-based authentication** with automatic session management
- **Multi-server support** (connect to different Jellyfin instances)
- **Quick Connect** support

### 📱 **Modern Android Architecture**
- **Jetpack Compose** for declarative UI
- **MVVM pattern** with ViewModels and StateFlow
- **Hilt dependency injection** for clean architecture
- **Kotlin Coroutines** for asynchronous operations

## 🛠️ Technical Stack

### **Core Technologies**
- **Language:** Kotlin 2.2.10
- **UI Framework:** Jetpack Compose (2025.08.01 BOM)
- **Architecture:** MVVM + Repository Pattern
- **Dependency Injection:** Hilt 2.57.1
- **Async Programming:** Kotlin Coroutines 1.10.2

### **Networking & API**
- **Jellyfin SDK:** 1.6.8 (Official Jellyfin Kotlin SDK)
- **HTTP Client:** Retrofit 3.0.0 + OkHttp 5.1.0
- **Serialization:** Kotlinx Serialization 1.9.0
- **Image Loading:** Coil 2.7.0 with Compose integration

### **Media & UI**
- **Media Playback:** ExoPlayer (Media3 1.8.0) with Jellyfin FFmpeg decoder
- **Material Design:** Material 3 with Carousel support
- **Navigation:** Navigation Compose 2.9.3
- **Data Storage:** DataStore Preferences 1.2.0-alpha02

## 📱 Requirements

- **Android 8.0** (API level 26) or higher
- **Active Jellyfin server** (version 10.8.0 or later recommended)
- **Internet connection** for streaming
- **Java 17** with core library desugaring enabled

## 🚀 Getting Started

### Prerequisites
- Android Studio Iguana or later
- JDK 17
- **Compile SDK:** 36
- **Target SDK:** 36

### Building the Project

1. **Clone the repository:**
   ```bash
   git clone https://github.com/rpeters1430/JellyfinAndroid.git
   cd JellyfinAndroid
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
   
   Optional:
   ```bash
   ./gradlew installDebug    # Install on a connected device/emulator
   ```

### First Launch Setup

1. **Launch the app** on your device/emulator
2. **Enter your Jellyfin server URL** (e.g., `https://jellyfin.example.com`)
3. **Provide your credentials** (username and password)
4. **Start browsing** your media collection!

## 🏗️ Project Structure

```
app/src/main/java/com/rpeters/jellyfin/
├── JellyfinApplication.kt       # Application class with Hilt
├── MainActivity.kt              # Main activity with navigation
├── core/                        # Core constants and helpers
├── data/                        # Models, paging, and repositories
├── di/                          # Hilt modules
├── ui/                          # Compose screens, navigation, and viewmodels
└── utils/                       # Utility classes
```

Additional paths:
- Module: `:app`
- Resources: `app/src/main/res`
- Manifest: `app/src/main/AndroidManifest.xml`
- Unit tests: `app/src/test/java`
- Instrumentation tests: `app/src/androidTest/java`

## 🎯 Key Components

### **🎠 Material 3 Carousel**
Beautiful horizontal carousel showcasing recently added content:
- **Scaled animations** (active items at 100%, others at 85%)
- **Gradient overlays** for text readability
- **Rich metadata** display with ratings and descriptions
- **Smooth scrolling** with page indicators

### **🏠 Home Screen**
Personalized dashboard featuring:
- **Welcome header** with server information
- **Recently added carousel** highlighting new content
- **Library grid** for quick access to collections
- **Pull-to-refresh** functionality

### **📚 Library Browser**
Browse your media collections with:
- **Visual library cards** with cover art
- **Collection type badges** (Movies, TV Shows, Music, etc.)
- **Item counts** and metadata
- **Loading states** with skeleton screens

### **⭐ Favorites**
Quick access to your favorite content:
- **Favorite indicators** on media cards
- **Dedicated favorites screen**
- **Star ratings** display
- **Fast browsing** experience

## 🔧 Development

### **Build, Test, Lint, Coverage**
- **Build debug APK:** `./gradlew assembleDebug`
- **Install debug APK:** `./gradlew installDebug`
- **Unit tests (JVM):** `./gradlew testDebugUnitTest`
- **Instrumentation tests:** `./gradlew connectedAndroidTest` (device/emulator required; uses `HiltTestRunner`)
- **Android Lint:** `./gradlew lintDebug`
- **Coverage (JaCoCo):** `./gradlew jacocoTestReport` (HTML/XML under `app/build/reports`)

### **Codex/Web/CI Environment Setup**
- Ensure an Android SDK is available and set `ANDROID_SDK_ROOT` (or `ANDROID_HOME`).
- If no SDK is installed, run `./setup.sh` to install cmdline-tools, accept licenses, and provision the SDK (Linux/macOS shells).
- Generate `local.properties` from your env: `scripts/gen-local-properties.sh` (bash) or `scripts/gen-local-properties.ps1` (PowerShell).
- Then build as usual: `./gradlew assembleDebug`.

### **Code Style & Naming**
- **Kotlin conventions**, 4-space indent, ~120 char lines
- **Material 3** UI, unidirectional data flow, **MVVM** with ViewModels
- **DI via Hilt**; repositories for data access
- Names: Classes `PascalCase`, functions/vars `camelCase`, constants `UPPER_SNAKE_CASE`

### **CI/CD**
Automated workflows for:
- ✅ **Build verification** on every push
- 🧪 **Unit testing** with detailed reports
- 🔍 **Code quality** checks (lint, security scans)
- 📦 **Dependency monitoring** (weekly updates)
- 🚀 **Automated releases** on git tags

## 🎨 Design System

### **Colors**
- **Primary:** Jellyfin Purple (#6200EE)
- **Secondary:** Jellyfin Blue (#2962FF)  
- **Tertiary:** Jellyfin Teal (#00695C)
- **Dynamic theming** support for Android 12+

### **Typography**
- **Material 3 type scale** implementation
- **Accessible contrast ratios**
- **Responsive text sizing**

### **Components**
- **Cards:** Elevated with rounded corners
- **Buttons:** Material 3 filled and outlined variants
- **Navigation:** Adaptive navigation suite
- **Loading:** Progress indicators and skeleton screens

## 🚧 Roadmap

### **Phase 1: Core Functionality** ✅
- [x] Server connection and authentication
- [x] Media library browsing
- [x] Material 3 UI implementation
- [x] Recently added carousel
- [x] Favorites management

### **Phase 2: Media Playback** 🔄
- [x] Video playback with ExoPlayer
- [ ] Audio playback support
- [ ] Subtitle handling
- [ ] Continue watching functionality
- [ ] Playback state synchronization

### **Phase 3: Advanced Features** 📋
- [ ] Search functionality
- [ ] Download for offline viewing
- [ ] Chromecast support
- [ ] Live TV integration
- [ ] User profiles and settings

### **Phase 4: Polish & Performance** 🎯
- [ ] Background sync
- [ ] Push notifications
- [ ] Widget support
- [ ] Performance optimizations
- [ ] Accessibility improvements

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

### **Development Setup**
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

### **Issue Reporting**
- Use the GitHub issue tracker
- Provide detailed reproduction steps
- Include device/OS information
- Attach logs when possible

### **Conventional Commits**
Follow Conventional Commits for all commit messages:
- `feat:` new feature
- `fix:` bug fix
- `docs:` documentation only changes
- `refactor:` code change that neither fixes a bug nor adds a feature
Examples: `feat: add movie detail screen`, `fix: prevent crash on empty library`.

### **Branching**
- `feature/...` for new features
- `bugfix/...` for bug fixes
- `hotfix/...` for urgent fixes
- `docs/...` for documentation updates

### **PR Checklist**
- Clear description and linked issue(s)
- Screenshots/GIFs for UI changes
- Tests added/updated where applicable
- `./gradlew testDebugUnitTest` passes
- `./gradlew lintDebug` passes
- Docs updated (README/CHANGELOG if needed)
- Note affected areas and test coverage in the PR body

## 🧪 Testing Guidelines
- Focus tests on ViewModel/Repository logic
- Use JUnit4, MockK, Turbine, AndroidX Test
- Mock network and I/O; avoid real server calls in unit tests
- Name tests descriptively (e.g., `loadMovieDetails_updates_state_on_success`)
- Coverage via `jacocoTestReport`; generated/DI classes are already filtered
 - Hilt testing is configured; use `HiltAndroidRule` and `HiltTestRunner` for instrumented tests

## 🔒 Security & Configuration
- Never commit secrets/keystores. Use Android Keystore/Encrypted storage.
- Network config at `app/src/main/res/xml/network_security_config.xml`.
- Min SDK 26, Target/Compile SDK 36.
- Keep dependency versions centralized in `gradle/libs.versions.toml`.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **Jellyfin Team** for the amazing media server platform
- **Android Team** for Jetpack Compose and Material 3
- **Community** for feedback and contributions

## 📞 Support

- **GitHub Issues:** [Report bugs and request features](https://github.com/rpeters1430/JellyfinAndroid/issues)
- **Jellyfin Community:** [Official Jellyfin forums](https://forum.jellyfin.org/)
- **Documentation:** [Jellyfin API docs](https://api.jellyfin.org/)

---

**Made with ❤️ for the Jellyfin community**
