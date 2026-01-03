# Jellyfin Android App - Comprehensive Improvement Plan

**Created**: January 2, 2026  
**Kotlin**: 2.3.0 | **Compose BOM**: 2025.12.01 | **Material 3**: 1.5.0-alpha11

---

## Executive Summary

This document provides a prioritized, step-by-step guide to improving your Jellyfin Android client. The recommendations are based on analysis of the current codebase and organized by impact and complexity.

### Quick Wins (1-2 days each)
- Fix experimental API opt-in warnings
- Enable stable Material 3 components
- Add missing LazyList keys

### Medium Effort (3-7 days each)
- Refactor large screen files
- Complete offline download functionality
- Improve test coverage

### Major Projects (2-4 weeks each)
- Full Android TV polish
- Background music playback
- Live TV implementation

---

## Phase 1: Code Quality & Technical Debt

### 1.1 Fix Experimental API Warnings

**Priority**: High | **Effort**: 1 day | **Impact**: Build cleanliness

The build shows warnings about experimental Coroutines APIs. Add proper opt-in annotations.

**Files to modify**:
- `MainAppViewModel.kt` (lines 227-229)
- `SearchViewModel.kt` (line 70)

**Steps**:
1. Open each file and locate the experimental API usage
2. Add the appropriate annotation above the function or class:
   ```kotlin
   @OptIn(ExperimentalCoroutinesApi::class)
   ```
3. For FlowPreview APIs:
   ```kotlin
   @OptIn(FlowPreview::class)
   ```
4. Alternatively, add file-level opt-in at the top:
   ```kotlin
   @file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
   ```
5. Consider refactoring to use stable APIs if the experimental APIs aren't essential

**Verification**: Run `./gradlew build` and confirm no experimental API warnings

---

### 1.2 Refactor Large Screen Files

**Priority**: High | **Effort**: 3-5 days | **Impact**: Maintainability

Several screen files are very large and should be broken into smaller, focused components.

**Files to refactor** (by size):
| File | Size | Recommended Action |
|------|------|-------------------|
| `VideoPlayerScreen.kt` | 58KB | Extract controls, overlays, gesture handlers |
| `MainAppViewModel.kt` | 50KB | Extract domain-specific methods to separate classes |
| `TVEpisodeDetailScreen.kt` | 45KB | Extract sections into composables |
| `TVSeasonScreen.kt` | 42KB | Extract episode list, header, actions |
| `HomeScreen.kt` | 41KB | Already has `home/` subfolder - continue extraction |
| `MovieDetailScreen.kt` | 36KB | Extract similar sections, cast list, actions |
| `TVShowsScreen.kt` | 35KB | Extract grid, filters, loading states |

**Steps for `VideoPlayerScreen.kt`**:
1. Create `ui/player/components/` directory
2. Extract `PlayerControls.kt` - play/pause, seek bar, time display
3. Extract `PlayerOverlay.kt` - title, subtitle info overlay
4. Extract `PlayerGestureHandler.kt` - brightness, volume, seek gestures
5. Extract `SubtitleSelector.kt` - subtitle track selection UI
6. Extract `AudioTrackSelector.kt` - audio track selection UI
7. Keep `VideoPlayerScreen.kt` as the coordinator composable

**Steps for `MainAppViewModel.kt`**:
1. Already uses repositories - verify all business logic is delegated
2. Extract pagination logic to `MediaPaginationHelper.kt`
3. Extract search logic delegation is complete
4. Consider using a `CompositeState` pattern for state management
5. Target: < 500 lines for the ViewModel itself

---

### 1.3 Add Missing LazyList Keys

**Priority**: Medium | **Effort**: 1 day | **Impact**: Performance, Compose stability

Ensure all `LazyColumn`, `LazyRow`, and `LazyVerticalGrid` items have stable keys.

**Steps**:
1. Search for `items(` and `itemsIndexed(` calls across the codebase:
   ```bash
   grep -r "items(" app/src/main --include="*.kt" | grep -v "key ="
   ```
2. For each occurrence, ensure a `key` parameter is provided:
   ```kotlin
   // Before
   items(movies) { movie ->
       MovieCard(movie)
   }
   
   // After
   items(
       items = movies,
       key = { it.id.toString() }
   ) { movie ->
       MovieCard(movie)
   }
   ```
3. For items without stable IDs, use a combination of properties:
   ```kotlin
   key = { "${it.name}-${it.type}-${it.sortName}" }
   ```

