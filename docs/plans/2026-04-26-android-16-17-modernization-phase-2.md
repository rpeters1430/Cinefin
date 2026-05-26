# Implementation Plan: Android 16 & 17 Modernization (Phase 2)

> [!IMPORTANT]
> This plan has been consolidated into `/tmp/workspace/rpeters1430/Cinefin/docs/plans/ANDROID_16_17_READINESS.md`.
> Use that file as the single checklist to track Android 16/17 work.
> Keep this file for historical context only.

## Objective
Adopt the latest platform features from Android 16 (Baklava) and ensure compliance with Android 17 (Vanilla Ice Cream) Beta 4. This phase focuses on user engagement via Live Update notifications, rich haptics, and system stability.

## Target Features

### 1. Android 16: Progress-Centric Notifications (`ProgressStyle`)
**Goal**: Replace standard progress bars in notifications with the new `Notification.ProgressStyle` for offline downloads and transcoding.
- **File**: `app/src/main/java/com/rpeters/jellyfin/data/worker/OfflineDownloadWorker.kt`
- **Action**: Use `NotificationCompat.ProgressStyle` (via Core KTX 1.19+) or platform `Notification.ProgressStyle` on API 36+.

### 2. Android 16: Standardized Haptic Curves
**Goal**: Enhance `ExpressiveHaptics.kt` to use the new standardized amplitude and frequency curves for a premium tactile feel during seeking and UI interactions.
- **File**: `app/src/main/java/com/rpeters/jellyfin/ui/utils/ExpressiveHaptics.kt`
- **Action**: Implement `VibrationEffect.Composition` enhancements using the new API 36 curve definitions.

### 3. Android 17: Background Audio Hardening
**Goal**: Ensure `AudioService` remains resilient under Android 17's stricter background constraints.
- **File**: `app/src/main/java/com/rpeters/jellyfin/ui/player/audio/AudioService.kt`
- **Action**: Audit lifecycle transitions, ensure `startForeground` is called proactively, and handle potential audio focus request failures gracefully.

### 4. Android 17: Mandatory Large Screen Adaptivity
**Goal**: Comply with Android 17's requirement that apps on screens ≥600dp cannot opt-out of resizability.
- **File**: `app/src/main/AndroidManifest.xml`
- **Action**: Audit activities for fixed orientations and ensure `resizeableActivity="true"` is handled correctly by the UI.

---

## Step-by-Step Implementation

### Step 1: Modernize Download Notifications (Android 16)
1.  Check `OfflineDownloadWorker.kt`.
2.  Implement `createModernForegroundInfo` using `Notification.ProgressStyle`.
3.  Add support for "Live Update" categories if supported by the system.

### Step 2: Implement Rich Haptics Curves (Android 16)
1.  Update `ExpressiveHaptics.kt`.
2.  Add a `seekTick()` and `limitReached()` effect using frequency-aware curves.
3.  Integrate into `VideoPlayerScreen` seek bar.

### Step 3: Android 17 Audio Compliance
1.  Update `AudioService.kt`.
2.  Refine `MediaSession` callback handling for restricted states.
3.  Verify foreground service type is correctly set and maintained.

### Step 4: Manifest & Layout Audit (Android 17)
1.  Update `AndroidManifest.xml` activity declarations.
2.  Ensure `VideoPlayerActivity` handles orientation changes dynamically without restart if not already doing so.

---

## Verification Plan
1.  **Unit Tests**: Verify `HandoffManager` and `OfflineDownloadWorker` logic.
2.  **Manual Check**: Run on Android 16/17 emulators (if available) or simulate behavior via logs.
3.  **Lint**: Run `./gradlew lintDebug` to check for new platform warnings.
