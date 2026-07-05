# Intro-Skipper MediaSegments Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the chapter-name-guessing heuristic that drives the video player's "Skip Intro"/"Skip Credits" buttons with real segment data from Jellyfin's official MediaSegments API, which the intro-skipper plugin populates.

**Architecture:** Add a `getMediaSegments(itemId)` method to the repository layer that calls the already-bundled SDK `ApiClient.mediaSegmentsApi.getItemSegments(...)`. `VideoPlayerMetadataManager.loadSkipMarkers` calls it first; if it returns real `INTRO`/`OUTRO` segments, those drive `VideoPlayerState.introStartMs/introEndMs/outroStartMs/outroEndMs`. If the call errors (old server, plugin not installed, no segments computed) or returns nothing, it falls back to the existing chapter-name heuristic unchanged. No UI-layer code changes — `SkipIntroOutroButtons` already consumes those four state fields correctly.

**Tech Stack:** Kotlin, Jellyfin Android SDK 1.8.11 (`org.jellyfin.sdk:jellyfin-core`, transitively `jellyfin-api`/`jellyfin-model`), Hilt, JUnit4 + MockK for unit tests.

## Global Constraints

- Ticks-to-milliseconds conversion is `ticks / 10_000` (100ns ticks), matching the existing chapter-based conversion in `VideoPlayerMetadataManager`.
- Scope is phone/tablet only (`VideoPlayerActivity` + `ExpressiveVideoControls`/`VideoPlayerOverlays`). Do not touch `TvVideoPlayerControls.kt` or `TvPlayerControls_Backup.kt` — verified unwired, out of scope.
- Segment types handled: `MediaSegmentType.INTRO` and `MediaSegmentType.OUTRO` only.
- No new user-facing preference/toggle; skip stays tap-to-skip via the existing buttons.
- `VideoPlayerState`, `SkipIntroOutroButtons`, `ExpressiveVideoControls`, `VideoPlayerViewModel` must NOT be modified — only `IJellyfinRepository`, `JellyfinRepository`, and `VideoPlayerMetadataManager`.
- Use `StandardTestDispatcher`/`runTest` and `coEvery` (not `every`) for suspend/Flow mocks, per this repo's testing conventions.

---

### Task 1: Add `getMediaSegments` to the repository layer

**Files:**
- Modify: `app/src/main/java/com/rpeters/jellyfin/data/repository/IJellyfinRepository.kt`
- Modify: `app/src/main/java/com/rpeters/jellyfin/data/repository/JellyfinRepository.kt`
- Test: `app/src/test/java/com/rpeters/jellyfin/data/repository/JellyfinRepositoryTest.kt`

**Interfaces:**
- Produces: `suspend fun IJellyfinRepository.getMediaSegments(itemId: String): ApiResult<List<MediaSegmentDto>>` — consumed by Task 2's `VideoPlayerMetadataManager`.

- [ ] **Step 1: Write the failing test**

Add to `app/src/test/java/com/rpeters/jellyfin/data/repository/JellyfinRepositoryTest.kt`. First add these imports at the top of the file (alongside the existing imports):

```kotlin
import io.mockk.coEvery
import io.mockk.secondArg
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.mediaSegmentsApi
import org.jellyfin.sdk.api.operations.MediaSegmentsApi
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentDtoQueryResult
import org.jellyfin.sdk.model.api.MediaSegmentType
import java.util.UUID
```

Then add this test to the `JellyfinRepositoryTest` class, after the existing `JellyfinRepository can be instantiated` test:

