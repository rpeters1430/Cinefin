# Jellyfin Android Client - Development Roadmap

This roadmap outlines the comprehensive improvement plan for transforming the Jellyfin Android client into a premium, multi-platform media experience.

## ✅ Immediate Fixes & Auth Hardening

These are focused, near-term items discovered during code review to solidify authentication and stability.

- [x] Fix client cache invalidation in `OptimizedClientFactory` so entries keyed by `serverUrl|token` are removed when invalidating by server URL.
- [ ] Normalize server URLs for credential storage and lookup to prevent "No saved password found" during re-auth (e.g., trim trailing slashes, consistent scheme/host casing).
- [ ] Consolidate auth handling by adopting `JellyfinSessionManager`/`BaseJellyfinRepository` wrappers across repositories to eliminate duplicate 401/re-auth logic.
- [ ] Split `MainAppViewModel` into smaller components and remove duplicated methods (e.g., multiple `ensureValidTokenWithWait` blocks) to reduce size and prevent merge artifacts.
- [ ] Move `TokenProvider` file to `app/src/main/java/com/rpeters/jellyfin/data/network/` to match its declared package and avoid source-set confusion.
- [ ] Replace `runBlocking` in `OptimizedClientFactory.getOptimizedClient` with a non-blocking approach (or make it `suspend`) to avoid potential main-thread blocking.
- [ ] Add unit tests for token expiry edge cases and single-flight re-auth (401 once → re-auth → success path; concurrent calls).
- [ ] Optional: Add Coil auth header support for servers that disallow `api_key` query param (configurable), while keeping current URLs.
- [ ] Implement Quick Connect flows (initiate, poll, authenticate) currently stubbed.

## 📊 **Progress Overview**

- **Total Phases**: 7 major phases
- **Total Steps**: 14 major implementation steps
- **Current Status**: Enhanced Playback System ✅ (completed in previous work)
- **Next Priority**: Android TV & Large Screen Optimization

---

## 🏗️ **PHASE 1: TV & Large Screen Optimization** 🔴 *HIGH PRIORITY*

**Objective**: Full Android TV/Google TV experience with 10-foot UI

### **Major Step 1.1: Android TV Architecture Implementation** ⏳ *Not Started*
**Target Completion**: Next 2-3 months

#### Implementation Checklist:
- [ ] **TV-Specific Navigation System**
  - [ ] Create `TVNavigationHost` with D-pad navigation support
  - [ ] Implement `TVHomeScreen` with horizontal content carousels
  - [ ] Add TV-specific routing in `NavGraph.kt`
  - [ ] Create TV manifest declarations and permissions

- [ ] **TV UI Components Library**
  - [ ] Build `TVMediaCard` components optimized for focus navigation
  - [ ] Create `TVCarousel` with auto-focus and smooth scrolling
  - [ ] Implement TV-specific detail screens with large imagery
  - [ ] Design TV-compatible loading states and error screens

- [ ] **Focus Management System**
  - [ ] Develop `FocusManager` for consistent D-pad navigation
  - [ ] Create focus indicators and animations
  - [ ] Implement TV remote control shortcuts
  - [ ] Add keyboard navigation support

- [ ] **Adaptive Layout System**
  - [ ] Enhance existing adaptive navigation to detect TV form factor
  - [ ] Create TV-specific layouts using `WindowSizeClass.Expanded`
  - [ ] Implement tablet optimization as intermediate step
  - [ ] Add landscape-first design patterns

**Dependencies**: AndroidX TV Material (already included), WindowSizeClass detection
**Estimated Effort**: 4-6 weeks
**Success Criteria**: Functional TV navigation with D-pad support, focus management working

### **Major Step 1.2: Playback Experience for TV** ⏳ *Not Started*
**Target Completion**: Month 3

#### Implementation Checklist:
- [ ] **Enhanced Video Player for TV**
  - [ ] Full-screen TV player with cinematic controls
  - [ ] TV-optimized player UI with large, readable text
  - [ ] Picture-in-picture support for TV multitasking
  - [ ] Custom TV player controls with D-pad navigation

- [ ] **Audio Visualization for TV**
  - [ ] TV-optimized music playback with visualizations
  - [ ] Album art display with TV-friendly layouts
  - [ ] Audio queue management for TV interface
  - [ ] Now playing screen optimization for large screens

- [ ] **Voice Control Integration**
  - [ ] Android TV voice search integration
  - [ ] Voice command handling for playback control
  - [ ] Google Assistant deep linking support

