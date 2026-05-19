# Companion Doc Patches

Edits needed in `docs/plans/CURRENT_STATUS.md` and `docs/features/KNOWN_ISSUES.md`
so they stop contradicting the codebase. Apply alongside the new `IMPROVEMENT_PLAN.md`.

---

## `docs/plans/CURRENT_STATUS.md` — required edits

### Edit 1: Move Music Playback from "Partial" to "Complete"
**Current** (line 16):
```
| **Partial** | ... | Music Playback, Android TV |
```
**New**:
```
| **Partial** | ... | Android TV |
```

**Current** (lines 51–52):
```
| **Music Playback** | ⚠️ Partial | Phone, Tablet | UI, basic playback | Background
  playback, notification controls, lock screen controls, queue management | [ROADMAP §1.1] |
```
**New** (move into the ✅ Complete table, around line 41):
```
| **Music Playback (Background)** | ✅ Complete | Phone, Tablet | MediaSession with
  notification + lock-screen controls, shuffle, repeat, queue management, session state
  persistence, onTaskRemoved cleanup. Pending OEM-stress validation (see IMPROVEMENT_PLAN §2.1) |
```

### Edit 2: Remove stale CI warning
**Current** (line 124):
```
- **CI/CD**: ⚠️ Dependency check workflow is active; primary Android CI workflows
  referenced in docs are currently missing from `.github/workflows`
```
**New**:
```
- **CI/CD**: ✅ Android CI (`android-ci.yml`), dependency check, Claude/Gemini
  automation, and release workflows all active
```

### Edit 3: Refresh the architecture stack table
**Current** (lines 100–117): table claims Kotlin 2.3.20, Compose BOM 2026.03.01,
Hilt 2.59.2, Media3 1.10.0, Jellyfin SDK 1.8.6, Paging 3.5.0-alpha01.

**Replacement**: Either correct each row, OR replace the whole table with:
```
### Architecture Stack

Current versions are the source of truth in `gradle/libs.versions.toml`. The
project tracks a deliberate "stable-first" policy with alpha/beta exceptions
documented per-dependency in [UPGRADE_PATH.md](UPGRADE_PATH.md).

Key technology choices:
- UI: Jetpack Compose with Material 3 (Expressive components on alpha track)
- Architecture: MVVM + Repository, Orbit MVI for new ViewModels
- DI: Hilt
- Media: Media3 / ExoPlayer with Jellyfin FFmpeg decoder
- AI: Google Gemini (on-device Nano + cloud fallback)
- Firebase: Crashlytics, Analytics, Remote Config, App Check
```

Rationale: this table will drift again. A one-line pointer to the source of truth
doesn't.

### Edit 4: Refresh "Performance Considerations" note
**Current** (line 150):
```
- **Large Composables**: Some screens are large (HomeScreen: 1,119 lines,
  VideoPlayerScreen: 1,726 lines) - planned refactor in [ROADMAP §3.1]
```
**New**:
```
- **Large Composables**: The original HomeScreen and VideoPlayerScreen refactors are
  complete (now 528 and 368 lines respectively). New refactor targets are tracked in
  [IMPROVEMENT_PLAN §3](IMPROVEMENT_PLAN.md#tier-3--architecture--technical-debt) —
  MainAppViewModel (1,675), JellyfinRepository (1,427), and the immersive detail screens.
```

### Edit 5: Update "Recent Completions" section
**Current** (lines 165–175): January-February 2026 only.

**Add to top** (or fold older entries into an "Earlier 2026" subsection):
```
### Recent Completions (March – May 2026)
- ✅ **Music background playback shipped**: MediaSession, notification provider,
  shuffle/repeat, session persistence, Android 17 task-removed hardening (Apr 2026)
- ✅ **Android 16 modernization**: ProgressStyle notifications, standardized haptic
  curves (seekTick, limitReached) (Apr 2026)
- ✅ **Auth refresh redesigned**: Removed blocking Thread.sleep + runBlocking patterns
  in favor of single-flight Mutex/Deferred with bounded timeout (Mar 2026)
- ✅ **Repository interface refactor**: 12 consumers moved to IJellyfinRepository,
  cast playback split off to ICastPlaybackRepository (Mar 2026)
- ✅ **VideoPlayerScreen extraction**: 1,726 → 368 lines; controls and gestures
  extracted to dedicated files (Mar 2026)
```

