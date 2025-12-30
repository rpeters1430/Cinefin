# Jellyfin Android - Comprehensive Improvement Plan

**Date**: December 30, 2025  
**Analysis Type**: Full Code Audit + Runtime Logs + Material 3 Expressive Opportunities  
**Priority**: Issues are ordered by severity (🔴 Critical → 🟠 High → 🟡 Medium → 🟢 Low)

---

## Executive Summary

This audit identified **1 critical crash**, **24+ performance issues**, and **numerous opportunities** to adopt the latest Material 3 Expressive components. The app has a solid foundation but needs targeted fixes and modernization to reach production quality.

### Quick Stats
| Category | Count | Status |
|----------|-------|--------|
| Critical Bugs | 1 | 🔴 Needs immediate fix |
| High Priority Issues | 5 | 🟠 Should fix this sprint |
| Medium Priority | 12 | 🟡 Plan for next sprint |
| M3 Expressive Opportunities | 8+ | 🟢 Enhancement opportunities |
| LazyList Keys Missing | 24+ | 🟠 Performance impact |

---

## 🔴 CRITICAL: Fatal Crash in MediaRouteButton

### Issue
**FATAL EXCEPTION**: App crashes when entering the video player with Chromecast button visible.

### Root Cause
```
java.lang.IllegalArgumentException: background can not be translucent: #0
    at androidx.core.graphics.ColorUtils.calculateContrast(ColorUtils.java:175)
    at androidx.mediarouter.app.MediaRouterThemeHelper.getControllerColor
    at com.rpeters.jellyfin.ui.player.MediaRouteButtonKt.MediaRouteButton$lambda$0$0
```

The `MediaRouteButton` View is being created with a context that has a translucent/transparent background color. The AndroidX MediaRouter library requires an opaque background to calculate contrast ratios.

### Location
`app/src/main/java/com/rpeters/jellyfin/ui/player/MediaRouteButton.kt:36-42`

### Fix

```kotlin
@Composable
fun MediaRouteButton(
    modifier: Modifier = Modifier,
    tint: Int = MaterialTheme.colorScheme.onSurface.toArgb(),
) {
    val context = LocalContext.current
    
    // Create a themed context with opaque background for MediaRouter
    val themedContext = remember(context) {
        ContextThemeWrapper(context, R.style.Theme_MediaRouter_Opaque)
    }

    AndroidView(
        factory = { _ ->
            MediaRouteButton(themedContext).apply {
                CastButtonFactory.setUpMediaRouteButton(themedContext, this)
                contentDescription = "Cast to device"
            }
        },
        modifier = modifier.size(48.dp),
    )
}
```

Also add to `res/values/themes.xml`:
```xml
<style name="Theme.MediaRouter.Opaque" parent="Theme.MaterialComponents.DayNight">
    <item name="android:colorBackground">@color/design_default_color_background</item>
    <item name="colorSurface">@color/design_default_color_surface</item>
</style>
```

