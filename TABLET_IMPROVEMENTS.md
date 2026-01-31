# Tablet & Adaptive Design Improvements

**Last Updated**: 2026-01-30
**Target Devices**: Tablets (7-13 inch), Foldables, Large Phones
**Test Device**: Pixel Tablet

---

## 📊 Overview

The Jellyfin Android app has a comprehensive adaptive layout system built (`AdaptiveLayoutManager.kt`) but the phone app (`JellyfinApp.kt`) doesn't utilize it. This document tracks the implementation of tablet-optimized layouts and adaptive design patterns.

### Current State
- ✅ AdaptiveLayoutManager infrastructure complete (478 lines)
- ✅ Material 3 adaptive components imported
- ✅ TV app shows working adaptive implementation
- ❌ Phone app uses fixed phone layouts on all screen sizes
- ❌ Navigation doesn't adapt (always bottom bar)
- ❌ Most screens don't utilize tablet screen space

### Window Size Class Breakpoints
```kotlin
Compact:  < 600dp width   (phones)
Medium:   600-840dp width (small-medium tablets)
Expanded: > 840dp width   (large tablets, foldables unfolded)
```

---

## 🎯 Implementation Phases

### Phase 1: Foundation (Critical) 🔴
**Goal**: Core adaptive infrastructure for all screens to use

- [x] **1.1 Adaptive Navigation System**
  - [x] Integrate WindowSizeClass calculation in JellyfinApp.kt
  - [x] Replace hardcoded BottomNavBar with NavigationSuiteScaffold
  - [x] Implement bottom bar (compact), rail (medium), drawer (expanded)
  - [ ] Test navigation on phone, tablet, and large tablet sizes
  - **Files**: `ui/JellyfinApp.kt`, `ui/components/BottomNavBar.kt`
  - **Status**: ✅ Implemented - Ready for Testing

- [ ] **1.2 Window Size Class Provider**
  - [ ] Add WindowSizeClass to CompositionLocal
  - [ ] Create `LocalWindowSizeClass` provider in theme
  - [ ] Update JellyfinApp to provide window size class
  - **Files**: `ui/theme/Theme.kt`, `ui/JellyfinApp.kt`
  - **Status**: ⏳ Pending

- [ ] **1.3 Adaptive Layout Config Provider**
  - [ ] Expose `AdaptiveLayoutConfig` via CompositionLocal
  - [ ] Calculate layout config at app root
  - [ ] Make available to all composables
  - **Files**: `ui/JellyfinApp.kt`, `ui/adaptive/AdaptiveLayoutManager.kt`
  - **Status**: ⏳ Pending

---

### Phase 2: Core Screens (High Impact) 🟠
**Goal**: Fix most-used screens that look poor on tablets

- [x] **2.1 HomeScreen Adaptation**
  - [x] Read WindowSizeClass in HomeScreen
  - [x] Keep carousel layout for compact screens
  - [x] Implement multi-column grid layout for medium/expanded screens
  - [x] Use dual-pane pattern from `TvAdaptiveHomeContent.kt` as reference
  - [x] Hero carousel maintained for all screen sizes
  - [x] Show multiple content sections in grids on tablets
  - [ ] Test: Phone portrait, tablet portrait, tablet landscape
  - **Files**: `ui/screens/HomeScreen.kt`
  - **Reference**: `ui/screens/tv/adaptive/TvAdaptiveHomeContent.kt` (working example)
  - **Status**: ✅ Implemented - Ready for Testing

- [ ] **2.2 LibraryScreen Grid Layout**
  - [ ] Replace single-column LazyColumn with adaptive grid
  - [ ] Phone: 2 columns
  - [ ] Medium tablet: 3 columns
  - [ ] Large tablet/landscape: 4-5 columns
  - [ ] Maintain card design, adjust size based on columns
  - [ ] Test: Verify all library types display correctly
  - **Files**: `ui/screens/LibraryScreen.kt`
  - **Status**: ⏳ Pending

