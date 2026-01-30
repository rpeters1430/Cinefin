# 🎯 Rebuild Chromecast System from Scratch

## Overview

The current Chromecast implementation works but has architectural limitations, state synchronization issues, and difficult-to-maintain code. This issue tracks the complete rebuild of the casting system with a clean architecture, better testability, and improved user experience.

**Full Technical Plan**: See [CHROMECAST_REBUILD_PLAN.md](../CHROMECAST_REBUILD_PLAN.md) for comprehensive 50+ page implementation guide.

## Current Issues

### Architecture Problems
- ⚠️ Complex state management with overlapping flags
- ⚠️ Mixed concerns (session + playback + UI state in one class)
- ⚠️ Threading issues across Main, IO, and callback threads
- ⚠️ Singleton scope makes testing difficult

### Functional Bugs
- 🐛 Unreliable session recovery after app restart
- 🐛 Cast state out of sync with actual device state
- 🐛 Subtitle tracks don't always load correctly
- 🐛 Poor error handling on unexpected disconnects
- 🐛 Inconsistent playback position tracking
- 🐛 Volume changes don't reflect in UI immediately

### UX Gaps
- ❌ No Cast device discovery UI
- ❌ Generic error messages
- ❌ No visual feedback during connection
- ❌ Can't disconnect from UI (only system menu)
- ❌ No queue management UI

## New Architecture

```
UI Layer (Compose)
  ├─ CastButton (connection state)
  ├─ CastDeviceDialog (device selection)
  └─ CastControlsPanel (playback controls)
      │
ViewModel Layer
  └─ CastViewModel (UI state & actions)
      │
Domain Layer
  ├─ CastSessionManager (lifecycle)
  ├─ CastPlaybackController (playback)
  ├─ CastDiscoveryService (devices)
  └─ CastQueueManager (queue)
      │
Data Layer
  ├─ CastStateRepository (reactive state)
  ├─ CastPreferencesRepository (settings)
  └─ CastFrameworkAdapter (Google Cast wrapper)
```

## Implementation Phases

### Phase 1: Foundation & Infrastructure (3-5 days)
- [ ] **1.1** Create domain models (CastDevice, CastSessionState, CastPlaybackState, CastError) - 2-3h
- [ ] **1.2** Create CastStateRepository with StateFlow - 4-6h
- [ ] **1.3** Refactor CastPreferencesRepository with new features - 2-3h
- [ ] **1.4** Create CastFrameworkAdapter to wrap Google Cast APIs - 6-8h

### Phase 2: Session Management (3-4 days)
- [ ] **2.1** Implement CastSessionManager with lifecycle & recovery - 8-10h
- [ ] **2.2** Implement CastDiscoveryService for device scanning - 4-6h
- [ ] **2.3** Implement CastConnectionManager with retry logic - 6-8h

### Phase 3: Media Playback (4-5 days)
- [ ] **3.1** Create CastMediaInfoBuilder for Jellyfin → Cast conversion - 4-5h
- [ ] **3.2** Implement CastPlaybackController (play/pause/seek/volume) - 8-10h
- [ ] **3.3** Implement CastPositionSyncService for server sync - 4-6h
- [ ] **3.4** Implement CastQueueManager for queue & auto-play - 6-8h

### Phase 4: UI Integration (3-4 days)
- [ ] **4.1** Create CastViewModel with reactive state - 6-8h
- [ ] **4.2** Create CastButton Composable with states - 3-4h
- [ ] **4.3** Create CastDeviceDialog for device selection - 4-6h
- [ ] **4.4** Create CastControlsPanel with playback controls - 6-8h
- [ ] **4.5** Integrate into VideoPlayerScreen - 4-6h
- [ ] **4.6** Integrate into detail screens (Movie, Episode) - 3-4h

### Phase 5: Testing & Polish (2-3 days)
- [ ] **5.1** Comprehensive unit testing (80%+ coverage) - 8-10h
- [ ] **5.2** Manual testing on real devices & bug fixes - 6-8h
- [ ] **5.3** Performance optimization & profiling - 4-6h
- [ ] **5.4** Documentation & code comments - 3-4h

### Phase 6: Migration & Cleanup (1-2 days)
- [ ] **6.1** Feature flag & gradual migration - 4-6h
- [ ] **6.2** Remove old CastManager implementation - 2-3h
- [ ] **6.3** Release notes & announcement - 1-2h

## Timeline

**Total Effort**: 16-23 days (3-5 weeks solo developer)  
**With Team**: 2-3 weeks (1 senior + 1 mid-level + 1 QA)

## Success Metrics

### Functional
- ✅ 100% feature parity with old system
- ✅ Session recovery success rate > 95%
- ✅ Connection success rate > 98%
- ✅ Playback start success rate > 99%

### Performance
- ✅ Connection time < 3s (p90)
- ✅ Media load time < 2s (p90)
- ✅ Memory usage < 50 MB
- ✅ CPU usage < 5% (idle)
- ✅ Battery drain < 5%/hour

### Code Quality
- ✅ Domain layer coverage ≥ 80%
- ✅ UI layer coverage ≥ 70%
- ✅ Zero memory leaks
- ✅ All public APIs documented

## Risk Mitigation

1. **Feature Flag**: Keep old system as fallback during rollout
2. **Gradual Migration**: Test with beta users before full release
3. **Device Testing**: Test on Chromecast, Google TV, Android TV
4. **Performance Monitoring**: Profile early and often
5. **Community Beta**: Get feedback before final release

## Getting Started

1. Read [CHROMECAST_REBUILD_PLAN.md](../CHROMECAST_REBUILD_PLAN.md) for full details
2. Start with Phase 1 (Foundation) - order matters!
3. Each subtask has detailed acceptance criteria
4. Write tests as you go (TDD recommended)
5. Create separate PRs for each phase

## Dependencies

- Kotlin 2.3.0+
- Google Cast Framework 22.2.0+
- AndroidX Media3 1.9.1+
- Jetpack Compose BOM 2026.01.01
- Hilt 2.59

## Questions?

Comment below or check the full plan document for architectural decisions, code examples, and detailed task breakdowns.

**Let's rebuild this thing right! 🚀**

---

**NOTE**: This is a template for creating a GitHub issue. To create the actual issue:
1. Go to https://github.com/rpeters1430/JellyfinAndroid/issues/new
2. Copy this content as the issue body
3. Set title: "🎯 Rebuild Chromecast System from Scratch"
4. Add labels: `enhancement`, `chromecast`, `architecture`
