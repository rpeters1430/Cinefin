# Cinefin: Plex + Server-Specific Features Implementation Checklist

**Date**: 2026-10-15
**Companion to**: [`2026-10-15-plex-server-support.md`](2026-10-15-plex-server-support.md)

## How to use this

Work top to bottom: Track A (Emby quick wins) is small and ships on its own, Track B (Plex core) is the bulk, then Tracks C and D add server-specific features on top. Each task is one reviewable change; tick it when it is merged with tests green.

The full design and rationale live in `docs/plans/2026-10-15-plex-server-support.md` (KD = key decision in that plan). Tasks marked **[P0]** depend on what the Phase 0 spike finds.

Conventions for every task:

- New server-specific behaviour is gated by a capability flag, never by `if (serverType == …)` in UI code.
- Every mapper or translator change ships with a fixture-driven unit test in `app/src/test/.../data/<server>/`.
- After each phase, run the Jellyfin and Emby smoke checklists in the Rollout section.

## Track A: Emby quick wins

Three small changes that make Emby feel native. All land inside `EmbyApiClient` or existing settings, with no new screens.

### A1. Intro and credits skip from chapter markers

- [ ] Capture `/Items/{id}?Fields=Chapters` JSON (hand-written stand-ins in `app/src/test/resources/emby/` until a real capture replaces them) for an episode with detected intro and credits from your Emby server; save to `app/src/test/resources/emby/`
- [ ] Confirm the `MarkerType` values Emby uses (expected `IntroStart`, `IntroEnd`, `CreditsStart`) and that positions are in ticks
- [x] Add an `EmbyRoute` handler for `/MediaSegments/{itemId}` (`EmbyMediaSegments`; reads `/Users/{userId}/Items/{id}?Fields=Chapters`) that fetches the item's chapters and returns `MediaSegmentDto` list (Intro = IntroStart→IntroEnd, Outro = CreditsStart→runtime end)
- [x] Unit test: fixture → expected segments, including an item with no markers (empty list, no crash)
- [x] Add `supportsMediaSegments` to `ServerType` and turn it on for `EMBY`
- [ ] Manual check: skip-intro and skip-credits buttons appear and seek correctly on Emby

### A2. Mixed Movies + Shows libraries

- [ ] Create a mixed library on the Emby test server (`user_views_mixed.json` is hand-written until then) (4.9.3+) and capture `/UserViews` and its `/Items` JSON
- [ ] Check the `CollectionType` value it reports (normalizer verified: any value the SDK enum lacks, e.g. `mixed`, becomes null and the library is kept; the real value still needs confirming) and how `EmbyJsonNormalizer` handles it (unknown enum → dropped?)
- [x] Make library screens treat it like a mixed folder: show both movies and series, no crash on unknown type (a null collection type already routed to the Stuff screen, but it only requested books/videos/folders; `LibraryType.itemKindsFor` now adds movies and series)
- [x] Unit test for the normalizer with the mixed-library fixture (`EmbyMixedLibraryTest`)

### A3. Respect per-user remote streaming quality

- [ ] Find where Emby 4.9.3 exposes the per-user remote quality setting (user policy or configuration on `/Users/{id}`)
- [ ] When on a non-local connection, use it as the default max bitrate in `EnhancedPlaybackManager` unless the user picked one in Cinefin
- [ ] Test: local connection ignores it; remote connection caps the bitrate

## Track B, Phase 0: Plex spike and fixtures

No app code yet: this phase answers every [P0] question with real responses from your Plex Media Server. Ideally have a second Plex account with a shared server and no Plex Pass.

### Setup

- [ ] Note the PMS version and whether the owner account has Plex Pass
- [ ] Download the current OpenAPI spec from developer.plex.tv/pms and save it with its fetch date
- [ ] Create `app/src/test/resources/plex/` for fixtures

### Auth and server discovery

- [ ] PIN sign-in by hand with curl: create PIN, approve in browser, poll; save `/api/v2/pins` responses
- [ ] Save `/api/v2/user` and `/api/v2/resources?includeHttps=1&includeRelay=1`
- [ ] Confirm each resource has its own `accessToken` and a list of `connections` (local / remote / relay)
- [ ] Confirm `/identity` answers without auth and what it returns
- [ ] Try the JWT/JWK PIN variant; check Ed25519 signing on the app's minSdk (Android Keystore vs Tink)
- [ ] **Decide KD6**: ship JWT from day one, or legacy token first. Record the decision in the plan
- [ ] Find the sign-out / token-revoke call for removing a profile

