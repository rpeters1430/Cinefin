# Jellyfin Android Client - Development Roadmap

This roadmap outlines the comprehensive improvement plan for transforming the Jellyfin Android
client into a premium, multi-platform media experience.

## ✅ Immediate Fixes & Performance Optimization

These are focused, near-term items discovered during code review and performance analysis to
solidify authentication, stability, and UI responsiveness.

### **Recently Completed Major Achievements**:

- ✅ **Android TV Architecture** - Complete TV experience with navigation, carousels, focus
  management, and adaptive layouts
- ✅ **MainAppViewModel Optimization** - Reduced from 1778 to 675 lines (62% reduction) via
  repository delegation
- ✅ **Authentication Consolidation** - Centralized auth handling with JellyfinSessionManager and
  BaseJellyfinRepository
- ✅ **Client Cache Management** - Fixed cache invalidation and URL normalization for credential
  storage
- ✅ **Performance Optimization** - Fixed 46 frame skipping issue by preventing concurrent
  loadInitialData() calls and moving heavy operations to background threads
- ✅ **Main Thread Protection** - Implemented loading guards and Dispatchers.IO usage to prevent UI
  blocking during data loading
- ✅ **Mobile Video Player Improvements** - Complete
    - ✅ Playback speed control (0.75×–2×)
    - ✅ Real audio/subtitle track selection via Media3 overrides
    - ✅ Defaults applied per playback: English audio, subtitles off
    - ✅ Skip Intro / Skip Credits from server chapters
    - ✅ 10s seek increments aligned with double‑tap
    - ✅ Progress/timeline updates fixed (position/duration ticker)
    - ✅ Safer ExoPlayer teardown (stop + clear surface) to reduce codec warnings
    - ✅ PiP button gated by device capability
- ✅ **TV Video Player** - Complete
    - ✅ TV-optimized player UI with D-pad navigation and focus management
    - ✅ Large, readable controls (80dp play/pause, 48dp margins) for 10-foot viewing
    - ✅ Settings dialog for audio/subtitle track selection and playback speed
    - ✅ Skip intro/credits buttons with TV-friendly sizing
    - ✅ Enhanced Picture-in-Picture support for Android TV (auto-enter, seamless resize)
    - ✅ Remote control support (play/pause, seek, back, media keys)
    - ✅ Platform detection to automatically route TV devices to TV UI
- ✅ **TV Audio Player with Visualizations** - Complete
    - ✅ 480dp album art display with blurred background gradients
    - ✅ Full playback controls (play/pause, skip, seek, shuffle, repeat)
    - ✅ Queue management overlay (view, skip to track, remove, clear)
    - ✅ Three visualization modes (Waveform, Spectrum, Circular)
    - ✅ Real-time position tracking and progress display
    - ✅ D-pad navigation with focus management throughout
- ✅ **Quick Connect Authentication** - Complete
    - ✅ TV-optimized Quick Connect screen with 96sp code display
    - ✅ 2-second polling with 5-minute timeout
    - ✅ D-pad navigation and focus management
    - ✅ Side-by-side with traditional sign-in on TV connection screen
    - ✅ Works on both TV and mobile platforms


- [x] Fix client cache invalidation in `OptimizedClientFactory` so entries keyed by
  `serverUrl|token` are removed when invalidating by server URL.
- [x] Normalize server URLs for credential storage and lookup to prevent "No saved password found"
  during re-auth (e.g., trim trailing slashes, consistent scheme/host casing).
- [x] Consolidate auth handling by adopting `JellyfinSessionManager`/`BaseJellyfinRepository`
  wrappers across repositories to eliminate duplicate 401/re-auth logic.
- [x] Split `MainAppViewModel` into smaller components via repository delegation and a single
  `ensureValidToken` method, removing duplicated methods (e.g., multiple `ensureValidTokenWithWait`
  blocks) to reduce size and prevent merge artifacts.
- [x] **Fix frame dropping performance issue** - Added loading guard to `loadInitialData()` to
  prevent concurrent API calls that caused 46 frame skips
- [x] **Implement background thread execution** - Use `withContext(Dispatchers.IO)` for heavy async
  operations to prevent main thread blocking
- [x] **Move `TokenProvider` file to `app/src/main/java/com/rpeters/jellyfin/data/network/`** (was
  under `com.example.jellyfinandroid.data.network`)
- [x] **Replace `runBlocking` in `OptimizedClientFactory.getOptimizedClient`** — switched to
  `suspend` + updated `JellyfinSessionManager`
- [ ] Add unit tests for token expiry edge cases and single-flight re-auth (401 once → re-auth →
  success path; concurrent calls).
- [ ] Optional: Add Coil auth header support for servers that disallow `api_key` query param (
  configurable), while keeping current URLs.
- [x] **Implement Quick Connect flows** - Complete with TV-optimized UI and 2-second polling

## 📊 **Progress Overview**

