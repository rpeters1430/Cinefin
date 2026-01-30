# Jellyfin Android Improvement Plan

**Last Updated**: January 30, 2026
**Scope**: app/src/main/java, key logs in Pixel-9-Pro-XL-Android-16_2026-01-14_200046.logcat, existing docs/archive plan

> **Note**: For user-facing bugs with workarounds, see [KNOWN_ISSUES.md](../KNOWN_ISSUES.md). This document focuses on technical debt and code quality.

This plan focuses on concrete issues found during a code read-through, plus targeted performance and UX work.

---

## Recently Completed ✅

**Completion Date**: January 2026

1) **Offline downloads hanging bug - FIXED**
- Issue: getDecryptedUrl was collecting an infinite DataStore Flow and never returning
- File: app/src/main/java/com/rpeters/jellyfin/data/offline/OfflineDownloadManager.kt:207
- Solution: Replaced collect with first() and added timeout to read single value
- Status: ✅ Verified fixed

2) **Offline download ID mismatch - FIXED**
- Issue: startDownload returned placeholder UUID instead of actual download ID stored in state
- File: app/src/main/java/com/rpeters/jellyfin/data/offline/OfflineDownloadManager.kt:84-94
- Solution: Made startDownload suspend and return actual ID asynchronously
- Status: ✅ Verified fixed with unit tests

3) **Cache directory initialization race condition - FIXED**
- Issue: cacheDir could be accessed before initialization completed
- File: app/src/main/java/com/rpeters/jellyfin/data/cache/JellyfinCache.kt
- Solution: Added ensureCacheDir() and call it before disk access
- Status: ✅ Verified fixed

4) **Memory cache thread safety - FIXED**
- Issue: memory cache writes/reads could race without synchronization
- File: app/src/main/java/com/rpeters/jellyfin/data/cache/JellyfinCache.kt
- Solution: Wrapped memoryCache access in synchronized blocks
- Status: ✅ Verified fixed

5) **GlobalScope replacement in cache init - FIXED**
- Issue: GlobalScope used for cache initialization and cleanup
- File: app/src/main/java/com/rpeters/jellyfin/data/cache/JellyfinCache.kt
- Solution: Injected @ApplicationScope and used it for cache init work
- Status: ✅ Verified fixed

6) **Video player auto quality selection - FIXED**
- Issue: "Auto" did not apply adaptive track selection
- File: app/src/main/java/com/rpeters/jellyfin/ui/player/VideoPlayerViewModel.kt:1026-1068
- Solution: Clear video track overrides to enable ExoPlayer adaptive selection
- Status: ✅ Verified fixed

---

## High Priority (Crash/Corruption Risk)

**Status**: None currently identified (previous high-priority items completed in Jan 2026).

## Medium Priority (Performance/Behavior)

7) Large composables are impacting runtime compilation and likely recomposition costs.
- Evidence: logcat shows high compile allocations for VideoPlayerScreen and MediaCards.
- File: Pixel-9-Pro-XL-Android-16_2026-01-14_200046.logcat:100343, 115343, 115628
- Plan:
  - Break VideoPlayerScreen and MediaCards into smaller composables with stable parameters.
  - Add @Stable data holders or remember blocks for derived values.
  - Consider moving heavy UI to dedicated files to reduce compilation and recomposition scope.

8) Blocking backoff in auth interceptor can stall OkHttp threads.
- Evidence: Thread.sleep in backoff; runBlocking token refresh in intercept/Authenticator.
- File: app/src/main/java/com/rpeters/jellyfin/network/JellyfinAuthInterceptor.kt:159
- Plan:
  - Replace blocking sleep with a non-blocking retry strategy (OkHttp Dispatcher or custom Call.Factory).
  - Pre-refresh tokens in a background flow to avoid blocking interceptors.

9) Memory cache read in JellyfinCache.isCached is unsynchronized.
- Evidence: isCached reads/removes memoryCache without synchronized blocks.
- File: app/src/main/java/com/rpeters/jellyfin/data/cache/JellyfinCache.kt:207-214
- Plan:
  - Wrap memoryCache access in synchronized blocks to match other cache operations.
  - Add a JVM test to cover concurrent cache access.

## Low Priority (Product/UX/Quality)

10) Offline downloads lack user control for network type and storage.
- Evidence: MediaDownloadManager forces WiFi; OfflineDownloadManager uses app files only.
- File: app/src/main/java/com/rpeters/jellyfin/ui/utils/DownloadManager.kt:55
- Plan:
  - Add settings for WiFi-only vs cellular, and download location selection.
  - Surface remaining storage in UI and warn before large downloads.

