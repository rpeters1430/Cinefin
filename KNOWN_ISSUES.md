# Jellyfin Android - Known Issues

**Last Updated**: January 30, 2026

This document tracks user-facing bugs, workarounds, and fix status. For technical debt and code quality improvements, see [docs/IMPROVEMENT_PLAN.md](docs/IMPROVEMENT_PLAN.md). For feature status, see [CURRENT_STATUS.md](CURRENT_STATUS.md). For planned features, see [ROADMAP.md](ROADMAP.md).

---

## 🔴 CRITICAL Issues (App-Breaking)

**Status**: None currently identified

---

## 🟠 HIGH PRIORITY Issues (Significant Impact)

### #10: Casting Requires Unauthenticated or Proxy URLs

**Impact**: Cast playback may fail on servers that require tokenized URLs
**Affected Users**: Users casting from secured Jellyfin servers
**Files**:
- `app/src/main/java/com/rpeters/jellyfin/ui/player/CastManager.kt`

**Details**:
- Cast receivers do not support custom authorization headers
- Access tokens are no longer appended to Cast URLs
- Requires either a trusted local proxy or an unauthenticated streaming endpoint for casting

**Workaround**:
- Use a local proxy that injects authorization headers
- Allow unauthenticated access to the specific streaming endpoint (if acceptable)

**Fix Status**: ✅ Documented trade-off (Phase B1)

---

## 🟡 MEDIUM PRIORITY Issues (Functionality Gaps)

### #5: Authentication Interceptor Blocks OkHttp Threads

**Impact**: Slow token refresh, potential network timeouts
**Affected Users**: All users during token refresh (every 24 hours)
**File**: `app/src/main/java/com/rpeters/jellyfin/network/JellyfinAuthInterceptor.kt:159`

**Details**:
- Token refresh uses `runBlocking` in OkHttp interceptor
- Backoff strategy uses `Thread.sleep()` which blocks OkHttp threads
- Can cause network requests to queue up during refresh
- May cause timeout errors if refresh takes too long

**Workaround**:
- None for users - token refresh is automatic
- If experiencing network timeouts, restart app to force new session

**Fix Status**: 🔜 Planned - [IMPROVEMENT_PLAN Priority 8](docs/IMPROVEMENT_PLAN.md)
**Target**: Phase 3 - Code Quality

**Code Location**:
```kotlin
// JellyfinAuthInterceptor.kt:159 - Blocking backoff
private fun backoff(attempt: Int) {
    Thread.sleep(calculateBackoff(attempt)) // Blocks OkHttp thread
}
```

---

### #6: Music Background Playback Incomplete

**Impact**: Music stops when app is backgrounded
**Affected Users**: All users attempting to play music
**Files**:
- `app/src/main/java/com/rpeters/jellyfin/ui/player/audio/AudioService.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/components/MiniPlayer.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/screens/NowPlayingScreen.kt`

**Details**:
- Music UI exists but background playback is not implemented
- No MediaSession integration for notification controls
- No lock screen controls
- Music stops when app is minimized or screen is locked
- Queue management (shuffle, repeat) not connected

**Workaround**:
- Keep app in foreground while listening to music
- Use screen timeout settings to keep app active longer
- Consider using Jellyfin web player or other clients for music

