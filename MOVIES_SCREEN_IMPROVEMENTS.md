# Movies Screen Material 3 Expressive Improvements

## Overview
Comprehensive Material 3 expressive design improvements to the movie library screen, focusing on enhanced theming, visual hierarchy, and user experience.

---

## ✅ Implemented Features

### 1. Enhanced Filter Chips
**Location:** `MoviesContent.kt`

**Improvements:**
- ✨ **Bold typography** (`FontWeight.Bold`) when selected for stronger visual emphasis
- 🎨 **Surface tonal elevation** using `surfaceContainerHigh` for unselected state
- 📏 **2dp elevated borders** on selected chips with semantic colors:
  - Primary container & border for basic filters (All, Favorites, etc.)
  - Secondary container & border for smart filters (Recent Releases, High Rated)
  - Tertiary container & border for genre filters (Action, Comedy, Drama, Sci-Fi)
- 🔝 **4dp elevation** on selected chips for depth and interactivity
- 🔍 **Larger icons** (18dp, up from 16dp) for better visibility
- 📝 **Typography upgrade** using `MaterialTheme.typography.labelLarge`

**Code Example:**
```kotlin
FilterChip(
    selected = selectedFilter == filter,
    colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ),
    border = if (selectedFilter == filter) {
        FilterChipDefaults.filterChipBorder(
            borderColor = MaterialTheme.colorScheme.primary,
            borderWidth = 2.dp,
        )
    } else {
        FilterChipDefaults.filterChipBorder(enabled = true)
    },
    elevation = FilterChipDefaults.filterChipElevation(
        elevation = if (selectedFilter == filter) 4.dp else 0.dp,
    ),
)
```

---

### 2. Hero Carousel for Featured Movies
**Location:** `MoviesContent.kt`

**Features:**
- 🎬 **Top 5 highly-rated movies** (7.5+ rating) displayed in auto-scrolling carousel
- 🔄 **15-second auto-scroll** interval for engaging browsing experience
- 📐 **280dp height** for prominent hero content
- 🏆 **Sorted by community rating** to showcase best content first
- ℹ️ **Rich metadata display:**
  - Movie title
  - Production year
  - Community rating (★ format)
  - Runtime (hours & minutes)
- 🎨 Uses official Material 3 `ExpressiveHeroCarousel` component
- 🖼️ Backdrop images with gradient overlays for text readability

**Subtitle Format:**
```
"2024 • ★ 8.5 • 2h 28m"
```

**Implementation:**
```kotlin
ExpressiveHeroCarousel(
    items = featuredMovies.map { movie ->
        CarouselItem(
            id = movie.id.toString(),
            title = movie.name ?: "Unknown",
            subtitle = buildMovieSubtitle(movie),
            imageUrl = getImageUrl(movie) ?: "",
            type = MediaType.MOVIE,
        )
    },
    heroHeight = 280.dp,
    useWavyIndicator = true,
)
```

---

### 3. Improved Movie Card Design
**Location:** `MediaCards.kt`

**Enhanced for:**
- `PosterMediaCard` (Grid view)
- `MediaCard` (Banner view)
- `RecentlyAddedCard` (Compact view)

**Improvements:**
- 📈 **Enhanced elevation system:**
  - Default: 6dp (increased from 4dp)
  - Pressed: 2dp (tactile feedback on press)
  - Hovered: 8dp (desktop/TV interaction support)
- 🎨 **Surface tonal colors:** `surfaceContainerLow` instead of plain `surface`
- 🌊 **Better visual depth** with Material 3 surface elevation
- ♿ **Improved accessibility** with proper state feedback

**Before/After:**
```kotlin
// Before
elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)

// After
elevation = CardDefaults.cardElevation(
    defaultElevation = 6.dp,
    pressedElevation = 2.dp,
    hoveredElevation = 8.dp,
)
colors = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
)
```

---