11) Testing gaps around cache, offline downloads, and auth refresh.
- Evidence: No targeted tests for JellyfinCache or OfflineDownloadManager flows.
- Plan:
  - Add JVM tests for JellyfinCache (race safety, TTL, disk read/write).
  - Add tests for OfflineDownloadManager ID handling and encrypted URL retrieval.
  - Add a mock WebServer test for auth refresh behavior in JellyfinAuthInterceptor.

## Suggested Implementation Priority Order

1) ✅ ~~Fix OfflineDownloadManager blocking + ID mismatch~~ - **COMPLETED** (Jan 2026)
2) ✅ ~~Stabilize JellyfinCache initialization/thread-safety~~ - **COMPLETED** (Jan 2026)
3) ✅ ~~Replace GlobalScope with ApplicationScope~~ - **COMPLETED** (Jan 2026)
4) ✅ ~~Implement auto quality selection for video player~~ - **COMPLETED** (Jan 2026)
5) Refactor VideoPlayerScreen/MediaCards for composition stability (Priority 7)
6) Improve auth retry behavior and add tests (Priority 8)
7) Synchronize JellyfinCache.isCached memory access (Priority 9)
8) Add offline download network/storage controls (Priority 10)
9) Close testing gaps for cache/offline/auth (Priority 11)

## Progress Update (2026-01-30)

### Completed (January 2026)
- ✅ **Offline downloads**: `startDownload` is now suspend/returns real IDs, and encrypted URL retrieval uses `first()` with timeout to prevent hanging.
  - Files: app/src/main/java/com/rpeters/jellyfin/data/offline/OfflineDownloadManager.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/downloads/DownloadsViewModel.kt
- ✅ **Cache initialization/thread-safety**: `ensureCacheDir()` guards disk access and memory cache uses synchronized blocks.
  - Files: app/src/main/java/com/rpeters/jellyfin/data/cache/JellyfinCache.kt
- ✅ **ApplicationScope usage**: Cache init now runs under injected @ApplicationScope.
  - Files: app/src/main/java/com/rpeters/jellyfin/data/cache/JellyfinCache.kt
- ✅ **Auto quality selection**: "Auto" clears track overrides to enable adaptive track selection.
  - Files: app/src/main/java/com/rpeters/jellyfin/ui/player/VideoPlayerViewModel.kt
- ✅ **Refactor**: Split Movies/TV Shows/Stuff screens into smaller content/top-bar files for readability and composition stability.
  - Files: app/src/main/java/com/rpeters/jellyfin/ui/screens/MoviesContent.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/screens/MoviesTopBar.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/screens/TVShowsContent.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/screens/TVShowsTopBar.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/screens/StuffContent.kt
- ✅ **Warning cleanup**: Cleanup sweep in refactor and related areas (safe-call removal, redundant casts, deprecated Cast seek).
  - Notable files: app/src/main/java/com/rpeters/jellyfin/ui/screens/HomeScreen.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/screens/HomeVideosScreen.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/screens/home/HomeCarousel.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/screens/home/LibraryGridSection.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/player/CastManager.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/viewmodel/MainAppViewModel.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/viewmodel/ServerConnectionViewModel.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/viewmodel/common/SharedAppStateManager.kt

### Pending (Next Phase)
- 🔜 Large composable refactoring (VideoPlayerScreen, HomeScreen) - Priority 7
- 🔜 Auth retry non-blocking improvements - Priority 8
- 🔜 Synchronize JellyfinCache.isCached memory access - Priority 9
- 🔜 Offline download network/storage controls - Priority 10
- 🔜 Testing gaps for cache/offline/auth - Priority 11

## Notes

- The previous archived plan (docs/archive/IMPROVEMENT_PLAN_DEC30_2025.md) contains older issues that appear resolved or refactored.
- Re-run lint and targeted tests after the fixes above.
- This document tracks technical debt and code quality improvements. For user-facing bugs, see [KNOWN_ISSUES.md](../KNOWN_ISSUES.md).

---

## Related Documentation

- **[KNOWN_ISSUES.md](../KNOWN_ISSUES.md)** - User-facing bugs with workarounds and fix status
- **[ROADMAP.md](../ROADMAP.md)** - Future features and development roadmap
- **[UPGRADE_PATH.md](../UPGRADE_PATH.md)** - Dependency upgrade strategy
- **[CURRENT_STATUS.md](../CURRENT_STATUS.md)** - Current feature status and platform support
- **[CLAUDE.md](../CLAUDE.md)** - Development guidelines and architecture details
- **[TESTING_GUIDE.md](TESTING_GUIDE.md)** - Testing patterns and best practices