```kotlin
    @Test
    fun `getMediaSegments returns mapped segments on success`() = runTest {
        // Given
        val server = JellyfinServer(
            id = "server-1",
            name = "Test Server",
            url = "https://demo.jellyfin.org",
            isConnected = true,
            userId = "test-user-id",
            username = "test-user",
            accessToken = "test-access-token",
        )
        serverFlow.value = server
        connectedFlow.value = true

        val itemUuid = UUID.randomUUID()
        val introSegment = MediaSegmentDto(
            id = UUID.randomUUID(),
            itemId = itemUuid,
            type = MediaSegmentType.INTRO,
            startTicks = 0L,
            endTicks = 900_000_000L,
        )
        val mockApiClient = mockk<ApiClient>()
        val mockMediaSegmentsApi = mockk<MediaSegmentsApi>()
        every { mockApiClient.mediaSegmentsApi } returns mockMediaSegmentsApi
        coEvery { mockMediaSegmentsApi.getItemSegments(any(), any()) } returns Response(
            content = MediaSegmentDtoQueryResult(
                items = listOf(introSegment),
                totalRecordCount = 1,
                startIndex = 0,
            ),
            status = 200,
            headers = emptyMap(),
        )
        coEvery {
            mockSessionManager.executeWithAuth<List<MediaSegmentDto>>(any(), any())
        } coAnswers {
            val block = secondArg<suspend (JellyfinServer, ApiClient) -> List<MediaSegmentDto>>()
            block(server, mockApiClient)
        }

        // When
        val result = repository.getMediaSegments(itemUuid.toString())

        // Then
        assertTrue(result is ApiResult.Success)
        val segments = (result as ApiResult.Success).data
        assertEquals(1, segments.size)
        assertEquals(MediaSegmentType.INTRO, segments[0].type)
        assertEquals(0L, segments[0].startTicks)
        assertEquals(900_000_000L, segments[0].endTicks)
    }

    @Test
    fun `getMediaSegments returns error when session manager throws`() = runTest {
        // Given
        coEvery {
            mockSessionManager.executeWithAuth<List<MediaSegmentDto>>(any(), any())
        } throws IllegalStateException("no active server")

        // When
        val result = repository.getMediaSegments(UUID.randomUUID().toString())

        // Then
        assertTrue(result is ApiResult.Error)
    }
```

This requires `import io.mockk.every` — the file already imports `io.mockk.every` and `io.mockk.mockk`, so no change needed there beyond the block above.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.rpeters.jellyfin.data.repository.JellyfinRepositoryTest"`
Expected: FAIL — compilation error, `getMediaSegments` is unresolved on `IJellyfinRepository`/`JellyfinRepository`.

- [ ] **Step 3: Add the method to the interface**

In `app/src/main/java/com/rpeters/jellyfin/data/repository/IJellyfinRepository.kt`, add the import:

```kotlin
import org.jellyfin.sdk.model.api.MediaSegmentDto
```

Then add this line directly after `suspend fun getItemDetails(itemId: String): ApiResult<BaseItemDto>` (currently line 67):

```kotlin
    suspend fun getMediaSegments(itemId: String): ApiResult<List<MediaSegmentDto>>
```

- [ ] **Step 4: Implement in JellyfinRepository**

In `app/src/main/java/com/rpeters/jellyfin/data/repository/JellyfinRepository.kt`, add these imports alongside the existing `org.jellyfin.sdk.*` imports:

```kotlin
import org.jellyfin.sdk.api.client.extensions.mediaSegmentsApi
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
```

Then add this method directly after `getItemDetails` (currently ending at line 836, right before the `// ===== SEARCH METHODS` comment):

```kotlin
    override suspend fun getMediaSegments(itemId: String): ApiResult<List<MediaSegmentDto>> =
        withServerClient("getMediaSegments") { _, client ->
            val uuid = UUID.fromString(itemId)
            client.mediaSegmentsApi.getItemSegments(
                itemId = uuid,
                includeSegmentTypes = listOf(MediaSegmentType.INTRO, MediaSegmentType.OUTRO),
            ).content.items
        }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.rpeters.jellyfin.data.repository.JellyfinRepositoryTest"`
