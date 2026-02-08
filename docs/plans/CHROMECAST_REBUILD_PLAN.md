# Chromecast System Rebuild - Detailed Implementation Plan

## Executive Summary

This document outlines a comprehensive plan to rebuild the Chromecast/Google Cast integration from scratch in the JellyfinAndroid app. While the current implementation is functional, it has architectural limitations and bugs that warrant a complete rebuild for long-term maintainability and reliability.

## Current State Analysis

### Existing Implementation Overview
- **CastManager**: Singleton manager (889 lines) handling Cast lifecycle, device discovery, media loading
- **CastOptionsProvider**: Configures Cast framework with receiver app ID
- **CastPreferencesRepository**: Persists cast session data for auto-reconnect
- **UI Integration**: Cast buttons in VideoPlayerScreen, MainAppViewModel, StreamingViewModel
- **Dependencies**: 
  - `google-cast-framework:22.2.0`
  - `androidx-media3-cast:1.9.1`

### Known Issues & Limitations

#### Architecture Issues
1. **Complex State Management**: CastManager has overlapping state with manual flag updates
2. **Mixed Concerns**: Session management, playback control, and UI state in one class
3. **Threading Issues**: Mix of Main, IO, and callback threads without clear boundaries
4. **Lifecycle Coupling**: Singleton scope makes testing difficult and lifecycle unclear

#### Functional Bugs
1. **Session Recovery**: Auto-reconnect is unreliable, especially after app restart
2. **State Synchronization**: Cast state can become out of sync with actual device state
3. **Subtitle Handling**: Subtitle tracks don't always load correctly on cast device
4. **Error Recovery**: Poor error handling when Cast device disconnects unexpectedly
5. **Position Tracking**: Playback position tracking is inconsistent during seeks
6. **Volume Control**: Volume changes don't always reflect in UI immediately

#### UI/UX Issues
1. **No Cast Discovery UI**: Users can't see available devices before connecting
2. **Limited Error Messages**: Generic error messages don't help users troubleshoot
3. **No Connection Status**: No visual feedback during connection attempts
4. **Missing Controls**: No way to disconnect from UI (only through system Cast menu)
5. **No Queue Management**: Can't manage or view cast queue from app

## Rebuild Strategy

### Design Principles
1. **Separation of Concerns**: Split responsibilities across focused classes
2. **Reactive Architecture**: Use StateFlow/SharedFlow for state management
3. **Testability First**: Design for unit testing with clear interfaces
4. **Error Recovery**: Graceful degradation and clear error messages
5. **User Feedback**: Visual feedback for all Cast operations

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                         UI Layer                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ CastButton   │  │ CastDialog   │  │ CastControls │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                      ViewModel Layer                         │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              CastViewModel                           │   │
│  │  - Cast state management                             │   │
│  │  - User action handling                              │   │
│  │  - UI state transformation                           │   │
│  └──────────────────────────────────────────────────────┘   │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                     Domain Layer                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ CastSession  │  │ CastPlayback │  │ CastDiscovery│      │
│  │ Manager      │  │ Controller   │  │ Service      │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                      Data Layer                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ CastState    │  │ CastPrefs    │  │ Google Cast  │      │
│  │ Repository   │  │ Repository   │  │ Framework    │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

## Implementation Plan - Phase by Phase

---

## Phase 1: Foundation & Infrastructure (3-5 days)

### 1.1: Create New Domain Models
**Effort**: 2-3 hours  
**Priority**: High  
**Dependencies**: None

**Tasks**:
- [ ] Create `CastDevice` data class with device info
- [ ] Create `CastSessionState` sealed class hierarchy
- [ ] Create `CastPlaybackState` sealed class for playback status
- [ ] Create `CastError` sealed class for error types
- [ ] Create `CastMediaInfo` data class for media metadata
- [ ] Add comprehensive documentation to all models

**Files to Create**:
- `app/src/main/java/com/rpeters/jellyfin/domain/cast/models/CastDevice.kt`
- `app/src/main/java/com/rpeters/jellyfin/domain/cast/models/CastSessionState.kt`
- `app/src/main/java/com/rpeters/jellyfin/domain/cast/models/CastPlaybackState.kt`
- `app/src/main/java/com/rpeters/jellyfin/domain/cast/models/CastError.kt`
- `app/src/main/java/com/rpeters/jellyfin/domain/cast/models/CastMediaInfo.kt`

