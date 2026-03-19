# Cinefin TV Roadmap

> Source-of-truth roadmap for the Android TV / Google TV experience in Cinefin.
>
> This document replaces the older split between `ANDROID_TV_MODERNIZATION_PLAN.md`, `features/tv detail.md`, `TV_DETAIL_UPGRADES_REVIEW.md`, and `TV_SHOWS_SCREEN_IMPROVEMENTS.md` as the primary planning artifact to follow going forward.

---

## Purpose

Turn the current TV mode into a polished, TV-first product with:

- predictable D-pad behavior
- strong focus restoration
- premium 10-foot browsing surfaces
- clear route-specific library behavior
- a dedicated TV playback experience
- enough automated/manual validation to stop regressions from slipping back in

---

## Current truth snapshot

Cinefin already has a real TV foundation in place:

- `MainActivity` branches into TV mode on television devices
- `TvJellyfinApp` provides a dedicated TV shell
- `TvNavGraph` defines TV routes
- TV-specific screens already exist for home, library, details, search, settings, server connection, and quick connect
- TV focus and remote helpers already exist
- the older modernization plan claims the initial TV shell, navigation drawer, home hero patterns, detail screen, player shell, library, and search work have all landed

### What is already substantially done

These items were previously tracked as completed across the older TV plan and TV UI review docs and should be treated as **baseline shipped work**, not net-new roadmap items:

- TV app shell and side navigation are in place
- TV home already has immersive/hero concepts
- TV library browsing already works
- TV search already exists as a dedicated TV flow
- TV detail screens already have a meaningful redesign foundation
- TV player controls already have a dedicated TV implementation
- TV Shows mobile/tablet expressive work was completed and reviewed separately
- Android TV detail metadata work was completed and reviewed separately

### What still appears incomplete or risky

The older docs drifted toward over-reporting completion. Based on the repo planning set as a whole, the biggest remaining TV risks are still:

- focus restoration consistency across drawer, rows, grids, details, search, and playback return
- route-specific library polish for Movies / TV Shows / Music / Stuff / Favorites
- TV details that feel premium across all content types, not just acceptable
- playback return/orientation polish after exit and autoplay-next flows
- real-device validation and regression coverage
- documentation drift between roadmap, review, and implementation notes

### Planning rule from this point forward

Use the status markers below consistently:

- ✅ **Done**: implemented and considered baseline behavior
- 🔄 **In progress / partial**: exists, but still needs meaningful follow-up
- ⏳ **Planned**: not started or not reliable enough to count as shipped quality

---

## Consolidated status matrix

| Area | Status | Notes |
| :--- | :--- | :--- |
| TV entry path and shell | ✅ Done | Dedicated TV app path and shell already exist. |
| TV navigation drawer | ✅ Done | Navigation foundation is present. |
| TV home immersive foundation | ✅ Done | Hero/background concepts exist; quality pass still needed. |
| TV library browsing foundation | ✅ Done | Browsing works, but route-specific polish remains. |
| TV search foundation | ✅ Done | Dedicated TV search flow exists. |
| TV detail foundation | 🔄 Partial | Solid base exists; premium action/rail/focus polish still needed. |
| TV playback overlay foundation | 🔄 Partial | Dedicated TV playback exists; remote-first chrome still needs refinement. |
| Focus restoration and D-pad reliability | 🔄 Partial | Helpers exist, but this is still the highest-risk usability area. |
| Route-specific TV library UX | 🔄 Partial | Works today, but several routes still feel generic. |
| TV settings / sign-in polish | 🔄 Partial | Functional, but not fully refined for a 10-foot UI. |
| Real-device validation | ⏳ Planned | Older docs marked this done too optimistically; treat as required work. |
| TV automated testing coverage | ⏳ Planned | Needed to prevent regressions. |

---

## Documents folded into this roadmap

### `docs/plans/ANDROID_TV_MODERNIZATION_PLAN.md`
Folded in as the historical record for the initial TV modernization push. Its completed phases now map to the “baseline shipped work” and “status matrix” sections above.

### `docs/features/tv detail.md`
Folded in as the original expressive upgrade plan for TV shows/detail surfaces. The actionable parts now live under the library/detail milestones below.

### `docs/features/TV_DETAIL_UPGRADES_REVIEW.md`
Folded in as implementation evidence that several TV-related UI upgrades are already complete. Those completed items now inform the status matrix instead of living as a separate planning track.