Expected: PASS — all tests in the class green, including the two new ones.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/rpeters/jellyfin/data/repository/IJellyfinRepository.kt app/src/main/java/com/rpeters/jellyfin/data/repository/JellyfinRepository.kt app/src/test/java/com/rpeters/jellyfin/data/repository/JellyfinRepositoryTest.kt
git commit -m "feat: add getMediaSegments to repository for intro-skipper support"
```

---

### Task 2: Use MediaSegments in `VideoPlayerMetadataManager`, falling back to chapter heuristic

**Files:**
- Modify: `app/src/main/java/com/rpeters/jellyfin/ui/player/VideoPlayerMetadataManager.kt`
- Test: `app/src/test/java/com/rpeters/jellyfin/ui/player/VideoPlayerMetadataManagerTest.kt` (new file)

**Interfaces:**
- Consumes: `IJellyfinRepository.getMediaSegments(itemId: String): ApiResult<List<MediaSegmentDto>>` (Task 1). `IJellyfinRepository.getEpisodeDetails`/`getMovieDetails` (existing). `VideoPlayerStateManager.updateState((VideoPlayerState) -> VideoPlayerState)` (existing, no-arg constructor, exposes `playerState: StateFlow<VideoPlayerState>`).
- Produces: `VideoPlayerMetadataManager.loadSkipMarkers(itemId: String): BaseItemDto?` keeps its existing signature and return type — only its internal data source changes. No callers need updating.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/rpeters/jellyfin/ui/player/VideoPlayerMetadataManagerTest.kt`:

```kotlin
package com.rpeters.jellyfin.ui.player

import com.rpeters.jellyfin.data.repository.IJellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ChapterInfo
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

class VideoPlayerMetadataManagerTest {

    private lateinit var repository: IJellyfinRepository
    private lateinit var stateManager: VideoPlayerStateManager
    private lateinit var metadataManager: VideoPlayerMetadataManager

    private val itemId = UUID.randomUUID()

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        stateManager = VideoPlayerStateManager()
        metadataManager = VideoPlayerMetadataManager(repository, stateManager)
    }

    private fun episodeWithChapters(chapters: List<ChapterInfo>): BaseItemDto =
        BaseItemDto(
            id = itemId,
            name = "Test Episode",
            type = BaseItemKind.EPISODE,
            chapters = chapters,
        )

    private fun introChapter() = ChapterInfo(
        startPositionTicks = 0L,
        name = "Intro",
        imageDateModified = LocalDateTime.now(),
    )

    private fun mainContentChapter() = ChapterInfo(
        startPositionTicks = 300_000_000L, // 30s
        name = "Main",
        imageDateModified = LocalDateTime.now(),
    )

    @Test
    fun `loadSkipMarkers uses MediaSegments when available and ignores chapter heuristic`() = runTest {
        val item = episodeWithChapters(listOf(introChapter(), mainContentChapter()))
        coEvery { repository.getEpisodeDetails(itemId.toString()) } returns ApiResult.Success(item)
        coEvery { repository.getMediaSegments(itemId.toString()) } returns ApiResult.Success(
            listOf(
                MediaSegmentDto(
                    id = UUID.randomUUID(),
                    itemId = itemId,
                    type = MediaSegmentType.INTRO,
                    startTicks = 50_000_000L, // 5s - deliberately different from the chapter-based 0s
                    endTicks = 850_000_000L, // 85s
                ),
                MediaSegmentDto(
                    id = UUID.randomUUID(),
                    itemId = itemId,
                    type = MediaSegmentType.OUTRO,
                    startTicks = 12_000_000_000L,
                    endTicks = 13_000_000_000L,
                ),
            ),
        )

        metadataManager.loadSkipMarkers(itemId.toString())

        val state = stateManager.playerState.value
        assertEquals(5_000L, state.introStartMs)
        assertEquals(85_000L, state.introEndMs)
        assertEquals(1_200_000L, state.outroStartMs)
        assertEquals(1_300_000L, state.outroEndMs)
    }

    @Test
    fun `loadSkipMarkers falls back to chapter heuristic when MediaSegments errors`() = runTest {
        val item = episodeWithChapters(listOf(introChapter(), mainContentChapter()))
        coEvery { repository.getEpisodeDetails(itemId.toString()) } returns ApiResult.Success(item)
        coEvery { repository.getMediaSegments(itemId.toString()) } returns ApiResult.Error("not supported")

        metadataManager.loadSkipMarkers(itemId.toString())

        val state = stateManager.playerState.value
        assertEquals(0L, state.introStartMs)
        assertEquals(30_000L, state.introEndMs)
        assertNull(state.outroStartMs)
        assertNull(state.outroEndMs)
    }

    @Test
    fun `loadSkipMarkers falls back to chapter heuristic when MediaSegments is empty`() = runTest {
        val item = episodeWithChapters(listOf(introChapter(), mainContentChapter()))
        coEvery { repository.getEpisodeDetails(itemId.toString()) } returns ApiResult.Success(item)
        coEvery { repository.getMediaSegments(itemId.toString()) } returns ApiResult.Success(emptyList())

        metadataManager.loadSkipMarkers(itemId.toString())

        val state = stateManager.playerState.value
        assertEquals(0L, state.introStartMs)
        assertEquals(30_000L, state.introEndMs)
    }

    @Test
    fun `loadSkipMarkers leaves all markers null when neither source has data`() = runTest {
        val item = episodeWithChapters(emptyList())
        coEvery { repository.getEpisodeDetails(itemId.toString()) } returns ApiResult.Success(item)
        coEvery { repository.getMediaSegments(itemId.toString()) } returns ApiResult.Success(emptyList())

        metadataManager.loadSkipMarkers(itemId.toString())

        val state = stateManager.playerState.value
        assertNull(state.introStartMs)
        assertNull(state.introEndMs)
        assertNull(state.outroStartMs)
        assertNull(state.outroEndMs)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat testDebugUnitTest --tests "com.rpeters.jellyfin.ui.player.VideoPlayerMetadataManagerTest"`
