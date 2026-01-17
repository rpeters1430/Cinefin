# TV Shows UI - Material 3 Expressive Upgrade Plan

## Current State

### TV Shows List Screen (`TVShowsScreen` + `TVShowsContent`)
- Standard Scaffold with `TVShowsTopBar`
- FilterChips for filtering
- Grid/list/carousel view modes:
  - Grid: `PosterMediaCard`
  - List: `ExpressiveCompactCard`
  - Carousel: `ExpressiveMediaCarousel`

### TV Show Detail / Seasons Screen (`TVSeasonScreen`)
- Expressive loading/error/empty states
- Hero header with backdrop + logo
- List of `ExpressiveSeasonCard` items with badges and metadata

### Season Episodes List (`TVEpisodesScreen`)
- Standard `TopAppBar`
- Custom `ExpressiveEpisodeRow` cards for each episode
- Expressive loading/error/empty states

### TV Episode Detail on TV Devices (`TvItemDetailScreen`)
- Uses Android TV Material3 components (`androidx.tv.material3`)
- TV cards and buttons rather than Expressive components (TV-specific package)

---

## Upgrade Plan

### 1. TV Shows List Screen

**Goal:** Elevate hierarchy and interactivity with expressive toolbar, list items, and menus.

- **Toolbar:** Replace `TVShowsTopBar` with `ExpressiveFloatingToolbar` or `ExpressiveCompactToolbar` for scroll-aware elevation and action grouping
- **Filters:** Replace FilterChip rows with `ExpressiveSegmentedListItem` or `ExpressiveMediaListItem` for better filter context and selection state
- **Grid cards:** Upgrade `PosterMediaCard` to `ExpressiveMediaCard` for expressive state layers, gradient overlays, and consistent rating/favorite indicators
- **List mode:** Upgrade from `ExpressiveCompactCard` to `ExpressiveMediaListItem` for denser metadata and better visual hierarchy (overline, headline, supporting text). Add inline watched indicator and rating badges
- **Menus:** Use `ExpressiveMediaActionsMenu` or `ExpressiveSelectableMenuItem` for sort/view/filter actions

### 2. TV Show Detail / Seasons Screen

**Goal:** Emphasize series hero content and season list with expressive components.

- **Hero section:** Use `ExpressiveHeroCarousel` for the series backdrop + logo area to get richer motion and consistent overlay gradients. Enhance or replace `SeriesDetailsHeader` while preserving layout intent
- **Season list:** Convert season entries to `ExpressiveMediaListItem` to show season poster, unplayed badges, and supporting text with built-in expressive typography and spacing
- **Season actions:** Add `ExpressiveMediaActionsMenu` anchored to each season card for contextual actions (Play Next Unwatched, Mark Season Watched)
- **Cast/crew:** Switch from plain cards to `ExpressiveMediaListItem` for standardized avatar + text hierarchy and state layers on focus/press

### 3. Season Episodes List

**Goal:** Make each episode row more expressive and consistent with TV show list.

- **Toolbar:** Replace `TopAppBar` with `ExpressiveFloatingToolbar` to unify with TV shows list and enable scroll/elevation transitions. Simplify manual loading indicator
- **Episode rows:** Replace `ExpressiveEpisodeRow` with `ExpressiveMediaListItem` (thumbnail + title + supporting info + badges) to align with new expressive list style and reduce custom layout boilerplate
- **Episode actions:** Add inline `ExpressiveMediaActionsMenu` per episode for quick actions (play, mark watched, add to queue) without long-press-only workflows

### 4. TV Item/Episode Detail on Android TV

**Goal:** Align with TV Material3 expressive style for larger screens.

This screen uses Android TV Material3 components (`androidx.tv.material3`). Adopt Material3 expressive patterns where available:
- Richer card elevations
- Branded hero sections
- Expressive colors/typography

Keep TV-specific components while applying consistent visual language.

---

## Suggested Execution Order

1. **TV shows list** - Expressive toolbar + list item upgrades (highest impact for navigation/browse)
2. **Season episodes list** - Expressive list items + menus (aligns episode browsing with show browsing)
3. **TV show detail / seasons** - Expressive hero and season list items for premium presentation
4. **TV detail on Android TV** - Apply consistent visual language within TV Material3 constraints