- **Total Phases**: 8 major phases (1 new phase added: Mobile Core Experience)
- **Total Steps**: 19 major implementation steps (5 new steps added)
- **Current Status**: Phase 1.2 - TV Playback Experience ✅ *COMPLETE*
- **Next Priority**: Phase 1.5 - Mobile Core Experience (Major Step 1.5.2 Kickoff)
- **Rationale for Priority Shift**: Mobile users likely represent the majority of the user base and deserve equal attention to the TV experience

---

## 🏗️ **PHASE 1: TV & Large Screen Optimization** 🔴 *HIGH PRIORITY*

**Objective**: Full Android TV/Google TV experience with 10-foot UI

### **Major Step 1.1: Android TV Architecture Implementation** ✅ *Mostly Complete*

**Target Completion**: Next 2-3 months *(Achieved early)*

#### Implementation Checklist:

- [x] **TV-Specific Navigation System**
    - [x] Create TVNavigationHost (TvNavGraph.kt) and wire into TvJellyfinApp
    - [x] Implement TvHomeScreen with horizontal content carousels and adaptive layouts
    - [x] Add TV-specific routing in TvNavGraph (ServerConnection → Home → Library → Item)
    - [x] Verify TV manifest (leanback launcher and banner)

- [x] **TV UI Components Library**
    - [x] TvContentCarousel with auto-scroll and focus-aware navigation
    - [x] TvLibrariesSection with focusable library cards
    - [x] TvItemDetailScreen with poster, backdrop, overview, Play/Resume/Direct Play buttons,
      favorite/watched actions
    - [x] TV loading/error states (TvSkeletonCarousel, TvErrorBanner, TvFullScreenLoading,
      TvEmptyState)
    - [x] TvLoadingStates.kt with shimmer effects and TV-optimized components

- [x] **Focus Management System**
    - [x] Initial focus set on first available carousel
    - [x] Focus glow/elevation effects on cards and buttons
    - [x] Comprehensive TvFocusManager with state persistence, carousel/grid focus handling
    - [x] D-pad navigation support with key event handling
    - [x] Focus scope management per screen (TvScreenFocusScope)
    - [x] TvFocusableCarousel and TvFocusableGrid composables with auto-scroll
    - [ ] **TV remote shortcuts and keyboard navigation parity** - Only basic D-pad navigation
      implemented

- [~] **Adaptive Layout System**
    - [~] **Detect TV/tablet form factors** - Basic WindowSizeClass usage in TvHomeScreen, needs
      expansion
    - [ ] **TV-specific layouts via WindowSizeClass** - Partially implemented, needs tablet-specific
      layouts
    - [ ] Tablet optimization
    - [ ] Landscape-first refinements
      **Dependencies**: AndroidX TV Material ✅ (already included), WindowSizeClass detection ✅
      **Estimated Effort**: 4-6 weeks *(3 weeks saved due to early completion)*
      **Success Criteria**: ✅ Functional TV navigation with D-pad support, ✅ focus management
      working

### **Major Step 1.2: Playback Experience for TV** ✅ *COMPLETE*

**Target Completion**: Month 3 *(Achieved ahead of schedule)*

#### Implementation Checklist:

- [x] **Enhanced Video Player for TV** - ✅ Complete
    - ✅ Base player improvements complete on mobile (speed control, track selection, etc.)
    - ✅ TV-optimized player UI with large, readable text (48sp margins, 80dp buttons)
    - ✅ Picture-in-picture support for TV (auto-enter on Android 12+, seamless resize)
    - ✅ Custom TV player controls with D-pad navigation & focus rings
    - ✅ Settings dialog for audio/subtitle tracks and playback speed
    - ✅ Remote control support (media keys, seek buttons)
    - ✅ Platform detection to automatically use TV UI on TV devices

- [x] **Audio Visualization for TV** - ✅ Complete
    - ✅ TV-optimized music playback with visualizations (Waveform, Spectrum, Circular)
    - ✅ Album art display with TV-friendly layouts (480dp with blurred background)
    - ✅ Audio queue management for TV interface (view, skip, remove, clear)
    - ✅ Now playing screen optimization for large screens (large text, D-pad navigation)
    - ✅ Real-time position tracking and progress display
    - ✅ Full playback controls (play/pause, skip, seek, shuffle, repeat)

- [ ] **Voice Control Integration** - *Future Enhancement*
    - [ ] Android TV voice search integration
    - [ ] Voice command handling for playback control
    - [ ] Google Assistant deep linking support

- [~] **Cast Integration Enhancement** - Basic Implementation Complete
    - ✅ Seamless casting from mobile to TV - CastManager and Cast framework integrated
    - [ ] TV as cast receiver functionality
    - [ ] Multi-device cast management

**Dependencies**: Media3 ExoPlayer ✅, Cast framework ✅
**Estimated Effort**: 3-4 weeks *(Completed in 3 weeks)*
**Success Criteria**: ✅ TV-optimized playback experience complete, voice search deferred to future phase

**Achievement Note**: Phase 1.2 successfully delivered TV video player with full D-pad navigation, TV audio player with visualizations, and Quick Connect authentication - providing a complete 10-foot UI experience for Android TV.

---

## 📱 **PHASE 1.5: Mobile Core Experience** 🔴 *HIGH PRIORITY - NEW*

