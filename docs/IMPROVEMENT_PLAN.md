# Jellyfin Android Improvement Plan

Date: 2026-01-14
Scope: app/src/main/java, key logs in Pixel-9-Pro-XL-Android-16_2026-01-14_200046.logcat, existing docs/archive plan

This plan focuses on concrete issues found during a code read-through, plus targeted performance and UX work.

## Priority 0 (Blocking/Functional Bugs)

1) Offline downloads can hang indefinitely when URLs are stored encrypted.
- Evidence: getDecryptedUrl collects an infinite DataStore Flow and never returns.
- File: app/src/main/java/com/rpeters/jellyfin/data/offline/OfflineDownloadManager.kt:198
- Plan:
  - Replace collect with first() or firstOrNull() to read a single value.
  - Add a small timeout and fail fast with a user-visible error.

2) Offline download IDs returned to callers are not the IDs stored in state.
- Evidence: startDownload returns a placeholder UUID because the real ID is assigned asynchronously.
- File: app/src/main/java/com/rpeters/jellyfin/data/offline/OfflineDownloadManager.kt:72
- Plan:
  - Make startDownload suspend and return the actual ID.
  - Alternatively return a Flow/Deferred and update callers to await the ID.
  - Add unit tests verifying the ID used for progress and delete matches the returned ID.

## High Priority (Crash/Corruption Risk)

3) Cache directory initialization is race-prone and can throw if used before init completes.
- Evidence: cacheItems/getCachedItems use cacheDir directly; init happens asynchronously via GlobalScope.
- Files: app/src/main/java/com/rpeters/jellyfin/data/cache/JellyfinCache.kt:63, :100, :144
- Plan:
  - Call ensureCacheDir() before disk access in cacheItems/getCachedItems.
  - Consider initializing cacheDir synchronously or via injected ApplicationScope.

4) Memory cache writes are not consistently synchronized.
- Evidence: disk-cache hit path updates memoryCache without synchronization.
- File: app/src/main/java/com/rpeters/jellyfin/data/cache/JellyfinCache.kt:166
- Plan:
  - Wrap memoryCache writes in synchronized blocks or replace with a thread-safe cache.
  - Add a unit test to simulate concurrent cache reads/writes.

5) GlobalScope usage makes cache/image work hard to cancel and reason about.
- Evidence: GlobalScope used for cache and image loader init/clear.
- Files: app/src/main/java/com/rpeters/jellyfin/data/cache/JellyfinCache.kt:75,
  app/src/main/java/com/rpeters/jellyfin/utils/ImageLoadingOptimizer.kt:28
- Plan:
  - Replace GlobalScope with an injected ApplicationScope (Hilt @ApplicationScope).
  - Ensure shutdown/cancellation on app process death is predictable.

## Medium Priority (Performance/Behavior)

6) Video player quality "Auto" option is a no-op.
- Evidence: TODO in quality menu onClick handler.
- File: app/src/main/java/com/rpeters/jellyfin/ui/player/VideoPlayerScreen.kt:1164
- Plan:
  - Implement adaptive quality selection (track bandwidth + player state).
  - Add a UI state flag indicating "Auto" is active and responsive to network changes.

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

## Low Priority (Product/UX/Quality)

9) Offline downloads lack user control for network type and storage.
- Evidence: MediaDownloadManager forces WiFi; OfflineDownloadManager uses app files only.
- File: app/src/main/java/com/rpeters/jellyfin/ui/utils/DownloadManager.kt:55
- Plan:
  - Add settings for WiFi-only vs cellular, and download location selection.
  - Surface remaining storage in UI and warn before large downloads.

10) Testing gaps around cache, offline downloads, and auth refresh.
- Evidence: No targeted tests for JellyfinCache or OfflineDownloadManager flows.
- Plan:
  - Add JVM tests for JellyfinCache (race safety, TTL, disk read/write).
  - Add tests for OfflineDownloadManager ID handling and encrypted URL retrieval.
  - Add a mock WebServer test for auth refresh behavior in JellyfinAuthInterceptor.

## Suggested Implementation Order

1) Fix OfflineDownloadManager blocking + ID mismatch (critical).
2) Stabilize JellyfinCache initialization/thread-safety.
3) Replace GlobalScope with ApplicationScope (cache + image).
4) Implement Auto quality selection for video player.
5) Refactor VideoPlayerScreen/MediaCards for composition stability.
6) Improve auth retry behavior and add tests.

## Progress Update (2026-01-14)

Completed
- Offline downloads: `startDownload` is now suspend/returns real IDs, and encrypted URL retrieval uses `first()` with timeout to prevent hanging.
  - Files: app/src/main/java/com/rpeters/jellyfin/data/offline/OfflineDownloadManager.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/downloads/DownloadsViewModel.kt
- Refactor: Split Movies/TV Shows/Stuff screens into smaller content/top-bar files for readability and composition stability.
  - Files: app/src/main/java/com/rpeters/jellyfin/ui/screens/MoviesContent.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/screens/MoviesTopBar.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/screens/TVShowsContent.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/screens/TVShowsTopBar.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/screens/StuffContent.kt
- Warning cleanup sweep in refactor and related areas (safe-call removal, redundant casts, deprecated Cast seek).
  - Notable files: app/src/main/java/com/rpeters/jellyfin/ui/screens/HomeScreen.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/screens/HomeVideosScreen.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/screens/home/HomeCarousel.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/screens/home/LibraryGridSection.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/player/CastManager.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/viewmodel/MainAppViewModel.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/viewmodel/ServerConnectionViewModel.kt,
    app/src/main/java/com/rpeters/jellyfin/ui/viewmodel/common/SharedAppStateManager.kt

Pending
- Cache initialization/thread-safety fixes (JellyfinCache).
- Replace GlobalScope usage with ApplicationScope.
- Auto quality selection in video player.
- Auth retry non-blocking improvements.

## Notes

- The previous archived plan (docs/archive/IMPROVEMENT_PLAN_DEC30_2025.md) contains older issues that appear resolved or refactored.
- Re-run lint and targeted tests after the fixes above.