**Acceptance Criteria**:
- All models are immutable data classes
- Sealed classes cover all possible states
- Models are framework-agnostic (no Google Cast types)
- Unit tests cover all state transitions

---

### 1.2: Create Cast State Repository
**Effort**: 4-6 hours  
**Priority**: High  
**Dependencies**: 1.1

**Tasks**:
- [ ] Create `CastStateRepository` interface
- [ ] Implement repository with StateFlow for reactive state
- [ ] Add methods for state queries (isConnected, isCasting, etc.)
- [ ] Add state transformation utilities
- [ ] Implement state persistence using DataStore
- [ ] Write comprehensive unit tests

**Files to Create**:
- `app/src/main/java/com/rpeters/jellyfin/domain/cast/CastStateRepository.kt`
- `app/src/main/java/com/rpeters/jellyfin/data/cast/CastStateRepositoryImpl.kt`
- `app/src/test/java/com/rpeters/jellyfin/data/cast/CastStateRepositoryTest.kt`

**Acceptance Criteria**:
- State is exposed via StateFlow
- Repository is framework-agnostic
- State changes are atomic and thread-safe
- All public methods have unit tests
- State survives configuration changes

---

### 1.3: Refactor Cast Preferences
**Effort**: 2-3 hours  
**Priority**: Medium  
**Dependencies**: None

**Tasks**:
- [ ] Review existing `CastPreferencesRepository`
- [ ] Add new preferences: preferred device, auto-connect timeout
- [ ] Add device history (last 5 devices used)
- [ ] Add quality preferences for Cast (SD/HD/Original)
- [ ] Update unit tests
- [ ] Add migration for existing preferences

**Files to Modify**:
- `app/src/main/java/com/rpeters/jellyfin/data/preferences/CastPreferencesRepository.kt`
- `app/src/test/java/com/rpeters/jellyfin/data/preferences/CastPreferencesRepositoryTest.kt`

**Acceptance Criteria**:
- New preferences are persisted correctly
- Migration doesn't lose existing data
- Default values are sensible
- All preferences have unit tests

---

### 1.4: Create Cast Framework Wrapper
**Effort**: 6-8 hours  
**Priority**: High  
**Dependencies**: 1.1

**Tasks**:
- [ ] Create `CastFrameworkAdapter` to wrap Google Cast APIs
- [ ] Implement `CastContext` initialization with proper lifecycle
- [ ] Add device discovery and route selection
- [ ] Implement session listener registration/cleanup
- [ ] Add proper error handling and logging
- [ ] Write integration tests with mocked Cast framework

**Files to Create**:
- `app/src/main/java/com/rpeters/jellyfin/data/cast/CastFrameworkAdapter.kt`
- `app/src/test/java/com/rpeters/jellyfin/data/cast/CastFrameworkAdapterTest.kt`

**Acceptance Criteria**:
- All Cast framework interactions go through adapter
- Adapter converts Cast types to domain models
- Proper lifecycle management (no leaks)
- Handles Cast framework errors gracefully
- Can be mocked for testing

---

## Phase 2: Session Management (3-4 days)

### 2.1: Implement Cast Session Manager
**Effort**: 8-10 hours  
**Priority**: High  
**Dependencies**: 1.1, 1.2, 1.4

**Tasks**:
- [ ] Create `CastSessionManager` service
- [ ] Implement session lifecycle (start, resume, suspend, end)
- [ ] Add automatic session recovery on app restart
- [ ] Implement session timeout handling
- [ ] Add heartbeat mechanism to detect dead sessions
- [ ] Handle network changes during active session
- [ ] Write comprehensive unit tests

**Files to Create**:
- `app/src/main/java/com/rpeters/jellyfin/domain/cast/CastSessionManager.kt`
- `app/src/test/java/com/rpeters/jellyfin/domain/cast/CastSessionManagerTest.kt`

**Acceptance Criteria**:
- Sessions start/resume/suspend/end reliably
- Auto-recovery works after app restart (within 24 hours)
- Dead sessions are detected and cleaned up
- Network changes don't break active sessions
- All lifecycle events are logged
- State changes are published to repository

---

### 2.2: Implement Device Discovery Service
**Effort**: 4-6 hours  
**Priority**: High  
**Dependencies**: 1.4

