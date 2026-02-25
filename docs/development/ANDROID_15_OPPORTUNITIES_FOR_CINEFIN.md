# Android 15 Opportunities for Cinefin (Target SDK 35)

## Scope
This document summarizes Android 15 changes most relevant to Cinefin (streaming, downloads, cast/screen projection, Compose navigation), and what to implement next.

## Current baseline in this repo
- `targetSdk = 35` in `app/build.gradle.kts`.
- `android:enableOnBackInvokedCallback="true"` already set in `AndroidManifest.xml`.
- `MainActivity` already calls `enableEdgeToEdge()`.
- App heavily uses Compose + Navigation Compose.

## What is new in Android 15 (relevant to Cinefin)
1. Edge-to-edge enforcement for apps targeting API 35.
2. Predictive back system animations enabled by default for opted-in apps.
3. Foreground service policy changes (timeouts/new mediaProcessing type/boot restrictions).
4. Security hardening (restricted TLS 1.0/1.1, safer intents, stricter background activity launches with PendingIntent changes).
5. Private space behavior changes.
6. 16 KB page size support now required by Play for Android 15+ targets (from Nov 1, 2025).
7. MediaProjection UX changes: status bar chip and auto-stop on lock (Android 15 QPR1+).
8. Screen recording detection callback APIs.

## Recommended implementations for Cinefin

### P0: Complete predictive back migration (high UX impact)
- Why: Android 15 defaults to predictive animations for migrated apps.
- What:
  - Use supported back APIs everywhere.
  - Add `PredictiveBackHandler` only for custom surfaces (sheets/overlays/player-like flows), not globally.
  - Keep root `MainActivity` from intercepting back, otherwise system back-to-home animation is lost.
- Repo tie-in:
  - See `docs/development/PREDICTIVE_BACK_ANIMATIONS_PLAN.md`.
  - Navigation files: `ui/navigation/*NavGraph.kt`.

### P0: Edge-to-edge audit pass for all major screens
- Why: API 35 enforcement can hide tappable UI behind system bars.
- What:
  - Audit top/bottom bars, FABs, mini player, and bottom nav overlap.
  - Standardize insets strategy (`Scaffold` padding or explicit insets modifiers per screen).
- Repo tie-in:
  - `MainActivity.kt`, `ui/JellyfinApp.kt`, immersive screen set under `ui/screens/`.

### P0: 16 KB page-size release compliance gate
- Why: Play requirement applies to updates targeting Android 15+.
- What:
  - Verify all native/transitive `.so` libs are 16 KB compatible.
  - Add CI check for page-size alignment for release artifacts.
- Repo tie-in:
  - App depends on media/native stacks (`androidx.media3`, cast, ffmpeg decoder).

### P1: Harden PendingIntent + intent safety paths
- Why: Android 15 tightens background activity launch behavior.
- What:
  - Audit all `PendingIntent` creators (notifications, player controls, download actions).
  - In debug builds, enable `StrictMode.detectUnsafeIntentLaunch()`.
- Repo tie-in:
  - `ui/player/PipActionReceiver.kt`, `data/worker/DownloadActionReceiver.kt`, notification surfaces.

### P1: Foreground service policy compliance for downloads/sync/media processing
- Why: Android 15 adds stricter rules and mediaProcessing time limits.
- What:
  - Confirm service types match purpose.
  - Ensure no invalid `BOOT_COMPLETED` FGS starts.
  - Prefer WorkManager where task is deferrable.
- Repo tie-in:
  - `data/worker/*`, `ui/player/audio/AudioService.kt`, manifest service declarations.

### P1: Private space resilience
- Why: App copy in private space stops fully when private space is locked.
- What:
  - Ensure graceful recovery when app/process resumes.
  - Ensure no assumptions that secondary profile == work profile.
  - Provide user messaging for delayed notifications/sync behavior in private space context.

### P2: Screen-recording-aware privacy overlays
- Why: Android 15 adds recording visibility callbacks.
- What:
  - Detect recording visibility for sensitive screens (credentials, server URLs, maybe account/settings).
  - Show subtle "Screen is being recorded" banner or blur sensitive rows.
- Repo tie-in:
  - `ServerConnectionScreen`, auth/profile/settings flows.

### P2: MediaProjection stop handling validation
- Why: QPR1 adds status chip + lock-screen auto-stop.
- What:
  - Ensure projection resources are released on callback `onStop()`.
  - Add QA checklist for stop-via-chip and stop-via-lockscreen paths.
- Repo tie-in:
  - Cast/projection related code under `ui/player/cast/` and any MediaProjection usage.

### P3: Notification channel vibration semantics
- Why: Android 15 supports richer channel vibration patterns.
- What:
  - Differentiate download completion vs failure vs playback alerts for accessibility.

## Suggested rollout order
1. Predictive back + edge-to-edge audit.
2. 16 KB page-size compliance and CI gate.
3. PendingIntent/intent hardening + foreground service policy audit.
4. Private space + screen recording + projection handling improvements.

## QA checklist (Android 15 devices)
- Back gesture preview works on key flows (home -> browse -> detail, detail -> player -> back).
- No controls hidden behind status/nav bars.
- Downloads/audio playback survive lifecycle edges without policy violations.
- No unsafe intent launch warnings in debug StrictMode.
- App behavior acceptable when installed in private space and then locked/unlocked.
- Release artifact verified for 16 KB page compatibility.

## Notes
- Predictive back is not new to Android 15 (introduced in Android 13), but Android 15 makes system animations default for opted-in apps, so migration quality now directly impacts UX.
