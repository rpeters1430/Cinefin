# Immersive Library Screens Refactor Design

**Date:** 2026-02-24
**Status:** Approved
**Goal:** Eliminate code duplication across the three immersive library browse screens (Movies, TV Shows, Home Videos) by extracting a shared `ImmersiveLibraryScreen` composable.

---

## Problem

`ImmersiveMoviesScreen`, `ImmersiveTVShowsScreen`, and `ImmersiveHomeVideosScreen` share the same structure but duplicate ~70% of their code:

- Hero carousel + adaptive grid layout
- Pagination `LaunchedEffect`
- `ImmersiveScaffold` + `ExpressivePullToRefreshBox` wrapper
- Floating header Row (back button + action button)
- Search FAB
- Empty state display
- Sort state management

Additionally, `ImmersiveHomeVideosScreen` diverges from the other two:
- Non-functional Settings icon in the floating header
- Sort menu hidden in a non-visible top bar
- No error state display
- Directly couples ViewModel instead of using a pure composable + container pattern
- Hero carousel was computed but never rendered

---

## Approach: Shared `ImmersiveLibraryScreen` Composable

### New File: `ui/screens/ImmersiveLibraryScreen.kt`

Contains:
- `ImmersiveLibraryConfig` data class
- `ImmersiveSortOption` data class (replaces 3 private screen-specific enums)
- `ImmersiveLibraryScreen` shared display composable

### Modified Files

| File | Change | Lines (before → after) |
|------|--------|------------------------|
| `ImmersiveMoviesScreen.kt` | Remove display composable, keep container only | ~295 → ~70 |
| `ImmersiveTVShowsScreen.kt` | Remove display composable, keep container only | ~307 → ~80 |
| `ImmersiveHomeVideosScreen.kt` | Add proper container pattern, delegate display to shared composable | ~305 → ~80 |

---

## Component API

```kotlin
data class ImmersiveLibraryConfig(
    val themeColor: Color,
    val emptyStateIcon: ImageVector,
    val emptyStateTitle: String,
    val emptyStateSubtitle: String,
)

data class ImmersiveSortOption(
    val labelRes: Int,
    val key: String,
)

@OptInAppExperimentalApis
@Composable
fun ImmersiveLibraryScreen(
    items: List<BaseItemDto>,            // pre-sorted items for the grid
    featuredItems: List<BaseItemDto>,    // carousel items (empty = no carousel)
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    config: ImmersiveLibraryConfig,
    sortOptions: List<ImmersiveSortOption>,
    selectedSortIndex: Int,
    onSortSelected: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (String) -> Unit,       // item ID string
    onCarouselItemClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onSearchClick: () -> Unit,
    onBackClick: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    buildCarouselItem: (BaseItemDto) -> CarouselItem?,  // caller-supplied mapping
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
)
```

### Key Design Decisions

- **`items` are pre-sorted by the container** — the shared composable is display-only
- **`onItemClick` takes a String ID** — standardized across all three screens (Movies was `BaseItemDto`, TV Shows was already `String`)
- **`buildCarouselItem` lambda** — lets each container control carousel metadata (TV needs `seriesId`, Movies uses `id`)
- **`errorMessage`** — now available to all three screens (was only in HomeVideos before)
- **HomeVideos carousel** — the previously-computed-but-unused `featuredVideos` will now render via `featuredItems`

---

## Internal Layout

```
ImmersiveLibraryScreen
  ├── PerformanceMetricsTracker
  ├── LaunchedEffect (pagination trigger — scroll near end)
  └── Box (fillMaxSize)
      ├── ImmersiveScaffold (topBarVisible=false, search FAB)
      │   └── ExpressivePullToRefreshBox (themeColor)
      │       ├── [errorMessage != null]  ExpressiveErrorState
      │       ├── [items.empty && !loading] ExpressiveSimpleEmptyState
      │       └── [content] LazyVerticalGrid (GridCells.Adaptive 160dp)
      │           ├── hero span item (if featuredItems.isNotEmpty())
      │           │   └── ImmersiveHeroCarousel
      │           └── grid items → ImmersiveMediaCard (SMALL)
      └── Floating header Row (statusBarsPadding)
          ├── Back button (CircleShape Surface)
          └── Sort dropdown (CircleShape Surface + DropdownMenu)
```

---

## Container Responsibilities

Each container:
1. Reads ViewModel state via `collectAsState()`
2. Computes `featuredItems` (e.g. top 5 by dateCreated)
3. Manages sort state (`selectedSortIndex`, pre-sorts `items`)
4. Passes config, data, and callbacks to `ImmersiveLibraryScreen`

### Movies Container
- `featuredItems`: top 5 by `dateCreated`
- Sort: Alphabetical / Recently Added / Year (3 options)
- `buildCarouselItem`: uses `movie.id.toString()`

### TV Shows Container
- `featuredItems`: from `recentEpisodes` (distinct by seriesId), fallback to `tvShows` top 5
- Sort: Alphabetical / Recently Added / Year (3 options)
- `buildCarouselItem`: uses `item.seriesId ?: item.id`

### Home Videos Container
- `featuredItems`: `sortedVideos.take(5)` (carousel now actually renders)
- Sort: Name A–Z / Name Z–A / Date Added (Newest/Oldest) / Date Created (Newest/Oldest) (6 options)
- `buildCarouselItem`: uses `video.id.toString()`
- Non-functional Settings icon removed; sort dropdown replaces it in the floating header

---

## Error Handling (Unified)

| State | Display |
|-------|---------|
| `errorMessage != null` | `ExpressiveErrorState` |
| `items.isEmpty() && !isLoading` | `ExpressiveSimpleEmptyState` |
| `isLoading && items.isEmpty()` | Pull-to-refresh spinner |
| `isLoadingMore` | Carousel/grid continues rendering; pagination footer |

---

## Out of Scope

- No changes to `LibraryTypeScreen` (non-immersive version — already shared)
- No changes to navigation, routes, or ViewModel
- No new features added beyond making HomeVideos carousel visible