**Objective**: Elevate mobile experience to match TV platform quality with modern Android features

**Rationale**: Mobile users likely represent the majority of the user base. While TV experience is excellent, mobile app needs equal attention with modern Android patterns, gesture controls, and platform integrations that users expect in 2025.

### **Major Step 1.5.1: Modern Android Integration** ✅ *Planning Complete*

**Target Completion**: Months 3-4

**Current Status**: Planning finalized — widget data sources, shortcut deep links, and notification integration flow defined for hand-off to implementation teams.

#### Implementation Checklist:

- [ ] **Home Screen Widgets**
    - [ ] "Now Playing" widget with playback controls
    - [ ] "Continue Watching" widget showing 3-4 items
    - [ ] Widget configuration and sizing options
    - [ ] Deep link integration from widgets
    - [ ] Widget preview images for picker

- [ ] **App Shortcuts & Quick Actions**
    - [ ] Static shortcuts (Search, Downloads, Favorites, Continue Watching)
    - [ ] Dynamic shortcuts for recently played content
    - [ ] Long-press launcher shortcuts
    - [ ] Shortcut icons and labels

- [ ] **System Integration**
    - [ ] Quick Settings tile for playback control
    - [ ] Share sheet integration for content recommendations
    - [ ] Deep linking support (jellyfin://server/item/123)
    - [ ] Intent filters for external content links
    - [ ] Material You dynamic theming (full implementation beyond basics)
    - [ ] Themed app icons (Android 13+)
    - [ ] Predictive back gesture support (Android 13+)

- [ ] **Notification Enhancements**
    - [ ] Rich media notifications with large artwork
    - [ ] Progress notifications for downloads
    - [ ] Notification channels for different types
    - [ ] Grouped notifications for multiple downloads

**Dependencies**: Android 12+ APIs, Glance for widgets, WorkManager for notifications
**Estimated Effort**: 3-4 weeks
**Success Criteria**: Widgets functional, shortcuts working, deep linking tested

### **Major Step 1.5.2: Mobile Gesture & Interaction System** 🟡 *In Progress*

**Target Completion**: Month 4

**Current Focus**: Establish gesture architecture blueprint and prioritize video player gesture prototypes before expanding to list and haptic interactions.

#### Implementation Checklist:

- [ ] **Video Player Gestures**
    - [ ] Swipe up/down on left for brightness control
    - [ ] Swipe up/down on right for volume control
    - [ ] Double-tap left/right to seek (enhance existing)
    - [ ] Pinch-to-zoom gesture
    - [ ] Long-press for playback speed menu
    - [ ] Visual feedback overlays for gestures

- [ ] **List & Content Gestures**
    - [ ] Swipe-to-dismiss for continue watching items
    - [ ] Pull-to-refresh on all content screens
    - [ ] Long-press context menus on media cards
    - [ ] Swipe actions for queue management
    - [ ] Pinch-to-zoom for image galleries

- [ ] **Haptic Feedback System**
    - [ ] Haptic confirmation for button presses
    - [ ] Seek feedback in video player
    - [ ] Drag-and-drop confirmation haptics
    - [ ] Error/success haptic patterns
    - [ ] Configurable haptic intensity in settings
    - [ ] Respect system haptic settings

- [ ] **Touch Optimization**
    - [ ] Minimum touch target size (48dp)
    - [ ] Touch feedback animations
    - [ ] Debouncing for rapid taps
    - [ ] Edge-to-edge gesture support

**Dependencies**: Compose gesture APIs, HapticFeedback API
**Estimated Effort**: 2-3 weeks
**Success Criteria**: All gestures working smoothly, haptic feedback consistent

### **Major Step 1.5.3: Mobile Loading States & Animations** ⏳ *Not Started*

**Target Completion**: Month 5

#### Implementation Checklist:

- [ ] **Loading States**
    - [ ] Shimmer skeleton screens for all list views
    - [ ] Progressive image loading with blur-up effect
    - [ ] Loading placeholders for media cards
    - [ ] Skeleton screens for detail pages
    - [ ] Loading state animations

- [ ] **Transitions & Animations**
    - [ ] Shared element transitions between screens
    - [ ] Hero animations for media items
    - [ ] Smooth scroll animations with physics
    - [ ] Card expansion animations
    - [ ] Bottom sheet slide animations
    - [ ] Fade transitions for content changes

- [ ] **Empty & Error States**
    - [ ] Illustrated empty states for each screen
    - [ ] Actionable error messages with retry
    - [ ] Helpful tips in empty states
    - [ ] Connection error states
    - [ ] Search no-results states with suggestions

- [ ] **Performance Animations**
    - [ ] 60fps animation guarantee
    - [ ] GPU-accelerated transitions
    - [ ] Reduce motion support for accessibility
    - [ ] Animation interrupt handling

**Dependencies**: Compose Animation APIs, Lottie for complex animations (optional)
**Estimated Effort**: 2-3 weeks
**Success Criteria**: Smooth animations at 60fps, all loading states implemented

### **Major Step 1.5.4: Content Discovery Enhancements** ⏳ *Not Started*

**Target Completion**: Month 5

#### Implementation Checklist:

- [ ] **Continue Watching Improvements**
    - [ ] Smart positioning (most likely to resume first)
    - [ ] Progress bars on all thumbnails
    - [ ] Swipe-to-remove gesture
    - [ ] "Mark as watched" quick action
    - [ ] Auto-removal of completed items
    - [ ] Restore accidentally removed items (undo)

- [ ] **Recently Added Enhancements**
    - [ ] Filter by type (movies/shows/music/books)
    - [ ] Sort options (date added, name, rating)
    - [ ] Mark as seen/unseen bulk actions
    - [ ] "New" badges on unwatched content
    - [ ] Expandable sections per content type

- [ ] **Collections & Playlists UI**
    - [ ] Create playlists from mobile
    - [ ] Edit playlist metadata and artwork
    - [ ] Drag-and-drop reordering
    - [ ] Add to playlist from any screen
    - [ ] Smart collection suggestions
    - [ ] Share playlists with other users

- [ ] **Quick Actions Everywhere**
    - [ ] Favorite/unfavorite quick action
    - [ ] Add to playlist quick action
    - [ ] Download quick action
    - [ ] Share quick action

**Dependencies**: Existing repository layer, DragAndDrop APIs
**Estimated Effort**: 3-4 weeks
**Success Criteria**: Playlist management working, smart suggestions functional

**Phase 1.5 Summary**:
- **Total Estimated Effort**: 10-14 weeks
- **Dependencies**: Android 12+ APIs, Compose gestures, existing architecture
- **Success Metrics**: Widget adoption >30%, gesture usage >60%, animation smoothness 60fps

---

### What's Next for 1.1 *(Updated based on current implementation)*

- ✅ **Centralized FocusManager** - Fully implemented with TvFocusManager, save/restore focus, and
  D-pad navigation
- ✅ **TV loading/error states** - Complete with skeleton tiles and TV-friendly error banners
- ✅ **Details polish** - Favorite/watched actions implemented, codec/resolution display implemented
- [~] **Adaptive layouts** - Basic WindowSizeClass usage implemented, needs tablet-specific
  optimizations
- [ ] **"Similar" content shelf** - Still pending implementation
- [ ] **TV remote shortcuts** - Basic D-pad works, needs media keys and shortcuts

---

## 🎵 **PHASE 2: Complete Media Experience Enhancement** 🟡 *Medium Priority*

**Objective**: Full-featured music streaming and offline capabilities

### **Major Step 2.1: Advanced Audio System** ⏳ *Not Started*

**Target Completion**: Months 6-7 *(Updated timeline after Phase 1.5)*

#### Implementation Checklist:

- [ ] **Music Player Service**
    - [ ] Background playback with notification controls
    - [ ] Media session integration for Android Auto/Bluetooth
    - [ ] Lock screen controls and artwork display
    - [ ] Audio focus management for interruptions
    - [ ] Media button handling (headphone controls)

- [ ] **Queue Management**
    - [ ] Add to queue, create playlists, shuffle/repeat
    - [ ] Smart queue suggestions based on listening history
    - [ ] Cross-fade and gapless playback support
    - [ ] Queue persistence across app restarts
    - [ ] Drag-and-drop queue reordering on mobile

- [ ] **Audio Visualizations**
    - [ ] Spectrum analyzer and waveform displays (enhance TV implementation)
    - [ ] Multiple visualization themes
    - [ ] Customizable visualization settings
    - [ ] Performance-optimized rendering
    - [ ] Mobile-optimized layouts

- [ ] **Enhanced Music Features**
    - [ ] Lyrics integration with synchronized display
    - [ ] Artist radio and smart playlists
    - [ ] Music discovery recommendations
    - [ ] Genre and mood-based browsing
    - [ ] Sleep timer with fade-out

- [ ] **Mobile Audio Platform Integration** 🆕
    - [ ] Android Auto full integration
        - [ ] Car-optimized browsing interface
        - [ ] Voice command support
        - [ ] Safe driving UI compliance
        - [ ] Album art and metadata display
    - [ ] Wear OS companion app
        - [ ] Now playing display on watch
        - [ ] Basic playback controls (play/pause/skip)
        - [ ] Volume control
        - [ ] Browse recent/favorites
    - [ ] Car mode UI (for non-Auto devices)
        - [ ] Large touch targets
        - [ ] Simplified navigation
        - [ ] Voice control hints
    - [ ] Equalizer integration (if device supports)
    - [ ] Audio effects support (reverb, bass boost)

**Dependencies**: Media3 Session (already included), MediaBrowserService, Android Auto APIs, Wear OS APIs
**Estimated Effort**: 7-8 weeks *(extended for mobile integrations)*
**Success Criteria**: Background music playback, Android Auto working, Wear OS controls functional, queue management complete

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

- [~] **Subtitle Management** - Partially Complete
    - ✅ Multiple subtitle tracks with easy switching - TrackSelectionManager implemented
    - [ ] Subtitle styling options (font, size, color)
    - [ ] Subtitle search and download integration
    - [ ] Custom subtitle file loading

- [~] **Video Enhancement Features** - Partially Complete
    - [ ] Video filters (brightness, contrast, saturation)
    - ✅ Playback speed controls (0.75x to 2x) - Implemented in mobile player
    - ✅ Video rotation and aspect ratio controls - Multiple modes available
    - ✅ Hardware acceleration optimization - Improved ExoPlayer integration

- [~] **Picture-in-Picture** - Basic Implementation Complete
    - ✅ Multi-tasking video playback - PiP button available in video player
    - ✅ PiP controls and gesture support - Basic controls implemented
    - ✅ Smart PiP activation based on context - Device capability detection
    - [ ] PiP size and position preferences

**Dependencies**: ExoPlayer advanced features, PictureInPicture API
**Estimated Effort**: 3-4 weeks
**Success Criteria**: ✅ Subtitle support implemented, ✅ PiP functionality working, [ ] Chapter navigation pending

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

## 👥 **PHASE 3.5: Social & Sharing Features** 🟡 *Medium Priority - NEW*

**Objective**: Enable social viewing experiences and content sharing while maintaining privacy

**Rationale**: Social features enhance engagement and discovery. Users want to share recommendations and watch content together, especially for family/friend groups using Jellyfin.

### **Major Step 3.5.1: Social Viewing Features** ⏳ *Not Started*

**Target Completion**: Month 12

#### Implementation Checklist:

- [ ] **Watch Party / Watch Together**
    - [ ] Create watch party session
    - [ ] Share session link/code
    - [ ] Synchronized playback across devices
    - [ ] Host controls (play/pause/seek)
    - [ ] Participant list and presence
    - [ ] Chat integration (optional)
    - [ ] Handle network latency and sync drift
    - [ ] Privacy controls (who can join)

- [ ] **Shared Watch Queues**
    - [ ] Create shared queues with family/friends
    - [ ] Add/remove items from shared queue
    - [ ] Queue voting system
    - [ ] "Up next" suggestions for groups
    - [ ] Queue history and analytics

- [ ] **Activity Feeds** (Privacy-Aware)
    - [ ] Opt-in activity sharing
    - [ ] "Recently watched" feed (if enabled)
    - [ ] "Currently watching" status
    - [ ] Favorites and ratings sharing
    - [ ] Granular privacy controls per activity type

**Dependencies**: WebSocket or real-time sync protocol, Server-side support
**Estimated Effort**: 4-5 weeks
**Success Criteria**: Watch party working with <2 second sync, privacy controls functional

### **Major Step 3.5.2: Content Sharing & Recommendations** ⏳ *Not Started*

**Target Completion**: Month 13

#### Implementation Checklist:

- [ ] **Share Content System**
    - [ ] Share to messaging apps (WhatsApp, Telegram, etc.)
    - [ ] Share to social media (with privacy warnings)
    - [ ] Generate share links with metadata preview
    - [ ] Share with server users directly
    - [ ] Share collections and playlists
    - [ ] Custom share message templates

- [ ] **Recommendation Engine**
    - [ ] "Recommend to user" action
    - [ ] Recommendation inbox/notifications
    - [ ] Accept/decline recommendations
    - [ ] Recommendation reasons ("John thinks you'll like this")
    - [ ] Recommendation tracking (what was watched)

- [ ] **Comments & Ratings** (Server Integration)
    - [ ] Display server comments if available
    - [ ] Add comments from mobile
    - [ ] Star ratings display and submission
    - [ ] Like/dislike system
    - [ ] Spoiler warnings for comments
    - [ ] Comment moderation tools

- [ ] **Family & Friends Discovery**
    - [ ] "Friends who watched this"
    - [ ] "Popular in your circle"
    - [ ] Collaborative filtering recommendations
    - [ ] Shared favorites showcase

**Dependencies**: Jellyfin server APIs, Share intent system
**Estimated Effort**: 3-4 weeks
**Success Criteria**: Sharing working to major apps, recommendations functional, comments displaying

**Phase 3.5 Summary**:
- **Total Estimated Effort**: 7-9 weeks
- **Privacy Focus**: All social features must be opt-in with clear privacy controls
- **Success Metrics**: Watch party usage >10%, sharing adoption >25%, recommendations engagement >40%

---

## 📱 **PHASE 4: Mobile Experience Polish** 🟡 *Medium Priority (Elevated from Low)*

### **Major Step 4.1: Onboarding & User Experience** ⏳ *Not Started*

**Target Completion**: Months 14-15 *(Reordered - now higher priority)*

#### Implementation Checklist:

- [ ] **First-Run Experience**
    - [ ] Welcome screen with app overview
    - [ ] Feature highlights carousel
    - [ ] Quick setup wizard
    - [ ] Server connection guidance
    - [ ] Permission request flow with explanations
    - [ ] Skip option for advanced users

- [ ] **Feature Discovery System**
    - [ ] Contextual tooltips for new features
    - [ ] "What's new" screen after updates
    - [ ] Feature hints on first use
    - [ ] Interactive tutorials for complex features
    - [ ] Progress tracking for onboarding completion
    - [ ] Achievement system for feature exploration

- [ ] **In-App Help & Documentation**
    - [ ] Help center with searchable articles
    - [ ] FAQ section
    - [ ] Troubleshooting guides
    - [ ] Video tutorials (linked)
    - [ ] Contact support integration
    - [ ] Community forum links

- [ ] **Settings & Customization Expansion** 🆕
    - [ ] **Video Preferences**
        - [ ] Default quality presets (Auto, High, Medium, Low)
        - [ ] Auto-play next episode toggle
        - [ ] Skip intro/credits default behavior
        - [ ] Subtitle language preference
        - [ ] Resume playback position handling
    - [ ] **Audio Preferences**
        - [ ] Default audio track language
        - [ ] Audio normalization settings
        - [ ] Crossfade duration
        - [ ] Gapless playback toggle
    - [ ] **Appearance Settings**
        - [ ] Theme selection (System, Light, Dark, AMOLED Black)
        - [ ] Custom accent colors (beyond Material You)
        - [ ] Font size preferences (Small, Medium, Large, Extra Large)
        - [ ] Grid vs List view defaults
        - [ ] Thumbnail size preferences
        - [ ] Animation speed controls
    - [ ] **Network Settings**
        - [ ] Cellular streaming quality limits
        - [ ] Download over cellular toggle
        - [ ] Bandwidth usage monitoring
        - [ ] Connection quality indicators
        - [ ] Streaming buffer size
    - [ ] **Privacy & Data Settings**
        - [ ] Analytics opt-out
        - [ ] Crash reporting preferences
        - [ ] Watch history privacy
        - [ ] Activity sharing controls
    - [ ] **Notification Preferences**
        - [ ] Per-category notification controls
        - [ ] Notification sound selection
        - [ ] LED color preferences
        - [ ] Do Not Disturb integration

**Dependencies**: DataStore for preferences, existing settings infrastructure
**Estimated Effort**: 4-5 weeks
**Success Criteria**: Onboarding completion >80%, settings comprehensive, help articles accessible

### **Major Step 4.2: Adaptive UI System** ⏳ *Not Started*

**Target Completion**: Months 16-17 *(Reordered)*

#### Implementation Checklist:

- [ ] **Foldable Device Support**
    - [ ] Optimized layouts for Samsung Galaxy Fold, Pixel Fold
    - [ ] Hinge-aware layouts and content positioning
    - [ ] Seamless folded/unfolded transitions
    - [ ] Multi-window support on foldables
    - [ ] Continuity between folded/unfolded states

- [ ] **Tablet Optimization**
    - [ ] Multi-pane layouts for large screens
    - [ ] Master-detail patterns
    - [ ] Split-screen support and optimization
    - [ ] Drag-and-drop functionality
    - [ ] Tablet-specific navigation patterns
    - [ ] Stylus support for annotations (where applicable)

- [ ] **Advanced Responsive Design**
    - [ ] Seamless portrait/landscape transitions
    - [ ] Content-aware layout adaptations
    - [ ] Dynamic typography scaling
    - [ ] Orientation-specific feature availability
    - [ ] Safe area handling for notches/cameras

- [ ] **Modern UI Enhancements**
    - [ ] Dynamic Island-style notifications (Android 13+)
    - [ ] Edge-to-edge design with gesture navigation
    - [ ] Adaptive refresh rates for smooth scrolling
    - [ ] Enhanced haptic feedback patterns
    - [ ] Predictive back gesture animations

**Dependencies**: WindowSizeClass, Adaptive Navigation Suite (already included), Foldable APIs
**Estimated Effort**: 5-6 weeks
**Success Criteria**: Foldable support working, tablet layouts optimized, responsive design seamless

### **Major Step 4.3: Performance & Battery Optimization** ⏳ *Not Started*

**Target Completion**: Month 18 *(Reordered)*

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

### **Major Step 4.4: Accessibility Excellence** 🟡 *Medium Priority - NEW*

**Target Completion**: Months 19-20

**Rationale**: Accessibility is both an ethical imperative and legal requirement in many jurisdictions. A fully accessible app opens the app to millions more users and demonstrates quality engineering.

#### Implementation Checklist:

- [ ] **Screen Reader Optimization**
    - [ ] Complete TalkBack support audit
    - [ ] Content descriptions for all interactive elements
    - [ ] Semantic ordering of screen elements
    - [ ] Meaningful announcements for state changes
    - [ ] Custom accessibility actions where appropriate
    - [ ] Live region announcements for dynamic content
    - [ ] Accessibility traversal ordering

- [ ] **Visual Accessibility**
    - [ ] High contrast mode implementation
    - [ ] Color blindness modes (Deuteranopia, Protanopia, Tritanopia)
    - [ ] Text scaling support up to 200%
    - [ ] Minimum contrast ratios (WCAG 2.1 AA: 4.5:1 for text)
    - [ ] Focus indicators clearly visible
    - [ ] No reliance on color alone for information
    - [ ] Large text mode for all UI

- [ ] **Motor Accessibility**
    - [ ] Minimum touch target size 48dp enforcement
    - [ ] Switch Access support
    - [ ] Voice Access optimization
    - [ ] Extended touch target areas
    - [ ] Configurable touch and hold duration
    - [ ] Gesture alternatives for all interactions

- [ ] **Cognitive Accessibility**
    - [ ] Reduce motion support for animations
    - [ ] Clear and simple error messages
    - [ ] Consistent navigation patterns
    - [ ] Progress indicators for long operations
    - [ ] Timeout warnings and extensions
    - [ ] Clear focus states

- [ ] **Keyboard Navigation**
    - [ ] Full keyboard navigation support
    - [ ] Visible focus indicators
    - [ ] Logical tab order
    - [ ] Keyboard shortcuts documentation
    - [ ] Arrow key navigation in lists

- [ ] **Testing & Compliance**
    - [ ] WCAG 2.1 AA compliance audit
    - [ ] Accessibility Scanner integration in CI
    - [ ] Manual testing with screen readers
    - [ ] User testing with accessibility needs
    - [ ] Accessibility documentation
    - [ ] Automated accessibility tests

**Dependencies**: Android Accessibility APIs, Accessibility Scanner, Manual testing
**Estimated Effort**: 4-5 weeks
**Success Criteria**: WCAG 2.1 AA compliant, 95%+ Accessibility Scanner pass rate, TalkBack fully functional

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

## 📅 **Implementation Timeline Summary** *(Updated with Phase 1.5)*

### **Completed (Months 1-2)**: TV Foundation ✅

- Phase 1.1: TV Architecture Implementation ✅ *Complete*
- Phase 1.2: TV Playback Experience ✅ *Complete*

### **Immediate Focus (Months 3-5)**: Mobile Core Experience 🔴 *NEW PRIORITY*

- **Phase 1.5: Mobile Core Experience** (10-14 weeks)
  - Step 1.5.1: Modern Android Integration ✅ (planning complete)
  - Step 1.5.2: Mobile Gesture & Interaction System 🟡 (in progress)
  - Step 1.5.3: Mobile Loading States & Animations
  - Step 1.5.4: Content Discovery Enhancements
- Phase 1.1 Polish: TV refinements (Similar content, remote shortcuts)

### **Short-term (Months 6-9)**: Media Enhancement 🟡

- Phase 2.1: Advanced Audio System + Mobile Audio Platform Integration
- Phase 2.2: Offline Content System
- Phase 2.3: Advanced Video Features

### **Medium-term (Months 10-13)**: Intelligence & Social 🟡

- Phase 3.1: Advanced Search & Discovery
- Phase 3.2: Personalization Engine
- **Phase 3.5: Social & Sharing Features** (NEW)

### **Long-term (Months 14-20)**: Polish, Accessibility & Connectivity 🟡

- Phase 4.1: Onboarding & User Experience (expanded)
- Phase 4.2: Adaptive UI System (Foldables, Tablets)
- Phase 4.3: Performance & Battery Optimization
- **Phase 4.4: Accessibility Excellence** (NEW)
- Phase 5.1: Multi-Device Sync
- Phase 5.2: Cloud Integration

### **Future/Optional (Months 21+)**: Advanced Features 🔵

- Phase 6.1: Gaming Support
- Phase 7.1: Modular Architecture
- Phase 7.2: Testing & QA Enhancement

---

## 🎯 **Success Metrics & KPIs** *(Updated)*

### **TV Experience Goals**

- [ ] 4.5+ rating on Google TV Play Store
- [ ] 50%+ TV usage for video content
- [ ] Sub-3 second navigation response time on TV
- [x] ✅ **D-pad navigation working** - Complete with focus management
- [x] ✅ **TV video/audio players functional** - Complete with visualizations

### **Mobile Experience Goals** 🆕

- [ ] 4.5+ rating on Google Play Store
- [ ] Sub-1 second screen transition time
- [ ] 60%+ gesture control adoption in video player
- [ ] 30%+ widget installation rate among active users
- [ ] 80%+ onboarding completion rate
- [ ] 50%+ app shortcut usage
- [ ] 25%+ content sharing adoption

### **Performance Targets**

- [ ] Sub-2 second app launch time
- [ ] 95% crash-free sessions
- [ ] 30% reduction in memory usage
- [x] ✅ **Eliminate frame drops during data loading** - Fixed 46+ frame skipping issue
- [x] ✅ **Prevent main thread blocking** - All heavy operations moved to background threads
- [ ] 60fps animation smoothness across all screens
- [ ] <100ms haptic feedback response time
- [ ] ANR rate <0.1%

### **User Engagement**

- [ ] 40% increase in daily active users
- [ ] 45% daily return rate on mobile
- [ ] 70% music playback on mobile devices
- [ ] 30% mobile usage for music content (original)
- [ ] 60% user retention after 30 days
- [ ] 10%+ watch party adoption
- [ ] 25%+ content sharing rate

### **Quality Standards**

- [ ] 90% automated test coverage
- [ ] Full accessibility compliance (WCAG 2.1 AA)
- [ ] 95%+ Accessibility Scanner pass rate
- [ ] Support for 95% of target devices
- [ ] TalkBack 100% functional throughout app

### **Platform Integration Goals** 🆕

- [ ] Android Auto: 20%+ usage among music listeners
- [ ] Wear OS: 15%+ adoption rate
- [ ] Widget: 30%+ installation rate
- [ ] Quick Settings Tile: 40%+ activation rate

---

## ⚡ **Quick Wins: Immediate Mobile Improvements** 🆕

These features can be implemented quickly (1-2 weeks each) to deliver immediate value and user satisfaction while the larger Phase 1.5 is being planned:

### **Week 1: Pull-to-Refresh & Swipe Gestures**
- **Implementation**: Add `pullRefresh` modifier to home/library screens
- **User Benefit**: Instant content refresh without button taps
- **Files**: `HomeScreen.kt`, `LibraryScreen.kt`, `MoviesScreen.kt`, etc.
- **Effort**: 2-3 days
- **Dependencies**: Compose Material3 (already included)

### **Week 2: App Shortcuts**
- **Implementation**: Add 4 static shortcuts (Search, Downloads, Favorites, Continue Watching)
- **User Benefit**: Quick access to common actions from launcher
- **Files**: `shortcuts.xml`, update `AndroidManifest.xml`
- **Effort**: 2-3 days
- **Dependencies**: None (Android API 25+)

### **Week 3: Haptic Feedback**
- **Implementation**: Add haptic feedback to key interactions (buttons, seek, gestures)
- **User Benefit**: Tactile confirmation of actions
- **Files**: Create `HapticFeedbackHelper.kt`, update player controls and buttons
- **Effort**: 3-4 days
- **Dependencies**: `HapticFeedback` API (already available)

### **Week 4: Shimmer Loading States**
- **Implementation**: Replace plain loading indicators with shimmer skeletons
- **User Benefit**: Better perceived performance, clearer loading feedback
- **Files**: Create `ShimmerComponents.kt`, update all list screens
- **Effort**: 4-5 days
- **Dependencies**: Compose Animation (already included)

### **Week 5: Material You Dynamic Colors**
- **Implementation**: Full Material You theme support with dynamic colors
- **User Benefit**: App matches user's wallpaper/theme
- **Files**: Update `Theme.kt`, ensure `dynamicColor = true` is used
- **Effort**: 2-3 days
- **Dependencies**: Android 12+ (already targeting)

### **Week 6: Swipe-to-Dismiss Continue Watching**
- **Implementation**: Add swipe gesture to remove items from continue watching
- **User Benefit**: Clean up continue watching list easily
- **Files**: Update `ContinueWatchingSection.kt` or equivalent
- **Effort**: 3-4 days
- **Dependencies**: Compose gestures (already available)

### **Week 7: Video Player Brightness/Volume Gestures**
- **Implementation**: Swipe up/down on left/right for brightness/volume
- **User Benefit**: Standard video player behavior users expect
- **Files**: Update `VideoPlayerScreen.kt` or `ExpressiveVideoControls.kt`
- **Effort**: 4-5 days
- **Dependencies**: Window/Audio Manager APIs

### **Week 8: Quick Settings Tile**
- **Implementation**: Add playback control tile for notification shade
- **User Benefit**: Quick media control without opening app
- **Files**: Create `JellyfinTileService.kt`, update manifest
- **Effort**: 3-4 days
- **Dependencies**: TileService API (Android 7+)

**Quick Wins Summary**: 8 impactful features implementable in ~8 weeks with 1 developer, providing immediate user value while Phase 1.5 is being planned and executed.

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

**Last Updated**: 2025-01-11
**Version**: 2.0 - Major Mobile-First Update
**Status**: Phase 1.1 Complete + Phase 1.2 Complete (TV Video Player, TV Audio Player, Quick Connect) + Mobile Video Player Improvements Complete + Performance Optimizations Implemented

**What's New in v2.0**:
- 🆕 **NEW Phase 1.5**: Mobile Core Experience (HIGH PRIORITY) - 4 major steps for modern Android integration
- 🆕 **NEW Phase 3.5**: Social & Sharing Features - Watch parties, content sharing, recommendations
- 🆕 **NEW Phase 4.4**: Accessibility Excellence - WCAG 2.1 AA compliance focus
- ✨ **Enhanced Phase 2.1**: Android Auto, Wear OS, and mobile audio platform integration
- 📱 **Enhanced Phase 4.1**: Comprehensive onboarding, settings expansion, and help system
- ⚡ **NEW Section**: Quick Wins - 8 immediate improvements (1-2 weeks each)
- 📊 **Expanded Metrics**: Mobile-specific KPIs and platform integration goals
- 🔄 **Updated Timeline**: Reflects new mobile-first priority and realistic sequencing

**Priority Shift Rationale**: Mobile users likely represent the majority of the user base. While the TV experience is excellent and complete, the mobile platform deserves equal engineering attention with modern Android patterns, gesture controls, widgets, and platform integrations that users expect in 2025.

---

*This roadmap is a living document and will be updated as features are completed and priorities
evolve. Version 2.0 represents a significant expansion focusing on mobile experience parity with the completed TV platform.*
