# Jellyfin Android Client - Known Issues

**Last Updated**: 2025-12-30

This document tracks active bugs, limitations, and workarounds in the Jellyfin Android Client. Resolved issues are moved to IMPROVEMENTS_ARCHIVE.md.

---

## 🚨 Critical Issues

### ~~1. MediaRouteButton Crash with Chromecast~~ ✅ RESOLVED

**Resolved**: December 30, 2025
**Status**: Fixed
**Severity**: Critical (Application crash)

**Description**:
The app would crash when entering the video player with the Chromecast button visible. The crash occurred because the `MediaRouteButton` View was being created with a context that had a translucent/transparent background color, and the AndroidX MediaRouter library requires an opaque background to calculate contrast ratios.

**Error**:
```
java.lang.IllegalArgumentException: background can not be translucent: #0
    at androidx.core.graphics.ColorUtils.calculateContrast(ColorUtils.java:175)
    at androidx.mediarouter.app.MediaRouterThemeHelper.getControllerColor
```

**Fix Applied**:
- Added `ContextThemeWrapper` with opaque theme to `MediaRouteButton.kt`
- Created `Theme_MediaRouter_Opaque` style in `themes.xml` with proper opaque colors
- Prevents crash while maintaining Material 3 design consistency

**Files Modified**:
- `app/src/main/java/com/rpeters/jellyfin/ui/player/MediaRouteButton.kt`
- `app/src/main/res/values/themes.xml`

**Testing Required**:
- [ ] Test video player on Pixel devices
- [ ] Test video player on Samsung devices
- [ ] Test Chromecast discovery and connection
- [ ] Verify no crashes with dynamic colors enabled/disabled

---

## ⚠️ High Priority Issues

### 1. Material 3 Carousel Dependency Disabled

**Status**: Known limitation
**Severity**: Medium
**Impact**: Custom carousel implementation instead of official Material 3 component

**Description**:
The official Material 3 Carousel dependency is commented out in `gradle/libs.versions.toml`:
```toml
# androidx-material3-carousel = { group = "androidx.compose.material3", name = "material3-carousel", version.ref = "material3ExpressiveComponents" }
```

**Current Behavior**:
- App uses custom carousel implementations (`ExpressiveCarousel.kt`, `PerformanceOptimizedCarousel.kt`)
- Works functionally but may not follow latest Material 3 design specs

**Workaround**:
- Continue using custom implementation
- Monitor Material 3 releases for stable carousel component

**Next Steps**:
- [ ] Decide whether to enable official carousel when stable
- [ ] Document design decision in architecture docs
- [ ] Ensure custom implementation meets Material 3 guidelines

**Related Files**:
- `gradle/libs.versions.toml`
- `app/src/main/java/com/rpeters/jellyfin/ui/components/ExpressiveCarousel.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/components/PerformanceOptimizedCarousel.kt`

---

### 2. Pull-to-Refresh Implementation Unclear

**Status**: Needs verification
**Severity**: Low-Medium
**Impact**: Using experimental APIs

**Description**:
The official Material 3 pull-to-refresh dependency is disabled:
```toml
# androidx-material3-pulltorefresh = { group = "androidx.compose.material3", name = "material3-pulltorefresh", version.ref = "material3ExpressiveComponents" }
```

However, `PullToRefreshBox` is imported from `androidx.compose.material3.pulltorefresh` in screens.

**Current Behavior**:
- Pull-to-refresh appears to work in HomeScreen
- Using APIs from experimental package

**Concerns**:
- Experimental APIs may change or be deprecated
- May need migration when stable version releases

**Next Steps**:
- [ ] Verify which pull-to-refresh implementation is actually in use
- [ ] Test thoroughly on various devices
- [ ] Add opt-in annotation if using experimental API
- [ ] Monitor for stable release

**Related Files**:
- `app/src/main/java/com/rpeters/jellyfin/ui/screens/HomeScreen.kt`
- `gradle/libs.versions.toml`

---

## 🔧 Medium Priority Issues

### 3. Incomplete Feature Implementations

Several features have UI components but incomplete functionality:

#### 3.1 Music Playback Controls

**Status**: Partially implemented
**Severity**: Medium
**Impact**: Music screen exists but playback controls incomplete

**Description**:
- `MusicScreen.kt` displays music library
- Background playback service not implemented
- No lock screen/notification controls

**Next Steps**:
- [ ] Implement MediaSession service
- [ ] Add background playback
- [ ] Add notification controls

**Related Files**:
- `app/src/main/java/com/rpeters/jellyfin/ui/screens/MusicScreen.kt`

---

#### 3.2 Offline Downloads

**Status**: Partially implemented
**Severity**: Medium
**Impact**: Screen exists but download functionality incomplete

**Description**:
- `OfflineScreen.kt` exists with UI
- Core download management incomplete
- No offline playback support

**Next Steps**:
- [ ] Complete download manager implementation
- [ ] Add offline playback support
- [ ] Implement storage management

**Related Files**:
- `app/src/main/java/com/rpeters/jellyfin/ui/screens/OfflineScreen.kt`
- `app/src/main/java/com/rpeters/jellyfin/data/offline/`

---

#### 3.3 Android TV Support

**Status**: Partially implemented
**Severity**: Medium
**Impact**: TV screens exist but D-pad navigation needs testing

**Description**:
- TV-specific screens implemented (`TvHomeScreen.kt`, `TvLibraryScreen.kt`)
- D-pad navigation not comprehensively tested
- Leanback integration incomplete

**Next Steps**:
- [ ] Comprehensive D-pad testing
- [ ] Fix any navigation issues
- [ ] Add TV-specific optimizations