**Key files to check**:
- `HomeScreen.kt`
- `LibraryScreen.kt`
- `MoviesScreen.kt`
- `TVShowsScreen.kt`
- `MusicScreen.kt`
- `SearchScreen.kt`
- All grid/list components in `ui/components/`

---

### 1.4 Material 3 Component Decisions

**Priority**: Medium | **Effort**: 2 days | **Impact**: UI consistency

The official Material 3 Carousel and Pull-to-Refresh dependencies are disabled. Make a clear decision.

**Option A: Enable Official Components**

1. In `gradle/libs.versions.toml`, uncomment:
   ```toml
   androidx-material3-carousel = { group = "androidx.compose.material3", name = "material3-carousel", version.ref = "material3ExpressiveComponents" }
   ```
2. In `app/build.gradle.kts`, add:
   ```kotlin
   implementation(libs.androidx.material3.carousel)
   ```
3. Test thoroughly on different devices
4. Migrate custom carousel to official implementation if it works well

**Option B: Continue with Custom Implementation**

1. Document the decision in `ARCHITECTURE.md` or `README.md`
2. Add comments in `ExpressiveCarousel.kt` explaining why custom was chosen
3. Ensure custom implementation follows Material 3 design guidelines
4. Add visual tests to verify carousel appearance

**Recommendation**: Test the official carousel component first. If it meets your needs and is stable, prefer the official implementation for future compatibility.

---

## Phase 2: Feature Completion

### 2.1 Complete Offline Downloads

**Priority**: High | **Effort**: 5-7 days | **Impact**: Major user feature

The `OfflineScreen.kt` exists but core download functionality is incomplete.

**Current State**:
- UI exists in `OfflineScreen.kt` (14KB)
- Download management code in `data/offline/` directory (27KB)

**Steps**:
1. **Implement DownloadManager**:
   ```kotlin
   // In data/offline/OfflineDownloadManager.kt
   interface DownloadManager {
       fun downloadItem(itemId: UUID, quality: VideoQuality): Flow<DownloadProgress>
       fun cancelDownload(itemId: UUID)
       fun getDownloadedItems(): Flow<List<OfflineMediaItem>>
       fun deleteDownloadedItem(itemId: UUID)
       fun getStorageUsage(): Flow<StorageInfo>
   }
   ```

2. **Add WorkManager for background downloads**:
   - Create `DownloadWorker.kt` using WorkManager
   - Support pause/resume
   - Handle network constraints (WiFi-only option)

3. **Implement offline playback**:
   - Create `OfflineMediaSourceFactory.kt`
   - Modify `VideoPlayerViewModel` to detect offline items
   - Route to local file instead of stream URL

4. **Add download UI elements**:
   - Download button on movie/episode detail screens
   - Download progress indicator
   - Storage usage indicator in settings

5. **Sync playback state**:
   - Queue progress updates when offline
   - Sync when network becomes available

**Testing checklist**:
- [ ] Download a movie successfully
- [ ] Download an episode successfully
- [ ] Cancel a download in progress
- [ ] Play downloaded content offline
- [ ] Delete downloaded content
- [ ] Verify storage usage is accurate

---

### 2.2 Complete Music Playback

**Priority**: Medium | **Effort**: 5-7 days | **Impact**: Feature completeness

Music library browsing exists but background playback and controls are incomplete.

**Current State**:
- `MusicScreen.kt` (32KB) - displays music library
- `NowPlayingScreen.kt` (16KB) - playback UI exists
- `AudioService.kt` (4KB) - basic service structure
- `AudioServiceConnection.kt` (9.5KB) - service binding

**Steps**:
1. **Complete MediaSession integration**:
   ```kotlin
   // Enhance AudioService.kt
   class AudioService : MediaSessionService() {
       private lateinit var mediaSession: MediaSession
       private lateinit var player: ExoPlayer
       
       override fun onCreate() {
           super.onCreate()
           player = ExoPlayer.Builder(this).build()
           mediaSession = MediaSession.Builder(this, player)
               .setCallback(MediaSessionCallback())
               .build()
       }
   }
   ```

2. **Add notification controls**:
   - Use `MediaStyleNotificationHelper`
   - Show album art, track info
   - Play/pause, skip, previous buttons

3. **Implement queue management**:
   - Create `MusicQueueManager.kt`
   - Support shuffle, repeat modes
   - Add "add to queue" action

4. **Lock screen controls**:
   - MediaSession handles this automatically
   - Ensure media buttons work

5. **Add Now Playing mini-player**:
   - Already have `MiniPlayer.kt` component
   - Integrate into main navigation scaffold

