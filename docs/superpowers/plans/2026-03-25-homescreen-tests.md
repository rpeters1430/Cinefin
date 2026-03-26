# Home Screen Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add comprehensive unit and instrumentation tests for the mobile home screen, covering pure helpers in `HomeContent.kt` and Compose rendering in `MobileExpressiveHomeContent`.

**Architecture:** One production visibility change unlocks direct testing of the phone layout composable. Two new test files cover it: a JVM unit test file for pure Kotlin helpers, and an instrumentation test file for Compose rendering and interactions.

**Tech Stack:** Kotlin, JUnit4, MockK, Jetpack Compose Test (`createComposeRule`), `StandardTestDispatcher`, Gradle instrumentation tests

**Spec:** `docs/superpowers/specs/2026-03-25-homescreen-tests-design.md`

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveHomeScreen.kt` | Modify line 435 | Change `MobileExpressiveHomeContent` from `private` → `internal @VisibleForTesting` |
| `app/src/test/java/com/rpeters/jellyfin/ui/screens/home/HomeContentHelpersTest.kt` | Create | JVM unit tests for `getContinueWatchingItems`, `itemSubtitle`, `toCarouselItem` |
| `app/src/androidTest/java/com/rpeters/jellyfin/ui/screens/ImmersiveHomeScreenTest.kt` | Create | Compose instrumentation tests for section visibility and interactions |

---

## Task 1: Make `MobileExpressiveHomeContent` testable

**Files:**
- Modify: `app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveHomeScreen.kt:433-435`

- [ ] **Step 1: Change visibility of `MobileExpressiveHomeContent`**

In `ImmersiveHomeScreen.kt`, find the function declaration at line ~435 (search for `private fun MobileExpressiveHomeContent`) and change it to `internal`. Add `@VisibleForTesting` on the line above `@OptIn`.

Before:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileExpressiveHomeContent(
```

After:
```kotlin
import androidx.annotation.VisibleForTesting  // add to imports at top of file

@VisibleForTesting
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileExpressiveHomeContent(
```

- [ ] **Step 2: Verify the project still builds**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveHomeScreen.kt
git commit -m "refactor: expose MobileExpressiveHomeContent as internal for testing"
```

---

## Task 2: Unit tests — `getContinueWatchingItems`

**Files:**
- Create: `app/src/test/java/com/rpeters/jellyfin/ui/screens/home/HomeContentHelpersTest.kt`

- [ ] **Step 1: Create the test file with the first three tests**

```kotlin
package com.rpeters.jellyfin.ui.screens.home