- [ ] **2.3 SearchScreen Results Grid**
  - [ ] Convert results to adaptive grid instead of single column
  - [ ] Use same column logic as LibraryScreen
  - [ ] Maintain search bar and filters at top
  - **Files**: `ui/screens/SearchScreen.kt`
  - **Status**: ⏳ Pending

---

### Phase 3: Detail Screens (Medium Impact) 🟡
**Goal**: Better use of tablet space in detail views

- [ ] **3.1 MovieDetailScreen Split View**
  - [ ] Detect window size class
  - [ ] Compact: Keep single-column layout
  - [ ] Medium/Expanded: Implement split view (60/40 or 70/30)
  - [ ] Left pane: Hero image, title, metadata, description, actions
  - [ ] Right pane: Related movies grid (instead of horizontal carousel)
  - [ ] Add cast & crew grid in right pane
  - [ ] Test: Verify scrolling works correctly in both panes
  - **Files**: `ui/screens/MovieDetailScreen.kt`
  - **Status**: ⏳ Pending

- [ ] **3.2 TVEpisodeDetailScreen Split View**
  - [ ] Same split view pattern as MovieDetailScreen
  - [ ] Left: Episode details
  - [ ] Right: Next episodes grid + season episodes
  - **Files**: `ui/screens/TVEpisodeDetailScreen.kt`
  - **Status**: ⏳ Pending

- [ ] **3.3 TVShowDetailScreen Split View**
  - [ ] Same pattern as above
  - [ ] Left: Show details, seasons selector
  - [ ] Right: Episodes grid for selected season
  - **Files**: `ui/screens/TVShowDetailScreen.kt`
  - **Status**: ⏳ Pending

- [ ] **3.4 ArtistDetailScreen Optimization**
  - [ ] Already uses GridCells.Adaptive - verify it looks good
  - [ ] May need larger album art on tablets
  - [ ] Test and adjust if needed
  - **Files**: `ui/screens/ArtistDetailScreen.kt`
  - **Status**: ⏳ Pending

---

### Phase 4: Settings & Profile (Polish) 🟢
**Goal**: Improve secondary screens

- [ ] **4.1 ProfileScreen Two-Column Layout**
  - [ ] Compact: Single column (current)
  - [ ] Medium/Expanded: Two columns
  - [ ] Left column: Avatar, user info, server connection status
  - [ ] Right column: Quick settings, preferences, logout button
  - [ ] Improve visual hierarchy
  - **Files**: `ui/screens/ProfileScreen.kt`
  - **Status**: ⏳ Pending

- [ ] **4.2 SettingsScreen Multi-Column**
  - [ ] Compact: Single column
  - [ ] Medium/Expanded: Two columns or master-detail pattern
  - [ ] Left: Settings categories
  - [ ] Right: Settings details for selected category
  - [ ] Consider using Material 3 list-detail scaffold
  - **Files**: `ui/screens/SettingsScreen.kt`
  - **Status**: ⏳ Pending

---

### Phase 5: Foldable Support (Future) 🔵
**Goal**: Optimize for foldable devices

- [ ] **5.1 Activate Foldable Detection**
  - [ ] Use existing posture detection in AdaptiveLayoutManager
  - [ ] Implement dual-pane for book/separating postures
  - [ ] Test on foldable emulator
  - **Files**: `ui/adaptive/AdaptiveLayoutManager.kt`, `ui/JellyfinApp.kt`
  - **Status**: ⏳ Pending

- [ ] **5.2 TableTop Mode Support**
  - [ ] Detect TableTop posture
  - [ ] Optimize video player for TableTop mode
  - [ ] Controls on bottom half, video on top half
  - **Files**: `ui/screens/VideoPlayerScreen.kt`
  - **Status**: ⏳ Pending

---

## 📐 Technical Implementation Details

### Navigation Adaptation Pattern

