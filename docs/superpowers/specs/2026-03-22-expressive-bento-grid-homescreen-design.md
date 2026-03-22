# Design Doc: Expressive "Bento" Grid Home Screen

**Date:** 2026-03-22
**Status:** Approved
**Topic:** Implementing a dynamic, Material 3 Expressive "Bento Box" layout for the Cinefin home screen.

## 1. Overview
The Cinefin home screen currently uses a standard linear list/carousel approach. This design introduces an "Expressive Grid" or "Bento Box" layout that uses variable card sizes and aspect ratios to create a more dynamic, curated feel for the user.

## 2. Goals
- Replace the current linear/carousel layout with a more visually interesting "Bento Box" grid.
- Use variable card sizes (1-column, 2-column, etc.) to signal importance and variety.
- Leverage the Material 3 Expressive theme (extraLarge shapes, bold typography, expressive motion).
- Ensure the layout remains adaptive to different screen sizes.

## 3. Architecture & Components

### 3.1 Component: `ExpressiveBentoGrid`
A new container component that will replace the primary content area of `ImmersiveHomeScreen.kt`. It will use a `LazyVerticalGrid` with `GridItemSpan` to achieve the variable card sizes.

**Grid Items:**
- **Featured Hero:** `span = { GridItemSpan(2) }`, height `240.dp`. Used for top recommendations or currently watching.
- **Action Tile:** `span = { GridItemSpan(1) }`, height `140.dp`. Used for quick actions (e.g., "Next Episode").
- **Discovery Banner:** `span = { GridItemSpan(2) }`, height `100.dp`. Used for utility sections (e.g., AI Discovery).

### 3.2 Component: `ExpressiveBentoCard`
A versatile card component based on `MaterialTheme.shapes.extraLarge` and `Surface`.

**Styling:**
- **Corner Radius:** `24.dp` or larger (via `MaterialTheme.shapes.extraLarge`).
- **Container Colors:** Uses `primaryContainer`, `secondaryContainer`, and `tertiaryContainer` to categorize content.
- **Typography:** Headlines use `MaterialTheme.typography.headlineSmall` (Bold).

## 4. Implementation Details

### 4.1 Grid Logic
The `ExpressiveBentoGrid` will be implemented using a `LazyVerticalGrid` with `columns = GridCells.Fixed(2)`.

**Example Span Logic:**
```kotlin
LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    modifier = modifier,
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp)
) {
    item(span = { GridItemSpan(2) }) {
        FeaturedHero(...)
    }
    item(span = { GridItemSpan(1) }) {
        ActionTile(...)
    }
    item(span = { GridItemSpan(1) }) {
        ActionTile(...)
    }
    item(span = { GridItemSpan(2) }) {
        DiscoveryBanner(...)
    }
}
```

### 4.2 Adaptive Support
On larger screens (tablets), the grid will expand to `GridCells.Fixed(4)` or more, with the spans adjusted proportionally to maintain the "Bento" look without excessive stretching.

## 5. Testing Strategy

### 5.1 Manual Verification
- **Visual Check:** Verify the "Bento Box" layout feels balanced and the expressive styling is prominent.
- **Responsiveness Check:** Test on both phone and tablet layouts.
- **Navigation Check:** Ensure clicking any card correctly navigates to the detailed view or starts playback.

### 5.2 Automated Tests
- **Screenshot Tests:** Compare the new Bento design against the baseline to ensure dimensions and grid spans match the spec.
- **Compose UI Tests:** Verify that the grid items are rendered and their `onClick` handlers are working.

## 6. Success Criteria
- Home screen is noticeably more dynamic and visually engaging.
- Improved visual hierarchy for media items.
- Full compliance with Material 3 Expressive design principles.