### `docs/features/TV_SHOWS_SCREEN_IMPROVEMENTS.md`
Folded in as a completed implementation summary for the TV shows browse experience. It is reference material, not an active roadmap.

---

## Roadmap priorities

1. Focus and D-pad reliability
2. TV architecture cleanup where it improves maintainability
3. Home screen polish
4. Route-specific library browsing polish
5. Detail screen redesign completion
6. Search / settings / sign-in polish
7. TV playback overlay refinement
8. TV UI tests and device validation

---

## Milestone 1 — Focus, D-pad, and restoration

### Goal
Make navigation on TV reliable and predictable on every screen.

### Why this moved to the top
The repo already has a lot of TV UI in place. The biggest remaining reason the experience can still feel “wonky” is focus inconsistency, not lack of screens.

### Screen-level contract to define everywhere
Each TV screen should define:

- initial focus target
- left-edge behavior
- right-edge behavior
- back behavior
- empty-state focus target
- focus restore behavior when returning from another route

### Tasks
- [ ] Write a focus contract for each TV route
- [ ] Standardize `TvScreenFocusScope` usage across all TV screens
- [ ] Ensure every screen has a stable `screenKey`
- [ ] Expand focus restore to include drawer items, grid items, hero items, action rows, and search results
- [ ] Persist focused index and scroll position by route/content source
- [ ] Restore the exact launching item after backing out of details
- [ ] Restore focus to the launching item after exiting playback
- [ ] Restore search query + result focus after returning from detail screens
- [ ] Audit `TvKeyboardHandler` against real remote behavior
- [ ] Confirm handling for D-pad center, enter, back, search, media keys, and menu keys where applicable

### Suggested file targets
- `app/src/main/java/com/rpeters/jellyfin/ui/tv/TvFocusManager.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/tv/TvKeyboardHandler.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/tv/TvJellyfinApp.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/tv/TvNavGraph.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/screens/tv/TvHomeScreen.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/screens/tv/TvLibraryScreen.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/screens/tv/TvItemDetailScreen.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/screens/tv/TvSearchScreen.kt`
- `app/src/main/java/com/rpeters/jellyfin/ui/screens/tv/TvSettingsScreen.kt`

### Definition of done
- Focus never feels random
- Back navigation returns the user to the correct place
- Drawer/content handoff is consistent
- The app is usable comfortably with only a remote

---

## Milestone 2 — TV architecture cleanup

### Goal
Create a clearer TV-only presentation layer so TV stops inheriting mobile design and navigation assumptions where that causes friction.

### Tasks
- [ ] Create a clearer package boundary for TV-only code
- [ ] Move TV helpers into a more consistent namespace
- [ ] Audit TV screens for accidental mobile-only UI dependencies
- [ ] Keep data/domain/viewmodel shared, but isolate TV presentation decisions
- [ ] Create a dedicated `CinefinTvTheme` if one does not already exist in a clean form
- [ ] Define TV-specific design tokens for spacing, typography, focus glow, scale, gutters, and content width
- [ ] Review whether TV should continue sharing the same launcher/activity path as mobile
- [ ] Re-evaluate whether Leanback support should remain optional for the current build strategy

### Definition of done
- TV code is easier to follow
- TV presentation is no longer accidentally driven by mobile assumptions
- Theme and layout decisions for TV are explicit rather than incidental

---

## Milestone 3 — Home screen polish

### Goal
Make Home the flagship TV surface and the visual standard for the rest of the TV app.

### Already landed
- immersive/hero foundations
- backdrop-driven browsing patterns
- TV row infrastructure

### Tasks
- [ ] Improve hero / featured item selection logic
- [ ] Prefer meaningful featured candidates such as Continue Watching, Next Up, new additions, or high-value recommendations
- [ ] Add a better artwork fallback chain for backdrop/banner/logo/poster
- [ ] Smooth backdrop transitions when focused content changes
- [ ] Refine row ordering and overall content strategy
- [ ] Decide which rows are essential: Continue Watching, Next Up, Recent Movies, Recent Episodes, Stuff, Libraries, Favorites, Recommended
- [ ] Improve loading, error, and empty states with TV-friendly placeholders and retry handling
- [ ] Audit recomposition hotspots caused by focus and backdrop changes
- [ ] Prefetch nearby hero and backdrop images to reduce jank