**Before** (Current - Phone Only):
```kotlin
// JellyfinApp.kt
Scaffold(
    bottomBar = {
        BottomNavBar(
            navController = navController,
            currentRoute = currentRoute
        )
    }
) { paddingValues ->
    // Content
}
```

**After** (Adaptive):
```kotlin
// JellyfinApp.kt
val windowSizeClass = calculateWindowSizeClass(activity = LocalContext.current as Activity)
val navigationType = when (windowSizeClass.widthSizeClass) {
    WindowWidthSizeClass.Compact -> NavigationSuiteType.NavigationBar
    WindowWidthSizeClass.Medium -> NavigationSuiteType.NavigationRail
    else -> NavigationSuiteType.NavigationDrawer
}

NavigationSuiteScaffold(
    navigationSuiteItems = {
        navigationItems.forEach { item ->
            item(
                selected = currentRoute == item.route,
                onClick = { navController.navigate(item.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    },
    layoutType = navigationType
) {
    // Content
}
```

### Window Size Class Access Pattern

**Option A: Calculate per screen** (Simple, some duplication)
```kotlin
@Composable
fun HomeScreen() {
    val windowSizeClass = calculateWindowSizeClass(activity = LocalContext.current as Activity)

    when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> PhoneHomeLayout()
        else -> TabletHomeLayout()
    }
}
```

**Option B: Provide via CompositionLocal** (Better, reusable)
```kotlin
// Theme.kt
val LocalWindowSizeClass = staticCompositionLocalOf<WindowSizeClass> {
    error("No WindowSizeClass provided")
}

// JellyfinApp.kt
val windowSizeClass = calculateWindowSizeClass(activity = this)
CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
    // App content
}

// Any screen
@Composable
fun HomeScreen() {
    val windowSizeClass = LocalWindowSizeClass.current
    // Use windowSizeClass
}
```

### Grid Column Calculation

```kotlin
fun calculateGridColumns(windowSizeClass: WindowSizeClass): Int {
    return when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> 2
        WindowWidthSizeClass.Medium -> 3
        else -> when (windowSizeClass.heightSizeClass) {
            WindowHeightSizeClass.Compact -> 5 // Landscape large tablet
            else -> 4 // Portrait large tablet
        }
    }
}
```

### Split View Layout Pattern

```kotlin
@Composable
fun MovieDetailScreen(windowSizeClass: WindowSizeClass) {
    if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact) {
        // Phone: Single pane
        SinglePaneDetailLayout()
    } else {
        // Tablet: Split view
        Row(modifier = Modifier.fillMaxSize()) {
            // Main content pane
            Box(modifier = Modifier.weight(0.6f)) {
                DetailContentPane()
            }

            // Related content pane
            Box(modifier = Modifier.weight(0.4f)) {
                RelatedContentGridPane()
            }
        }
    }
}
```

---

## 🧪 Testing Checklist

For each implemented improvement, test on:

### Device Sizes
- [ ] Phone portrait (360dp width)
- [ ] Phone landscape (640dp width)
- [ ] Small tablet portrait (600dp width) - Pixel Tablet
- [ ] Small tablet landscape (960dp width) - Pixel Tablet
- [ ] Large tablet portrait (840dp width)
- [ ] Large tablet landscape (1280dp width)

### Functionality Tests
- [ ] Navigation works correctly (all nav items accessible)
- [ ] Touch targets are appropriate size (min 48dp)
- [ ] Content doesn't overflow or clip
- [ ] Scrolling works smoothly
- [ ] Images load at appropriate sizes
- [ ] Transitions between screens are smooth
- [ ] Back button behavior is correct
- [ ] Deep links work correctly

### Visual Tests
- [ ] Spacing is consistent and appropriate
- [ ] Text is readable (not too wide on large screens)
- [ ] Images maintain aspect ratio
- [ ] Content doesn't look stretched or squished
- [ ] Material 3 theming is consistent
- [ ] Dark/light theme both look good