**Tasks**:
- [ ] Create `CastDiscoveryService`
- [ ] Implement active device scanning
- [ ] Add device availability monitoring
- [ ] Implement device capability detection
- [ ] Add device history tracking
- [ ] Handle discovery errors gracefully
- [ ] Write unit tests

**Files to Create**:
- `app/src/main/java/com/rpeters/jellyfin/domain/cast/CastDiscoveryService.kt`
- `app/src/test/java/com/rpeters/jellyfin/domain/cast/CastDiscoveryServiceTest.kt`

**Acceptance Criteria**:
- Device list updates reactively
- Discovery can be started/stopped
- Devices show correct availability status
- Device capabilities are detected correctly
- Discovery doesn't drain battery excessively

---

### 2.3: Implement Connection Manager
**Effort**: 6-8 hours  
**Priority**: High  
**Dependencies**: 2.1, 2.2

**Tasks**:
- [ ] Create `CastConnectionManager`
- [ ] Implement device connection flow with retries
- [ ] Add connection timeout handling
- [ ] Implement disconnection flow
- [ ] Add auto-reconnect logic with exponential backoff
- [ ] Handle connection errors with user feedback
- [ ] Write comprehensive tests

**Files to Create**:
- `app/src/main/java/com/rpeters/jellyfin/domain/cast/CastConnectionManager.kt`
- `app/src/test/java/com/rpeters/jellyfin/domain/cast/CastConnectionManagerTest.kt`

**Acceptance Criteria**:
- Connection succeeds reliably
- Retries work with exponential backoff
- Timeouts are handled gracefully
- Disconnection cleans up properly
- Auto-reconnect respects user preferences
- All connection events are logged

---

## Phase 3: Media Playback (4-5 days)

### 3.1: Create Media Info Builder
**Effort**: 4-5 hours  
**Priority**: High  
**Dependencies**: 1.1

**Tasks**:
- [ ] Create `CastMediaInfoBuilder` utility
- [ ] Implement Jellyfin item to Cast MediaInfo conversion
- [ ] Add proper content type detection (HLS/DASH/MP4)
- [ ] Implement artwork URL generation with auth
- [ ] Add subtitle track conversion
- [ ] Handle various media types (Movie, Episode, Music)
- [ ] Write unit tests for all conversions

**Files to Create**:
- `app/src/main/java/com/rpeters/jellyfin/domain/cast/CastMediaInfoBuilder.kt`
- `app/src/test/java/com/rpeters/jellyfin/domain/cast/CastMediaInfoBuilderTest.kt`

**Acceptance Criteria**:
- All Jellyfin item types convert correctly
- Content types are detected accurately
- Auth tokens are added to URLs properly
- Subtitles are included correctly
- Artwork loads on cast device

---

### 3.2: Implement Playback Controller
**Effort**: 8-10 hours  
**Priority**: High  
**Dependencies**: 2.1, 3.1

**Tasks**:
- [ ] Create `CastPlaybackController`
- [ ] Implement media loading with proper error handling
- [ ] Add playback controls (play, pause, stop, seek)
- [ ] Implement volume control
- [ ] Add subtitle selection/toggling
- [ ] Implement playback position tracking
- [ ] Handle buffering and loading states
- [ ] Add playback error recovery
- [ ] Write comprehensive tests

**Files to Create**:
- `app/src/main/java/com/rpeters/jellyfin/domain/cast/CastPlaybackController.kt`
- `app/src/test/java/com/rpeters/jellyfin/domain/cast/CastPlaybackControllerTest.kt`

**Acceptance Criteria**:
- Media loads reliably on cast device
- All playback controls work correctly
- Volume changes reflect immediately
- Subtitles can be toggled
- Position tracking is accurate
- Buffering states are reported
- Errors are handled gracefully

---

### 3.3: Implement Position Sync Service
**Effort**: 4-6 hours  
**Priority**: Medium  
**Dependencies**: 3.2

**Tasks**:
- [ ] Create `CastPositionSyncService`
- [ ] Implement periodic position polling
- [ ] Add position reporting to Jellyfin server
- [ ] Handle seek operations correctly
- [ ] Sync position when switching from local to cast playback
- [ ] Stop sync when playback ends
- [ ] Write unit tests

**Files to Create**:
- `app/src/main/java/com/rpeters/jellyfin/domain/cast/CastPositionSyncService.kt`
- `app/src/test/java/com/rpeters/jellyfin/domain/cast/CastPositionSyncServiceTest.kt`