### Definition of done
- Home feels cinematic and intentional
- Moving through rows feels smooth
- Loading and error states feel designed rather than temporary

---

## Milestone 4 — Route-specific library browsing polish

### Goal
Turn Movies, TV Shows, Music, Stuff, and Favorites into purpose-built TV destinations instead of one generic grid with minor route differences.

### Already landed
- TV library browsing works
- TV shows browse/detail-related expressive work was heavily upgraded in the non-TV phone/tablet surfaces and reviewed
- Android TV already has dedicated browse routes and card infrastructure

### Tasks
- [ ] Define route-specific behavior for Movies, TV Shows, Music, Stuff, and Favorites
- [ ] Keep shared grid infrastructure, but customize headers, filters, sort modes, empty states, and card styles per route
- [ ] Add TV-friendly filter and sort UX
- [ ] Persist current sort/filter state by route
- [ ] Tune grid column counts for 1080p and 4K
- [ ] Support different card styles where appropriate
- [ ] Improve pagination UX with visible loading-more feedback
- [ ] Prevent focus jumps while more items append
- [ ] Preserve exact focus position after entering/exiting details

### Definition of done
- Each library route feels intentional
- Large libraries remain comfortable to browse with a remote
- Pagination does not break focus or user orientation

---

## Milestone 5 — Detail screen redesign completion

### Goal
Make TV details feel like a premium streaming app surface across movie, series, season, episode, and home-video flows.

### Already landed
- Android TV detail screens use `androidx.tv.material3`
- richer metadata work has already been implemented and reviewed
- TV shows/seasons/episodes have already seen expressive design improvements outside the TV-specific surface

### Corrective note from the older docs
The old modernization plan marked detail work as fully done, while the roadmap still had major redesign items open. The most accurate interpretation is: **the foundation is implemented, but the premium polish pass is still incomplete**.

### Tasks
- [ ] Build or finalize a TV-first detail template with backdrop, logo/title area, metadata row, actions row, overview, and related rails
- [ ] Support content-type variants for movie, series, season, episode, and home video
- [ ] Standardize primary action order: Play/Resume, Play from Beginning, Next Episode, Favorite, Watched, Audio/Subtitles, More
- [ ] Make resume progress obvious and useful
- [ ] Set dynamic default focus to the most useful action
- [ ] Add rails for seasons, episodes, related titles, cast, and next-up content where available
- [ ] Preserve focus correctly between actions and nested rails
- [ ] Improve transition flow from library/home → detail and between series → season → episode levels

### Definition of done
- Details feel premium rather than utilitarian
- Resume/play choices are obvious
- Users can move through detail content fluidly with a remote

---

## Milestone 6 — Search, settings, sign-in, and support flows

### Goal
Make all supporting flows feel designed specifically for TV rather than simply functional.

### Search tasks
- [ ] Improve D-pad-first search flow
- [ ] Manage focus between search field, keyboard, and results more deliberately
- [ ] Restore query and result focus after returning from details
- [ ] Add TV-friendly loading / empty / no-results states
- [ ] Group result types where useful

### Settings tasks
- [ ] Rework settings into a TV-friendly settings hub
- [ ] Group settings into clear categories such as Account, Playback, Audio/Subtitles, Appearance, Diagnostics
- [ ] Improve focus and selected-state affordances for rows, toggles, and list options

### Sign-in / quick connect / connection tasks
- [ ] Improve visual hierarchy on server connection
- [ ] Improve quick connect readability from typical couch distance
- [ ] Verify polling, timeout, retry, and error states
- [ ] Preserve readable 10-foot layout at all times

### Definition of done
- Support flows feel intentional on a remote
- New users can sign in without confusion
- Settings are comfortable to browse on a TV

---

## Milestone 7 — TV playback overlay refinement

### Goal
Keep the playback engine stable, but rebuild playback chrome so it is truly remote-first.

### Corrective note from the older docs
The older modernization plan marked the player as done. Treat that as “dedicated TV player shell exists,” not “final polish is complete.”

### Tasks
- [ ] Keep playback internals as-is unless there is a specific bug requiring deeper work
- [ ] Build or refine a TV-first overlay with large focusable transport controls
- [ ] Add clearly focusable controls for play/pause, seek, timeline, subtitles, audio, speed, next episode, and more actions where supported
- [ ] Add an Up Next panel
- [ ] Make resume / continue progress obvious
- [ ] Ensure exiting playback restores focus to the launching item
- [ ] Ensure autoplay-next integrates properly with TV details and home rows
- [ ] Verify remote keys in playback on real devices