- [ ] **Cast Integration Enhancement**
  - [ ] Seamless casting from mobile to TV
  - [ ] TV as cast receiver functionality
  - [ ] Multi-device cast management

**Dependencies**: Media3 ExoPlayer (already included), Cast framework (already included)
**Estimated Effort**: 3-4 weeks
**Success Criteria**: TV-optimized playback experience, voice search working

---

## 🎵 **PHASE 2: Complete Media Experience Enhancement** 🟡 *Medium Priority*

**Objective**: Full-featured music streaming and offline capabilities

### **Major Step 2.1: Advanced Audio System** ⏳ *Not Started*
**Target Completion**: Months 4-5

#### Implementation Checklist:
- [ ] **Music Player Service**
  - [ ] Background playback with notification controls
  - [ ] Media session integration for Android Auto/Bluetooth
  - [ ] Lock screen controls and artwork display
  - [ ] Audio focus management for interruptions

- [ ] **Queue Management**
  - [ ] Add to queue, create playlists, shuffle/repeat
  - [ ] Smart queue suggestions based on listening history
  - [ ] Cross-fade and gapless playback support
  - [ ] Queue persistence across app restarts

- [ ] **Audio Visualizations**
  - [ ] Spectrum analyzer and waveform displays
  - [ ] Multiple visualization themes
  - [ ] Customizable visualization settings
  - [ ] Performance-optimized rendering

- [ ] **Enhanced Music Features**
  - [ ] Lyrics integration with synchronized display
  - [ ] Artist radio and smart playlists
  - [ ] Music discovery recommendations
  - [ ] Genre and mood-based browsing

**Dependencies**: Media3 Session (already included), MediaBrowserService
**Estimated Effort**: 5-6 weeks
**Success Criteria**: Background music playback, queue management, basic visualizations

### **Major Step 2.2: Offline Content System** ⏳ *Not Started*
**Target Completion**: Month 6

#### Implementation Checklist:
- [ ] **Download Manager**
  - [ ] Queue-based downloading with progress tracking
  - [ ] Parallel download support with bandwidth management
  - [ ] Download retry logic and error handling
  - [ ] Storage location management (internal/external)

- [ ] **Smart Sync Features**
  - [ ] Auto-download based on user preferences
  - [ ] Download quality selection (resolution/bitrate)
  - [ ] WiFi-only download options
  - [ ] Downloaded content expiration management

- [ ] **Storage Management**
  - [ ] Intelligent cleanup and storage optimization
  - [ ] Storage usage analytics and reporting
  - [ ] Download size estimation before downloading
  - [ ] Cache management for streaming content

- [ ] **Offline Playback**
  - [ ] Seamless offline/online switching
  - [ ] Offline library browsing and search
  - [ ] Sync status indicators throughout UI
  - [ ] Offline-first architecture for downloaded content

**Dependencies**: WorkManager for background downloads, Room for offline database
**Estimated Effort**: 4-5 weeks
**Success Criteria**: Reliable offline downloads, storage management, offline playback

### **Major Step 2.3: Advanced Video Features** ⏳ *Not Started*
**Target Completion**: Month 7

#### Implementation Checklist:
- [ ] **Chapter Support**
  - [ ] Video chapter navigation and thumbnails
  - [ ] Chapter timeline scrubbing
  - [ ] Chapter-based bookmarking
  - [ ] Chapter metadata display

- [ ] **Subtitle Management**
  - [ ] Multiple subtitle tracks with easy switching
  - [ ] Subtitle styling options (font, size, color)
  - [ ] Subtitle search and download integration
  - [ ] Custom subtitle file loading

- [ ] **Video Enhancement Features**
  - [ ] Video filters (brightness, contrast, saturation)
  - [ ] Playback speed controls (0.5x to 2x)
  - [ ] Video rotation and aspect ratio controls
  - [ ] Hardware acceleration optimization

- [ ] **Picture-in-Picture**
  - [ ] Multi-tasking video playback
  - [ ] PiP controls and gesture support
  - [ ] Smart PiP activation based on context
  - [ ] PiP size and position preferences

**Dependencies**: ExoPlayer advanced features, PictureInPicture API
**Estimated Effort**: 3-4 weeks
**Success Criteria**: Chapter navigation, subtitle support, PiP functionality

---

## 🔍 **PHASE 3: Discovery & Intelligence Features** 🟡 *Medium Priority*

### **Major Step 3.1: Advanced Search & Discovery** ⏳ *Not Started*
**Target Completion**: Months 8-9

