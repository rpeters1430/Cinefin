# Offline Downloads & Offline Mode Plan (Cinefin)

This document describes the target UX and a step-by-step implementation plan for:
- Showing **Downloaded** status on item detail screens
- Deleting **only the offline copy**
- A **Downloads** settings screen listing all downloads
- **Offline watch-state tracking** and **sync back to Jellyfin** when online

---

## Goals

### User-facing goals
1. A user can download a video, go fully offline (airplane mode), open the app, and **play the downloaded file**.
2. The item detail screen clearly shows when an item is downloaded:
   - A **Downloaded** indicator (chip/badge)
   - A **Delete offline copy** action that deletes the local file only
3. A settings screen shows:
   - Total space used by downloads
   - List of all downloaded items
   - Delete per item and delete-all options
4. When back online, the app can:
   - Sync watch progress (position) and played state to the Jellyfin server

### Non-goals (for MVP)
- Full offline browsing/search of the entire server library (requires metadata + image caching)
- Multi-user download separation (optional later)
- DRM or protected content handling (out of scope)

---

## UX Requirements

### Item detail screen (Movie/Series/Episode detail)
- Show a **Downloaded** chip/badge when a local offline copy exists.
- Provide a **Delete offline copy** button (deletes local file + local record only).
- Play button behavior:
  - If offline and downloaded → play local file
  - If online → normal streaming (optional: allow “Play offline copy” menu action)

Recommended UI elements:
- AssistChip: “Downloaded”
- AssistChip (optional): “Offline” when device has no network
- TextButton or IconButton: “Delete offline copy”

### Downloads settings screen
- Storage summary:
  - Total download size (sum of local files)
  - Optional: free space indicator
- Downloads list:
  - Title (and season/episode info when relevant)
  - Quality label (e.g., “720p H264”)
  - File size
  - Download date
  - Optional: last played date
- Actions:
  - Delete offline copy (per item)
  - Delete all downloads
  - Optional: “Clear watched downloads”

### Sync watch state when online
- Offline playback must write progress locally.
- When device is online, a background sync uploads progress/played state to Jellyfin.
- Sync should be resilient:
  - retries with backoff
  - avoids spamming (batch events)

---

## Architecture Overview

### Core idea: Playback Source Selection
At play time, choose between:
- **Offline source**: local file URI → Media3/ExoPlayer plays from disk
- **Online source**: Jellyfin stream URL / playback info

Decision rule (MVP):
- If item is downloaded AND (device is offline OR user forced offline playback) → offline
- Else → online

### Data layers
- **Download records**: list of downloaded items with local path and metadata
- **Watch state queue**: offline playback events to be synced later

Recommended persistence:
- Downloads list: **Room** (best) or DataStore (OK for early MVP)
- Sync queue: **Room** (recommended)

---

## Data Model (Recommended)

### Room: `offline_downloads`
Fields (suggested):
- `itemId` (PK)
- `title`
- `mediaType` (MOVIE / EPISODE / OTHER)
- `seriesName` (nullable)
- `seasonNumber` (nullable)
- `episodeNumber` (nullable)
- `runTimeMs` (nullable)
- `qualityLabel` (e.g., “720p H264”)
- `bitrate` (nullable)
- `localFilePath`
- `fileSizeBytes`
- `createdAt`
- `lastPlayedAt` (nullable)
- `downloadState` (DOWNLOADING / COMPLETE / FAILED)

### Room: `offline_sync_queue` (or `offline_play_events`)
Fields (suggested):
- `id` (PK auto)
- `itemId`
- `eventType` (PROGRESS / STOPPED / MARK_PLAYED / MARK_UNPLAYED)
- `positionMs` (nullable)
- `durationMs` (nullable)
- `timestamp`
- `synced` (bool)
- `retryCount`

---

## Implementation Plan (Step-by-Step)

### Phase 0 — Ensure offline playback works end-to-end (1–2 PRs)
✅ Outcome: Downloads → Play works in airplane mode.