### 4. Segmented Button for View Mode
**Location:** `MoviesTopBar.kt`

**Features:**
- 🎚️ **Material 3 Segmented Button** replacing circular icon toggle
- 📱 **Clear Grid/List labels** with icons for better UX
- ✨ **Active state indication** with proper Material 3 styling
- 🎯 **18dp icons** with proper sizing
- 🔘 **Rounded shape system** using `SegmentedButtonDefaults.itemShape`

**Implementation:**
```kotlin
SingleChoiceSegmentedButtonRow {
    SegmentedButton(
        selected = viewMode == MovieViewMode.GRID,
        onClick = { onViewModeChange(MovieViewMode.GRID) },
        icon = {
            SegmentedButtonDefaults.Icon(active = viewMode == MovieViewMode.GRID) {
                Icon(imageVector = Icons.Default.GridView, modifier = Modifier.size(18.dp))
            }
        }
    ) {
        Text("Grid", style = MaterialTheme.typography.labelMedium)
    }
    SegmentedButton(
        selected = viewMode == MovieViewMode.LIST,
        onClick = { onViewModeChange(MovieViewMode.LIST) },
        icon = {
            SegmentedButtonDefaults.Icon(active = viewMode == MovieViewMode.LIST) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ViewList, modifier = Modifier.size(18.dp))
            }
        }
    ) {
        Text("List", style = MaterialTheme.typography.labelMedium)
    }
}
```

---

### 5. Surface Tonal Elevation System
**Location:** `MoviesContent.kt`

**Applied to:**
- Movies grid view (`MoviesGrid`)
- Movies list view (`MoviesList`)
- Loading state grid (`MoviesLoadingContent`)

**Implementation:**
```kotlin
LazyVerticalGrid(
    modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)
)
```

**Benefits:**
- 🏗️ **Visual hierarchy** with multiple elevation levels
- 📐 **Material 3 compliance** following design guidelines
- 🎨 **Depth perception** through tonal variations:
  - Background: `surfaceContainerLowest`
  - Cards: `surfaceContainerLow`
  - Chips: `surfaceContainerHigh`

---

### 6. Expressive Pull-to-Refresh Indicator
**Location:** `MoviesScreen.kt`

