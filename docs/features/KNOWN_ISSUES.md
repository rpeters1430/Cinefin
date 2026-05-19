# Jellyfin Android - Known Issues

**Last verified on**: 2026-05-19

This document tracks user-facing bugs, workarounds, and fix status. For technical debt and code quality improvements, see [docs/plans/IMPROVEMENT_PLAN.md](../plans/IMPROVEMENT_PLAN.md). For feature status, see [CURRENT_STATUS.md](../plans/CURRENT_STATUS.md). For planned features, see [ROADMAP.md](../plans/ROADMAP.md).

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

### #7: Offline Downloads Reliability Edge Cases

**Impact**: Core offline downloads work, but long-running/background edge cases can still fail
**Affected Users**: Subset of users on constrained networks or aggressive OEM background policies
**Files**:
- `app/src/main/java/com/rpeters/jellyfin/data/offline/OfflineDownloadManager.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/downloads/DownloadsScreen.kt`

**Details**:
- Offline downloads are implemented and generally functional
- Remaining risk is reliability under prolonged background constraints and intermittent connectivity
- Validation focus is WorkManager constraints, retry behavior, and resume handling after process death

**Workaround**:
- Keep app unrestricted by battery optimization when downloading large libraries
- Prefer stable Wi-Fi for large jobs

