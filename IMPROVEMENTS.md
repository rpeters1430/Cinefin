# Jellyfin Android Client - Improvement Roadmap

**Last Updated**: 2025-12-22

This document outlines the planned improvements and feature roadmap for the Jellyfin Android Client. For current project status, see [CURRENT_STATUS.md](CURRENT_STATUS.md).

---

## 🎯 Development Philosophy

**Priority Order**:
1. Core media playback stability
2. User experience and UI polish
3. Platform expansion (TV, tablets)
4. Advanced features

**Quality Standards**:
- All features must be tested before marking complete
- Material 3 design compliance
- Performance optimization (60fps UI, efficient network usage)
- Accessibility support

---

## 📋 Roadmap Overview

### Phase 1: Core Experience Polish (HIGH PRIORITY) 🔴
**Goal**: Ensure core features are rock-solid and delightful to use

### Phase 2: Platform Expansion (MEDIUM PRIORITY) 🟠
**Goal**: Full Android TV support and tablet optimization

### Phase 3: Advanced Media Features (MEDIUM PRIORITY) 🟡
**Goal**: Complete media playback capabilities (music, offline, live TV)

### Phase 4: Power User Features (LOW PRIORITY) 🟢
**Goal**: Advanced functionality for power users

---

## Phase 1: Core Experience Polish 🔴

**Target**: Next 2-4 weeks

### 1.1 Video Playback Enhancements
- [ ] **Verify and test PiP mode** on various devices
  - Test: Pixel phones, Samsung devices, different Android versions
  - Document: Any device-specific quirks

- [ ] **Subtitle selection UI/UX**
  - Verify subtitle loading works correctly
  - Add subtitle customization (size, color, background)
  - Test with multiple subtitle tracks

- [ ] **Audio track selection improvements**
  - Test multi-audio content
  - Add language labels
  - Persist user's audio preference

- [ ] **Playback controls polish**
  - Improve gesture responsiveness
  - Add brightness/volume indicators
  - Test on different screen sizes

**Acceptance Criteria**:
- ✅ PiP works on 5+ different devices
- ✅ Subtitles can be toggled and customized
- ✅ Audio tracks switch without playback interruption
- ✅ All playback gestures feel responsive

---

### 1.2 Material 3 Design Compliance

**Current Issue**: Material 3 Carousel and Pull-to-Refresh dependencies are disabled

- [ ] **Decision: Carousel implementation**
  - Option A: Enable official Material 3 Carousel dependency
  - Option B: Continue with custom implementation and document why
  - Action: Test official carousel if available in stable release

- [ ] **Decision: Pull-to-Refresh**
  - Option A: Enable official Material 3 pull-to-refresh
  - Option B: Use experimental API with opt-in
  - Option C: Implement custom pull-to-refresh

- [ ] **Audit all Material 3 component usage**
  - Verify compliance with Material 3 guidelines
  - Check for deprecated components
  - Add missing components where needed

**Acceptance Criteria**:
- ✅ Documented decision on carousel approach
- ✅ Pull-to-refresh works consistently
- ✅ All screens follow Material 3 design system

---

### 1.3 Performance Optimization

- [ ] **Image loading optimization**
  - Implement proper image caching strategy
  - Add placeholder images
  - Lazy load images in lists

- [ ] **List scrolling performance**
  - Profile scroll performance in large libraries
  - Optimize LazyColumn/Grid implementations
  - Add pagination where needed

- [ ] **App startup time**
  - Profile cold start time
  - Optimize initialization sequence
  - Defer non-critical operations

- [ ] **Memory usage**
  - Profile memory usage during video playback
  - Fix any memory leaks
  - Optimize bitmap handling

**Acceptance Criteria**:
- ✅ Smooth 60fps scrolling in all lists
- ✅ Cold start < 2 seconds
- ✅ No memory leaks detected by LeakCanary
- ✅ Images load progressively without jank

---

### 1.4 Search & Discovery

- [ ] **Complete advanced search filters**
  - Add filter by: genre, year, rating, actors
  - Add sort options
  - Persist search filters

- [ ] **Search result improvements**
  - Add result type grouping (movies, shows, music)
  - Add recent searches
  - Add search suggestions