**Testing checklist**:
- [ ] Play music in background
- [ ] Controls work from notification
- [ ] Controls work from lock screen
- [ ] Queue management works
- [ ] Shuffle/repeat work correctly
- [ ] Mini-player appears and functions

---

### 2.3 Verify and Polish Chromecast

**Priority**: Medium | **Effort**: 3-5 days | **Impact**: Cast users

The MediaRouteButton crash was fixed. Now verify full casting functionality.

**Current State**:
- `CastManager.kt` (26KB) - cast management
- `MediaRouteButton.kt` (2.5KB) - fixed crash issue
- Media3 Cast dependency included

**Steps**:
1. **Test cast discovery**:
   - Verify Chromecast devices appear
   - Test discovery on different networks
   - Test with Google Home devices

2. **Test video casting**:
   - Cast a movie
   - Cast a TV episode
   - Verify playback starts correctly
   - Test different video formats (H.264, HEVC)

3. **Test remote control during cast**:
   - Play/pause from phone
   - Seek from phone
   - Volume control

4. **Test cast session management**:
   - Reconnect after network interruption
   - Handle cast device disconnect gracefully
   - Transfer playback back to phone

5. **Test subtitle/audio during cast**:
   - Enable subtitles while casting
   - Change audio track while casting

**Document findings** in `KNOWN_ISSUES.md` or create a `CAST_SUPPORT.md`

---

### 2.4 Picture-in-Picture Verification

**Priority**: Medium | **Effort**: 2 days | **Impact**: Video UX

PiP mode is implemented but needs thorough testing.

**Steps**:
1. **Test on various devices**:
   - Pixel phones (multiple generations)
   - Samsung Galaxy devices
   - OnePlus devices
   - Tablets

2. **Test on different Android versions**:
   - Android 8.0 (Oreo) - PiP minimum
   - Android 12+ (new PiP behavior)
   - Android 14+

3. **Test scenarios**:
   - Enter PiP via home button during playback
   - Enter PiP via PiP button if available
   - Controls in PiP window
   - Expand from PiP back to fullscreen
   - PiP with different aspect ratios

4. **Document any device-specific issues** in `KNOWN_ISSUES.md`

---

## Phase 3: Android TV Polish

### 3.1 D-pad Navigation Audit

**Priority**: High for TV users | **Effort**: 3-5 days | **Impact**: TV usability

**Current State**:
- TV-specific screens exist (`ui/screens/tv/` - 69KB)
- TV components exist (`ui/components/tv/` - 23KB)
- `TvJellyfinApp()` entry point

**Steps**:
1. **Set up testing environment**:
   - Use Android TV emulator or physical device
   - Disable touch input, test only with D-pad
   - Test with multiple remote types

2. **Audit each screen for focus**:
   | Screen | File | Check |
   |--------|------|-------|
   | Home | `TvHomeScreen.kt` | Focus moves logically |
   | Library | `TvLibraryScreen.kt` | Grid navigation works |
   | Detail | `TvItemDetailScreen.kt` | All buttons reachable |
   | Player | `TvVideoPlayerScreen.kt` | Seek controls work |

3. **Check focus indicators**:
   - Ensure focus is clearly visible
   - Check contrast on all backgrounds
   - Verify focus ring/highlight appearance

4. **Fix navigation dead-ends**:
   - Ensure you can always navigate back
   - Check that focus wraps appropriately
   - Verify initial focus placement

5. **Test with voice search** (if supported):
   - Google Assistant integration
   - Voice search results navigation

**Code patterns to use**:
```kotlin
// Ensure focusable items
Modifier
    .focusable()
    .onKeyEvent { event ->
        when (event.key) {
            Key.DirectionCenter -> {
                // Handle select
                true
            }
            else -> false
        }
    }
```

---

### 3.2 TV Leanback Integration

**Priority**: Medium | **Effort**: 5 days | **Impact**: TV launcher integration

**Steps**:
1. **Add home screen channel**:
   - Create `TvChannelManager.kt`
   - Add "Continue Watching" channel
   - Add "Recently Added" channel

2. **Support content recommendations**:
   - Use `PreviewChannelHelper`
   - Show recommendations on TV home

3. **Add search provider**:
   - Implement `ContentProvider` for global TV search
   - Return searchable media items

4. **Test with Google TV interface**

---

## Phase 4: Performance Optimization

### 4.1 Image Loading Optimization

**Priority**: High | **Effort**: 2 days | **Impact**: UI smoothness

**Current State**: Using Coil 3.3.0

