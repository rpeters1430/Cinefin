# Cinefin Android — App Todo List

**Created**: 2026-09-01
**Verified against code on**: 2026-09-01 (versionCode 161 / versionName 15.92)
**Scope**: This is the single working checklist for bugs, improvements, and new
features across the app. **Android TV is intentionally back-burnered** — its
section is last and shortest on purpose. Everything above it is phone/tablet.

> This is now the authoritative day-to-day checklist, superseding
> `CURRENT_STATUS.md`, `KNOWN_ISSUES.md`, `ROADMAP.md`, and `IMPROVEMENT_PLAN.md`
> for active tracking — those four had drifted from each other and from the code
> (see [Note on the older docs](#note-on-the-older-docs) at the bottom). They
> still hold detailed history and rationale, so nothing is deleted, but
> **this file is the one to check off**; update the others only if you want the
> historical record to stay accurate.

Checklist convention: `[ ]` open, `[x]` done. Check items off in place and add
a one-line note (date + what shipped) rather than deleting the line — keeps a
lightweight changelog without a separate summary doc.

---

## 🔥 Do next (highest leverage, lowest effort)

- [x] **Wire up the "Ask" resume dialog** — 2026-09-01: `VideoPlayerViewModel`
      now branches on `ResumePlaybackMode.ASK` — if the saved position is
      above a 5s threshold it starts playback at 0 and surfaces
      `showResumeDialog`/`resumeDialogPositionMs` in `VideoPlayerState`
      instead of silently resuming. Added `ResumePlaybackDialog` composable
      (`VideoPlayerDialogs.kt`) wired into `VideoPlayerScreen.kt`, plus
      `ConfirmResumePlayback`/`DismissResumeDialog` intents. TV still resumes
      unprompted (`ALWAYS`-style), as scoped.
- [x] **Delete or restore `TvPlayerControls_Backup.kt`** — 2026-09-01:
      confirmed zero references anywhere in the codebase and deleted the
      file.
- [x] **Implement or remove the multimodal image-loading stub** — 2026-09-01:
      removed the fully-commented-out `analyzeImage`/`loadBitmapFromUri`
      block from `GenerativeAiRepository.kt` (it wasn't even live code — the
      whole thing was inside a `/* */` block already). Nothing else
      referenced it.
- [x] **Re-run `testDebugUnitTest` and triage red tests** — 2026-09-01:
      couldn't run it from this session directly (this sandbox's egress
      policy denies `dl.google.com`, so the Android SDK can't be downloaded
      here), but PR #1262's CI (`build-test-lint`) ran it for real: `Build
      debug APK` and `Run unit tests` both succeeded on the full suite,
      confirming the resume-dialog/auto-skip changes (and the new/rewritten
      tests added alongside them) actually compile and pass. `Run lint`
      still fails, but that's pre-existing and unrelated — same 4 errors,
      same first failure (`EpisodeNotificationWorker.kt:152`,
      `MissingPermission`), already failing on `main` before this branch
      existed; documented on the PR rather than folded into this diff.

---

## 🐛 Bugs

- [x] **Resume "Ask" mode is a no-op** — see Do Next above; fixed 2026-09-01.
- [ ] **CI is red on `main`, unrelated to any specific PR** — verified
      2026-09-01 while driving PR #1262 to green: `build-test-lint`'s `Run
      lint` step fails on `main` itself (checked at commit `1299abe`, this
      branch's base, and multiple prior unrelated PRs/merges going back
      through at least Aug 27) with `EpisodeNotificationWorker.kt:152:
      Error: Call requires permission which may be rejected by user
      [MissingPermission]` — the `POST_NOTIFICATIONS` call at that line
      needs a `checkSelfPermission`/`SecurityException` guard. Separately,
      the repo's `.github/workflows/claude.yml` automated-review job
      (triggered on `pull_request`) fails near-instantly on essentially
      every PR (is_error:true, 0 turns, 0 cost, ~350-400ms — dies during
      SDK init before reading any diff), confirmed across multiple
      unrelated PRs including Renovate dependency bumps; looks like a
      workflow/secret misconfiguration (e.g. `ANTHROPIC_API_KEY`), not a
      code issue. Both make every PR in this repo show red regardless of
      its own quality — worth a maintainer's attention independent of any
      single PR's diff.
- [ ] **Subtitle sync delay missing** — no `subtitleDelayMs` field anywhere in
      `SubtitleAppearancePreferences` or the player. Users with slightly
      out-of-sync subtitles have no in-app fix. **Files**:
      `ui/player/VideoPlayerViewModel.kt`,
      `data/preferences/SubtitleAppearancePreferences.kt`. *~1 day.*
- [ ] **External subtitle files not selectable in the streaming player** —
      `OfflineDownloadManager.downloadExternalSubtitles()` exists for offline
      playback, but the live-streaming path still filters external tracks
      out. Inconsistent experience between offline and online playback of the
      same item. *~2 days.*
- [ ] **ASS/SSA subtitle styling is flattened** — styled subtitles get
      converted to plain VTT-equivalent text, losing positioning/color/font
      styling that Media3's renderer could otherwise preserve.
- [ ] **Cast artwork breaks on auth-locked servers** — API tokens were
      correctly removed from Cast URLs (CWE-598 fix), but that means servers
      requiring auth on `/Items/{id}/Images/*` now serve broken thumbnails to
      the Cast receiver. No proxy or workaround shipped yet; currently just a
      known trade-off. Decide: document only, ship a local auth-injecting
      proxy (~3 days), or defer to the official Jellyfin Cast receiver
      protocol (large, not near-term).
- [ ] **Progress reporting has no retry-on-drop queue** — a network blip
      during playback-position reporting to the server silently loses that
      update instead of queuing/retrying. Low-severity but a real data-loss
      case for watch-progress sync across devices.

---

## 🧹 Improvements / tech debt

Verified current line counts (Sept 2026) — several of these are worse than
what `IMPROVEMENT_PLAN.md` last recorded in May, i.e. the god-files kept
growing while other work shipped around them:

| File | Lines now | Trend |
|---|---:|---|
| `ui/screens/RequestsScreen.kt` | 1,841 | grew from 1,148 — now the largest UI file in the app |
| `ui/viewmodel/MainAppViewModel.kt` | 1,716 | grew from 1,675 |
| `ui/screens/ServerConnectionScreen.kt` | 1,343 | grew from 1,215 |
| `ui/viewmodel/ServerConnectionViewModel.kt` | 1,272 | grew from 1,135 |
| `data/repository/JellyfinRepository.kt` | 1,504 | grew from 1,427 |
| `data/repository/GenerativeAiRepository.kt` | 1,184 | roughly flat |
| `ui/player/tv/TvVideoPlayerScreen.kt` | 1,089 | flat (TV, low priority) |

- [ ] **Break up `RequestsScreen.kt` (1,841 lines)** — now the single largest
      file in the app and was untracked as recently as May. Extract
      request-list, request-detail, and search/filter sections into their own
      composables before it grows further.
- [ ] **Decompose `MainAppViewModel.kt` (1,716 lines)** — proposed split:
      `LibraryActionsViewModel` (delete-item, library load), `HomeContentViewModel`
      (home load, home videos), fold single-item loads into the existing
      detail-screen ViewModels. Per-feature test files already hint at the
      natural seams.
- [ ] **Split `ServerConnectionScreen.kt` + `ServerConnectionViewModel.kt`
      (1,343 + 1,272 lines)** — pull Quick Connect into its own screen/VM
      (mirrors what already exists for TV), extract the multi-step
      scan/manual-entry/test/submit flow into an explicit state machine.
- [ ] **Decompose `JellyfinRepository.kt` (1,504 lines)** — split into
      `JellyfinLibraryRepository`, `JellyfinPlaybackUrlBuilder`,
      `JellyfinUserDataReporter`, `JellyfinSessionRepository`; keep
      `IJellyfinRepository` frozen per the 2026-03-30 refactor note — don't
      expand the interface further, split the concrete class instead.
- [ ] **Split `GenerativeAiRepository.kt` (1,184 lines)** — one file handles
      chat, summaries, mood analysis, recommendations, smart search, person
      bio, thematic analysis, and (mostly commented-out) multimodal. Split by
      feature under a `data/ai/` sub-package with the repository as a façade.
- [x] **Auto-skip intro/outro preference** — 2026-09-01: added
      `autoSkipIntro: Boolean` to `PlaybackPreferences` (DataStore-backed, off
      by default) with a toggle in Settings → Playback → "Auto-skip Intro &
      Credits". `VideoPlayerViewModel` now silently seeks past a known
      intro/outro window once per item when enabled, without touching the
      existing manual "Skip Intro"/"Skip Credits" buttons. While doing this,
      found and fixed a real bug: `playbackPreferences` in
      `VideoPlayerViewModel` was `stateIn(..., WhileSubscribed(5000), ...)`
      but only ever read via `.value`, never collected — so its upstream
      DataStore flow never actually started, and `.value` stayed frozen at
      `PlaybackPreferences.DEFAULT` (`resumePlaybackMode = ALWAYS`) for the
      ViewModel's whole life regardless of what the user picked in Settings.
      Switched it to `SharingStarted.Eagerly`. This was a deeper root cause
      of the "Ask" resume dialog bug above than the missing `when` branch
      alone — even `NEVER` would silently have had no effect.
- [x] **Document the Requests feature** — 2026-09-01: added
      `docs/features/REQUESTS.md` (and an index entry in `docs/README.md`).
      Turns out there isn't one backend — it talks to Jellyseerr/Overseerr,
      Sonarr, and Radarr directly (each optional/independently configured),
      plus an optional self-hosted "Cinefin" Jellyfin plugin used only to
      import credentials for those three, never to proxy requests. No
      feature flag gates it; the bottom-nav tab is hidden until at least one
      backend is enabled+configured in Settings → Media Requests.
- [ ] **Reduce the ~150 build warnings** — the warning-budget machinery
      already exists in `build.gradle.kts` (`deprecation: 24, nullability:
      16, api-migration: 18, tooling: 12`); nobody has actually cut a budget
      since it was set up. Cut each category 25% as a sprint goal.
- [ ] **Expand `DeviceCapabilities.kt` test coverage** — this file decides
      direct-play vs. transcode for every playback and doesn't have
      table-driven tests across codec families (H.264, H.265 8/10-bit, AV1,
      VP9) and bitrate/audio-channel tiers. A future Jellyfin SDK bump could
      silently break playback decisions with no test catching it.
- [ ] **TalkBack / accessibility sweep** — a11y usage exists throughout
      (`contentDescription` is widely used) but there's no recorded sweep.
      Do one pass focused on player controls, bottom nav, media cards, and
      settings toggles; write up findings in
      `docs/development/ACCESSIBILITY_AUDIT.md`.
- [ ] **Reconcile the four planning docs** — `CURRENT_STATUS.md`,
      `KNOWN_ISSUES.md`, `ROADMAP.md`, and `IMPROVEMENT_PLAN.md` each describe
      overlapping ground and had already drifted from each other by May 2026
      (music playback was "Complete" in one, "Partial" in another).
      Recommend: keep `CURRENT_STATUS.md` as the single feature-status source
      (it already claims that role), fold `KNOWN_ISSUES.md`'s open bugs into
      this file, and archive the ~15 dated/session-summary files under
      `docs/plans/` (`2026-02-*`, `SESSION_*_SUMMARY.md`, `PHASE_*_*.md`,
      `QUICK_WINS_*`) into `docs/archive/` since their work has shipped.
- [x] **Refresh stale version numbers in `CLAUDE.md`** — 2026-09-01: replaced
      every hardcoded version number (versionCode/versionName in two places,
      Compose BOM, Hilt, Media3, Retrofit/OkHttp/Jellyfin SDK, Coil, Kotlin/KSP,
      compileSdk/minSdk/targetSdk) with pointers to `app/build.gradle.kts` /
      `gradle/libs.versions.toml`. Turned out drift was worse than the todo
      described — minSdk had drifted from the documented 26 to the actual 30,
      targetSdk 35→36, compileSdk 36→37 — which is exactly why pointers beat
      copied numbers.

---

## ✨ New features (phone/tablet)

Pulled from `AI_INTEGRATION_OPPORTUNITIES.md` and the Phase 4 backlog in
`ROADMAP.md` — picking out the ones with the best effort/impact ratio given
what's already shipped (AI chat, AI summaries, mood analysis all exist).

- [ ] **"Why You'll Love This" AI blurb on detail screens** — short
      personalized paragraph using the existing `GenerativeAiRepository`
      chat/summary infra, driven off watch history. Natural next step now
      that AI summaries are already shipped.
- [ ] **"Because You Watched X" home-screen explanations** — pairs with
      existing recommendation generation; surface the *reason* next to
      recommended items instead of an unexplained row.
- [ ] **"What should I watch tonight?" conversational entry point** — a
      focused prompt-driven flow on top of the AI Assistant that already
      exists, rather than a whole new feature.
- [ ] **Subtitle sync delay UI** — see Bugs above; listing again here because
      it doubles as a small feature (a ±5s slider in the player overlay).
- [ ] **Home screen widget** — Continue Watching + Quick Play, still fully
      unstarted per `ROADMAP.md` Phase 4.4.
- [ ] **Multi-profile / kids mode** — still fully unstarted per `ROADMAP.md`
      Phase 4.3. Larger effort; worth scoping only if there's real demand.
- [ ] **Sync Play** — still fully unstarted (`ROADMAP.md` 4.2). Large,
      probably not worth prioritizing above the bugs/tech-debt list.
- [ ] **Live TV & DVR** — still fully unstarted (`ROADMAP.md` 4.1). Same
      caveat as Sync Play — big lift, low current demand signal.

---

## 📺 Android TV (back burner)

Intentionally last and intentionally short — TV is "partial" and staying that
way while phone/tablet gets the attention. Revisit this section as a block,
not item-by-item, once the phone app backlog above is mostly clear.

- [ ] Full D-pad-only audit of the 10 TV screens (`TvHomeScreen`,
      `TvLibraryScreen`, `TvItemDetailScreen`, `TvSearchScreen`,
      `TvServerConnectionScreen`, `TvQuickConnectScreen`, `TvRequestsScreen`,
      `TvSettingsScreen`, `TvVideoPlayerScreen`, `TvAdaptiveHomeContent`) —
      focus indicators, dead-ends, initial focus placement.
- [ ] TV player controls: D-pad seek, select-to-play/pause, back-button
      handling, subtitle/audio track selection via remote.
- [ ] Refactor `TvItemDetailScreen.kt` (999 lines) and
      `TvVideoPlayerScreen.kt` (1,089 lines) once the audit above identifies
      what actually needs to change (don't refactor blind).

---

## Note on the older docs

`CURRENT_STATUS.md`, `KNOWN_ISSUES.md`, and `ROADMAP.md` were all last
verified between April and June 2026; `IMPROVEMENT_PLAN.md` (the most
detailed one) was last audited 2026-05-19 against versionCode 111. The app is
now at versionCode 161 — roughly 50 versions have shipped since that audit,
including things that plan flagged as open (skip-intro/outro buttons, the AI
Discovery button wiring) which are already done in code today. Rather than
patch four documents that had already drifted from each other before this
one existed, this file is a fresh pass verified directly against the current
source tree. Use it as the day-to-day list; the older docs are still useful
for historical rationale on *why* a decision was made (e.g. the Cast
token-removal trade-off, the Google Cast receiver choice).