- [ ] **Continue Watching feature**
  - Verify backend API support
  - Implement UI section on home screen
  - Test resume from exact position

**Acceptance Criteria**:
- ✅ All search filters work correctly
- ✅ Search results are well-organized
- ✅ Continue watching appears on home screen

---

## Phase 2: Platform Expansion 🟠

**Target**: 1-2 months

### 2.1 Android TV - Full Support

**Current Status**: TV screens exist but need comprehensive testing and polish

- [ ] **D-pad navigation audit**
  - Test all screens with D-pad only
  - Ensure focus indicators are visible
  - Fix any navigation dead-ends

- [ ] **TV-specific UI components**
  - Implement leanback-style layouts
  - Add TV-optimized image sizes
  - Create TV-specific home screen

- [ ] **Remote control support**
  - Test with various TV remotes
  - Add playback control shortcuts
  - Support voice search (if applicable)

- [ ] **TV media session**
  - Integrate with Android TV home screen
  - Add "Continue Watching" row
  - Support content recommendations

**Acceptance Criteria**:
- ✅ Full D-pad navigation without mouse/touch
- ✅ Focus indicators clearly visible
- ✅ Passes Android TV certification requirements
- ✅ Content appears in TV launcher

---

### 2.2 Tablet Optimization

- [ ] **Adaptive layouts**
  - Use Material 3 adaptive components
  - Implement master-detail patterns
  - Support landscape orientation

- [ ] **Multi-pane layouts**
  - Library browser with side panel
  - Video player with metadata panel
  - Settings with category navigation

