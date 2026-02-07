# Testing the Immersive UI

This guide explains how to test the new immersive UI redesign.

## Quick Start (Debug Builds)

**The immersive UI is automatically enabled in debug builds!**

Just build and run the debug APK:
```bash
./gradlew installDebug  # Windows: gradlew.bat installDebug
```

The home screen will automatically use the new `ImmersiveHomeScreen` with:
- Full-screen hero carousel (480dp height)
- Larger media cards (280dp width vs 200dp)
- Tighter spacing (16dp vs 24dp)
- Auto-hiding top app bar
- Floating action buttons for Search + AI
- Immersive design throughout

## How Feature Flags Work

### Architecture
The app uses Firebase Remote Config for feature flags, which allows:
- **Remote control**: Toggle features without app updates
- **Gradual rollout**: Enable for % of users
- **A/B testing**: Compare old vs new designs
- **Emergency rollback**: Disable features instantly

### Feature Flag Keys
Defined in `core/FeatureFlags.kt`:
```kotlin
FeatureFlags.ImmersiveUI.ENABLE_IMMERSIVE_UI      // Master toggle
FeatureFlags.ImmersiveUI.IMMERSIVE_HOME_SCREEN    // Home screen only
FeatureFlags.ImmersiveUI.IMMERSIVE_DETAIL_SCREENS // Detail screens
// ... etc
```

### Default Behavior
- **Debug builds**: Immersive UI **enabled** by default (for testing)
- **Release builds**: Immersive UI **disabled** by default (controlled remotely)

## Controlling Feature Flags

### Option 1: Local Override (Debug Only)
Edit `di/RemoteConfigModule.kt`:
```kotlin
val enableImmersiveUIDebug = true  // Set to false to disable
```

Then rebuild:
```bash
./gradlew installDebug
```

### Option 2: Firebase Console (Production)
1. Go to Firebase Console → Remote Config
2. Add/edit parameter:
   - Key: `enable_immersive_ui`
   - Value: `true` or `false`
3. Publish changes
4. App will fetch new value within fetch interval (0s debug, 1h production)

### Option 3: Programmatic Override
In `RemoteConfigModule.kt`, modify the defaults map:
```kotlin
val defaults = mapOf(
    "enable_immersive_ui" to true,        // Master toggle
    "immersive_home_screen" to true,      // Home screen
    "immersive_detail_screens" to false,  // Not implemented yet
)
```

## Testing Checklist

### Home Screen Testing
- [ ] Hero carousel displays and auto-scrolls every 15 seconds
- [ ] Media cards are larger (280dp) with proper aspect ratio
- [ ] Tighter spacing between rows (16dp)
- [ ] Top app bar auto-hides when scrolling down
- [ ] Top app bar reappears when scrolling up or near top
- [ ] Floating action buttons are visible and functional (Search + AI)
- [ ] Mini player overlays at bottom
- [ ] Pull-to-refresh works
- [ ] All content sections render correctly:
  - [ ] Continue Watching
  - [ ] Next Up
  - [ ] Recently Added Movies
  - [ ] Recently Added TV Shows
  - [ ] Recently Added Stuff
- [ ] Item clicks navigate correctly
- [ ] Long-press shows management sheet
- [ ] Viewing mood widget displays (if AI enabled)

### Performance Testing
- [ ] Scroll performance: No jank, <16ms frame times
- [ ] Memory usage: <150MB on average phone
- [ ] Image loading: Smooth, no stuttering
- [ ] Hero carousel animations: Smooth transitions

### Accessibility Testing
- [ ] TalkBack: All elements properly labeled
- [ ] Touch targets: All buttons ≥48dp
- [ ] Contrast ratios: Text on gradients ≥4.5:1

### Device Testing
- [ ] Phone (360x800dp): Hero 480dp height
- [ ] Tablet (600x960dp): Hero 600dp height (need to verify)
- [ ] Android TV (1920x1080dp): Hero 720dp height (need to verify)

## Switching Between Old and New UI

The feature flags enable **live A/B testing**:

### To Test Old UI (Expressive):
```kotlin
// In RemoteConfigModule.kt
val enableImmersiveUIDebug = false
```

### To Test New UI (Immersive):
```kotlin
// In RemoteConfigModule.kt
val enableImmersiveUIDebug = true
```

Both screens use the **same ViewModels and data layer**, so switching is instant and safe.

## Debug Logging

The app logs feature flag decisions in debug builds:
```
adb logcat | grep "HomeScreen"
```

You'll see:
```
HomeScreen: Feature flags - enable_immersive_ui: true, immersive_home_screen: true, using immersive: true
```

## Troubleshooting

### Immersive UI Not Showing
1. Check logcat for feature flag values
2. Verify `BuildConfig.DEBUG = true` in debug builds
3. Check `RemoteConfigModule.kt` defaults
4. Clear app data and restart

### Build Errors
```bash
./gradlew clean assembleDebug
```

### Firebase Not Connected
The app will use local defaults (set in `RemoteConfigModule.kt`), so Firebase connection is not required for testing.

## Current Implementation Status

**Phase 1**: ✅ Foundation Components (Complete)
- All core components built and tested
- Theme tokens, gradients, cards, carousel, scaffold

**Phase 2**: 🟡 Home & Detail Screens (25% complete)
- ✅ ImmersiveHomeScreen (Complete)
- ⏳ ImmersiveMovieDetailScreen (Not started)
- ⏳ ImmersiveTVSeasonScreen (Not started)
- ⏳ ImmersiveLibraryScreen (Not started)

**Phase 3**: ⏳ Browse & Discovery (Not started)
**Phase 4**: ⏳ Polish & Accessibility (Not started)
**Phase 5**: ⏳ Rollout & Monitoring (Not started)

## Next Steps

1. **Test ImmersiveHomeScreen** on emulator/device
2. **Create demo video/screenshots**
3. **Build ImmersiveMovieDetailScreen**
4. **Add remaining screens**
5. **Performance optimization**
6. **Accessibility audit**
7. **Production rollout**

---

**Questions?** Check `IMMERSIVE_UI_PROGRESS.md` for detailed implementation notes.
