# Navigation 3 suitability evaluation (Jan 2026)

This repo currently uses **Navigation Compose** (`androidx.navigation:navigation-compose`) with string routes, plus a separate TV navigation graph.

This doc evaluates whether migrating to **AndroidX Navigation 3** (“Navigation 3 component”) makes sense for this app right now.

## Current navigation architecture (what exists today)

- **Phone/tablet** root uses a single `NavHost` with multiple feature graphs:
  - `JellyfinApp` creates the controller via `rememberNavController()` and hosts `JellyfinNavGraph`.
  - `JellyfinNavGraph` installs: `authNavGraph`, `homeLibraryNavGraph`, `mediaNavGraph`, `profileNavGraph`, `detailNavGraph`.
- **Routes are string-based** via `sealed class Screen(val route: String)` and `createRoute(...)` helpers for parameterized routes.
- **Bottom navigation** is a Material 3 `NavigationBar` that navigates to string routes with `saveState/restoreState`.
- **Auth gating / redirects** are implemented as `LaunchedEffect` checks that navigate to login/home based on connection state.
- **App shortcuts** pass a raw destination string which is validated against a regex + a whitelist of base route strings in `MainActivity`.
- **Android TV** has a completely separate `TvNavGraph` using Navigation Compose as well, with its own route constants and arguments.

Code pointers:
- Phone root navigation setup: `app/src/main/java/com/rpeters/jellyfin/ui/JellyfinApp.kt`
- Phone graph assembly: `app/src/main/java/com/rpeters/jellyfin/ui/navigation/NavGraph.kt`
- Route definitions: `app/src/main/java/com/rpeters/jellyfin/ui/navigation/NavRoutes.kt`
- TV navigation: `app/src/main/java/com/rpeters/jellyfin/ui/tv/TvNavGraph.kt`
- Shortcut route whitelist: `app/src/main/java/com/rpeters/jellyfin/MainActivity.kt`

## What Navigation 3 would (potentially) improve

Navigation 3’s main promise is a more **Compose-first** navigation model (where the back stack is more directly represented as Compose state) and a path toward:

- **More explicit back stack modeling** (less hidden behavior than `NavController`/`NavBackStackEntry`).
- **Better composability** for advanced layouts (e.g., multi-pane, list-detail, split navigation) without forcing everything through a `NavHost` DSL.
- Potentially **cleaner testability** (depending on how you model the back stack and destinations).

If the app were about to undertake a major navigation redesign (e.g., a fully adaptive multi-pane experience with custom back stack rules), Navigation 3 could be a good foundation for that redesign.

## What makes Navigation 3 a poor fit *right now* for this repo

Based on the current codebase shape, a migration would be high-cost and high-risk without delivering clear user-visible wins:

- **Current needs are already met** by Navigation Compose:
  - Many screens, deep navigation into details, multiple argument types, and separate TV graph.
  - Existing route-string strategy is used outside of navigation (shortcuts whitelist), so it’s not “just a nav graph swap”.
- **Large migration surface area**:
  - Every `composable(route = ...)` destination and every `Screen.*.createRoute(...)` call site would need rework.
  - Shortcut validation (`ALLOWED_SHORTCUT_ROUTES`) and any implicit assumptions about route formats would need redesign.
  - TV would either remain on Navigation Compose (two systems) or also migrate (even more work).
- **The app already depends on Material 3 adaptive navigation libraries** (`material3-adaptive-navigation-suite`), but the phone UI currently uses a fixed `NavigationBar`.
  - The most obvious *near-term* UX win is actually **adopting adaptive navigation UI** (bar/rail/drawer) rather than swapping the underlying navigation engine.

## Recommendation

### Recommendation: **Do not migrate to Navigation 3 right now**

For this repo, Navigation 3 is not currently the highest-leverage change. Navigation Compose is stable and already integrated across:

- phone/tablet navigation
- TV navigation
- shortcut route validation
- existing tests (`androidx.navigation:navigation-testing`)

The migration cost is substantial, and the likely net benefit (given the app’s current architecture) is low unless you’re explicitly planning a larger navigation overhaul.

## What *does* make sense instead (incremental improvements)

These are lower-risk changes that align with the “Navigation 3 motivation” (cleaner, more adaptive navigation) while keeping the current Navigation Compose foundation:

- **Adopt adaptive navigation UI for larger screens**:
  - You already ship Material3 adaptive navigation dependencies.
  - Consider moving from a fixed `BottomNavBar` to an adaptive scaffold (bar/rail/drawer) for tablets/desktop-like widths.
  - You also already have accessible rail components (`AccessibleNavigationRail`), which suggests the intent is there.

- **Consider true per-tab back stacks (if desired)**:
  - Today: a single `NavController` + `saveState/restoreState` on bottom nav.
  - If you want separate stacks per tab (Home/Library/Search/Favorites/Profile), that can be done incrementally with established Navigation Compose patterns.

- **Reduce string-route coupling**:
  - Centralize “main destinations” checks (bottom nav visibility, shortcut whitelist) so route changes don’t require editing multiple hard-coded lists.
  - Consider type-safe routing patterns available within Navigation Compose (where appropriate) to reduce `"{id}"` string handling.

## When to revisit Navigation 3

Re-evaluate Navigation 3 if any of these become true:

- You’re committing to a **major adaptive multi-pane redesign** where the app needs a custom, explicit back stack model.
- Navigation Compose becomes a blocker (e.g., you need back stack behaviors that are hard to express/maintain in the current DSL).
- Navigation 3 reaches feature maturity and you want to consolidate phone + TV navigation into a single, consistent model.

