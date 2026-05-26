# Cinefin — Android 16 & 17 Readiness Checklist

**Generated**: 2026-05-26
**Current state**: `compileSdk = 37`, `targetSdk = 37`, `minSdk = 30`, NDK `29.0.14206865`, AGP `9.2.1`, Kotlin `2.3.21`
**Scope**: Behavior changes, deprecations, and new platform features that apply once Cinefin runs (or targets) Android 16 (API 36, "Baklava") and Android 17 (API 37).

This document supersedes the open items in:
- `docs/plans/2026-03-28-android-16-17-modernization.md`
- `docs/plans/2026-04-26-android-16-17-modernization-phase-2.md`

Items already complete in the codebase are listed at the bottom in **§9 — Already Done** so we don't redo them.

---

## 1. Hard Compatibility Items (must-pass before shipping to SDK 37)

These will silently break the app or get flagged by the Play Console review for new Android 17 uploads. None are theoretical — each one maps to a specific behavior change or to a Play policy gate.

### 1.1 — 16 KB page size for native libraries
Android 15+ devices ship with 16 KB memory pages and Play requires that new apps and updates be 16 KB compatible from **November 1, 2025**. We have one native dependency we don't build ourselves:

- `org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1` — bundles `libffmpegJNI.so`
- `androidx.graphics:graphics-path` — bundles `libandroidx.graphics.path.so`
- DataStore — bundles `libdatastore_shared_counter.so`

**Action**:
- [ ] Add a CI check that runs `zipalign -c -P 16 -v 4 app-release.apk` (or `apksigner verify --print-page-size`) and fails if any `.so` is not 16 KB aligned.
- [ ] Verify `libffmpegJNI.so` version 1.9.0+1 is 16 KB aligned. If not, bump to whichever release of `jellyfin-media3-ffmpeg-decoder` is built with NDK r28+. (Our own NDK `29.0.14206865` is fine.)
- [ ] Test a release APK on an Android 16 emulator with a 16 KB system image (`system-images;android-36;google_apis;arm64-v8a` — the `_16k_` variant).

### 1.2 — Edge-to-edge enforcement (Android 16, targetSdk 36+)
On SDK 36+, apps can no longer opt out of edge-to-edge. `Window.setDecorFitsSystemWindows(false)` is now a no-op-or-crash; the system *always* draws under system bars. We already call `enableEdgeToEdge()` in `MainActivity` and use `WindowInsets.statusBars` / `WindowInsets.navigationBars` in Compose. Remaining audit items:

- [ ] **`VideoPlayerActivity`** — `Theme.JellyfinAndroid.VideoPlayer` correctly drops the deprecated `windowFullscreen` flags (good), but verify the runtime full-screen path in `setupFullScreenMode()` uses `WindowInsetsControllerCompat.hide(systemBars())` with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, not the deprecated `systemUiVisibility` flags.
- [ ] **Bottom sheets, dialogs, snackbars** — anywhere we wrap a `Scaffold` with a custom bottom bar (`JellyfinApp.kt` lines ~334, ~369, ~407 use `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` manually), confirm they don't double-pad once the system enforces edge-to-edge. Prefer `Modifier.safeDrawingPadding()` or `Scaffold`'s built-in `contentWindowInsets` over manual `WindowInsets.navigationBars` reads.
- [ ] **TV surface (`TvJellyfinApp`)** — TV runs without system bars but the same code path executes. Sanity check that the manual `navigationBars` padding becomes zero on Leanback rather than introducing a phantom gap.
- [ ] **Status bar / nav bar color** — these XML attrs are deprecated at SDK 35+; we don't set them in `themes.xml`, but double-check no inherited style does.

### 1.3 — Adaptive / large screen mandate (Android 16)
Apps on screens **≥600dp** can no longer opt out of resizability or lock orientation, regardless of `android:resizeableActivity` and `android:screenOrientation`. We're well-positioned (`resizeableActivity="true"`, `MainActivity` uses `screenOrientation="user"`), but the player needs work:

