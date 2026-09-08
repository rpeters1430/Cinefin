# Cinefin Android — Improvement Plan

**Last verified against code on**: 2026-05-19
**Replaces**: `IMPROVEMENT_PLAN.md` (last verified 2026-04-22)
**Method**: Direct audit of `Cinefin-main.zip` source tree, not derived from prior plans.

> **Important**: This document supersedes the prior IMPROVEMENT_PLAN. Many "open" items
> from the April plan are already shipped in code and have been moved to **§ Already Done
> (Reclassified)**. Several large files and a brand-new Requests feature were never tracked
> and are now in **§ New Tech Debt**.

---

## Table of Contents

1. [Scope of this audit](#scope-of-this-audit)
2. [Already done (reclassified)](#already-done-reclassified)
3. [Open work — Tier 1: User-visible & shippable](#tier-1--user-visible--shippable)
4. [Open work — Tier 2: Reliability & polish](#tier-2--reliability--polish)
5. [Open work — Tier 3: Architecture & technical debt](#tier-3--architecture--technical-debt)
6. [Open work — Tier 4: Documentation hygiene](#tier-4--documentation-hygiene)
7. [Suggested execution order](#suggested-execution-order)
8. [GitHub issue templates](#github-issue-templates)

---

## Scope of this audit

I read the source tree directly and cross-checked every claim in the prior plan against
actual files. Findings fall into three buckets:

- **Already done** — prior plan lists it as open; code shows it shipped. Reclassified, not
  re-tracked.
- **Still open** — the plan was right; the work remains.
- **New / untracked** — emerged since the last plan and is not in any current document.

Version reference points used:
- `versionCode 111`, `versionName "14.79"` (was `79 / 14.47` in GEMINI.md)
- Kotlin `2.3.21`, AGP `9.2.1`, KSP `2.3.8`
- compileSdk `37`, targetSdk `35`, minSdk `26`
- Compose BOM `2026.05.00`, Material 3 `1.5.0-alpha19`, Media3 `1.10.1`
- Jellyfin SDK `1.8.8`, Hilt `2.59.2`, Orbit MVI `11.0.0`

---

## Already done (reclassified)

These were open in the previous plan or `KNOWN_ISSUES.md`. Code review shows them shipped.
No further work tracked here — they exist only so reviewers know not to file new issues.

### ✅ VideoPlayerScreen.kt refactor (Phase F1 / Known Issue #9)
Prior plan: 1,726 lines. Actual: **368 lines**. Player UI was extracted into
`ExpressiveVideoControls.kt` (844), `TvVideoPlayerControls.kt` (703), and the
`ui/player/components/` directory. The refactor is functionally complete.

### ✅ Auth interceptor non-blocking refresh (Known Issue #5 / ROADMAP §3.0)
The interceptor now delegates to `JellyfinAuthRefreshManager` which uses:
- Single-flight `Mutex` + `Deferred` to coalesce concurrent refreshes
- Coroutine `delay()` for backoff (no `Thread.sleep`)
- `withTimeoutOrNull(10_000L)` bound on the refresh
One `runBlocking` remains inside `refreshAfterUnauthorized()` because OkHttp's
`Authenticator` interface is synchronous — this is unavoidable and bounded. **The issue
as filed is resolved.**

### ✅ Music background playback foundation (ROADMAP §1.1 / Known Issue #6)
`AudioService` is a complete `MediaSessionService` with `DefaultMediaNotificationProvider`,
media-button handling (play/pause/next/prev/seek/stop), `mediaPlayback` foreground service
type in the manifest, `onTaskRemoved` cleanup for Android 17, session state persistence
via `AudioSessionStateStore`, and shuffle/repeat exposed through `AudioServiceConnection`.
`NowPlayingScreen` wires all of it. Music in the background, on the lock screen, and from
notification controls all work in code. See Tier 2 for the remaining validation work.

### ✅ Android 16 ProgressStyle notifications (Phase 2 modernization plan)
`OfflineDownloadWorker.kt:172-194` uses `NotificationCompat.ProgressStyle` gated on
`SDK_INT >= 36`, with indeterminate fallback during the transcoding phase and a legacy
`setProgress()` path for older devices.

### ✅ Android 16 standardized haptic curves (Phase 2 modernization plan)
`ExpressiveHaptics.kt` has both `seekTick()` (uses `PRIMITIVE_LOW_TICK`) and
`limitReached()` (`PRIMITIVE_THUD`) on top of the existing `selectionTick()` and
`errorTick()`. The architecture supports the new curves as planned.

### ✅ Android 17 AudioService hardening (Phase 2 modernization plan)
`onTaskRemoved` cleanup is in place at `AudioService.kt:167`.

### ✅ Android 17 mandatory resizability (Phase 2 modernization plan)
`AndroidManifest.xml:47` already declares `android:resizeableActivity="true"`. The
`VideoPlayerActivity` uses `configChanges="orientation|screenSize|keyboardHidden|screenLayout"`
which is the correct pattern for dynamic orientation handling.

### ✅ JellyfinRepository → IJellyfinRepository boundary (2026-03-30 plan)
12 consumers converted to the interface; cast playback split into
`ICastPlaybackRepository`. The remaining concrete usages in `MainAppViewModel` and
`ServerConnectionViewModel` are intentional (they touch session/connection lifecycle).
**Do not expand `IJellyfinRepository` further** without designing a separate
session/connection abstraction first.

### ✅ Empty states (Phase D1)
`EmptyStateComposable.kt` exists and is used in `FavoritesScreen`, `LibraryScreen`,
`AudioQueueScreen`, `SearchResultsContent`, and the immersive TV detail screens.
`ExpressiveStateComponents.kt` covers the more decorated cases.

### ✅ Android CI workflow (CURRENT_STATUS warning)
The previous `CURRENT_STATUS.md` claimed "primary Android CI workflows are missing."
`.github/workflows/android-ci.yml` exists with build/test/lint stages.

---

## Tier 1 — User-visible & shippable

Highest-leverage items the next user-facing release should pick up.

### 1.1 Resume-playback "Ask" dialog
**Status**: backend ready, UI missing
**Effort**: 0.5 day
**File**: `ui/player/VideoPlayerViewModel.kt` (line 239 — `else` branch silently treats
ASK like ALWAYS), new dialog component in `ui/player/components/`.

The `ResumePlaybackMode.ASK` enum value, preference UI, and DataStore plumbing all
exist, but the `VideoPlayerViewModel.initializePlayback()` `when` block has no ASK case
— it falls through to auto-resume. Users who choose "Ask" silently get "Always".

**Fix**:
1. Add ASK branch in `VideoPlayerViewModel.initializePlayback()` that emits a
   `ResumePromptState` instead of seeking immediately
2. New `ResumePlaybackDialog` composable in `ui/player/components/`
3. Wire dialog → `viewModel.confirmResume(fromBeginning: Boolean)`
4. Phone player + TV player both need the dialog

**Acceptance**: A previously-watched item with `resumePlaybackMode = ASK` shows a
"Resume from XX:XX / Start over" dialog before playback starts on both phone and TV.

---

### 1.2 Subtitle gaps — external, styling, sync delay
**Status**: appearance customization shipped; functional gaps remain
**Effort**: 3–5 days
**Files**: `ui/player/VideoPlayerViewModel.kt`,
`data/preferences/SubtitleAppearancePreferences.kt`,
`ui/screens/settings/SubtitleSettingsScreen.kt`

`SubtitleAppearancePreferences` covers text size, font, and background only. Three
functional gaps remain — confirmed by reading the preferences file (no delay field, no
external-track flag, no style preservation flag):

- **External subtitles**: `OfflineDownloadManager.downloadExternalSubtitles()` exists for
  offline playback, but the streaming player still filters them out. Bring the streaming
  path up to parity.
- **ASS/SSA styling**: currently flattened to VTT-equivalent text. Preserve original
  styling where Media3's text renderer supports it; document the fallback for what it
  doesn't.
- **Sync delay**: add a `subtitleDelayMs: Int = 0` to `SubtitleAppearancePreferences`
  (range ±5,000 ms), expose a slider in the player overlay, and apply via the timed
  text track's offset.

**Acceptance**: An MKV with embedded ASS subtitles plays with styling intact; a separate
`.srt` next to a media item is selectable in the player; the sync slider shifts subtitles
in real time.

---

### 1.3 Auto-skip intro / outro preference (ROADMAP §1.6.2)
**Status**: not started
**Effort**: 2–3 days
**Files**: `data/preferences/PlaybackPreferencesRepository.kt`,
`ui/player/VideoPlayerViewModel.kt`, `ui/screens/settings/PlaybackSettingsScreen.kt`

Jellyfin servers expose chapter markers (`Chapters[]` with `MarkerType: "IntroStart"` /
`"IntroEnd"` / `"CreditsStart"`). Add:
- New preferences: `autoSkipIntro: Boolean`, `autoSkipCredits: Boolean`, plus
  `skipIntroPromptSeconds: Int` (0 = silent skip, >0 = show "Skip intro" button)
- Player listener that watches `currentPosition` against chapter markers and either
  auto-seeks or surfaces a skip-prompt overlay
- Setting UI in `PlaybackSettingsScreen` matching the existing dropdown style

**Acceptance**: Toggling auto-skip-intro produces the expected behavior on content that
has intro markers; absence of markers gracefully no-ops.

---

### 1.4 Android TV D-pad navigation audit (ROADMAP §2.1)
**Status**: TV UI exists, no audit recorded
**Effort**: 3–5 days
**Files**: `ui/tv/TvFocusManager.kt`, `ui/screens/tv/*`, `ui/player/tv/TvVideoPlayerScreen.kt`

`TvFocusManager` (507 lines) and `TvKeyboardHandler` (425 lines) suggest serious focus
infrastructure, but no audit document exists for any of the 10 TV screens.

Per-screen audit checklist:
- [ ] `TvHomeScreen.kt` (364 lines)
- [ ] `TvLibraryScreen.kt` (472)
- [ ] `TvItemDetailScreen.kt` (999) — also a refactor candidate
- [ ] `TvSearchScreen.kt` (359)
- [ ] `TvServerConnectionScreen.kt` (405)
- [ ] `TvQuickConnectScreen.kt` (435)
- [ ] `TvRequestsScreen.kt` (302)
- [ ] `TvSettingsScreen.kt` (242)
- [ ] `TvVideoPlayerScreen.kt` (1,089) — also a refactor candidate
- [ ] `TvAdaptiveHomeContent.kt` (360)

For each screen, verify (using a physical remote or `adb shell input keyevent`):
- Initial focus lands on a sensible element
- Every reachable card has a visible focus indicator
- No focus dead-ends (no node where DPAD-back exits the app unexpectedly)
- Long-press, menu, and play/pause buttons all behave

**Acceptance**: A signed-off audit doc at `docs/plans/2026-XX-XX-tv-dpad-audit.md` per
screen, plus issues filed for each defect found.

---

## Tier 2 — Reliability & polish

### 2.1 Music background playback validation
**Status**: code complete (see § Already Done); needs OEM-stress validation
**Effort**: 1–2 days

The architecture is shipped. What's missing is real-device validation:
- Verify lock-screen art renders on Samsung One UI, MIUI, ColorOS (their notification
  customizations break things)
- Doze / app-standby behavior — does playback survive 30+ minutes screen-off?
- Bluetooth headset disconnect / reconnect — does play/pause survive?
- Phone-call interruption + resume

This is testing work, not coding. Output should be a recorded test matrix in
`docs/development/MUSIC_PLAYBACK_VALIDATION.md`.

### 2.2 Offline downloads reliability on aggressive OEMs (Known Issue #7)
**Status**: feature works; long-running edge cases unverified
**Effort**: 2–3 days

Same shape as 2.1 — the feature is shipped, but WorkManager behavior under aggressive
battery-saver policies (Xiaomi, OPPO, Samsung) and process death during long downloads
needs documented validation. The recently-fixed download-hang and ID-mismatch bugs
prove this area still attracts edge cases.

### 2.3 Cast image authentication
**Status**: known trade-off
**Effort**: 3–4 days for the proxy approach
**File**: `ui/player/CastManager.kt`, `ui/player/cast/CastMediaLoadBuilder.kt`

API tokens were removed from Cast URLs (correctly, per CWE-598). The trade-off is that
servers locked down to require auth on `/Items/{id}/Images/*` now serve broken Cast
artwork. Options:
- **Short term**: document the server-side workaround in `KNOWN_ISSUES.md` (allow
  unauthenticated image access for the Items endpoint)
- **Medium term**: add an opt-in local proxy that injects auth headers for Cast
  receivers — would require a local HTTP server in the app, ~3 days
- **Long term**: implement the official Jellyfin Cast receiver protocol
  (`F007D354` / `6F511C87`) which supports custom data payloads — this is large, not on
  any near-term plan

For now, **document the trade-off explicitly** and pick one of the longer paths in a
later quarter.

### 2.4 Content descriptions sweep (Phase D3)
**Status**: partial — 476 `contentDescription` usages across `app/src/main`, but no
audit confirms full coverage
**Effort**: 1–2 days

The codebase clearly cares about a11y (MediaCards has them, lots of IconButtons have
them), but there's no documented TalkBack sweep. Run TalkBack and audit:
- Player controls (play/pause, skip, subtitle/audio buttons)
- Bottom nav bar items
- All media cards on Home and Library screens
- Settings toggles and dropdowns

Filing this as one task rather than per-screen because the actual work is small once
someone sits down with the device. Expected deliverable: small PR + a
`docs/development/ACCESSIBILITY_AUDIT.md` checklist.

### 2.5 AI Discovery TODO
**Status**: dead-end click handler
**Effort**: 0.5 day or "remove the button"
**File**: `ui/screens/home/ExpressiveBentoGrid.kt:113`

```kotlin
onClick = { /* TODO: Implement AI Discovery navigation */ },
```
A button on the home bento grid does nothing on tap. Either wire it to the existing
AI Assistant route (`Screen.AiAssistant.route`) or remove the button until the
discovery feature exists. Both options ship in under an hour.

---

## Tier 3 — Architecture & technical debt

The previous plan tracked HomeScreen / VideoPlayerScreen as the refactor targets.
Those are done. **The new size leaders are below.** Top 10 by line count:

| File | Lines | Verdict |
|---|---:|---|
| `ui/viewmodel/MainAppViewModel.kt` | 1,675 | God ViewModel — see 3.1 |
| `data/repository/JellyfinRepository.kt` | 1,427 | God repository — see 3.2 |
| `ui/screens/ImmersiveTVEpisodeDetailScreen.kt` | 1,235 | Immersive sprawl — see 3.3 |
| `data/offline/OfflineDownloadManager.kt` | 1,216 | Borderline; deferred |
| `ui/screens/ServerConnectionScreen.kt` | 1,215 | Not on any list — see 3.4 |
| `data/repository/GenerativeAiRepository.kt` | 1,195 | AI repo bloat — see 3.5 |
| `ui/screens/RequestsScreen.kt` | 1,148 | New, untracked — see 3.6 |
| `ui/viewmodel/ServerConnectionViewModel.kt` | 1,135 | Pairs with 3.4 |
| `ui/player/tv/TvVideoPlayerScreen.kt` | 1,089 | TV player not refactored |
| `ui/screens/ImmersiveTVSeasonScreen.kt` | 1,069 | Immersive sprawl |

### 3.1 Break up `MainAppViewModel.kt` (1,675 lines)
**Effort**: 3–4 days

Largest single source file. Holds delete-item, library load, home video load, home
load, library item, load item, and several other concerns — each currently lives in its
own test file (`MainAppViewModel*Test.kt`), which is a hint at the natural seams.

Extract into feature-scoped ViewModels or `LibraryActions` / `HomeActions` collaborator
classes. Concrete proposal:
- `LibraryActionsViewModel` — delete-item, library load, library item
- `HomeContentViewModel` — home load, home video load, home videos
- `LoadItemViewModel` (or move into existing `MovieDetailViewModel` / `TVSeasonViewModel`)
- `MainAppViewModel` stays as the navigation/auth coordinator only

Use the existing per-feature test files as the conversion checklist.

### 3.2 Decompose `JellyfinRepository.kt` (1,427 lines)
**Effort**: 3–5 days

The interface refactor (2026-03-30) decoupled consumers, but the concrete class still
mixes media library reads, playback URL construction, image URL construction, user data
reporting, session restoration, quick-connect, and token refresh. Natural splits:

- `JellyfinLibraryRepository` — items, search, person, episodes (most of `IJellyfinRepository`)
- `JellyfinPlaybackUrlBuilder` — `getTranscodedStreamUrl` and related
- `JellyfinUserDataReporter` — played/unplayed/favorite reporting
- `JellyfinSessionRepository` — session restore, quick connect, token refresh helpers
  (already implied by the "keep concrete" list in 2026-03-30 status)
- `JellyfinRepository` becomes a thin façade or is deleted

The 2026-03-30 doc explicitly said *"do not expand IJellyfinRepository further"* — that
guidance is correct. Instead, split the concrete class.

### 3.3 Immersive detail screens (TV Show/Season/Episode, Movie)
**Effort**: 2 days each, can be staged
**Files**: `ImmersiveTVEpisodeDetailScreen.kt` (1,235),
`ImmersiveTVSeasonScreen.kt` (1,069), `ImmersiveTVShowDetailScreen.kt` (1,059),
`ImmersiveMovieDetailScreen.kt` (938)

Each immersive detail screen follows the same general structure: hero section + metadata
+ actions + cast row + similar items + episodes/seasons (where applicable). Extract a
shared `ImmersiveDetailScaffold` and section composables similar to what
`ui/screens/details/components/` already does for the non-immersive path. Should
collapse all four screens to ~400–500 lines each.

### 3.4 ServerConnection screen + ViewModel pair (1,215 + 1,135 lines)
**Effort**: 3 days

These weren't on any prior plan but together they're 2,350 lines of single-feature code.
Likely candidates:
- Pull the QR-code / Quick Connect path into its own screen + VM (similar to
  `TvQuickConnectScreen` which is its own file)
- Pull cert-pinning prompts into `ui/screens/details/components/` or a similar
- Extract the multi-step "scan / manual entry / test connection / submit" flow into
  a state machine; the current single-file approach is the reason for the size

### 3.5 `GenerativeAiRepository.kt` (1,195 lines)
**Effort**: 2 days

Brand new since the last plan. Holds chat, summary, mood analysis, recommendations,
smart search, person bio, theme extraction, why-you'll-love-this, mood collections, and
multimodal (mostly commented out). Each AI feature has its own prompt template, its own
parsing, and its own caching strategy. Split by feature into a small `data/ai/`
sub-package:

- `AiChatService`
- `AiSummaryService`
- `AiRecommendationService`
- `AiPersonBioService`
- `AiThematicAnalysisService`
- Repository becomes a façade over the services

Bonus: the `loadBitmapFromUri` `TODO()` is in a commented-out block (lines 1169–1194) —
when you uncomment that for multimodal support, implement it via Coil's
`ImageRequest.Builder` and the existing `ContentResolver` pattern.

### 3.6 Track the Requests feature (1,148 + 741 lines)
**Effort**: documentation only (1 hour)

`RequestsScreen.kt`, `RequestsViewModel.kt`, and `TvRequestsScreen.kt` exist but appear
in no `docs/features/` or `docs/plans/` document. Add a feature doc covering what this
is (Jellyseerr integration? Self-hosted requests?), where the endpoints come from, and
which feature flags gate it. Without that, future Claude/Gemini sessions will treat the
3 files as orphans and possibly delete them.

### 3.7 Test suite is partially red (2026-03-30 note never followed up)
**Effort**: 1–2 days of triage

The repository refactor note said *"full unit test suite is not green overall."* That
admission was 7 weeks ago and no follow-up exists. Action:
1. Run `./gradlew testDebugUnitTest` and capture the failure list
2. Categorize: flaky vs broken-by-refactor vs broken-by-dep-update
3. File one issue per category, fix in order
4. Add a `verifyTestsPass` task to CI gating, so this can't drift again

### 3.8 Build warnings (~150) (Phase F / Known Issue #11)
**Effort**: 2–3 hours
The "warning budget" task in `build.gradle.kts` already encodes the categories
(`deprecation: 24, nullability: 16, api-migration: 18, tooling: 12`). Reduce each
budget by 25% as a sprint goal, then again. The pipeline machinery exists; just use it.

### 3.9 `DeviceCapabilities.kt` (934 lines) — high test-value
**Effort**: 1–2 days
Not a refactor — a test-coverage target. This file feeds every playback decision.
`DeviceCapabilitiesTest.kt` exists but doesn't cover the codec-detection branches
exhaustively. Add table-driven tests across codec families (H.264, H.265 8-bit, H.265
10-bit, AV1, VP9), bitrate tiers, and audio channel configurations. This is the kind
of file where a future Jellyfin SDK update could silently break direct-play decisions.

---

## Tier 4 — Documentation hygiene

The `docs/plans/` directory has accumulated 45+ files including 5 session summaries,
3 phase-completion summaries, dated plans from February, and an `IMPROVEMENT_SYSTEM.md`
describing how plans are managed. Three meta-problems:

### 4.1 Conflicting truth sources
`CURRENT_STATUS.md` says Music Playback is "Partial." `KNOWN_ISSUES.md` says it's
"Incomplete." `AudioService.kt` says both are wrong (it's nearly fully wired). Pick
**one canonical truth source** (`CURRENT_STATUS.md` is the right one — it already calls
itself the source of truth in its own header) and rewrite the others to defer to it.

### 4.2 Stale version numbers in `GEMINI.md`
The "High-Level Architecture" section lists Compose BOM 2026.03.01, Hilt 2.59.1, Kotlin
2.3.20, Media3 1.10.0-rc03, Jellyfin SDK 1.8.6, versionCode 79. Every one of those
is now stale. Replace with a single line that says *"see `gradle/libs.versions.toml`
for current versions"* — that's where they actually live, and a doc copy will always
drift.

### 4.3 Archive the dated plans
Move anything under `docs/plans/` whose work is shipped into `docs/archive/`. Specifically:
- `2026-02-20-*` (TV login, surgical fixes — done)
- `2026-02-21-*` (transcoding progress — done)
- `2026-02-22-*` (offline download bug fixes — done)
- `2026-02-24-*` (immersive library refactor — done)
- `2026-03-16-upgrade-plan.md` / `upgrade-path.md` (superseded by `UPGRADE_PATH.md`)
- `2026-03-28-*` (Android 16/17 modernization — done; see § Already Done)
- `2026-03-30-jellyfin-repository-refactor-status.md` (refactor at chosen boundary — done)
- `2026-04-26-android-16-17-modernization-phase-2.md` (done; see § Already Done)
- All `SESSION_*_SUMMARY.md`, `PHASE_*_*.md`, `QUICK_WINS_*` (point-in-time records)

Leave only the active strategic docs in `docs/plans/`:
- `IMPROVEMENT_PLAN.md` (this file)
- `CURRENT_STATUS.md`
- `ROADMAP.md`
- `TV_ROADMAP.md`
- `UPGRADE_PATH.md`
- `CINEFIN_SERVER_PLUGIN_ARCHITECTURE.md`
- `NAVIGATION3_EVALUATION.md`

---

## Suggested execution order

If you're picking what to do next, in priority order:

1. **2.5 AI Discovery TODO** (30 min) — visible broken button, trivial fix
2. **1.1 Resume "Ask" dialog** (0.5 day) — backend ready, ship the UI
3. **3.6 Document the Requests feature** (1 hour) — prevents future confusion
4. **3.7 Test suite triage** (1–2 days) — unlock confident refactoring
5. **1.3 Auto-skip intro/outro** (2–3 days) — high-impact user feature
6. **1.4 Android TV D-pad audit** (3–5 days) — flips TV from Partial → Complete
7. **1.2 Subtitle gaps** (3–5 days) — last missing piece of "complete media client"
8. **3.1 + 3.2 MainAppViewModel and JellyfinRepository decomposition** (~1 week)
9. **3.3 Immersive screen extraction** (~1 week, can stage per-screen)
10. **4.1 + 4.2 + 4.3 Docs cleanup** (1 day, batch it)

Tier 2 reliability work (2.1 music validation, 2.2 OEM offline, 2.4 a11y sweep) is best
folded into whichever release ships features 1-3 above — they're "do while you're already
holding the test device."

---

## GitHub issue templates

If you want to file these as issues directly, here are ready-to-paste titles and bodies.
I've tagged what should be one issue vs. parent-and-children. Use the existing
`.github/ISSUE_TEMPLATE/feature_request.md` and `bug_report.md` as the base.

### Issues to file

| # | Title | Type | Priority |
|---|---|---|---|
| A | Wire up ResumePlaybackMode.ASK dialog in player | enhancement | high |
| B | Add subtitle sync delay preference | enhancement | medium |
| C | Enable external subtitle support in streaming player | enhancement | medium |
| D | Preserve ASS/SSA subtitle styling | enhancement | medium |
| E | Add auto-skip intro/outro preference | enhancement | medium |
| F | Android TV D-pad navigation audit (parent) | epic | high |
| F.1–F.10 | One sub-issue per TV screen | task | medium |
| G | Music background playback OEM validation | testing | medium |
| H | Offline download OEM/battery-saver validation | testing | medium |
| I | Cast image authentication strategy decision | discussion | low |
| J | TalkBack accessibility sweep | enhancement | medium |
| K | Implement AI Discovery click handler or remove the button | bug | low |
| L | Decompose MainAppViewModel.kt (1,675 lines) | refactor | medium |
| M | Decompose JellyfinRepository.kt (1,427 lines) | refactor | medium |
| N | Extract immersive detail-screen scaffold | refactor | medium |
| O | Split ServerConnectionScreen + ViewModel | refactor | low |
| P | Decompose GenerativeAiRepository.kt | refactor | medium |
| Q | Document the Requests feature | docs | high |
| R | Triage and fix failing unit tests | bug | high |
| S | Reduce build warning budget by 25% per sprint | chore | low |
| T | Expand DeviceCapabilities test coverage | testing | medium |
| U | Documentation: pick one canonical status source | docs | medium |
| V | Documentation: archive shipped dated plans | docs | low |
| W | Documentation: remove stale versions from GEMINI.md | docs | low |

Each issue body should cite the relevant section of this plan plus its file references.
Most of the labor of writing those bodies is already done above — paragraphs in
§§ 1.1–4.3 can be lifted verbatim into the issues.

---

## Related documentation

- [CURRENT_STATUS.md](CURRENT_STATUS.md) — feature truth table
- [ROADMAP.md](ROADMAP.md) — forward-looking feature work
- [KNOWN_ISSUES.md](../features/KNOWN_ISSUES.md) — user-facing bugs (needs reconciliation
  with this plan; see 4.1)
- [UPGRADE_PATH.md](UPGRADE_PATH.md) — dependency upgrade strategy
- [GEMINI.md](../../GEMINI.md) — development guidelines (needs version cleanup; see 4.2)
- [TESTING_GUIDE.md](../development/TESTING_GUIDE.md) — ViewModel testing patterns
