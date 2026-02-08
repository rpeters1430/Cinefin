# Material 3 Expressive Implementation Guide

This document describes the Material 3 Expressive components and theming features available in the Jellyfin Android app.

## Overview

The Jellyfin Android app uses **Material 3 1.5.0-alpha13**, which includes the latest Material 3 Expressive design system features. Material 3 Expressive is an expansion of Material Design 3 that provides:

- Enhanced visual hierarchy and expressiveness
- Research-backed updates to theming, components, motion, and typography
- More engaging and delightful user experiences
- Better accessibility and usability

## Current Dependencies

- **Material 3**: `androidx.compose.material3:material3:1.5.0-alpha13`
- **Compose BOM**: `2026.01.01`
- **Material 3 Adaptive Navigation Suite**: `1.5.0-alpha13`
- **Material 3 Adaptive**: `1.3.0-alpha07`

## Theming System

### Location
All theme files are in `/app/src/main/java/com/rpeters/jellyfin/ui/theme/`

### Theme Files

#### 1. Theme.kt
Main theme implementation with support for:
- Dynamic colors (Android 12+)
- Multiple theme modes (System, Light, Dark, AMOLED Black)
- Custom accent colors (Jellyfin Purple, Blue, Teal + Material variants)
- Contrast level adjustments (Standard, Medium, High)
- Edge-to-edge layout support

**Key Features:**
- `JellyfinAndroidTheme()` - Main theme composable
- `applyContrastLevel()` - WCAG-compliant contrast adjustments
- `applyAmoledBlackToDynamicColors()` - Pure black for OLED screens