import com.rpeters.jellyfin.ui.viewmodel.MainAppState
import io.mockk.mockk
import org.jellyfin.sdk.model.api.BaseItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeContentHelpersTest {

    // =========================================================
    // getContinueWatchingItems
    // =========================================================

    @Test
    fun `getContinueWatchingItems_withLimit_returnsCorrectCount`() {
        val items = List(5) { mockk<BaseItemDto>(relaxed = true) }
        val state = MainAppState(continueWatching = items)

        val result = getContinueWatchingItems(state, 3)

        assertEquals(3, result.size)
    }

    @Test
    fun `getContinueWatchingItems_withEmptyList_returnsEmpty`() {
        val state = MainAppState(continueWatching = emptyList())

        val result = getContinueWatchingItems(state, 5)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getContinueWatchingItems_limitExceedsSize_returnsAll`() {
        val items = List(2) { mockk<BaseItemDto>(relaxed = true) }
        val state = MainAppState(continueWatching = items)

        val result = getContinueWatchingItems(state, 10)

        assertEquals(2, result.size)
    }
}
```

- [ ] **Step 2: Run the tests and verify they pass**

```bash
./gradlew testDebugUnitTest --tests "com.rpeters.jellyfin.ui.screens.home.HomeContentHelpersTest"
```
Expected: `3 tests, 0 failures`

---

## Task 3: Unit tests — `itemSubtitle`

**Files:**
- Modify: `app/src/test/java/com/rpeters/jellyfin/ui/screens/home/HomeContentHelpersTest.kt`

`itemSubtitle` in `HomeContent.kt` (package `com.rpeters.jellyfin.ui.screens.home`) is a different function from the one in `ImmersiveMediaRow.kt`. These tests target the `HomeContent.kt` version only.

- [ ] **Step 1: Add the `itemSubtitle` tests inside `HomeContentHelpersTest`**

Add these tests after the `getContinueWatchingItems` block. New imports needed at the top: `import io.mockk.every` and `import org.jellyfin.sdk.model.api.BaseItemKind`.

```kotlin
    // =========================================================
    // itemSubtitle (HomeContent.kt version)
    // =========================================================

    @Test
    fun `itemSubtitle_episode_returnsSeriesName`() {
        val item = mockk<BaseItemDto>(relaxed = true) {
            every { type } returns BaseItemKind.EPISODE
            every { seriesName } returns "Breaking Bad"
        }
        assertEquals("Breaking Bad", itemSubtitle(item))
    }

    @Test
    fun `itemSubtitle_episodeNullSeriesName_returnsEmpty`() {
        val item = mockk<BaseItemDto>(relaxed = true) {
            every { type } returns BaseItemKind.EPISODE
            every { seriesName } returns null
        }
        assertEquals("", itemSubtitle(item))
    }

    @Test
    fun `itemSubtitle_movie_returnsProductionYear`() {
        val item = mockk<BaseItemDto>(relaxed = true) {
            every { type } returns BaseItemKind.MOVIE
            every { productionYear } returns 2022
        }
        assertEquals("2022", itemSubtitle(item))
    }

    @Test
    fun `itemSubtitle_movieNullYear_returnsEmpty`() {
        val item = mockk<BaseItemDto>(relaxed = true) {
            every { type } returns BaseItemKind.MOVIE
            every { productionYear } returns null
        }
        assertEquals("", itemSubtitle(item))
    }

    @Test
    fun `itemSubtitle_series_returnsProductionYear`() {
        val item = mockk<BaseItemDto>(relaxed = true) {
            every { type } returns BaseItemKind.SERIES
            every { productionYear } returns 2019
        }
        assertEquals("2019", itemSubtitle(item))
    }

    @Test
    fun `itemSubtitle_audio_returnsFirstArtist`() {
        val item = mockk<BaseItemDto>(relaxed = true) {
            every { type } returns BaseItemKind.AUDIO
            every { artists } returns listOf("Artist A", "Artist B")
        }
        assertEquals("Artist A", itemSubtitle(item))
    }

    @Test
    fun `itemSubtitle_audioNoArtists_returnsEmpty`() {
        val item = mockk<BaseItemDto>(relaxed = true) {
            every { type } returns BaseItemKind.AUDIO
            every { artists } returns null
        }
        assertEquals("", itemSubtitle(item))
    }
```

- [ ] **Step 2: Run and verify all 10 tests pass**

```bash
./gradlew testDebugUnitTest --tests "com.rpeters.jellyfin.ui.screens.home.HomeContentHelpersTest"
```
Expected: `10 tests, 0 failures`

---

## Task 4: Unit tests — `toCarouselItem`

**Files:**
- Modify: `app/src/test/java/com/rpeters/jellyfin/ui/screens/home/HomeContentHelpersTest.kt`

`toCarouselItem` is an extension function on `BaseItemDto` in `HomeContent.kt`. It maps to `CarouselItem`. The `CarouselItem.type` field always defaults to `MediaType.MOVIE` because the extension never sets it — testing with a `SERIES`-typed input confirms no type mapping occurs.

- [ ] **Step 1: Add the `toCarouselItem` tests**

New imports needed: `import com.rpeters.jellyfin.ui.components.CarouselItem`, `import com.rpeters.jellyfin.ui.components.MediaType`, `import java.util.UUID`.

```kotlin
    // =========================================================
    // toCarouselItem
    // =========================================================

    @Test
    fun `toCarouselItem_mapsFieldsCorrectly`() {
        val itemId = UUID.randomUUID()
        val item = mockk<BaseItemDto>(relaxed = true) {
            every { id } returns itemId
        }

        val result = item.toCarouselItem(
            titleOverride = "Override Title",
            subtitleOverride = "2022",
            imageUrl = "https://example.com/img.jpg",
        )

        assertEquals(itemId.toString(), result.id)
        assertEquals("Override Title", result.title)
        assertEquals("2022", result.subtitle)
        assertEquals("https://example.com/img.jpg", result.imageUrl)
    }

    @Test
    fun `toCarouselItem_respectsTitleOverride`() {
        val item = mockk<BaseItemDto>(relaxed = true) {
            every { id } returns UUID.randomUUID()
            every { name } returns "Original Name"
        }

        val result = item.toCarouselItem(
            titleOverride = "Different Title",
            subtitleOverride = "",
            imageUrl = "",
        )

        assertEquals("Different Title", result.title)
    }

    @Test
    fun `toCarouselItem_respectsSubtitleOverride`() {
        val item = mockk<BaseItemDto>(relaxed = true) {
            every { id } returns UUID.randomUUID()
        }

        val result = item.toCarouselItem(
            titleOverride = "Title",
            subtitleOverride = "Custom Subtitle",
            imageUrl = "",
        )

        assertEquals("Custom Subtitle", result.subtitle)
    }

    @Test
    fun `toCarouselItem_mapsImageUrl`() {
        val item = mockk<BaseItemDto>(relaxed = true) {
            every { id } returns UUID.randomUUID()
        }
        val url = "https://server.local/image.jpg"

        val result = item.toCarouselItem(
            titleOverride = "T",
            subtitleOverride = "",
            imageUrl = url,
        )

        assertEquals(url, result.imageUrl)
    }

    @Test
    fun `toCarouselItem_typeDefaultsToMovie_evenForSeriesItem`() {
        // The extension has no 'type' param so CarouselItem.type always gets its default (MOVIE)
        val item = mockk<BaseItemDto>(relaxed = true) {
            every { id } returns UUID.randomUUID()
            every { type } returns BaseItemKind.SERIES
        }

        val result = item.toCarouselItem(
            titleOverride = "A Series",
            subtitleOverride = "",
            imageUrl = "",
        )

        assertEquals(MediaType.MOVIE, result.type)
    }
```

- [ ] **Step 2: Run all unit tests and verify they all pass**

```bash
./gradlew testDebugUnitTest --tests "com.rpeters.jellyfin.ui.screens.home.HomeContentHelpersTest"
```
Expected: `15 tests, 0 failures`

- [ ] **Step 3: Commit the unit test file**

```bash
git add app/src/test/java/com/rpeters/jellyfin/ui/screens/home/HomeContentHelpersTest.kt
git commit -m "test: add unit tests for HomeContent helpers (getContinueWatchingItems, itemSubtitle, toCarouselItem)"
```

---

## Task 5: Instrumentation test setup + hero carousel tests

**Files:**
- Create: `app/src/androidTest/java/com/rpeters/jellyfin/ui/screens/ImmersiveHomeScreenTest.kt`

`MobileExpressiveHomeContent` is in `ImmersiveHomeScreen.kt` which is package `com.rpeters.jellyfin.ui.screens`. Since the function is now `internal`, it is accessible from `androidTest` sources in the same module.

The composable's full signature:
```
appState: MainAppState,
contentLists: HomeContentLists,
getImageUrl: (BaseItemDto) -> String?,
getBackdropUrl: (BaseItemDto) -> String?,
getSeriesImageUrl: (BaseItemDto) -> String?,
onItemClick: (BaseItemDto) -> Unit,
onItemLongPress: (BaseItemDto) -> Unit,
onLibraryClick: (BaseItemDto) -> Unit,
viewingMood: String?,
contentPadding: PaddingValues,
modifier: Modifier = Modifier
```

Key point: `viewingMood` is a **direct parameter**, not derived from `appState.viewingMood` inside the composable. Always pass it explicitly.

- [ ] **Step 1: Create the test file with helpers and hero carousel tests**

```kotlin
package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.jellyfin.ui.screens.home.HomeContentLists
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import com.rpeters.jellyfin.ui.viewmodel.MainAppState
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ImmersiveHomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule(
        effectContext = StandardTestDispatcher(),
    )

    // =========================================================
    // Test helpers
    // =========================================================

    private fun makeMovie(name: String = "Test Movie"): BaseItemDto =
        mockk<BaseItemDto>(relaxed = true) {
            io.mockk.every { id } returns UUID.randomUUID()
            io.mockk.every { this@mockk.name } returns name
            io.mockk.every { type } returns BaseItemKind.MOVIE
            // dateCreated: relaxed mock returns null for nullable fields, no stub needed
        }

    private fun makeEpisode(
        name: String = "Test Episode",
        seriesName: String = "Test Series",
    ): BaseItemDto =
        mockk<BaseItemDto>(relaxed = true) {
            io.mockk.every { id } returns UUID.randomUUID()
            io.mockk.every { this@mockk.name } returns name
            io.mockk.every { type } returns BaseItemKind.EPISODE
            io.mockk.every { this@mockk.seriesName } returns seriesName
            // dateCreated: relaxed mock returns null for nullable fields, no stub needed
        }

    private fun makeLibrary(name: String = "Movies"): BaseItemDto =
        mockk<BaseItemDto>(relaxed = true) {
            io.mockk.every { id } returns UUID.randomUUID()
            io.mockk.every { this@mockk.name } returns name
            io.mockk.every { type } returns BaseItemKind.COLLECTION_FOLDER
            // dateCreated: relaxed mock returns null for nullable fields, no stub needed
        }

    /** Renders [MobileExpressiveHomeContent] with all required params.
     *  Pass overrides for whatever the test actually exercises. */
    private fun setHomeContent(
        appState: MainAppState = MainAppState(),
        contentLists: HomeContentLists = HomeContentLists(),
        viewingMood: String? = null,
        onItemClick: (BaseItemDto) -> Unit = {},
        onItemLongPress: (BaseItemDto) -> Unit = {},
        onLibraryClick: (BaseItemDto) -> Unit = {},
    ) {
        composeTestRule.setContent {
            JellyfinAndroidTheme {
                MobileExpressiveHomeContent(
                    appState = appState,
                    contentLists = contentLists,
                    getImageUrl = { null },
                    getBackdropUrl = { null },
                    getSeriesImageUrl = { null },
                    onItemClick = onItemClick,
                    onItemLongPress = onItemLongPress,
                    onLibraryClick = onLibraryClick,
                    viewingMood = viewingMood,
                    contentPadding = PaddingValues(),
                )
            }
        }
    }

    // =========================================================
    // Hero carousel
    // =========================================================

    @Test
    fun heroCarousel_withMovies_isDisplayed() {
        val movie = makeMovie("Inception")
        setHomeContent(contentLists = HomeContentLists(recentMovies = listOf(movie)))

        composeTestRule.onNodeWithText("Inception").assertIsDisplayed()
    }

    @Test
    fun heroCarousel_withNoMovies_isNotRendered() {
        setHomeContent(contentLists = HomeContentLists(recentMovies = emptyList()))

        composeTestRule.onNodeWithText("Inception").assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Run the hero carousel tests on the emulator**

Note: the project uses fish shell. The `#` character starts a comment in fish, so quote the argument or escape it.

```bash
# Run a single test (fish shell — quote the argument)
./gradlew connectedAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.rpeters.jellyfin.ui.screens.ImmersiveHomeScreenTest#heroCarousel_withMovies_isDisplayed'
```

Or run the whole class (simpler):
```bash
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rpeters.jellyfin.ui.screens.ImmersiveHomeScreenTest
```

Expected: both hero carousel tests pass. If `heroCarousel_withMovies_isDisplayed` fails with "node not found", add `composeTestRule.waitForIdle()` after `setHomeContent(...)` before the assertion.

---

## Task 6: Instrumentation tests — section visibility

**Files:**
- Modify: `app/src/androidTest/java/com/rpeters/jellyfin/ui/screens/ImmersiveHomeScreenTest.kt`

- [ ] **Step 1: Add continue watching, next up, and libraries tests**

Add these inside the class after the hero carousel tests:

```kotlin
    // =========================================================
    // Continue watching
    // =========================================================

    @Test
    fun continueWatching_withItems_sectionVisible() {
        val item = makeMovie("Partially Watched Movie")
        setHomeContent(
            appState = MainAppState(continueWatching = listOf(item)),
            contentLists = HomeContentLists(continueWatching = listOf(item)),
        )

        composeTestRule.onNodeWithText("Partially Watched Movie").assertIsDisplayed()
    }

    @Test
    fun continueWatching_empty_sectionAbsent() {
        setHomeContent(
            appState = MainAppState(continueWatching = emptyList()),
            contentLists = HomeContentLists(continueWatching = emptyList()),
        )

        composeTestRule.onNodeWithText("Partially Watched Movie").assertDoesNotExist()
    }

    // =========================================================
    // Next up
    // =========================================================

    @Test
    fun nextUp_withEpisodes_sectionVisible() {
        val episode = makeEpisode("Pilot")
        setHomeContent(
            contentLists = HomeContentLists(recentEpisodes = listOf(episode)),
        )

        composeTestRule.onNodeWithText("Pilot").assertIsDisplayed()
    }

    @Test
    fun nextUp_empty_sectionAbsent() {
        setHomeContent(
            contentLists = HomeContentLists(recentEpisodes = emptyList()),
        )

        composeTestRule.onNodeWithText("Pilot").assertDoesNotExist()
    }

    // =========================================================
    // Libraries — rendered by LibraryNavigationCarousel → LibraryExpressiveCard
    // The item{} block is always emitted; LibraryNavigationCarousel has an early
    // return when the list is empty, so no card text appears.
    // =========================================================

    @Test
    fun libraries_withItems_carouselVisible() {
        val library = makeLibrary("My Movies")
        setHomeContent(appState = MainAppState(libraries = listOf(library)))

        composeTestRule.onNodeWithText("My Movies").assertIsDisplayed()
    }

    @Test
    fun libraries_empty_carouselAbsent() {
        setHomeContent(appState = MainAppState(libraries = emptyList()))

        composeTestRule.onNodeWithText("My Movies").assertDoesNotExist()
    }
```

- [ ] **Step 2: Run the new tests**

```bash
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rpeters.jellyfin.ui.screens.ImmersiveHomeScreenTest
```
Expected: all 8 tests pass.

---

## Task 7: Instrumentation tests — viewing mood widget

**Files:**
- Modify: `app/src/androidTest/java/com/rpeters/jellyfin/ui/screens/ImmersiveHomeScreenTest.kt`

`viewingMood` is a **standalone parameter** to `MobileExpressiveHomeContent`. The condition is `!viewingMood.isNullOrBlank()`. Pass it directly, not via `appState.viewingMood`.

- [ ] **Step 1: Add viewing mood tests**

```kotlin
    // =========================================================
    // Viewing mood widget
    // =========================================================

    @Test
    fun viewingMoodWidget_whenMoodSet_isVisible() {
        setHomeContent(viewingMood = "Action mood")

        composeTestRule.onNodeWithText("Action mood").assertIsDisplayed()
    }

    @Test
    fun viewingMoodWidget_whenMoodNull_isHidden() {
        setHomeContent(viewingMood = null)

        composeTestRule.onNodeWithText("Action mood").assertDoesNotExist()
    }

    @Test
    fun viewingMoodWidget_whenMoodEmpty_isHidden() {
        setHomeContent(viewingMood = "")

        // isNullOrBlank() suppresses empty strings
        composeTestRule.onNodeWithText("").assertDoesNotExist()
    }
```

- [ ] **Step 2: Run**

```bash
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rpeters.jellyfin.ui.screens.ImmersiveHomeScreenTest
```
Expected: all 11 tests pass.

---

## Task 8: Instrumentation tests — interactions and empty state

**Files:**
- Modify: `app/src/androidTest/java/com/rpeters/jellyfin/ui/screens/ImmersiveHomeScreenTest.kt`

For `itemClick_firesWithCorrectItem`: the hero carousel does ID-string matching internally (`heroMovies.firstOrNull { it.id.toString() == selected.id }`). The mock `BaseItemDto`'s `id` must be a **specific, deterministic UUID** — not left to relaxed-mock defaults. Store the item reference and pass it both to `contentLists` and to the `onItemClick` mock so `verify` can match on it.

- [ ] **Step 1: Add interaction and empty state tests**

New import needed: `import io.mockk.mockk` is already there; add `import io.mockk.verify`.

```kotlin
    // =========================================================
    // Interactions
    // =========================================================

    @Test
    fun itemClick_firesWithCorrectItem() {
        val movieId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val movie = mockk<BaseItemDto>(relaxed = true) {
            io.mockk.every { id } returns movieId
            io.mockk.every { name } returns "Click Me"
            io.mockk.every { type } returns BaseItemKind.MOVIE
            // dateCreated: relaxed mock returns null for nullable fields, no stub needed
        }
        val onItemClick = mockk<(BaseItemDto) -> Unit>(relaxed = true)

        setHomeContent(
            contentLists = HomeContentLists(recentMovies = listOf(movie)),
            onItemClick = onItemClick,
        )

        composeTestRule.onNodeWithText("Click Me").performClick()

        verify { onItemClick(movie) }
    }

    // =========================================================
    // Empty state
    // =========================================================

    @Test
    fun emptyState_noMoviesNoLibraries_doesNotCrash() {
        // Should render without exception; no specific content to assert
        setHomeContent(
            appState = MainAppState(),
            contentLists = HomeContentLists(),
            viewingMood = null,
        )
        composeTestRule.waitForIdle()
        // If we get here without an exception the test passes
    }
```

- [ ] **Step 2: Run the full instrumentation suite**

```bash
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rpeters.jellyfin.ui.screens.ImmersiveHomeScreenTest
```
Expected: all 13 tests pass.

- [ ] **Step 3: Commit the instrumentation test file**

```bash
git add app/src/androidTest/java/com/rpeters/jellyfin/ui/screens/ImmersiveHomeScreenTest.kt
git commit -m "test: add instrumentation tests for ImmersiveHomeScreen mobile layout"
```

---

## Task 9: Run full CI test suite to confirm no regressions

- [ ] **Step 1: Run all unit tests**

```bash
./gradlew testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`, no failures.

- [ ] **Step 2: Run all instrumentation tests**

```bash
./gradlew connectedAndroidTest
```
Expected: `BUILD SUCCESSFUL`, no failures.

- [ ] **Step 3: Final commit (if any fixes were needed)**

```bash
git add -p   # stage only intentional changes
git commit -m "test: fix test suite regressions from homescreen test additions"
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `Unresolved reference: MobileExpressiveHomeContent` in instrumentation test | Visibility change not saved or build cache stale | Re-run `assembleDebug`, then clean: `./gradlew clean assembleDebug` |
| `MockKException` on `every { name }` | `name` clashes with Kotlin's own `name` property | Use `every { this@mockk.name } returns "..."` as shown in the helpers |
| Hero carousel test fails with "node not found" | Carousel hasn't finished rendering | Add `composeTestRule.waitForIdle()` before the assertion |
| `itemClick` test: `verify` fails even though click worked | Relaxed mock UUID colliding with carousel's ID-string match | Ensure `id` is stubbed as the **same** specific `UUID` instance stored in `movie` |
| `artists` field mock causes compile error | SDK generates `artists` as `List<String>?` | Stub as `every { artists } returns listOf("Artist A")` (no casting needed) |