Expected: FAIL — the first test fails because `loadSkipMarkers` currently ignores `getMediaSegments` entirely and only ever runs the chapter heuristic, so `introStartMs` would be `0L` (from the chapter) instead of the expected `5_000L`.

- [ ] **Step 3: Implement MediaSegments-first logic with fallback**

In `app/src/main/java/com/rpeters/jellyfin/ui/player/VideoPlayerMetadataManager.kt`, add this import:

```kotlin
import org.jellyfin.sdk.model.api.MediaSegmentType
```

Then replace the entire `loadSkipMarkers` function body (lines 23-88) with:

```kotlin
    suspend fun loadSkipMarkers(itemId: String): BaseItemDto? {
        return try {
            val item = when (val ep = repository.getEpisodeDetails(itemId)) {
                is com.rpeters.jellyfin.data.repository.common.ApiResult.Success -> ep.data
                else -> when (val mv = repository.getMovieDetails(itemId)) {
                    is com.rpeters.jellyfin.data.repository.common.ApiResult.Success -> mv.data
                    else -> null
                }
            }

            if (item == null) {
                clearSkipMarkers()
                return null
            }

            if (!applyMediaSegments(itemId)) {
                applyChapterHeuristic(item)
            }

            item
        } catch (_: Exception) {
            null
        }
    }

    private fun clearSkipMarkers() {
        stateManager.updateState { it.copy(
            introStartMs = null,
            introEndMs = null,
            outroStartMs = null,
            outroEndMs = null,
        ) }
    }

    /**
     * Applies real segment data from the Jellyfin MediaSegments API (populated by the
     * intro-skipper plugin on servers that support it). Returns false when no usable
     * segment data was found, so the caller can fall back to the chapter-name heuristic.
     */
    private suspend fun applyMediaSegments(itemId: String): Boolean {
        val segments = when (val result = repository.getMediaSegments(itemId)) {
            is com.rpeters.jellyfin.data.repository.common.ApiResult.Success -> result.data
            else -> return false
        }
        if (segments.isEmpty()) return false

        fun ticksToMs(ticks: Long): Long = ticks / 10_000

        val intro = segments
            .filter { it.type == MediaSegmentType.INTRO }
            .minByOrNull { it.startTicks }
        val outro = segments
            .filter { it.type == MediaSegmentType.OUTRO }
            .minByOrNull { it.startTicks }

        stateManager.updateState { it.copy(
            introStartMs = intro?.let { seg -> ticksToMs(seg.startTicks) },
            introEndMs = intro?.let { seg -> ticksToMs(seg.endTicks) },
            outroStartMs = outro?.let { seg -> ticksToMs(seg.startTicks) },
            outroEndMs = outro?.let { seg -> ticksToMs(seg.endTicks) },
        ) }
        return true
    }

    private fun applyChapterHeuristic(item: BaseItemDto) {
        val chapters = item.chapters ?: emptyList()
        if (chapters.isEmpty()) {
            clearSkipMarkers()
            return
        }

        fun ticksToMs(ticks: Long?): Long? = ticks?.let { it / 10_000 }

        var introStart: Long? = null
        var introEnd: Long? = null
        var outroStart: Long? = null
        var outroEnd: Long? = null

        chapters.forEachIndexed { index, ch ->
            val name = ch.name?.lowercase() ?: ""
            val startMs = ticksToMs(ch.startPositionTicks)
            val nextStartMs = chapters.getOrNull(index + 1)?.startPositionTicks?.let { it / 10_000 }
            val endMs = nextStartMs

            if (introStart == null && ("intro" in name || "opening" in name)) {
                introStart = startMs
                introEnd = endMs
            }
            if (outroStart == null && ("credits" in name || "outro" in name || "ending" in name)) {
                outroStart = startMs
                outroEnd = endMs
            }
        }

        stateManager.updateState { it.copy(
            introStartMs = introStart,
            introEndMs = introEnd,
            outroStartMs = outroStart,
            outroEndMs = outroEnd,
        ) }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat testDebugUnitTest --tests "com.rpeters.jellyfin.ui.player.VideoPlayerMetadataManagerTest"`