**Fix Status**: 🔜 Planned
**Canonical Plan**: [IMPROVEMENT_PLAN §2.2](../plans/IMPROVEMENT_PLAN.md#22-offline-downloads-reliability-on-aggressive-oems-known-issue-7)
**Target**: Reliability hardening
**Effort**: 2-4 days of validation + fixes

**Verification**:
- Feature marked **Complete** in [CURRENT_STATUS truth table](../plans/CURRENT_STATUS.md#feature-truth-table-canonical)
- Roadmap item is explicitly marked complete (section 1.2)

**Recently Fixed** (January 2026):
- ✅ Download hanging bug (infinite DataStore Flow collection) - Fixed
- ✅ Download ID mismatch (placeholder UUID vs real ID) - Fixed
- See [IMPROVEMENT_PLAN](../plans/IMPROVEMENT_PLAN.md) for details

---

### #8: Subtitle Feature Gaps (External + Styling + Sync)

**Impact**: Incomplete subtitle experience for users with external subtitle files and styled subtitle formats
**Affected Users**: Users needing external subtitles, ASS/SSA styling, or timing adjustments
**Files**:
- `app/src/main/java/com/rpeters/jellyfin/ui/player/VideoPlayerViewModel.kt`

**Details**:
- External subtitle support remains incomplete
- ASS/SSA styling may be lost when converted/rendered as simpler formats
- No in-player subtitle sync delay setting for manual correction

**Workaround**:
- Use embedded server subtitles where available
- Prefer content with pre-synced subtitles

**Fix Status**: 🔜 Planned
**Canonical Plan**: [ROADMAP §1.6.1](../plans/ROADMAP.md#161-subtitle-system)
**Target**: Subtitle system improvements
**Effort**: 3-5 days

---

## 🟢 LOW PRIORITY Issues (Minor Issues)

### #9: Large Composables Impact Recomposition Performance

**Impact**: Slower UI updates on lower-end devices for the largest screens
**Affected Users**: All users (more noticeable on LOW-tier devices per ImmersivePerformanceConfig)

**Status update (May 2026)**: The original offenders — HomeScreen (was 1,119) and
VideoPlayerScreen (was 1,726) — have been refactored (now 528 and 368 lines).
The new size leaders are:

| File | Lines |
|---|---:|
| `ui/viewmodel/MainAppViewModel.kt` | 1,675 |
| `data/repository/JellyfinRepository.kt` | 1,427 |
| `ui/screens/ImmersiveTVEpisodeDetailScreen.kt` | 1,235 |
| `ui/screens/ImmersiveTVSeasonScreen.kt` | 1,069 |
| `ui/screens/ImmersiveTVShowDetailScreen.kt` | 1,059 |
| `ui/screens/ImmersiveMovieDetailScreen.kt` | 938 |

**Workaround**:
- None for users
- Developers: Refactor screens into smaller composables

**Fix Status**: 🔜 Planned
**Canonical Plan**: [IMPROVEMENT_PLAN §3](../plans/IMPROVEMENT_PLAN.md#tier-3--architecture--technical-debt)
**Effort**: 3–4 days per major file; can be staged

---

### #11: Build Warnings (~150 Warnings)

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
- Developers: See canonical plan below

**Fix Status**: 🔜 Planned
**Canonical Plan**: [IMPROVEMENT_PLAN §3.8](../plans/IMPROVEMENT_PLAN.md#38-build-warnings-150-phase-f--known-issue-11)
**Target**: Reduce warning budget 25% per sprint (machinery already in place)
**Effort**: 2-3 hours

---

### #12: Android TV D-Pad Navigation Not Fully Tested

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

**Fix Status**: 🔜 Planned
**Canonical Plan**: [IMPROVEMENT_PLAN §1.4](../plans/IMPROVEMENT_PLAN.md#14-android-tv-d-pad-navigation-audit-roadmap-21)
**Target**: Phase 2 - Android TV Polish
**Effort**: 3-5 days

---

## ✅ Recently Resolved Issues

### Authentication Interceptor Blocking (✅ Fixed Mar 2026)
**Status**: Refactored to single-flight Mutex + Deferred pattern with coroutine `delay()`
**Files**:
- `app/src/main/java/com/rpeters/jellyfin/network/JellyfinAuthInterceptor.kt`
- `app/src/main/java/com/rpeters/jellyfin/data/repository/JellyfinAuthRefreshManager.kt` (new)
**Details**:
- Token refresh now goes through `JellyfinAuthRefreshManager` which coalesces concurrent refresh requests via `Mutex` + cached `Deferred`
- Backoff uses coroutine `delay()` instead of `Thread.sleep()` — no longer blocks OkHttp threads
- 10-second timeout via `withTimeoutOrNull()` prevents indefinite hangs
- One `runBlocking` remains at the OkHttp `Authenticator` interop boundary (unavoidable because the SAM interface is synchronous); it's bounded and only fires on 401 responses

### Music Background Playback (✅ Fixed Apr 2026)
**Status**: Full MediaSession integration shipped; foreground service hardened for Android 17
**Files**:
- `app/src/main/java/com/rpeters/jellyfin/ui/player/audio/AudioService.kt` (442 lines)
- `app/src/main/java/com/rpeters/jellyfin/ui/player/audio/AudioServiceConnection.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/player/audio/AudioNotificationProvider.kt`
**Details**:
- `AudioService` extends `MediaSessionService` with full callback handling
- `DefaultMediaNotificationProvider` provides system notification + lock screen controls
- Media buttons (play/pause/next/prev/seek/stop) all wired through `onMediaButtonEvent`
- Shuffle and repeat exposed via `AudioServiceConnection.toggleShuffle()` / `toggleRepeatMode()`
- Session state persistence via `AudioSessionStateStore` survives process death
- `onTaskRemoved()` cleanup for Android 17 background-constraint compliance
- Manifest declares `android:foregroundServiceType="mediaPlayback"`

**Remaining**: OEM-stress validation on Samsung One UI, MIUI, ColorOS — tracked in [IMPROVEMENT_PLAN §2.1](../plans/IMPROVEMENT_PLAN.md#21-music-background-playback-validation)

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

For a summary of active issues and overall project status, please refer to the **[CURRENT_STATUS.md](../plans/CURRENT_STATUS.md)** document.

---

## Reporting New Issues

### Before Reporting
1. **Check this document** to see if the issue is already known
2. **Check [ROADMAP.md](../plans/ROADMAP.md)** to see if the feature is in progress
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
1. **Resume "Ask" dialog** - Backend ready; just write the dialog UI (~0.5 day)
2. **Subtitle gaps** (#8) - External subtitles, ASS/SSA styling, sync delay

### Good First Issues
- **Build warnings** (#11) - Low risk, good for beginners
- **Android TV testing** (#12) - Manual testing, no code changes required initially

---

## Related Documentation

- **[CURRENT_STATUS.md](../plans/CURRENT_STATUS.md)** - What works now, feature status matrix
- **[ROADMAP.md](../plans/ROADMAP.md)** - Future features and development roadmap
- **[UPGRADE_PATH.md](UPGRADE_PATH.md)** - Dependency upgrade strategy
- **[docs/plans/IMPROVEMENT_PLAN.md](../plans/IMPROVEMENT_PLAN.md)** - Technical debt and code quality focus
- **[CONTRIBUTING.md](CONTRIBUTING.md)** - How to contribute fixes
- **[CLAUDE.md](CLAUDE.md)** - Development guidelines and architecture

---

## Notes

- **User vs Developer Issues**: This document focuses on user-facing bugs. For technical debt and code quality issues, see [docs/plans/IMPROVEMENT_PLAN.md](../plans/IMPROVEMENT_PLAN.md).
- **Priority Definitions**:
  - 🔴 **Critical**: App crashes, data loss, security issues
  - 🟠 **High**: Major functionality broken, affects all users, potential data corruption
  - 🟡 **Medium**: Feature incomplete or degraded, affects some users
  - 🟢 **Low**: Minor annoyances, developer experience, performance on edge cases

**Last Review**: May 19, 2026
**Next Review**: After IMPROVEMENT_PLAN Tier 1 items ship