**Acceptance Criteria**:
- Position syncs every 10 seconds
- Server receives accurate position updates
- Seeks don't cause duplicate updates
- Sync stops when playback ends
- Resume position is accurate

---

### 3.4: Implement Queue Management
**Effort**: 6-8 hours  
**Priority**: Medium  
**Dependencies**: 3.2

**Tasks**:
- [ ] Create `CastQueueManager`
- [ ] Implement queue creation and modification
- [ ] Add item insertion/removal
- [ ] Implement queue reordering
- [ ] Add auto-play next episode support
- [ ] Handle queue state persistence
- [ ] Write unit tests

**Files to Create**:
- `app/src/main/java/com/rpeters/jellyfin/domain/cast/CastQueueManager.kt`
- `app/src/test/java/com/rpeters/jellyfin/domain/cast/CastQueueManagerTest.kt`

**Acceptance Criteria**:
- Queue can be created and modified
- Items can be added/removed/reordered
- Auto-play next episode works
- Queue state persists across sessions
- Queue UI reflects actual state

---

## Phase 4: ViewModel & UI Integration (3-4 days)

### 4.1: Create Cast ViewModel
**Effort**: 6-8 hours  
**Priority**: High  
**Dependencies**: 2.1, 2.2, 2.3, 3.2

**Tasks**:
- [ ] Create `CastViewModel`
- [ ] Expose Cast state as StateFlow for UI
- [ ] Implement user action handlers
- [ ] Add proper error handling with user messages
- [ ] Implement device selection flow
- [ ] Add playback control commands
- [ ] Write comprehensive unit tests

**Files to Create**:
- `app/src/main/java/com/rpeters/jellyfin/ui/viewmodel/CastViewModel.kt`
- `app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/CastViewModelTest.kt`

**Acceptance Criteria**:
- All UI state exposed via StateFlow
- User actions handled correctly
- Errors shown to user appropriately
- State updates are immediate
- ViewModel survives configuration changes
- All public methods tested

---

### 4.2: Create Cast Button Component
**Effort**: 3-4 hours  
**Priority**: High  
**Dependencies**: 4.1

**Tasks**:
- [ ] Create `CastButton` Composable
- [ ] Implement visual states (disconnected, connecting, connected, casting)
- [ ] Add click handling for device selection
- [ ] Show current device name when connected
- [ ] Add animations for state transitions
- [ ] Ensure accessibility
- [ ] Write UI tests

**Files to Create**:
- `app/src/main/java/com/rpeters/jellyfin/ui/components/cast/CastButton.kt`
- `app/src/androidTest/java/com/rpeters/jellyfin/ui/components/cast/CastButtonTest.kt`

**Acceptance Criteria**:
- Button shows correct state
- Click opens device selection
- Visual feedback is immediate
- Animations are smooth
- Accessible to screen readers
- Works on all screen sizes

---

### 4.3: Create Device Selection Dialog
**Effort**: 4-6 hours  
**Priority**: High  
**Dependencies**: 4.1, 2.2

**Tasks**:
- [ ] Create `CastDeviceDialog` Composable
- [ ] Show list of available devices
- [ ] Add device status indicators
- [ ] Implement device selection handling
- [ ] Add "Scanning..." state
- [ ] Show "No devices found" message
- [ ] Add refresh button
- [ ] Write UI tests

**Files to Create**:
- `app/src/main/java/com/rpeters/jellyfin/ui/components/cast/CastDeviceDialog.kt`
- `app/src/androidTest/java/com/rpeters/jellyfin/ui/components/cast/CastDeviceDialogTest.kt`

**Acceptance Criteria**:
- Dialog shows available devices
- Device list updates reactively
- Selection connects to device
- Scanning state is clear
- Empty state is helpful
- Dialog is dismissible

---

### 4.4: Create Cast Controls Panel
**Effort**: 6-8 hours  
**Priority**: High  
**Dependencies**: 4.1, 3.2

**Tasks**:
- [ ] Create `CastControlsPanel` Composable
- [ ] Show current media info (title, image, time)
- [ ] Add playback controls (play/pause, seek)
- [ ] Add volume control slider
- [ ] Show subtitle selection
- [ ] Add disconnect button
- [ ] Implement queue view
- [ ] Write UI tests