Expected: PASS — all four tests green.

- [ ] **Step 5: Run the full existing player/repository test suites to check for regressions**

Run: `./gradlew.bat testDebugUnitTest --tests "com.rpeters.jellyfin.ui.player.*" --tests "com.rpeters.jellyfin.data.repository.*"`
Expected: PASS — no regressions in `VideoPlayerViewModelTest`, `VideoPlayerViewModelInitTest`, or any repository test.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/rpeters/jellyfin/ui/player/VideoPlayerMetadataManager.kt app/src/test/java/com/rpeters/jellyfin/ui/player/VideoPlayerMetadataManagerTest.kt
git commit -m "feat: prefer Jellyfin MediaSegments API for skip intro/credits over chapter heuristic"
```

---

### Task 3: Full-suite verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full unit test suite**

Run: `./gradlew.bat testDebugUnitTest`
Expected: BUILD SUCCESSFUL, no failing tests.

- [ ] **Step 2: Run lint**

Run: `./gradlew.bat lintDebug`
Expected: BUILD SUCCESSFUL (or only pre-existing warnings unrelated to the touched files).

- [ ] **Step 3: Manually verify on device/emulator (if available)**

Play an episode on a server running the intro-skipper plugin (or a Jellyfin 10.9+ server with MediaSegments populated) that has computed INTRO/OUTRO segments for that episode. Confirm:
- "Skip Intro" appears during the real fingerprinted intro window and seeking lands at the segment's `endTicks`.
- "Skip Credits" appears once playback passes the real OUTRO segment start.
- On a server/item with no MediaSegments data (or an older server), confirm the old chapter-based behavior still works when named "Intro"/"Credits" chapters exist, and that no buttons appear when neither source has data.

This step has no automated pass/fail — note results in the PR description or session summary since intro-skipper server setup isn't available in this environment.

- [ ] **Step 4: Commit (only if step 1-2 required fixes)**

If Steps 1-2 required any fixes, stage and commit them with an appropriate `fix:` message. If no fixes were needed, skip this step — there is nothing to commit.

---

## Self-Review Notes

- **Spec coverage:** Task 1 covers spec section "1. Repository: fetch real segments". Task 2 covers "2. Metadata manager: prefer real segments, fall back to chapter heuristic" and "3. No UI changes" (verified by not touching those files). Testing section of the spec is covered by the tests in Tasks 1 and 2. Out-of-scope items (TV, auto-skip, other segment types) are explicitly not touched per Global Constraints.
- **Type consistency:** `getMediaSegments(itemId: String): ApiResult<List<MediaSegmentDto>>` signature is identical across the interface (Task 1 Step 3), implementation (Task 1 Step 4), and the mock setup consumed in Task 2's tests.
- **No placeholders:** all steps contain complete, runnable code.
