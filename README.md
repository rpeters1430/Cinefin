# Jellyfin Android Client

[![Android CI](https://github.com/rpeters1430/JellyfinAndroid/actions/workflows/android-ci.yml/badge.svg)](https://github.com/yourusername/JellyfinAndroid/actions/workflows/android-ci.yml)
[![Dependency Check](https://github.com/rpeters1430/JellyfinAndroid/actions/workflows/dependency-check.yml/badge.svg)](https://github.com/yourusername/JellyfinAndroid/actions/workflows/dependency-check.yml)
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
- **Quick Connect** support (coming soon)

### 📱 **Modern Android Architecture**
- **Jetpack Compose** for declarative UI
- **MVVM pattern** with ViewModels and StateFlow
- **Hilt dependency injection** for clean architecture
- **Kotlin Coroutines** for asynchronous operations

## 🛠️ Technical Stack

### **Core Technologies**
- **Language:** Kotlin 2.2.0
- **UI Framework:** Jetpack Compose (2025.06.01 BOM)
- **Architecture:** MVVM + Repository Pattern
- **Dependency Injection:** Hilt 2.56.2
- **Async Programming:** Kotlin Coroutines 1.10.2

### **Networking & API**
- **Jellyfin SDK:** 1.6.8 (Official Jellyfin Kotlin SDK)
- **HTTP Client:** Retrofit 3.0.0 + OkHttp 4.12.0
- **Serialization:** Kotlinx Serialization 1.9.0
- **Image Loading:** Coil 2.7.0 with Compose integration

### **Media & UI**
- **Media Playback:** ExoPlayer (Media3 1.7.1) - *Ready for implementation*
- **Material Design:** Material 3 with Carousel support
- **Navigation:** Navigation Compose 2.9.1
- **Data Storage:** DataStore Preferences 1.1.7

## 📱 Requirements

- **Android 8.0** (API level 26) or higher
- **Active Jellyfin server** (version 10.8.0 or later recommended)
- **Internet connection** for streaming

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

### First Launch Setup

1. **Launch the app** on your device/emulator
2. **Enter your Jellyfin server URL** (e.g., `https://jellyfin.example.com`)
3. **Provide your credentials** (username and password)
4. **Start browsing** your media collection!

## 🏗️ Project Structure

```
app/src/main/java/com/example/jellyfinandroid/
├── 📱 MainActivity.kt              # Main activity with navigation
├── 🚀 JellyfinApplication.kt       # Application class with Hilt
├── 📊 data/
│   ├── JellyfinServer.kt           # Server data models
│   └── repository/
│       └── JellyfinRepository.kt   # Data layer with API calls
├── 🌐 network/
│   └── JellyfinApiService.kt       # Retrofit API definitions
├── 💉 di/
│   └── NetworkModule.kt            # Hilt dependency injection
└── 🎨 ui/
    ├── screens/
    │   └── ServerConnectionScreen.kt # Authentication UI
    ├── theme/
    │   ├── Color.kt                # Jellyfin brand colors
    │   ├── Theme.kt                # Material 3 theming
    │   └── Type.kt                 # Typography definitions
    └── viewmodel/
        ├── MainAppViewModel.kt     # Main app state management
        └── ServerConnectionViewModel.kt # Authentication logic
```

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

### **Running Tests**
```bash
# Unit tests
./gradlew testDebugUnitTest

# Lint checks
./gradlew lintDebug

# Build verification
./gradlew assembleDebug
```

### **Code Style**
- **Kotlin coding conventions** followed
- **Material 3 design guidelines** implemented
- **MVVM architecture** patterns
- **Clean code** principles

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
- [ ] Video playback with ExoPlayer
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