**Acceptance Criteria**:
- ✅ Looks great on tablets (7", 10", 12"+)
- ✅ Uses screen space effectively
- ✅ Smooth experience in both orientations

---

## Phase 3: Advanced Media Features 🟡

**Target**: 2-3 months

### 3.1 Music Playback

**Current Status**: Music screen exists, playback controls incomplete

- [ ] **Full audio playback implementation**
  - Background audio playback service
  - Media notification controls
  - Lock screen controls

- [ ] **Music player UI**
  - Now Playing screen
  - Queue management
  - Shuffle and repeat modes

- [ ] **Music library features**
  - Artist view
  - Album view
  - Playlist support

**Acceptance Criteria**:
- ✅ Music plays in background
- ✅ Controls work from notification/lock screen
- ✅ Queue management works smoothly

---

### 3.2 Offline Downloads

**Current Status**: OfflineScreen.kt exists, core functionality incomplete

- [ ] **Download management**
  - Select items for download
  - Download queue
  - Download progress tracking
  - Storage management

- [ ] **Offline playback**
  - Play downloaded content
  - Sync playback state when back online
  - Handle offline mode gracefully

- [ ] **Download policies**
  - WiFi-only option
  - Quality selection
  - Auto-delete watched content

**Acceptance Criteria**:
- ✅ Can download movies/episodes
- ✅ Downloaded content plays offline
- ✅ Storage usage is manageable

---

### 3.3 Live TV & DVR

- [ ] **Live TV streaming**
  - Browse live TV channels
  - EPG (Electronic Program Guide)
  - Live stream playback

- [ ] **DVR features**
  - Browse recordings
  - Schedule recordings
  - Manage recording settings

**Acceptance Criteria**:
- ✅ Can watch live TV
- ✅ EPG is easy to navigate
- ✅ Recordings can be managed

---

### 3.4 Chromecast Integration

**Current Status**: Dependencies included, integration status unknown

- [ ] **Verify Chromecast setup**
  - Test current cast functionality
  - Document what works/doesn't work

- [ ] **Cast implementation**
  - Discover cast devices
  - Cast video content
  - Remote control while casting

- [ ] **Cast session management**
  - Queue management
  - Subtitle support while casting
  - Audio track selection

**Acceptance Criteria**:
- ✅ Can discover and connect to Chromecast
- ✅ Video casts successfully
- ✅ Remote control works smoothly

---

## Phase 4: Power User Features 🟢

**Target**: 3-6 months

### 4.1 Advanced Playback Features

- [ ] **Playback speed control** (0.5x - 2x)
- [ ] **A-B repeat** for video/music
- [ ] **Chapter navigation** (if metadata available)
- [ ] **Advanced subtitle sync** controls
- [ ] **Video filters** (brightness, contrast, saturation)

---

### 4.2 Sync Play (Watch Together)

- [ ] **Create sync play session**
- [ ] **Join sync play session**
- [ ] **Synchronized playback** across devices
- [ ] **Chat/reactions** (if supported by Jellyfin)

---

### 4.3 Multi-Profile Support

- [ ] **Switch between user profiles** in-app
- [ ] **Profile-specific settings**
- [ ] **Quick profile switching**
- [ ] **Kids mode** with content restrictions

---

### 4.4 Widgets & Notifications

- [ ] **Home screen widget** (continue watching)
- [ ] **Download progress notifications**
- [ ] **New content notifications** (optional)
- [ ] **Playback notification** enhancements

---

## 🔧 Technical Debt & Maintenance

### Code Quality
- [ ] **Reduce MainActivity.kt size** (currently ~3.0KB / 3,092 bytes)
  - Extract navigation logic
  - Extract state management
  - Break into smaller components

- [ ] **Add comprehensive unit tests**
  - Target: 70%+ code coverage
  - Focus on ViewModels and Repository
  - Add integration tests for critical flows

- [ ] **Add UI tests**
  - Compose UI testing
  - Navigation testing
  - End-to-end critical user flows

---

### Dependency Updates

- [ ] **Monitor Material 3 stable releases**
  - Carousel (if released as stable)
  - Pull-to-refresh (if released as stable)
  - Other expressive components

- [ ] **Regular dependency updates**
  - Monthly review of dependency updates
  - Test compatibility with new versions
  - Update Kotlin when Hilt supports it

---

### Build System

- [ ] **Optimize build times**
  - Profile build performance
  - Add build caching where possible
  - Optimize Gradle configuration

- [ ] **Improve CI/CD**
  - Add automated testing to CI
  - Add code quality checks (ktlint, detekt)
  - Automated release process

---

## 📊 Success Metrics

How we measure progress:

### User Experience
- **App Store Rating**: Target 4.5+/5.0
- **Crash Rate**: < 0.1%
- **Startup Time**: < 2 seconds cold start
- **UI Performance**: 60fps scrolling

### Code Quality
- **Test Coverage**: > 70%
- **Build Success Rate**: > 95%
- **Code Review Approval**: 100% before merge

### Feature Completeness
- **Phase 1**: 100% complete
- **Phase 2**: 80% complete
- **Phase 3**: 60% complete
- **Phase 4**: 40% complete

---

## 🤝 How to Contribute

1. **Pick a task** from Phase 1 (highest priority)
2. **Create a feature branch**: `feature/task-description`
3. **Implement with tests**
4. **Update this document** when complete
5. **Create a pull request** with:
   - Clear description of changes
   - Screenshots/videos for UI changes
   - Test results
   - Updated documentation

---

## 📝 Improvement Tracking

Each improvement should be tracked with:
- [ ] **Task checkbox** in this document
- [ ] **GitHub issue** (if significant work)
- [ ] **Branch created** for implementation
- [ ] **PR submitted** when ready
- [ ] **Tests added** and passing
- [ ] **Documentation updated**

When a task is completed:
- [x] **Mark checkbox complete**
- **Add completion date**
- **Link to PR**
- **Update CURRENT_STATUS.md** if feature status changes

---

## 🔄 Review Schedule

This roadmap should be reviewed:
- **Weekly**: Progress check on Phase 1 items
- **Bi-weekly**: Adjust priorities based on progress
- **Monthly**: Review Phase 2-4 items, re-prioritize

**Next Review**: 2025-12-29

---

## 📚 Related Documentation

- **[CURRENT_STATUS.md](CURRENT_STATUS.md)** - Current verified project state
- **[KNOWN_ISSUES.md](KNOWN_ISSUES.md)** - Active bugs and workarounds
- **[CONTRIBUTING.md](CONTRIBUTING.md)** - Contribution guidelines
- **[IMPROVEMENTS_ARCHIVE.md](IMPROVEMENTS_ARCHIVE.md)** - Historical plans (archived)