### Browse responses

- [ ] Save `/library/sections`
- [ ] Save `/library/sections/{id}/all` for a movie and a show library, with `sort` and `X-Plex-Container-Start/Size`; confirm `totalSize` is returned
- [ ] Save a movie and an episode from `/library/metadata/{rk}?includeChapters=1&includeMarkers=1&includeExtras=1`
- [ ] Save `/children` for a show and a season, and `/allLeaves` for a show
- [ ] Save `/library/onDeck`, `/hubs/continueWatching`, `/library/recentlyAdded`, `/hubs/search?query=…`, `/similar`
- [ ] Save an actor-filtered section query (`?actor={tagId}`)
- [ ] Confirm `Marker` units (ms) and types (`intro`, `credits`)
- [ ] Check for `UltraBlurColors`, `Rating` (multiple sources), editions and `clearLogo` in metadata; note field names

### Playback

- [ ] Capture the transcoder decision for: h264/aac MP4 (direct play), HEVC/TrueHD MKV (transcode), file with image subtitles (burn-in)
- [ ] Confirm the `start.m3u8` parameters that actually play in ExoPlayer
- [ ] Confirm `/:/timeline`, `/:/scrobble`, `/:/unscrobble`, and `PUT /library/parts/{id}?audioStreamID=`
- [ ] From off-LAN with the non-Pass account, record what a refused remote stream looks like (status, body)

### Prototype and exit

- [ ] Map one movie and one episode into `BaseItemDto` by hand and load them into the existing details screen
- [ ] List any `BaseItemDto` field the UI assumes non-null that Plex can't fill
- [ ] Update the plan doc: every [P0] answered, KD3/KD4 confirmed or revised

## Track B, Phase 1: Plumbing and capability model

Nothing user-visible changes in this phase; the exit test is that Jellyfin and Emby behave exactly as before.

### IDs (KD2)

- [x] Move `ServerIdCodec` from `data/emby` to `data/common/ids` and parameterize it by a 4-byte marker
- [x] Add markers `PLEX` (ratingKeys, part IDs) and `PLXT` (tag IDs: people, genres)
- [x] Make `decodeAllIn` handle every marker; keep the cheap prefix check
- [x] Existing `ServerIdCodecTest` passes unchanged; add Plex and tag round-trip tests (only its package line changed with the move)

### Server type and capabilities (KD7)

- [x] Add `ServerType.PLEX`
- [x] Add detection from `/identity` (try it when `/System/Info/Public` 404s) (`PlexIdentityProbe`, called from `JellyfinAuthRepository.testServerConnection` when the Jellyfin/Emby probe fails)
- [x] Add capability getters: `supportsQuickConnect`, `supportsFavorites`, `supportsMediaSegments`, `supportsPasswordSignIn`, `remoteStreamingMayRequirePass`
- [x] Add placeholder getters for Tracks C/D, all false for now: `supportsManagedUsers`, `supportsWatchlist`, `providesServerHubs`, `supportsServerConvertedDownloads`
- [x] Replace any direct `serverType ==` checks in UI code with capability getters (Quick Connect checks now use `supportsQuickConnect`; type labels go through `ServerType.labelRes`; the TV sign-in screen still checks for Emby for Emby-specific wording)
- [x] Unit test for the capability table, one row per server type (`ServerTypeCapabilitiesTest`)

### Profiles

- [ ] Add to `ServerProfile`: `plexAccountToken` (encrypted), alternate connection URIs; all default to null/empty (fields added and round-tripped; the token is stored like `accessToken` for now, Keystore encryption still to do)
- [x] App-wide `plexClientIdentifier` (generated once, persisted) (`DeviceIdentityProvider.plexClientIdentifier()`, kept in app-private SharedPreferences)
- [x] Test: profiles saved by the current release still load unchanged

### Network (KD8)

