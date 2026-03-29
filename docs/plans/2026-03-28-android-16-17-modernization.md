# Android 16 & 17 Modernization Plan

**Date**: March 28, 2026
**Status**: Draft / Proposed
**Target SDK**: 35 (Current) -> 36 (Android 16) -> 37 (Android 17 Beta 3)

## Overview
As of March 2026, Android 16 is the stable major release, and Android 17 is in Beta 3 (Platform Stability). This plan outlines the transition to these new APIs to ensure Cinefin remains a top-tier, modern media client.

---

## 🟢 Phase 1: Android 16 "Baklava" Stabilization (Q2 2026)

Android 16 focused on user experience, professional media, and richer tactile feedback.

### 1. Richer Haptics (Consistent Tactile Feel)
*   **Goal**: Replace basic haptics with frequency-aware haptic curves.
*   **API**: `VibrationEffect.Composition` with new amplitude/frequency modulation.
*   **Application**: Update `ExpressiveHaptics.kt` to use these curves for "Play/Pause" (sharp tick) and "Seeking" (variable frequency hum).

### 2. Embedded Photo Picker (Profile Customization)
*   **Goal**: Integrate the photo picker for user profile images without leaving the app context.
*   **API**: `MediaStore.ACTION_PICK_IMAGES` with embedded view support.

### 3. Predictive Back (Refined Navigation)
*   **Goal**: Ensure seamless "back-to-home" and "back-to-previous-screen" animations.
*   **Action**: Audit all `BackHandler` usages in Compose to ensure they don't block system-managed animations.

---

## 🔵 Phase 2: Android 17 "Vanilla Ice Cream" Early Adoption (Q3 2026)

Android 17 Beta 3 is now feature-complete. We will target the new continuity and media APIs.

### 1. Handoff API (Cross-Device Continuity)
*   **Goal**: Allow users to start a movie on their phone and instantly "handoff" the playback state (timestamp, subtitle track) to an Android TV or Tablet.
*   **API**: `CompanionDeviceManager` state transfer.
*   **Integration**: Hook into `VideoPlayerPlaybackManager.kt` to sync state via the new Handoff system.

### 2. Independent Assistant Volume
*   **Goal**: Ensure our AI Assistant doesn't drown out the movie audio.
*   **Action**: Categorize AI Assistant audio streams correctly to respect the new system-level independent volume slider.

### 3. NPU-Accelerated AI (On-Device Performance)
*   **Goal**: Move AI summaries and recommendations to the NPU for zero latency and privacy.
*   **API**: Declare `FEATURE_NEURAL_PROCESSING_UNIT` and optimize ML Kit / Gemini Nano calls.

---

## 🔴 Phase 3: SDK & Tooling Update

### 1. Version Bump
*   Update `compileSdk` to 36/37.
*   Update `targetSdk` to 36.

### 2. Media3 Update
*   Upgrade to Media3 1.10.0+ (expected for Android 17 compatibility) to support the new XHE-AAC encoders and professional color adjustments.

---

## 📊 Success Metrics
*   **Continuity**: 100% success rate for playback handoff between phone and tablet.
*   **Performance**: 30% faster AI response times using on-device NPU.
*   **Delight**: Improved tactile feedback satisfaction scores.

---

## Next Steps
1.  [ ] Prototype Android 16 Rich Haptics in `ExpressiveHaptics.kt`.
2.  [ ] Audit `VideoPlayerViewModel` for Handoff API compatibility.
3.  [ ] Update `AndroidManifest.xml` with NPU declarations.