**Fix Status**: 🔜 In Progress - [ROADMAP §1.1](ROADMAP.md#11-music-background-playback)
**Target**: Phase 1 - Complete Core Features
**Effort**: 5-7 days

---

### #7: Offline Downloads Non-Functional

**Impact**: Cannot download media for offline viewing
**Affected Users**: All users attempting offline downloads
**Files**:
- `app/src/main/java/com/rpeters/jellyfin/data/offline/OfflineDownloadManager.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/downloads/DownloadsScreen.kt`

**Details**:
- Downloads UI screens exist but core download logic is incomplete
- No WorkManager integration for background downloads
- No download progress tracking
- No offline playback detection in `VideoPlayerViewModel`
- No storage management UI
- No WiFi-only download option

**Workaround**:
- Use online streaming only
- Ensure stable network connection for uninterrupted playback
- Pre-buffer content by starting playback before watching

**Fix Status**: 🔜 In Progress - [ROADMAP §1.2](ROADMAP.md#12-offline-downloads)
**Target**: Phase 1 - Complete Core Features
**Effort**: 5-7 days

**Recently Fixed** (January 2026):
- ✅ Download hanging bug (infinite DataStore Flow collection) - Fixed
- ✅ Download ID mismatch (placeholder UUID vs real ID) - Fixed
- See [IMPROVEMENT_PLAN](docs/IMPROVEMENT_PLAN.md) for details

---

## 🟢 LOW PRIORITY Issues (Minor Issues)

### #8: Large Composables Impact Recomposition Performance

**Impact**: Slower UI updates, increased memory during recomposition
**Affected Users**: All users (noticeable on lower-end devices)
**Files**:
- `app/src/main/java/com/rpeters/jellyfin/ui/screens/HomeScreen.kt` (1,119 lines)
- `app/src/main/java/com/rpeters/jellyfin/ui/player/VideoPlayerScreen.kt` (1,726 lines)

**Details**:
- Some Composable functions are very large (1,000+ lines)
- Increases compilation time and memory usage during recomposition
- Harder to maintain and test
- Can cause unnecessary recompositions of entire screen
- Impacts developer experience (slow IDE, hard to navigate)

**Workaround**:
- None for users
- Developers: Refactor screens into smaller composables (see [ROADMAP §3.1](ROADMAP.md#31-refactor-large-files))

**Fix Status**: 🔜 Planned - [ROADMAP §3.1](ROADMAP.md#31-refactor-large-files)
**Target**: Phase 3 - Code Quality
**Effort**: 3-5 days

---

### #9: Build Warnings (~150 Warnings)

**Impact**: Developer experience, potential future issues
**Affected Users**: Developers only
**Files**: Various

**Details**:
- ~150 non-critical build warnings across the project
- Deprecated `hiltViewModel` imports
- Unnecessary safe calls (`?.` on non-null types)
- Deprecated `CastPlayer` constructor
- No functional impact on app behavior

**Workaround**:
- Ignore warnings - they are non-critical
- Developers: See [ROADMAP §3.2](ROADMAP.md#32-fix-build-warnings)

**Fix Status**: 🔜 Planned - [ROADMAP §3.2](ROADMAP.md#32-fix-build-warnings)
**Target**: Phase 3 - Code Quality
**Effort**: 2-3 hours

---

### #10: Android TV D-Pad Navigation Not Fully Tested

**Impact**: Potential navigation issues on Android TV
**Affected Users**: Android TV users
**Files**:
- `app/src/main/java/com/rpeters/jellyfin/ui/tv/TvHomeScreen.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/screens/tv/TvLibraryScreen.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/screens/tv/TvItemDetailScreen.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/screens/tv/TvVideoPlayerScreen.kt`

**Details**:
- Android TV UI screens exist but D-pad navigation not fully tested
- Focus indicators may not be visible in all cases
- Possible navigation dead-ends (can't escape certain screens with remote)
- Initial focus placement may not be optimal
- Player controls may not work correctly with D-pad

**Workaround**:
- Use mouse/touchpad with Android TV if available
- Restart app if stuck in navigation dead-end
- Report specific navigation issues on GitHub

**Fix Status**: 🔜 Planned - [ROADMAP §2.1](ROADMAP.md#21-d-pad-navigation-audit)
**Target**: Phase 2 - Android TV Polish
**Effort**: 3-5 days

---

## ✅ Recently Resolved Issues

### Auto-Play Next Episode (✅ Fixed Jan 23, 2026)
**Status**: Implemented with countdown UI and automatic continuation
**Commit**: `8463e8bd` - "feat: implement auto-play next episode feature with countdown and UI updates"

### Auto Quality Selection (✅ Fixed Jan 2026)
**Status**: "Auto" now clears track overrides to enable ExoPlayer adaptive selection when multiple video tracks are available
**File**: `app/src/main/java/com/rpeters/jellyfin/ui/player/VideoPlayerViewModel.kt:1026-1068`
**Details**: Selecting Auto clears video track overrides and keeps `selectedQuality` null to reflect adaptive playback

### Offline Download Hanging (✅ Fixed Jan 2026)
**Status**: Fixed by replacing `collect` with `first()` and adding timeout
**File**: `app/src/main/java/com/rpeters/jellyfin/data/offline/OfflineDownloadManager.kt:207`
**Details**: `getDecryptedUrl()` was collecting infinite DataStore Flow - now uses `first()` to read single value

### Download ID Mismatch (✅ Fixed Jan 2026)
**Status**: Fixed by making `startDownload()` suspend and returning actual ID
**File**: `app/src/main/java/com/rpeters/jellyfin/data/offline/OfflineDownloadManager.kt:84-94`
**Details**: Placeholder UUID was returned to callers - now returns real download ID

### Cache Directory Initialization Race Condition (✅ Fixed Jan 2026)
**Status**: Fixed with `ensureCacheDir()` function and proper initialization
**File**: `app/src/main/java/com/rpeters/jellyfin/data/cache/JellyfinCache.kt`
**Details**: 
- Added `ensureCacheDir()` function that safely initializes cache directory on demand
- `cacheItems()` now calls `ensureCacheDir()` before disk access (line 126)
- `getCachedItems()` now calls `ensureCacheDir()` before disk access (line 168)
- Prevents NullPointerException crash on first app launch or after cache clear

### Memory Cache Thread Safety (✅ Fixed Jan 2026)
**Status**: Fixed with synchronized blocks around memory cache access
**File**: `app/src/main/java/com/rpeters/jellyfin/data/cache/JellyfinCache.kt`
**Details**:
- Memory cache operations now wrapped in `synchronized(memoryCache)` blocks
- Read operations (lines 149-165) protected from concurrent modification
- Write operations (lines 120-123, 174-181) protected from race conditions
- Prevents ConcurrentModificationException and data corruption

### GlobalScope Replacement in JellyfinCache (✅ Fixed Jan 2026)
**Status**: Replaced GlobalScope with ApplicationScope
**File**: `app/src/main/java/com/rpeters/jellyfin/data/cache/JellyfinCache.kt:30, 82`
**Details**:
- Constructor now injects `@ApplicationScope` CoroutineScope via Hilt
- Init block uses applicationScope instead of GlobalScope (line 82)
- Properly bound to application lifecycle, no more memory leaks
- Cache cleanup operations can be properly cancelled

---

## 📘 Common Connection Issues & Troubleshooting

### DNS Resolution Failures

**Symptoms**:
- Error message: "Could not find an IP address for the server hostname"
- Error message: "Could not resolve server hostname"
- Connection fails when using a hostname (e.g., `jellyfin.myserver.com`)

**Root Causes**:
- `android.system.GaiException: EAI_NODATA` - Hostname exists but has no DNS records
- `android.system.GaiException: EAI_NONAME` - Hostname does not exist
- DNS server misconfiguration or connectivity issues
- Typo in server hostname
- Expired or missing DNS records

**Troubleshooting Steps**:

1. **Verify hostname spelling**: Double-check the server address for typos, extra spaces, or incorrect characters.

2. **Try using an IP address directly**:
   - IPv4 example: `http://192.168.1.100:8096`
   - IPv6 example: `http://[fe80::1]:8096`
   - If connecting via IP works, the issue is DNS-related.

3. **Check DNS configuration**:
   - Test DNS resolution on another device (computer, phone)
   - Use `nslookup` or `dig` to verify DNS records exist
   - Verify your device's DNS settings in network configuration

4. **Network-specific solutions**:
   - **Local network**: Use IP address instead of hostname
   - **Remote access**: Ensure DNS records are properly configured with your domain registrar
   - **Reverse proxy**: Verify reverse proxy DNS points to correct server

5. **Alternative connection methods**:
   - Use Jellyfin Quick Connect if DNS is unreliable
   - Configure a static IP on your Jellyfin server
   - Use a Dynamic DNS service (e.g., DuckDNS, No-IP) for remote access

**For Server Administrators**:
- Verify DNS A/AAAA records exist for your hostname
- Check DNS propagation (may take up to 48 hours)
- Ensure internal DNS (if used) has correct entries
- Test external DNS resolution from multiple providers
- Consider using a more reliable DNS provider

---

## Issue Summary

| Priority | Count | Status |
|----------|-------|--------|
| 🔴 Critical | 0 | None identified |
| 🟠 High | 0 | All fixed in Jan 2026 |
| 🟡 Medium | 3 | In progress or planned |
| 🟢 Low | 3 | Planned for Phase 2-3 |
| ✅ Resolved | 8 | Fixed in Jan 2026 |
| **Total Active** | **6** | - |

---

## Reporting New Issues

### Before Reporting
1. **Check this document** to see if the issue is already known
2. **Check [ROADMAP.md](ROADMAP.md)** to see if the feature is in progress
3. **Update to latest version** to ensure issue still exists
4. **Check GitHub issues** for existing reports

### How to Report
1. Go to [GitHub Issues](https://github.com/rpeters1430/JellyfinAndroid/issues)
2. Click "New Issue"
3. Provide the following information:
   - **Clear title** describing the issue
   - **Steps to reproduce** (detailed, step-by-step)
   - **Expected behavior** vs **actual behavior**
   - **Device information**: Model, Android version, app version
   - **Screenshots/videos** if applicable
   - **Logs** if experiencing crashes (use `adb logcat` or Android Studio Logcat)

### Issue Template
```markdown
**Issue Description**:
[Brief description of the issue]

**Steps to Reproduce**:
1. [First step]
2. [Second step]
3. [...]

**Expected Behavior**:
[What should happen]

**Actual Behavior**:
[What actually happens]

**Device Information**:
- Device: [e.g., Pixel 7 Pro]
- Android Version: [e.g., Android 14]
- App Version: [e.g., 0.10]

**Screenshots**:
[Attach screenshots if applicable]

**Logs**:
[Attach relevant logs if available]
```

---

## Contributing Fixes

We welcome contributions to fix these issues! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### High-Impact Fixes Needed
1. **Auth refresh retry/backoff** (#5) - Medium priority, avoid blocking OkHttp threads
2. **Music background playback** (#6) - Already in progress, help welcome

### Good First Issues
- **Build warnings** (#9) - Low risk, good for beginners
- **Android TV testing** (#10) - Manual testing, no code changes required initially

---

## Related Documentation

- **[CURRENT_STATUS.md](CURRENT_STATUS.md)** - What works now, feature status matrix
- **[ROADMAP.md](ROADMAP.md)** - Future features and development roadmap
- **[UPGRADE_PATH.md](UPGRADE_PATH.md)** - Dependency upgrade strategy
- **[docs/IMPROVEMENT_PLAN.md](docs/IMPROVEMENT_PLAN.md)** - Technical debt and code quality focus
- **[CONTRIBUTING.md](CONTRIBUTING.md)** - How to contribute fixes
- **[CLAUDE.md](CLAUDE.md)** - Development guidelines and architecture

---

## Notes

- **User vs Developer Issues**: This document focuses on user-facing bugs. For technical debt and code quality issues, see [docs/IMPROVEMENT_PLAN.md](docs/IMPROVEMENT_PLAN.md).
- **Priority Definitions**:
  - 🔴 **Critical**: App crashes, data loss, security issues
  - 🟠 **High**: Major functionality broken, affects all users, potential data corruption
  - 🟡 **Medium**: Feature incomplete or degraded, affects some users
  - 🟢 **Low**: Minor annoyances, developer experience, performance on edge cases

**Last Review**: January 30, 2026
**Next Review**: TBD (review after each major release)