- [x] `JellyfinAuthInterceptor`: for Plex profiles send the X-Plex header set + `Accept: application/json`, and no Emby headers
- [x] Helper that appends `X-Plex-Token` as a query parameter for URLs handed to ExoPlayer, Coil and Cast (`PlexTokenUrls.withToken`; callers wire it in Phase 3/4)
- [x] Test: a Plex token is never sent to a Jellyfin/Emby host and vice versa (the Plex token is only attached for the server's own addresses)

### Flag (KD9)

- [x] Add `ENABLE_PLEX_SUPPORT` to `FeatureFlags.Experimental` and Remote Config, default off
- [x] With the flag off, a Plex URL shows "Plex support is not available yet"

### Exit

- [ ] All unit tests green; Jellyfin and Emby smoke checklists pass (unit tests, lint and warning budget green; manual smoke checklists still to run)

## Track B, Phase 2: Sign in and pick a server

Exit: a user signs in with Plex, picks a server, and it appears in the profile switcher and survives a restart.

### plex.tv client

- [ ] `PlexTvClient` (Retrofit, its own OkHttp client, account token only, never the Jellyfin interceptor)
- [ ] Endpoints: create PIN, poll PIN, `/api/v2/user`, `/api/v2/resources`
- [ ] `PlexModels` for PIN, user, resource, connection (only fields used)
- [ ] Fixture tests for each response

### PIN flow

- [ ] `PlexAuthDataSource`: create PIN → poll with backoff → token; cancelable; stops when the PIN expires
- [ ] If Phase 0 chose JWT: key generation, JWK on PIN create, signed device JWT on poll, refresh path
- [ ] Unit test the state machine (approved, expired, cancelled, network error)

### UI

- [ ] "Sign in with Plex" entry on `ServerConnectionScreen`
- [ ] PIN screen: code, "Open plex.tv" button (Custom Tab to app.plex.tv/auth with clientID and code), waiting state, expiry with retry
- [ ] Typing a Plex URL in the manual field detects Plex and routes to "Sign in with Plex"
- [ ] Server picker: name, owned vs shared (with owner name), online state

### Connection selection

- [ ] `PlexConnectionSelector`: probe `/identity` on every connection in parallel with short timeouts
- [ ] Prefer local HTTPS → local HTTP → remote → relay; store all URIs on the profile
- [ ] Unit test the ranking with fake probe results

### Profile

- [ ] Save profile: `serverId` = machineIdentifier, `accessToken` = the resource's server token, `plexAccountToken` separate
- [ ] Plex badge in `ServerTypeBadge` and the switcher
- [ ] Switching to and from a Plex profile goes through the existing `switchProfile()` reset
- [ ] Removing a Plex profile revokes/forgets its tokens

### Exit

- [ ] Sign in, pick server, restart app, still signed in; switch to Jellyfin and back

## Track B, Phase 3: Browse

Exit: home rows, library grids with paging, series → season → episode, details, search, similar and played state all work on Plex.

### Client skeleton (KD3)

- [ ] `PlexApiClient : ApiClient` with a route table keyed on SDK `pathTemplate`, each entry a handler
- [ ] Unhandled routes: canned empty result + one log line; throw in debug builds
- [ ] `OptimizedClientFactory` hands out `PlexApiClient` for Plex sessions
- [ ] Handlers for `/System/Info/Public`, `/System/Info` (via `/identity`), `/Users/Me` (cached plex.tv user)

### Mapper

- [ ] `PlexMapper`: movie, show, season, episode → `BaseItemDto` with correct `BaseItemKind`
- [ ] Parent links: `ParentId`/`SeasonId`/`SeriesId` from `parentRatingKey`/`grandparentRatingKey`
- [ ] Times: `duration` and `viewOffset` ms → ticks (one helper, tested)
- [ ] `UserData`: `viewOffset` → position, `viewCount` → Played
- [ ] People from `Role`/`Director`/`Writer` with `PLXT`-encoded IDs; genres, studios, year, overview, ratings
- [ ] Sections → `CollectionFolder` items with `CollectionType` (movies/tvshows)
- [ ] Fixture tests for every mapped type

### Query planner (KD4)

- [ ] Capture the actual `getItems` parameter combinations from the repositories into a test table
- [ ] `PlexQueryPlanner` rows: library all, series children, season children, allLeaves, ids, search, resumable, recently added, person, favorites (empty), music (empty + log)
- [ ] Sort mapping (`SortName`, `DateCreated`, `PremiereDate`, `CommunityRating`, `Random`, `DatePlayed`) and order
- [ ] Paging via `X-Plex-Container-Start/Size`; `TotalRecordCount` from `totalSize`
- [ ] Table-driven test: one case per row

### Other routes

- [ ] `/UserViews`, `/Items/{id}`, `/Items/{id}/Similar`, `/Shows/NextUp`, `/Items/{id}/LocalTrailers`
- [ ] Played / unplayed via `/:/scrobble` and `/:/unscrobble`; `getItemUserData`
- [ ] Delete item and library refresh (admin only)

### Images

- [ ] `PlexUrlBuilder`: Primary → `thumb`, Backdrop → `art`, Logo → `clearLogo`, episode series image → `grandparentThumb`
- [ ] Resize through `/photo/:/transcode` to the app's requested max size; token as query param
- [ ] `ImageTags["Primary"]` = thumb timestamp so caches bust on change
- [ ] Plex branch in `JellyfinStreamRepository.getImageUrl` family and `VideoPlayerMetadataManager`

### UI gating

- [ ] Hide favorite heart and Favorites row when `supportsFavorites` is false
- [ ] Walk every main screen on Plex; fix crashes from null fields found in Phase 0

## Track B, Phase 4: Play video

Exit: direct play and forced transcode both work, and resume position and watched state show up in app.plex.tv.

### Decision → PlaybackInfo (KD5)

- [ ] Build a Plex client profile from `JellyfinDeviceProfile` codec support
- [ ] Handler for `/Items/{id}/PlaybackInfo`: call the universal decision endpoint, map to one `MediaSourceInfo`
- [ ] Direct play: `SupportsDirectPlay`, URL = `Part.key` + token
- [ ] Transcode/direct stream: `TranscodingUrl` = `start.m3u8` with `session`, `TranscodingSubProtocol = hls`
- [ ] `MediaStreams` from `Part.Stream[]` (video/audio/subtitle), indices preserved; `PlaySessionId` = session UUID
- [ ] Fixture tests for the three Phase 0 decision captures

### Stream URLs

- [ ] Plex branch in `JellyfinStreamRepository` for direct, transcode, HLS and download, delegating to `PlexUrlBuilder`

### Track selection

- [ ] Audio/subtitle change: `PUT /library/parts/{partId}?audioStreamID=&subtitleStreamID=`, then re-request the decision and restart at current position
- [ ] External/sidecar subtitle URLs work in ExoPlayer

### Reporting

- [ ] `/Sessions/Playing`, `/Progress`, `/Stopped` → `/:/timeline` (state playing/paused/stopped, ms)
- [ ] Report every 10 s and on pause, resume, seek, stop
- [ ] On stop, call transcode stop for the session
- [ ] Auto-scrobble at the player's watched threshold (or rely on Plex's own threshold; confirm in testing)
- [ ] Unit test: ticks → ms and state mapping