### Edit 6: Drop stale "Active Development" entries
**Current** (lines 178–179):
```
- 🔄 Music background playback (in progress)
```
**New**: remove (it's complete; validation tracked in IMPROVEMENT_PLAN §2.1).

---

## `docs/features/KNOWN_ISSUES.md` — required edits

### Edit 1: Close Issue #5 (auth interceptor blocking)
The entry at lines 39–65 should be moved to "Recently Resolved Issues" with this entry:

```markdown
### Authentication Interceptor Blocking (✅ Fixed Mar 2026)
**Status**: Refactored to single-flight Mutex + Deferred pattern with coroutine `delay()`
**Files**:
- `app/src/main/java/com/rpeters/jellyfin/network/JellyfinAuthInterceptor.kt`
- `app/src/main/java/com/rpeters/jellyfin/data/repository/JellyfinAuthRefreshManager.kt` (new)
**Details**:
- Token refresh now goes through `JellyfinAuthRefreshManager` which coalesces concurrent
  refresh requests via `Mutex` + cached `Deferred`
- Backoff uses coroutine `delay()` instead of `Thread.sleep()` — no longer blocks OkHttp threads
- 10-second timeout via `withTimeoutOrNull()` prevents indefinite hangs
- One `runBlocking` remains at the OkHttp `Authenticator` interop boundary (unavoidable
  because the SAM interface is synchronous); it's bounded and only fires on 401 responses
```

### Edit 2: Close Issue #6 (music background playback)
The entry at lines 69–93 should be moved to "Recently Resolved Issues" with this entry:

```markdown
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
**Remaining**: OEM-stress validation on Samsung One UI, MIUI, ColorOS — tracked in
[IMPROVEMENT_PLAN §2.1](../plans/IMPROVEMENT_PLAN.md#21-music-background-playback-validation)
```

### Edit 3: Close Issue #9 (Large Composables — HomeScreen / VideoPlayerScreen)
The entry at lines 155–177 should be REPLACED (not moved — the issue is partially
resolved, new files are now the offenders):

```markdown
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

**Fix Status**: 🔜 Planned
**Canonical Plan**: [IMPROVEMENT_PLAN §3](../plans/IMPROVEMENT_PLAN.md#tier-3--architecture--technical-debt)
**Effort**: 3–4 days per major file; can be staged
```

### Edit 4: Renumber Issue #10 (CI workflow)
Issue #10 has TWO entries (the "Casting Requires Unauthenticated URLs" at line 17 AND
"Build Warnings" at line 181). Rename one of them — the build-warnings one should be #11,
and the existing #11 (TV D-pad) should become #12. (Or whatever your numbering policy
prefers — the duplicate is the actual bug to fix.)

### Edit 5: Update "Last Review" footer
**Current** (line 422):
```
**Last Review**: January 30, 2026
```
**New**:
```
**Last Review**: May 19, 2026
**Next Review**: After IMPROVEMENT_PLAN Tier 1 items ship
```

---

## How to apply this patch

Option A — manually open each file and apply the edits.

Option B — let an agent do it:
```
/fix: Apply the edits in docs/plans/DOC_PATCHES_2026-05-19.md to
CURRENT_STATUS.md and KNOWN_ISSUES.md. Move closed issues to "Recently Resolved",
update version numbers per IMPROVEMENT_PLAN.md § Scope of this audit, and rerun
the truth-table consistency check.
```

The Gemini scheduled-triage workflow can probably do this if you commit the patch file
and tag an issue with `/fix`.