Status: ✅ **Complete** (implemented and code-verified)

- `VideoPlayerActivity.createIntent(...)` accepts and forwards `forceOffline`.
- Downloads screen launch path sets `forceOffline = true` for offline playback.
- `VideoPlayerViewModel.initializePlayer()` now branches to local playback when
  `isDownloaded && (!isOnline || forceOffline)`.
- Offline file validation restricts playback to readable files in app-specific storage before
  creating `MediaItem` URIs.

1. **Add a “forceOffline” flag to Player intent**
   - Update `VideoPlayerActivity.createIntent(...)` to accept `forceOffline: Boolean`
   - Pass `forceOffline = true` when launching player from Downloads screen

2. **Add offline branching in `VideoPlayerViewModel.initializePlayer()`**
   - Determine:
     - `isDownloaded(itemId)`
     - `isOnline`
     - `forceOffline`
   - If downloaded and (offline or forceOffline), load Media3 `MediaItem` from local file

3. **Harden local URI usage**
   - Prefer app-specific storage (e.g., `context.getExternalFilesDir(...)`)
   - Ensure Media3 can read the file (file:// or content:// as appropriate)

Acceptance checks:
- Download a video
- Turn on airplane mode
- Go to Downloads
- Tap Play
- Video plays

---

### Phase 1 — Detail screen “Downloaded” badge + delete offline copy (1–2 PRs)
✅ Outcome: Detail screen shows offline availability and can delete local-only.

Status: ✅ **Complete** (implemented and code-verified)

- Detail view models observe download state and expose delete-offline-copy actions.
- Movie and episode detail screens show **Downloaded** and **Offline** chips plus a
  confirmed **Delete offline copy** action.
- Downloaded state now only reports `true` when the local file is still valid/readable in
  app-specific storage (stale files no longer show as downloaded).

4. **Expose download state to detail ViewModel**
   - Add:
     - `observeIsDownloaded(itemId): Flow<Boolean>`
     - `observeDownloadInfo(itemId): Flow<OfflineDownload?>` (optional for size/quality)
     - `deleteOfflineCopy(itemId)`

5. **Add UI chip/badge to detail screen**
   - If downloaded: show “Downloaded” chip (and optionally quality/size)
   - If offline: show “Offline” chip

6. **Add “Delete offline copy” action**
   - Implement delete:
     - Delete local file at `localFilePath`
     - Remove download record only
   - Confirm dialog recommended:
     - “Remove offline copy?” (does not affect server)

Acceptance checks:
- Item detail shows Downloaded status when local file exists
- Delete removes only local copy; streaming still works online

---

### Phase 2 — Downloads settings screen (2–3 PRs)
🚧 Outcome: Manage all downloads in one place.

Status: ✅ **Complete** (implemented and code-verified)

Implemented in this phase:
- Settings route now opens the real Downloads screen.
- Downloads list shows title, quality, size, and timestamp.
- Added bulk **Delete all downloads** action with confirmation.
- Added optional **Clear watched downloads** action with confirmation.
- Added open-detail action per completed download (routes to movie/episode/generic detail).
- Added status hint for non-completed downloads so users know detail view opens after completion.
- Added download preferences backed by DataStore:
  - Download over Wi-Fi only
  - Default download quality

7. **Create Downloads screen (Settings → Downloads)**
   - Display total size used by downloads
   - List downloaded items (title, quality, size, date)
   - Tap item → open detail screen
   - Swipe/delete or menu → delete offline copy

8. **Add settings toggles**
   - “Download over Wi‑Fi only”
   - “Default download quality” (or “Prefer smaller files”)
   - Store in DataStore preferences

9. **Bulk actions**
   - “Delete all downloads”
   - Optional: “Clear watched downloads”

Acceptance checks:
- Downloads screen works offline
- Storage usage updates correctly after deletes

---

### Phase 3 — Offline watch tracking (2–4 PRs)
✅ Outcome: Offline playback updates local watch state.

Status: ✅ **Complete** (implemented and code-verified)

Implemented in this phase:
- Offline queue now supports typed events: `PROGRESS`, `STOPPED`, `MARK_PLAYED`, `MARK_UNPLAYED`.
- Playback progress sync interval is throttled to ~15 seconds.
- `MARK_PLAYED` is emitted automatically once playback crosses the watched threshold (90%).
- Queue coalescing avoids duplicates:
  - latest `PROGRESS` / `STOPPED` per item is kept
  - only latest watched-state event per item is kept
- Queue includes timestamps and is visible via a UI hint in Downloads settings:
  - “Pending watch sync: X update(s) ...”

10. **Record offline playback progress locally**
   - During offline playback:
     - Write PROGRESS every ~15 seconds (throttle)
     - Write STOPPED on pause/stop
     - Write MARK_PLAYED when passing e.g. 90–95% watched
   - Store into Room sync queue (recommended)

11. **Avoid spam and duplicates**
   - Coalesce progress events:
     - Keep the latest position per item if multiple events pending
   - Keep timestamps

Acceptance checks:
- Offline playback updates local progress
- Pending sync events appear in debug logs (or UI indicator)

---

### Phase 3.5 — Reliability hardening (recommended)
✅ Outcome: Offline event queue survives edge cases and sync remains resilient.

Status: ✅ **Complete** (implemented and code-verified)

Implemented in this phase:
- Sessionless replay fallback:
  - If queued `sessionId` is empty, replay uses a deterministic generated session ID.
- Queue durability and corruption recovery:
  - Invalid/corrupt queue payloads are treated as recoverable and reset safely.
- Stale-event pruning:
  - Events older than 30 days are dropped to avoid syncing obsolete playback history.
- Expanded observability:
  - Worker logs include run attempt, pending queue depth before/after sync, and sync summary.
- Explicit STOPPED semantics:
  - STOPPED is emitted on app background / playback release paths.
  - Duplicate STOPPED reports per tracking session are suppressed.

---

### Phase 4 — Sync to Jellyfin when online (2–4 PRs)
✅ Outcome: Watch state syncs reliably when connectivity returns.

Status: ✅ **Complete** (implemented and code-verified)

Implemented in this phase:
- `OfflineProgressSyncWorker` runs with `NetworkType.CONNECTED` and exponential backoff.
- Sync is triggered:
  - on app startup when online (initial connectivity emission)
  - on connectivity change back to online
  - after playback stop/release when online and queue has pending events
- Replay handles event types: `PROGRESS`, `STOPPED`, `MARK_PLAYED`, `MARK_UNPLAYED`.
- Replay ordering uses timestamp with tie-breaker priority:
  - `MARK_PLAYED` > `MARK_UNPLAYED` > `STOPPED` > `PROGRESS`
- Worker/repository logs now include queue depth and per-run sync summary.

12. **Create `OfflineSyncWorker` (WorkManager)**
   - Constraints: `NetworkType.CONNECTED`
   - Reads unsynced events
   - Sends to Jellyfin:
     - progress update (position)
     - mark played/unplayed
   - Marks events as synced

13. **Trigger sync**
   - On app start if online
   - On connectivity change → online
   - After playback ends if online

14. **Conflict strategy (MVP)**
 - Simple: “last update wins” using timestamps
 - Tie-breaker when timestamps are equal:
   - `MARK_PLAYED` > `MARK_UNPLAYED` > `STOPPED` > `PROGRESS`
 - Optional improvement:
   - compare server’s last playback update time with local event timestamp

Acceptance checks:
- Go offline, watch part of a download
- Reconnect
- Jellyfin server shows updated progress/played state

---

## API / Repository Touchpoints (Conceptual)

Your repository likely already supports online playback reporting.
For sync you need equivalent methods, such as:
- `reportPlaybackProgress(itemId, positionMs, ...)`
- `markPlayed(itemId)` / `markUnplayed(itemId)`

Implement these as repository functions so the Worker doesn’t call raw networking directly.

---

## Storage & Permissions Notes

- Prefer app-specific storage for downloaded media:
  - e.g. `context.getExternalFilesDir("downloads")` or `context.filesDir`
- App-specific storage avoids needing media read permissions on modern Android.
- If you use shared/public storage:
  - Android 13+ requires `READ_MEDIA_VIDEO`
  - File access may require `content://` via `MediaStore` / SAF

---

## Nice-to-Have (Later)

### Full offline library browsing
To browse posters and metadata offline:
- Cache metadata in Room (items, images, relationships)
- Store posters/backdrops in persistent storage (not cacheDir)
- Implement an “Offline mode” home that shows cached sections

Current implementation status:
- ✅ Downloaded-item offline browsing is implemented:
  - Offline library list is now driven from completed downloads (not a placeholder list)
  - Download snapshots store metadata needed for browsing (series/season/episode, overview, year)
  - Poster thumbnails are cached to app-local storage and rendered offline when available
- 🚧 Full library-wide offline browsing (including non-downloaded items) remains future work

### Multiple qualities and redownload
- Allow selecting quality per download
- Provide “Redownload in different quality” action on detail screen

Current implementation status:
- ✅ Quality selection for new downloads is implemented.
- ✅ Redownload in different quality is implemented:
  - Detail-screen completed download controls support quality-based redownload.
  - Downloads list completed rows include a redownload action with quality picker.

### Auto-clean policy
- Keep downloads for X days
- Remove downloads when storage low (prompt user)

Current implementation status:
- ✅ Auto-clean policy is implemented for watched downloads:
  - Configurable settings in Downloads preferences:
    - Enable/disable auto-clean
    - Watched retention days (7/14/30/60)
    - Minimum free-space target (2/5/10/20 GB)
  - Auto-clean runs automatically before starting new downloads when enabled.
  - Manual "Run Auto-clean Now" action is available in Downloads settings.
  - Cleanup behavior:
    - Deletes watched downloads older than retention window
    - If free space is still below target, deletes additional watched downloads (oldest first)

---

## QA Checklist

- [ ] Download plays with airplane mode enabled
- [ ] Detail screen shows Downloaded badge instantly and reliably
- [ ] Delete offline copy removes file and record, does not affect server item
- [ ] Downloads settings screen lists all downloads and shows accurate sizes
- [ ] Offline playback creates local progress/played events
- [ ] Sync worker uploads events when online and clears queue
- [ ] No crashes when local file missing (graceful error message)
- [ ] Airplane mode toggled mid-playback preserves queue integrity
- [ ] App kill/restart while offline preserves queued events
- [ ] Token expiry during sync re-queues recoverable events
- [ ] Connectivity flapping (online/offline) does not duplicate queue events

---

## Suggested Task Breakdown (Tracking)

### MVP Milestones
- [x] P0: Offline branching in player works
- [x] P1: Detail badge + delete offline copy
- [x] P2: Downloads settings screen
- [x] P3: Local watch tracking
- [x] P4: Sync worker

### Future Milestones
- [x] Offline library browsing for downloaded items (metadata + local posters)
- [ ] Full library-wide offline browsing (metadata + images)
- [x] Redownload quality selection
- [x] Auto-clean & storage management

---

## Notes / Decisions Log

Use this section to record decisions:
- Offline played threshold: 90%
- Progress update interval: 15 seconds
- Storage location: app-specific storage (`getExternalFilesDir(Environment.DIRECTORY_MOVIES)/JellyfinOffline`, fallback to `filesDir/JellyfinOffline`)
- Conflict strategy: Last update wins (timestamp ordering during replay)
- Sessionless sync fallback: Deterministic generated session ID when missing
- Queue retention: 200 events max, stale events pruned after 30 days