**Files to Create**:
- `app/src/main/java/com/rpeters/jellyfin/ui/components/cast/CastControlsPanel.kt`
- `app/src/androidTest/java/com/rpeters/jellyfin/ui/components/cast/CastControlsPanelTest.kt`

**Acceptance Criteria**:
- Media info displays correctly
- All controls work properly
- Volume changes reflect immediately
- Subtitles can be toggled
- Disconnect works reliably
- Queue is accessible

---

### 4.5: Integrate into Video Player Screen
**Effort**: 4-6 hours  
**Priority**: High  
**Dependencies**: 4.2, 4.3, 4.4

**Tasks**:
- [ ] Add CastButton to VideoPlayerScreen toolbar
- [ ] Implement cast initiation from player
- [ ] Add seamless local-to-cast transition
- [ ] Show cast controls when casting
- [ ] Hide local player when casting
- [ ] Maintain playback position during transition
- [ ] Update existing tests

**Files to Modify**:
- `app/src/main/java/com/rpeters/jellyfin/ui/player/VideoPlayerScreen.kt`
- `app/src/androidTest/java/com/rpeters/jellyfin/ui/player/VideoPlayerScreenTest.kt`

**Acceptance Criteria**:
- Cast button is visible and functional
- Transition to cast is smooth
- Position transfers correctly
- Cast controls are accessible
- Local player hidden when casting
- Existing functionality not broken

---

### 4.6: Integrate into Detail Screens
**Effort**: 3-4 hours  
**Priority**: Medium  
**Dependencies**: 4.2

**Tasks**:
- [ ] Add CastButton to MovieDetailScreen
- [ ] Add CastButton to TVEpisodeDetailScreen
- [ ] Implement "Send to Cast" action
- [ ] Show preview on cast device when browsing
- [ ] Update existing tests

**Files to Modify**:
- `app/src/main/java/com/rpeters/jellyfin/ui/screens/MovieDetailScreen.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/screens/TVEpisodeDetailScreen.kt`

**Acceptance Criteria**:
- Cast button visible on detail screens
- Preview works correctly
- Playback starts from cast
- Existing functionality intact

---

## Phase 5: Testing & Polish (2-3 days)

### 5.1: Comprehensive Unit Testing
**Effort**: 8-10 hours  
**Priority**: High  
**Dependencies**: All previous phases

**Tasks**:
- [ ] Ensure 80%+ code coverage for all Cast classes
- [ ] Test all error scenarios
- [ ] Test edge cases (network loss, device disconnect)
- [ ] Test state transitions exhaustively
- [ ] Add integration tests between components
- [ ] Performance test (memory leaks, thread usage)

**Acceptance Criteria**:
- Code coverage ≥ 80% for Cast domain layer
- Code coverage ≥ 70% for Cast UI layer
- All error paths tested
- No memory leaks detected
- All tests pass consistently

---

### 5.2: Manual Testing & Bug Fixes
**Effort**: 6-8 hours  
**Priority**: High  
**Dependencies**: All previous phases

**Tasks**:
- [ ] Test with real Chromecast device
- [ ] Test with Chromecast Audio
- [ ] Test with Google TV
- [ ] Test all media types (movies, episodes, music)
- [ ] Test subtitle selection and toggling
- [ ] Test network interruptions
- [ ] Test app backgrounding/foregrounding
- [ ] Fix all discovered bugs

**Test Devices**:
- Chromecast (3rd gen or newer)
- Chromecast with Google TV
- Android TV device with Cast receiver

**Acceptance Criteria**:
- All features work on all devices
- No crashes during normal operation
- Graceful handling of errors
- Smooth user experience

---

### 5.3: Performance Optimization
**Effort**: 4-6 hours  
**Priority**: Medium  
**Dependencies**: 5.2

**Tasks**:
- [ ] Profile memory usage during casting
- [ ] Optimize state update frequency
- [ ] Reduce network polling overhead
- [ ] Optimize media info serialization
- [ ] Add memory leak tests
- [ ] Ensure smooth 60 FPS in cast UI

**Acceptance Criteria**:
- No memory leaks during cast sessions
- CPU usage < 5% during idle casting
- Network traffic < 10 KB/s during idle
- UI maintains 60 FPS
- Battery drain acceptable (< 5% per hour)

---

### 5.4: Documentation & Code Comments
**Effort**: 3-4 hours  
**Priority**: Medium  
**Dependencies**: All previous phases