**Steps**:
1. **Verify caching is configured**:
   ```kotlin
   // In your Application class or DI module
   ImageLoader.Builder(context)
       .memoryCache {
           MemoryCache.Builder()
               .maxSizePercent(context, 0.25)
               .build()
       }
       .diskCache {
           DiskCache.Builder()
               .directory(context.cacheDir.resolve("image_cache"))
               .maxSizePercent(0.02)
               .build()
       }
       .build()
   ```

2. **Add placeholder images**:
   - Create placeholder drawables for movies, shows, music
   - Use `placeholder()` parameter in `AsyncImage`

3. **Implement image size optimization**:
   ```kotlin
   AsyncImage(
       model = ImageRequest.Builder(LocalContext.current)
           .data(imageUrl)
           .size(Size.ORIGINAL)  // Or specific size
           .crossfade(true)
           .build(),
       contentDescription = null
   )
   ```

4. **Add error images**:
   - Create error state drawables
   - Use `error()` parameter

---

### 4.2 List Scrolling Performance

**Priority**: High | **Effort**: 2-3 days | **Impact**: UX smoothness

**Steps**:
1. **Profile scroll performance**:
   - Use Android Studio Profiler
   - Check for dropped frames
   - Look for recomposition issues

2. **Optimize LazyColumn/Grid items**:
   ```kotlin
   // Use remember for expensive calculations
   val formattedDuration = remember(item.runTimeTicks) {
       formatDuration(item.runTimeTicks)
   }
   
   // Use derivedStateOf for derived values
   val isPlayed by remember {
       derivedStateOf { item.userData?.played == true }
   }
   ```

3. **Add pagination**:
   - Implement proper paging with Paging 3
   - Already have `LibraryItemPagingSource.kt` - ensure it's used

4. **Use `contentType` in lazy lists**:
   ```kotlin
   items(
       items = mixedContent,
       key = { it.id },
       contentType = { it.type }  // Helps Compose reuse layouts
   ) { item ->
       when (item) {
           is Movie -> MovieCard(item)
           is TVShow -> TVShowCard(item)
       }
   }
   ```

---

### 4.3 App Startup Optimization

**Priority**: Medium | **Effort**: 2 days | **Impact**: First impression

**Steps**:
1. **Profile cold start**:
   ```bash
   adb shell am start-activity -W com.rpeters.jellyfin/.MainActivity
   ```

2. **Defer non-critical initialization**:
   - Move heavy init out of `onCreate`
   - Use lazy initialization where possible

3. **Consider App Startup library**:
   ```kotlin
   // Add to build.gradle.kts
   implementation("androidx.startup:startup-runtime:1.2.0")
   ```

4. **Target**: < 2 seconds cold start

---

## Phase 5: Testing Improvements

### 5.1 Increase Test Coverage

**Priority**: High | **Effort**: Ongoing | **Impact**: Reliability

**Current State**: 48 test files, targeting 70%+ coverage

**Steps**:
1. **Generate coverage report**:
   ```bash
   ./gradlew jacocoTestReport
   ```

2. **Identify low-coverage areas**:
   - Review `build/reports/jacoco/html/index.html`
   - Focus on critical paths first

3. **Priority test targets**:
   | Area | Files | Priority |
   |------|-------|----------|
   | Auth flow | `ServerConnectionViewModel` | Critical |
   | Playback | `VideoPlayerViewModel` | Critical |
   | Navigation | Screen navigation | High |
   | Offline | `OfflineDownloadManager` | High |
   | Search | `SearchViewModel` | Medium |

4. **Add missing ViewModel tests**:
   - `LibraryBrowserViewModel`
   - `UserActionsViewModel`
   - `StreamingViewModel`

---

### 5.2 Add UI Tests

**Priority**: Medium | **Effort**: 3-5 days | **Impact**: Regression prevention

**Steps**:
1. **Set up Compose testing**:
   - Already have `ui-test-junit4` dependency
   - Create test directory: `app/src/androidTest/java/.../ui/`

2. **Add critical flow tests**:
   ```kotlin
   @HiltAndroidTest
   class LoginFlowTest {
       @get:Rule
       val composeTestRule = createAndroidComposeRule<MainActivity>()
       
       @Test
       fun testLoginFlow() {
           composeTestRule.onNodeWithTag("server_url_input")
               .performTextInput("https://demo.jellyfin.org")
           // ... continue test
       }
   }
   ```

3. **Add screenshot tests** (optional):
   - Use Paparazzi or similar
   - Catch visual regressions

---

## Phase 6: Build & CI Improvements

### 6.1 Add Static Analysis

**Priority**: Medium | **Effort**: 1 day | **Impact**: Code quality

