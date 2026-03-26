# Home Screen Tests Design

**Date:** 2026-03-25
**Status:** Approved

## Overview

Add comprehensive tests for the mobile home screen across two layers:
- **Unit tests** for pure Kotlin helpers in `HomeContent.kt`
- **Instrumentation UI tests** for `ImmersiveHomeScreen` rendering and interactions

No existing home screen UI tests exist. `MainAppViewModelTest.kt` is disabled. This fills the gap in coverage for the home screen's data derivation logic and Compose rendering.

---

## Architecture

### Files Created

| File | Type | Location |
|---|---|---|
| `HomeContentHelpersTest.kt` | Unit (JVM) | `app/src/test/.../ui/screens/home/` |
| `ImmersiveHomeScreenTest.kt` | Instrumentation | `app/src/androidTest/.../ui/screens/` |

### Patterns

- MockK for mocking `BaseItemDto` (use `relaxed = true` + explicit stubs for accessed properties)
- `StandardTestDispatcher` for coroutine control (matches existing `MediaCardsTest` pattern)
- `createComposeRule(effectContext = StandardTestDispatcher())` for instrumentation tests
- `JellyfinAndroidTheme` wrapper on all Compose content
- Image URL lambdas return `null` to skip network calls in UI tests
- `@OptIn(ExperimentalCoroutinesApi::class)` where needed

---

## Unit Tests: `HomeContentHelpersTest.kt`

**Package:** `com.rpeters.jellyfin.ui.screens.home`

Tests the internal pure functions and data structures in `HomeContent.kt`.

### `getContinueWatchingItems`

| Test | Condition | Expected |
|---|---|---|
| `getContinueWatchingItems_withLimit_returnsCorrectCount` | 5 items, limit 3 | Returns 3 items |
| `getContinueWatchingItems_withEmptyList_returnsEmpty` | Empty `continueWatching` | Returns empty list |
| `getContinueWatchingItems_limitExceedsSize_returnsAll` | 2 items, limit 10 | Returns all 2 items |

### `itemSubtitle`

| Test | Item type | Expected |
|---|---|---|
| `itemSubtitle_episode_returnsSeriesName` | EPISODE with seriesName "Breaking Bad" | `"Breaking Bad"` |
| `itemSubtitle_episodeNullSeriesName_returnsEmpty` | EPISODE, seriesName null | `""` |
| `itemSubtitle_movie_returnsProductionYear` | MOVIE, year 2022 | `"2022"` |
| `itemSubtitle_movieNullYear_returnsEmpty` | MOVIE, year null | `""` |
| `itemSubtitle_series_returnsProductionYear` | SERIES, year 2019 | `"2019"` |
| `itemSubtitle_audio_returnsFirstArtist` | AUDIO, artists `["Artist A", "Artist B"]` | `"Artist A"` |
| `itemSubtitle_audioNoArtists_returnsEmpty` | AUDIO, artists null | `""` |

### `toCarouselItem`

| Test | Condition | Expected |
|---|---|---|
| `toCarouselItem_mapsFieldsCorrectly` | Item with id, name, year, backdropUrl | `CarouselItem` with correct id/title/subtitle/imageUrl |
| `toCarouselItem_respectsTitleOverride` | titleOverride != item.name | Uses titleOverride, not item.name |
| `toCarouselItem_respectsSubtitleOverride` | subtitleOverride provided | Uses subtitleOverride |

### `HomeContentLists`

| Test | Condition | Expected |
|---|---|---|
| `homeContentLists_defaultConstruction_allEmpty` | Default constructor | All list fields are empty |

---

## Instrumentation Tests: `ImmersiveHomeScreenTest.kt`

**Package:** `com.rpeters.jellyfin.ui.screens`

Tests `MobileExpressiveHomeContent` (phone layout path) rendered via `composeTestRule.setContent`.

### Test Helpers

```kotlin
fun makeMovie(name: String = "Test Movie"): BaseItemDto
fun makeEpisode(name: String = "Test Episode"): BaseItemDto
fun makeLibrary(name: String = "Movies"): BaseItemDto
fun makeAppState(...): MainAppState
```

All `BaseItemDto` mocks use `relaxed = true` with explicit stubs for `id`, `name`, `type`, `dateCreated`, and any fields accessed by the composable.

### Hero Carousel

| Test | State | Assertion |
|---|---|---|
| `heroCarousel_withMovies_isDisplayed` | 3 movies in `recentlyAddedByTypes[MOVIE]` | First movie name visible |
| `heroCarousel_withNoMovies_isNotRendered` | Empty movies list | No carousel item text visible |

### Continue Watching

| Test | State | Assertion |
|---|---|---|
| `continueWatching_withItems_sectionVisible` | 2 items in `continueWatching` | Section renders item names |
| `continueWatching_empty_sectionAbsent` | Empty list | Continue watching item names absent |

### Next Up

| Test | State | Assertion |
|---|---|---|
| `nextUp_withEpisodes_sectionVisible` | 1 episode in `recentlyAddedByTypes[EPISODE]` | Episode name visible |
| `nextUp_empty_sectionAbsent` | Empty | Episode name absent |

### Libraries

| Test | State | Assertion |
|---|---|---|
| `libraries_withItems_carouselVisible` | 2 libraries | Library names visible |
| `libraries_empty_carouselAbsent` | Empty | Library names absent |

### Viewing Mood Widget

| Test | State | Assertion |
|---|---|---|
| `viewingMoodWidget_whenMoodSet_isVisible` | `viewingMood = "Action mood"` | Text "Action mood" displayed |
| `viewingMoodWidget_whenMoodNull_isHidden` | `viewingMood = null` | Text absent |

### Interactions

| Test | Action | Assertion |
|---|---|---|
| `itemClick_firesWithCorrectItem` | Click on a movie card | `onItemClick` called with that item |
| `emptyState_noMoviesNoLibraries_doesNotCrash` | Render with all-empty state | Composable renders without exception |

---

## Test Data Strategy

- `BaseItemDto` mocks: `mockk<BaseItemDto>(relaxed = true)` with `every { id } returns UUID.randomUUID()` and explicit stubs for each accessed property
- Image lambdas always return `null` in UI tests (avoids Coil network calls)
- `MainAppState` constructed directly — no ViewModel needed in UI tests
- `onItemClick`, `onLibraryClick`, etc. passed as `mockk(relaxed = true)` lambdas and verified with `verify`

---

## What Is Not Tested Here

- Tablet bento grid layout (`ExpressiveBentoGrid`) — separate concern
- `ImmersiveScaffold` overlay animations — animation testing is flaky and low value
- Actual image loading — covered by Coil's own tests
- `MainAppViewModel` data loading — covered in existing `MainAppViewModelLibraryLoadTest`, `MainAppViewModelDeleteItemTest`, etc.