**Tasks**:
- [ ] Add KDoc comments to all public APIs
- [ ] Document architecture decisions
- [ ] Create Cast troubleshooting guide
- [ ] Update CLAUDE.md with Cast details
- [ ] Update CURRENT_STATUS.md
- [ ] Create Cast developer guide

**Files to Create/Update**:
- `docs/CAST_ARCHITECTURE.md` (new)
- `docs/CAST_TROUBLESHOOTING.md` (new)
- `CLAUDE.md` (update)
- `CURRENT_STATUS.md` (update)

**Acceptance Criteria**:
- All public APIs documented
- Architecture clearly explained
- Troubleshooting guide helpful
- Developer guide complete

---

## Phase 6: Migration & Cleanup (1-2 days)

### 6.1: Gradual Migration Strategy
**Effort**: 4-6 hours  
**Priority**: High  
**Dependencies**: All Phase 4 complete

**Tasks**:
- [ ] Create feature flag for new Cast system
- [ ] Implement fallback to old system
- [ ] Add migration path for saved preferences
- [ ] Test with both systems side-by-side
- [ ] Gradually phase out old system

**Acceptance Criteria**:
- Feature flag works correctly
- Fallback is seamless
- Preferences migrate correctly
- Both systems can coexist temporarily

---

### 6.2: Remove Old Cast Implementation
**Effort**: 2-3 hours  
**Priority**: Medium  
**Dependencies**: 6.1, All testing complete

**Tasks**:
- [ ] Remove old CastManager
- [ ] Remove old Cast UI integrations
- [ ] Remove feature flag
- [ ] Update all imports
- [ ] Clean up unused dependencies
- [ ] Update documentation

**Files to Remove**:
- `app/src/main/java/com/rpeters/jellyfin/ui/player/CastManager.kt`
- Old Cast UI code in ViewModels

**Acceptance Criteria**:
- Old code completely removed
- No unused imports
- App compiles and runs
- All tests pass

---

### 6.3: Release Notes & Announcement
**Effort**: 1-2 hours  
**Priority**: Low  
**Dependencies**: 6.2

**Tasks**:
- [ ] Write detailed release notes
- [ ] Document breaking changes (if any)
- [ ] Create upgrade guide
- [ ] Announce improvements to users
- [ ] Update app store description

**Acceptance Criteria**:
- Release notes complete
- Breaking changes documented
- Upgrade guide helpful
- Announcement clear

---

## Timeline & Resource Allocation

### Total Estimated Effort
- **Phase 1 (Foundation)**: 3-5 days
- **Phase 2 (Session Management)**: 3-4 days
- **Phase 3 (Media Playback)**: 4-5 days
- **Phase 4 (UI Integration)**: 3-4 days
- **Phase 5 (Testing & Polish)**: 2-3 days
- **Phase 6 (Migration & Cleanup)**: 1-2 days

**Total**: 16-23 days (3-5 weeks with one developer)

### Recommended Team Structure
- **1 Senior Android Developer** (Lead, Architecture, Core Features)
- **1 Mid-Level Android Developer** (UI Components, Testing)
- **1 QA Engineer** (Testing, Bug Reports, Device Coverage)

With this team, timeline reduces to **2-3 weeks**.

---

## Risk Assessment & Mitigation

### High Risks

#### Risk: Breaking Existing Functionality
**Probability**: Medium  
**Impact**: High  
**Mitigation**:
- Implement feature flag for gradual rollout
- Keep old system as fallback during migration
- Extensive testing before removing old code
- Beta testing with subset of users

#### Risk: Google Cast API Changes
**Probability**: Low  
**Impact**: High  
**Mitigation**:
- Use stable Cast framework version
- Abstract Cast APIs behind adapter
- Monitor Cast framework release notes
- Have rollback plan ready

#### Risk: Device Compatibility Issues
**Probability**: Medium  
**Impact**: Medium  
**Mitigation**:
- Test on multiple device types
- Implement device capability detection
- Graceful degradation for unsupported features
- Community beta testing program

### Medium Risks

#### Risk: Performance Degradation
**Probability**: Low  
**Impact**: Medium  
**Mitigation**:
- Profile performance early
- Optimize state update frequency
- Use efficient data structures
- Continuous performance monitoring