### Markers and resume

- [ ] `/MediaSegments/{itemId}` handler from Plex `Marker`s (intro, credits); turn on `supportsMediaSegments` for Plex
- [ ] Resume prompt uses `viewOffset`

### Remote paywall

- [ ] Map the refusal captured in Phase 0 to a specific error: "This server needs Plex Pass or Remote Watch Pass for streaming outside its home network"

### Exit

- [ ] Manual checklist on Plex: sign in, home rows, open a movie, direct play, forced transcode, switch audio and subtitles, skip intro, resume, watched state in app.plex.tv
- [ ] Turn the flag on for debug builds

## Track B, Phase 5: Gating, wording, polish

Exit: no Jellyfin-only entry point is reachable on Plex, and shared screens no longer say "Jellyfin" where they mean any server.

- [ ] Audit every Jellyfin-only entry point against the capability table: SyncPlay, Cinefin plugin, Quick Connect, favorites, admin actions, sessions
- [ ] Replace "Jellyfin server" wording on shared screens with "server"; keep product names where they're specific
- [ ] Re-select a Plex connection automatically when the stored URI stops answering (e.g. leaving home Wi-Fi), using the stored alternates
- [ ] Review unhandled-route logs from Phase 3/4 testing; add handlers or explicit empty results
- [ ] Tablet layout pass on Plex sign-in and server picker

## Track C: Plex-specific features

Each item is independent and starts after Phase 4; they're ordered by value. Field and endpoint names marked (verify) are from memory and get confirmed against the Phase 0 captures first.

### C1. Plex Home managed users

