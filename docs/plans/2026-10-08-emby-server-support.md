# Plan: Emby Server Support

**Date**: 2026-10-08
**Status**: Draft for review. Nothing is implemented yet.

## Approach

Add Emby as a second server backend behind the existing repository layer. The repositories stay the
public facade the ViewModels already use; each one routes to either the Jellyfin SDK (today's path)
or a new hand-written Emby Retrofit client, based on a `serverType` stored on the connected server.
Emby responses are converted into the Jellyfin SDK model types the UI already consumes, so screens
and ViewModels are not rewritten.

## Goal and success criteria

Done means: a user can add an Emby server alongside a Jellyfin one, sign in with username and
password, switch between the saved profiles, browse libraries, and play a movie and an episode
with progress reported back to the right server, on the phone UI, with Jellyfin behaviour
unchanged.

Evidence:

- `./gradlew.bat testDebugUnitTest` passes, including new Emby mapper/codec/repository tests driven
  by JSON fixtures captured from a real Emby server.
- Manual checklist on a real Emby server (Phase 4 exit) passes: sign in, home rows populate, open a
  movie, direct play, forced transcode, resume position visible in Emby's own web client.
- The same checklist on a Jellyfin server shows no regression.

## Scope

- **In**: saved server profiles with a switcher across Jellyfin and Emby, a Jellyfin/Emby choice
  when adding a server, username/password sign-in, libraries, home rows, item details,
  images, search, favorites, played state, video playback (direct play and transcode), playback
  progress reporting, feature gating for Jellyfin-only features, phone and tablet UI.
- **Out (first release)**: Emby Connect (cloud account sign-in), Live TV, SyncPlay, Cinefin server
  plugin features, Emby-side intro markers, admin features.
- **Later phases (in the plan, not the first release)**: Android TV UI, Chromecast, DLNA, offline
  downloads, music, playlists.

## What the code looks like today (verified)

| Fact | Where |
|------|-------|
| 285 `org.jellyfin.sdk` imports across 141 files | `app/src/main/java` |
| `BaseItemDto` appears 1,027 times across 123 files, including screens and components | UI, ViewModels, repositories |
| About 80 SDK API call sites, all in 8 repository classes | `data/repository/` |
| Item IDs are parsed with `UUID.fromString` in 93 places across 14 files; non-UUID IDs are rejected | `RepositoryUtils.parseUuid`, `JellyfinStreamRepository.getStreamUrl` |
| Connection test rejects any server whose major version is below 12 | `JellyfinAuthRepository.isServerVersionSupported`, `Constants.kt:107` |
| Stream and image URLs are built in one class (16 sites) plus one in the player | `JellyfinStreamRepository`, `VideoPlayerMetadataManager` |
| LAN discovery broadcasts `Who is JellyfinServer?` | `JellyfinDiscoveryRepository` |
| Auth headers are already Emby-era names (`X-Emby-Token`, `X-Emby-Authorization`, `MediaBrowser` scheme) | `JellyfinAuthInterceptor` |
| Retrofit + OkHttp are already used for non-SDK services (Seerr, Sonarr, Radarr, plugin) | `data/repository/*ApiService.kt` |
| `JellyfinServer` is a `@Serializable` data class persisted for session restore | `data/JellyfinServer.kt` |
| Jellyfin-only features in use: Quick Connect, SyncPlay, MediaSegments, Cinefin plugin | `JellyfinAuthRepository`, `SyncPlayRepository`, `JellyfinRepository:901`, `CinefinPluginRepository` |

Two consequences:

1. An Emby server cannot connect at all today. Emby versions are 4.x, so the "major version >= 12"
   gate rejects it before sign-in.
2. The Jellyfin SDK cannot simply be pointed at an Emby server. Emby item IDs are numeric strings,
   and the SDK models type them as `UUID`.

## Emby API assumptions (from memory, NOT yet verified)

These drive the design and are the first thing Phase 0 checks against a real server and the Emby
OpenAPI spec. If one is wrong, the affected phase changes.

- `GET /System/Info/Public` works unauthenticated and does not return a `ProductName` containing
  "Jellyfin".
- Sign-in is `POST /Users/AuthenticateByName` with `Username` and `Pw`, returning `AccessToken`,
  `User`, `ServerId`.
- Item IDs are numeric strings; user and server IDs are 32-character hex GUIDs.
- User-scoped routes keep the older shape: `/Users/{userId}/Views`, `/Users/{userId}/Items`,
  `/Users/{userId}/Items/Latest`, `/Users/{userId}/Items/Resume`, `/Shows/NextUp`,
  `/Users/{userId}/FavoriteItems/{id}`, `/Users/{userId}/PlayedItems/{id}`.
- Playback: `POST /Items/{id}/PlaybackInfo` takes a device profile; streaming is
  `/Videos/{id}/stream` and `/Videos/{id}/master.m3u8`; reporting is `/Sessions/Playing`,
  `/Sessions/Playing/Progress`, `/Sessions/Playing/Stopped`.
- Images are `/Items/{id}/Images/{type}`.
- Discovery uses the same UDP port 7359 with the message `who is EmbyServer?`.
- There is no maintained official Kotlin SDK for Emby.

### Checked on 2026-10-08 against the test server (Emby 4.11.0.6)

Source: unauthenticated `GET /System/Info/Public` and the server's own `GET /openapi.json`
(OpenAPI 3.0.1, 434 paths, served without sign-in, so it can be re-fetched at any time).

| Assumption | Result |
|------------|--------|
| `/System/Info/Public` works unauthenticated | Confirmed. Returns `ServerName`, `Version`, `Id`, `LocalAddresses`, `RemoteAddresses` |
| No `ProductName` in the public info | Confirmed. Detection rule: no `ProductName` means Emby |
| Server ID is a 32-character hex GUID | Confirmed |
| No `/emby` prefix needed | Confirmed on 4.11; both `/System/…` and `/emby/System/…` answer |
| Sign-in body is `Username` + `Pw`; result has `User`, `SessionInfo`, `AccessToken`, `ServerId` | Confirmed in the spec |
| User-scoped routes (`/Users/{UserId}/Views`, `/Items`, `/Items/{Id}`, `/Items/Latest`, `/Items/Resume`, `FavoriteItems`, `PlayedItems`) | All present |
| `/Shows/NextUp`, `/Shows/{Id}/Seasons`, `/Shows/{Id}/Episodes` | Present |
| `POST /Items/{Id}/PlaybackInfo`, `/Videos/{Id}/stream`, `/Videos/{Id}/master.m3u8`, `/Items/{Id}/Download` | Present |
| `/Sessions/Playing`, `/Progress`, `/Stopped`, `GET /Sessions` | Present |
| `/Items/{Id}/Images/{Type}` | Present |
| Quick Connect | Absent (`/QuickConnect/Enabled` returns 404) |
| `/Search/Hints` | **Absent.** Search must use `/Users/{UserId}/Items?SearchTerm=…` |
| Intro markers | `ChapterInfo.MarkerType` exists, so Emby intro-skip via chapters is possible later |
| Item IDs are numeric strings | Spec types them as plain `string` (not `uuid`); the actual format needs a signed-in request |
| `BaseItemDto` shape | 163 properties in Emby's schema; overlap with the SDK model still to be measured |

The test server has Emby Premiere (confirmed by the owner), so limits on non-Premiere servers
still need a separate check before rollout.

### From Emby's developer docs (https://dev.emby.media/doc/restapi/index.html)

- The documented client header is `Authorization: Emby UserId="…", Client="…", Device="…",
  DeviceId="…", Version="…"` on every request, with the token in `X-Emby-Token`. The app sends
  `X-Emby-Authorization: MediaBrowser Client=…` today. The server's CORS list allows both header
  names, but whether the `MediaBrowser` scheme word is accepted is unverified, so
  `JellyfinAuthInterceptor` should send the documented `Emby` form to Emby servers.
- Sign-out is `POST /Sessions/Logout`, which revokes the token. Removing a profile (KD9) should
  call it.
- A 401 generally means the token was revoked and the user should be sent to sign-in. The docs
  describe saving the token for later use and mention no timed expiry (my reading, not an explicit
  statement), so the app's age-based `isTokenExpired()` re-auth probably should not run for Emby
  profiles; confirm in Phase 0.
- The docs say multi-server apps should store each token against the server `Id` so a token is
  never sent to the wrong server. `ServerProfile` should key on server ID plus user ID, not URL.

- **Discovery**: UDP broadcast of `who is EmbyServer?` on port 7359; the reply JSON has
  `Address`, `Id`, `Name` (no `Version`, which `parseDiscoveryMessage` already treats as optional).
- **Stream URLs**: `/Videos/{Id}/stream` and `/Videos/{Id}/stream.{Container}`; the docs list
  `MediaSourceId` and `PlaySessionId` as required, with `PlaySessionId` taken from the
  PlaybackInfo response. `static=true` serves the file directly. Several builders in
  `JellyfinStreamRepository` send neither `MediaSourceId` nor a server-issued `PlaySessionId`, so
  the Emby path must go through PlaybackInfo first (Phase 4).
- **Seeking in a progressive transcode** is not client-side: stop the stream and start a new one
  with `StartTimeTicks`. Check how the player seeks on a non-HLS Emby transcode in Phase 4.
- **Check-ins**: `POST /Sessions/Playing`, `/Progress`, `/Stopped`; progress every 10 seconds and
  immediately on user actions, with an `EventName` (`TimeUpdate`, `Pause`, `Unpause`,
  `AudioTrackChange`, `SubtitleTrackChange`, …). Body fields include `ItemId`, `MediaSourceId`,
  `PositionTicks`, `PlayMethod`, `PlaySessionId`, `IsPaused`, `CanSeek`.
- **Browsing**: `/Users/{UserId}/Views` (with `CollectionType`), `/Users/{UserId}/Items` with
  `ParentId`, `Recursive`, `IncludeItemTypes`, `Fields`, `SortBy`, `SortOrder`, `Limit`.
- **Token in a URL**: `api_key` is accepted as a query parameter, which is what Cast URLs need.
- **ID formats are mixed**: the doc examples show a 32-hex user ID and a dashed-GUID view ID,
  and item IDs are expected to be numeric. `ServerIdCodec` (KD2) therefore has to handle three
  shapes: GUID (pass through), numeric (reversible encoding), and anything else. For "anything
  else" a reversible encoding is not possible, so the codec needs a fallback lookup table; whether
  that case occurs at all is a Phase 0 question.

### Phase 0 spike results (2026-10-08, signed in as a test user on Emby 4.11.0.6)

Method: read-only requests for views, 13 item types, details, latest, resume, next up, search,
seasons, episodes, system info, sessions, and the user, plus three `POST /Items/{Id}/PlaybackInfo`
calls. A throwaway unit test ran every response through the Jellyfin SDK serializers. The test
was removed from the repo afterwards and the spike sessions were signed out.

| Question | Result |
|----------|--------|
| Does Emby accept the header the app sends today? | **Yes.** `X-Emby-Authorization: MediaBrowser …` and `Authorization: Emby …` both returned 200 on sign-in. No interceptor change is required, though sending the documented form is still the safer choice |
| Item ID format | **Numeric** in all 637 item IDs seen, and in `ParentId`, `SeriesId`, `SeasonId`, `AlbumId`, people, studios, genres, and view IDs. User, server, and display-preference IDs are 32-hex. Media source IDs are `mediasource_{n}` (a `String` in the SDK model, so no encoding needed). No other shapes appeared, so the lookup-table fallback is not needed on this server |
| Do raw Emby responses decode as SDK models? | **No, 0 of 96.** Every sample fails |
| Do they decode after normalizing? | **Yes, 96 of 96**: 91 items across all types, the sign-in result, the user, and 3 playback-info responses. **KD3 is a go** |
| Why raw decoding fails | Three causes only: numeric IDs in `UUID` fields; fields the SDK marks required that Emby omits; one enum value the SDK lacks (`LockedFields: SortName`) |
| Unknown item `Type` values | None in this library |
| Does Emby accept the app's Jellyfin-shaped PlaybackInfo body and device profile? | **Yes**, HTTP 200. An HEVC 1080p MKV movie and episode came back as direct-playable |
| Stream URLs | Emby returns ready-made URLs: `DirectStreamUrl: /videos/{id}/original.mkv?DeviceId=…&MediaSourceId=…&PlaySessionId=…&api_key=…`, and a `TranscodingUrl` when it transcodes. They already carry real IDs and the token |
| Sign-out | `POST /Sessions/Logout` returned 204 and the token then got 401 |

Required-by-the-SDK fields Emby omits (the normalizer must default these):

- `UserData.ItemId` and `UserData.Key` on every item, `UserData.PlayCount` on some. `ItemId`
  should be filled from the parent item's ID, not a zero UUID.
- `MediaStreams[].IsOriginal`; `MediaSources[].GenPtsInput`, `HasSegments`, `IgnoreDts`,
  `IgnoreIndex`, `TranscodingSubProtocol`; `Chapters[].ImageDateModified`.
- On sign-in and user: `SessionInfo.IsActive`, `SupportsMediaControl`, `HasCustomDeviceName`,
  `LastPlaybackCheckIn`, `PlayState.PlaybackOrder`; `Configuration.DisplayCollectionsView`,
  `GroupedFolders`; `Policy.SyncPlayAccess`, `MaxActiveSessions`, `LoginAttemptsBeforeLockout`,
  `PasswordResetProviderId`, `ForceRemoteSourceTranscoding`.

Design changes that follow:

1. **Build the normalizer from the SDK's serializer descriptors, not a hand-kept field list.**
   The prototype walked `BaseItemDto.serializer().descriptor` and, for each element: encoded
   numeric values in `UUID` fields, filled missing required fields with a type default, and
   dropped enum values and enum map keys the SDK does not know. About 60 lines handled every type
   tested, and it adapts by itself when the SDK is upgraded. This replaces the "list of JSON fields
   the normalizer rewrites" idea in KD3.
2. **Use Emby's returned stream URLs.** For Emby, playback takes `DirectStreamUrl` or
   `TranscodingUrl` from the PlaybackInfo response instead of assembling `/Videos/{id}/stream`
   by hand. That removes most ID decoding from `JellyfinStreamRepository` on the video path.
   Image URLs still need decoding.
3. **`ServerIdCodec` is confirmed as designed** (KD2): numeric to synthetic UUID and back, 32-hex
   passed through.

Still open after the spike:

- **Transcoding was not exercised.** Both videos direct-played, so `TranscodingUrl` for video,
  HLS, and how Emby honours `EnableDirectPlay=false` are untested.
- **Audio came back as transcode-only** (an MP3 track, `SupportsDirectPlay: false`). The profile
  used was the simple `createAndroidDeviceProfile(maxWidth, maxHeight)` overload, not the
  capability-based one the app really sends, so this may be an artifact of the spike. Check in the
  music increment.
- **Non-Premiere behaviour**: `/System/Info` exposes `HardwareAccelerationRequiresPremiere`, but
  playback limits on a server without Premiere are untested.
- **Minimum Emby version**: only 4.11.0.6 was tested.
- **Fixtures are not in the repo yet.** The raw captures contain real library titles and file
  paths, so they stay out of version control. Phase 3 commits a small hand-trimmed set with paths
  and names replaced.

## Key Decisions (review first)

### KD1. Keep `BaseItemDto` as the app's item model

- **Choice**: Emby responses are converted into Jellyfin SDK model types (`BaseItemDto`,
  `PlaybackInfoResponse`, `MediaSourceInfo`).
- **Discarded**: introducing Cinefin-owned domain models and migrating the UI to them.
- **Why**: 1,027 usages in 123 files. A model migration is a rewrite of the UI layer with no user
  benefit and would block Emby work for weeks. It can still be done later, independently.
- **Cost**: the app stays tied to the Jellyfin SDK's model shapes; Emby-only fields are dropped.

### KD2. Represent Emby numeric IDs as synthetic UUIDs

- **Choice**: a single `ServerIdCodec` maps an Emby numeric ID to a reversible, recognisable UUID
  (fixed marker in the high bits, the number in the low 48 bits) and back. GUID-shaped Emby IDs pass
  through unchanged. Encoding happens when Emby JSON comes in; decoding happens where a URL or
  request body goes out.
- **Discarded**: changing every `UUID` in the app to `String`.
- **Why**: `BaseItemDto.id` is a `UUID` in the SDK and cannot be changed. The outbound chokepoints
  are few: the Emby Retrofit data sources and `JellyfinStreamRepository`.
- **Risk**: a synthetic UUID leaking into a URL sent to Emby returns 404. Mitigation: a debug-build
  OkHttp interceptor that fails loudly if a marker UUID appears in a request to an Emby server, plus
  codec unit tests.

### KD3. Convert Emby JSON by normalising it, then decoding with the SDK serializers

- **Choice**: an `EmbyJsonNormalizer` rewrites an Emby JSON tree (ID fields through
  `ServerIdCodec`, unknown enum values to a safe fallback, missing required fields defaulted) and
  then decodes with the SDK's own `kotlinx.serialization` serializers.
- **Discarded**: hand-written Emby DTO classes plus field-by-field mappers for every type.
- **Why**: `BaseItemDto` has well over a hundred fields; the two APIs share ancestry and most field
  names. Normalising is a few hundred lines; mapping is thousands.
- **Outcome**: confirmed by the Phase 0 spike. 96 of 96 captured responses decoded after
  normalizing, using a normalizer driven by the SDK's serializer descriptors.

### KD4. Route inside the existing repositories

> **Superseded on 2026-10-08 (Phase 3).** The repositories are not branched at all. The Jellyfin
> SDK's `ApiClient` is an abstract class whose typed APIs all funnel through one `request()`
> method, so `EmbyApiClient` subclasses it and translates there: IDs out, a short route table,
> normalized JSON back. `OptimizedClientFactory` hands that client out for an Emby session.
> About 80 planned branches became one class. The text below is the original decision, kept for
> the record.

- **Choice**: `JellyfinRepository`, `JellyfinMediaRepository`, `JellyfinUserRepository`,
  `JellyfinSearchRepository`, `JellyfinAuthRepository`, `JellyfinSystemRepository` keep their
  public signatures. Each gets an injected Emby data source and branches on
  `server.serverType` through one shared helper in `BaseJellyfinRepository`.
- **Discarded**: a full `MediaServerBackend` interface with two Hilt-bound implementations swapped
  at runtime.
- **Why**: ViewModels inject several of these repositories as concrete classes, and
  `docs/plans/2026-03-30-jellyfin-repository-refactor-status.md` explicitly says not to widen
  `IJellyfinRepository` further. Branching in place lets Emby land one screen at a time.
- **Cost**: the repository classes grow. `JellyfinRepository.kt` is already about 1,500 lines, so
  Emby logic lives in the separate data source classes and the branch is one line per method.

### KD5. Hand-written Retrofit client for Emby

- **Choice**: `EmbyApiService` (Retrofit, `ResponseBody`/`JsonElement` returns) on the existing
  authenticated OkHttp client.
- **Discarded**: generating a client from Emby's OpenAPI spec.
- **Why**: the app needs roughly 25 endpoints; Retrofit is the established pattern here and the
  auth interceptor already sends the headers Emby expects.

### KD6. The user picks Jellyfin or Emby when adding a server; the probe checks the pick

- **Choice** (your direction, 2026-10-08): the add-server screen offers Jellyfin and Emby as
  explicit options. `/System/Info/Public` is still probed, and if the server turns out to be the
  other type the app says so and offers to switch the selection instead of failing. The confirmed
  type is stored on the profile as `serverType` (default `JELLYFIN`, so the existing saved session
  still loads).
- **Discarded**: silent auto-detection with no visible choice.
- **Why**: the choice is visible and intentional, and the probe stops a wrong pick from becoming a
  confusing sign-in error.

### KD9. Saved server profiles with a switcher

- **Choice** (your direction, 2026-10-08): the app keeps a list of saved profiles. A profile is one
  sign-in: server URL, server type, server name, user ID, username, token, sign-in time. Jellyfin
  and Emby profiles sit in the same list, and a switcher in the profile screen (and TV settings)
  changes the active one without signing out of the others.
- **Today**: there is exactly one session, stored as six loose DataStore keys in
  `ServerConnectionViewModel` (`server_url`, `username`, `session_token`, …). Passwords are
  already stored per (server URL, username) in `SecureCredentialManager`, which suits profiles.
  The `pr-1178-switch-server` branch only adds a "switch server" action that signs out; it is not a
  profile list.
- **Design**: a `ServerProfileRepository` owns the list and the active profile ID. On first run
  after the update it migrates the six legacy keys into one profile. `JellyfinAuthRepository`
  keeps holding only the active server, so the repositories and ViewModels below it do not change.
- **Switching** is a controlled reset, in this order: stop playback and end any Cast session;
  clear `JellyfinCache`, the SDK client cache, and in-memory app state
  (`SharedAppStateManager`, home and library ViewModels); activate the new profile's token;
  navigate to Home. If the stored token is expired, re-authenticate with the saved password or
  ask for it.
- **Decisions inside this that are mine unless you object**:
  - Tokens stay in app-private DataStore, as the single saved session always was. Reversed
    during Phase 1: the Keystore key can be set to require device authentication
    (`requireStrongAuthForCredentials`), and a token stored under it could not be read at
    launch without a prompt, which would break silent session restore.
  - Playback, theme, and subtitle preferences stay app-wide. Seerr, Sonarr, and Radarr settings
    stay app-wide too for now.
  - Downloads are tagged with the profile they came from and only play under that profile.
  - Removing a profile deletes its token and saved password.
- **Risk**: state that survives a switch (a cached list, a stale image auth header, a queued
  offline progress update sent to the wrong server). Mitigation: one `switchProfile()` entry point
  that owns the reset, and a test that fails if a server-scoped singleton is not registered with it.

### KD7. Ship behind a Remote Config flag

- **Choice**: `enable_emby_support` in `FeatureFlags.Experimental`, default off. When off, an Emby
  server gets a clear "Emby support is not available yet" message instead of the current
  misleading "requires Jellyfin Server 12.0" error.

### KD8. Capability model for feature gating

- **Choice**: a small `ServerCapabilities` value derived from `serverType` (`quickConnect`,
  `syncPlay`, `mediaSegments`, `cinefinPlugin`). UI entry points read it instead of checking the
  server type directly.
- **Why**: keeps `if (emby)` out of composables and makes the Jellyfin-only list one file.

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `data/JellyfinServer.kt` | Modify | Add `serverType: ServerType = ServerType.JELLYFIN` |
| `data/model/ServerProfile.kt` | Create | Saved profile: URL, type, names, user ID, token reference, sign-in time |
| `data/preferences/ServerProfileRepository.kt` | Create | Profile list, active profile ID, legacy single-session migration |
| `data/session/ProfileSwitcher.kt` | Create | The one `switchProfile()` entry point that owns the reset order |
| `ui/components/ProfileSwitcherSheet.kt` | Create | List of profiles with type badge, add, remove |
| `ui/screens/ProfileScreen.kt`, `ui/screens/tv/TvSettingsScreen.kt` | Modify | Entry point to the switcher |
| `ui/viewmodel/MainAppViewModel.kt` | Modify | Expose profiles; reset state on switch |
| `data/SecureCredentialManager.kt` | Modify | Store tokens per profile alongside passwords |
| `data/model/ServerType.kt` | Create | `enum ServerType { JELLYFIN, EMBY }` and `ServerCapabilities` |
| `data/emby/ServerIdCodec.kt` | Create | Reversible Emby ID to UUID mapping (KD2) |
| `data/emby/EmbyJsonNormalizer.kt` | Create | Rewrites Emby JSON into SDK-decodable JSON (KD3) |
| `data/emby/EmbyApiService.kt` | Create | Retrofit interface for the Emby endpoints used |
| `data/emby/EmbyAuthDataSource.kt` | Create | Public info, sign-in |
| `data/emby/EmbyMediaDataSource.kt` | Create | Views, items, details, latest, resume, next up, search |
| `data/emby/EmbyUserDataSource.kt` | Create | Favorites, played state, playback reporting |
| `data/emby/EmbyPlaybackDataSource.kt` | Create | PlaybackInfo and device profile translation |
| `di/EmbyModule.kt` | Create | Provides `EmbyApiService` on the authenticated OkHttp client |
| `core/constants/Constants.kt` | Modify | Per-type minimum version (`MIN_SUPPORTED_EMBY_VERSION`) |
| `core/FeatureFlags.kt` | Modify | `ENABLE_EMBY_SUPPORT` |
| `data/repository/ConnectionOptimizer.kt` | Modify | Return a type-neutral `ServerProbeResult` instead of SDK `PublicSystemInfo` |
| `data/repository/JellyfinAuthRepository.kt` | Modify | Type detection, per-type version gate, Emby sign-in, persist `serverType` |
| `data/repository/IJellyfinAuthRepository.kt` | Modify | `testServerConnection` return type |
| `data/repository/common/BaseJellyfinRepository.kt` | Modify | `withBackend(jellyfin = …, emby = …)` routing helper |
| `data/utils/RepositoryUtils.kt` | Modify | `is401Error` recognises Retrofit `HttpException` from the Emby path (already partly does) |
| `data/repository/JellyfinMediaRepository.kt` | Modify | Route browse calls |
| `data/repository/JellyfinRepository.kt` | Modify | Route details, playback info, server info |
| `data/repository/JellyfinUserRepository.kt` | Modify | Route favorites, played, playback reporting |
| `data/repository/JellyfinSearchRepository.kt` | Modify | Route search |
| `data/repository/JellyfinStreamRepository.kt` | Modify | Decode IDs before building URLs; drop the "must be a UUID" rejection for Emby |
| `data/repository/JellyfinDiscoveryRepository.kt` | Modify | Also broadcast the Emby discovery message; tag results with type |
| `network/JellyfinAuthInterceptor.kt` | Modify | Only if Phase 0 shows Emby rejects the current header format |
| `ui/viewmodel/ServerConnectionViewModel.kt` | Modify | Consume `ServerProbeResult`; hide Quick Connect for Emby |
| `ui/screens/ServerConnectionScreen.kt` | Modify | Server-type-neutral copy; show detected type |
| `res/values/strings.xml` | Modify | Neutral wording where "Jellyfin" appears (7 mentions) |
| `app/src/test/resources/emby/*.json` | Create | Captured Emby response fixtures |
| `app/src/test/java/.../data/emby/*Test.kt` | Create | Codec, normalizer, data source tests |

## Next Step

Phase 4: exercise transcoding, track switching, episodes and resume on a real device; then
Cast URL decoding. Phases 1 to 3 are committed; see each phase's status for what is left.
Phase 0 is done except for the items listed under "Still open after the spike".

## Phase 0: Spike and fixtures

**Status**: complete (2026-10-08); results are under "Phase 0 spike results". Transcoding,
non-Premiere behaviour, and committed fixtures carry over to Phases 3 and 4.
**Posture**: characterization-first
**Files**: `app/src/test/resources/emby/`, a throwaway branch
**Tasks**:
- [ ] Get an Emby server to test against (Docker image `emby/embyserver`, a small library with one
      movie, one series, one music album). Note whether it has Emby Premiere.
- [ ] Capture raw JSON for: `/System/Info/Public`, sign-in, views, an items page, a movie, a
      series, an episode, resume, next up, latest, search, PlaybackInfo. Scrub tokens and save
      under `app/src/test/resources/emby/`.
- [ ] Check every bullet under "Emby API assumptions" against the captures and the Emby OpenAPI
      spec; correct this document where they differ.
- [ ] Confirm the current `X-Emby-Authorization: MediaBrowser Client=…` header is accepted by Emby.
- [ ] Write a throwaway test that runs an Emby item fixture through a prototype normalizer and
      `BaseItemDto.serializer()`; list every field that fails.
- [ ] Record which device-profile fields Emby's PlaybackInfo rejects or ignores compared with what
      `JellyfinDeviceProfile` sends.
**Verify**: the throwaway test decodes the movie, series, and episode fixtures into `BaseItemDto`.
**Exit**: go/no-go on KD3 written into this document; assumptions list marked verified or corrected.

## Phase 1: Server profiles and switcher (Jellyfin only)

Independent of Emby and shippable on its own. Done first because Phase 2 would otherwise change
the same session-storage code a second time.

**Status**: in progress on branch `emby` (2026-10-08). Code and unit tests are written and the
debug APK builds; nothing has been run on a device yet.

What was built, and where it differs from the task list below:

- `ServerType`, `ServerProfile`, `ServerProfileRepository` (own DataStore, `server_profiles`).
- **Mirror design instead of moving session storage.** The `login_preferences` keys the launch
  path reads are kept as "the active session"; `ActiveSessionStore` writes the chosen profile
  into them on a switch. The launch, auto-login, and biometric paths in
  `ServerConnectionViewModel` are therefore unchanged. Migration is: the first time the legacy
  session is restored it is also saved as a profile.
- `ProfileSwitcher` (`switchTo`, `remove`, `suspendActiveSession`) owns the reset: stop audio,
  end Cast, clear SDK clients, `JellyfinCache`, `SharedAppStateManager`. `MainAppViewModel`
  clears its state when the session identity changes.
- `ProfilesViewModel` and `ServerProfilesCard`, shown on the profile screen (switch, remove, add)
  and on the connection screen when signed out with saved profiles.
- **Add server** ends the running session but keeps its profile, which sends the app to the
  connection screen. This avoided adding an "add mode" to the connection ViewModel.
- **Sign out does not fall back to another profile.** It removes the active profile and lands on
  the connection screen, where the remaining profiles are listed.
- `MainAppViewModel.logout()` no longer wipes every saved password
  (`SecureCredentialManager.clearCredentials()`); the repository logout already clears the
  current server's.

Not done yet:

- Verified on an emulator (Android 17, two users on one Jellyfin server, 2026-10-08): the card
  in Settings, add server, picking a saved profile from the connection screen, switching both
  ways with each user's own Home content, cold restart keeping the active profile, and removing
  the inactive profile. Three things found and fixed there: the card was first placed on the
  Profile screen, which the phone UI never navigates to (moved to Settings); profile rows showed
  the username as the server name (now the host); and a second sign-in was not saved because
  navigation cancelled `saveCurrentSessionToken()` (now `NonCancellable`, as `saveCredentials()`
  already was).
- Still unverified on a device: removing the active profile, a profile whose token is missing or
  older than 30 days, switching during audio or Cast playback, two different servers, tablet
  layout, and upgrading from the current release build.
- TV entry point (`TvSettingsScreen`) and the TV connection screen list.
- The registration test for server-scoped singletons. The reset list is hand-maintained.
- Queued offline progress updates (`OfflineProgressRepository`) carry no profile, so an update
  queued under one profile could be sent after switching. Needs a profile tag on
  `QueuedProgressUpdate`.
- Downloads are not tagged with a profile.
- `AuthenticationViewModel` and `OptimizedMainAppViewModel` still call `clearCredentials()` on
  logout; whether they are reachable was not checked.
- `pr-1178-switch-server` is not reconciled.

**Posture**: characterization-first (pin today's session restore, expiry, and sign-out behaviour
in tests before moving the storage)
**Files**: `ServerProfile.kt`, `ServerType.kt`, `ServerProfileRepository.kt`, `ProfileSwitcher.kt`,
`SecureCredentialManager.kt`, `ServerConnectionViewModel.kt`, `MainAppViewModel.kt`,
`ProfileSwitcherSheet.kt`, `ProfileScreen.kt`
**Tasks**:
- [ ] Add tests that pin current behaviour of session restore, token-expiry on restore, and
      sign-out in `ServerConnectionViewModel`.
- [ ] Add `ServerType` and `ServerProfile` (with `serverType` defaulting to `JELLYFIN`).
- [ ] Add `ServerProfileRepository` with add, remove, list, set active; store tokens through
      `SecureCredentialManager`.
- [ ] Migrate the six legacy DataStore keys into one profile on first launch; test that an
      upgraded install stays signed in.
- [ ] Move session save and restore in `ServerConnectionViewModel` onto the repository.
- [ ] Add `ProfileSwitcher.switchProfile(id)` implementing the reset order in KD9.
- [ ] List every `@Singleton` that holds server-scoped data and register each with the reset.
- [ ] Add `ProfileSwitcherSheet` and open it from `ProfileScreen`; "Add server" goes to the
      connection screen without signing out the current profile.
- [ ] Make sign-out remove only the active profile and fall back to another if one exists.
- [ ] Reconcile with `pr-1178-switch-server`: reuse its labels and tests, drop its sign-out flow.
**Verify**: `./gradlew.bat testDebugUnitTest --tests "*ServerProfile*" --tests "*ServerConnectionViewModel*"`
passes. On device with two Jellyfin accounts: upgrade from the current build and stay signed in;
add a second profile; switch both ways and see each account's own Continue Watching; start
playback, switch, and confirm playback stopped and progress went to the right server; remove a
profile.
**Exit**: two Jellyfin profiles can be switched without restarting the app and without any
content from one appearing under the other.

## Phase 2: Connect and sign in to an Emby server

**Status**: in progress on branch `emby` (2026-10-08). Sign-in works on an emulator against
Emby 4.11.0.6: the server is detected as Emby, the test user signs in, the profile is saved as
Emby next to a Jellyfin profile, switching both ways works, and the Emby profile survives a cold
restart. Home is blank under Emby, as expected until Phase 3.

Built:

- `ServerType.detect(productName, version)`: Jellyfin names itself; no product name plus a
  version below 10 means Emby; anything unidentifiable is treated as Jellyfin.
- `ServerIdCodec` and the descriptor-driven `EmbyJsonNormalizer` (pulled forward from Phase 3,
  because sign-in already needs the normalizer to produce an SDK `AuthenticationResult`).
- `EmbyHttpClient` and `EmbyAuthDataSource`. **Plain OkHttp instead of Retrofit** (changes KD5):
  the base URL differs per profile, which Retrofit handles awkwardly, and the responses are
  decoded by the normalizer anyway. It uses the app's shared client, so pinning and the existing
  auth headers apply.
- `JellyfinAuthRepository`: per-type version gate (Emby minimum 4.8, provisional), Emby sign-in,
  re-authentication by the session's server type, `serverType` on the session.
- `enable_emby_support` flag, on in debug builds only. With it off, an Emby server gets "This is
  an Emby server. Emby support is not available in this version of the app yet."
- `EmbyHttpException` mapped in `RepositoryUtils` like the other HTTP errors.

Changed from the task list below:

- `ConnectionOptimizer` still returns the SDK `PublicSystemInfo`. The SDK's probe decodes
  Emby's public info without help, so the type-neutral `ServerProbeResult` was not needed.
- `authenticateUser` still returns the SDK `AuthenticationResult`, for the same reason.

Not done yet:

- The Jellyfin/Emby choice on the add-server screen (KD6). The type is detected automatically
  today; there is no visible selector.
- Hiding Quick Connect for Emby. The existing check already reports it as unavailable on a 404,
  but this was not looked at on a device.
- TV connection screens.
- `POST /Sessions/Logout` when an Emby profile is signed out or removed.
- Age-based token expiry still applies to Emby sessions.
- Under Emby the Settings account card shows "User" instead of the user's name, because loading
  the current user still goes through the Jellyfin SDK (Phase 3).
**Posture**: test-first
**Files**: `ServerType.kt`, `JellyfinServer.kt`, `Constants.kt`, `FeatureFlags.kt`,
`ConnectionOptimizer.kt`, `JellyfinAuthRepository.kt`, `IJellyfinAuthRepository.kt`,
`EmbyApiService.kt`, `EmbyAuthDataSource.kt`, `EmbyModule.kt`
**Tasks**:
- [ ] Add `serverType` to `JellyfinServer`, populated from the active `ServerProfile`.
- [ ] Add the Jellyfin/Emby choice to the add-server screen (phone and TV), with the mismatch
      prompt from KD6.
- [ ] Add `ServerProbeResult(serverName, version, productName, serverId, serverType)` and make
      `ConnectionOptimizer.testServerConnection` return it; update its callers (`JellyfinAuthRepository`, `JellyfinRepository`,
      `JellyfinSystemRepository`, `ServerConnectionViewModel`).
- [ ] Add the probe check: product name contains "Jellyfin" means Jellyfin; otherwise Emby (exact
      rule set by Phase 0). Compare with the user's pick.
- [ ] Replace `isServerVersionSupported(version)` with a per-type check; keep the Jellyfin rule
      (major >= 12) and add the Emby minimum decided in Phase 0.
- [ ] Add `ENABLE_EMBY_SUPPORT`; when off and the probe says Emby, return
      `ErrorType.UNSUPPORTED_SERVER_VERSION` with the "not available yet" message.
- [ ] Add `EmbyApiService.authenticateByName` and `EmbyAuthDataSource.signIn`; route
      `authenticateUserInternal` on the probed type.
- [ ] Remove the SDK `AuthenticationResult` from `authenticateUser`'s return type in favour of the
      app's own result, since Emby user IDs do not need the SDK type.
- [ ] Persist `serverType`; confirm `reAuthenticateInternal` and session restore use it.
- [ ] Hide Quick Connect on the connection screens when the probe says Emby.
**Verify**: `./gradlew.bat testDebugUnitTest --tests "*JellyfinAuthRepositoryTest*"` passes with new
Emby cases; on device, signing in to the Emby server lands on the home screen (rows may be empty or
erroring, that is Phase 3) and the profile screen shows the Emby version.
**Exit**: Emby sign-in, sign-out, app restart with restored session, and expired-token re-auth all
work; an Emby profile and a Jellyfin profile can sit in the switcher together; Jellyfin sign-in
unchanged.

## Phase 3: Browse an Emby library

**Status**: in progress on branch `emby` (2026-10-08). Browsing works on an emulator against
Emby 4.11.0.6, with no repository changes.

How it works:

- `EmbyApiClient` wraps a plain SDK client. For each SDK request it decodes `ServerIdCodec` IDs
  in the path and query, applies `EmbyRoute`, forwards through the SDK client (Emby accepts the
  SDK's `Authorization: MediaBrowser … Token=…` header), and normalizes the JSON response.
- Checked against the server: Emby answers Jellyfin-shaped `/Items?userId=…` with camelCase
  query names, and `/Shows/NextUp`, `/Items/{id}/Similar`, `/Items/{id}/PlaybackInfo`,
  `/System/Info` under the same paths. Only these needed remapping: `/UserViews`, `/Users/Me`,
  `/UserFavoriteItems/{id}`, `/UserPlayedItems/{id}`, `/UserItems/{id}/UserData`. The Jellyfin-only
  `/MediaSegments/{id}` gets an empty answer without a request.
- `JellyfinAuthInterceptor` rewrites encoded IDs in any outgoing URL, so image URLs built
  anywhere in the app reach Emby with real IDs. This covers everything sent through OkHttp;
  URLs handed to Cast or other players do not pass through it (Phase 4 and 6).

Verified on the emulator under the Emby profile: Home (hero, libraries, recently added, images),
the Library tab with item counts, the Movies grid, a movie detail including its playback-info
summary, TV shows, a series with seasons and episodes, an episode detail, search, and the user's
name in Settings. Switching back to Jellyfin and browsing there still works.

Not done yet:

- **Emby list items lack some fields.** Movie cards show no year or rating under Emby; Emby only
  returns those when they are named in `Fields`. `EmbyApiClient` should add them to item queries.
- Continue Watching and Next Up were empty for the test user, so those rows are unverified with
  data. Favorites and mark-played were not exercised (they write to the server).
- Paging past the first page of a large library, music, playlists, collections, people.
- The debug-only check that no encoded ID leaves in a request was dropped in favour of the
  interceptor rewrite.
- No committed JSON fixtures; the normalizer and client tests use hand-written Emby-shaped JSON.
- Item types Emby has and the SDK lacks get the SDK's first enum value as a placeholder.
**Posture**: test-first
**Files**: `ServerIdCodec.kt`, `EmbyJsonNormalizer.kt`, `EmbyMediaDataSource.kt`,
`BaseJellyfinRepository.kt`, `JellyfinMediaRepository.kt`, `JellyfinRepository.kt`,
`JellyfinSearchRepository.kt`, `JellyfinStreamRepository.kt`
**Tasks** (detailed after Phase 0; outline):
- [ ] `ServerIdCodec` with round-trip tests, including GUID pass-through and the largest numeric ID.
- [ ] `EmbyJsonNormalizer` driven by the Phase 0 fixtures.
- [ ] `withBackend` helper in `BaseJellyfinRepository`.
- [ ] Route, one slice at a time and in this order: user views, library items (including
      `LibraryItemPagingSource`), recently added, continue watching, next up, item details
      (movie, series, season, episode), search, favorites list.
- [ ] Decode IDs in `JellyfinStreamRepository` image URL builders.
- [ ] Add the debug interceptor that flags a marker UUID in a request to an Emby host.
**Verify**: on device against Emby, the home screen shows Continue Watching, Next Up, and Recently
Added rows with posters; a library grid pages past 100 items; a movie detail and a series, season,
and episode drill-down load; search returns results.
**Exit**: every phone screen reachable from Home, Library, and Search renders Emby content without
an error state.

## Phase 4: Play video from Emby

**Status**: in progress on branch `emby` (2026-10-08). A movie plays from Emby on an emulator
and its progress reaches the server.

What it took: one change. `EmbyApiClient` now also translates item IDs inside request bodies
(serialize the way the SDK would, swap encoded IDs, hand the SDK a JSON tree). Everything else
already worked through Phase 3's pieces: the player fetches through the app's shared OkHttp
client, so the stream URLs the app builds get real IDs and auth from the interceptor.

Verified on the emulator against Emby 4.11.0.6, cross-checked on the server through its API:

- The app chose Direct Stream for an HEVC + DTS movie (video copied, audio to AAC over HLS);
  segments loaded and video rendered.
- While playing, the server's `/Sessions` showed the movie as now playing from Cinefin, with
  Emby's own numeric item ID and play method DirectStream.
- Seeking forward with the skip button worked.
- After leaving the player at about 2:18, the server held a resume position of 158 s and listed
  the movie under Resume; the app's Home then showed it under Continue Watching.
- Year, rating and other list fields: `EmbyApiClient` adds them to every item query, since Emby
  only returns them when asked.

Differs from the plan: the app still builds its own stream URLs instead of using Emby's
`DirectStreamUrl`/`TranscodingUrl`. They work, so that change was not needed for playback from
the phone.

Follow-up on 2026-10-08 (after the Phase 4 commit):

- **Fixed:** encoded item IDs are now decoded where stream, image, subtitle and Cast URLs are
  built (`JellyfinStreamRepository`, `CastMediaLoadBuilder`, `EnhancedPlaybackManager`,
  `VideoPlayerMetadataManager`), so URLs handed to Chromecast or DLNA carry Emby's own IDs.
  Covered by unit tests; not tried against a Cast device.
- **Start-up time is not an Emby problem.** The same movie took 17 s to start from Emby and 16 s
  from Jellyfin on the emulator, with the same Direct Stream decision.
- **Open bug: resuming a partly watched movie stalls.** The player opens at the saved position
  (3:55), the server returns the playlist and the two segments at that position, and then the
  player sits buffering without requesting more; it was still stalled minutes later. Starting
  from the beginning and skipping forward in small steps works. Fetching the same mid-file
  segment directly from the server works (10 s for the first request, then instant), so the
  stall is in how the player handles it. Not yet compared with resuming on Jellyfin.
- **Picture quality is still unsettled.** The emulator could not give a fair comparison; this
  needs a real device.

Not done or not checked:

- Picture quality. The one frame captured looked banded and green-tinted. That may be the
  emulator's software HEVC decoder; it was not compared with Jellyfin on the same emulator or
  checked on a real device.
- Playback took about 20 seconds to start. Not compared with Jellyfin.
- A full video transcode (forced lower quality), pure direct play, and audio or subtitle track
  switching.
- Episodes, resuming from the saved position, and playing to the end (marked played).
- Favourite and mark-played buttons.
- Chromecast and DLNA: their URLs do not pass through the interceptor, so encoded IDs must be
  decoded where those URLs are built (`CastMediaLoadBuilder`, `JellyfinStreamRepository`).
- Downloads and music playback.
**Posture**: characterization-first (capture current Jellyfin playback decisions before touching
`getPlaybackInfo`)
**Files**: `EmbyPlaybackDataSource.kt`, `EmbyUserDataSource.kt`, `JellyfinRepository.kt`,
`JellyfinUserRepository.kt`, `JellyfinStreamRepository.kt`, `EnhancedPlaybackManager.kt`
**Tasks** (outline):
- [ ] Translate `JellyfinDeviceProfile` output into the device-profile JSON Emby accepts.
- [ ] Route `getPlaybackInfo`; normalise the response into `PlaybackInfoResponse`.
- [ ] Decode IDs in stream, HLS, and download URL builders; remove the UUID-format rejection.
- [ ] Route playback start, progress, and stopped reports.
- [ ] Route mark played/unplayed and toggle favorite.
- [ ] Check subtitle and audio track selection against Emby stream indexes.
**Verify**: manual checklist on Emby: direct play a compatible file; force a transcode with the
quality selector; switch audio and subtitle tracks; stop at a known position and see the same
resume point in Emby's web client; finish an episode and see it marked played. Repeat on Jellyfin.
**Exit**: checklist passes on both server types. This is the first shippable point (flag on for
testers).

## Phase 5: Gating, discovery, and wording

**Status**: pending
**Files**: `ServerType.kt` (`ServerCapabilities`), `JellyfinDiscoveryRepository.kt`,
`SyncPlayRepository.kt` callers, intro-skip callers of `getMediaSegments`,
`CinefinPluginRepository.kt` callers, `strings.xml`, `ServerConnectionScreen.kt`
**Tasks** (outline):
- [ ] Hide SyncPlay, intro-skip, and plugin-backed UI when the capability is false;
      `getMediaSegments` returns an empty success for Emby.
- [ ] Broadcast both discovery messages and label discovered servers with their type.
- [ ] Reword the strings that say "Jellyfin" where the server could be either (7 mentions in
      `strings.xml`, plus the hardcoded version-gate message in `JellyfinAuthRepository`).
- [ ] Add `server_type` as an analytics dimension through `AnalyticsHelper`.
**Verify**: on Emby, no Jellyfin-only entry point is visible and none returns an error; discovery
lists both a Jellyfin and an Emby server on the same LAN.
**Exit**: no dead ends for an Emby user in the phone UI.

## Phase 6: Remaining surfaces (each is its own increment)

Order is a proposal; each can ship separately.

1. **Android TV** (`ui/tv/`, `ui/screens/tv/`): mostly inherits Phases 1 to 5; needs the TV
   connection and Quick Connect screens gated and a D-pad pass.
2. **Chromecast** (`ui/player/cast/`): `getCastPlaybackInfo` routing and ID decoding in
   `CastMediaLoadBuilder`. Cast URLs go straight to the receiver, so the OkHttp safety net does not
   cover them.
3. **Music and playlists**: route album, artist, track, and playlist calls; check that the recent
   album-grouping logic holds for Emby's album model.
4. **Offline downloads**: download URL and offline progress sync. The feature is incomplete on
   Jellyfin too (`docs/plans/CURRENT_STATUS.md`), so this waits on that.
5. **DLNA**: URL decoding only.

## Phase 7: Rollout

- [ ] Turn the flag on for internal testers, then a percentage, then everyone.
- [ ] Update `AGENTS.md`, `docs/plans/CURRENT_STATUS.md`, `docs/plans/ROADMAP.md`, and the store
      listing copy.
- [ ] Decide whether the "requires Jellyfin" wording in the store listing changes (your call).

## Risks

| Risk | Likelihood | Effect | Mitigation |
|------|-----------|--------|------------|
| KD3 normalizer cannot cover the shape differences | Medium | Phase 3 grows | Phase 0 go/no-go; hand-written DTO fallback |
| Synthetic UUID leaks into an Emby URL | Medium | 404s in odd corners | Single codec, debug interceptor, tests |
| Emby device-profile format differs enough that the server always transcodes | Medium | Poor playback quality | Phase 0 capture; dedicated translation in `EmbyPlaybackDataSource` |
| Emby server-side limits on playback for servers without Premiere | Unknown | Some users cannot play | Check in Phase 0 with a non-Premiere server |
| Repository classes become harder to maintain | High | Slower future work | Emby logic kept in `data/emby/`; one-line branches |
| Regressions on Jellyfin | Low to medium | Affects all current users | Flag off by default; Jellyfin path untouched except the shared helper; regression checklist every phase |
| The full unit suite was not green as of March | Known | Noisy verification | Establish the current baseline before Phase 1 and compare against it |

## Deferred to Implementation

- Exact UUID marker layout for `ServerIdCodec`.
- The list of JSON fields the normalizer rewrites (comes from the Phase 0 failures list).
- Minimum supported Emby version.
- Whether Emby needs an `/emby` path prefix on older servers.

## Open Questions

1. Do you have an Emby server to test against, and does it have Premiere? Without one, nothing
   past Phase 0 can be verified.
2. Is "phone and tablet first, TV and Cast afterwards" the right order, or does TV need to be in
   the first release?
3. Should a profile be one sign-in (two users on the same server are two profiles), as KD9
   assumes?