#### Implementation Checklist:
- [ ] **AI-Powered Search**
  - [ ] Natural language queries ("action movies from 2020s")
  - [ ] Search suggestions and autocomplete
  - [ ] Typo tolerance and fuzzy matching
  - [ ] Multi-criteria search filters

- [ ] **Smart Recommendations**
  - [ ] ML-based content suggestions using viewing history
  - [ ] Similar content recommendations
  - [ ] Trending content analysis
  - [ ] Seasonal and contextual recommendations

- [ ] **Advanced Search Features**
  - [ ] Visual search using poster/scene recognition
  - [ ] Voice search integration
  - [ ] Barcode scanning for physical media matching
  - [ ] Advanced filtering (genre, year, rating, duration)

- [ ] **Discovery Enhancement**
  - [ ] "More like this" suggestions
  - [ ] Director/actor filmography navigation
  - [ ] Related content carousels
  - [ ] Discovery feeds based on mood/occasion

**Dependencies**: ML Kit or TensorFlow Lite, Advanced search algorithms
**Estimated Effort**: 5-6 weeks
**Success Criteria**: Natural language search, ML recommendations working

### **Major Step 3.2: Personalization Engine** ⏳ *Not Started*
**Target Completion**: Month 10

#### Implementation Checklist:
- [ ] **User Profiles**
  - [ ] Individual family member profiles
  - [ ] Age-appropriate content filtering
  - [ ] Personalized recommendations per profile
  - [ ] Profile-specific watch history and preferences

- [ ] **Watching Pattern Analytics**
  - [ ] Viewing behavior analysis for content curation
  - [ ] Watch time analytics and insights
  - [ ] Content completion tracking
  - [ ] Binge-watching detection and suggestions

- [ ] **Smart Collections**
  - [ ] Auto-generated collections based on viewing history
  - [ ] Dynamic collections that update automatically
  - [ ] User-customizable collection rules
  - [ ] Collection sharing between family members

- [ ] **Mood-Based Discovery**
  - [ ] "Feel Good Movies", "Rainy Day Shows" categories
  - [ ] Time-of-day content suggestions
  - [ ] Mood detection based on viewing patterns
  - [ ] Contextual recommendations (weekend, evening, etc.)

**Dependencies**: User analytics framework, Machine learning models
**Estimated Effort**: 4-5 weeks
**Success Criteria**: User profiles working, personalized recommendations, smart collections

---

## 📱 **PHASE 4: Mobile Experience Polish** 🟢 *Low Priority*

### **Major Step 4.1: Adaptive UI System** ⏳ *Not Started*
**Target Completion**: Months 11-12

#### Implementation Checklist:
- [ ] **Foldable Device Support**
  - [ ] Optimized layouts for Samsung Galaxy Fold, Pixel Fold
  - [ ] Hinge-aware layouts and content positioning
  - [ ] Seamless folded/unfolded transitions
  - [ ] Multi-window support on foldables

- [ ] **Tablet Optimization**
  - [ ] Multi-pane layouts for large screens
  - [ ] Split-screen support and optimization
  - [ ] Drag-and-drop functionality
  - [ ] Tablet-specific navigation patterns

- [ ] **Advanced Responsive Design**
  - [ ] Seamless portrait/landscape transitions
  - [ ] Content-aware layout adaptations
  - [ ] Dynamic typography scaling
  - [ ] Orientation-specific feature availability

- [ ] **Modern UI Enhancements**
  - [ ] Dynamic Island-style notifications (Android 13+)
  - [ ] Edge-to-edge design with gesture navigation
  - [ ] Adaptive refresh rates for smooth scrolling
  - [ ] Haptic feedback integration

**Dependencies**: WindowSizeClass, Adaptive Navigation Suite (already included)
**Estimated Effort**: 4-5 weeks
**Success Criteria**: Foldable support, tablet optimization, smooth responsive design

### **Major Step 4.2: Performance & Battery Optimization** ⏳ *Not Started*
**Target Completion**: Month 13

#### Implementation Checklist:
- [ ] **Background Processing Optimization**
  - [ ] Intelligent sync and prefetching
  - [ ] Background task prioritization
  - [ ] Battery-aware background processing
  - [ ] Efficient WorkManager implementation

- [ ] **Battery Life Enhancement**
  - [ ] Adaptive streaming quality based on battery level
  - [ ] Dark mode optimization for OLED displays
  - [ ] CPU/GPU usage optimization
  - [ ] Network usage optimization