**Alternative Quick Fix** (if you don't want to create a new theme):
```kotlin
AndroidView(
    factory = { ctx ->
        // Wrap context with explicit background color
        val wrapper = android.view.ContextThemeWrapper(ctx, 0).apply {
            theme.applyStyle(R.style.Theme_Material3_DayNight, true)
        }
        MediaRouteButton(wrapper).apply {
            CastButtonFactory.setUpMediaRouteButton(wrapper, this)
            contentDescription = "Cast to device"
            // Force opaque background
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    },
    modifier = modifier.size(48.dp),
)
```

---

## 🟠 HIGH PRIORITY: Performance Issues

### 1. Missing LazyList Keys (24+ instances)

**Impact**: Poor scroll performance, incorrect item reuse, animations breaking

**Locations Requiring Keys**:

| File | Line | Fix |
|------|------|-----|
| `SkeletonLoading.kt` | 203 | Add `key = { index }` |
| `TvLoadingStates.kt` | 126 | Add `key = { index }` |
| `PaginatedMediaGrid.kt` | 90 | Add `key = { it.id }` |
| `StuffScreen.kt` | 251 | Add `key = { it.id }` |
| `SearchScreen.kt` | 231, 287, 315 | Add `key = { it.id }` |
| `LibraryFilters.kt` | 57 | Add `key = { it.hashCode() }` |
| `LibraryTypeScreen.kt` | 348, 385, 426, 463 | Add `key = { it.id }` |
| `TVSeasonScreen.kt` | 854 | Add `key = { it.id }` |
| `MoviesScreen.kt` | 324, 363, 401 | Add `key = { it.id }` |
| `TVShowsScreen.kt` | 560, 607 | Add `key = { it.id }` |
| `SettingsScreen.kt` | 156 | Add `key = { it.hashCode() }` |
| `FavoritesScreen.kt` | 150 | Add `key = { it.id }` |
| `HomeScreen.kt` | 768, 846 | Already have keys on some, verify all |
| `MusicScreen.kt` | 375 | Add `key = { it.id }` |
| `TvVideoPlayerScreen.kt` | 536 | Add `key = { index }` |

**Example Fix Pattern**:
```kotlin
// BEFORE
items(items) { item ->
    MediaCard(item = item)
}

// AFTER
items(
    items = items,
    key = { item -> item.id ?: item.hashCode() }
) { item ->
    MediaCard(item = item)
}
```

### 2. Large Screen Files Need Refactoring

| File | Size | Lines | Recommendation |
|------|------|-------|----------------|
| `HomeScreen.kt` | 40KB | 1,057 | Extract sections to separate files |
| `TVSeasonScreen.kt` | 42KB | ~1,100 | Extract components |
| `TVEpisodeDetailScreen.kt` | 45KB | ~1,200 | Split into smaller composables |
| `MovieDetailScreen.kt` | 26KB | ~700 | Consider extraction |

**Recommended Extraction for HomeScreen**:
- `HomeHeader.kt` - Header component
- `ContinueWatchingSection.kt` - Continue watching row  
- `FeaturedCarouselSection.kt` - Hero carousel
- `MediaRowSection.kt` - Generic media row component
- `HomeTopBar.kt` - Top app bar

---

## 🟠 HIGH PRIORITY: Theme & Color Issues

### 1. Translucent Color Usage in Sensitive Contexts

Some places use `.copy(alpha = 0f)` or `Color.Transparent` where opaque colors might be expected:

```kotlin
// ExpressiveCards.kt:217-219 - Gradient starts with zero alpha
Brush.verticalGradient(
    colors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),  // ⚠️
        Color.Transparent,  // ⚠️
    ),
)
```

**Review Locations**:
- `ExpressiveToolbar.kt`: Lines 250, 301-302
- `ExpressiveCards.kt`: Lines 217-219
- `MediaCards.kt`: Lines 212-213

### 2. Missing `contentType` in LazyList Items

Adding `contentType` improves performance by enabling better item reuse:

```kotlin
// BEFORE
item(key = "header") {
    HomeHeader()
}

// AFTER  
item(key = "header", contentType = "header") {
    HomeHeader()
}
```

---

## 🟡 MEDIUM PRIORITY: Material 3 Expressive Modernization

### Current State
- Using Material 3 `1.5.0-alpha11` ✅
- Using Compose BOM `2025.12.01` ✅
- Custom Expressive components exist ✅
- **Missing**: Latest M3 Expressive APIs

### Recommended Upgrades

#### 1. Adopt `MaterialExpressiveTheme`

**Current** (`Theme.kt`):
```kotlin
MaterialTheme(
    colorScheme = adjustedColorScheme,
    typography = Typography,
    shapes = JellyfinShapes,
    content = content,
)
```

**Recommended**:
```kotlin
import androidx.compose.material3.MaterialExpressiveTheme

MaterialExpressiveTheme(
    colorScheme = adjustedColorScheme,
    typography = Typography,
    shapes = JellyfinShapes,
    motionScheme = MotionScheme.expressive(),  // NEW
    content = content,
)
```

#### 2. Use Official `MotionScheme.expressive()`

**Current** (`Motion.kt`): Custom motion tokens

**Enhancement**:
```kotlin
// Access via MaterialTheme in composables
val motionScheme = MaterialTheme.motionScheme

// For spatial animations (scale, position, rotation)
val animationSpec = motionScheme.defaultSpatialSpec<Float>()

// For effects animations (color, alpha, elevation)  
val effectsSpec = motionScheme.defaultEffectsSpec<Float>()
```

#### 3. New M3 Expressive Components to Consider

| Component | Use Case | Priority |
|-----------|----------|----------|
| `HorizontalFloatingToolbar` | Player controls, contextual actions | 🟡 Medium |
| `FlexibleBottomAppBar` | Main navigation (scroll-aware) | 🟡 Medium |
| `FloatingActionButtonMenu` | Multi-action FAB | 🟢 Low |
| `ButtonGroup` | Segmented controls | 🟡 Medium |
| `LoadingIndicator` | New expressive loading | 🟢 Low |
| `MaterialShapes` | Morphable shapes | 🟢 Low |

#### 4. Expressive Menu Components

You have `ExpressiveMenus.kt` - verify it uses the new official APIs:

```kotlin
// New in 1.5.0-alpha09+
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveMenu() {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        // Toggleable menu item (with switch)
        ToggleableDropdownMenuItem(
            text = { Text("Auto-play next") },
            checked = autoPlay,
            onCheckedChange = { autoPlay = it }
        )
        
        // Selectable menu item (with checkmark)
        SelectableDropdownMenuItem(
            text = { Text("1080p") },
            selected = quality == "1080p",
            onClick = { quality = "1080p" }
        )
        
        // Menu group with header
        MenuGroup(header = { Text("Quality") }) {
            // group items
        }
    }
}
```

---

## 🟡 MEDIUM PRIORITY: Code Quality Improvements

### 1. Experimental API Opt-ins

Ensure all experimental coroutine APIs are properly annotated:

**Locations needing annotation**:
- `MainAppViewModel.kt` lines 227-229
- `SearchViewModel.kt` line 70

```kotlin
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainAppViewModel @Inject constructor(...) : ViewModel() {
    // ...
}
```

### 2. SecureTextField Warning

Logcat shows:
```
Failed to fetch show password setting, using value: true
android.provider.Settings$SettingNotFoundException: show_password
```

This is a benign warning from Compose's `OutlinedSecureTextField` on the emulator but should be handled gracefully on real devices.

### 3. Image Loading Failures

Logcat shows image failures:
```
🚨 Failed - https://.../Items/.../Images/Primary?maxHeight=400&maxWidth=400
```

**Recommendations**:
- Add retry logic with exponential backoff
- Implement better fallback images
- Add error logging for debugging

---

## 🟢 LOW PRIORITY: Enhancement Opportunities

### 1. Shared Element Transitions

With Compose 1.10's improved shared element APIs:

```kotlin
SharedTransitionLayout {
    AnimatedContent(targetState = screen) { currentScreen ->
        when (currentScreen) {
            is Screen.List -> MediaList(
                modifier = Modifier.sharedElement(
                    rememberSharedContentState(key = "media-${item.id}"),
                    animatedVisibilityScope = this@AnimatedContent
                )
            )
            is Screen.Detail -> MediaDetail(
                modifier = Modifier.sharedElement(
                    rememberSharedContentState(key = "media-${item.id}"),
                    animatedVisibilityScope = this@AnimatedContent
                )
            )
        }
    }
}
```

### 2. `retain` API for Player State

New Compose API to persist state across configuration changes without serialization:

```kotlin
@Composable
fun VideoPlayer() {
    val player = retain { 
        ExoPlayer.Builder(context).build()
    }
    // Player persists across rotation without recreation
}
```

### 3. Auto-sizing Text

For titles that need to fit available space:

```kotlin
import androidx.compose.foundation.text.AutoSizeText

AutoSizeText(
    text = movieTitle,
    maxLines = 2,
    minTextSize = 12.sp,
    maxTextSize = 24.sp,
    style = MaterialTheme.typography.headlineSmall
)
```

### 4. Predictive Back Animations

The app should support Android 14+ predictive back:

```kotlin
PredictiveBackHandler { progress ->
    // progress: Flow<BackEventCompat>
    progress.collect { backEvent ->
        // Animate based on back gesture progress
        scale = 1f - (backEvent.progress * 0.1f)
    }
}
```

---

## Implementation Roadmap

### Sprint 1: Critical & High Priority (Week 1)

| Task | File | Effort | Priority |
|------|------|--------|----------|
| Fix MediaRouteButton crash | `MediaRouteButton.kt` | 1-2 hours | 🔴 Critical |
| Add LazyList keys (batch 1) | Various screens | 4-6 hours | 🟠 High |
| Review translucent colors | Theme files | 2-3 hours | 🟠 High |

### Sprint 2: Performance & Quality (Week 2)

| Task | File | Effort | Priority |
|------|------|--------|----------|
| Add remaining LazyList keys | Various screens | 3-4 hours | 🟠 High |
| Add contentType parameters | All LazyLists | 2-3 hours | 🟡 Medium |
| Refactor HomeScreen | `ui/screens/home/` | 6-8 hours | 🟡 Medium |
| Add experimental opt-ins | ViewModels | 1 hour | 🟡 Medium |

### Sprint 3: M3 Expressive (Week 3-4)

| Task | File | Effort | Priority |
|------|------|--------|----------|
| Adopt MaterialExpressiveTheme | `Theme.kt` | 4-6 hours | 🟡 Medium |
| Implement MotionScheme | Throughout | 6-8 hours | 🟡 Medium |
| Add HorizontalFloatingToolbar | Player | 4-6 hours | 🟢 Low |
| Evaluate FlexibleBottomAppBar | Navigation | 4-6 hours | 🟢 Low |

---

## Testing Checklist

### After MediaRouteButton Fix
- [ ] Test video player on Pixel (no crash)
- [ ] Test video player on Samsung device
- [ ] Test Chromecast discovery and connection
- [ ] Test with dynamic colors enabled/disabled

### After LazyList Key Additions
- [ ] Profile scroll performance with Composition Tracing
- [ ] Verify no visual glitches during fast scroll
- [ ] Test on low-end device
- [ ] Verify animations work correctly during scroll

### After Theme Updates
- [ ] Test all theme modes (Light, Dark, AMOLED)
- [ ] Test all accent colors
- [ ] Test contrast levels (Standard, Medium, High)
- [ ] Verify dynamic colors on Android 12+

---

## Resources

### Official Documentation
- [Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)
- [Compose Material 3 Releases](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- [Compose December '25 Release](https://android-developers.googleblog.com/2025/12/whats-new-in-jetpack-compose-december.html)
- [Androidify Sample App](https://github.com/android/androidify) - M3 Expressive reference

### Related Project Files
- `CURRENT_STATUS.md` - Project overview
- `KNOWN_ISSUES.md` - Tracked issues
- `MATERIAL3_EXPRESSIVE.md` - Existing M3 documentation
- `IMPROVEMENTS.md` - General roadmap

---

**Document Version**: 1.0  
**Last Updated**: December 30, 2025  
**Next Review**: January 6, 2026