### Definition of done
- Playback controls feel designed for TV rather than adapted from touch UI
- Exiting playback never feels disorienting

---

## Milestone 8 — TV tests and device validation

### Goal
Protect the TV experience from regressions as the UI evolves.

### Tasks
- [ ] Add Compose UI tests for TV launch, sign-in, drawer nav, home → detail → back, library restore, and search restore
- [ ] Add focus-specific tests where practical
- [ ] Add tests for drawer/content handoff and edge navigation
- [ ] Validate on emulator and at least one real TV / Google TV device
- [ ] Validate on 1080p and 4K
- [ ] Validate with simple remotes and keyboard-enabled remotes where possible
- [ ] Capture a repeatable manual QA checklist and link it from this roadmap

### Definition of done
- TV regressions are caught earlier
- Focus/navigation breakage is less likely to ship unnoticed

---

## File-by-file implementation checklist

### `MainActivity.kt`
- [ ] Keep TV dispatch logic clear and minimal
- [ ] Remove mobile-only startup assumptions that affect TV
- [ ] Review intent/shortcut behavior on TV

### `TvJellyfinApp.kt`
- [ ] Harden drawer behavior
- [ ] Centralize route/focus handoff rules
- [ ] Improve drawer ↔ content focus transitions

### `TvNavGraph.kt`
- [ ] Replace thin route wrappers with richer, route-specific surfaces where needed
- [ ] Audit start-destination and auth transitions
- [ ] Add explicit focus restore hooks per route

### `TvFocusManager.kt`
- [ ] Expand from row memory into full route-level focus restoration
- [ ] Add debug logging hooks if useful
- [ ] Consider test coverage for restoration behavior

### `TvKeyboardHandler.kt`
- [ ] Separate global shortcuts from per-screen key handling
- [ ] Audit against real remote inputs

### `TvHomeScreen.kt`
- [ ] Upgrade featured selection logic
- [ ] Refine row strategy
- [ ] Improve placeholders and backdrop transitions
- [ ] Reduce jank during rapid focus changes

### `TvLibraryScreen.kt`
- [ ] Add TV-native filter/sort UX
- [ ] Improve route-specific presentation
- [ ] Improve pagination and restore behavior

### `TvItemDetailScreen.kt`
- [ ] Prioritize action row and related rails
- [ ] Improve resume/play behavior
- [ ] Improve season/episode drill-down UX

### `TvSearchScreen.kt`
- [ ] Improve D-pad-first flow and restoration
- [ ] Improve grouping and empty states

### `TvSettingsScreen.kt`
- [ ] Rework into a clearer TV settings hub
- [ ] Improve affordances for toggles and list rows

### `TvServerConnectionScreen.kt`
- [ ] Improve validation, loading, and error hierarchy

### `TvQuickConnectScreen.kt`
- [ ] Verify full polling/retry/error lifecycle
- [ ] Preserve readable 10-foot layout at all times

---

## Suggested sprint order

### Sprint 1
- [ ] Milestone 1 — focus contracts and restoration
- [ ] Milestone 2 — architecture cleanup
- [ ] Home screen focus/restore fixes

### Sprint 2
- [ ] Home screen polish
- [ ] Library screen sort/filter UX
- [ ] Library restore/pagination polish

### Sprint 3
- [ ] Detail screen redesign completion
- [ ] Search polish
- [ ] Settings/sign-in polish

### Sprint 4
- [ ] TV playback overlay refinement
- [ ] TV UI tests
- [ ] Device validation pass

---

## Definition of done: “TV is no longer wonky”

The TV app can be considered stable when:

- launching on TV feels intentional
- the drawer and content hand off focus correctly every time
- backing out of details returns to the exact item the user came from
- Home feels cinematic and stable
- library browsing is predictable and remote-friendly
- detail pages feel like a streaming app, not a utility screen
- search and sign-in are comfortable with only a remote
- playback controls are clearly TV-first
- emulator and real-device QA both pass a shared checklist

---

## Best immediate next task

If only one task should be tackled first, do this:

**Run a focused TV navigation + focus restoration cleanup pass across `TvJellyfinApp`, `TvNavGraph`, `TvHomeScreen`, and `TvLibraryScreen`.**

That should remove a large share of the current “wonky” feeling before deeper visual redesign work begins.