- [ ] **Memory Management**
  - [ ] Efficient image caching with LRU eviction
  - [ ] Memory leak prevention and detection
  - [ ] Garbage collection optimization
  - [ ] Large bitmap handling improvements

- [ ] **Network Intelligence**
  - [ ] Adaptive quality based on connection type
  - [ ] Smart preloading based on usage patterns
  - [ ] Bandwidth usage monitoring and reporting
  - [ ] Connection quality indicators

**Dependencies**: Performance monitoring tools, Battery optimization APIs
**Estimated Effort**: 3-4 weeks
**Success Criteria**: Improved battery life, memory efficiency, network optimization

---

## 🌐 **PHASE 5: Connectivity & Sync Features** 🟢 *Low Priority*

### **Major Step 5.1: Multi-Device Synchronization** ⏳ *Not Started*
**Target Completion**: Month 14

#### Implementation Checklist:
- [ ] **Real-time Sync**
  - [ ] Watch progress sync across devices
  - [ ] Real-time playback position synchronization
  - [ ] Sync conflict resolution strategies
  - [ ] Offline sync queue management

- [ ] **Cross-Device Features**
  - [ ] Shared watch queues between devices
  - [ ] Device handoff (start on phone, continue on TV)
  - [ ] Remote control functionality between devices
  - [ ] Multi-device playlist management

- [ ] **Family Sync Features**
  - [ ] Shared family watching experiences
  - [ ] Family member activity feeds
  - [ ] Shared favorites and collections
  - [ ] Parental control synchronization

**Dependencies**: WebSocket or similar real-time sync technology
**Estimated Effort**: 4-5 weeks
**Success Criteria**: Real-time sync working, device handoff functional

### **Major Step 5.2: Cloud Integration** ⏳ *Not Started*
**Target Completion**: Month 15

#### Implementation Checklist:
- [ ] **Backup & Restore**
  - [ ] Google Drive backup for settings and preferences
  - [ ] iCloud backup support (if applicable)
  - [ ] Backup encryption and security
  - [ ] Selective backup options

- [ ] **Cross-Platform Sync**
  - [ ] Sync with Jellyfin web interface
  - [ ] Mobile app settings sync
  - [ ] Watch history cross-platform sync
  - [ ] Bookmarks and favorites sync

- [ ] **Server Management**
  - [ ] Automatic local server discovery
  - [ ] Server health monitoring
  - [ ] Multiple server support enhancements
  - [ ] Server-specific settings management

- [ ] **Remote Access**
  - [ ] Secure external server connections
  - [ ] VPN detection and optimization
  - [ ] Remote server performance monitoring
  - [ ] Connection quality adaptation

**Dependencies**: Cloud storage APIs, Network discovery protocols
**Estimated Effort**: 3-4 weeks
**Success Criteria**: Cloud backup working, server discovery functional

---

## 🎮 **PHASE 6: Gaming & Interactive Features** 🔵 *Future/Optional*

### **Major Step 6.1: Retro Gaming Support** ⏳ *Not Started*
**Target Completion**: Month 16+

#### Implementation Checklist:
- [ ] **Game Library Integration**
  - [ ] Browse and launch retro games through Jellyfin
  - [ ] Game metadata display and organization
  - [ ] Game artwork and screenshots
  - [ ] Game collection management

- [ ] **Gaming Experience**
  - [ ] Bluetooth gamepad integration
  - [ ] Touch controls for mobile gaming
  - [ ] Save state management and cloud sync
  - [ ] Game settings and configuration

- [ ] **Social Gaming Features**
  - [ ] Achievement system integration
  - [ ] Gaming progress tracking
  - [ ] Multiplayer coordination
  - [ ] Gaming statistics and analytics

**Dependencies**: Gaming emulation libraries, Controller support APIs
**Estimated Effort**: 6-8 weeks
**Success Criteria**: Basic game library browsing, controller support

---

## 🔧 **PHASE 7: Developer Experience & Architecture** 🔵 *Future/Optional*

### **Major Step 7.1: Modular Architecture Enhancement** ⏳ *Not Started*
**Target Completion**: Month 17+

#### Implementation Checklist:
- [ ] **Multi-Module Architecture**
  - [ ] Convert to feature-based module structure
  - [ ] Core module for shared functionality
  - [ ] Feature modules for major app sections
  - [ ] Dynamic feature modules for optional functionality

- [ ] **Extensibility**
  - [ ] Plugin system for custom features
  - [ ] API for third-party integrations
  - [ ] Custom theme system with user customization
  - [ ] Extension point architecture