**Related Files**:
- `app/src/main/java/com/rpeters/jellyfin/ui/screens/tv/`

---

### 4. Experimental Coroutines API Usage

**Status**: Build warnings
**Severity**: Low
**Impact**: Using preview/experimental APIs

**Description**:
Build shows warnings about experimental Coroutines APIs:
```
w: This declaration needs opt-in. Its usage should be marked with '@kotlinx.coroutines.ExperimentalCoroutinesApi'
w: This declaration is in a preview state and can be changed with '@kotlinx.coroutines.FlowPreview'
```

**Files Affected**:
- `MainAppViewModel.kt` (lines 227-229)
- `SearchViewModel.kt` (line 70)

**Workaround**:
Add opt-in annotations to suppress warnings:
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
```

**Next Steps**:
- [ ] Add opt-in annotations where experimental APIs are used
- [ ] OR refactor to use stable APIs

**Related Files**:
- `app/src/main/java/com/rpeters/jellyfin/ui/viewmodel/MainAppViewModel.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/viewmodel/SearchViewModel.kt`

---

## 🟢 Low Priority Issues

### 6. MainActivity.kt Size

**Status**: Code organization
**Severity**: Low
**Impact**: Large file, could be refactored for maintainability

**Description**:
- MainActivity.kt is 41KB
- Could be broken into smaller components
- Not affecting functionality, but makes code harder to navigate

**Next Steps**:
- [ ] Extract navigation logic to separate files
- [ ] Extract state management
- [ ] Create smaller, focused components

**Related Files**:
- `app/src/main/java/com/rpeters/jellyfin/MainActivity.kt`

---

### 7. Native Library Stripping Warning

**Status**: Build informational message
**Severity**: Very Low
**Impact**: APK slightly larger, no functional impact

**Description**:
Build shows message:
```
Unable to strip the following libraries, packaging them as they are:
libandroidx.graphics.path.so, libdatastore_shared_counter.so, libffmpegJNI.so
```

**Cause**:
Some native libraries don't support stripping debug symbols.

**Current Behavior**:
- Libraries included as-is in APK
- Slightly larger APK size
- No runtime impact

**Workaround**:
This is expected behavior for these libraries.

**Next Steps**:
- No action required

---

## 🔍 Items Needing Verification

These items require testing to determine if they're issues or working correctly:

### 8. Picture-in-Picture Mode

**Status**: Needs testing
**Priority**: Medium

**Action Required**:
- [ ] Test PiP on various devices (Samsung, Pixel, OnePlus, etc.)
- [ ] Test on different Android versions (8.0-14+)
- [ ] Document any device-specific issues
- [ ] Update status based on findings

---

### 9. Subtitle Support

**Status**: Needs verification
**Priority**: Medium

**Action Required**:
- [ ] Test subtitle loading
- [ ] Test multiple subtitle tracks
- [ ] Test subtitle customization
- [ ] Update status based on findings

---

### 10. Chromecast Integration

**Status**: Partially Working - Critical crash fixed
**Priority**: Medium

**Description**:
- Cast framework dependency is included (v22.2.0)
- MediaRouteButton implementation fixed (Dec 30, 2025)
- Basic integration in place, needs comprehensive testing

**Recent Fix**:
- ✅ Fixed critical crash when Chromecast button was visible (see Critical Issues above)

**Action Required**:
- [ ] Test cast discovery on multiple devices
- [ ] Test video casting with different media formats
- [ ] Test remote control during cast (play/pause/seek)
- [ ] Test reconnection after network interruption
- [ ] Document findings and any remaining issues

---

## 📝 Reporting New Issues

To report a new issue:

1. **Check if it's already listed** in this document
2. **Gather information**:
   - Steps to reproduce
   - Expected behavior
   - Actual behavior
   - Device model and Android version
   - App version
   - Logs (if applicable)

3. **Create GitHub issue** with template:
   ```markdown
   ## Description
   [Clear description of the issue]

   ## Steps to Reproduce
   1. Step 1
   2. Step 2
   3. Step 3

   ## Expected Behavior
   [What should happen]

   ## Actual Behavior
   [What actually happens]

   ## Environment
   - Device: [e.g., Pixel 7]
   - Android Version: [e.g., 14]
   - App Version: [e.g., 1.0.0]
   - Jellyfin Server Version: [e.g., 10.8.0]

   ## Additional Context
   [Screenshots, logs, or other relevant information]
   ```

4. **Add to this document** if confirmed
5. **Link to GitHub issue**

---

## ✅ Issue Resolution Process

When an issue is fixed:

1. **Mark as resolved** in this document
2. **Add resolution date**
3. **Link to PR that fixed it**
4. **Move to IMPROVEMENTS_ARCHIVE.md** if significant
5. **Update CURRENT_STATUS.md** if feature status changes

Example:
```markdown
### ~~Issue Title~~ ✅ RESOLVED

**Resolved**: 2025-12-22
**PR**: #123
**Fix**: Description of what was changed
```

---

## 🔄 Review Schedule

This document should be reviewed:
- **Weekly**: Update status of high priority items
- **Bi-weekly**: Add new issues as discovered
- **Monthly**: Clean up resolved issues

**Next Review**: 2026-01-06

---

## 📚 Related Documentation

- **[CURRENT_STATUS.md](CURRENT_STATUS.md)** - Current verified project state
- **[IMPROVEMENTS.md](IMPROVEMENTS.md)** - Improvement roadmap
- **[CONTRIBUTING.md](CONTRIBUTING.md)** - Contribution guidelines
- **GitHub Issues** - https://github.com/rpeters1430/JellyfinAndroid/issues