**Steps**:
1. **Add detekt**:
   ```kotlin
   // In build.gradle.kts (project level)
   plugins {
       id("io.gitlab.arturbosch.detekt") version "1.23.7"
   }
   ```

2. **Configure detekt**:
   - Create `detekt.yml` with custom rules
   - Exclude generated code
   - Add to CI pipeline

3. **Ktlint enforcement**:
   - Already configured - ensure CI runs it
   - Add pre-commit hook:
     ```bash
     ./gradlew ktlintCheck
     ```

---

### 6.2 Improve CI/CD Pipeline

**Priority**: Medium | **Effort**: 2 days | **Impact**: Development velocity

**Steps**:
1. **Add to GitHub Actions**:
   - Lint check on PR
   - Unit tests on PR
   - Coverage report generation
   - APK build for releases

2. **Add dependency update checking**:
   - Use Renovate or Dependabot
   - Auto-create PRs for updates

3. **Add release automation**:
   - Tag-based releases (already configured)
   - Changelog generation
   - GitHub Releases with APK

---

## Phase 7: Security Enhancements

### 7.1 Security Audit Followup

**Priority**: High | **Effort**: 2-3 days | **Impact**: User data protection

**Current State**: `SECURITY_AUDIT.md` exists (16KB)

**Steps**:
1. **Review existing audit**:
   - Check all items in security docs
   - Verify fixes are implemented

2. **Certificate pinning** (if not done):
   - Review `PinningTrustManager.kt`
   - Test with self-signed certs (should fail)
   - Test with valid certs (should succeed)

3. **Credential storage audit**:
   - Review `SecureCredentialManager.kt`
   - Verify Android Keystore usage
   - Test credential persistence

4. **Network security**:
   - Ensure HTTPS is enforced
   - Review `network_security_config.xml`

---

## Phase 8: Future Features (Backlog)

### 8.1 Live TV & DVR

**Priority**: Low | **Effort**: 2-4 weeks | **Impact**: Feature completeness

- Browse live TV channels
- EPG (Electronic Program Guide)
- DVR recordings management
- Schedule recordings

### 8.2 Sync Play (Watch Together)

**Priority**: Low | **Effort**: 2-3 weeks | **Impact**: Social feature

- Create/join sync sessions
- Synchronized playback
- Chat during playback

### 8.3 Multi-Profile Support

**Priority**: Low | **Effort**: 1-2 weeks | **Impact**: Family use

- Switch profiles in-app
- Profile-specific settings
- Kids mode

### 8.4 Home Screen Widget

**Priority**: Low | **Effort**: 1 week | **Impact**: Convenience

- Continue watching widget
- Quick play widget

---

## Implementation Schedule

### Week 1-2: Foundation
- [ ] Fix experimental API warnings (1.1)
- [ ] Add LazyList keys (1.3)
- [ ] Material 3 component decisions (1.4)
- [ ] Image loading optimization (4.1)

### Week 3-4: Code Quality
- [ ] Start refactoring large files (1.2)
- [ ] Increase test coverage (5.1)
- [ ] Add static analysis (6.1)

### Week 5-6: Features
- [ ] Complete offline downloads (2.1)
- [ ] Verify Chromecast (2.3)
- [ ] Verify PiP (2.4)

### Week 7-8: Platform
- [ ] Android TV D-pad audit (3.1)
- [ ] Complete music playback (2.2)
- [ ] UI tests (5.2)

### Ongoing
- [ ] Continue large file refactoring
- [ ] Test coverage improvement
- [ ] Performance profiling

---

## Progress Tracking

### Completed
- [x] MediaRouteButton crash fix (Dec 30, 2025)
- [x] MainActivity refactoring (reduced to ~140 lines)

### In Progress
- [ ] _Update this section as work begins_

### Blocked
- [ ] _Note any blockers here_

---

## Document Maintenance

**Update this document**:
- When starting a new task: Add to "In Progress"
- When completing a task: Move to "Completed" with date
- Weekly: Review priorities and adjust
- Monthly: Archive completed sections to `IMPROVEMENTS_ARCHIVE.md`

**Next Review**: January 9, 2026

---

## Related Documentation

- [CURRENT_STATUS.md](CURRENT_STATUS.md) - Verified project state
- [KNOWN_ISSUES.md](KNOWN_ISSUES.md) - Active bugs
- [IMPROVEMENTS.md](IMPROVEMENTS.md) - Feature roadmap
- [SECURITY_AUDIT.md](SECURITY_AUDIT.md) - Security review
- [TESTING_GUIDE.md](TESTING_GUIDE.md) - Testing instructions