- [ ] Capture `/api/v2/home/users` and the switch-user call (with and without PIN) (verify)
- [ ] Turn on `supportsManagedUsers` for Plex profiles on a Home account
- [ ] "Switch user" sheet on the profile screen: avatars, PIN entry when protected
- [ ] Switching swaps the account token, re-fetches resources, and gets the new user's server token
- [ ] Decide: one Cinefin profile per Home user, or one profile with a sub-user picker
- [ ] Test: watched state and continue-watching differ per Home user

### C2. Server hubs as home rows

- [ ] Capture `/hubs` and `/hubs/sections/{id}` (verify)
- [ ] Map each hub to a row: title, items, hub key for "See all"
- [ ] Turn on `providesServerHubs`; home screen appends hub rows below the built-in rows on Plex
- [ ] De-duplicate hubs that repeat built-in rows (continue watching, recently added)

### C3. Watchlist as favorites

- [ ] Find the plex.tv Discover watchlist endpoints (verify) and how Discover IDs link to library items (`Guid` matching)
- [ ] Turn on `supportsWatchlist`; heart = add/remove watchlist on Plex
- [ ] Watchlist row: owned items open normally; unowned items show a "Not on your server" state
- [ ] For unowned items, offer "Request" through the existing Seerr integration when it's configured

### C4. Extras row

- [ ] Map `/library/metadata/{rk}/extras` by subtype: trailer, behind the scenes, featurette, deleted scene, interview
- [ ] Extras row on movie and series details; plays through the normal player

### C5. Editions

- [ ] Map multiple `Media` entries with `editionTitle` to media sources with readable names (verify field)
- [ ] Version picker shows "Director's Cut", "Extended", etc.

### C6. UltraBlur backgrounds

- [ ] Map `UltraBlurColors` (four corner colors) into an app-side model (verify field)
- [ ] Use them for the details and immersive backgrounds on Plex; fall back to existing palette extraction elsewhere

### C7. Multiple rating sources

- [ ] Map the `Rating` array (Rotten Tomatoes critic/audience, IMDb, TMDB) (verify)
- [ ] Rating chips on details with source icons; keep the single-rating chip for Jellyfin/Emby

## Track D: Server-converted downloads

Lets a user download at a chosen quality (e.g. 720p for the phone) with the server doing the conversion. Emby needs Premiere; Plex needs Plex Pass. Jellyfin keeps original-file downloads. Start after Track A and Phase 4.

### Shared

- [ ] Define a `ConvertedDownloadSource` interface in the downloads layer: request conversion, poll status, get file URL, cancel
- [ ] Quality picker on the download action when `supportsServerConvertedDownloads` is on (Original, 1080p, 720p, 480p)
- [ ] Download worker handles the "converting on server" state before the file transfer starts
- [ ] Capability comes from what the server reports at runtime (Premiere / Plex Pass), not just `ServerType`

### Emby (Premiere)

- [ ] Find the Sync/conversion endpoints and Premiere status in your server's `/openapi.json` (verify)
- [ ] Implement `EmbyConvertedDownloadSource`; capture fixtures; tests
- [ ] Manual check: request 720p, server converts, file downloads and plays offline

### Plex (Plex Pass)

- [ ] Find the download/conversion flow and how Plex Pass status is reported (verify)
- [ ] Implement `PlexConvertedDownloadSource`; capture fixtures; tests
- [ ] Manual check as above; offline progress syncs back via `/:/timeline` when online

## Rollout and regression checklist

### Smoke checklist (run after every phase, on Jellyfin and Emby)

- [ ] Sign in, restart, still signed in
- [ ] Home rows populate; open a library, scroll to page 2
- [ ] Open a movie and an episode; direct play; forced transcode
- [ ] Resume position and watched state show in the server's own web app
- [ ] Switch profile and back
- [ ] Search returns results

### Plex rollout

- [ ] Internal testers: `enable_plex_support` on via Remote Config
- [ ] Watch Crashlytics for `PlexApiClient` unhandled-route logs and mapper decode failures for one release
- [ ] Beta track with the flag on
- [ ] Store listing and README mention Plex; don't promise remote streaming for non-Pass users
- [ ] Flag default on

### Later (not in this checklist)

Android TV UI in this app, Chromecast, music, photos, playlists and collections, GDM LAN discovery, and CinefinTV each get their own plan entry.