- [ ] **Future-Proofing**
  - [ ] Better abstraction for Jellyfin API changes
  - [ ] Version migration system
  - [ ] Feature flag system for gradual rollouts
  - [ ] A/B testing framework integration

**Dependencies**: Gradle multi-module setup, Plugin architecture frameworks
**Estimated Effort**: 5-6 weeks
**Success Criteria**: Modular architecture working, plugin system functional

### **Major Step 7.2: Testing & Quality Assurance** ⏳ *Not Started*
**Target Completion**: Month 18+

#### Implementation Checklist:
- [ ] **Comprehensive Testing**
  - [ ] UI testing suite with Espresso/Compose Testing
  - [ ] Performance regression tests
  - [ ] Accessibility compliance testing
  - [ ] Device compatibility testing matrix

- [ ] **Quality Infrastructure**
  - [ ] Device farm integration for automated testing
  - [ ] Crash reporting and analytics enhancement
  - [ ] Performance monitoring and alerting
  - [ ] User feedback integration system

- [ ] **Development Tools**
  - [ ] Developer debugging tools
  - [ ] Performance profiling integration
  - [ ] Code quality gates and automation
  - [ ] Continuous integration enhancements

**Dependencies**: Testing frameworks, Device farm services, Analytics platforms
**Estimated Effort**: 4-5 weeks
**Success Criteria**: 90%+ test coverage, automated quality gates

---

## 📅 **Implementation Timeline Summary**

### **Immediate Focus (Months 1-3)**: TV Experience 🔴
- Phase 1.1: TV Architecture Implementation
- Phase 1.2: TV Playback Experience

### **Short-term (Months 4-7)**: Media Enhancement 🟡
- Phase 2.1: Advanced Audio System
- Phase 2.2: Offline Content System  
- Phase 2.3: Advanced Video Features

### **Medium-term (Months 8-10)**: Intelligence 🟡
- Phase 3.1: Advanced Search & Discovery
- Phase 3.2: Personalization Engine

### **Long-term (Months 11-15)**: Polish & Connectivity 🟢
- Phase 4.1: Adaptive UI System
- Phase 4.2: Performance Optimization
- Phase 5.1: Multi-Device Sync
- Phase 5.2: Cloud Integration

### **Future/Optional (Months 16+)**: Advanced Features 🔵
- Phase 6.1: Gaming Support
- Phase 7.1: Modular Architecture
- Phase 7.2: Testing & QA

---

## 🎯 **Success Metrics & KPIs**

### **TV Experience Goals**
- [ ] 4.5+ rating on Google TV Play Store
- [ ] 50%+ TV usage for video content
- [ ] Sub-3 second navigation response time on TV

### **Performance Targets**  
- [ ] Sub-2 second app launch time
- [ ] 95% crash-free sessions
- [ ] 30% reduction in memory usage

### **User Engagement**
- [ ] 40% increase in daily active users
- [ ] 30% mobile usage for music content
- [ ] 60% user retention after 30 days

### **Quality Standards**
- [ ] 90% automated test coverage
- [ ] Full accessibility compliance (WCAG 2.1 AA)
- [ ] Support for 95% of target devices

---

## 📝 **Notes for Future Development**

### **Current Architecture Strengths to Maintain:**
- MVVM + Repository Pattern with Clean Architecture
- Enhanced Playback System with intelligent Direct Play detection
- Material 3 design with adaptive navigation
- Comprehensive error handling with `ApiResult<T>`
- Hilt dependency injection for clean architecture

### **Technical Debt to Address:**
- Convert to multi-module architecture (Phase 7.1)
- Improve test coverage across all features
- Performance optimization for lower-end devices
- Better offline/online state management

### **Key Dependencies Already in Place:**
- ✅ AndroidX TV Material (1.1.0-alpha01)  
- ✅ Media3 with ExoPlayer (1.8.0)
- ✅ Material 3 Adaptive Navigation Suite (1.5.0-alpha03)
- ✅ Enhanced Playback System implementation
- ✅ Cast framework integration (22.1.0)

### **Resources Needed:**
- UI/UX designer for TV experience
- Additional developer for parallel feature development
- QA resources for multi-device testing
- Performance testing infrastructure

---

**Last Updated**: 2025-01-31  
**Version**: 1.0  
**Status**: Ready for Phase 1 implementation

---

*This roadmap is a living document and will be updated as features are completed and priorities evolve.*
