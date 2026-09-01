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

- [ ] **Wire up the "Ask" resume dialog** — `VideoPlayerViewModel.kt:241` still
      routes `ResumePlaybackMode.ASK` through the `else` branch, so it behaves
      exactly like `ALWAYS`. The preference exists in Settings and quietly
      lies to the user. Backend (`playbackProgressManager.getResumePosition`)
      is ready; just needs a branch that emits a prompt state + a
      `ResumePlaybackDialog` composable wired to phone player (TV can wait).
      *0.5 day.*
- [ ] **Delete or restore `TvPlayerControls_Backup.kt`** —
      `ui/player/TvPlayerControls_Backup.kt` (326 lines, modified Aug 27) is a
      stray backup file sitting alongside the real TV controls. Confirm
      nothing references it, then delete it — dead code with a `_Backup`
      suffix is exactly the kind of thing that confuses the next person (or
      the next Claude session). *15 min.*
- [ ] **Implement or remove the multimodal image-loading stub** —
      `GenerativeAiRepository.kt:1181` has `TODO("Implement bitmap loading
      from URI")` inside `loadBitmapFromUri`. It's the only TODO/FIXME left in
      `app/src/main` (test doubles have a few unrelated `TODO()` stubs, e.g.
      `MediaRequestSettingsViewModelTest.kt`, which are fine as-is). Either
      wire it via Coil's `ImageRequest.Builder` + `ContentResolver` for the
      (currently mostly commented-out) multimodal AI path, or delete the dead
      function until multimodal ships. *1–2 hrs.*
- [ ] **Re-run `testDebugUnitTest` and triage red tests** — last documented
      check (2026-03-30) found the suite "not green overall" and nobody
      confirmed it since. This has been open for 5+ months across three plan
      revisions without anyone actually running the command. Do that first;
      it's the fastest way to know how much of the rest of this list is safe
      to build on. *~30 min to run, more to fix what it finds.*

---

## 🐛 Bugs

- [ ] **Resume "Ask" mode is a no-op** — see Do Next above; listed here too
      since it's a genuine bug, not just a missing feature.
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
- [ ] **Auto-skip intro/outro preference** — manual "Skip Intro"/"Skip
      Credits" buttons already ship (`VideoPlayerOverlays.kt`,
      `ExpressiveVideoControls.kt` both have them wired to chapter markers).
      What's still missing is the *automatic* silent-skip preference
      (`autoSkipIntro: Boolean`) for users who don't want to tap a button
      every episode. Small addition on top of what already exists.
- [ ] **Document the Requests feature** — `RequestsScreen.kt`,
      `RequestsViewModel.kt`, `TvRequestsScreen.kt` still have no entry in
      `docs/features/` explaining what backend this talks to (Jellyseerr?
      something self-hosted?) or which flags gate it. Given it's now the
      largest screen in the app, this is overdue. *~1 hr.*
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
- [ ] **Refresh stale version numbers in `CLAUDE.md`** — it currently lists
      Compose BOM 2026.03.01, Hilt 2.59.1, versionCode 123/versionName 14.91
      in one place and versionCode 79/14.47 in another; the real values are
      now 161/15.92. Replace the copied numbers with "see
      `gradle/libs.versions.toml`" so this can't drift again.

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