**Features:**
- 🌊 **Wavy progress indicator** using Material 3 `CircularWavyProgressIndicator`
- 🎨 **Branded color scheme** using `MovieRed` (#E53E3E)
- 📏 **52dp indicator size** for optimal visibility
- ⚡ **Smooth animations** with wavy motion
- 🔄 **Auto-refreshing** support with state management

**Parameters:**
```kotlin
ExpressivePullToRefreshBox(
    isRefreshing = isLoading,
    onRefresh = onRefresh,
    indicatorColor = MovieRed,           // Branded red color
    indicatorSize = 52.dp,                // Large, visible indicator
    useWavyIndicator = true,              // Material 3 wavy animation
)
```

**Visual Features:**
- 🌀 **Wavy animation** (amplitude: 0.12f, wavelength: 32dp, speed: 16dp)
- 🎯 **Determinate progress** while pulling (shows pull distance)
- ⚪ **Indeterminate animation** while refreshing
- 🔴 **MovieRed color** for thematic consistency
- 💫 **20% opacity track color** for subtle background

**Technical Details:**
```kotlin
CircularWavyProgressIndicator(
    modifier = Modifier.size(52.dp),
    color = MovieRed,                    // #E53E3E
    trackColor = MovieRed.copy(alpha = 0.2f),
    amplitude = 0.12f,                   // Wave height
    wavelength = 32.dp,                  // Distance between waves
    waveSpeed = 16.dp,                   // Animation speed
)
```

---

## Material 3 Design Principles Applied

### ✨ Expressive Design
- Bold typography for selected states
- Dynamic animations and transitions
- Wavy progress indicators
- Enhanced elevation and shadows

### 🎨 Surface Tonal System
- Multi-level elevation hierarchy
- Proper use of surface containers:
  - `surfaceContainerLowest` - Backgrounds
  - `surfaceContainerLow` - Cards
  - `surfaceContainerHigh` - Chips

### 📱 Touch Target Optimization
- Proper sizing (48dp minimum)
- Clear active states
- Adequate spacing between elements

### ♿ Accessibility
- Clear labels on all interactive elements
- Proper contrast ratios
- Semantic color roles
- State feedback (pressed, hovered, selected)

### 🎭 Motion Design
- Smooth transitions using `MotionTokens`
- Auto-scroll animations (15s interval)
- Scale animations on card press
- Wavy refresh indicator

---

## Color Palette

### Primary Colors
- **Primary:** Theme-based (customizable)
- **Movie Red:** `#E53E3E` (for movies-specific elements)

### Surface Elevation
- **Surface Container Lowest:** Background layer
- **Surface Container Low:** Card layer
- **Surface Container High:** Chip layer

### Semantic Colors
- **Primary Container:** Basic filters
- **Secondary Container:** Smart filters
- **Tertiary Container:** Genre filters

---

## Files Modified

1. ✅ `MoviesContent.kt` - Filter chips, hero carousel, surface elevation
2. ✅ `MoviesTopBar.kt` - Segmented button for view mode
3. ✅ `MoviesScreen.kt` - Expressive pull-to-refresh
4. ✅ `MediaCards.kt` - Enhanced card elevation and colors

---

## Dependencies Used

- ✅ Material 3 v1.5.0-alpha12 (Expressive components)
- ✅ Material 3 Carousel (official component)
- ✅ Material 3 Segmented Button
- ✅ Material 3 Wavy Progress Indicator
- ✅ Compose BOM 2026.01.00

---

## Performance Considerations

- ✅ Used `remember` for expensive computations
- ✅ Proper key usage in LazyColumn/LazyRow
- ✅ Optimized image loading with ImageSize enum
- ✅ Efficient state management with StateFlow
- ✅ Minimal recomposition with stable keys

---

## Future Enhancements (Optional)

### Bottom Sheet for Advanced Filtering
- Replace dropdown menu with Material 3 bottom sheet
- Grouped filters with section headers
- Sort options with radio buttons
- Clear and apply actions

### Floating Action Button
- Quick actions menu (shuffle, favorites, random movie)
- Uses `ExpressiveExtendedFAB`
- Themed with primary container color

### Enhanced Empty States
- Illustrations with animations
- Action buttons (add movies, refresh)
- Better messaging

### Pull-to-Refresh Customization
- Spring physics for natural feel
- Haptic feedback on refresh trigger
- Custom threshold for refresh activation

---

## Testing Recommendations

1. **Visual Testing**
   - Test with different theme modes (Light/Dark/AMOLED)
   - Verify accent color changes
   - Check contrast levels

2. **Interaction Testing**
   - Filter chip selection
   - Carousel auto-scroll
   - Pull-to-refresh gesture
   - View mode toggle
   - Card press states

3. **Performance Testing**
   - Scroll performance with 100+ movies
   - Memory usage with images
   - Animation smoothness

4. **Accessibility Testing**
   - TalkBack navigation
   - Contrast ratio verification
   - Touch target sizes

---

## Summary

The Movies Screen now features:
- 🎨 **Rich Material 3 theming** with expressive components
- 🎬 **Hero carousel** showcasing top-rated content
- 🎯 **Enhanced filtering** with visual feedback
- 📱 **Modern UI patterns** (segmented buttons, wavy indicators)
- ♿ **Better accessibility** and user feedback
- 🌊 **Branded pull-to-refresh** with MovieRed color and wavy animation
- 🏗️ **Proper visual hierarchy** with surface elevation

All improvements follow Material 3 design guidelines and maintain consistency with the existing Jellyfin Android app architecture.