- [ ] **`VideoPlayerActivity`** sets `requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR` at runtime (line 329). On ≥600dp devices on Android 16, the system will *ignore* this. Confirm the player still renders correctly when the system forces a re-layout mid-playback (configChanges handles orientation, but verify `screenLayout`, `density`, and `smallestScreenSize` are also handled — they are, per the manifest).
- [ ] Add a Robolectric or instrumentation test that resizes the activity at ≥600dp width and asserts the ExoPlayer surface survives without releasing the player.
- [ ] Audit `configChanges` on `VideoPlayerActivity`. Missing from the list: `uiMode` (dark/light switch), `fontScale`, `layoutDirection`. Decide per-attribute whether to handle or to recreate.

### 1.4 — Foreground service type strictness (Android 14+, tightened in 15/16)
We use two FGS types: `mediaPlayback` (`AudioService`) and `dataSync` (download worker). Already mostly correct, but:

- [ ] Confirm `AudioService.onCreate()` calls `startForeground(...)` **within 5 seconds** of the service starting in every code path, including the rebind/restart after `onTaskRemoved`. The Media3 `MediaSessionService` base class handles this, but our custom `notificationProvider` swap could regress it — add a unit test that asserts `startForeground` is called before any awaitable suspension.
- [ ] Long-running downloads on Android 15+ hit the **6-hour cumulative dataSync FGS quota**. `OfflineDownloadWorker` needs a fallback: when a single download exceeds 6h (e.g. transcoding a 4K Blu-ray remux on a slow server), switch to `setExpedited` chunks or chain `OneTimeWorkRequest`s. Today the worker can be killed silently mid-download. *Tracked in IMPROVEMENT_PLAN §2.1; promote to Android-16-blocker.*
- [ ] Declare `FOREGROUND_SERVICE_MEDIA_PROCESSING` (new in API 36) for any future server-side transcode-monitoring service. Not used today; leave as a note.

### 1.5 — `ACCESS_LOCAL_NETWORK` runtime grant on SDK 37
Already implemented in `ServerConnectionScreen.kt` (lines 121–148), but the gating is fragile:

- [x] The launcher fires from `LaunchedEffect(Unit)` on first composition. Denial UX now includes inline rationale text plus a "Grant in Settings" deep-link, and explicitly states manual URL entry still works (implemented on 2026-05-26 in `ServerConnectionScreen`).
- [ ] Permission denial should not block manual server URL entry. Confirm `onRestartDiscovery` is only called when granted, and the manual URL flow still works without it.
- [ ] Add a robolectric test for `Build.VERSION.SDK_INT >= 37` permission denial → manual entry still functional.

### 1.6 — Certificate Transparency enforcement
`network_security_config.xml` notes "Certificate Transparency (enforced at API 37+) applies automatically to all HTTPS connections." This is correct, but:

- [ ] User-installed CAs (TOFU pinning flow) **bypass CT** in the system trust store. Our config currently trusts both `<certificates src="system"/>` and `<certificates src="user"/>` in the base-config. Verify on a real Android 17 build that the certificate-pinning TOFU flow still works against a self-signed Jellyfin server. CT only enforces against publicly-trusted CAs, so user-CA pinning should still work — but **test it**.
- [ ] If we ever add a "known servers" list as hinted in the NSC comment, give each one its own `<domain-config>` with `cleartextTrafficPermitted="false"`.

### 1.7 — `usesCleartextTraffic` deprecation (SDK 37)
We're already doing this right — cleartext is controlled via NSC, not the manifest attribute. The NSC comment ("`usesCleartextTraffic` is deprecated at API 37") is correct. **No action required**, just don't add the attribute later.

---

## 2. Behavior Changes That Apply to All Apps (regardless of targetSdk)

These changes hit when the *device* is Android 16/17, even if `targetSdk` is lower. We've already opted in to SDK 37, so they apply unconditionally.

### 2.1 — JobScheduler quota tightening (Android 15+)
Regular jobs get a stricter execution-runtime quota. WorkManager handles this transparently, but expedited jobs (`setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)`) silently demote. Audit:

- [ ] `OfflineDownloadWorker` — uses ForegroundInfo (FGS), so quota doesn't apply directly, but any chained workers may. Grep for `setExpedited` and document expected behavior.