---

## 📚 Reference Files

### Working Examples (Study These)
- `ui/screens/tv/TvHomeScreen.kt` - Adaptive layout selection
- `ui/screens/tv/adaptive/TvAdaptiveHomeContent.kt` - Dual-pane implementation
- `ui/screens/tv/TvLibraryScreen.kt` - TV adaptive layout

### Adaptive Infrastructure
- `ui/adaptive/AdaptiveLayoutManager.kt` - Core adaptive logic (478 lines)
- `ui/components/AccessibleNavigation.kt` - Navigation rail/bar/drawer components

### Grid Implementations (Good Examples)
- `ui/screens/MoviesContent.kt` - GridCells.Adaptive usage
- `ui/screens/TVShowsContent.kt` - GridCells.Adaptive usage
- `ui/components/PaginatedMediaGrid.kt` - Reusable grid component

### Screens Needing Updates
- `ui/JellyfinApp.kt` - Main app scaffold (navigation)
- `ui/screens/HomeScreen.kt` - Most important screen
- `ui/screens/LibraryScreen.kt` - Library grid
- `ui/screens/MovieDetailScreen.kt` - Detail split view
- `ui/screens/TVEpisodeDetailScreen.kt` - Detail split view
- `ui/screens/ProfileScreen.kt` - Profile layout
- `ui/screens/SettingsScreen.kt` - Settings layout

---

## 🎨 Design Principles

### Material 3 Adaptive Guidelines
1. **Navigation**: Bottom bar → Rail → Drawer as width increases
2. **Content**: Single column → Multi-column grid as width increases
3. **Details**: Single pane → Split view as width increases
4. **Spacing**: Increase padding/margins on larger screens
5. **Typography**: Consider larger text on tablets (optional)
6. **Touch Targets**: Min 48dp on all devices

### Jellyfin-Specific Patterns
1. **Hero Carousel**: Keep on all sizes, adjust item dimensions
2. **Media Grids**: Use `GridCells.Adaptive(minSize = 150-160.dp)`
3. **Library Cards**: Maintain design, adjust columns
4. **Posters**: Maintain aspect ratio (2:3 for movies/shows)
5. **Consistency**: Follow TV app adaptive patterns where applicable

---

## 📈 Progress Tracking

**Overall Progress**: 9% (2/22 tasks completed)

### Phase Completion
- 🔄 Phase 1 (Foundation): 1/3 tasks (Implementation complete, testing pending)
- 🔄 Phase 2 (Core Screens): 1/3 tasks (Implementation complete, testing pending)
- ⏳ Phase 3 (Detail Screens): 0/4 tasks
- ⏳ Phase 4 (Settings & Profile): 0/2 tasks
- ⏳ Phase 5 (Foldable Support): 0/2 tasks

### Status Legend
- ✅ Completed
- 🔄 In Progress
- ⏳ Pending
- ❌ Blocked
- 🔍 Needs Review

---

## 🐛 Known Issues & Notes

### Issue Tracking
- [ ] AdaptiveLayoutManager exists but isn't integrated into phone app
- [ ] Two device detection systems exist (DeviceTypeUtils vs AdaptiveLayoutManager)
- [ ] Navigation Rail component exists but never gets used
- [ ] Window Size Classes calculated in TV screens but not phone screens

### Implementation Notes
- Material 3 adaptive dependencies already in build.gradle.kts
- No new dependencies needed for adaptive support
- TV app shows the pattern works - just need to apply to phone app
- Consider creating reusable adaptive wrapper components to reduce duplication

---

## 📝 Version History

| Date | Version | Changes |
|------|---------|---------|
| 2026-01-30 | 1.0 | Initial document created with 5 phases, 22 tasks identified |

---

**Next Steps**:
1. ✅ Document created
2. 🔄 Implement Phase 1.1: Adaptive Navigation System
3. ⏳ Test on Pixel Tablet
4. ⏳ Continue with remaining phases