#### 2. ColorSchemes.kt
Comprehensive color scheme definitions:
- **Jellyfin Brand Colors**: Purple (#6200EE), Blue (#2962FF), Teal (#00695C)
- **Light/Dark/AMOLED variants** for each accent color
- **8 accent color options** total (3 Jellyfin + 5 Material)

#### 3. Color.kt
Extended color system including:
- **Expressive Color Tokens**: `ExpressivePrimary`, `ExpressiveSecondary`, etc.
- **Neutral Color Scale**: 11 shades from Neutral0 (black) to Neutral99 (white)
- **Semantic Colors**: Content type indicators (MovieRed, SeriesBlue, MusicGreen)
- **Media-Specific Colors**: Video/audio player controls
- **Accessibility Colors**: High contrast and color blind friendly variants
- **Utility Functions**: `getContentTypeColor()`, `getQualityColor()`, `getStatusColor()`

#### 4. Motion.kt
Material 3 Expressive motion system:
- **MotionScheme API**: `expressiveMotionScheme` and `standardMotionScheme`
- **Easing Curves**: ExpressiveEasing, EmphasizedEasing, StandardEasing
- **Media-Specific Curves**: MediaPlayEasing, MediaSeekEasing, CarouselScrollEasing
- **Duration Tokens**: Short (50-200ms), Medium (250-400ms), Long (450-600ms), Extra Long (700-1000ms)
- **Animation Specs**: Pre-configured tween animations for enter/exit transitions

**Usage Example:**
```kotlin
animateFloatAsState(
    targetValue = if (isVisible) 1f else 0f,
    animationSpec = MotionTokens.expressiveEnter,
)
```

#### 5. Type.kt
Material 3 Expressive typography scale:
- **Display Styles**: Large (57sp), Medium (45sp), Small (36sp)
- **Headline Styles**: Large (32sp), Medium (28sp), Small (24sp)
- **Title Styles**: Large (22sp), Medium (16sp), Small (14sp)
- **Body Styles**: Large (16sp), Medium (14sp), Small (12sp)
- **Label Styles**: Large (14sp), Medium (12sp), Small (11sp)

All styles include proper line heights and letter spacing for optimal readability.

#### 6. Shape.kt
Material 3 shape tokens:
- **Corner Radii**: ExtraSmall (4dp), Small (8dp), Medium (12dp), Large (16dp), ExtraLarge (28dp), Full (50dp)
- **Component-Specific**: ButtonShape, CardShape, DialogShape, FabShape, etc.
- **Media Content**: PosterShape, ThumbnailShape, AvatarShape

#### 7. Elevation.kt
Material 3 elevation system:
- **6 Elevation Levels**: Level0 (0dp) through Level5 (12dp)
- **Component Elevations**: Card, Dialog, FAB, Menu, etc.
- **CardElevations Helpers**: `default()`, `elevated()`, `filled()` with state-aware elevations

#### 8. Interaction.kt
State layer tokens for interactive components:
- **State Layer Opacity**: Hover (0.08), Focus (0.12), Pressed (0.12), Dragged (0.16)
- **Interaction Helpers**: `onSurfacePressed()`, `primaryHover()`, etc.
- **Extension Functions**: `Color.withStateLayer()`

#### 9. Dimensions.kt
Consistent spacing tokens:
- Spacing4, Spacing8, Spacing12, Spacing16, Spacing24, Spacing32, Spacing56

## Expressive Components

### Location
`/app/src/main/java/com/rpeters/jellyfin/ui/components/`

### Available Components

#### 1. ExpressiveCarousel.kt
Material 3 Expressive carousels for media content:
- **ExpressiveHeroCarousel**: Hero content with pager and indicators
- **ExpressiveCompactCarousel**: Horizontal scrolling row for media
- **ExpressiveHeroCard**: Large hero card with gradient overlays
- **ExpressiveCarouselCard**: Compact card for carousel items
- **ExpressiveCarouselIndicators**: Page indicators with animations

**Features:**
- Smooth scrolling with expressive motion curves
- Gradient overlays for better text readability
- Scale animations on interaction
- Optimized for performance with lazy loading

#### 2. ExpressiveCards.kt
Material 3 Expressive cards for media items:
- **ExpressiveMediaCard**: Primary card for movies, shows, music
- **ExpressiveContentCard**: Flexible content card with actions
- **ExpressiveGridCard**: Optimized for grid layouts
- **Three Card Types**: ELEVATED, OUTLINED, FILLED

**Features:**
- Scale animations on press (0.98f scale)
- Rating badges (gold star icons)
- Favorite indicators
- Action buttons (Play, Favorite, More)
- Gradient overlays on images

#### 3. ExpressiveFAB.kt
Material 3 Expressive FAB Menu:
- **ExpressiveFABMenu**: Multi-action FAB with expandable menu
- **FAB Actions**: Play, Add to Queue, Download, Favorite

**Features:**
- Smooth expand/collapse animations
- Rotation animations on main FAB icon
- Individual action FABs slide in/out
- Uses expressive motion curves

#### 4. ExpressiveToolbar.kt
Material 3 Expressive toolbars:
- **ExpressiveTopAppBar**: Enhanced top app bar with custom styling
- **ExpressiveSearchBar**: Search bar with expressive animations
- **ExpressiveToolbarActions**: Action button groups

**Features:**
- Scroll-aware elevation changes
- Smooth color transitions
- Custom title and subtitle styles
- Search integration

#### 5. ExpressiveLoading.kt
Material 3 Expressive loading states:
- **ExpressiveLoadingIndicator**: Animated circular progress
- **ExpressiveShimmerEffect**: Shimmer loading placeholder
- **ExpressiveSkeletonScreen**: Full skeleton loading layouts

**Features:**
- Smooth animations with expressive timing
- Color-aware (adapts to theme)
- Multiple sizes and variants

#### 6. ExpressiveListItems.kt (NEW)
Material 3 Expressive list items introduced in 1.5.0-alpha11:
- **ExpressiveMediaListItem**: Media content with enhanced styling
- **ExpressiveCheckableListItem**: Multi-select with checkboxes
- **ExpressiveSwitchListItem**: Settings items with switches
- **ExpressiveSegmentedListItem**: Categorized content with segment badges
- **MediaLibrarySection**: Pre-built section with list items

**Features:**
- Enhanced visual hierarchy
- Better interaction feedback
- Segmented styling support
- Accessibility improvements
- Support for overline, headline, supporting text, and icons

#### 7. ExpressiveMenus.kt (NEW)
Material 3 Expressive menus introduced in 1.5.0-alpha09+:
- **ExpressiveMediaActionsMenu**: Complete actions menu for media items
- **ExpressiveToggleableMenuItem**: Menu item with switch (new M3 feature)
- **ExpressiveSelectableMenuItem**: Menu item with checkbox (new M3 feature)
- **ExpressiveMenuGroup**: Organized menu sections with headers
- **QualitySelectionMenu**: Example quality picker menu

**Features:**
- Toggleable menu items with switches (Material 3 1.5.0-alpha09+)
- Selectable menu items with checkmarks
- Menu groups with visual dividers
- Enhanced expressive styling
- Custom spacing and padding

## Using Expressive Components

### Example: Media List with Expressive Items

```kotlin
@Composable
fun MediaLibraryScreen(items: List<MediaItem>) {
    LazyColumn {
        item {
            MediaLibrarySection(
                sectionTitle = "Recently Added",
                items = items.map { item ->
                    MediaListItemData(
                        title = item.title,
                        subtitle = item.subtitle,
                        overline = item.year,
                        icon = Icons.Default.PlayArrow,
                        onClick = { /* navigate to details */ }
                    )
                }
            )
        }
    }
}
```

### Example: Media Actions Menu

```kotlin
@Composable
fun MediaItemCard(item: MediaItem) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        ExpressiveMediaCard(
            title = item.title,
            imageUrl = item.imageUrl,
            onMoreClick = { menuExpanded = true }
        )

        ExpressiveMediaActionsMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            onPlayClick = { /* play */ },
            onAddToQueueClick = { /* queue */ },
            onDownloadClick = { /* download */ },
            onFavoriteClick = { /* favorite */ },
            onShareClick = { /* share */ },
            isFavorite = item.isFavorite
        )
    }
}
```

### Example: Settings with Switch List Items

```kotlin
@Composable
fun SettingsScreen(preferences: Preferences) {
    LazyColumn {
        item {
            ExpressiveSwitchListItem(
                title = "Dynamic Colors",
                subtitle = "Use system wallpaper colors",
                checked = preferences.useDynamicColors,
                onCheckedChange = { /* update preference */ },
                leadingIcon = Icons.Default.Palette
            )
        }
    }
}
```

## Motion System Usage

### Expressive Animations

```kotlin
// Use expressive motion for engaging animations
val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.95f else 1.0f,
    animationSpec = MotionTokens.expressiveEnter,
)

// Use standard motion for subtle animations
val alpha by animateFloatAsState(
    targetValue = if (isVisible) 1.0f else 0.0f,
    animationSpec = MotionTokens.standardEnter,
)

// Use MotionScheme for complex animations
val motionScheme = MotionTokens.expressiveMotionScheme
```

### Animation Durations

```kotlin
// Short durations for instant feedback (50-200ms)
MotionTokens.DurationShort2 // 100ms

// Medium durations for most animations (250-400ms)
MotionTokens.DurationMedium2 // 300ms

// Long durations for complex transitions (450-600ms)
MotionTokens.DurationLong1 // 450ms
```

## Best Practices

### 1. Use Expressive Components for Key User Interactions
- Hero carousels for featured content
- Media cards for primary content browsing
- Expressive FABs for primary actions
- Expressive menus for contextual actions

### 2. Apply Consistent Motion
- Use `MotionTokens.expressiveEnter` for engaging enter animations
- Use `MotionTokens.standardExit` for subtle exit animations
- Use appropriate durations (short for feedback, medium for transitions)

### 3. Maintain Visual Hierarchy
- Use display styles for hero content
- Use headline styles for section headers
- Use title styles for card titles
- Use body styles for descriptions

### 4. Ensure Accessibility
- Support all contrast levels (Standard, Medium, High)
- Use semantic colors with proper contrast ratios
- Include content descriptions for icons
- Support dynamic text sizing

### 5. Optimize Performance
- Use lazy loading for lists and grids
- Limit concurrent animations
- Use `remember` for expensive calculations
- Leverage composition locals for theme values

## Migration from Standard Material 3

If you have existing Material 3 components and want to migrate to Expressive variants:

1. **Replace Standard Lists**:
   - `ListItem` → `ExpressiveMediaListItem` for enhanced styling
   - Add switches/checkboxes using `ExpressiveSwitchListItem`

2. **Replace Standard Menus**:
   - Add `ExpressiveToggleableMenuItem` for toggleable options
   - Use `ExpressiveSelectableMenuItem` for single-choice selections
   - Organize with `ExpressiveMenuGroup` for better structure

3. **Update Motion**:
   - Replace manual easing curves with `MotionTokens.expressiveEasing`
   - Use `MotionScheme.expressive()` for comprehensive motion specs

4. **Enhance Cards**:
   - Migrate to `ExpressiveMediaCard` for scale animations
   - Add gradient overlays for better text readability
   - Use appropriate card types (ELEVATED, OUTLINED, FILLED)

## Testing Expressive Features

### Visual Testing
- Test all theme modes (Light, Dark, AMOLED)
- Test all accent colors
- Test all contrast levels
- Verify animations on different devices

### Accessibility Testing
- Use TalkBack to verify screen reader support
- Test with large text sizes
- Verify color contrast ratios
- Test keyboard navigation

### Performance Testing
- Profile animation performance with Composition Tracing
- Monitor memory usage with LeakCanary
- Test on low-end devices (see `DevicePerformanceProfile`)

## References

- [Material Design 3 Documentation](https://m3.material.io/)
- [Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)
- [Compose Material 3 Release Notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)

## Version History

- **1.5.0-alpha13** (January 2026): Current project version
- **1.5.0-alpha11** (December 2025): Expressive list items, multi-aspect carousels, enhanced SearchBar
- **1.5.0-alpha09** (November 2025): Expressive menu items (toggleable, selectable, groups)
- **1.4.0** (Stable): Core Material 3 components
- Earlier versions: Foundation for Expressive design system

## Future Enhancements

Potential features to implement as they become available in future Material 3 releases:
- Material 3 carousel enhancements and new expressive variants as APIs stabilize
- Enhanced search experiences with ExpandedFullScreenContainedSearchBar
- Multi-aspect carousel layouts with lazy grids
- Additional expressive component variants
- Enhanced motion patterns for media playback

---

**Last Updated**: January 30, 2026
**Material 3 Version**: 1.5.0-alpha13
**Compose BOM**: 2026.01.01