#### Risk: Timeline Overrun
**Probability**: Medium  
**Impact**: Medium  
**Mitigation**:
- Buffer time in estimates (30%)
- Prioritize features (MVP first)
- Daily progress tracking
- Regular team check-ins

---

## Success Metrics

### Functional Metrics
- [ ] 100% of old Cast features work in new system
- [ ] Zero P0/P1 bugs in Cast functionality
- [ ] Session recovery success rate > 95%
- [ ] Connection success rate > 98%
- [ ] Playback start success rate > 99%

### Performance Metrics
- [ ] Connection time < 3 seconds (90th percentile)
- [ ] Media load time < 2 seconds (90th percentile)
- [ ] Memory usage < 50 MB during casting
- [ ] CPU usage < 5% during idle casting
- [ ] Battery drain < 5% per hour of casting

### Code Quality Metrics
- [ ] Code coverage ≥ 80% for domain layer
- [ ] Code coverage ≥ 70% for UI layer
- [ ] Zero memory leaks detected
- [ ] Zero threading issues detected
- [ ] All public APIs documented

### User Experience Metrics
- [ ] User satisfaction rating > 4.5/5
- [ ] Cast feature usage increase > 20%
- [ ] Bug report volume decrease > 50%
- [ ] Average session duration increase > 15%

---

## Dependencies & Prerequisites

### Technical Dependencies
- Android Studio 2024.1.1+
- Gradle 9.0.0+
- Kotlin 2.3.0+
- Google Cast Framework 22.2.0+ (consider upgrading)
- AndroidX Media3 1.9.1+

### Knowledge Prerequisites
- Deep understanding of Google Cast framework
- Expertise in Kotlin Coroutines and Flow
- Experience with Jetpack Compose
- Understanding of MVVM architecture
- Media playback expertise (HLS, DASH, MP4)

### Testing Prerequisites
- Access to multiple Cast devices
- Reliable WiFi network for testing
- Jellyfin server for integration testing
- Performance profiling tools

---

## Post-Implementation Roadmap

### Immediate Next Steps (After Rebuild)
1. **Custom Receiver App** (Optional)
   - Build custom Cast receiver for Jellyfin branding
   - Add advanced features (web interface, server-side transcoding)
   - Improved subtitle rendering

2. **Advanced Features**
   - Multi-device casting (send to multiple devices)
   - Cast to speaker groups
   - Video quality auto-adjustment based on network

3. **Analytics & Monitoring**
   - Track Cast usage patterns
   - Monitor error rates
   - Performance telemetry

### Long-Term Vision
- Seamless multi-room audio with Chromecast Audio
- Integration with Google Assistant for voice commands
- Cast queue sharing between users
- Picture-in-picture mode while casting

---

## Conclusion

This comprehensive rebuild of the Chromecast system will provide a solid foundation for years of reliable casting functionality. By separating concerns, improving testability, and focusing on user experience, we'll deliver a Cast implementation that users love and developers can maintain with confidence.

The phased approach ensures we can deliver value incrementally while managing risk effectively. The detailed task breakdown makes it easy to track progress and identify blockers early.

**Let's build something great! 🎯**

---

## Appendix: Key Architectural Decisions

### Why Rebuild Instead of Refactor?

1. **Technical Debt**: Current implementation has accumulated significant technical debt
2. **Testing**: Current code is difficult to test due to tight coupling
3. **Maintenance**: Bug fixes often require changes across multiple concerns
4. **Extensibility**: Adding new features requires understanding entire CastManager
5. **Fresh Start**: Clean architecture is easier than incremental refactoring

### Why This Architecture?

1. **Separation of Concerns**: Each class has one clear responsibility
2. **Testability**: All components can be tested in isolation
3. **Maintainability**: Changes are localized to relevant components
4. **Scalability**: Easy to add new features without breaking existing code
5. **Standards**: Follows Android best practices and Material Design guidelines

### Key Technical Choices

1. **StateFlow over LiveData**: More powerful, Kotlin-first, works well with Coroutines
2. **Repository Pattern**: Clean abstraction over data sources
3. **Adapter Pattern**: Isolate Google Cast framework behind interface
4. **Sealed Classes**: Type-safe state management with exhaustive when
5. **Dependency Injection**: Hilt for compile-time safety and easy testing

---

**Document Version**: 1.0  
**Last Updated**: January 29, 2026  
**Author**: Jellyfin Android Team  
**Status**: Ready for Review