### 2.2 — Notification permission UX (Android 13+, stricter prompt suppression on 16)
Android 16 will suppress repeated rejected prompts more aggressively. We currently request from `DownloadButton` and `ImmersiveTVEpisodeDetailScreen`:

- [ ] Add `shouldShowRequestPermissionRationale` check before launching. Show our own pre-prompt explaining why downloads need a notification.
- [ ] After two denials, treat notifications as permanently denied for this session and surface a "Notifications disabled — open Settings" affordance next to the download button instead of re-prompting.

### 2.3 — `getParcelableExtra(name, Class)` migration
Already done in `AudioService.kt`. One more spot — `MainActivity.handleHandoffIntent` reads several string and long extras, which are fine. **No further action.**

---

## 3. Android 16 Feature Adoption (opportunity, not blocker)

### 3.1 — `Notification.ProgressStyle` (Android 16, NotificationCompat 1.13+)
**Partially done.** `OfflineDownloadWorker` already wraps `NotificationCompat.ProgressStyle` behind `Build.VERSION.SDK_INT >= 36`. Polish remaining:

- [ ] Add segment styling — break the progress bar into "downloading" (blue) and "transcoding" (orange) segments using `ProgressStyle.Segment` for clearer UX when transcoding precedes download.
- [ ] Add `setStyledByProgress(false)` if we want the icon to stay our brand color rather than the progress tint.
- [ ] Set `Notification.CATEGORY_PROGRESS` (already done) and consider the new `Notification.LIVE_UPDATE` category once it's promoted out of beta on API 36+ for the persistent ongoing-download notification.

### 3.2 — Richer haptics (`VibrationEffect.Composition` primitives)
**Done.** `ExpressiveHaptics.kt` already uses `PRIMITIVE_QUICK_RISE`, `PRIMITIVE_QUICK_FALL`, `PRIMITIVE_THUD` with `Build.VERSION.SDK_INT >= 36` guards. Polish only:

