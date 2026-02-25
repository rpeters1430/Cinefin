# Predictive Back Animations Plan (Android 15+ UX, Android 13+ API)

## Why this doc
This app already opts into the platform back callback (`android:enableOnBackInvokedCallback="true"` in `AndroidManifest.xml`), but most navigation exits still use direct `navController.popBackStack()` calls and screen-level back buttons. This document defines how to add predictive back animations in a controlled way.

## Version clarification
- Predictive back was introduced in Android 13 (API 33, August 2022).
- Android 14 expanded and stabilized behavior for more apps.
- Android 15 continues improving consistency and transitions, so it is a good target for polish.

## Current app state
- Main app is Compose + Navigation Compose (`navigation-compose` 2.9.7).
- Manifest already has back-invoked opt-in enabled.
- `MainActivity` is `FragmentActivity` hosting Compose.
- Most navigation stacks are in:
  - `ui/navigation/HomeLibraryNavGraph.kt`
  - `ui/navigation/MediaNavGraph.kt`
  - `ui/navigation/DetailNavGraph.kt`
  - `ui/navigation/ProfileNavGraph.kt`
- Fullscreen player uses separate `VideoPlayerActivity` with custom back behavior.

## Goals
- Show system predictive back animation for gesture back where possible.
- Avoid abrupt pops when leaving detail/media screens.
- Keep behavior consistent between gesture back and in-UI back buttons.
- Preserve existing business logic and routing.

## Non-goals
- No redesign of route hierarchy.
- No behavior changes for TV DPAD back (focus on phone/tablet touch navigation).
- No migration to fragment-based nav.

## Implementation strategy

### Phase 1: Baseline compliance and parity
1. Keep using `NavHost` as the single source of back stack truth.
2. Ensure custom UI back buttons call the same action as system back for each destination.
3. Audit screens that intercept back (dialogs/sheets/player overlays) and remove unnecessary interception where possible.

### Phase 2: Predictive-back-aware handlers for custom surfaces
Use Compose predictive back APIs only where you must intercept back:
- Fullscreen overlays
- Modal workflows
- Cases where progress-based visual response is needed

For these, use `PredictiveBackHandler` (from `androidx.activity:activity-compose`) and:
1. Collect back progress.
2. Drive lightweight visual transforms (scale, translation, scrim alpha).
3. On completion, execute the same final action (`popBackStack` / dismiss).
4. On cancellation, animate state back to rest.

Do **not** wrap every screen in a custom predictive handler. Let NavHost/system handle normal destinations.

### Phase 3: Navigation transitions tuned for predictive feel
Define consistent enter/exit/pop transitions at NavHost destination boundaries so they visually align with back progress:
1. Detail -> list pop should use directional reverse motion.
2. Modal-style screens should fade/scale out on pop.
3. Keep durations/easing short and consistent (avoid heavy spring motion on back).

If route-level transition APIs are used, centralize them in nav graph builders to prevent drift.

### Phase 4: Video player activity back modernization
`VideoPlayerActivity` currently overrides `onBackPressed`. Migrate to modern dispatcher/back-invoked handling:
1. Keep existing overlay-close-first behavior.
2. Use back callbacks compatible with predictive flow.
3. Ensure final finish transition matches app style.

## Suggested technical tasks
1. Create `ui/navigation/BackNavigationPolicy.kt`:
   - Single place to define destination groups (root tabs, detail, modal).
   - Shared helper for back action parity.
2. Add `ui/navigation/PredictiveBackTransitions.kt`:
   - Reusable animated specs for back-driven surfaces.
3. Introduce targeted `PredictiveBackHandler` only in screens that currently override default back semantics.
4. Update `VideoPlayerActivity` back handling to modern callback APIs.
5. Add logging tags for back lifecycle:
   - Start, progress, complete, cancel (debug-only).

## Testing plan

### Manual (required)
Phone/tablet on Android 15 and Android 14:
1. Library -> TV Shows -> detail -> swipe back slowly and cancel.
2. Complete swipe back from detail to list.
3. Open modal/bottom sheet and test predictive gesture.
4. Enter/exit player and verify back path.
5. Validate in-UI back button matches system back destination.

### Automated (recommended)
1. Navigation tests verifying destination after back from key routes.
2. UI tests for overlay dismissal precedence (overlay closes before route pop).
3. Regression tests for player back behavior.

## Rollout and safety
1. Gate predictive custom handlers behind a feature flag (`predictive_back_expressive`).
2. Roll out in stages:
   - Internal debug
   - Beta
   - Production
3. Track:
   - Back-related crash rate
   - Navigation cancellation anomalies
   - User complaints around accidental exits

## Risks
- Overusing custom back handlers can fight NavHost and degrade UX.
- Heavy animations on back progress can cause jank.
- Separate player activity can diverge from app navigation feel if not aligned.

## Definition of done
- Gesture back shows smooth predictive transitions on key paths.
- Cancelled back gestures fully restore prior state.
- UI back button and system back are destination-equivalent.
- No regressions in player or modal back behavior.
- Verified on Android 14 and Android 15 devices.