- [ ] Replace the hardcoded `>= 36` numeric literal with `>= Build.VERSION_CODES.BAKLAVA` once the constant lands in our compileSdk. (It does at SDK 36 — use `Build.VERSION_CODES.BAKLAVA`.)
- [ ] Add a per-device capability check via `vibrator.areAllPrimitivesSupported(PRIMITIVE_QUICK_RISE, ...)`. Some OEMs report API 36 but ship a basic vibrator that no-ops the composition. Fall back to legacy haptics when unsupported.
- [ ] Wire `seekTick()` into the seek bar in `ExpressiveVideoControls.kt` (the plan in `2026-04-26-...-phase-2.md` Step 2.3 hasn't been done yet).

### 3.3 — Embedded Photo Picker (Android 16)
**Not done.** Plan called for profile-image picker integration. Currently we don't have a profile-image upload flow at all. Defer — when we add it, use `ActivityResultContracts.PickVisualMedia()` with embedded preview on API 36+.

### 3.4 — Frame-rate hints API
Android 15+ exposes `Surface.setFrameRate()` to declare the desired frame rate to the display compositor. The ExoPlayer/Media3 1.10+ codepath does this for us when video frame rate metadata is available. **Verify** by checking `VideoPlayerScreen` doesn't override the surface frame rate, then no action.

### 3.5 — Predictive Back Compose integration
We have `android:enableOnBackInvokedCallback="true"` ✓ but **zero `BackHandler` or `PredictiveBackHandler` usages** anywhere in `app/src/main`. That means:

- [ ] Custom back handling in nested screens (drawers, detail screens, video controls overlay) currently relies entirely on the OS-level dispatcher. That's fine in most places, but the player's full-screen controls overlay should be dismissible via predictive back — wire up `PredictiveBackHandler` to consume the back swipe and fade the controls instead of exiting the player on the first swipe.
- [ ] Navigation Compose 2.10+ has its own predictive-back animation support; verify `JellyfinNavGraph` uses the `composable(..., enterTransition = ...)` API and not the deprecated manual transition spec, so back swipes get the system animation.

---

## 4. Android 17 Feature Adoption

### 4.1 — Cross-device Handoff (`CompanionDeviceManager` state transfer)
**Stub only.** `HandoffManager.kt` has the signing/verification flow and an `updateAndroid17Handoff` method that just logs. The real Android 17 API (Beta 3+) for state transfer was being finalized as of the original plan. Action when stable:

- [ ] Replace the placeholder body of `updateAndroid17Handoff` with the real `CompanionDeviceManager.transferState(...)` call once the API ships. Until then, the HMAC-signed broadcast on `>= 34` is the fallback path and works.
- [ ] Add a Settings toggle to enable/disable handoff broadcasting (privacy: handoff exposes what the user is watching to nearby paired devices).
- [ ] Wire `HandoffManager.startBroadcasting` / `updatePosition` / `stopBroadcasting` calls into `VideoPlayerPlaybackManager` lifecycle (per Phase 1 §1 in `2026-03-28-...`).

### 4.2 — Independent Assistant Volume stream
Android 17 splits the Assistant audio stream from media. AI Assistant TTS or audio summaries should use `AudioAttributes.USAGE_ASSISTANT` (or `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` if appropriate), not `USAGE_MEDIA`. Today `AudioService` correctly uses `USAGE_MEDIA` for music. For the AI Assistant:

- [ ] Audit `firebase-ai` and ML Kit `genai-prompt` code paths. If they play any audio (TTS responses), set `AudioAttributes.Builder().setUsage(USAGE_ASSISTANT)`. If they only return text, no action.

### 4.3 — NPU declarations for on-device AI
The plan mentions declaring `FEATURE_NEURAL_PROCESSING_UNIT` for hardware-accelerated ML Kit / Gemini Nano. The actual hardware-feature constant for the NPU is `android.hardware.neuralnetworks` (no app-side declaration needed) or `PackageManager.FEATURE_HARDWARE_KEYSTORE_LIMITED_USE_KEY` — verify what the final SDK 37 docs say before adding a `<uses-feature>` line. Premature addition could break installs on non-NPU devices.

- [ ] **Do not add a `<uses-feature>` for NPU yet.** ML Kit and Firebase AI already auto-route to NPU when available. Just confirm `mlkit-genai-prompt` is on its latest release.

### 4.4 — Background activity launch restrictions (tightened)
Android 17 further tightens BAL. Audit anywhere we start an activity from a non-user-initiated context:

- [ ] `MainActivity.handleHandoffIntent` triggers navigation from an Intent — fine, that's the system delivering an intent the user initiated on another device.
- [ ] `PipActionReceiver` triggers actions from PiP — needs `PendingIntent` with proper flags (already `FLAG_IMMUTABLE`).
- [ ] Verify `DownloadActionReceiver` doesn't launch any activities — it only mutates the worker state.
- [ ] No `Activity.startActivity` from `BroadcastReceiver.onReceive` anywhere — grep confirmed clean.

### 4.5 — Glance widget changes for Android 17
- [ ] `continue_watching_widget_info.xml` and `recently_added_widget_info.xml` are very minimal. Add `android:targetCellWidth`/`targetCellHeight` (Android 12+), `android:previewLayout`, `android:description` (Android 12+), and `android:maxResizeWidth`/`maxResizeHeight` for the new Android 17 widget host sizing rules.

---

## 5. Tooling / Build System

### 5.1 — AGP 9.2.1 + Gradle 9.5.0
We're on the cutting edge. AGP 9.x reached stable in Q1 2026 and brings the new build pipeline; Gradle 9.5 is stable. **Action**: monitor 9.3 / 9.4 release notes for any required `compose-stability.conf` schema changes — we already have a `compose-stability.conf`, just don't let it drift from the schema.

### 5.2 — Kotlin 2.3.21 + KSP 2.3.8
On stable. K2 compiler is default. Compose Compiler plugin (`org.jetbrains.kotlin.plugin.compose`) is correctly used. No action.

### 5.3 — Compose BOM `2026.05.01`
Stable. Carries the latest Material 3 1.4 transitively, but we override with `1.5.0-alpha20` for Expressive components. Per the project's stable-first policy (`docs/plans/UPGRADE_PATH.md`), exit criteria is "Expressive moves to stable."

- [ ] Add a tracking issue: as soon as `androidx.compose.material3:material3` 1.5.0 ships stable, downgrade the four `material3*` alphas in `libs.versions.toml`. Same for `material3Adaptive` (currently `1.3.0-beta02`).

### 5.4 — Lint baselines for new platform warnings
`lint { abortOnError = true; warningsAsErrors = false }` is set. Android 16 SDK introduced ~15 new lint checks (Notification ProgressStyle migration, Predictive Back, Edge-to-edge enforcement, 16 KB native lib check, etc.).

- [ ] Run `./gradlew lintDebug` against the current SDK 37 compile and triage. Capture the baseline in `app/lint-baseline.xml` so CI doesn't regress, but file an issue for each one to actually fix.
- [ ] Specifically check for `EdgeToEdge`, `GestureBackNavigation`, `NotificationPermission`, and `ForegroundServiceType` lint IDs.

### 5.5 — Robolectric on SDK 36 / 37
`robolectric = "4.16.1"` officially supports up to SDK 35 at time of writing. Android 16+ instrumentation tests may need `@Config(sdk = [34])` annotations to avoid Robolectric crashing on unmapped APIs.

- [ ] Add `@Config(sdk = [34])` (or whatever Robolectric's max supported SDK is at upgrade time) to any new tests touching API 36/37 surfaces.
- [ ] When Robolectric 4.17+ ships with SDK 36 shadows, bump and remove the `@Config` overrides.

---

## 6. Dependency Modernization (gates the SDK 37 release)

Pulled from `libs.versions.toml` — only items that block or risk an Android 16/17 final build:

| Dependency | Version | Channel | Blocker for? |
|---|---|---|---|
| `androidx.compose.material3:material3` | `1.5.0-alpha20` | Alpha | Expressive components |
| `material3-adaptive` | `1.3.0-beta02` | Beta | Adaptive layout for ≥600dp mandate (§1.3) |
| `androidx.navigation:navigation-compose` | `2.10.0-alpha05` | Alpha | Predictive back animations (§3.5) |
| `androidx.lifecycle:lifecycle-*` | `2.11.0-beta02` | Beta | — |
| `androidx.activity:activity-compose` | `1.13.0` | Stable ✓ | — |
| `androidx.datastore:datastore-preferences` | `1.3.0-alpha09` | Alpha | — |
| `androidx.biometric:biometric` | `1.4.0-alpha07` | Alpha | — |
| `androidx.window:window` | `1.6.0-alpha03` | Alpha | WindowSizeClass for adaptive (§1.3) |
| `coil-compose` | `3.5.0-beta01` | Beta | — |
| `glance` | `1.3.0-alpha01` | Alpha | Widget API changes (§4.5) |
| `firebase-bom` | `34.13.0` | Stable ✓ | — |
| `mlkit-genai-prompt` | `1.0.0-beta2` | Beta | Gemini Nano API stability |

**Action**:
- [ ] For each Alpha/Beta line, confirm there's an exit criterion documented in `UPGRADE_PATH.md`. Several are blanket "Hold Alpha (UI Stack)" — make sure that's still the right call once Compose 2026.08 BOM ships.

---

## 7. Testing Plan

- [ ] Spin up emulators:
  - `android-36;google_apis_playstore;arm64-v8a` (Android 16 baseline)
  - `android-36;google_apis_playstore;arm64-v8a_16k` (16 KB page size)
  - `android-37;google_apis;arm64-v8a` (Android 17 latest preview)
  - `android-36;android-tv;arm64-v8a` (TV form factor)
- [ ] Smoke-test matrix per emulator:
  - Cold start → server discovery (Android 17: verify `ACCESS_LOCAL_NETWORK` flow)
  - Login + biometric (covers KeyStore changes)
  - Browse library + open detail (covers edge-to-edge insets)
  - Start playback → background → PiP → return → orientation change (covers FGS, edge-to-edge, adaptive)
  - Start download → background app → wait > 5 min (covers FGS dataSync quota)
  - Music playback → lock screen → media buttons (covers `mediaPlayback` FGS)
  - Cast to Chromecast (covers MediaSession changes, Cast SDK 22.3.1)
  - Resize window on freeform/foldable (covers §1.3)
  - Predictive back gesture from each main screen (covers §3.5)
- [ ] Add a `connectedAndroidTest` that verifies `WindowInsets.safeDrawing != 0` after `enableEdgeToEdge()` on the home screen.
- [ ] CI: add a job that builds with SDK 37 + `-Pandroid.experimental.testOptions.emulatorSnapshots.maxSnapshotsForTestFailures=1` and runs the smoke set headless.

---

## 8. Risk Ranking (what to fix first)

1. **High — 16 KB page size (§1.1)** — blocks Play Store updates once enforcement deadline hits.
2. **High — Adaptive playback on ≥600dp (§1.3)** — VideoPlayerActivity orientation handling.
3. **High — FGS dataSync 6h cap (§1.4)** — large downloads silently fail on Android 15+ today.
4. **Medium — Lint baseline for new checks (§5.4)** — these are how Play pre-launch report grades the upload.
5. **Medium — Predictive back in player overlay (§3.5)** — UX regression on Android 16 devices.
6. **Medium — Material 3 stable migration (§5.3, §6)** — long-running tech debt, gate-keeps removing alpha exposure.
7. **Low — Handoff real API (§4.1)** — stub is fine until the API stabilizes.
8. **Low — NPU declarations (§4.3)** — risk of adding the wrong feature flag prematurely.

---

## 9. Already Done (do not redo)

For context — these were the standard upgrade items and they're complete in the current tree:

- ✅ `compileSdk = 37` / `targetSdk = 37` / `minSdk = 30`
- ✅ NDK `29.0.14206865` (produces 16 KB-aligned libs by default; just need to verify third-party `.so` files)
- ✅ Java 21 / Kotlin JVM target 21 with `coreLibraryDesugaring`
- ✅ `enableEdgeToEdge()` in `MainActivity.onCreate`
- ✅ `android:enableOnBackInvokedCallback="true"` in manifest
- ✅ `ACCESS_LOCAL_NETWORK` permission declared and runtime-requested on SDK 37
- ✅ `RECEIVER_NOT_EXPORTED` flag on all runtime-registered receivers (TIRAMISU+ branch)
- ✅ `PendingIntent.FLAG_IMMUTABLE` everywhere — no `FLAG_MUTABLE` in non-Cast code
- ✅ `FOREGROUND_SERVICE_TYPE_DATA_SYNC` declared in `OfflineDownloadWorker.getForegroundInfo`
- ✅ `foregroundServiceType="mediaPlayback"` on `AudioService` in manifest
- ✅ `foregroundServiceType="dataSync"` merged onto `SystemForegroundService` via `tools:node="merge"`
- ✅ `NotificationCompat.ProgressStyle` for Android 16 download notifications (partial — needs polish per §3.1)
- ✅ `VibrationEffect.Composition` Android 16 haptic primitives in `ExpressiveHaptics`
- ✅ `Intent.getParcelableExtra(name, Class)` migration in `AudioService` and `MainActivity`
- ✅ `screenOrientation="user"` on `MainActivity` (adaptive-friendly)
- ✅ `resizeableActivity="true"` on application
- ✅ `usesCleartextTraffic` not used; cleartext gated by `network_security_config.xml` instead
- ✅ Network security config trusts both system and user CAs (for TOFU pinning)
- ✅ POST_NOTIFICATIONS runtime permission flow with TIRAMISU+ guards in download buttons
- ✅ Glance widgets declared with `appwidget-provider` (basic config — see §4.5 for polish)
- ✅ `ndk.debugSymbolLevel = "FULL"` for Play Console crash symbolication
- ✅ `useLegacyPackaging = false` for jniLibs (required for 16 KB alignment to take effect)
- ✅ `HandoffManager` skeleton with HMAC-SHA256 signature verification (real API binding pending — §4.1)

---

*If anything here conflicts with `docs/plans/2026-03-28-android-16-17-modernization.md` or `docs/plans/2026-04-26-android-16-17-modernization-phase-2.md`, this document wins — those were drafted before the SDK 37 bump and several of their tasks are already done.*
